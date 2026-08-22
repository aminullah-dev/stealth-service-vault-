# SafeBeauty — recovery runbook

What to do when data is lost, corrupted, or deleted by mistake.

Every number below was **measured on 2026-08-22**, not estimated. Re-measure
quarterly; an untested backup is a belief, not a capability.

| Objective | Target | Measured | How |
|---|---|---|---|
| RPO — how much data a disaster can cost | ≤ 1 min | **≤ 1 min** | Firestore PITR, 7-day window |
| RTO — how long recovery takes | ≤ 4 h | **≈ 15 min** | 12s restore + verification |
| Restore rehearsed | quarterly | **2026-08-22** | Full import into staging, counts matched |

At the drill, production held 262 documents. Restore time grows with data
volume, so re-measure as the platform grows rather than trusting the number
above forever.

---

## First: which failure is this?

| Symptom | Use |
|---|---|
| Bad deploy or script deleted/corrupted recent data | **A — Point-in-time recovery** |
| Corruption noticed days later, beyond the 7-day PITR window | **B — Restore from nightly export** |
| One collection wrong, rest fine | **A**, exporting only that collection |
| Whole project lost or inaccessible | **B**, into a fresh project |

Prefer **A**. It recovers to the minute and does not depend on a backup job
having run.

---

## Before touching anything

1. **Stop the bleeding.** If a running job is still corrupting data, disable it
   first:
   ```bash
   gcloud scheduler jobs pause JOB_NAME --location=us-central1 --project=safebeauty
   ```
2. **Write down the last known-good time**, to the minute, in UTC. Everything
   below depends on it.
3. **Never restore over production first.** Restore into `safebeauty-staging`,
   confirm the data is what you expect, and only then decide about production.
   A restore is itself a destructive write.

---

## A — Point-in-time recovery (preferred)

Recovers Firestore as it existed at any exact minute within the last 7 days.

```bash
# 1. Confirm the moment is inside the window
gcloud firestore databases describe --database='(default)' --project=safebeauty \
  --format="value(earliestVersionTime)"

# 2. Export that moment. The timestamp MUST be an exact minute — seconds are
#    rejected with INVALID_ARGUMENT.
gcloud firestore export gs://safebeauty-firestore-backups/pitr-$(date -u +%Y%m%d-%H%M) \
  --snapshot-time='2026-08-22T03:16:00Z' \
  --project=safebeauty --async

# 3. Watch it. Took 12 seconds at 262 documents.
gcloud firestore operations list --project=safebeauty --limit=1
```

Then import into staging (step B3 below) and verify before doing anything to
production.

---

## B — Restore from the nightly export

```bash
# 1. Pick an export. Folders are named for the Kabul-local day.
gcloud storage ls gs://safebeauty-firestore-backups/

# 2. Confirm it actually finished. Without this file the export is partial.
gcloud storage ls gs://safebeauty-firestore-backups/2026-08-22/ \
  | grep overall_export_metadata

# 3. Import into staging first.
gcloud firestore import gs://safebeauty-firestore-backups/2026-08-22 \
  --project=safebeauty-staging --async

# 4. Wait for done=True.
gcloud firestore operations list --project=safebeauty-staging --limit=1
```

### One-time grant, if the import is denied

Staging's Firestore service agent must be able to read production's bucket:

```bash
for role in roles/storage.legacyBucketReader roles/storage.objectViewer; do
  gcloud storage buckets add-iam-policy-binding gs://safebeauty-firestore-backups \
    --member="serviceAccount:service-938215172040@gcp-sa-firestore.iam.gserviceaccount.com" \
    --role="$role"
done
```

IAM takes up to a minute to propagate. A denial immediately after granting is
usually just that — wait and retry before assuming the grant was wrong.

---

## Verify before trusting the restore

An import that reports `SUCCESSFUL` has only proven it did not crash.

```bash
TOKEN=$(gcloud auth print-access-token)
count() {
  curl -s -X POST "https://firestore.googleapis.com/v1/projects/$1/databases/(default)/documents:runAggregationQuery" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"structuredAggregationQuery\":{\"structuredQuery\":{\"from\":[{\"collectionId\":\"$2\"}]},\"aggregations\":[{\"count\":{},\"alias\":\"c\"}]}}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['result']['aggregateFields']['c']['integerValue'])"
}

for c in users salons appointments payments reviews uid_map notifications; do
  printf "%-16s prod=%s restored=%s\n" "$c" "$(count safebeauty $c)" "$(count safebeauty-staging $c)"
done
```

Counts matching is necessary, not sufficient. Also open one appointment and one
user and read the fields. At the drill a spot-checked appointment hashed
identically in both projects.

---

## Promoting a restore to production

Only after staging looks right.

**Firestore import merges — it does not replace.** Documents in the export
overwrite those with the same id; documents created since the export are left
untouched. That is usually what you want after a partial loss, and is the wrong
thing after a corruption that also wrote new documents. In that case delete the
affected collections first, deliberately, and know what you are giving up.

```bash
gcloud firestore import gs://safebeauty-firestore-backups/2026-08-22 \
  --project=safebeauty --async
```

Afterwards:

- Re-run **Health → Assign missing booking references** in the admin console.
- Re-run **Health → Assign missing phone lookup keys**. Without it, restored
  legacy accounts fall back to a slower sign-in path.
- Fire **Health → Send a test alert** to confirm monitoring survived.

---

## Afterwards — mandatory

**Wipe the restored data from staging.** It is real customer data: names, phone
numbers, tazkira numbers, for women in Kabul. It must not sit in a second
environment after the drill.

```bash
npx firebase firestore:delete --all-collections --project safebeauty-staging --force
```

Delete any drill export too — each one is a full copy of production:

```bash
gcloud storage rm -r gs://safebeauty-firestore-backups/pitr-DRILL-FOLDER
```

---

## What broke last time, and why it will look fine when it isn't

The nightly backup ran for the entire life of the project and **never produced a
single file**. Three separate causes, none of which announced themselves:

1. `gs://safebeauty-backups` had been created in an unrelated project
   (`worktrack-prod`). Bucket names are one global namespace, so a plausible
   name being taken — including by yourself, in another project — is silent.
   The bucket is now `gs://safebeauty-firestore-backups`, owned by `safebeauty`.

2. The runtime service account lacked `roles/datastore.importExportAdmin`. The
   setup commands existed only as a comment in `functions/index.js` and had
   never been run. They are now `scripts/setup-backup-bucket.sh`.

3. The bucket grant went to the calling service account, but Firestore exports
   are written by a **different** principal —
   `service-PROJECT_NUMBER@gcp-sa-firestore.iam.gserviceaccount.com`. The error
   says the *service account* lacks access, which reads like the caller's
   problem and is not.

The lesson worth keeping: every layer reported healthy. The job was deployed,
the schedule was enabled, the code was correct, the bucket existed. Only asking
"is there a file in it" found the truth. Ask that question, not the others.

Since P3 this failure raises a `BACKUP_FAILED` alert. That alert only reaches a
person if the notification channel is verified — check that it still is.

---

## Quarterly drill

1. Trigger a backup: `gcloud scheduler jobs run firebase-schedule-scheduledFirestoreBackup-us-central1 --location=us-central1 --project=safebeauty`
2. Confirm a new dated folder with `overall_export_metadata` appears.
3. Import into staging, timing it.
4. Verify counts and spot-check a document.
5. Wipe staging.
6. Update the measured RTO at the top of this file.
