#!/usr/bin/env bash
#
# One-time per project: create the Firestore export bucket and grant the
# runtime service account the two roles scheduledFirestoreBackup needs.
#
#   ./scripts/setup-backup-bucket.sh safebeauty
#   ./scripts/setup-backup-bucket.sh safebeauty-staging
#
# The bucket name is derived from the project id (see BACKUP_BUCKET in
# functions/index.js), so each project backs up into its own bucket. It used to
# be hardcoded, which meant a second project would have exported its data into
# production's backup folder — corrupting the one artefact a real recovery
# depends on, discoverable only while attempting that recovery.

set -euo pipefail

PROJECT="${1:-}"
if [[ -z "$PROJECT" ]]; then
  echo "usage: $0 <project-id>" >&2
  exit 2
fi

BUCKET="gs://${PROJECT}-firestore-backups"
NUMBER="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
SA="${NUMBER}-compute@developer.gserviceaccount.com"

echo "── Project $PROJECT (#$NUMBER)"
echo "── Bucket  $BUCKET"

if gcloud storage buckets describe "$BUCKET" --project="$PROJECT" >/dev/null 2>&1; then
  echo "   bucket already exists"
else
  gcloud storage buckets create "$BUCKET" \
    --project="$PROJECT" --location=us-central1 --uniform-bucket-level-access
  echo "   bucket created"
fi

# Two accounts, for two different reasons. The function's runtime account needs
# permission to ASK for an export. The Firestore service agent is what actually
# writes the files, so the bucket grant must go to it — granting only the caller
# produces "Service account does not have access to Google Cloud Storage file",
# which reads like the caller's problem and is not.
AGENT="service-${NUMBER}@gcp-sa-firestore.iam.gserviceaccount.com"

echo "── Granting export permission to the caller: $SA"
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member="serviceAccount:${SA}" \
  --role=roles/datastore.importExportAdmin >/dev/null

echo "── Granting bucket access to the writer: $AGENT"
for member in "serviceAccount:${SA}" "serviceAccount:${AGENT}"; do
  gcloud storage buckets add-iam-policy-binding "$BUCKET" \
    --member="$member" --role=roles/storage.admin >/dev/null
done

echo "── Done. scheduledFirestoreBackup can now export to $BUCKET"
