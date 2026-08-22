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

echo "── firebase deploy"
if [[ -n "$TARGETS" ]]; then
  npx firebase deploy --project "$PROJECT" --only "$TARGETS"
else
  npx firebase deploy --project "$PROJECT"
fi

# Only relevant when functions were part of this deploy.
if [[ -n "$TARGETS" && "$TARGETS" != *"functions"* ]]; then
  echo "── Skipping invoker bindings (functions not in this deploy)"
  exit 0
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
