// maintenance — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { admin, alertable, assertAdmin, db, logAdminAction, logger } = require("../shared");

const { hasBookableWeek } = require("../lib/hours");
const { shouldPurge } = require("../lib/kycretention");
// An absolute time, not "every 24 hours".
//
// A relative interval is measured from the last deploy, so every
// `firebase deploy --only functions` pushed this another day out. This project
// deploys most days, and the scheduler agreed: last attempt 2026-09-04, next
// run exactly 24h after a DEPLOYMENT_ROLLOUT. It had missed two days and would
// have gone on missing them. Every other daily job here already uses a
// wall-clock time, which is why they were all firing and these two were not.
exports.cleanupRateLimits = onSchedule(
  { schedule: "every day 03:30", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const snap = await db.collection("rate_limits")
      .where("updatedAt", "<", cutoff).limit(500).get();
    if (snap.empty) return;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    logger.log(`cleanupRateLimits: removed ${snap.size} stale counter(s)`);
  }
);

// ── Scheduled Firestore backup ────────────────────────────────────────────────
//
// Everything the business depends on — salons, appointments, payments, provider
// balances, KYC decisions — lives in one Firestore database with no history. A
// bad admin action, a bad deploy, or an accidental bulk delete is unrecoverable
// without an export. This writes a full daily export to a Cloud Storage bucket,
// which is the only thing that turns "we lost the bookings" into "we restore
// yesterday's".
//
// Restore (manual, deliberately not automated):
//   gcloud firestore import gs://safebeauty-backups/<TIMESTAMP>
//
// Requires, one time:
//   gcloud storage buckets create gs://safebeauty-backups --location=us-central1
//   gcloud projects add-iam-policy-binding safebeauty \
//     --member=serviceAccount:238802374530-compute@developer.gserviceaccount.com \
//     --role=roles/datastore.importExportAdmin
//   gcloud storage buckets add-iam-policy-binding gs://safebeauty-backups \
//     --member=serviceAccount:238802374530-compute@developer.gserviceaccount.com \
//     --role=roles/storage.admin

// Derived from the running project rather than hardcoded. With a staging
// project deploying the same code, a fixed bucket meant staging would export
// its own data into production's backup folder — quietly corrupting the one
// artefact a real recovery depends on, and only discovered while trying to use
// it. The bucket for each project is created by scripts/setup-backup-bucket.sh.
// "-firestore-backups", not "-backups": gs://safebeauty-backups already exists
// in an unrelated project (worktrack-prod), which is why every nightly export
// failed with PERMISSION_DENIED and the bucket this code pointed at stayed empty
// for the life of the project. Bucket names are a single global namespace, so a
// plausible name being taken by someone else — including yourself, in another
// project — is normal and silent.
const BACKUP_BUCKET = `gs://${process.env.GCLOUD_PROJECT || "safebeauty"}-firestore-backups`;

exports.scheduledFirestoreBackup = onSchedule(
  { schedule: "every day 02:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const projectId = process.env.GCLOUD_PROJECT || "safebeauty";
    const client = new admin.firestore.v1.FirestoreAdminClient();
    const databaseName = client.databasePath(projectId, "(default)");
    // Kabul-local date, so a backup folder name matches the day the operator
    // would ask for ("restore Tuesday's data").
    const stamp = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kabul" });

    // exportDocuments STARTS an export and returns immediately; the operation can
    // still fail afterwards. Recording the run in Firestore, and having
    // verifyFirestoreBackup finish the story, is what turns "we called the API"
    // into "we have a backup" — the difference the comment below warns about.
    const runRef = db.doc(`system_backups/${stamp}`);

    try {
      const [response] = await client.exportDocuments({
        name: databaseName,
        outputUriPrefix: `${BACKUP_BUCKET}/${stamp}`,
        collectionIds: [],   // empty = every collection
      });
      await runRef.set({
        stamp,
        state:         "RUNNING",
        operationName: response.name || "",
        outputUri:     `${BACKUP_BUCKET}/${stamp}`,
        startedAt:     Date.now(),
        finishedAt:    0,
        error:         "",
      });
      logger.log(`scheduledFirestoreBackup: started ${response.name} -> ${BACKUP_BUCKET}/${stamp}`);
    } catch (e) {
      // Loud: a silently failing backup is worse than no backup, because you
      // only discover it the day you need to restore.
      await runRef.set({
        stamp, state: "FAILED", operationName: "",
        outputUri: `${BACKUP_BUCKET}/${stamp}`,
        startedAt: Date.now(), finishedAt: Date.now(),
        error: String((e && e.message) || e).slice(0, 500),
      }, { merge: true });
      alertable("BACKUP_FAILED", "scheduledFirestoreBackup FAILED", { error: String(e && e.message || e) });
      throw e;
    }
  }
);

/**
 * Split "gs://bucket/prefix" into its two halves, or null if it is not one.
 *
 * Pure and exported so the parsing can be tested without a bucket. It decides
 * which objects get counted, and counting the wrong prefix would report an
 * empty backup for a good one — or, worse, a healthy count for a prefix that
 * belongs to a different day.
 */
function parseGsUri(uri) {
  const s = String(uri == null ? "" : uri).trim();
  if (!s.startsWith("gs://")) return null;
  const rest = s.slice("gs://".length);
  const slash = rest.indexOf("/");
  if (slash <= 0) return null;
  const bucketName = rest.slice(0, slash);
  const path = rest.slice(slash + 1).replace(/\/+$/, "");
  if (!bucketName || !path) return null;
  return { bucketName, prefix: path + "/" };
}

/**
 * What actually landed in the bucket for one export.
 *
 * Returns zeroes rather than throwing when the bucket cannot be read: a
 * verifier that dies on a listing error stops verifying every OTHER backup in
 * the same run, and the states it would have written are the only record that
 * any of this happened. A zero is reported as a failed backup, which is the
 * conservative reading of "we could not confirm one exists".
 */
async function measureBackup(outputUri) {
  const parsed = parseGsUri(outputUri);
  if (!parsed) return { objects: 0, bytes: 0 };
  const { bucketName, prefix } = parsed;

  try {
    const [files] = await admin.storage().bucket(bucketName).getFiles({ prefix });
    let bytes = 0;
    for (const f of files) bytes += Number((f.metadata && f.metadata.size) || 0);
    return { objects: files.length, bytes };
  } catch (e) {
    logger.error(`measureBackup: could not list ${outputUri}`, e);
    return { objects: 0, bytes: 0 };
  }
}

// ── verifyFirestoreBackup ─────────────────────────────────────────────────────
//
// The export is asynchronous: scheduledFirestoreBackup only learns that it
// started. This closes the loop by asking the operation how it ended, so the
// console can say "last good backup: 02:00 today" rather than "we asked for one".
//
// Runs a few times after the nightly window rather than once, because a full
// export of a growing database takes an unpredictable while.
exports.verifyFirestoreBackup = onSchedule(
  { schedule: "0 3,4,6,9 * * *", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const running = await db.collection("system_backups")
      .where("state", "==", "RUNNING").limit(10).get();
    if (running.empty) return;

    const client = new admin.firestore.v1.FirestoreAdminClient();

    for (const doc of running.docs) {
      const { operationName, startedAt } = doc.data();
      if (!operationName) continue;
      const stuck = Date.now() - Number(startedAt || 0) > 12 * 60 * 60 * 1000;

      // Two independent checks, because they fail in different ways.
      //
      // Asking the long-running-operation directly is the precise answer, but it
      // reaches through a generated client whose surface is not part of any
      // stability promise — so it is attempted, and never trusted to be there.
      // The elapsed-time rule needs nothing but the clock, and is what actually
      // guarantees a stalled export cannot sit in RUNNING forever looking fine.
      let op = null;
      try {
        if (client.operationsClient && typeof client.operationsClient.getOperation === "function") {
          const [fetched] = await client.operationsClient.getOperation({ name: operationName });
          op = fetched;
        }
      } catch (e) {
        logger.warn(`verifyFirestoreBackup: could not read operation for ${doc.id}`, e);
      }

      try {
        if (op && op.done === true) {
          if (op.error && op.error.message) {
            await doc.ref.update({
              state: "FAILED", finishedAt: Date.now(),
              error: String(op.error.message).slice(0, 500),
            });
            alertable("BACKUP_FAILED", `verifyFirestoreBackup: ${doc.id} failed`,
              { id: doc.id, error: String(op.error.message).slice(0, 500) });
          } else {
            // The operation says it finished. That is not the same claim as
            // "there is a backup", and this file already records what the
            // difference costs: the export used to point at a bucket in another
            // project, every run failed, and the folder stayed empty for the
            // life of the project. An operation reporting success is a fact
            // about an API call; only the objects are the backup.
            //
            // So the artefact is measured before DONE is written, and the size
            // is stored — a Health tab that says "last good backup: 11 objects,
            // 179 KiB" can be disbelieved by a person reading it, which "DONE"
            // cannot.
            const measured = await measureBackup(doc.data().outputUri || "");
            if (measured.objects === 0) {
              await doc.ref.update({
                state: "FAILED", finishedAt: Date.now(),
                objects: 0, bytes: 0,
                error: `Export reported success but wrote no objects to ${doc.data().outputUri || "(no uri)"}.`,
              });
              alertable("BACKUP_FAILED", "verifyFirestoreBackup: export completed but the bucket is empty",
                { stamp: doc.id, outputUri: doc.data().outputUri || "" });
              logger.error(`verifyFirestoreBackup: ${doc.id} completed with an empty bucket`);
            } else {
              await doc.ref.update({
                state: "DONE", finishedAt: Date.now(), error: "",
                objects: measured.objects, bytes: measured.bytes,
              });
              logger.log(`verifyFirestoreBackup: ${doc.id} completed — ${measured.objects} object(s), ${measured.bytes} byte(s)`);
            }
          }
        } else if (stuck) {
          await doc.ref.update({
            state: "FAILED", finishedAt: Date.now(),
            error: "Export never reported completion within 12 hours.",
          });
          logger.error(`verifyFirestoreBackup: ${doc.id} stuck in RUNNING`);
        }
      } catch (e) {
        logger.error(`verifyFirestoreBackup: could not update ${doc.id}`, e);
      }
    }
  }
);

// ── pruneOldBackups ───────────────────────────────────────────────────────────
//
// Every night's export is a full copy of the database. Kept forever they are a
// bill that grows quadratically with the life of the product, for copies nobody
// will ever restore. Thirty days is long enough to notice that something was
// corrupted weeks ago and short enough that the cost stays flat.
//
// Only ever deletes a prefix that has its own DONE record older than the
// window — never a folder it does not recognise, and never the newest one.
const BACKUP_RETENTION_DAYS = 30;

exports.pruneOldBackups = onSchedule(
  { schedule: "every day 05:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - BACKUP_RETENTION_DAYS * 24 * 60 * 60 * 1000;
    const old = await db.collection("system_backups")
      .where("state", "==", "DONE")
      .where("finishedAt", "<", cutoff)
      .limit(20).get();
    if (old.empty) return;

    // "never the newest one", which the comment above has always promised and
    // the code never did. After thirty days without a successful export every
    // DONE record is past the cutoff, so this deleted ALL of them — leaving no
    // backup at all, on exactly the day a restore would be needed. The state
    // that produces it is a broken exporter, which is also the state in which
    // nobody is reading the logs.
    const newest = await db.collection("system_backups")
      .where("state", "==", "DONE")
      .orderBy("finishedAt", "desc")
      .limit(1).get();
    const keepId = newest.empty ? "" : newest.docs[0].id;

    const bucketName = BACKUP_BUCKET.replace("gs://", "");
    const bucket = admin.storage().bucket(bucketName);

    for (const doc of old.docs) {
      if (doc.id === keepId) {
        logger.warn("pruneOldBackups: keeping the newest backup though it is past retention — " +
                    "no successful export in " + BACKUP_RETENTION_DAYS + " days", { id: doc.id });
        continue;
      }
      try {
        await bucket.deleteFiles({ prefix: `${doc.id}/`, force: true });
        await doc.ref.update({ state: "PRUNED", prunedAt: Date.now() });
        logger.log(`pruneOldBackups: removed ${doc.id}`);
      } catch (e) {
        logger.error(`pruneOldBackups: could not remove ${doc.id}`, e);
      }
    }
  }
);

// ── reconcileIntegrity ────────────────────────────────────────────────────────
//
// Nothing in this system notices when it has quietly gone wrong.
//
// Every failure mode below has the same shape: two records that should agree
// stop agreeing, and neither side complains, because each one is individually
// valid. A payment settles but the webhook retry that flips the appointment
// never lands; a booking sits in AWAITING_PAYMENT because the customer closed
// the tab; a visit passes and nobody marks it done. Each is invisible until a
// person happens to look at exactly the right row -- usually because a customer
// is already angry.
//
// This looks for the disagreements on a schedule and writes what it finds to
// system_alerts, so the console can show them and the platform learns about its
// own problems before its customers explain them.
//
// Findings only. Nothing here repairs anything on its own: an automatic fix
// applied to a case nobody has understood yet turns one wrong record into two.
const INTEGRITY_LOOKBACK_DAYS = 30;

/** Firestore's `in` operator takes at most 10 values, so queries go in tens. */

function chunkArray(arr, size) {
  const out = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

exports.reconcileIntegrity = onSchedule(
  { schedule: "every day 04:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    // Everything below runs inside a guard, because the sweep failing is itself
    // a critical event and the alert for it used to live in code the failure
    // skipped. This job crashed on a missing index from the day it was written
    // and reported nothing for it — a watchdog that cannot announce its own
    // death is indistinguishable from a system with nothing wrong.
    try {
      return await runIntegritySweep();
    } catch (e) {
      alertable("INTEGRITY_CRITICAL", "reconcileIntegrity itself failed", {
        error: String((e && e.message) || e),
      });
      throw e;
    }
  }
);

async function runIntegritySweep() {
  {
    const now    = Date.now();
    const since  = now - INTEGRITY_LOOKBACK_DAYS * 24 * 60 * 60 * 1000;
    const findings = [];

    const add = (kind, severity, ref, detail) =>
      findings.push({ kind, severity, ref, detail });

    // 1. Paid, but the booking never moved out of AWAITING_PAYMENT.
    //    The customer has been charged and the salon has never seen the request.
    //    This is the one that costs money and trust at the same time.
    const paid = await db.collection("payments")
      .where("status", "==", "PAID").where("createdAt", ">", since).get();
    const paidApptIds = paid.docs
      .map((d) => d.data())
      .filter((p) => p.appointmentId && !p.type)
      .map((p) => p.appointmentId);

    for (const chunk of chunkArray(paidApptIds, 10)) {
      const snap = await db.collection("appointments")
        .where(admin.firestore.FieldPath.documentId(), "in", chunk).get();
      snap.docs.forEach((d) => {
        const a = d.data();
        if (a.status === "AWAITING_PAYMENT") {
          add("PAID_BUT_AWAITING", "critical", a.bookingCode || d.id,
            "Payment settled but the booking never reached the salon.");
        }
      });
    }

    // 2. Bookings stuck in AWAITING_PAYMENT well past any live checkout.
    //    expireStalePayments should have collected these; if they are here, it
    //    did not run or the payment row is missing.
    const stuck = await db.collection("appointments")
      .where("status", "==", "AWAITING_PAYMENT")
      .where("createdAt", "<", now - 24 * 60 * 60 * 1000)
      .limit(50).get();
    stuck.docs.forEach((d) => {
      const a = d.data();
      add("STUCK_AWAITING_PAYMENT", "warn", a.bookingCode || d.id,
        "Awaiting payment for over a day — expiry should have cleared it.");
    });

    // 3. Visits that happened and were never closed. completePastAppointments
    //    flips CONFIRMED past its time; a PENDING one it never touches, so a
    //    booking the salon never accepted just rots.
    const past = await db.collection("appointments")
      .where("status", "==", "PENDING")
      .where("appointmentDate", "<", now - 48 * 60 * 60 * 1000)
      .limit(50).get();
    past.docs.forEach((d) => {
      const a = d.data();
      add("NEVER_ANSWERED", "warn", a.bookingCode || d.id,
        "The visit time passed while the salon had still not accepted or declined.");
    });

    // 4. Refunds nobody has actioned. HesabPay has no automated refund API
    //    wired, so every one of these is a person owed money who is waiting on
    //    a human — and the only thing tracking that human is this list.
    const refunds = await db.collection("refund_requests")
      .where("status", "==", "PENDING")
      .where("createdAt", "<", now - 7 * 24 * 60 * 60 * 1000)
      .limit(50).get();
    refunds.docs.forEach((d) => {
      add("REFUND_OVERDUE", "critical", d.id,
        `Refund pending for more than a week (${Number(d.data().amount || 0)} AFN).`);
    });

    // 5. A backup that is not recent is not a backup.
    const lastGood = await db.collection("system_backups")
      .where("state", "==", "DONE").orderBy("finishedAt", "desc").limit(1).get();
    const lastGoodAt = lastGood.empty ? 0 : Number(lastGood.docs[0].data().finishedAt || 0);
    if (now - lastGoodAt > 48 * 60 * 60 * 1000) {
      add("BACKUP_STALE", "critical", "system_backups",
        lastGoodAt
          ? `Last verified backup finished ${Math.floor((now - lastGoodAt) / 86400000)} days ago.`
          : "No verified backup has ever been recorded.");
    }

    // 6. Negative provider balances. A payout that overshot, or commission debt
    //    that never cleared — either way the arithmetic has drifted.
    // owedAmount, not owed. This has read a field no writer has ever written —
    // Number(undefined || 0) is 0, and 0 is not below 0 — so the only automated
    // guard on the provider ledger has never fired once in its existence. Every
    // balance the arithmetic has ever drifted on was invisible, and a check that
    // cannot fail is indistinguishable from a ledger that never breaks.
    const balances = await db.collection("provider_balances").get();
    balances.docs.forEach((d) => {
      const owed = Number((d.data() || {}).owedAmount || 0);
      if (owed < 0) {
        add("NEGATIVE_BALANCE", "warn", d.id,
          `Provider balance is ${owed} AFN — the platform is owed money by this `
          + "salon, or a payout overshot. A commission debt on an unpaid cash "
          + "booking is normal and clears itself; a large or growing one is not.");
      }
    });

    // 7. Live bookings whose salon no longer exists.
    //
    //    Deleting a salon leaves its appointments behind, which is right for
    //    history — a customer's past visit should not vanish, and the salon name
    //    is stored on the booking so it still reads correctly. A booking that has
    //    not happened yet is a different thing: nobody is going to answer it, and
    //    nobody is watching it. That is not hypothetical, it is what made
    //    nudgeUnconfirmedBookings alert every hour on two June bookings whose
    //    salon had been removed — the nudge had nowhere to send and no way to say
    //    so. Finished bookings are deliberately not flagged; there are 18 of them
    //    in production and they are simply the past.
    const live = await db.collection("appointments")
      .where("status", "in", ["PENDING", "CONFIRMED", "AWAITING_PAYMENT"])
      .limit(200).get();
    const liveSalonIds = [...new Set(
      live.docs.map((d) => String(d.data().salonId || "")).filter(Boolean)
    )];
    if (liveSalonIds.length) {
      const salonDocs = await db.getAll(
        ...liveSalonIds.map((id) => db.doc(`salons/${id}`))
      );
      const missing = new Set(
        salonDocs.filter((d) => !d.exists).map((d) => d.id)
      );
      live.docs.forEach((d) => {
        const a = d.data();
        if (missing.has(String(a.salonId || ""))) {
          add("SALON_GONE", "critical", a.bookingCode || d.id,
            `Booking is still open but its salon (${a.salonName || a.salonId}) no longer exists.`);
        }
      });
    }

    // 8. Salons the derivation could not place confidently.
    //
    //    deriveSalonDiscovery refuses to guess when a service matches no category
    //    or a district could be two places, which is right — but it was reporting
    //    that refusal to a log line, and a log line is not a queue. Nobody read
    //    it, so nobody knew that a salon whose only earning service is called
    //    "mo" appears under no category chip at all: a customer searching for
    //    what it actually does is told there are no providers, while it sits on
    //    the previous screen.
    //
    //    Not critical. Nothing is lost or wrong — it is work waiting for a
    //    person, and paging someone at three in the morning about a category
    //    mapping is how a list gets ignored.
    const unplaced = await db.collection("salons")
      .where("needsDiscoveryReview", "==", true)
      .limit(50).get();
    unplaced.docs.forEach((d) => {
      const r = d.data().discoveryReview || {};
      const bits = [];
      if ((r.unmatchedServices || []).length) {
        bits.push(`services matching no category: ${r.unmatchedServices.join(", ")}`);
      }
      if ((r.districtCandidates || []).length) {
        bits.push(`district could be ${r.districtCandidates.join(" or ")}`);
      }
      add("SALON_NEEDS_REVIEW", "warn", d.data().salonName || d.id,
        bits.join("; ") || "The discovery fields could not be derived confidently.");
    });

    // A salon that is listed, searchable, and cannot be booked on any day.
    //
    // Nothing fails when this happens. Every screen looks right: she appears in
    // search, her profile opens, her services and prices are there — and the
    // date picker offers no times, on any date, forever. Salons were created
    // with an empty week while the provider editor showed a filled-in one, so
    // the owner agreed with what she saw and saved nothing. The two creation
    // paths are fixed and the Health tab can backfill the rest, but a backfill
    // that was never pressed is exactly the kind of absence that survives here
    // for months — so it is watched rather than assumed.
    const listed = await db.collection("salons")
      .where("isAvailable", "==", true)
      .limit(200).get();
    listed.docs.forEach((d) => {
      const salon = d.data() || {};
      if (!hasBookableWeek(salon)) {
        add("SALON_UNBOOKABLE", "critical", salon.salonName || d.id,
          "Listed and searchable, but open on no day of the week — the date "
          + "picker offers nothing. Run \u201cFill in missing opening hours\u201d "
          + "on the Health tab, or ask the owner to set her hours.");
      }
    });

    const critical = findings.filter((f) => f.severity === "critical").length;

    await db.doc(`system_alerts/${new Date(now).toLocaleDateString("en-CA", { timeZone: "Asia/Kabul" })}`)
      .set({
        ranAt: now,
        total: findings.length,
        critical,
        // Bounded: a genuinely broken day could produce thousands, and a
        // document that cannot be written tells nobody anything.
        findings: findings.slice(0, 200),
        truncated: findings.length > 200,
      });

    if (critical > 0) {
      alertable("INTEGRITY_CRITICAL",
        `reconcileIntegrity: ${critical} critical finding(s)`,
        { critical, findings: findings.slice(0, 20) });
    } else {
      logger.log(`reconcileIntegrity: ${findings.length} finding(s), none critical`);
    }
  }
}

// ── Notification localization ─────────────────────────────────────────────────
//
// Every push and in-app notification used to be written in English, to an
// audience that reads Dari and Pashto. The text is composed inside payment and
// booking transactions, so rather than restructure that money-handling code,
// each notification carries an additive `msgKey` + `msgParams`. The single push
// trigger resolves them against the recipient's language — it already reads the
// user document for the FCM token, so localization costs nothing extra.
//
// Docs written before this (or by any path that forgets msgKey) still push their
// stored English title/body, so nothing regresses.
//
// `type` is NOT the key: several distinct messages share type "SYSTEM".

exports.parseGsUri = parseGsUri;

// ── purgeKycImages ────────────────────────────────────────────────────────────
//
// Deletes the photograph and keeps the fact.
//
// This app asks a woman in Afghanistan to photograph her tazkira and her own
// face. Those two images are the most dangerous thing it holds — they are the
// reason every safety rule in this product exists — and they were kept
// forever. On the day this was written there were six of them in production
// for three verified accounts, the oldest fifty-seven days old, and nothing
// anywhere would ever have removed them.
//
// Nothing reads them after review. What opens a booking is `kycStatus`
// on the user document, not the picture. So after a window the objects go and
// `kycStatus` stays: her verification is untouched, and there is simply less
// to lose if this project is ever breached, subpoenaed, or seized.
//
// Never touches PENDING. Deleting those deletes the application itself — the
// admin would open the review and find nothing to look at.
//
// `dryRun` reports what it would delete and deletes nothing, because the first
// run of an irreversible sweep should be readable before it is trusted.
exports.purgeKycImages = onSchedule(
  { schedule: "every day 03:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => { await runKycPurge({ dryRun: false }); }
);

/** The same sweep, on demand, so an admin can see what it would do first. */
exports.adminPurgeKycImages = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const result = await runKycPurge({ dryRun: request.data && request.data.dryRun === true });
  await logAdminAction(me, "PURGE_KYC_IMAGES", result);
  return result;
});

async function runKycPurge({ dryRun }) {
  const bucket = admin.storage().bucket();
  const now = Date.now();

  // Bounded: the whole KYC prefix, which is two objects per account that has
  // ever submitted. If this ever stops being small it needs paging, and the
  // count in the return value is what will say so.
  const [files] = await bucket.getFiles({ prefix: "kyc/", maxResults: 5000 });

  // Group by the uid in kyc/{uid}/{file}.
  const byUid = new Map();
  for (const file of files) {
    const uid = String(file.name).split("/")[1] || "";
    if (!uid) continue;
    if (!byUid.has(uid)) byUid.set(uid, []);
    byUid.get(uid).push(file);
  }

  const purged = [];
  const kept = {};
  for (const [uid, objects] of byUid) {
    const snap = await db.doc(`users/${uid}`).get().catch(() => null);
    const user = snap && snap.exists ? snap.data() : null;

    // No user document at all: the account is gone and the objects outlived
    // it. Age them from the file itself.
    const kycStatus = user ? String(user.kycStatus || "NONE") : "ORPHANED";

    // The recorded decision, or — for accounts reviewed before that field
    // existed — the upload time, which is no later than the review.
    let reviewedAt = Number(user && user.kycReviewedAt) || 0;
    if (!reviewedAt) {
      const created = objects
        .map((f) => Date.parse((f.metadata && f.metadata.timeCreated) || ""))
        .filter((n) => Number.isFinite(n));
      reviewedAt = created.length ? Math.min(...created) : 0;
    }

    const decision = shouldPurge({
      kycStatus: kycStatus === "ORPHANED" ? "REJECTED" : kycStatus,
      reviewedAt,
      now,
      hasImages: objects.length > 0,
    });

    if (!decision.purge) {
      kept[decision.why] = (kept[decision.why] || 0) + 1;
      continue;
    }

    if (!dryRun) {
      for (const file of objects) {
        await file.delete().catch((e) => logger.warn(`purgeKycImages: ${file.name}`, e));
      }
      if (user) {
        // A record that the images are gone, so an admin opening the account
        // is told rather than left wondering whether they failed to upload.
        await db.doc(`users/${uid}`)
          .set({ kycImagesPurgedAt: now }, { merge: true })
          .catch(() => {});
      }
    }
    purged.push({ uid, objects: objects.length, why: decision.why, ageDays: Math.round((now - reviewedAt) / 86400000) });
  }

  const result = {
    dryRun: dryRun === true,
    accountsScanned: byUid.size,
    accountsPurged: purged.length,
    objectsPurged: purged.reduce((n, p) => n + p.objects, 0),
    kept,
    purged,
  };
  logger.log(`purgeKycImages: ${JSON.stringify(result)}`);
  return result;
}
