// maintenance — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { admin, alertable, db, logger } = require("../shared");

exports.cleanupRateLimits = onSchedule(
  { schedule: "every 24 hours", region: "us-central1" },
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
            logger.error(`verifyFirestoreBackup: ${doc.id} failed`, op.error);
          } else {
            await doc.ref.update({ state: "DONE", finishedAt: Date.now(), error: "" });
            logger.log(`verifyFirestoreBackup: ${doc.id} completed`);
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

    const bucketName = BACKUP_BUCKET.replace("gs://", "");
    const bucket = admin.storage().bucket(bucketName);

    for (const doc of old.docs) {
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
    const balances = await db.collection("provider_balances").get();
    balances.docs.forEach((d) => {
      const owed = Number(d.data().owed || 0);
      if (owed < 0) {
        add("NEGATIVE_BALANCE", "warn", d.id, `Provider balance is ${owed} AFN.`);
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
