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

echo "── Ensuring public invoker on callables (I-12)"

CALLABLES="$(grep -oE '^exports\.[a-zA-Z0-9_]+ = onCall' functions/index.js \
             | sed 's/^exports\.//; s/ = onCall//')"

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
