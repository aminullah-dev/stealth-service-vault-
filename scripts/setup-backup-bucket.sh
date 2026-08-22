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

BUCKET="gs://${PROJECT}-backups"
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

echo "── Granting export permissions to $SA"
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member="serviceAccount:${SA}" \
  --role=roles/datastore.importExportAdmin >/dev/null
gcloud storage buckets add-iam-policy-binding "$BUCKET" \
  --member="serviceAccount:${SA}" \
  --role=roles/storage.admin >/dev/null

echo "── Done. scheduledFirestoreBackup can now export to $BUCKET"
