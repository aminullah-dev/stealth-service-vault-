#!/usr/bin/env bash
#
# Deploy SafeBeauty to a Firebase project alias, reproducibly.
#
#   ./scripts/deploy.sh staging
#   ./scripts/deploy.sh prod
#   ./scripts/deploy.sh prod functions,firestore:rules     # narrower target
#
# Exists because a deploy used to have a step that lived only in someone's
# memory: a newly created callable is deployed with no public invoker binding
# and returns 403 until someone remembers to add it by hand. That has already
# produced one live outage in the admin console. Specification invariant I-12.
#
# Callables are read out of functions/index.js rather than listed here, so the
# list cannot drift from the code. Scheduled functions and Firestore triggers
# are deliberately NOT made invokable — they are called by Google, not by
# clients, and opening them would be a real hole.

set -euo pipefail

ALIAS="${1:-}"
TARGETS="${2:-}"

if [[ -z "$ALIAS" ]]; then
  echo "usage: $0 <staging|prod> [firebase deploy targets]" >&2
  exit 2
fi

cd "$(dirname "$0")/.."

# Read rather than require: .firebaserc has no .json extension, so require()
# parses it as JavaScript and fails on the first colon.
PROJECT="$(node -e "
  const rc = JSON.parse(require('fs').readFileSync('.firebaserc', 'utf8'));
  const p = rc.projects['$ALIAS'];
  if (!p) { console.error('Unknown alias: $ALIAS'); process.exit(1); }
  console.log(p);
")"

echo "── Deploying to '$ALIAS' ($PROJECT)"

# Production deploys run the tests first. A deploy is the last moment the suite
# is free to run; after it, a failure is a customer's problem.
if [[ "$ALIAS" == "prod" ]]; then
  echo "── Running unit tests"
  ( cd functions && npm test )
fi

# ── Narrow "functions" to the functions that actually changed ────────────────
#
# A full functions deploy asks Cloud Build for one build per function. There are
# 76, the project's concurrent-build quota is far below that, and the result is
# not a slow deploy but a failed one: 71 builds came back CANCELLED in a single
# run, and the five that succeeded were only the ones that started first.
#
# The baseline is what was last DEPLOYED, not what was last pushed — git has no
# idea which commit is running in us-central1, so the script records it. The
# marker is written only after a deploy that actually succeeded, so a failed run
# leaves the baseline where it was and the next attempt still carries the work.
#
#   deploy.sh prod functions        → only what changed since the last success
#   deploy.sh prod functions all    → every function, when you mean it
#   deploy.sh prod firestore:rules  → untouched, no narrowing
MARKER=".firebase/functions-deployed-$PROJECT"
NARROWED=""

if [[ "$TARGETS" == "functions" && "${3:-}" != "all" ]]; then
  LAST="$(cat "$MARKER" 2>/dev/null || true)"
  if [[ -z "$LAST" ]] || ! git cat-file -e "$LAST^{commit}" 2>/dev/null; then
    echo "── functions: no record of a previous deploy — deploying all"
  else
    CHANGED="$(git diff --name-only "$LAST"..HEAD -- functions/ | grep -v '^functions/test/' || true)"
    if [[ -z "$CHANGED" ]]; then
      echo "── functions: nothing changed since ${LAST:0:8} — deploying all anyway"
      echo "     (a previous run may have failed after the marker was written)" >&2
    elif grep -qE '^functions/index\.js$|^functions/shared\.js$' <<<"$CHANGED"; then
      # index.js registers every export and shared.js is imported by all of
      # them. Nothing narrower is honest.
      echo "── functions: index.js or shared.js changed — deploying all"
    else
      # A changed lib/ file reaches only the domains that require it. Falling
      # back to "all" here is what produced the 71 cancelled builds: lib/areas.js
      # is imported by exactly one domain, and deploying 76 functions to cover it
      # is how a small change becomes a failed deploy.
      FILES=""
      for f in $CHANGED; do
        case "$f" in
          functions/domains/*.js) FILES="$FILES $f" ;;
          functions/lib/*.js)
            MOD="$(basename "$f" .js)"
            # shared.js is imported by every domain, so a lib it requires
            # reaches every domain too — even ones with no literal
            # require("../lib/<mod>") of their own. Matching only the literal
            # left those domains running the OLD lib code while the deploy
            # exited 0 and recorded the commit as shipped, which is the worst
            # possible combination: wrong, and marked done.
            if grep -qE "require\(\"\./lib/$MOD\"\)" functions/shared.js; then
              echo "── functions: lib/$MOD is reached through shared.js — deploying all"
              FILES="ALL"; break
            fi
            for d in functions/domains/*.js; do
              grep -qE "require\(\"\.\./lib/$MOD\"\)" "$d" && FILES="$FILES $d"
            done ;;
          *) FILES="ALL"; break ;;
        esac
      done
      if [[ "$FILES" == "ALL" ]]; then
        echo "── functions: a file outside domains/ and lib/ changed — deploying all"
        FILES=""
      fi
      # Only names index.js re-exports are real functions. A domain also
      # exports pure helpers — deriveSalonDiscovery, storedDiscoveryFields,
      # parseGsUri — which index.js does not wire up, and asking firebase to
      # deploy one of those makes it throw during prepare and take the WHOLE
      # batch with it, including the functions that were fine.
      WIRED="$(grep -oE '^exports\.[A-Za-z_][A-Za-z0-9_]*' functions/index.js | sed 's/exports\.//' | sort -u)"
      for f in $(tr ' ' '\n' <<<"$FILES" | sort -u); do
        for n in $(grep -oE '^exports\.[A-Za-z_][A-Za-z0-9_]*' "$f" | sed 's/exports\.//'); do
          if grep -qx "$n" <<<"$WIRED"; then
            NARROWED="${NARROWED:+$NARROWED,}functions:$n"
          else
            echo "     (skipping $n — a helper index.js does not export)"
          fi
        done
      done
      if [[ -n "$NARROWED" ]]; then
        echo "── functions: changed since ${LAST:0:8} —"
        tr ',' '\n' <<<"$NARROWED" | sed 's/^functions:/     /'
        TARGETS="$NARROWED"
      fi
    fi
  fi
elif [[ "${3:-}" == "all" ]]; then
  echo "── functions: deploying all (asked for)"
fi

echo "── firebase deploy"
# The exit code is captured rather than allowed to kill the script. A deploy can
# fail on one function and still have changed 73 others, and the invoker check
# below is the step that exists to stop a callable going live returning 403 —
# skipping it exactly when a deploy went wrong is the opposite of what it is for.
# The failure is not swallowed: it is re-raised at the end, after the check.
# Functions go out in batches. Cloud Build runs one build per function and the
# project's concurrent quota is small: asking for seventeen at once put every
# one of them in a queue none of them left, and they were cancelled at the queue
# TTL without a single startTime between them. Five at a time, sequentially,
# stays under it. Slower, and it finishes.
DEPLOY_STATUS=0
# "Deploy all" is the case that most needs batching, and was the one case that
# skipped it. TARGETS is the bare string "functions" there, which never matched
# the functions:* test below, so all 79 went out in a single invocation — the
# exact shape that put seventeen builds in a queue none of them left and had
# them cancelled at the queue TTL. Expanded into named targets from index.js so
# it takes the same five-at-a-time path as every narrowed deploy.
if [[ "$TARGETS" == "functions" ]]; then
  ALL_FNS="$(grep -oE '^exports\.[A-Za-z_][A-Za-z0-9_]*' functions/index.js \
             | sed 's/exports\./functions:/' | sort -u | paste -sd, -)"
  if [[ -n "$ALL_FNS" ]]; then
    echo "── functions: expanding \"all\" into $(tr ',' '\n' <<<"$ALL_FNS" | wc -l | tr -d ' ') named targets so they batch"
    TARGETS="$ALL_FNS"
  fi
fi
if [[ "$TARGETS" == functions:* ]]; then
  IFS=',' read -ra FNS <<<"$TARGETS"
  TOTAL=${#FNS[@]}
  BATCH=5
  for ((i = 0; i < TOTAL; i += BATCH)); do
    CHUNK="$(IFS=,; echo "${FNS[*]:i:BATCH}")"
    echo "── batch $((i / BATCH + 1)) of $(((TOTAL + BATCH - 1) / BATCH)): $(tr ',' ' ' <<<"${CHUNK//functions:/}")"
    npx firebase deploy --project "$PROJECT" --only "$CHUNK" || DEPLOY_STATUS=$?
  done
elif [[ -n "$TARGETS" ]]; then
  npx firebase deploy --project "$PROJECT" --only "$TARGETS" || DEPLOY_STATUS=$?
else
  npx firebase deploy --project "$PROJECT" || DEPLOY_STATUS=$?
fi

if [[ "$DEPLOY_STATUS" -ne 0 ]]; then
  echo "── firebase deploy exited $DEPLOY_STATUS — continuing to the invoker check anyway" >&2
fi

# Only relevant when functions were part of this deploy.
# The baseline for the next run. Written only on success: a failed deploy that
# moved the marker would make the next run think the work was already out.
if [[ "$DEPLOY_STATUS" -eq 0 && "$TARGETS" == *"functions"* ]]; then
  mkdir -p .firebase && git rev-parse HEAD > "$MARKER"
  echo "── functions: recorded $(git rev-parse --short HEAD) as deployed"
fi

if [[ -n "$TARGETS" && "$TARGETS" != *"functions"* ]]; then
  echo "── Skipping invoker bindings (functions not in this deploy)"
  exit "$DEPLOY_STATUS"
fi

# Indexes drift when deploys use narrow --only targets, which most do. The drift
# is invisible until a query fails at runtime on the project that is behind —
# and the project that is behind is usually staging, so the failure shows up
# exactly where it was supposed to be caught, but after the change shipped.
LOCAL_IDX="$(node -e "
  const j = JSON.parse(require('fs').readFileSync('firestore.indexes.json','utf8'));
  console.log((j.indexes||[]).length);
")"
REMOTE_IDX="$(gcloud firestore indexes composite list --project="$PROJECT" \
  --format='value(name)' 2>/dev/null | wc -l | tr -d ' ')"

# The two directions of drift mean different things and only one is urgent.
if [[ "$LOCAL_IDX" -gt "$REMOTE_IDX" ]]; then
  echo "── Missing indexes on $PROJECT: $REMOTE_IDX deployed, $LOCAL_IDX defined." >&2
  echo "     A query will fail at runtime with FAILED_PRECONDITION. Deploy them:" >&2
  echo "     npx firebase deploy --project $PROJECT --only firestore:indexes" >&2
elif [[ "$REMOTE_IDX" -gt "$LOCAL_IDX" ]]; then
  echo "── $PROJECT has $((REMOTE_IDX - LOCAL_IDX)) index(es) no longer defined locally." >&2
  echo "     Harmless but billed: superseded indexes keep being maintained on every" >&2
  echo "     write. Firebase asks before removing them, so this needs a person:" >&2
  echo "     npx firebase deploy --project $PROJECT --only firestore:indexes" >&2
fi

echo "── Ensuring public invoker on callables (I-12)"

# index.js AND the domain modules. Splitting the backend moved callables out of
# index.js, and a grep that only looked there would have found fewer of them and
# reported success — leaving the ones it missed returning 403, which is the
# precise outage this script exists to prevent.
CALLABLES="$(grep -hoE '^exports\.[a-zA-Z0-9_]+ = onCall' \
               functions/index.js functions/domains/*.js 2>/dev/null \
             | sed 's/^exports\.//; s/ = onCall//' | sort -u)"

if [[ -z "$CALLABLES" ]]; then
  echo "   No callables found — check the export pattern in functions/index.js" >&2
  exit 1
fi

missing=0
while read -r fn; do
  [[ -z "$fn" ]] && continue
  # Cloud Run lowercases the function name for the service id.
  svc="$(echo "$fn" | tr '[:upper:]' '[:lower:]')"

  if gcloud run services get-iam-policy "$svc" \
       --region=us-central1 --project="$PROJECT" \
       --format='value(bindings.members)' 2>/dev/null | grep -q allUsers; then
    continue
  fi

  echo "   + $fn"
  if ! gcloud run services add-iam-policy-binding "$svc" \
         --region=us-central1 --project="$PROJECT" \
         --member=allUsers --role=roles/run.invoker >/dev/null 2>&1; then
    echo "   ! could not bind $fn" >&2
    missing=$((missing + 1))
  fi
done <<< "$CALLABLES"

if [[ "$missing" -gt 0 ]]; then
  echo "── $missing callable(s) have no public invoker and will return 403" >&2
  exit 1
fi

echo "── Done. $(echo "$CALLABLES" | wc -l | tr -d ' ') callables reachable."

# Re-raise the deploy's own failure now that the safety check has run. A
# transient IAM error on a scheduled function — which needs no public invoker —
# looks exactly like this and is worth seeing, but it should not have hidden
# whether the callables are reachable.
exit "$DEPLOY_STATUS"
