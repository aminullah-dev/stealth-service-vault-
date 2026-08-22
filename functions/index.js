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
const discovery = require("./domains/discovery");
exports.deriveSalonFields       = discovery.deriveSalonFields;
exports.normalizeSalonsDaily    = discovery.normalizeSalonsDaily;
exports.adminNormalizeSalons    = discovery.adminNormalizeSalons;
exports.adminDemandReport       = discovery.adminDemandReport;
exports.measureSalonReliability = discovery.measureSalonReliability;

const notifications = require("./domains/notifications");
exports.pushOnNotificationCreated = notifications.pushOnNotificationCreated;
exports.pushOnBroadcastCreated    = notifications.pushOnBroadcastCreated;
exports.resumeBroadcasts          = notifications.resumeBroadcasts;
exports.sendReengagementNudges    = notifications.sendReengagementNudges;

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




















// ── Promo codes ───────────────────────────────────────────────────────────────













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

