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




// Kabul-local calendar day for a timestamp.
//
// The client writes requestedDate as device-local (Kabul, UTC+4:30) midnight
// while this code runs in UTC, so comparing UTC midnight to Kabul midnight never
// matched and the waitlist promotion silently never fired. Afghanistan has no
// DST, so a fixed offset is exact.
//
// At module scope because two separate jobs now need it, and a copy in each is
// how two definitions of "the same day" start disagreeing.
const KABUL_OFFSET_MS = 4.5 * 3600 * 1000;
const kabulDay = (ts) => Math.floor((Number(ts) + KABUL_OFFSET_MS) / 86400000);


// Referral rewards (AFN). Both are granted when a referred user's identity is
// verified (see reviewKyc): the new user gets a welcome credit, the friend who
// invited them gets a referrer credit. Both are auto-applied at checkout.
const REFERRAL_WELCOME_CREDIT  = 100;
const REFERRAL_REFERRER_CREDIT = 100;

// ── Helpers ──────────────────────────────────────────────────────────────────






// ── createPaymentSession (callable) ──────────────────────────────────────────







// ── Domain modules ────────────────────────────────────────────────────────────
//
// Firebase discovers functions by enumerating this file's exports, so a module
// that is not re-exported here is simply not deployed. Named rather than spread,
// so the deployed set stays explicit and greppable.
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

// Byte-for-byte mirror of the Android PinHasher: PBKDF2WithHmacSHA256, 65,536
// iterations, 256-bit output, salt is Base64(NO_WRAP) bytes. Must match exactly
// or every PIN verification fails.
function pbkdf2Hash(pin, saltB64) {
  const salt = Buffer.from(String(saltB64), "base64");
  return crypto.pbkdf2Sync(String(pin), salt, 65536, 32, "sha256").toString("base64");
}

// Constant-time string compare to avoid leaking match progress via timing.
function hashesEqual(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

// NOTE: authenticateWithPin was removed. It was a pre-auth callable that hashed
// a submitted numeric string against EVERY user's pinHash and, on a match,
// returned that user's salt + firebaseEmail — a mass account-takeover oracle
// (one unauthenticated request tested the whole user base, and password login
// stores its hash in the same pinHash field). PINs are gone; the client only
// uses authenticateWithPassword (which resolves ONE account by phone first).
// Deleting the export removes the function on the next `firebase deploy
// --only functions`.

/**
 * Password login, keyed by phone number (the app's login identifier now that
 * PINs are gone). Unlike authenticateWithPin — which matched a numeric PIN
 * against EVERY user — this resolves the ONE account for the given phone and
 * checks its password hash, so two users sharing a password can never collide.
 * Returns the same shape as authenticateWithPin: on success the client derives
 * the auth password from (password + salt) and signs in; the hash never leaves
 * the server. No auth required (this IS the pre-auth login step).
 */
exports.authenticateWithPassword = onCall({ region: "us-central1" }, async (request) => {
  // Unauthenticated by necessity — this IS the login. Throttle both the caller
  // and the targeted phone so an attacker can neither grind one account from
  // many IPs nor sweep many accounts from one.
  const _ip = callerIp(request);
  const _phoneKey = normalizePhone(String((request.data || {}).phone || "").trim() || "unknown");
  await enforceRateLimit(`login-ip:${_ip}`, 30, 15 * 60 * 1000);
  await enforceRateLimit(`login-phone:${_phoneKey}`, 10, 15 * 60 * 1000);
  const d = request.data || {};
  const phone    = String(d.phone || "").trim();
  const password = String(d.password || "");
  if (!phone || !password) {
    return { mode: "INVALID" };
  }

  // Resolve the account with indexed lookups instead of reading the whole users
  // collection and matching in JavaScript. That scan was correct and did not
  // scale: at 100,000 users every sign-in read 100,000 documents, which is a
  // cost problem, a latency ceiling, and — on an endpoint that by definition
  // cannot require auth — a denial-of-service surface.
  //
  // Three bounded attempts, in descending order of how most accounts are stored:
  //
  //   1. the normalized form the app has written since PhoneUtils existed,
  //   2. the raw string, for a record stored exactly as it was typed,
  //   3. phoneDigits, the subscriber-tail key that adminBackfillPhoneKeys
  //      writes onto older records whose phone field was never normalized.
  //
  // phoneDigits is written ONLY by the server. Registration is a client write,
  // so a client-supplied login key would let one account claim another's key and
  // lock its owner out — the password check would then run against the wrong
  // record. Accounts created by the current app are always found by attempt 1,
  // so the key is a recovery path for legacy records rather than the norm.
  const attempts = [
    ["phone",       normalizePhone(phone)],
    ["phone",       phone],
    ["phoneDigits", phoneKey(phone)],
  ];

  let doc = null;
  for (const [field, value] of attempts) {
    if (!value) continue;
    const q = await db.collection("users").where(field, "==", value).limit(1).get();
    if (!q.empty) { doc = q.docs[0]; break; }
  }
  if (!doc) return { mode: "INVALID" };

  const u = doc.data();
  if (!u.pinHash || !u.salt) return { mode: "INVALID" };
  if (!hashesEqual(pbkdf2Hash(password, u.salt), u.pinHash)) {
    return { mode: "INVALID" };
  }

  return {
    mode:            "REAL",
    uid:             doc.id,
    name:            u.name  || "",
    role:            u.role  || "CUSTOMER",
    status:          u.status || "",
    rejectionReason: u.rejectionReason || "",
    kycStatus:       u.kycStatus || "NONE",
    firebaseEmail:   u.firebaseEmail || "",
    salt:            u.salt,
  };
});

/**
 * Bridges the two identity schemes: Firebase Auth's uid (request.auth.uid,
 * what firestore.rules' me() sees) and the app's own uid (the client-
 * generated UUID that is the actual users/{uid} document ID — see
 * RegisterViewModel). The client calls this right after firebaseAuth.signIn
 * succeeds so security rules can resolve `me()` to the real app uid via this
 * map. Verifies the claimed appUid actually belongs to the signed-in account
 * (its firebaseEmail must match the auth token's email) before trusting it,
 * so a client can't claim someone else's identity.
 */
exports.syncUidMap = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const appUid = String((request.data || {}).appUid || "");
  if (!appUid) {
    throw new HttpsError("invalid-argument", "appUid is required.");
  }
  const email = String(request.auth.token.email || "").toLowerCase();
  if (!email) {
    throw new HttpsError("failed-precondition", "No email on the auth token.");
  }
  const userSnap = await db.doc(`users/${appUid}`).get();
  // Case-insensitive: older docs may store firebaseEmail as typed, while the
  // auth token's email is always lowercase.
  if (!userSnap.exists ||
      String(userSnap.data().firebaseEmail || "").toLowerCase() !== email) {
    throw new HttpsError("permission-denied", "appUid does not match the signed-in account.");
  }
  await db.doc(`uid_map/${request.auth.uid}`).set({
    appUid,
    updatedAt: Date.now(),
  });
  return { synced: true };
});

/**
 * Updates the caller's OWN pinHash + salt (used by both Change-PIN and
 * Forgot-PIN). Server-side because the Forgot-PIN flow signs in fresh and has
 * no uid_map entry yet, so a direct client write can be denied by the rules'
 * me() lookup AFTER the Firebase Auth password was already reset — leaving
 * pinHash pointing at the old PIN and locking the account out entirely.
 * resolveAppUser identifies the caller by their auth-token email (the same
 * ownership the rules grant for direct writes) and also repopulates uid_map,
 * so the session is fully usable right after a PIN reset.
 */
exports.updatePinHash = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const pinHash = String((request.data || {}).pinHash || "");
  const salt    = String((request.data || {}).salt || "");
  if (!pinHash || !salt) {
    throw new HttpsError("invalid-argument", "pinHash and salt are required.");
  }
  const appUser = await resolveAppUser(request);
  await db.doc(`users/${appUser.uid}`).update({ pinHash, salt });
  return { updated: true };
});

/**
 * Submits the caller's identity-verification (KYC) documents for admin review.
 * The tazkira/selfie photos were already uploaded client-side to the private
 * kyc/{uid}/ Storage path; only their URLs + the text fields are passed here.
 * Server-side so kycStatus can't be self-set to APPROVED — the whole point of
 * verification. Allowed only from NONE/REJECTED (can't resubmit while PENDING
 * or overwrite an APPROVED verification).
 */
exports.submitKyc = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const d = request.data || {};
  const tazkiraNumber   = String(d.tazkiraNumber || "").trim();
  const addressProvince = String(d.addressProvince || "").trim();
  const addressDetail   = String(d.addressDetail || "").trim();
  const tazkiraPhotoUrl = String(d.tazkiraPhotoUrl || "").trim();
  const selfiePhotoUrl  = String(d.selfiePhotoUrl || "").trim();
  // Optional identity details (also editable later by the admin). Capped.
  const birthYear         = String(d.birthYear || "").trim().slice(0, 40);
  const tazkiraIssueDate  = String(d.tazkiraIssueDate || "").trim().slice(0, 40);
  const tazkiraExpiryDate = String(d.tazkiraExpiryDate || "").trim().slice(0, 40);

  if (!tazkiraNumber || !addressProvince || !addressDetail ||
      !tazkiraPhotoUrl || !selfiePhotoUrl) {
    throw new HttpsError(
      "invalid-argument",
      "Tazkira number, address, and both photos are required."
    );
  }

  const appUser = await resolveAppUser(request);
  const current = appUser.kycStatus || "NONE";
  if (current === "PENDING") {
    throw new HttpsError("failed-precondition", "Your verification is already under review.");
  }
  if (current === "APPROVED") {
    throw new HttpsError("failed-precondition", "You are already verified.");
  }

  await db.doc(`users/${appUser.uid}`).update({
    kycStatus:          "PENDING",
    kycRejectionReason: "",
    tazkiraNumber,
    birthYear,
    tazkiraIssueDate,
    tazkiraExpiryDate,
    addressProvince,
    addressDetail,
    tazkiraPhotoUrl,
    selfiePhotoUrl,
  });
  return { submitted: true };
});

/**
 * Admin approves or rejects a user's KYC submission. Admin-only. On approval,
 * kycStatus → APPROVED (unlocking booking for customers / go-live for
 * providers); on rejection, → REJECTED with a reason the user sees so they can
 * resubmit. Notifies the user either way.
 */
exports.reviewKyc = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const reviewer = await resolveAppUser(request);
  if (reviewer.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admins only.");
  }

  const d = request.data || {};
  const targetUid = String(d.targetUid || "");
  const approve   = d.approve === true;
  const reason    = String(d.rejectionReason || "").trim();
  if (!targetUid) {
    throw new HttpsError("invalid-argument", "targetUid is required.");
  }
  assertDocId(targetUid, "targetUid");
  if (!approve && !reason) {
    throw new HttpsError("invalid-argument", "A rejection reason is required.");
  }

  const targetRef = db.doc(`users/${targetUid}`);
  const targetSnap = await targetRef.get();
  if (!targetSnap.exists) {
    throw new HttpsError("not-found", "User not found.");
  }

  await targetRef.update({
    kycStatus:          approve ? "APPROVED" : "REJECTED",
    kycRejectionReason: approve ? "" : reason,
  });

  await db.collection("notifications").doc().set({
    recipientId: targetUid,
    type:        "SYSTEM",
    title:       approve ? "Identity Verified" : "Verification Rejected",
    body:        approve
      ? "Your identity has been verified. You can now continue."
      : `Your verification was rejected: ${reason}`,
    isRead:      false,
    createdAt:   Date.now(),
    relatedId:   targetUid,
  });

  // ── Referral reward ──────────────────────────────────────────────────────────
  // Rewards are granted here, at identity verification, rather than at
  // registration — passing KYC needs a real tazkira + selfie + admin review, so
  // this gates the reward against someone farming credit with fake accounts. The
  // newly-verified user gets a welcome credit; the friend whose code they used
  // gets a referrer credit. Runs once per user (guarded by referralRewarded).
  if (approve) {
    const target = targetSnap.data();
    const referredBy = String(target.referredBy || "").trim().toUpperCase();
    if (referredBy && target.referralRewarded !== true) {
      try {
        await db.runTransaction(async (tx) => {
          const freshTarget = await tx.get(targetRef);
          if (freshTarget.data().referralRewarded === true) return; // already done
          const refQ = await tx.get(
            db.collection("users").where("referralCode", "==", referredBy).limit(1)
          );
          // Mark rewarded regardless so a bad/self code can't be retried forever.
          // The welcome credit is granted only when the matched referrer is a
          // DIFFERENT user — a user whose referredBy equals their own code must
          // not self-grant AFN at approval.
          const referrerIsOther = !refQ.empty && refQ.docs[0].id !== targetUid;
          tx.update(targetRef, {
            referralRewarded: true,
            referralCredit: admin.firestore.FieldValue.increment(
              referrerIsOther ? REFERRAL_WELCOME_CREDIT : 0
            ),
          });
          if (!refQ.empty && refQ.docs[0].id !== targetUid) {
            const referrerRef = refQ.docs[0].ref;
            tx.update(referrerRef, {
              referralCredit: admin.firestore.FieldValue.increment(REFERRAL_REFERRER_CREDIT),
            });
            tx.set(db.collection("notifications").doc(), {
              recipientId: refQ.docs[0].id,
              type:        "SYSTEM",
              msgKey:      "REFERRAL_REWARD",
              msgParams:   { credit: REFERRAL_REFERRER_CREDIT },
              title:       "Referral Reward",
              body:        `A friend you invited just joined — you earned AFN ${REFERRAL_REFERRER_CREDIT} credit!`,
              isRead:      false,
              createdAt:   Date.now(),
              relatedId:   targetUid,
            });
          }
        });
      } catch (err) {
        logger.error("reviewKyc: referral reward failed (non-fatal)", err);
      }
    }
  }

  return { reviewed: true };
});

/**
 * Creates a provider's salon at registration. Server-side because the salon's
 * providerId must be authoritative (the app-level uid, not the Firebase Auth
 * uid) and because at registration time the uid_map bridge isn't populated yet,
 * so firestore.rules' me() can't resolve — a direct client write can't be
 * verified. resolveAppUser looks the provider up by their auth-token email
 * (their users/{uid} doc already exists at this point) and sets providerId to
 * the real app uid. The salon starts hidden (isAvailable=false) and unverified
 * until an admin approves the provider.
 */
exports.createProviderSalon = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "PROVIDER") {
    throw new HttpsError("permission-denied", "Only providers can create a salon.");
  }

  const { salonName, district, services } = request.data || {};
  if (!salonName || !district || !Array.isArray(services) || services.length === 0) {
    throw new HttpsError(
      "invalid-argument",
      "salonName, district and at least one service are required."
    );
  }

  // One salon per provider — return the existing one instead of duplicating
  // (e.g. if the client retries after a dropped response).
  const existing = await db.collection("salons")
    .where("providerId", "==", appUser.uid).limit(1).get();
  if (!existing.empty) {
    return { salonId: existing.docs[0].id, alreadyExisted: true };
  }

  const ref = db.collection("salons").doc();
  await ref.set({
    providerId:          appUser.uid,
    providerName:        appUser.name || "",
    salonName:           String(salonName),
    district:            String(district),
    services:            services.map(String),
    isAvailable:         false,   // hidden until an admin approves the provider
    rating:              0,
    workingHours:        [],
    slotDurationMinutes: 60,
    pricePerService:     {},
    confirmedCount:      0,
    isVerified:          false,
  });
  return { salonId: ref.id };
});

/**
 * Pre-auth lookup of an account's Firebase Auth email by phone, for the
 * password-reset flows (Forgot-PIN / Set-New-PIN). Returns ONLY the email
 * fields — never pinHash/salt — so `users` reads can stay locked to owner/admin.
 */

// Resolves an account by phone (used by password recovery AND the registration
// uniqueness check). Normalizes the input, and falls back to the raw string so
// any legacy record still matches.
exports.lookupAccountByPhone = onCall({ region: "us-central1" }, async (request) => {
  const raw = String((request.data || {}).phone || "").trim();
  if (!raw) return { found: false };
  // Throttle before touching the database: this endpoint is unauthenticated and
  // answers "does this phone have an account?", which is exactly what an
  // enumeration sweep wants. 20 lookups per IP per 10 minutes is far above any
  // real signup/recovery flow and far below a useful sweep.
  await enforceRateLimit(`lookup:${callerIp(request)}`, 20, 10 * 60 * 1000);
  const phone = normalizePhone(raw);

  let q = await db.collection("users").where("phone", "==", phone).limit(1).get();
  if (q.empty && phone !== raw) {
    q = await db.collection("users").where("phone", "==", raw).limit(1).get();
  }
  if (q.empty) return { found: false };

  const doc = q.docs[0];
  const u   = doc.data();
  return {
    found:         true,
    uid:           doc.id,
    firebaseEmail: u.firebaseEmail || "",
    email:         u.email || "",
  };
});

// ── cancelPaidAppointment (shared helper) ─────────────────────────────────────
//
// Every appointment that reaches PENDING or CONFIRMED has already been paid
// (the webhook only flips AWAITING_PAYMENT -> PENDING after payment succeeds),
// so ANY cancellation — by the customer OR by the provider declining — is a
// paid-booking cancellation. The old client paths just flipped status to
// CANCELLED via a direct Firestore write and never touched the payment —
// money taken, no refund record, no one notified. This shared transaction
// cancels the appointment AND creates a refund request the admin must action
// (HesabPay has no automated refund API wired), used by both
// cancelAppointment (customer) and providerDeclineAppointment (provider).
//
// `cancelledBy` is "CUSTOMER" or "PROVIDER" — it decides who gets notified
// (the other party) and who is authorized to act, via `authorize(appt, payment)`.
async function cancelPaidAppointment(appointmentId, cancelledBy, authorize, actor = null, reason = "") {
  const apptRef = db.doc(`appointments/${appointmentId}`);

  const result = await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();
    if (appt.status !== "PENDING" && appt.status !== "CONFIRMED") {
      throw new HttpsError("failed-precondition", "This booking can no longer be cancelled.");
    }

    // AppointmentDocument has no providerId field — it lives on the payment
    // (and salon) record, so the payment must be read before authorization.
    const paySnap = await tx.get(
      db.collection("payments").where("appointmentId", "==", appointmentId).limit(1)
    );
    const payDoc  = paySnap.empty ? null : paySnap.docs[0];
    const payment = payDoc ? payDoc.data() : null;

    if (!authorize(appt, payment)) {
      throw new HttpsError("permission-denied", "Not authorized to cancel this booking.");
    }

    tx.update(apptRef, { status: "CANCELLED" });
    writeAppointmentEvent(
      tx, appt, appointmentId, "CANCELLED",
      actor || { uid: "system", role: cancelledBy, name: "" },
      reason || (cancelledBy === "PROVIDER"
        ? "Salon declined the booking"
        : cancelledBy === "CUSTOMER"
          ? "Cancelled by the customer"
          : "Cancelled automatically")
    );

    let refundRequestId = null;
    const providerId = payment ? (payment.providerId || "") : "";
    if (payment && payment.method === "CASH") {
      // No online money ever moved, so there's nothing to refund — just undo
      // the commission debt that was charged to the provider at booking time.
      tx.update(payDoc.ref, { status: "CANCELLED" });
      if (providerId) {
        tx.set(
          db.doc(`provider_balances/${providerId}`),
          {
            providerId,
            owedAmount: admin.firestore.FieldValue.increment(payment.commissionAmount || 0),
            updatedAt:  Date.now(),
          },
          { merge: true }
        );
      }
    } else if (payment && payment.status === "PAID") {
      tx.update(payDoc.ref, { status: "REFUND_PENDING" });
      const refundRef = db.collection("refund_requests").doc();
      tx.set(refundRef, {
        appointmentId,
        paymentId:   payDoc.id,
        customerId:  appt.customerId,
        providerId,
        salonId:     appt.salonId,
        amount:      payment.amount,
        status:      "PENDING",
        createdAt:   Date.now(),
      });
      refundRequestId = refundRef.id;

      // Reverse the provider's owed balance if it was already credited
      // (it is, as soon as the webhook marked this payment PAID). Guarded:
      // an empty providerId would make db.doc() throw and every cancel of
      // such a booking fail outright.
      if (providerId) {
        tx.set(
          db.doc(`provider_balances/${providerId}`),
          {
            providerId,
            owedAmount: admin.firestore.FieldValue.increment(-payment.providerNet),
            updatedAt:  Date.now(),
          },
          { merge: true }
        );
      }
    }

    // Notify whichever party didn't initiate the cancellation.
    if (cancelledBy === "CUSTOMER" && providerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "BOOKING_CANCELLED",
        msgKey:      "BOOKING_CANCELLED_BY_CUSTOMER",
        msgParams:   { service: appt.serviceName || "A booking" },
        title:       "Booking Cancelled",
        body:        `${appt.serviceName || "A booking"} was cancelled by the customer.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    } else if (cancelledBy === "PROVIDER" && appt.customerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: appt.customerId,
        type:        "BOOKING_CANCELLED",
        msgKey:      "BOOKING_DECLINED",
        msgParams:   { service: appt.serviceName || "Your booking", salon: appt.salonName || "the salon" },
        title:       "Booking Declined",
        body:        `${appt.serviceName || "Your booking"} at ${appt.salonName || "the salon"} was declined.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    }

    return { refundRequestId, appt, payment };
  });

  // Refund the referral credit + promo use that were reserved atomically at
  // checkout (both online and cash bookings are written reserved:true). The
  // refund_requests row above only covers the online-paid `amount`; the wallet
  // credit portion lives on the user doc and must be handed back separately, or
  // a customer loses it on every cancel/decline of a credit-assisted booking.
  if (result.payment && result.payment.reserved && !result.payment.type) {
    await refundReservation({
      customerId:   result.appt.customerId,
      referralUsed: result.payment.referralUsed,
      promoId:      result.payment.promoCode,
    });
  }

  // Release the freed slot to the next waitlisted customer (best-effort,
  // outside the transaction since it's a separate, non-critical write).
  // Mirrors FirestoreRepository.notifyFirstWaiting's single-field query +
  // in-memory filter so no new composite index is required.
  try {
    // Match by KABUL-local day, not exact millis: the client writes requestedDate
    // as device-local (Kabul, UTC+4:30) midnight while this code runs in UTC —
    // comparing UTC midnight to Kabul midnight never matched, so the promotion
    // silently never fired. Afghanistan has no DST, so a fixed offset is exact.
    const wantedDay = kabulDay(result.appt.appointmentDate);

    const entries = await db.collection("waitlist")
      .where("salonId", "==", result.appt.salonId)
      .get();
    const first = entries.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .filter((w) => w.status === "WAITING" && kabulDay(w.requestedDate) === wantedDay)
      .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))[0];
    if (first) {
      // offeredAt is what lets rotateWaitlistOffers age an unclaimed offer and
      // pass it on. Without it the entry has no clock and the queue stalls.
      await db.doc(`waitlist/${first.id}`).update({
        status: "SLOT_AVAILABLE",
        offeredAt: Date.now(),
      });
      // Also tell the customer directly — the doc flip alone was invisible until
      // they happened to open the bookings sheet. The notifications trigger turns
      // this into an FCM push, and the Notification Center's Waitlist filter
      // (type "WAITLIST") finally has a producer.
      if (first.customerId) {
        await db.collection("notifications").add({
          recipientId: first.customerId,
          type:        "WAITLIST",
          msgKey:      "WAITLIST_SLOT",
          msgParams:   { salon: first.salonName || "A salon" },
          title:       "A slot opened up 🎉",
          body:        `${first.salonName || "A salon"} has a free slot on your waitlisted day — book it before it's gone!`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   first.salonId || "",
        });
      }
    }
  } catch (err) {
    logger.error("cancelPaidAppointment: waitlist notify failed (non-fatal)", err);
  }

  return { cancelled: true, refundRequestId: result.refundRequestId };
}

// ── cancelAppointment (callable, customer) ────────────────────────────────────
exports.cancelAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId } = request.data || {};
  if (!appointmentId) {
    throw new HttpsError("invalid-argument", "appointmentId is required.");
  }
  const appUser = await resolveAppUser(request);
  return cancelPaidAppointment(appointmentId, "CUSTOMER", (appt) =>
    appt.customerId === appUser.uid,
    { uid: appUser.uid, role: "CUSTOMER", name: appUser.name }
  );
});

// ── providerDeclineAppointment (callable, provider) ───────────────────────────
//
// Replaces ProviderViewModel.declineAppointment's direct Firestore write,
// which had the exact same gap as the old customer-cancel path: a PENDING
// appointment is already paid, and a plain status flip to CANCELLED left that
// payment marked PAID forever with no refund trail.
exports.providerDeclineAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId } = request.data || {};
  if (!appointmentId) {
    throw new HttpsError("invalid-argument", "appointmentId is required.");
  }
  const appUser = await resolveAppUser(request);
  return cancelPaidAppointment(appointmentId, "PROVIDER", (appt, payment) =>
    !!payment && payment.providerId === appUser.uid,
    { uid: appUser.uid, role: "PROVIDER", name: appUser.name }
  );
});

// ── getBookedSlots (callable) ─────────────────────────────────────────────────
//
// Returns only the taken time-slots for a salon within a day window. The
// client must not read other customers' appointment docs (they carry names
// and phone numbers), and the rules rightly deny such a query — which
// silently broke the client-side availability check: the denied read came
// back empty, so every slot looked free and double-booking was possible.
exports.getBookedSlots = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { salonId, dayStart, dayEnd } = request.data || {};
  const start = Number(dayStart);
  const end   = Number(dayEnd);
  if (!salonId || !Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
    throw new HttpsError("invalid-argument", "salonId, dayStart and dayEnd are required.");
  }

  const salonSnap = await db.doc(`salons/${salonId}`).get();
  const slotMinutes = Number((salonSnap.exists ? salonSnap.data().slotDurationMinutes : 0)) || 60;

  const snap = await db.collection("appointments")
    .where("salonId", "==", salonId)
    .get();
  const inWindow = snap.docs
    .map((d) => d.data())
    .filter((a) =>
      a.status !== "CANCELLED" &&
      Number(a.appointmentDate) >= start &&
      Number(a.appointmentDate) <= end);

  // A multi-service / group booking occupies several back-to-back slots, so expand
  // each appointment into every slot it actually takes (see lib/slots.js, tested).
  // `slots` is the legacy plain-times shape; `booked` tags each with its staff so
  // the client only treats a time as full when every active staff member is taken.
  const { slots, booked } = expandBooked(inWindow, slotMinutes);

  return { slots, booked };
});

// ── rescheduleAppointment (callable, customer) ────────────────────────────────
//
// Replaces the customer's direct Firestore write, which the rules had no
// customer branch for (appointment updates were provider/admin-only) — so
// rescheduling always failed, silently. Runs server-side like every other
// appointment mutation: validates ownership + status, moves the date, drops
// the booking back to PENDING for re-confirmation, and notifies the provider.
exports.rescheduleAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId, newDate } = request.data || {};
  const dateMs = Number(newDate);
  if (!appointmentId || !Number.isFinite(dateMs)) {
    throw new HttpsError("invalid-argument", "appointmentId and newDate are required.");
  }
  if (dateMs < Date.now()) {
    throw new HttpsError("invalid-argument", "The new time must be in the future.");
  }
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  const apptRef = db.doc(`appointments/${appointmentId}`);

  await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();

    if (appt.customerId !== appUser.uid && appUser.role !== "ADMIN") {
      throw new HttpsError("permission-denied", "Not authorized to reschedule this booking.");
    }
    if (appt.status !== "PENDING" && appt.status !== "CONFIRMED") {
      throw new HttpsError("failed-precondition", "This booking can no longer be rescheduled.");
    }

    const salonSnap  = await tx.get(db.doc(`salons/${appt.salonId}`));
    const salon      = salonSnap.exists ? salonSnap.data() : {};
    const providerId = salon.providerId || "";

    // Reject a move onto a day the salon blocked off (defense in depth; the
    // client hides them). Kabul-local "yyyy-MM-dd", matching createPaymentSession.
    const blocked = Array.isArray(salon.blockedDates) ? salon.blockedDates : [];
    if (blocked.length > 0) {
      const day = new Date(dateMs).toLocaleDateString("en-CA", { timeZone: "Asia/Kabul" });
      if (blocked.includes(day)) {
        throw new HttpsError("failed-precondition", "The salon is closed on that day.", { reason: "SALON_CLOSED" });
      }
    }

    // Reject a move that collides with another live booking on the same chair.
    // Read inside the transaction (all reads precede the write) so two concurrent
    // reschedules can't both land on the same slot. Exclude this booking so it
    // never conflicts with its own current slot.
    const slotMinutes = Number(salon.slotDurationMinutes) || 60;
    const span = Math.max(
      1,
      Number(appt.slotsCount) ||
        (Array.isArray(appt.services) ? appt.services.length : 0) ||
        1
    );
    const otherSnap = await tx.get(
      db.collection("appointments").where("salonId", "==", appt.salonId)
    );
    const others = otherSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    if (hasSlotConflict(others, dateMs, span, String(appt.staffId || ""), slotMinutes, appointmentId)) {
      throw new HttpsError("failed-precondition", "That time is no longer available.");
    }

    tx.update(apptRef, { appointmentDate: dateMs, status: "PENDING", reminderSent: false });
    writeAppointmentEvent(tx, appt, appointmentId, "PENDING", actor,
      `Rescheduled to ${new Date(dateMs).toISOString()}`);
    if (providerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "BOOKING_RESCHEDULED",
        msgKey:      "BOOKING_RESCHEDULED",
        msgParams:   { service: appt.serviceName || "A booking" },
        title:       "Booking Rescheduled",
        body:        `${appt.serviceName || "A booking"} was moved to a new time — please re-confirm.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    }
  });

  return { rescheduled: true };
});

// ── confirmAppointment (callable, provider) ───────────────────────────────────
//
// Replaces ProviderViewModel.acceptAppointment's four separate client writes
// (status flip, confirmedCount increment, loyalty points, notification) with
// one atomic transaction. Also required so `notifications` creation could be
// locked to the Admin SDK — with real FCM pushes now sent for every
// notification doc, an open client create rule would let any signed-in user
// push arbitrary text to any user's phone.
exports.confirmAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId } = request.data || {};
  if (!appointmentId) {
    throw new HttpsError("invalid-argument", "appointmentId is required.");
  }
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  const apptRef = db.doc(`appointments/${appointmentId}`);

  await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();

    if (appt.status === "CONFIRMED") return; // idempotent re-tap
    if (appt.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Only a pending booking can be confirmed.");
    }

    const salonRef  = db.doc(`salons/${appt.salonId}`);
    const salonSnap = await tx.get(salonRef);
    const salon     = salonSnap.exists ? salonSnap.data() : null;
    if ((!salon || salon.providerId !== appUser.uid) && appUser.role !== "ADMIN") {
      throw new HttpsError("permission-denied", "Not authorized to confirm this booking.");
    }

    // The customer doc can be missing (e.g. legacy data written under a
    // different uid scheme) — never let that block the confirmation itself.
    let customerRef = null;
    let customerExists = false;
    if (appt.customerId) {
      customerRef = db.doc(`users/${appt.customerId}`);
      customerExists = (await tx.get(customerRef)).exists;
    }

    tx.update(apptRef, { status: "CONFIRMED" });
    writeAppointmentEvent(tx, appt, appointmentId, "CONFIRMED",
      { uid: appUser.uid, role: "PROVIDER", name: appUser.name }, "Salon accepted the booking");
    if (salonSnap.exists) {
      tx.update(salonRef, {
        confirmedCount: admin.firestore.FieldValue.increment(1),
      });
    }
    if (customerExists) {
      tx.update(customerRef, {
        loyaltyPoints: admin.firestore.FieldValue.increment(10),
      });
    }
    if (appt.customerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: appt.customerId,
        type:        "BOOKING_CONFIRMED",
        msgKey:      "BOOKING_CONFIRMED",
        msgParams:   { service: appt.serviceName || "Your booking", salon: appt.salonName || "the salon" },
        title:       "Booking Confirmed",
        body:        `${appt.serviceName || "Your booking"} at ${appt.salonName || "the salon"}`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    }
  });

  return { confirmed: true };
});

// ── reportCustomer (callable, provider) ───────────────────────────────────────
//
// A provider's post-appointment feedback about a customer — the other half of
// the two-way rating system. One report per appointment: an optional 1–5 star
// rating, an optional no-show flag, and an optional escalation to admin for
// misconduct. Every change is bound to a real appointment the provider owns and
// guarded against duplicates, so a provider can't repeatedly tank a customer's
// reputation. The aggregates live on the customer's user doc (server-only) and
// are shown to providers on future booking requests.
exports.reportCustomer = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  if (appUser.role !== "PROVIDER") {
    throw new HttpsError("permission-denied", "Only providers can rate customers.");
  }

  const d = request.data || {};
  const appointmentId = String(d.appointmentId || "");
  const rating  = Math.max(0, Math.min(5, Math.floor(Number(d.rating || 0))));
  const noShow  = d.noShow === true;
  const flagged = d.flagged === true;
  const comment = String(d.comment || "").trim().slice(0, 500);
  if (!appointmentId) throw new HttpsError("invalid-argument", "appointmentId is required.");
  if (rating === 0 && !noShow && !flagged) {
    throw new HttpsError("invalid-argument", "Give a rating, mark a no-show, or flag an issue.");
  }

  const apptRef = db.doc(`appointments/${appointmentId}`);

  const result = await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();

    // Only the provider who owns this salon may report, and only once per booking.
    const salonSnap = await tx.get(db.doc(`salons/${appt.salonId}`));
    const providerId = salonSnap.exists ? (salonSnap.data().providerId || "") : "";
    if (providerId !== appUser.uid) {
      throw new HttpsError("permission-denied", "Not your booking to review.");
    }
    if (appt.customerReported === true) {
      throw new HttpsError("failed-precondition", "You've already reviewed this booking.");
    }
    // Feedback only makes sense once a booking was actually accepted/served.
    // COMPLETED is included because completePastAppointments auto-flips
    // CONFIRMED → COMPLETED ~2h after the start time — which is exactly when a
    // provider sits down to report a no-show, so excluding it would kill the
    // primary post-visit reporting window.
    if (appt.status !== "CONFIRMED" && appt.status !== "PENDING" && appt.status !== "COMPLETED") {
      throw new HttpsError("failed-precondition", "This booking can't be reviewed.");
    }

    tx.update(apptRef, { customerReported: true });
    writeAppointmentEvent(tx, appt, appointmentId, appt.status,
      { uid: appUser.uid, role: "PROVIDER", name: appUser.name },
      noShow ? "Salon reported a no-show" : `Salon rated the customer ${rating}/5`);

    const reportRef = db.collection("customer_reports").doc();
    tx.set(reportRef, {
      appointmentId,
      customerId:   appt.customerId,
      customerName: appt.customerName || "",
      providerId:   appUser.uid,
      salonId:      appt.salonId,
      salonName:    appt.salonName || "",
      rating,
      noShow,
      flagged,
      comment,
      status:       flagged ? "OPEN" : "REVIEWED",
      createdAt:    Date.now(),
    });

    // Fold into the customer's reputation aggregates (server-controlled).
    if (appt.customerId) {
      const agg = {};
      if (rating > 0) {
        agg.customerRatingSum   = admin.firestore.FieldValue.increment(rating);
        agg.customerRatingCount = admin.firestore.FieldValue.increment(1);
      }
      if (noShow) {
        agg.noShowCount = admin.firestore.FieldValue.increment(1);
      }
      if (Object.keys(agg).length > 0) {
        tx.set(db.doc(`users/${appt.customerId}`), agg, { merge: true });
      }
    }
    return { reportId: reportRef.id };
  });

  return { reported: true, reportId: result.reportId };
});



// ── sendBookingReminders (scheduled) ──────────────────────────────────────────
//
// Confirmed appointments starting within the next ~2 hours get a one-time
// reminder notification (reused notifications → FCM pipeline, same as every
// other notification in this file). reminderSent starts false at booking
// time (see createPaymentSession) and is reset to false on reschedule (see
// rescheduleAppointment) — flipping it to true here makes a slow or retried
// run idempotent instead of double-sending.
const REMINDER_WINDOW_MS = 2 * 60 * 60 * 1000;

exports.sendBookingReminders = onSchedule(
  { schedule: "every 15 minutes", region: "us-central1" },
  async () => {
    const now       = Date.now();
    const windowEnd = now + REMINDER_WINDOW_MS;

    const upcoming = await db.collection("appointments")
      .where("status", "==", "CONFIRMED")
      .where("reminderSent", "==", false)
      .where("appointmentDate", "<=", windowEnd)
      .get();

    if (upcoming.empty) return;

    let count = 0;
    for (const doc of upcoming.docs) {
      const appt = doc.data();
      // Already in the past (e.g. this function was down for a while) —
      // nothing useful to remind about; just stop it from being re-scanned.
      if (appt.appointmentDate < now) {
        await doc.ref.update({ reminderSent: true });
        continue;
      }
      const batch = db.batch();
      batch.update(doc.ref, { reminderSent: true });
      if (appt.customerId) {
        batch.set(db.collection("notifications").doc(), {
          recipientId: appt.customerId,
          type:        "BOOKING_REMINDER",
          msgKey:      "BOOKING_REMINDER",
          msgParams:   { service: appt.serviceName || "Your appointment", salon: appt.salonName || "the salon" },
          title:       "Upcoming Appointment",
          body:        `${appt.serviceName || "Your appointment"} at ${appt.salonName || "the salon"} is coming up soon.`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   doc.id,
        });
      }
      await batch.commit();
      count++;
    }
    logger.log(`sendBookingReminders: reminded ${count} upcoming appointment(s)`);
  }
);

// A CONFIRMED appointment whose time has passed (plus a grace window so one that's
// literally happening now isn't closed early) is finished — flip it to COMPLETED.
// This gives "past" a real status instead of only being inferred from the date, so
// review prompts, "book again", and provider stats read a booking as done. Once
// flipped it no longer matches status=="CONFIRMED", so the scanned set stays small
// and no composite index is needed (single-field equality + in-code date filter).
const COMPLETE_GRACE_MS = 2 * 60 * 60 * 1000;

exports.completePastAppointments = onSchedule(
  { schedule: "every 60 minutes", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - COMPLETE_GRACE_MS;
    const confirmed = await db.collection("appointments")
      .where("status", "==", "CONFIRMED")
      .get();
    if (confirmed.empty) return;

    let count = 0;
    for (const doc of confirmed.docs) {
      const a = doc.data();
      if (Number(a.appointmentDate) <= cutoff) {
        const now = Date.now();
        await doc.ref.update({ status: "COMPLETED", completedAt: now });
        await logAppointmentEvent({ ...doc.data(), status: "CONFIRMED" }, doc.id, "COMPLETED",
          null, "Visit time passed without a cancellation");
        // Stamp the customer's last visit — a cheap recency signal the
        // re-engagement nudge reads instead of scanning appointment history.
        if (a.customerId) {
          await db.doc(`users/${a.customerId}`).set({ lastVisitAt: now }, { merge: true });
        }
        count++;
      }
    }
    logger.log(`completePastAppointments: completed ${count} appointment(s)`);
  }
);

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




// ── resolveCustomerReport (admin) ─────────────────────────────────────────────
//
// Admin closing out a flagged misconduct report: marks it REVIEWED so it leaves
// the open-reports queue, and — if [suspend] is true — suspends the reported
// customer's account. customer_reports is client-write-locked, so this status
// flip can only happen here.
exports.resolveCustomerReport = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");
  const d = request.data || {};
  const reportId = String(d.reportId || "");
  const suspend  = d.suspend === true;
  if (!reportId) throw new HttpsError("invalid-argument", "reportId is required.");

  const reportRef = db.doc(`customer_reports/${reportId}`);
  const snap = await reportRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Report not found.");
  const report = snap.data();

  await reportRef.update({
    status: "REVIEWED",
    resolvedBy: appUser.uid,
    resolvedAt: Date.now(),
    actionTaken: suspend ? "SUSPENDED" : "DISMISSED",
  });

  if (suspend && report.customerId) {
    await db.doc(`users/${report.customerId}`).set(
      { status: "SUSPENDED" }, { merge: true }
    );
  }
  return { reportId, actionTaken: suspend ? "SUSPENDED" : "DISMISSED" };
});

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



exports.grantAdmin = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const targetUid = String((request.data || {}).targetUid || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");
  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  await ref.update({ role: "ADMIN", status: "APPROVED" });
  await logAdminAction(me, "GRANT_ADMIN", { targetUid, targetName: snap.data().name || "" });
  return { ok: true };
});

/** Demote an admin back to a normal CUSTOMER. Refuses to remove the LAST admin
 *  (so the platform can never lock itself out). */
exports.revokeAdmin = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const targetUid = String((request.data || {}).targetUid || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");
  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  if (snap.data().role !== "ADMIN") throw new HttpsError("failed-precondition", "That user is not an admin.");

  // Never leave the platform with zero admins.
  const admins = await db.collection("users").where("role", "==", "ADMIN").get();
  if (admins.size <= 1) {
    throw new HttpsError("failed-precondition", "Can't remove the last remaining admin.");
  }
  await ref.update({ role: "CUSTOMER", status: "APPROVED" });
  await logAdminAction(me, "REVOKE_ADMIN", { targetUid, targetName: snap.data().name || "" });
  return { ok: true };
});

// ── "Solve any problem" toolbox ───────────────────────────────────────────────

/** Reset a user's password. Sets a new salt + pinHash (Firestore) AND the
 *  derived Firebase Auth password (Admin SDK), so the user can immediately sign
 *  in with `newPassword`. The admin then tells the user the temporary password. */
exports.adminResetPassword = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const targetUid   = String(d.targetUid || "");
  const newPassword = String(d.newPassword || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");
  if (newPassword.length < 4)   throw new HttpsError("invalid-argument", "Password must be at least 4 characters.");

  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  const u = snap.data();
  const email = String(u.firebaseEmail || "");
  if (!email) throw new HttpsError("failed-precondition", "This account has no Firebase Auth email.");

  // Mirror PinHasher exactly: 16-byte base64 salt; pinHash = PBKDF2(pw),
  // authPassword = PBKDF2("AUTH:"+pw).
  const salt         = crypto.randomBytes(16).toString("base64");
  const pinHash      = pbkdf2Hash(newPassword, salt);
  const authPassword = pbkdf2Hash("AUTH:" + newPassword, salt);

  let authRecord;
  try {
    authRecord = await admin.auth().getUserByEmail(email);
  } catch (e) {
    throw new HttpsError("not-found", "No Firebase Auth user for this account.");
  }
  await admin.auth().updateUser(authRecord.uid, { password: authPassword });
  await ref.update({ pinHash, salt });
  await logAdminAction(me, "RESET_PASSWORD", { targetUid, targetName: u.name || "" });
  return { ok: true };
});

/** Fix a user's name and/or phone. Phone is normalized and uniqueness-checked
 *  (it is the login identifier), which is why a client can't self-edit it. */
exports.adminUpdateUser = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const targetUid = String(d.targetUid || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");

  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");

  const updates = {};
  if (d.name != null) {
    const name = String(d.name).trim();
    if (name) updates.name = name;
  }
  if (d.phone != null) {
    const phone = normalizePhone(String(d.phone));
    if (!phone || phone.replace(/\D/g, "").length < 7) {
      throw new HttpsError("invalid-argument", "Invalid phone number.");
    }
    const dup = await db.collection("users").where("phone", "==", phone).limit(1).get();
    if (!dup.empty && dup.docs[0].id !== targetUid) {
      throw new HttpsError("already-exists", "Another account already uses that phone.");
    }
    updates.phone = phone;
  }
  // Identity / address fields the admin curates on behalf of a provider. These
  // are frozen against client self-edits in firestore.rules; the Admin SDK
  // bypasses those rules. An empty string is allowed (clearing a value).
  const CURATED = ["addressProvince","addressDetail","tazkiraNumber",
                   "birthYear","tazkiraIssueDate","tazkiraExpiryDate"];
  for (const f of CURATED) {
    if (d[f] != null) updates[f] = String(d[f]).trim().slice(0, 200);
  }
  if (Object.keys(updates).length === 0) {
    throw new HttpsError("invalid-argument", "Nothing to update.");
  }
  await ref.update(updates);
  await logAdminAction(me, "UPDATE_USER", { targetUid, updates });
  return { ok: true, updates };
});

/** Adjust a provider's balance for dispute resolution / goodwill. `delta` is in
 *  AFN and may be negative (they owe more) or positive (platform owes more). */
exports.adminAdjustProviderBalance = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const providerId = String(d.providerId || "");
  const delta = Math.round(Number(d.delta));
  const reason = String(d.reason || "").slice(0, 500);
  if (!isValidDocId(providerId)) throw new HttpsError("invalid-argument", "Bad providerId.");
  if (!Number.isFinite(delta) || delta === 0) throw new HttpsError("invalid-argument", "delta must be a non-zero number.");

  await db.doc(`provider_balances/${providerId}`).set(
    { owedAmount: admin.firestore.FieldValue.increment(delta) },
    { merge: true }
  );
  await logAdminAction(me, "ADJUST_BALANCE", { providerId, delta, reason });
  return { ok: true };
});

/** Grant (or deduct) referral credit to a customer — real checkout money used
 *  for goodwill / manual refunds. Notifies the customer when credit is added. */
exports.adminGrantCredit = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const customerId = String(d.customerId || "");
  const amount = Math.round(Number(d.amount));
  const reason = String(d.reason || "").slice(0, 500);
  if (!isValidDocId(customerId)) throw new HttpsError("invalid-argument", "Bad customerId.");
  if (!Number.isFinite(amount) || amount === 0) throw new HttpsError("invalid-argument", "amount must be a non-zero number.");

  const ref  = db.doc(`users/${customerId}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  await ref.update({ referralCredit: admin.firestore.FieldValue.increment(amount) });

  if (amount > 0) {
    await db.collection("notifications").add({
      recipientId: customerId,
      type:        "SYSTEM",
      msgKey:      "CREDIT_ADDED",
      msgParams:   { amount, reason: reason || "" },
      title:       "Credit added to your account 🎁",
      body:        `You've received ${amount} AFN in credit${reason ? " — " + reason : ""}.`,
      isRead:      false,
      createdAt:   Date.now(),
      relatedId:   "",
    });
  }
  await logAdminAction(me, "GRANT_CREDIT", { customerId, amount, reason });
  return { ok: true };
});

const LIVE_APPOINTMENT_STATUSES = ["AWAITING_PAYMENT", "PENDING", "CONFIRMED"];

/** Commit a batch every 400 writes (Firestore's hard limit is 500). */
async function flushIfFull(batch, count) {
  if (count >= 400) { await batch.commit(); return { batch: db.batch(), count: 0 }; }
  return { batch, count };
}

exports.requestAccountDeletion = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");

  const user    = await resolveAppUser(request);
  const uid     = user.uid;              // app-level id (users/{uid})
  const authUid = request.auth.uid;      // Firebase Auth uid

  // An admin deleting themselves could orphan the platform. Refuse — another
  // admin must revoke the role first (revokeAdmin already blocks the last one).
  if (user.role === "ADMIN") {
    throw new HttpsError(
      "failed-precondition",
      "Admin accounts can't be self-deleted. Have another admin revoke your admin role first."
    );
  }

  const now = Date.now();
  let batch = db.batch();
  let count = 0;
  const add = async (fn) => { fn(batch); count++; ({ batch, count } = await flushIfFull(batch, count)); };

  // 1. Cancel every live appointment on both sides of the marketplace.
  let cancelled = 0;
  for (const field of ["customerId", "providerId"]) {
    const snap = await db.collection("appointments")
      .where(field, "==", uid)
      .where("status", "in", LIVE_APPOINTMENT_STATUSES)
      .get();
    for (const d of snap.docs) {
      await add((b) => b.update(d.ref, {
        status: "CANCELLED",
        cancelledAt: now,
        cancelReason: "ACCOUNT_DELETED",
      }));
      cancelled++;
    }
  }

  // 2. Strip personal fields from the financial records we keep.
  const asCustomer = await db.collection("appointments").where("customerId", "==", uid).get();
  for (const d of asCustomer.docs) {
    await add((b) => b.update(d.ref, { customerName: "", customerPhone: "", notes: "" }));
  }

  // 3. Reviews stay (they inform other customers) but lose their author.
  const reviews = await db.collection("reviews").where("customerId", "==", uid).get();
  for (const d of reviews.docs) {
    await add((b) => b.update(d.ref, { customerName: "" }));
  }

  // 4. Purely personal rows are deleted outright.
  for (const [coll, field] of [
    ["favorites",     "customerId"],
    ["waitlist",      "customerId"],
    ["notifications", "uid"],
  ]) {
    try {
      const snap = await db.collection(coll).where(field, "==", uid).get();
      for (const d of snap.docs) await add((b) => b.delete(d.ref));
    } catch (e) {
      logger.warn(`requestAccountDeletion: skipping ${coll}`, e);
    }
  }

  // 5. A departing provider's salon must stop taking bookings.
  const salons = await db.collection("salons").where("providerId", "==", uid).get();
  for (const d of salons.docs) {
    await add((b) => b.update(d.ref, { hidden: true, isVerified: false, deletedAt: now }));
  }

  // 6. The uid_map bridge (there may be several, one per Auth account used).
  const maps = await db.collection("uid_map").where("appUid", "==", uid).get();
  for (const d of maps.docs) await add((b) => b.delete(d.ref));
  await add((b) => b.delete(db.doc(`uid_map/${authUid}`)));

  // 7. The user document itself — the home of every remaining PII field
  //    (name, phone, email, tazkira number, KYC photo URLs, fcmToken).
  await add((b) => b.delete(db.doc(`users/${uid}`)));

  if (count > 0) await batch.commit();

  // 8. Private images. Best-effort: a Storage hiccup must not resurrect an
  //    account whose Firestore identity is already gone.
  for (const prefix of [`kyc/${uid}/`, `profile/${uid}/`, `reviews/${uid}/`]) {
    try {
      await admin.storage().bucket().deleteFiles({ prefix });
    } catch (e) {
      logger.warn(`requestAccountDeletion: storage cleanup failed for ${prefix}`, e);
    }
  }

  // 9. Finally the credential itself. Last, so a failure above leaves the user
  //    able to sign in and retry rather than locked out mid-deletion.
  try {
    await admin.auth().deleteUser(authUid);
  } catch (e) {
    logger.error("requestAccountDeletion: auth delete failed", e);
    throw new HttpsError("internal", "Your data was removed but the sign-in could not be closed. Contact support.");
  }

  await db.collection("admin_audit").add({
    adminUid: uid, adminName: "(self)", action: "DELETE_ACCOUNT",
    details: { role: user.role || "", cancelledAppointments: cancelled, salonsHidden: salons.size },
    createdAt: now,
  });

  logger.log(`requestAccountDeletion: deleted ${uid} (cancelled ${cancelled} appointment(s))`);
  return { ok: true, cancelledAppointments: cancelled };
});

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

exports.adminCreateSalon = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d  = request.data || {};

  const ownerName = String(d.ownerName || "").trim();
  const rawPhone  = String(d.phone || "").trim();
  const password  = String(d.password || "");
  const salonName = String(d.salonName || "").trim();
  const district  = String(d.district || "").trim();
  const services  = Array.isArray(d.services)
    ? d.services.map((s) => String(s).trim()).filter(Boolean)
    : [];
  const prices    = (d.pricePerService && typeof d.pricePerService === "object")
    ? d.pricePerService : {};

  if (!ownerName)        throw new HttpsError("invalid-argument", "Owner name is required.");
  if (!rawPhone)         throw new HttpsError("invalid-argument", "Phone number is required.");
  if (password.length < 6) throw new HttpsError("invalid-argument", "Password must be at least 6 characters.");
  if (!salonName)        throw new HttpsError("invalid-argument", "Salon name is required.");
  if (!district)         throw new HttpsError("invalid-argument", "District is required.");
  if (!services.length)  throw new HttpsError("invalid-argument", "At least one service is required.");

  const phone = normalizePhone(rawPhone);

  // The phone is the login identifier, so it must be unique platform-wide.
  const clash = await db.collection("users").where("phone", "==", phone).limit(1).get();
  if (!clash.empty) {
    throw new HttpsError("already-exists", "An account with this phone number already exists.");
  }

  // Mirror PinHasher / RegisterViewModel exactly so the owner can sign in from
  // the app with the plain password the admin hands them.
  const uid           = crypto.randomUUID();
  const salt          = crypto.randomBytes(16).toString("base64");
  const pinHash       = pbkdf2Hash(password, salt);
  const authPassword  = pbkdf2Hash("AUTH:" + password, salt);
  const firebaseEmail = `${uid.replace(/-/g, "")}@sb.app`;
  const referralCode  = "SB" + uid.replace(/-/g, "").slice(0, 6).toUpperCase();
  const now           = Date.now();

  let authUid = null;
  try {
    const rec = await admin.auth().createUser({ email: firebaseEmail, password: authPassword });
    authUid = rec.uid;

    await db.doc(`users/${uid}`).set({
      uid, name: ownerName, phone, email: "",
      role: "PROVIDER",
      // Admin-created salons are live immediately — the admin has met the owner,
      // which is exactly what the approval queue exists to establish.
      status: "APPROVED",
      pinHash, salt, firebaseEmail,
      createdAt: now,
      referralCode, referredBy: "",
      loyaltyPoints: 0,
      kycStatus: "NONE",
    });

    const salonRef = db.collection("salons").doc();
    await salonRef.set({
      providerId:          uid,
      providerName:        ownerName,
      salonName,
      district,
      services,
      pricePerService:     prices,
      isAvailable:         false,   // owner opens for business by setting hours
      rating:              0,
      workingHours:        [],
      slotDurationMinutes: 60,
      confirmedCount:      0,
      isVerified:          true,    // vouched for by the admin who added it
      createdAt:           now,
      createdByAdmin:      me.uid,
    });

    await logAdminAction(me, "CREATE_SALON", {
      targetUid: uid, targetName: ownerName, salonName, district, salonId: salonRef.id,
    });

    logger.log(`adminCreateSalon: ${salonName} (${salonRef.id}) for ${phone} by ${me.uid}`);
    return { ok: true, salonId: salonRef.id, providerUid: uid, phone };
  } catch (e) {
    // Roll back the Auth account so the phone number stays usable.
    if (authUid) {
      try { await admin.auth().deleteUser(authUid); } catch (_) {}
      try { await db.doc(`users/${uid}`).delete(); } catch (_) {}
    }
    if (e instanceof HttpsError) throw e;
    logger.error("adminCreateSalon failed", e);
    throw new HttpsError("internal", "Could not create the salon.");
  }
});








// ── adminLookupBooking ────────────────────────────────────────────────────────
//
// Everything known about one booking, from a code a customer can read down the
// phone. Assembled server-side because the pieces live in five collections the
// admin console would otherwise have to query separately, and because two of
// them (payments, refund_requests) are not client-readable at all.
//
// Accepts either the booking code or the raw document id, so a support call and
// a log line both lead to the same place.
exports.adminLookupBooking = onCall({ region: "us-central1" }, async (request) => {
  await assertAdmin(request);
  const raw = String((request.data || {}).code || "").trim();
  if (!raw) throw new HttpsError("invalid-argument", "A booking code is required.");

  // Typed by a human off a screen or read down a phone line: lower case, a
  // missing prefix and stray punctuation all resolve to the same code. Anything
  // that could not be a code normalizes to "" and skips the query entirely.
  const code = normalizeBookingCode(raw);

  let apptDoc = null;
  if (code) {
    const snap = await db.collection("appointments")
      .where("bookingCode", "==", code).limit(1).get();
    apptDoc = snap.empty ? null : snap.docs[0];
  }

  // Fall back to the raw document id, so a value copied out of a log line finds
  // the same booking a customer's code does. Validated first: an unchecked
  // string interpolated into a path throws a 500 on the first stray slash.
  if (!apptDoc && isValidDocId(raw)) {
    const byId = await db.doc(`appointments/${raw}`).get();
    if (byId.exists) apptDoc = byId;
  }
  if (!apptDoc) throw new HttpsError("not-found", "No booking with that reference.");

  const appointmentId = apptDoc.id;
  const appt = apptDoc.data();

  const [eventsSnap, paySnap, refundSnap, reportSnap, reviewSnap] = await Promise.all([
    db.collection("appointment_events").where("appointmentId", "==", appointmentId)
      .orderBy("at", "asc").get(),
    db.collection("payments").where("appointmentId", "==", appointmentId).get(),
    db.collection("refund_requests").where("appointmentId", "==", appointmentId).get(),
    db.collection("customer_reports").where("appointmentId", "==", appointmentId).get(),
    db.collection("reviews").where("appointmentId", "==", appointmentId).get(),
  ]);

  const rows = (q) => q.docs.map((d) => ({ id: d.id, ...d.data() }));

  return {
    appointment: { id: appointmentId, ...appt },
    events:      rows(eventsSnap),
    payments:    rows(paySnap),
    refunds:     rows(refundSnap),
    reports:     rows(reportSnap),
    reviews:     rows(reviewSnap),
  };
});

// ── adminBackfillBookingCodes ─────────────────────────────────────────────────
//
// Bookings made before codes existed have none, so support cannot look them up
// and their history starts mid-story. Walks a page at a time and is safe to run
// repeatedly: it only ever touches appointments with no code, so a second run
// after a timeout continues where the first stopped.
exports.adminBackfillBookingCodes = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const limit = Math.min(400, Math.max(1, Number((request.data || {}).limit || 200)));

  // A missing field cannot be queried for, so this walks by creation order and
  // skips the ones already done rather than filtering server-side.
  const snap = await db.collection("appointments").orderBy("createdAt", "asc").limit(limit).get();

  let assigned = 0;
  for (const d of snap.docs) {
    if (d.data().bookingCode) continue;
    const code = await reserveBookingCode();
    await d.ref.update({ bookingCode: code });
    await logAppointmentEvent(
      { ...d.data(), bookingCode: code, status: d.data().status },
      d.id, d.data().status, { uid: me.uid, role: "ADMIN", name: me.name },
      "Reference assigned retroactively"
    );
    assigned += 1;
  }

  await logAdminAction(me, "BACKFILL_CODES", { scanned: snap.size, assigned });
  return { ok: true, scanned: snap.size, assigned, done: snap.size < limit };
});

// ── adminTestAlert ────────────────────────────────────────────────────────────
//
// Fires a synthetic alert so the whole path can be checked end to end: log →
// log-based metric → alert policy → notification → a person's phone.
//
// Monitoring that has never fired is monitoring nobody knows works, and the
// moment you find out is the moment you needed it. Each link here can break
// quietly — a metric filter that matches nothing, a notification channel that
// was never verified, a policy left disabled — and none of those failures
// announce themselves.
//
// Safe to run any time: it touches no data and describes itself as a drill in
// the alert body, so whoever receives it is not misled into thinking the
// platform is broken.
exports.adminTestAlert = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const note = String((request.data || {}).note || "").slice(0, 200);

  alertable(
    "ALERT_PIPELINE_TEST",
    "DRILL — this is a test of the alerting pipeline, not a real failure",
    { triggeredBy: me.uid, triggeredByName: me.name || "", note, at: Date.now() }
  );

  await logAdminAction(me, "TEST_ALERT", { note });
  return {
    ok: true,
    firedAt: Date.now(),
    expect: "A notification should arrive within about five minutes.",
  };
});

// ── rotateWaitlistOffers ──────────────────────────────────────────────────────
//
// When a slot frees, the first person waiting is told and given the chance to
// book it. That is the right shape for a paid marketplace — booking on someone's
// behalf would charge them for an appointment they never chose.
//
// But nothing ever moved the offer on. If that first person did not act, their
// entry sat in SLOT_AVAILABLE permanently: they were never told again about a
// later opening, and nobody behind them in the queue was told at all. One
// unanswered notification stalled the whole waitlist for that salon, silently.
//
// So an unclaimed offer expires and passes to the next person. The window is
// generous — these are women who may see the notification hours later — but it
// is finite, because a queue that never advances is not a queue.
const WAITLIST_OFFER_WINDOW_MS = 6 * 60 * 60 * 1000;

exports.rotateWaitlistOffers = onSchedule(
  { schedule: "every 30 minutes", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - WAITLIST_OFFER_WINDOW_MS;

    const stale = await db.collection("waitlist")
      .where("status", "==", "SLOT_AVAILABLE")
      .where("offeredAt", "<", cutoff)
      .limit(50)
      .get();
    if (stale.empty) return;

    let passed = 0;
    for (const doc of stale.docs) {
      const w = doc.data() || {};
      await doc.ref.update({ status: "EXPIRED", expiredAt: Date.now() });

      // The next person still waiting for the same salon and the same day.
      const queue = await db.collection("waitlist")
        .where("salonId", "==", w.salonId || "")
        .where("status", "==", "WAITING")
        .limit(50)
        .get();

      const next = queue.docs
        .map((d) => ({ id: d.id, ref: d.ref, ...d.data() }))
        .filter((x) => kabulDay(x.requestedDate) === kabulDay(w.requestedDate))
        .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))[0];

      if (!next) continue;

      await next.ref.update({ status: "SLOT_AVAILABLE", offeredAt: Date.now() });
      if (next.customerId) {
        await db.collection("notifications").add({
          recipientId: next.customerId,
          type:        "WAITLIST",
          msgKey:      "WAITLIST_SLOT",
          msgParams:   { salon: next.salonName || "A salon" },
          title:       "A slot opened up 🎉",
          body:        `${next.salonName || "A salon"} has a free slot on your waitlisted day — book it before it's gone!`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   next.salonId || "",
        });
      }
      passed += 1;
    }

    logger.log(`rotateWaitlistOffers: expired ${stale.size}, passed on ${passed}`);
  }
);

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

// ── adminBackfillPhoneKeys ────────────────────────────────────────────────────
//
// Writes the phoneDigits lookup key onto accounts that predate it.
//
// Only needed for records whose phone field was never normalized — anything the
// current app wrote is already found by an exact match on `phone`. Server-only
// by design: a client-supplied login key would let one account claim another's
// and lock its owner out.
//
// Also reports collisions rather than silently picking a winner. Two accounts
// sharing a subscriber key means login is ambiguous for that number, and that is
// a fact a human needs to see, not something a backfill should paper over.
exports.adminBackfillPhoneKeys = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const limit = Math.min(500, Math.max(1, Number((request.data || {}).limit || 300)));

  const snap = await db.collection("users").orderBy("createdAt", "asc").limit(limit).get();

  const seen = new Map();   // key -> first uid that claimed it
  const collisions = [];
  let written = 0;

  for (const d of snap.docs) {
    const data = d.data();
    const key  = phoneKey(data.phone);
    if (!key) continue;

    if (seen.has(key)) {
      collisions.push({ key, uids: [seen.get(key), d.id] });
    } else {
      seen.set(key, d.id);
    }

    if (data.phoneDigits !== key) {
      await d.ref.update({ phoneDigits: key });
      written += 1;
    }
  }

  await logAdminAction(me, "BACKFILL_PHONE_KEYS", {
    scanned: snap.size, written, collisions: collisions.length,
  });
  if (collisions.length) {
    logger.error("adminBackfillPhoneKeys: duplicate phone keys", { collisions });
  }
  return {
    ok: true,
    scanned: snap.size,
    written,
    collisions,
    done: snap.size < limit,
  };
});

// ── adminSetUserStatus ────────────────────────────────────────────────────────
//
// Suspend or reinstate an account. The platform could already adjust a user's
// money and rewrite their password, but not stop them booking -- the only
// lever against someone abusing the service was deleting them outright, which
// destroys the evidence along with the account.
//
// A suspension is recorded on the user document rather than in Firebase Auth so
// the person can still sign in and read their own history; what stops is the
// ability to act, which the booking callables check.
exports.adminSetUserStatus = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d  = request.data || {};
  const targetUid = String(d.targetUid || "").trim();
  const suspend   = d.suspend === true;
  const reason    = String(d.reason || "").trim().slice(0, 300);

  if (!targetUid) throw new HttpsError("invalid-argument", "targetUid is required.");
  if (suspend && !reason) {
    throw new HttpsError("invalid-argument", "A suspension needs a reason on the record.");
  }

  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "No such user.");
  const target = snap.data();

  if (target.role === "ADMIN" && suspend) {
    throw new HttpsError("failed-precondition", "Remove admin access before suspending this account.");
  }

  await ref.update({
    suspended:       suspend,
    suspendedReason: suspend ? reason : "",
    suspendedAt:     suspend ? Date.now() : 0,
    suspendedBy:     suspend ? me.uid : "",
  });

  await logAdminAction(me, suspend ? "SUSPEND_USER" : "REINSTATE_USER", {
    targetUid, targetName: target.name || "", reason,
  });
  return { ok: true, suspended: suspend };
});

// ── adminUserDossier ──────────────────────────────────────────────────────────
//
// Everything about one account in a single call: who they are, what they have
// booked, what they have paid, what has been said about them and by them.
//
// The console used to answer a support question by opening four tabs and
// eyeballing across them, which is slow at the moment it matters and easy to
// get wrong. Bounded to the most recent rows of each kind so the response stays
// a fixed size no matter how long the account has been active.
exports.adminUserDossier = onCall({ region: "us-central1" }, async (request) => {
  await assertAdmin(request);
  const targetUid = String((request.data || {}).targetUid || "").trim();
  if (!targetUid) throw new HttpsError("invalid-argument", "targetUid is required.");

  const snap = await db.doc(`users/${targetUid}`).get();
  if (!snap.exists) throw new HttpsError("not-found", "No such user.");
  const user = snap.data();

  // Strip the credential material. An admin never needs it, and a dossier is
  // exactly the kind of payload that ends up pasted into a chat window.
  const { pinHash, salt, firebaseEmail, ...safeUser } = user;

  const isProvider = user.role === "PROVIDER";
  const bookingsQ  = isProvider
    ? db.collection("appointments").where("salonId", "==", String((request.data || {}).salonId || "___none___"))
    : db.collection("appointments").where("customerId", "==", targetUid);

  const [bookings, reviews, reportsAbout, balance, salons] = await Promise.all([
    bookingsQ.orderBy("createdAt", "desc").limit(25).get().catch(() => ({ docs: [] })),
    db.collection("reviews").where("customerId", "==", targetUid)
      .limit(15).get().catch(() => ({ docs: [] })),
    db.collection("customer_reports").where("customerId", "==", targetUid)
      .limit(15).get().catch(() => ({ docs: [] })),
    db.doc(`provider_balances/${targetUid}`).get().catch(() => ({ exists: false })),
    db.collection("salons").where("providerId", "==", targetUid).get().catch(() => ({ docs: [] })),
  ]);

  const rows = (q) => (q.docs || []).map((d) => ({ id: d.id, ...d.data() }));

  return {
    user:     { id: targetUid, ...safeUser },
    salons:   rows(salons),
    bookings: rows(bookings),
    reviews:  rows(reviews),
    reports:  rows(reportsAbout),
    balance:  balance.exists ? balance.data() : null,
  };
});

// ── Abuse protection for the pre-login callables ──────────────────────────────
//
// `lookupAccountByPhone` and `authenticateWithPassword` cannot require auth —
// they run BEFORE the user has a session. That leaves two open doors:
//
//   1. Enumeration. Afghan mobile numbers are a small, guessable space, so an
//      unthrottled lookup reveals which numbers belong to SafeBeauty users. For
//      an app used by women in Kabul that is a personal-safety problem, not just
//      a privacy one.
//   2. Brute force. An unthrottled login endpoint lets an attacker grind
//      passwords for a phone number they already know.
//
// A fixed window in Firestore is enough here: the traffic is low, the counter is
// cheap, and a transaction keeps concurrent calls honest. Limits are per-caller
// (IP) and, for login, additionally per-phone so one victim can't be targeted
// from many IPs.

/**
 * Fixed-window rate limit. Throws resource-exhausted once [max] calls have been
 * made under [key] inside [windowMs]. Fails OPEN on infrastructure errors — a
 * Firestore hiccup must never lock legitimate users out of signing in.
 */
async function enforceRateLimit(key, max, windowMs) {
  const ref = db.doc(`rate_limits/${encodeURIComponent(key)}`);
  const now = Date.now();
  try {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.exists ? snap.data() : null;
      if (!d || now - (d.windowStart || 0) >= windowMs) {
        tx.set(ref, { windowStart: now, count: 1, updatedAt: now });
        return;
      }
      if ((d.count || 0) >= max) {
        const retryInSec = Math.ceil((d.windowStart + windowMs - now) / 1000);
        throw new HttpsError(
          "resource-exhausted",
          `Too many attempts. Please try again in ${retryInSec} second(s).`
        );
      }
      tx.update(ref, { count: (d.count || 0) + 1, updatedAt: now });
    });
  } catch (e) {
    if (e instanceof HttpsError) throw e;   // the limit itself — propagate
    logger.warn("enforceRateLimit failed open", e);
  }
}

/** Best-effort caller IP for a v2 callable. */
function callerIp(request) {
  const r = request.rawRequest || {};
  const fwd = (r.headers && (r.headers["x-forwarded-for"] || r.headers["X-Forwarded-For"])) || "";
  return String(fwd).split(",")[0].trim() || r.ip || "unknown";
}

/** Purge stale rate-limit counters so the collection can't grow without bound. */
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

exports.nudgeUnconfirmedBookings = onSchedule(
  { schedule: "every 1 hours", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - UNCONFIRMED_NUDGE_AFTER_MS;
    const snap = await db.collection("appointments")
      .where("status", "==", "PENDING")
      .where("createdAt", "<", cutoff)
      .get();
    if (snap.empty) return;

    // One notification per provider, however many bookings are waiting —
    // five separate pushes would read as noise and get the app muted.
    //
    // The provider is resolved through the salon, because an appointment does
    // not carry providerId and never has. This job used to read a.providerId
    // directly and skip anything without one, which is every appointment ever
    // written — so the whole nudge, auto-cancel and refund flow had never run
    // for a single booking since it was added. It exists precisely so a
    // customer who has paid is not left waiting indefinitely on a salon that
    // never answers, and that is the case it was silently not covering.
    const providerBySalon = new Map();
    const providerFor = async (salonId) => {
      if (!salonId) return "";
      if (providerBySalon.has(salonId)) return providerBySalon.get(salonId);
      const snap = await db.doc(`salons/${salonId}`).get();
      const pid = snap.exists ? String((snap.data() || {}).providerId || "") : "";
      providerBySalon.set(salonId, pid);
      return pid;
    };

    const byProvider = new Map();
    const orphaned = [];
    for (const d of snap.docs) {
      const a = d.data();
      if (a.providerNudged) continue;            // already chased this one
      const providerId = await providerFor(a.salonId);
      if (!providerId) {
        // The salon is gone, or the booking predates salons having owners.
        // There is nobody to nudge, so it is reported rather than skipped in
        // silence — an unactionable row that reappears in every sweep forever
        // is how a report becomes noise and the next real finding gets missed.
        orphaned.push({ appointmentId: d.id, salonId: a.salonId || "", salonName: a.salonName || "" });
        continue;
      }
      if (!byProvider.has(providerId)) byProvider.set(providerId, []);
      byProvider.get(providerId).push(d);
    }

    if (orphaned.length) {
      alertable("BOOKING_FAILED", "Pending bookings whose salon no longer exists", {
        count: orphaned.length, orphaned: orphaned.slice(0, 20),
      });
    }
    if (byProvider.size === 0) return;

    const now = Date.now();
    let notified = 0;
    for (const [providerId, docs] of byProvider) {
      const batch = db.batch();
      batch.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "NEW_BOOKING",             // taps route to the requests tab
        msgKey:      "PENDING_BOOKINGS_WAITING",
        msgParams:   { count: docs.length },
        title:       "Bookings waiting for you ⏳",
        body:        docs.length === 1
          ? "A customer has paid and is waiting for you to confirm their booking."
          : `${docs.length} customers have paid and are waiting for you to confirm their bookings.`,
        isRead:      false,
        createdAt:   now,
        relatedId:   docs[0].id,
      });
      docs.forEach((d) => batch.update(d.ref, { providerNudged: true }));
      try {
        await batch.commit();
        notified++;
      } catch (e) {
        logger.warn("nudgeUnconfirmedBookings: batch failed", { providerId, error: String(e.message || e) });
      }
    }
    logger.log(`nudgeUnconfirmedBookings: nudged ${notified} provider(s)`);

    // ── Stage 2: put a human on it ──────────────────────────────────────────
    // With a handful of salons the admin personally onboarded every owner and
    // has their phone number, so one call converts most of these into a
    // confirmed booking. That is worth far more than a refund.
    const adminCutoff = now - UNCONFIRMED_ADMIN_AFTER_MS;
    const needsAdmin = snap.docs.filter((d) => {
      const a = d.data();
      return !a.adminAlerted && (a.createdAt || 0) < adminCutoff;
    });
    if (needsAdmin.length) {
      const admins = await db.collection("users").where("role", "==", "ADMIN").get();
      const batch = db.batch();
      admins.docs.forEach((adminDoc) => {
        batch.set(db.collection("notifications").doc(), {
          recipientId: adminDoc.id,
          type:        "SYSTEM",
          title:       "Bookings still unconfirmed",
          body:        `${needsAdmin.length} paid booking(s) have gone unconfirmed for over 6 hours. Contact the salon before they are auto-cancelled.`,
          isRead:      false,
          createdAt:   now,
          relatedId:   needsAdmin[0].id,
        });
      });
      needsAdmin.forEach((d) => batch.update(d.ref, { adminAlerted: true }));
      try {
        await batch.commit();
        logger.log(`nudgeUnconfirmedBookings: alerted admins about ${needsAdmin.length} booking(s)`);
      } catch (e) {
        logger.warn("nudgeUnconfirmedBookings: admin alert failed", e);
      }
    }

    // ── Stage 3: stop waiting, refund, and say so ───────────────────────────
    // Deliberately NOT auto-confirm. The provider never agreed to this booking;
    // sending a customer across Kabul to a salon that is not expecting her is a
    // worse outcome than any refund.
    const expired = snap.docs.filter((d) => now >= unconfirmedDeadline(d.data()));
    let cancelled = 0;
    for (const d of expired) {
      const appt = d.data();
      try {
        // Reuses the same path as a provider decline, so the refund request,
        // the payment status and the provider's owed balance all unwind exactly
        // as they do for a manual cancellation.
        await cancelPaidAppointment(d.id, "SYSTEM", () => true, null,
          "Salon never confirmed before the deadline");
        await db.collection("notifications").add({
          recipientId: appt.customerId,
          type:        "BOOKING_CANCELLED",
          msgKey:      "BOOKING_AUTO_CANCELLED",
          msgParams:   { salon: appt.salonName || "The salon" },
          title:       "Booking cancelled — refund on the way",
          body:        `${appt.salonName || "The salon"} did not confirm your booking in time, so we cancelled it. Your payment is being refunded.`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   d.id,
        });
        cancelled++;
      } catch (e) {
        // A booking the provider confirmed or cancelled in the same window
        // throws failed-precondition here; that is the correct outcome, not an
        // error worth retrying.
        logger.warn("nudgeUnconfirmedBookings: could not auto-cancel", {
          appointmentId: d.id, error: String(e.message || e),
        });
      }
    }
    if (cancelled) {
      logger.log(`nudgeUnconfirmedBookings: auto-cancelled ${cancelled} unconfirmed booking(s)`);
    }
  }
);

// ── Expire stories ────────────────────────────────────────────────────────────
//
// A story is a 24-hour announcement. Clients already hide expired ones by
// comparing expiresAt, so this is not what makes them disappear — it is what
// stops the collection growing forever, and what removes the photo from Storage,
// which no client-side filter can do.

