/**
 * SafeBeauty Cloud Functions — HesabPay payment integration.
 *
 * Why this lives server-side:
 *   The HesabPay API key is a secret that grants access to the platform's
 *   merchant account. It must NEVER ship inside the Android APK (which can be
 *   decompiled). All HesabPay calls happen here, where the key is held as a
 *   Firebase Secret and never leaves Google's servers.
 *
 * Money flow (prepay-at-booking, fixed global commission):
 *   1. Customer picks a service + slot → app calls createPaymentSession().
 *   2. We read the salon's price + the platform commission %, create an
 *      appointment in AWAITING_PAYMENT, create a HesabPay checkout session,
 *      and return the checkout URL to the app.
 *   3. Customer pays in the HesabPay web checkout.
 *   4. HesabPay calls hesabPayWebhook() → we verify the signature, mark the
 *      payment PAID, split the amount (platform commission vs. provider net),
 *      flip the appointment to PENDING so the provider can confirm it, and
 *      notify the provider.
 *
 * Configuration (set before deploy):
 *   firebase functions:secrets:set HESAB_API_KEY
 *   firebase functions:secrets:set HESAB_WEBHOOK_SECRET
 *   # Optional non-secret base URL via functions/.env:
 *   #   HESAB_BASE_URL=https://api.hesab.com/api/v1
 *
 * HesabPay API — confirmed live field names (create-session):
 *   Request  : items[]{id, name, price}, email, redirect_success_url, redirect_failure_url
 *   Response : { success, session_id, url, expires_at } — checkout link is `url`
 *   Webhook  : lookup by session_id stored at payment creation time
 */

const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated, onDocumentWritten, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { defineSecret, defineString } = require("firebase-functions/params");
// admin, db, logger and alertable now live in shared.js, which runs
// initializeApp() exactly once on first require. Every domain module takes them
// from the same place, so they all address the same Firestore.
const {
  admin, db, logger, alertable,
  assertDocId, resolveAppUser, cleanPhone,
  normalizeAfghanPhone, normalizePhone, assertAdmin,
  assertNotSuspended, logAdminAction, appointmentEvent,
  writeAppointmentEvent, logAppointmentEvent,
  refundReservation, randomBookingCode, reserveBookingCode,
  pbkdf2Hash,
} = require("./shared");
const crypto = require("crypto");
const { promoDiscountFor, computeCheckout, resolveServicesTotal, validateGiftAmount, loyaltyToCredit, offerDiscountFor, lastMinuteDiscount, packageDiscountFor } = require("./lib/money");
const { expandBooked, serviceSlotSpan, hasSlotConflict } = require("./lib/slots");
const { isPaidSignal, isFailSignal, isUnderpaid } = require("./lib/webhook");
const { isValidDocId } = require("./lib/validate");
const { averageRating } = require("./lib/reviews");
const { bookingCodeFromBytes, normalizeBookingCode } = require("./lib/booking");
const { phoneKey } = require("./lib/phone");
const { categoriesFor, normalize: categoryNormalize } = require("./lib/categories");
const { normalizeDistrict } = require("./lib/areas");
const { SlotTakenError, pendingWrites, commitBookingAtomically, slotConflictWindow } = require("./lib/reservation");







// ── Helpers ──────────────────────────────────────────────────────────────────






// ── createPaymentSession (callable) ──────────────────────────────────────────







// ── Domain modules ────────────────────────────────────────────────────────────
//
// Firebase discovers functions by enumerating this file's exports, so a module
// that is not re-exported here is simply not deployed. Named rather than spread,
// so the deployed set stays explicit and greppable.
const admin_ = require("./domains/admin");
exports.grantAdmin                 = admin_.grantAdmin;
exports.revokeAdmin                = admin_.revokeAdmin;
exports.adminResetPassword         = admin_.adminResetPassword;
exports.adminUpdateUser            = admin_.adminUpdateUser;
exports.adminAdjustProviderBalance = admin_.adminAdjustProviderBalance;
exports.adminGrantCredit           = admin_.adminGrantCredit;
exports.adminCreateSalon           = admin_.adminCreateSalon;
exports.adminSetUserStatus         = admin_.adminSetUserStatus;
exports.adminUserDossier           = admin_.adminUserDossier;
exports.adminTestAlert             = admin_.adminTestAlert;
exports.resolveCustomerReport      = admin_.resolveCustomerReport;

const identity = require("./domains/identity");
exports.authenticateWithPassword = identity.authenticateWithPassword;
exports.syncUidMap               = identity.syncUidMap;
exports.updatePinHash            = identity.updatePinHash;
exports.submitKyc                = identity.submitKyc;
exports.reviewKyc                = identity.reviewKyc;
exports.createProviderSalon      = identity.createProviderSalon;
exports.lookupAccountByPhone     = identity.lookupAccountByPhone;
exports.requestAccountDeletion   = identity.requestAccountDeletion;
exports.adminBackfillPhoneKeys   = identity.adminBackfillPhoneKeys;

const bookings = require("./domains/bookings");
exports.cancelAppointment          = bookings.cancelAppointment;
exports.providerDeclineAppointment = bookings.providerDeclineAppointment;
exports.getBookedSlots             = bookings.getBookedSlots;
exports.rescheduleAppointment      = bookings.rescheduleAppointment;
exports.confirmAppointment         = bookings.confirmAppointment;
exports.reportCustomer             = bookings.reportCustomer;
exports.sendBookingReminders       = bookings.sendBookingReminders;
exports.completePastAppointments   = bookings.completePastAppointments;
exports.nudgeUnconfirmedBookings   = bookings.nudgeUnconfirmedBookings;
exports.rotateWaitlistOffers       = bookings.rotateWaitlistOffers;
exports.adminLookupBooking         = bookings.adminLookupBooking;
exports.adminBackfillBookingCodes  = bookings.adminBackfillBookingCodes;

const payments = require("./domains/payments");
exports.createPaymentSession    = payments.createPaymentSession;
exports.createGiftCardSession   = payments.createGiftCardSession;
exports.createWalletTopUp       = payments.createWalletTopUp;
exports.createTipSession        = payments.createTipSession;
exports.hesabPayWebhook         = payments.hesabPayWebhook;
exports.expireAbandonedPayments = payments.expireAbandonedPayments;
exports.recordProviderPayout    = payments.recordProviderPayout;
exports.recordRefundProcessed   = payments.recordRefundProcessed;
exports.adminStuckPayments      = payments.adminStuckPayments;
exports.adminSettleStuckPayment = payments.adminSettleStuckPayment;
exports.redeemLoyaltyPoints     = payments.redeemLoyaltyPoints;
exports.claimProfileReward      = payments.claimProfileReward;
exports.previewPromo            = payments.previewPromo;
exports.upsertPromoCode         = payments.upsertPromoCode;
exports.setPromoActive          = payments.setPromoActive;

// Same guard as the domain: present for the integration tests, invisible to the
// deploy analyser otherwise.
if (process.env.SAFEBEAUTY_TEST_HOOKS === "1") {
  exports.__testHooks = payments.__testHooks;
}

const content = require("./domains/content");
exports.pushPostToFollowers  = content.pushPostToFollowers;
exports.countPostLike        = content.countPostLike;
exports.countPostComment     = content.countPostComment;
exports.cleanupDeletedPost   = content.cleanupDeletedPost;
exports.adminPostForSalon    = content.adminPostForSalon;
exports.cleanupExpiredStories = content.cleanupExpiredStories;
exports.submitReview         = content.submitReview;
exports.awardReviewPoints    = content.awardReviewPoints;
exports.pushOfferToFavoriters = content.pushOfferToFavoriters;

const maintenance = require("./domains/maintenance");
exports.cleanupRateLimits        = maintenance.cleanupRateLimits;
exports.scheduledFirestoreBackup = maintenance.scheduledFirestoreBackup;
exports.verifyFirestoreBackup    = maintenance.verifyFirestoreBackup;
exports.pruneOldBackups          = maintenance.pruneOldBackups;
exports.reconcileIntegrity       = maintenance.reconcileIntegrity;


// ── Server-side PIN authentication ────────────────────────────────────────────
//
// Why this is server-side:
//   Verifying client-side would require downloading the ENTIRE users
//   collection (every pinHash + salt) to the device — meaning anyone who
//   reached the login screen could exfiltrate the whole credential table and
//   brute-force 6-digit PINs offline. Login sends only the PIN here; the
//   hashes never leave the server. Firestore rules lock `users` reads to the
//   owner/admin, so the table can no longer be dumped.

















// Nudge lapsed customers back. A customer whose last completed visit is older
// than 30 days gets a one-time "we miss you" notification (→ FCM via
// pushOnNotificationCreated), at most once per 30 days (lastNudgedAt cooldown).
// Only people who have actually visited (lastVisitAt set) are ever nudged.
const REENGAGE_AFTER_MS    = 30 * 24 * 60 * 60 * 1000;
const REENGAGE_COOLDOWN_MS = 30 * 24 * 60 * 60 * 1000;

exports.sendReengagementNudges = onSchedule(
  { schedule: "every 24 hours", region: "us-central1" },
  async () => {
    const now = Date.now();
    const lapsed = await db.collection("users")
      .where("lastVisitAt", "<=", now - REENGAGE_AFTER_MS)
      .get();
    if (lapsed.empty) return;

    let count = 0;
    for (const doc of lapsed.docs) {
      const u = doc.data();
      if (Number(u.lastNudgedAt || 0) > now - REENGAGE_COOLDOWN_MS) continue; // cooldown
      const batch = db.batch();
      batch.update(doc.ref, { lastNudgedAt: now });
      batch.set(db.collection("notifications").doc(), {
        recipientId: doc.id,
        type:        "REENGAGEMENT",
        msgKey:      "REENGAGEMENT",
        msgParams:   {},
        title:       "We miss you 💕",
        body:        "It's been a while — book your next beauty appointment on SafeBeauty.",
        isRead:      false,
        createdAt:   now,
        relatedId:   "",
      });
      await batch.commit();
      count++;
    }
    logger.log(`sendReengagementNudges: nudged ${count} lapsed customer(s)`);
  }
);

// ── recordProviderPayout (callable, admin-only) ───────────────────────────────

/**
 * Records that the platform has paid a provider their owed balance (cash,
 * HesabPay transfer, etc. — settled outside the app). Atomically writes a
 * `payouts` history row and resets provider_balances/{providerId}.owedAmount
 * to 0. Admin-only; clients can't touch provider_balances directly.
 */


// ── Promo codes ───────────────────────────────────────────────────────────────





// ── pushOnNotificationCreated (Firestore trigger) ─────────────────────────────
//
// Single delivery point for real push notifications: every notifications/{id}
// doc — written by the functions above AND by client-side flows (booking
// confirmed, waitlist slot available) — becomes an FCM push to the recipient's
// registered device. Without this, "notifications" only ever appeared inside
// the app's Notification Center while it was open; a salon would never learn
// about a new paid booking until they happened to open the app.
exports.pushOnNotificationCreated = onDocumentCreated(
  { document: "notifications/{notifId}", region: "us-central1" },
  async (event) => {
    const n = event.data ? event.data.data() : null;
    if (!n || !n.recipientId || !n.title) return;

    const userSnap = await db.doc(`users/${n.recipientId}`).get();
    const token = userSnap.exists ? String(userSnap.data().fcmToken || "") : "";
    if (!token) return; // user never logged in on a push-capable device

    // The user doc is already loaded for the token, so reading their language
    // here is free. Falls back to the stored English text for older docs.
    const lang = userSnap.exists ? String(userSnap.data().lang || "") : "";
    const { title, body } = localizeNotification(n, lang);

    try {
      await admin.messaging().send({
        token,
        notification: { title, body },
        // Duplicated under both key styles: the foreground handler
        // (SafeBeautyMessagingService) reads "type"/"relatedId", while a tap on
        // a background notification delivers data keys as raw intent extras,
        // where MainActivity expects "notif_type"/"notif_related_id".
        data: {
          type:             String(n.type || ""),
          relatedId:        String(n.relatedId || ""),
          notif_type:       String(n.type || ""),
          notif_related_id: String(n.relatedId || ""),
        },
        android: { priority: "high" },
      });
    } catch (err) {
      // Expired/rotated tokens are routine — log and move on, never retry-loop.
      logger.warn("pushOnNotificationCreated: send failed", {
        recipientId: n.recipientId,
        error: String(err.message || err),
      });
    }
  }
);

// ── pushOnBroadcastCreated (Firestore trigger) ────────────────────────────────
//
// An admin broadcast is a platform-wide announcement, so it fans out as an FCM
// push to every user with a registered device — the message reaches people even
// when the app is closed (the in-app banner/popup only shows while it's open).
// FCM multicast is capped at 500 tokens per call, so we chunk.
exports.pushOnBroadcastCreated = onDocumentCreated(
  { document: "broadcasts/{bId}", region: "us-central1" },
  async (event) => {
    const b = event.data ? event.data.data() : null;
    if (!b || !b.message) return;

    // Optional audience filters set by the admin console. Absent/blank means
    // "everyone", so existing broadcasts keep their old platform-wide behaviour.
    const wantRole = String(b.targetRole || "").toUpperCase();      // CUSTOMER | PROVIDER
    const wantLang = String(b.targetLang || "").toLowerCase();      // fa | ps | en
    const wantDistrict = String(b.targetDistrict || "");            // salon district

    // A district filter only makes sense for providers, and their district lives
    // on the salon rather than the user, so resolve those owners first.
    let districtOwners = null;
    if (wantDistrict) {
      districtOwners = new Set();
      const sSnap = await db.collection("salons").where("district", "==", wantDistrict).get();
      sSnap.forEach((d) => { const pid = d.data().providerId; if (pid) districtOwners.add(pid); });
      if (districtOwners.size === 0) {
        logger.log(`pushOnBroadcastCreated: no salons in ${wantDistrict}, nothing sent`);
        return;
      }
    }

    // Paged rather than read-all. This used to load every matching user into one
    // invocation and send 500 at a time in sequence -- which at 100,000 users is
    // 100,000 reads and roughly two hundred sequential sends, comfortably past
    // the function timeout. It would then stop halfway with no record of where,
    // so the broadcast could be neither resumed nor safely retried: retrying
    // would message everyone it had already reached a second time.
    //
    // Progress is recorded on the broadcast document, and resumeBroadcasts
    // continues anything left unfinished. Invariant Q-4.
    await event.data.ref.set({
      sendState: "SENDING",
      sentCount: 0,
      startedAt: Date.now(),
    }, { merge: true });

    await deliverBroadcast(event.data.ref, b, districtOwners);
  }
);

/** How many recipients one invocation will attempt before handing over. */
const BROADCAST_PAGE = 500;
const BROADCAST_MAX_PAGES_PER_RUN = 6;

/**
 * Send a broadcast from where it left off, and record how far it got.
 *
 * Bounded per invocation so it always finishes well inside the timeout, leaving
 * a cursor rather than an unknown state. Called on creation and again by
 * resumeBroadcasts until the state reaches DONE.
 */
async function deliverBroadcast(ref, b, districtOwners) {
  const wantRole = String(b.targetRole || "").toUpperCase();
  const wantLang = String(b.targetLang || "").toLowerCase();

  const title = "SafeBeauty";
  const body  = String(b.message);
  const data  = { type: "BROADCAST", relatedId: "", notif_type: "BROADCAST", notif_related_id: "" };

  let cursorId = String(b.sendCursor || "");
  let sent     = Number(b.sentCount || 0);
  let pages    = 0;

  while (pages < BROADCAST_MAX_PAGES_PER_RUN) {
    let q = db.collection("users").orderBy(admin.firestore.FieldPath.documentId());
    if (wantRole) q = q.where("role", "==", wantRole);
    if (wantLang) q = q.where("lang", "==", wantLang);
    if (cursorId) q = q.startAfter(cursorId);

    const page = await q.limit(BROADCAST_PAGE).get();
    if (page.empty) {
      await ref.set({ sendState: "DONE", sentCount: sent, finishedAt: Date.now() }, { merge: true });
      logger.log(`deliverBroadcast: finished, ${sent} device(s)`);
      return;
    }

    const tokens = [];
    page.docs.forEach((doc) => {
      if (districtOwners && !districtOwners.has(doc.id)) return;
      const t = String((doc.data() || {}).fcmToken || "");
      if (t) tokens.push(t);
    });

    if (tokens.length) {
      try {
        await admin.messaging().sendEachForMulticast({
          tokens, notification: { title, body }, data,
          android: { priority: "high" },
        });
        sent += tokens.length;
      } catch (err) {
        logger.warn("deliverBroadcast: batch send failed", { error: String(err.message || err) });
      }
    }

    cursorId = page.docs[page.docs.length - 1].id;
    pages += 1;

    // Written every page, not at the end: an invocation killed mid-run must
    // leave behind where it actually got to.
    await ref.set({ sendState: "SENDING", sentCount: sent, sendCursor: cursorId }, { merge: true });

    if (page.size < BROADCAST_PAGE) {
      await ref.set({ sendState: "DONE", sentCount: sent, finishedAt: Date.now() }, { merge: true });
      logger.log(`deliverBroadcast: finished, ${sent} device(s)`);
      return;
    }
  }

  logger.log(`deliverBroadcast: paused after ${sent} device(s), will resume`);
}

// Continues any broadcast that did not finish in one invocation.
exports.resumeBroadcasts = onSchedule(
  { schedule: "every 5 minutes", region: "us-central1" },
  async () => {
    const stuck = await db.collection("broadcasts")
      .where("sendState", "==", "SENDING")
      .limit(3)
      .get();
    if (stuck.empty) return;

    for (const doc of stuck.docs) {
      const b = doc.data() || {};
      // The district filter has to be rebuilt, since it is derived rather than
      // stored on the broadcast.
      let districtOwners = null;
      const wantDistrict = String(b.targetDistrict || "");
      if (wantDistrict) {
        districtOwners = new Set();
        const sSnap = await db.collection("salons").where("district", "==", wantDistrict).get();
        sSnap.forEach((d) => { const pid = (d.data() || {}).providerId; if (pid) districtOwners.add(pid); });
      }
      await deliverBroadcast(doc.ref, b, districtOwners);
    }
  }
);






// ── Admin: onboard a salon directly ───────────────────────────────────────────
//
// A marketplace has a cold-start problem: salon owners in Kabul are signed up in
// person, not by self-registration. This lets the admin create the owner's
// account AND their salon in one step, so a salon can be live before its owner
// ever opens the app. The owner signs in afterwards with the phone + password
// the admin sets here.
//
// Ordering is deliberate: Auth account -> user doc -> salon doc, with rollback
// of the Auth account if either write fails. A stranded Auth account with no
// user doc permanently bricks that phone number (every retry hits
// "email-already-exists" while login-by-phone finds nothing).














// ── measureSalonReliability ───────────────────────────────────────────────────
//
// How dependably a salon answers the bookings it receives, counted from what is
// already recorded.
//
// This is measurement, not ranking. It stores counts and a rate; it does not
// order anyone or decide who gets shown first. That distinction is the whole
// reason it can be built now while D-6 keeps ranking closed: counting facts a
// salon produced is defensible at any scale, whereas weighting those facts into
// a position — which allocates real income between real businesses — needs data
// this platform does not yet have.
//
// It is also the input P8 will need whenever its gate opens, and it needs weeks
// of history to mean anything, so starting the clock now is the point.
//
// Deliberately excludes the customer's own behaviour. A salon is not less
// reliable because a customer cancelled, and folding that in would let a run of
// unlucky customers damage a salon's standing.
const RELIABILITY_WINDOW_DAYS = 90;

exports.measureSalonReliability = onSchedule(
  { schedule: "every day 02:30", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const since = Date.now() - RELIABILITY_WINDOW_DAYS * 24 * 60 * 60 * 1000;
    const salons = await db.collection("salons").limit(500).get();

    for (const salonDoc of salons.docs) {
      const appts = await db.collection("appointments")
        .where("salonId", "==", salonDoc.id)
        .where("createdAt", ">", since)
        .limit(1000)
        .get();

      // Which cancellations this salon actually made. The trail denormalizes
      // salonId, so this is one query rather than a read per booking.
      const declined = new Set();
      const events = await db.collection("appointment_events")
        .where("salonId", "==", salonDoc.id)
        .where("to", "==", "CANCELLED")
        .limit(1000)
        .get();
      events.docs.forEach((e) => {
        const ev = e.data() || {};
        if (ev.actorRole === "PROVIDER" && ev.appointmentId) declined.add(ev.appointmentId);
      });

      let answered = 0;      // the salon acted: confirmed, or declined
      let confirmed = 0;
      let unanswered = 0;    // the visit time passed with the salon silent

      appts.docs.forEach((d) => {
        const a = d.data() || {};
        const status = a.status || "";
        if (status === "CONFIRMED" || status === "COMPLETED") {
          answered += 1; confirmed += 1;
        } else if (status === "CANCELLED") {
          // A cancellation counts against the salon only when the salon made
          // it. A customer changing her mind, or a checkout expiring, says
          // nothing about how dependably this salon answers — and counting it
          // would let a run of ordinary customer cancellations damage a salon's
          // standing for something it did not do. Anything the trail cannot
          // attribute is left out of both halves of the ratio rather than
          // guessed at.
          if (declined.has(d.id)) answered += 1;
        } else if (status === "PENDING" && Number(a.appointmentDate || 0) < Date.now()) {
          unanswered += 1;
        }
      });

      const decided = answered + unanswered;
      const reliability = {
        window: RELIABILITY_WINDOW_DAYS,
        bookings: appts.size,
        confirmed,
        unanswered,
        // Null rather than a flattering 1.0 when there is nothing to judge. A
        // brand new salon has not earned a perfect record, and showing one would
        // be a claim the data does not support.
        confirmRate: decided > 0 ? Math.round((confirmed / decided) * 100) : null,
        measuredAt: Date.now(),
      };

      // Compare every value that is stored, not a subset of them. A guard that
      // checks three of four fields silently pins the fourth: when the counts
      // held steady but the rate changed, the new rate was computed and thrown
      // away, and the salon kept a stale figure with no sign anything was wrong.
      const prev = (salonDoc.data() || {}).reliability || {};
      const changed = ["bookings", "confirmed", "unanswered", "confirmRate", "window"]
        .some((k) => prev[k] !== reliability[k]);
      if (changed) {
        await salonDoc.ref.update({ reliability });
      }
    }

    logger.log(`measureSalonReliability: measured ${salons.size} salon(s)`);
  }
);

// ── adminDemandReport ─────────────────────────────────────────────────────────
//
// Which district wants which service, and whether there is anyone there to
// serve it. This is the question the whole demand-capture exists to answer:
// with two live salons and five hundred as the target, the binding constraint
// is supply, and until now which salon to recruit next was a guess.
//
// Demand and supply are counted from different collections and joined here, so
// the console does not have to know how either is stored. Signals carry no
// identity, so everything below is genuinely aggregate — there is no per-person
// view to accidentally build on top of it.
exports.adminDemandReport = onCall({ region: "us-central1" }, async (request) => {
  await assertAdmin(request);
  const days = Math.min(90, Math.max(1, Number((request.data || {}).days || 30)));
  const since = Date.now() - days * 24 * 60 * 60 * 1000;

  const signals = await db.collection("demand_signals")
    .where("at", ">", since)
    .orderBy("at", "desc")
    .limit(2000)
    .get();

  // demand[districtKey][category] = { total, byKind }
  const demand = new Map();
  const bump = (district, category, kind) => {
    const dk = district || "(unspecified)";
    const ck = category || "(any)";
    if (!demand.has(dk)) demand.set(dk, new Map());
    const row = demand.get(dk);
    if (!row.has(ck)) row.set(ck, { total: 0, byKind: {} });
    const cell = row.get(ck);
    cell.total += 1;
    cell.byKind[kind] = (cell.byKind[kind] || 0) + 1;
  };

  signals.docs.forEach((d) => {
    const s = d.data() || {};
    bump(s.districtKey, s.category, s.kind || "UNKNOWN");
  });

  // Supply, from the derived fields the discovery queries already use, so the
  // two halves of this report agree with what a customer actually sees.
  const salons = await db.collection("salons").limit(500).get();
  const supply = new Map();   // districtKey -> { total, byCategory }
  salons.docs.forEach((d) => {
    const s = d.data() || {};
    if (s.isAvailable !== true) return;
    const dk = s.districtKey || "(unspecified)";
    if (!supply.has(dk)) supply.set(dk, { total: 0, byCategory: {} });
    const row = supply.get(dk);
    row.total += 1;
    (Array.isArray(s.categories) ? s.categories : []).forEach((c) => {
      row.byCategory[c] = (row.byCategory[c] || 0) + 1;
    });
  });

  const rows = [];
  for (const [districtKey, categories] of demand) {
    for (const [category, cell] of categories) {
      const sup = supply.get(districtKey);
      const salonsHere = category === "(any)"
        ? (sup ? sup.total : 0)
        : (sup ? (sup.byCategory[category] || 0) : 0);
      rows.push({
        districtKey,
        category,
        demand: cell.total,
        byKind: cell.byKind,
        salons: salonsHere,
        // The number that ranks recruitment: demand with nobody to serve it is
        // worth more attention than demand a salon is already meeting.
        unmet: salonsHere === 0 ? cell.total : 0,
      });
    }
  }

  rows.sort((a, b) => (b.unmet - a.unmet) || (b.demand - a.demand));

  return {
    ok: true,
    days,
    signalCount: signals.size,
    truncated: signals.size >= 2000,
    supplyByDistrict: [...supply].map(([districtKey, v]) => ({ districtKey, ...v })),
    rows,
  };
});

// ── Stuck payments: detection automatic, correction human ────────────────────
//
// The webhook verifies each payload by calling HesabPay. If that endpoint is
// unreachable, every webhook is rejected: the money settles at HesabPay and the
// booking never reaches the salon. The customer has paid for an appointment
// nobody knows about.
//
// Repairing that automatically would mean asking HesabPay whether a given
// payment succeeded, and this integration knows exactly two of their endpoints
// -- create-session and verify-signature. There is no status endpoint here to
// call, and guessing one against a payment provider is not a thing to do. So
// the platform finds these, presents everything needed to check them against
// the HesabPay dashboard, and a person decides. Invariant I-15.
//
// When a status endpoint is available, the automatic version calls exactly the
// same settlement this does, and this screen becomes the fallback.



// ── Salon discovery fields ────────────────────────────────────────────────────
//
// The customer app filters salons by category and neighbourhood. Both filters
// have been broken since they were written, because nothing ever connected what
// a salon types to what the filter looks for:
//
//   services  is free text ("ناخن"), the chip compares an English key ("Nails")
//   district  is free text on older salons, the filter wants a canonical key
//
// Neither failure is visible. Both look exactly like a filter working correctly
// on an empty result, and a customer concludes there are no nail salons in
// Kabul while one sits on the previous screen offering nails.
//
// These derived fields are what the server can actually index and query:
//
//   categories   string[]  canonical category keys, from lib/categories
//   districtKey  string    canonical area key, from lib/areas ("" when unresolved)
//
// The originals are never overwritten. `services` and `district` remain exactly
// what the salon entered, so nothing here can lose data a salon typed, and the
// derivation can be changed and re-run.

// A salon with no priced service still has to appear in a price-sorted list,
// and it belongs at the end rather than nowhere. Firestore EXCLUDES documents
// that lack the orderBy field entirely, so "no price" must be a number, not an
// absent field — otherwise sorting by price silently hides salons, which is the
// same invisible-empty failure this whole phase exists to fix.
const NO_PRICE = 9999999;

/** Lowest priced service, or NO_PRICE when the salon has priced nothing. */
function salonMinPrice(salon) {
  const prices = (salon && salon.pricePerService) || {};
  const values = Object.values(prices)
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0);
  return values.length ? Math.min(...values) : NO_PRICE;
}

/** What the derived fields should be for a salon, given what it stores. */
function deriveSalonDiscovery(salon) {
  const { categories, unmatched } = categoriesFor(salon && salon.services);
  const area = normalizeDistrict(salon && salon.district);
  return {
    categories,
    districtKey: area.key || "",
    // Prefix-searchable form of the name. Firestore cannot match a substring,
    // but a range on a normalized name gives prefix search, which is what a
    // customer typing the start of a salon name actually needs.
    nameKey: categoryNormalize(salon && salon.salonName),
    minPrice: salonMinPrice(salon),
    // Always written, never inherited from the document: a salon that has never
    // been rated must still be orderable by rating.
    sortRating: Number((salon && salon.rating) || 0),
    // Not stored — returned so the caller can report what a human needs to look at.
    unmatchedServices: unmatched,
    districtCandidates: area.candidates || [],
  };
}

/** The subset of derived values that actually get stored on the document. */
function storedDiscoveryFields(derived) {
  return {
    categories:  derived.categories,
    districtKey: derived.districtKey,
    nameKey:     derived.nameKey,
    minPrice:    derived.minPrice,
    sortRating:  derived.sortRating,
  };
}

/** True when the stored derived fields already equal the freshly derived ones. */
function discoveryUpToDate(salon, derived) {
  const stored = Array.isArray(salon.categories) ? salon.categories : [];
  return stored.length === derived.categories.length
      && stored.every((c, i) => c === derived.categories[i])
      && (salon.districtKey || "") === derived.districtKey
      && (salon.nameKey || "") === derived.nameKey
      && Number(salon.minPrice) === derived.minPrice
      && Number(salon.sortRating) === derived.sortRating;
}

// Keeps the derived fields correct as salons edit themselves, so the backfill is
// a one-time repair rather than a permanent chore.
//
// This writes back to the document it is triggered by, which re-triggers it once.
// The up-to-date check is what stops that being a loop: the second invocation
// finds nothing to change and returns.
exports.deriveSalonFields = onDocumentWritten(
  { document: "salons/{salonId}", region: "us-central1" },
  async (event) => {
    const after = event.data && event.data.after;
    if (!after || !after.exists) return;

    const salon = after.data() || {};
    const derived = deriveSalonDiscovery(salon);
    if (discoveryUpToDate(salon, derived)) return;

    await after.ref.update(storedDiscoveryFields(derived));

    if (derived.unmatchedServices.length || derived.districtCandidates.length) {
      logger.warn("deriveSalonFields: needs human review", {
        salonId: event.params.salonId,
        unmatchedServices: derived.unmatchedServices,
        districtCandidates: derived.districtCandidates,
      });
    }
  }
);

// Runs the same derivation on a schedule, so existing salons are repaired
// without anyone remembering to press a button.
//
// The customer's discovery queries order by sortRating or minPrice, and
// Firestore returns NO documents that lack the ordering field — not documents
// sorted last, none at all. That makes the backfill a hard dependency of the
// read path rather than a nice-to-have, and a hard dependency that waits on a
// human is the deploy step invariant I-12 exists to forbid.
//
// Idempotent, and cheap when there is nothing to do: it writes only where the
// derived values differ from what is stored.
exports.normalizeSalonsDaily = onSchedule(
  { schedule: "every day 01:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const snap = await db.collection("salons").limit(500).get();
    let updated = 0;
    const review = [];

    for (const d of snap.docs) {
      const salon = d.data() || {};
      const derived = deriveSalonDiscovery(salon);
      if (!discoveryUpToDate(salon, derived)) {
        await d.ref.update(storedDiscoveryFields(derived));
        updated += 1;
      }
      if (derived.unmatchedServices.length || derived.districtCandidates.length) {
        review.push({
          salonId: d.id,
          unmatchedServices: derived.unmatchedServices,
          districtCandidates: derived.districtCandidates,
        });
      }
    }

    logger.log(`normalizeSalonsDaily: scanned ${snap.size}, updated ${updated}, ${review.length} need review`);
    if (review.length) logger.warn("normalizeSalonsDaily: needs human review", { review: review.slice(0, 20) });
  }
);

// ── adminNormalizeSalons ──────────────────────────────────────────────────────
//
// Backfills the derived fields for salons that predate them, and reports what it
// could not resolve.
//
// The report is the valuable half. A service it cannot categorise means the
// vocabulary needs a synonym; a district with two candidates means a human must
// choose. Neither is guessed: filing a salon under a category it does not serve,
// or in a neighbourhood it is not in, is worse than leaving it unfiltered,
// because the customer only finds out by turning up.
exports.adminNormalizeSalons = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const limit = Math.min(500, Math.max(1, Number((request.data || {}).limit || 300)));

  const snap = await db.collection("salons").orderBy("createdAt", "asc").limit(limit).get();

  let updated = 0;
  const needsReview = [];

  for (const d of snap.docs) {
    const salon = d.data() || {};
    const derived = deriveSalonDiscovery(salon);

    if (!discoveryUpToDate(salon, derived)) {
      await d.ref.update(storedDiscoveryFields(derived));
      updated += 1;
    }

    if (derived.unmatchedServices.length || derived.districtCandidates.length) {
      needsReview.push({
        salonId:   d.id,
        salonName: salon.salonName || "",
        district:  salon.district || "",
        unmatchedServices:  derived.unmatchedServices,
        districtCandidates: derived.districtCandidates,
      });
    }
  }

  await logAdminAction(me, "NORMALIZE_SALONS", {
    scanned: snap.size, updated, needsReview: needsReview.length,
  });

  return {
    ok: true,
    scanned: snap.size,
    updated,
    needsReview,
    done: snap.size < limit,
  };
});



const NOTIF_I18N = {
  NEW_BOOKING_CASH: {
    en: { t: "New Cash Booking",  b: (p) => `${p.service} — AFN ${p.price} to collect in person` },
    fa: { t: "رزرو نقدی جدید",     b: (p) => `${p.service} — ${p.price} افغانی نقدی دریافت کنید` },
    ps: { t: "نوی نغدي بکینګ",     b: (p) => `${p.service} — ${p.price} افغانۍ په نغدو واخلئ` },
  },
  NEW_BOOKING_PAID: {
    en: { t: "New Paid Booking",  b: (p) => `${p.service} — paid AFN ${p.amount}` },
    fa: { t: "رزرو پرداخت‌شده جدید", b: (p) => `${p.service} — ${p.amount} افغانی پرداخت شد` },
    ps: { t: "نوی تادیه شوی بکینګ", b: (p) => `${p.service} — ${p.amount} افغانۍ تادیه شوې` },
  },
  BOOKING_CONFIRMED: {
    en: { t: "Booking Confirmed", b: (p) => `${p.service} at ${p.salon}` },
    fa: { t: "رزرو تأیید شد",      b: (p) => `${p.service} در ${p.salon}` },
    ps: { t: "بکینګ تایید شو",     b: (p) => `${p.service} په ${p.salon} کې` },
  },
  BOOKING_CANCELLED_BY_CUSTOMER: {
    en: { t: "Booking Cancelled", b: (p) => `${p.service} was cancelled by the customer.` },
    fa: { t: "رزرو لغو شد",        b: (p) => `${p.service} توسط مشتری لغو شد.` },
    ps: { t: "بکینګ لغوه شو",      b: (p) => `${p.service} د پیرودونکي لخوا لغوه شو.` },
  },
  BOOKING_DECLINED: {
    en: { t: "Booking Declined",  b: (p) => `${p.service} at ${p.salon} was declined.` },
    fa: { t: "رزرو رد شد",         b: (p) => `${p.service} در ${p.salon} رد شد.` },
    ps: { t: "بکینګ رد شو",        b: (p) => `${p.service} په ${p.salon} کې رد شو.` },
  },
  BOOKING_RESCHEDULED: {
    en: { t: "Booking Rescheduled", b: (p) => `${p.service} was moved to a new time — please re-confirm.` },
    fa: { t: "رزرو جابه‌جا شد",      b: (p) => `${p.service} به زمان جدیدی منتقل شد — لطفاً دوباره تأیید کنید.` },
    ps: { t: "بکینګ بدل شو",         b: (p) => `${p.service} نوي وخت ته ولیږدول شو — مهرباني وکړئ بیا یې تایید کړئ.` },
  },
  BOOKING_REMINDER: {
    en: { t: "Upcoming Appointment", b: (p) => `${p.service} at ${p.salon} is coming up soon.` },
    fa: { t: "نوبت پیشِ‌رو",          b: (p) => `${p.service} در ${p.salon} به‌زودی است.` },
    ps: { t: "راتلونکی نوبت",         b: (p) => `${p.service} په ${p.salon} کې ډېر ژر دی.` },
  },
  WAITLIST_SLOT: {
    en: { t: "A slot opened up 🎉", b: (p) => `${p.salon} has a free slot on your waitlisted day — book it before it's gone!` },
    fa: { t: "یک نوبت خالی شد 🎉",   b: (p) => `${p.salon} در روزی که در لیست انتظار بودید جای خالی دارد — قبل از پر شدن رزرو کنید!` },
    ps: { t: "یو ځای خالي شو 🎉",    b: (p) => `${p.salon} په هغه ورځ کې چې د انتظار لیست کې وئ خالي ځای لري — د ډکېدو دمخه یې ونیسئ!` },
  },
  POINTS_REDEEMED: {
    en: { t: "Points redeemed 🎉", b: (p) => `You turned ${p.spend} points into AFN ${p.credit} of wallet credit.` },
    fa: { t: "امتیازها تبدیل شد 🎉", b: (p) => `${p.spend} امتیاز را به ${p.credit} افغانی اعتبار تبدیل کردید.` },
    ps: { t: "ټکي تبادله شول 🎉",   b: (p) => `${p.spend} ټکي مو په ${p.credit} افغانۍ کریډیټ بدل کړل.` },
  },
  PROFILE_COMPLETE: {
    en: { t: "Profile complete 🌟", b: (p) => `You earned ${p.points} loyalty points for completing your profile.` },
    fa: { t: "پروفایل کامل شد 🌟",   b: (p) => `برای تکمیل پروفایل ${p.points} امتیاز وفاداری گرفتید.` },
    ps: { t: "پروفایل بشپړ شو 🌟",   b: (p) => `د پروفایل بشپړولو لپاره مو ${p.points} د وفادارۍ ټکي ترلاسه کړل.` },
  },
  GIFT_RECEIVED: {
    en: { t: "You received a gift card 🎁", b: (p) => `AFN ${p.amount} credit was added to your account.` },
    fa: { t: "کارت هدیه دریافت کردید 🎁",   b: (p) => `${p.amount} افغانی اعتبار به حساب شما اضافه شد.` },
    ps: { t: "د ډالۍ کارت مو ترلاسه کړ 🎁", b: (p) => `${p.amount} افغانۍ کریډیټ ستاسو حساب ته اضافه شو.` },
  },
  WALLET_TOPUP: {
    en: { t: "Wallet topped up 👛", b: (p) => `AFN ${p.amount} was added to your wallet.` },
    fa: { t: "کیف پول شارژ شد 👛",   b: (p) => `${p.amount} افغانی به کیف پول شما اضافه شد.` },
    ps: { t: "بټوه ډکه شوه 👛",      b: (p) => `${p.amount} افغانۍ ستاسو بټوې ته اضافه شوې.` },
  },
  TIP_RECEIVED: {
    en: { t: "You received a tip 💝", b: (p) => `A customer tipped you AFN ${p.amount}.` },
    fa: { t: "انعام دریافت کردید 💝",  b: (p) => `یک مشتری ${p.amount} افغانی انعام داد.` },
    ps: { t: "بخشش مو ترلاسه کړ 💝",   b: (p) => `یو پیرودونکي تاسو ته ${p.amount} افغانۍ بخشش درکړ.` },
  },
  REFERRAL_REWARD: {
    en: { t: "Referral Reward", b: (p) => `A friend you invited just joined — you earned AFN ${p.credit} credit!` },
    fa: { t: "پاداش معرفی",      b: (p) => `دوستی که دعوت کردید عضو شد — ${p.credit} افغانی اعتبار گرفتید!` },
    ps: { t: "د معرفي انعام",     b: (p) => `هغه ملګری چې بلنه مو ورکړې وه غړی شو — ${p.credit} افغانۍ کریډیټ مو ترلاسه کړ!` },
  },
  PAYOUT_SENT: {
    en: { t: "Payout Sent", b: (p) => `You have been paid AFN ${p.amount}.` },
    fa: { t: "پرداخت ارسال شد", b: (p) => `${p.amount} افغانی به شما پرداخت شد.` },
    ps: { t: "تادیه واستول شوه",  b: (p) => `${p.amount} افغانۍ تاسو ته تادیه شوې.` },
  },
  REFUND_PROCESSED: {
    en: { t: "Refund Processed", b: (p) => `Your refund of AFN ${p.amount} has been processed.` },
    fa: { t: "بازپرداخت انجام شد", b: (p) => `بازپرداخت ${p.amount} افغانی شما انجام شد.` },
    ps: { t: "بیرته ورکړه ترسره شوه", b: (p) => `ستاسو د ${p.amount} افغانۍ بیرته ورکړه ترسره شوه.` },
  },
  CREDIT_ADDED: {
    en: { t: "Credit added to your account 🎁", b: (p) => `You've received ${p.amount} AFN in credit${p.reason ? " — " + p.reason : ""}.` },
    fa: { t: "اعتبار به حسابتان اضافه شد 🎁",   b: (p) => `${p.amount} افغانی اعتبار دریافت کردید${p.reason ? " — " + p.reason : ""}.` },
    ps: { t: "کریډیټ ستاسو حساب ته اضافه شو 🎁", b: (p) => `${p.amount} افغانۍ کریډیټ مو ترلاسه کړ${p.reason ? " — " + p.reason : ""}.` },
  },
  REENGAGEMENT: {
    en: { t: "We miss you 💕", b: () => "It's been a while — book your next beauty appointment on SafeBeauty." },
    fa: { t: "دلتنگ شما شدیم 💕", b: () => "مدتی گذشته — نوبت بعدی زیبایی‌تان را در سیف‌بیوتی رزرو کنید." },
    ps: { t: "ستاسو په یاد یو 💕", b: () => "یو څه وخت تېر شو — خپل راتلونکی د ښکلا نوبت په سیف‌بیوتي کې ونیسئ." },
  },
  PENDING_BOOKINGS_WAITING: {
    en: { t: "Bookings waiting for you ⏳", b: (p) => p.count === 1
      ? "A customer has paid and is waiting for you to confirm their booking."
      : `${p.count} customers have paid and are waiting for you to confirm their bookings.` },
    fa: { t: "رزروها در انتظار شما ⏳", b: (p) => p.count === 1
      ? "یک مشتری پرداخت کرده و منتظر تأیید رزرو توسط شماست."
      : `${p.count} مشتری پرداخت کرده‌اند و منتظر تأیید رزروهایشان توسط شما هستند.` },
    ps: { t: "بکینګونه ستاسو په تمه دي ⏳", b: (p) => p.count === 1
      ? "یو پیرودونکي تادیه کړې او ستاسو د بکینګ تایید ته انتظار باسي."
      : `${p.count} پیرودونکو تادیه کړې او ستاسو د خپلو بکینګونو تایید ته انتظار باسي.` },
  },
  BOOKING_AUTO_CANCELLED: {
    en: { t: "Booking cancelled — refund on the way", b: (p) =>
      `${p.salon} did not confirm your booking in time, so we cancelled it. Your payment is being refunded.` },
    fa: { t: "رزرو لغو شد — بازپرداخت در راه است", b: (p) =>
      `${p.salon} رزرو شما را به‌موقع تأیید نکرد، بنابراین آن را لغو کردیم. مبلغ پرداختی شما بازگردانده می‌شود.` },
    ps: { t: "بکینګ لغوه شو — بیرته ورکړه په لاره ده", b: (p) =>
      `${p.salon} ستاسو بکینګ په وخت سره تایید نه کړ، نو موږ یې لغوه کړ. ستاسو تادیه بیرته درکول کیږي.` },
  },
  REVIEW_THANKS: {
    en: { t: "Thanks for your review 💬", b: (p) => `You earned ${p.points} loyalty points.` },
    fa: { t: "از نظر شما ممنونیم 💬",     b: (p) => `${p.points} امتیاز وفاداری گرفتید.` },
    ps: { t: "ستاسو د نظر مننه 💬",       b: (p) => `${p.points} د وفادارۍ ټکي مو ترلاسه کړل.` },
  },
};

/** Resolve a notification's text for [lang], falling back to the stored English
 *  title/body when the doc predates msgKey or the key is unknown. */
function localizeNotification(n, lang) {
  const entry = NOTIF_I18N[n.msgKey];
  if (!entry) return { title: String(n.title || ""), body: String(n.body || "") };
  // Dari is the default: the app's audience is Dari-first, so an unknown or
  // unset language should land there rather than on English.
  const L = entry[lang] || entry.fa || entry.en;
  const params = n.msgParams || {};
  let body;
  try { body = typeof L.b === "function" ? L.b(params) : String(L.b || ""); }
  catch (_) { body = String(n.body || ""); }
  return { title: L.t, body };
}

// ── Chase unconfirmed bookings ────────────────────────────────────────────────
//
// A booking is PAID before the provider confirms it: the webhook flips it
// AWAITING_PAYMENT -> PENDING, and it stays there until the provider taps
// confirm. Nothing chased that. A provider who simply doesn't open the app
// leaves a paying customer waiting indefinitely — no confirmation, no
// cancellation, no refund, and no one told either side.
//
// That is the two-sided marketplace failure mode: the supply side going quiet
// silently ruins the demand side's experience, and the customer blames the
// platform, not the salon. This nudges the provider once per booking so the
// booking either gets confirmed or gets declined (which refunds), instead of
// hanging.
//
// Nudged once per appointment (providerNudged), so a provider who is genuinely
// away is not spammed every run.

// Timings and the give-up deadline live in lib/unconfirmed.js so the boundary
// cases are unit-tested — this decides whether real money is refunded.
const {
  UNCONFIRMED_NUDGE_AFTER_MS,
  UNCONFIRMED_ADMIN_AFTER_MS,
  unconfirmedDeadline,
} = require("./lib/unconfirmed");


// ── Expire stories ────────────────────────────────────────────────────────────
//
// A story is a 24-hour announcement. Clients already hide expired ones by
// comparing expiresAt, so this is not what makes them disappear — it is what
// stops the collection growing forever, and what removes the photo from Storage,
// which no client-side filter can do.

