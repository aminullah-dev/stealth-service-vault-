/**
 * The pieces every part of the backend needs, in one place.
 *
 * index.js had grown past 5,700 lines. Splitting it by domain only works if
 * there is somewhere for the things that are genuinely shared to live —
 * otherwise each domain re-initialises Firebase and ends up with its own
 * Firestore handle, which is not a smaller version of the same program.
 *
 * initializeApp() runs HERE, once, on first require. Node caches modules, so
 * every domain that requires this gets the same initialised app and the same
 * `db`. Doing it in each domain instead would throw on the second call.
 */

const { HttpsError } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const { isValidDocId } = require("./lib/validate");
const { phoneKey } = require("./lib/phone");
const { bookingCodeFromBytes } = require("./lib/booking");
const crypto = require("crypto");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/**
 * Log something a human should be told about, with a stable machine label.
 *
 * Alert policies match on jsonPayload.alert rather than on message text, so
 * rewording a log line cannot silently disable the alert that depends on it.
 */
function alertable(kind, message, details) {
  logger.error(message, { alert: kind, ...(details || {}) });
}

// ── Identity, authorisation and audit ─────────────────────────────────────────
//
// These lived in index.js, where every domain split out of it would have had to
// reach back into the file it was leaving. They are the genuinely cross-cutting
// rules — who the caller is, whether they may act, and what gets written down
// about it — so they belong beside db rather than beside any one feature.

// Reject a malformed / path-unsafe document id before it is interpolated into a
// Firestore doc path — defense-in-depth: a value with a slash makes an
// odd-segment path (unhandled 500), and untrusted strings don't belong in paths.
function assertDocId(id, field) {
  if (!isValidDocId(id)) {
    throw new HttpsError("invalid-argument", `Invalid ${field}.`);
  }
}

// The app's `users/{uid}` documents are keyed by a UUID generated at
// registration — by registerAccount on the server now, by the client for
// accounts that predate it — NOT by the Firebase Auth uid that
// `request.auth.uid` carries. The two are only linked via `firebaseEmail`.
// Every callable that needs "who is this app user" must resolve through here
// instead of using request.auth.uid directly, or it silently tags data with
// an identity the rest of the app (which queries by the app-level uid) can
// never match — the appointment becomes invisible everywhere.
async function resolveAppUser(request) {
  // Lowercased on both sides: Firebase Auth normalizes emails to lowercase,
  // and registration stores firebaseEmail lowercased to match.
  const email = String(request.auth.token.email || "").toLowerCase();
  if (!email) {
    throw new HttpsError("failed-precondition", "No email on the auth token.");
  }
  const q = await db.collection("users").where("firebaseEmail", "==", email).limit(1).get();
  if (q.empty) {
    // Fallback for older docs whose stored firebaseEmail casing differs from
    // the (always-lowercase) token email: the uid_map bridge written at login
    // (syncUidMap verifies ownership case-insensitively) still resolves them.
    const mapSnap = await db.doc(`uid_map/${request.auth.uid}`).get();
    if (mapSnap.exists) {
      const mapped = await db.doc(`users/${mapSnap.data().appUid}`).get();
      if (mapped.exists) return { uid: mapped.id, ...mapped.data() };
    }
    throw new HttpsError("not-found", "User profile not found.");
  }
  const doc = q.docs[0];
  // Opportunistically keep uid_map fresh (see syncUidMap) so firestore.rules'
  // me() resolves correctly even for app versions that predate the explicit
  // post-signin sync call. Best-effort — never blocks the actual request.
  db.doc(`uid_map/${request.auth.uid}`)
    .set({ appUid: doc.id, updatedAt: Date.now() })
    .catch((err) => logger.error("resolveAppUser: uid_map sync failed", err));
  return { uid: doc.id, ...doc.data() };
}

// Phone normalization mirroring the app's PhoneUtils.normalizeForLogin, so a
// number typed in any common format (0700…, 700…, +93700…, 0093700…) resolves
// to the single canonical "+93…" (or an international "+…") that registration
// stored — otherwise the same person looks like "no account" or a new one.
function cleanPhone(raw) {
  const t = String(raw || "").trim();
  const plus = t.startsWith("+");
  const digits = t.replace(/\D/g, "");
  return plus ? "+" + digits : digits;
}

function normalizeAfghanPhone(raw) {
  let c = cleanPhone(raw);
  if (c.startsWith("+93")) return c;
  if (c.startsWith("0093")) return "+93" + c.slice(4);
  if (c.startsWith("93") && c.length >= 11) return "+" + c;
  c = c.replace(/^\+/, "");
  if (c.startsWith("0")) c = c.slice(1);
  return "+93" + c;
}

function normalizePhone(raw) {
  const c = cleanPhone(raw);
  return c.startsWith("+") ? c : normalizeAfghanPhone(raw);
}

async function assertAdmin(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admins only.");
  }
  return appUser;
}

/**
 * Find the account that already owns a phone number, in any shape it was stored.
 *
 * The uniqueness checks queried `phone` with the normalized `+93…` form, but
 * authenticateWithPassword resolves a login by `phoneDigits` — the subscriber
 * tail, which is normalization-independent. An account written before
 * normalization existed is stored as "0700123456", so the check found nothing
 * and let a second account be created on the same number. Both people then
 * share one login key, whoever the index returns first wins, and the other is
 * locked out of an account that still exists. That is not recoverable by the
 * person it happens to.
 *
 * So this asks the question the login actually asks. phoneDigits is written by
 * the deriveUserPhoneKey trigger and backfilled by adminBackfillPhoneKeys, but
 * an account it has not reached yet simply lacks the field — and equality on a
 * missing field matches nothing — so the two stored spellings of `phone` are
 * still checked behind it. Three limit(1) reads on an admin action is nothing;
 * a permanent lockout is not.
 *
 * @param {string} phone normalized (+93…)
 * @param {string} [raw] whatever the caller typed, if it differed
 * @param {string} [exceptUid] an account allowed to keep its own number
 * @returns {Promise<FirebaseFirestore.QueryDocumentSnapshot|null>}
 */
async function findAccountByPhone(phone, raw = "", exceptUid = "") {
  const users = db.collection("users");
  const key = phoneKey(phone || raw);
  const attempts = [];
  if (key) attempts.push(users.where("phoneDigits", "==", key).limit(2));
  if (phone) attempts.push(users.where("phone", "==", phone).limit(2));
  if (raw && raw !== phone) attempts.push(users.where("phone", "==", raw).limit(2));

  for (const q of attempts) {
    const snap = await q.get();
    const hit = snap.docs.find((d) => d.id !== exceptUid);
    if (hit) return hit;
  }
  return null;
}

/**
 * Refuse an action by a suspended account.
 *
 * Deliberately not folded into resolveAppUser: a suspended person must still be
 * able to sign in, read their own history and reach support -- otherwise a
 * suspension is indistinguishable from a broken account, and the one route for
 * disputing it is the route that gets closed. What stops is acting.
 */

/**
 * Suspension has been written two different ways, and each half only ever
 * closed half the door.
 *
 * adminSuspendUser writes `suspended: true`, which this function reads, so
 * every callable refuses — but the security rules gate direct writes on
 * `isApproved()`, which reads `status`, so a suspended provider could still
 * create salons, services, offers and posts straight from the client.
 *
 * resolveCustomerReport and the console's Users tab write `status:
 * "SUSPENDED"`, which the rules read — but nothing on the server did, so a
 * customer suspended for misconduct through the reports flow, which is the one
 * place a report leads to action, went on booking and paying as though nothing
 * had happened. The admin saw a red badge and believed it.
 *
 * Both fields now mean suspension on both sides. Reading both is also what
 * makes every account suspended before today start being enforced without a
 * migration.
 */
function isSuspended(appUser) {
  if (!appUser) return false;
  return appUser.suspended === true || appUser.status === "SUSPENDED";
}

function assertNotSuspended(appUser) {
  if (isSuspended(appUser)) {
    throw new HttpsError(
      "permission-denied",
      "This account is suspended. Please contact support."
    );
  }
}

/**
 * Log something a person should be told about, under a stable machine label.
 *
 * Alert policies match on `jsonPayload.alert` rather than on the message text.
 * A policy that greps prose breaks the day someone rewords a log line, and it
 * breaks silently — the alert simply stops firing, which is indistinguishable
 * from nothing going wrong. The label is the contract; the message is for the
 * human who reads it afterwards.
 *
 * Kinds in use:
 *   BOOKING_FAILED    a customer tried to book and could not
 *   SLOT_MISMATCH     a booking succeeded at a time the app should not have
 *                     offered — the client's slot grid and the salon's stored
 *                     opening hours disagree. Deliberately not BOOKING_FAILED:
 *                     nothing failed, and conflating them makes a working
 *                     product page as an outage.
 *   DUPLICATE_PHONE   two accounts claim one number, so login is ambiguous
 *   PAYMENT_FAILED    money moved, or failed to, without the record agreeing
 *   BACKUP_FAILED     the nightly export did not complete
 *   INTEGRITY_CRITICAL the nightly sweep found something that loses money
 *   ALERT_PIPELINE_TEST a deliberate drill — see adminTestAlert
 */
/** Append a tamper-evident record of a privileged admin action. Best-effort. */

async function logAdminAction(adminUser, action, details) {
  try {
    await db.collection("admin_audit").add({
      adminUid:  adminUser.uid,
      adminName: adminUser.name || "",
      action,
      details:   details || {},
      createdAt: Date.now(),
    });
  } catch (e) {
    logger.error("logAdminAction failed", e);
  }
}

// ── Multi-admin management ────────────────────────────────────────────────────

/** Promote a user to ADMIN (idempotent). The target is also marked APPROVED so
 *  a pending/suspended account can still administer. */

function appointmentEvent(appt, appointmentId, to, actor, reason) {
  return {
    appointmentId,
    bookingCode: appt.bookingCode || "",
    salonId:     appt.salonId     || "",
    customerId:  appt.customerId  || "",
    from:        appt.status      || "",
    to,
    at:          Date.now(),
    actorUid:    (actor && actor.uid)  || "system",
    actorRole:   (actor && actor.role) || "SYSTEM",
    actorName:   (actor && actor.name) || "",
    reason:      String(reason || ""),
  };
}

/**
 * Append to the trail from inside a transaction or batch.
 *
 * Preferred over the fire-and-forget version: the history entry then commits
 * with the status change it describes, so the two can never disagree.
 */

function writeAppointmentEvent(txOrBatch, appt, appointmentId, to, actor, reason) {
  txOrBatch.set(
    db.collection("appointment_events").doc(),
    appointmentEvent(appt, appointmentId, to, actor, reason)
  );
}

/** Append outside a transaction. Best-effort: never fail a booking over history. */

async function logAppointmentEvent(appt, appointmentId, to, actor, reason) {
  try {
    await db.collection("appointment_events").add(
      appointmentEvent(appt, appointmentId, to, actor, reason)
    );
  } catch (e) {
    logger.error("logAppointmentEvent failed", e);
  }
}

// ── Booking references and checkout reservations ─────────────────────────────
//
// Both halves of the marketplace need these: payments mints a booking code and
// hands back a reservation when a checkout fails, and bookings does the same
// when a booking is cancelled. Leaving them beside either one would make the
// other reach across a domain boundary for them.

// Refund a checkout-time reservation (referral credit + one promo use) when a
// reserved booking never durably completes — a HesabPay create/session failure,
// a failed write, or an abandoned online payment that later expires. Only
// payments written with `reserved:true` are ever routed here; legacy
// spend-at-settlement payments are untouched. Best-effort with logged failures.
async function refundReservation({ customerId, referralUsed, promoId }) {
  const used = Number(referralUsed || 0);
  if (used > 0 && customerId) {
    await db.doc(`users/${customerId}`)
      .set({ referralCredit: admin.firestore.FieldValue.increment(used) }, { merge: true })
      .catch((e) => logger.error("refundReservation: referral", e));
  }
  if (promoId) {
    await db.doc(`promo_codes/${promoId}`)
      .set({ usedCount: admin.firestore.FieldValue.increment(-1) }, { merge: true })
      .catch((e) => logger.error("refundReservation: promo", e));
  }
}

// ── Booking codes and the appointment event trail ────────────────────────────
//
// A booking used to be identifiable only by its Firestore document id: twenty
// random characters that nobody can read down a phone line. When a customer
// rings to ask what happened to her appointment, the person answering needs
// something she can say out loud, and a record of what actually happened to it.
//
// The alphabet and the normalizer live in lib/booking, which is unit-tested:
// getting either wrong means two customers can hold the same reference, or a
// correctly-read code fails to resolve.
function randomBookingCode() {
  return bookingCodeFromBytes(crypto.randomBytes(6));
}

/**
 * Reserve a booking code nobody else holds.
 *
 * The reservation is a document create, which fails if the id is taken — so
 * uniqueness is decided by Firestore rather than by a read-then-write that two
 * simultaneous bookings could both pass. A code reserved by a booking that then
 * fails to write is simply never used; that costs one tiny document, which is
 * the cheaper end of the trade against ever issuing the same code twice.
 */

async function reserveBookingCode(attempts = 6) {
  for (let i = 0; i < attempts; i++) {
    const code = randomBookingCode();
    try {
      await db.doc(`booking_codes/${code}`).create({ createdAt: Date.now() });
      return code;
    } catch (e) {
      if (i === attempts - 1) {
        logger.error("reserveBookingCode: exhausted attempts", e);
        throw new HttpsError("internal", "Could not allocate a booking reference.");
      }
    }
  }
}

/**
 * The shape of one entry in an appointment's history.
 *
 * Denormalizes salonId/customerId so the trail can be queried for "everything
 * that happened to this salon's bookings" without joining back through the
 * appointment, and the actor so the record still reads correctly after that
 * person's name or role changes.
 */

// ── Password derivation ───────────────────────────────────────────────────────
//
// Both halves of account handling need this: signing in derives the hash to
// compare, and an admin resetting a password or creating a salon derives the
// one to store. One implementation, because two would eventually disagree about
// the iteration count and lock people out of their own accounts.

// Byte-for-byte mirror of the Android PinHasher: PBKDF2WithHmacSHA256, 65,536
// iterations, 256-bit output, salt is Base64(NO_WRAP) bytes. Must match exactly
// or every PIN verification fails.
function pbkdf2Hash(pin, saltB64) {
  const salt = Buffer.from(String(saltB64), "base64");
  return crypto.pbkdf2Sync(String(pin), salt, 65536, 32, "sha256").toString("base64");
}


// ── Paging a whole collection, for the maintenance backfills ─────────────────
//
// Every backfill in this codebase was written the same wrong way twice over,
// and the admin console has been telling someone to "run again to continue" a
// job that could not continue:
//
//   1. No cursor. `orderBy(x).limit(300)` returns the SAME first 300 documents
//      on every call, so document 301 is unreachable however many times the
//      button is pressed — and `done: snap.size < limit` is then only ever true
//      when the entire collection fits in one page.
//   2. Ordered by createdAt. A query with orderBy returns none of the documents
//      that lack the field, and the documents needing a backfill are the oldest
//      ones — precisely the ones most likely to predate createdAt as well. The
//      sweep silently skips exactly what it was written to find.
//
// Ordering by __name__ cannot drop a document, because every document has one.
//
// [after] is a document id from a previous page's `cursor`. The cursor is a
// value, not a snapshot, so a page whose last document is deleted between two
// calls does not strand the run.
function idPage(collectionName, limit, after) {
  let q = db.collection(collectionName)
    .orderBy(admin.firestore.FieldPath.documentId())
    .limit(limit);
  if (after) q = q.startAfter(db.collection(collectionName).doc(after));
  return q.get();
}

/** The `cursor` and `done` a paged callable returns, given the page it read. */
function pageEnd(snap, limit, after) {
  return {
    cursor: snap.size ? snap.docs[snap.docs.length - 1].id : (after || ""),
    done:   snap.size < limit,
  };
}

/** The cursor a paged callable was called with, sanitised. */
function pageCursor(data) {
  const c = (data || {}).cursor;
  return typeof c === "string" ? c.trim() : "";
}


/**
 * A fixed-window counter in `rate_limits/{key}`.
 *
 * Lifted out of identity.js, where it guarded login, registration and the
 * phone lookup and nothing else — so the whole identity surface was covered
 * and every other callable was not. It is the same function, moved.
 *
 * Fails OPEN: if the counter itself cannot be read or written, the caller
 * proceeds. A throttle that turns a Firestore hiccup into "nobody can book
 * today" is worse than the abuse it prevents. Only the limit itself throws.
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

module.exports = {
  enforceRateLimit,
  findAccountByPhone,
  isSuspended,
  pbkdf2Hash,
  refundReservation, randomBookingCode, reserveBookingCode,
  admin, db, logger, alertable,
  assertDocId, resolveAppUser, cleanPhone,
  normalizeAfghanPhone, normalizePhone, assertAdmin,
  assertNotSuspended, logAdminAction, appointmentEvent,
  writeAppointmentEvent, logAppointmentEvent,
  idPage, pageEnd, pageCursor,
};
