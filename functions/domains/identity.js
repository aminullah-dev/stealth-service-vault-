// identity — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { phoneKey } = require("../lib/phone");
const { defaultWorkingHours } = require("../lib/hours");
const { deriveReferralCode, maxAttempts, BACKFILL_MIN_ATTEMPT } = require("../lib/referral");
const { acceptedReferral, buildRegistrationDocument, selfRegisterRole } = require("../lib/registration");
const { authIsRecent, isHash, isSalt, rotationProblem } = require("../lib/password");
// The same normaliser salons use for nameKey, so a name is searchable under one
// spelling rather than two. See lib/categories.
const { normalize: normalizeName } = require("../lib/categories");
const { assertAdmin, assertDocId, assertNotSuspended, findAccountByPhone, idPage, logAdminAction, normalizePhone, pageCursor, pageEnd, pbkdf2Hash, resolveAppUser } = require("../shared");
const crypto = require("crypto");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { admin, alertable, db, logger } = require("../shared");

// Referral rewards (AFN). Both are granted when a referred user's identity is
// verified (see reviewKyc): the new user gets a welcome credit, the friend who
// invited them gets a referrer credit. Both are auto-applied at checkout.
const REFERRAL_WELCOME_CREDIT  = 100;

const REFERRAL_REFERRER_CREDIT = 100;

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
  // The same gate changePassword uses, for the same reason and on the same two
  // fields. Without it this callable was the way around that one: a session
  // alone could overwrite pinHash + salt while the Firebase Auth password
  // stayed as it was, which is the split state that stops BOTH passwords from
  // working. A gate the caller can decline is not a gate.
  //
  // Free for both real callers: SetNewPinViewModel and public/reset sign in on
  // the line immediately above their call, and a sign-in is what sets
  // auth_time.
  if (!authIsRecent(request.auth.token && request.auth.token.auth_time, Date.now())) {
    throw new HttpsError("failed-precondition",
      "Please sign in again before changing your password.");
  }
  const pinHash = String((request.data || {}).pinHash || "");
  const salt    = String((request.data || {}).salt || "");
  // Shape-checked rather than merely non-empty: a truncated or double-encoded
  // value is written once and then never matches the password again.
  if (!isHash(pinHash) || !isSalt(salt)) {
    throw new HttpsError("invalid-argument", "pinHash and salt are required.");
  }
  const appUser = await resolveAppUser(request);
  await db.doc(`users/${appUser.uid}`).update({ pinHash, salt });
  return { updated: true };
});

/**
 * Change your own password: the Firestore salt + pinHash AND the Firebase Auth
 * password derived from them, in one call that owns the ordering.
 *
 * The device used to do this itself, and the order it used could not be
 * recovered from. ChangePinViewModel reauthenticated, called
 * `auth.updatePassword(newAuthPassword)`, and only then called updatePinHash —
 * keeping the new salt and hash in coroutine locals. When that last call
 * failed, the Auth password was new and the stored hash was old, and BOTH
 * passwords stopped working: the new one fails the hash check, and the old one
 * passes it, is handed the old salt, derives the old Auth password, and is
 * refused by Firebase. The values needed to finish were gone with the
 * coroutine, so there was nothing to retry — only an admin reset.
 *
 * The password itself does not travel, exactly as in registerAccount: the
 * device sends the current password's hash as proof, plus the new salt, hash
 * and derived Auth password. Nothing here can be derived without the password,
 * and none of it is the password.
 *
 * Order matters and is the point of moving this. Firestore is written FIRST,
 * because its previous values have just been read and are therefore in hand to
 * put back if the Auth update then fails. The reverse order would need the old
 * *Auth password* to undo, and the server never sees it.
 *
 * The residual: a hard crash between the two writes still leaves the pair
 * disagreeing. That window is now a process death rather than a dropped mobile
 * connection — the failure this actually happened on — and adminResetPassword
 * is the recovery.
 */
exports.changePassword = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }

  // currentPinHash is a password verifier, so this endpoint is a place to
  // grind one. Keyed on the Auth account rather than the app uid: the caller
  // is authenticated, so this is the identity they cannot vary.
  await enforceRateLimit(`changepw:${request.auth.uid}`, 5, 60 * 60 * 1000);

  // The actual proof that the caller knows the CURRENT password.
  //
  // currentPinHash below cannot carry that proof and must not be mistaken for
  // it: firestore.rules allows `get` on the whole user document to its owner,
  // pinHash included, and the Android client already reads it for a local
  // pre-check. A signed-in session can therefore read the stored hash and send
  // it straight back. Gating on that alone would be weaker than the
  // client-side reauthenticate this callable replaced — a picked-up unlocked
  // phone could lock the owner out of her own account without ever knowing the
  // password. auth_time is the time of the last authentication event and
  // cannot be advanced by refreshing a token, only by really signing in again.
  //
  // This is only a boundary because the other two ways in are shut: pinHash and
  // salt are frozen on the users self-update rule (they were not, and a client
  // could write the pair directly), and updatePinHash carries the same gate.
  if (!authIsRecent(request.auth.token && request.auth.token.auth_time, Date.now())) {
    throw new HttpsError("failed-precondition",
      "Please sign in again before changing your password.");
  }

  const problem = rotationProblem(request.data);
  if (problem) {
    throw new HttpsError("invalid-argument", `Bad or missing ${problem}.`);
  }
  const d = request.data;

  // Deliberately NOT assertNotSuspended. A suspended account keeps read access
  // to its own history and to support (see the note on that helper); being
  // unable to change your own password is not part of a suspension, and an
  // account someone else may know the password to is the wrong thing to freeze.
  const appUser = await resolveAppUser(request);

  const oldPinHash = String(appUser.pinHash || "");
  const oldSalt    = String(appUser.salt || "");
  if (!oldPinHash || !oldSalt) {
    throw new HttpsError("failed-precondition", "This account has no password set.");
  }
  // Defence in depth, NOT the security boundary — auth_time above is that.
  // This catches a client that derived the hash wrongly, or a stale salt,
  // before either can be written over a working credential. Same
  // constant-time compare the login path uses, not a second one.
  if (!hashesEqual(d.currentPinHash, oldPinHash)) {
    throw new HttpsError("permission-denied", "Current password is incorrect.");
  }

  const email = String(appUser.firebaseEmail || "");
  if (!email) {
    throw new HttpsError("failed-precondition", "This account has no Firebase Auth email.");
  }
  // Resolved by email rather than request.auth.uid: the derived Auth password
  // belongs to the credential whose email is on the user document, and uid_map
  // may hold several Auth accounts for one person (see requestAccountDeletion).
  let authRecord;
  try {
    authRecord = await admin.auth().getUserByEmail(email);
  } catch (e) {
    throw new HttpsError("not-found", "No Firebase Auth user for this account.");
  }

  const ref = db.doc(`users/${appUser.uid}`);
  await ref.update({ pinHash: String(d.newPinHash), salt: String(d.newSalt) });

  try {
    await admin.auth().updateUser(authRecord.uid, { password: String(d.newAuthPassword) });
  } catch (e) {
    try {
      await ref.update({ pinHash: oldPinHash, salt: oldSalt });
    } catch (rollbackErr) {
      // The one state this function exists to prevent, reached anyway. Loud,
      // because nothing else will notice: the account looks ordinary and simply
      // refuses both passwords.
      // alertable, not logger.error: the monitoring policy matches on the
      // `alert` label, and this is the one outcome nobody finds on their own —
      // the account looks ordinary and simply refuses both passwords.
      alertable("password-rotation-stuck",
        "changePassword: rollback FAILED — account cannot sign in with either password",
        { uid: appUser.uid, authUid: authRecord.uid,
          rollbackErr: String(rollbackErr), authErr: String(e) });
      throw new HttpsError("internal",
        "The password change could not be completed or undone. Please contact support.");
    }
    logger.warn("changePassword: auth update failed, Firestore rolled back",
      { uid: appUser.uid, authErr: String(e) });
    throw new HttpsError("internal", "Could not update the sign-in password. Nothing was changed.");
  }

  // A successful rotation is the most takeover-relevant event on an account,
  // and until this line only the failures were written down.
  logger.info("changePassword: rotated", { uid: appUser.uid, authUid: authRecord.uid });
  return { ok: true };
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
  // Optional identity details (also editable later by the admin). Capped.
  const birthYear         = String(d.birthYear || "").trim().slice(0, 40);
  const tazkiraIssueDate  = String(d.tazkiraIssueDate || "").trim().slice(0, 40);
  const tazkiraExpiryDate = String(d.tazkiraExpiryDate || "").trim().slice(0, 40);

  if (!tazkiraNumber || !addressProvince || !addressDetail) {
    throw new HttpsError("invalid-argument", "Tazkira number and address are required.");
  }

  const appUser = await resolveAppUser(request);

  // The photo locations are DERIVED, never sent. The client used to pass two
  // https download URLs, which it obtained from ref.downloadUrl — a token URL,
  // served without authentication and outside storage.rules entirely. Those
  // strings then landed on the user document and were opened in a browser by
  // both admin surfaces. Deriving the path from the caller's own uid removes
  // the client-supplied string, and reading it back through the SDK puts the
  // rules in the path of every access.
  //
  // Existence is checked rather than assumed: without the old "both URLs are
  // non-empty" guard, a caller could otherwise reach PENDING with nothing
  // uploaded and land in the review queue as two broken images.
  const tazkiraPhotoPath = `kyc/${appUser.uid}/tazkira.jpg`;
  const selfiePhotoPath  = `kyc/${appUser.uid}/selfie.jpg`;
  const bucket = admin.storage().bucket();
  const [tazkiraThere, selfieThere] = await Promise.all([
    bucket.file(tazkiraPhotoPath).exists().then((r) => r[0]).catch(() => false),
    bucket.file(selfiePhotoPath).exists().then((r) => r[0]).catch(() => false),
  ]);
  if (!tazkiraThere || !selfieThere) {
    throw new HttpsError("failed-precondition", "Both photos must be uploaded first.");
  }
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
    tazkiraPhotoPath,
    selfiePhotoPath,
    // The legacy token URLs are cleared as their owner re-submits, so a
    // resubmission also revokes the old public link rather than leaving it
    // beside the new private path.
    tazkiraPhotoUrl: "",
    selfiePhotoUrl: "",
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
    msgKey:      approve ? "KYC_APPROVED" : "KYC_REJECTED",
    msgParams:   { reason: approve ? "" : String(reason || "") },
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
 * Registration, in one call.
 *
 * The device used to do this in three: create the Firebase Auth account, write
 * users/{uid}, and — if the second failed — delete the first again. Three round
 * trips from a handset on Afghan mobile data, where the compensating delete
 * needs the same connection whose loss is the reason it is running. Its Result
 * was discarded, so when it failed nothing recorded that it had.
 *
 * What that produced, measured on 2026-09-04: 117 Firebase Auth accounts, 12
 * users documents. Of the 106 accounts with no profile, 99 have no trace
 * anywhere else either — no appointment, payment, notification, favourite,
 * review, message or ticket under the appUid their synthetic address encodes.
 * An account that never did anything is not someone who left; it is a
 * registration that stopped between step one and step two. Four of them used
 * real Gmail addresses, so these are people, not fixtures.
 *
 * Here the whole sequence is one server invocation. The rollback runs on the
 * server, over a connection that did not just fail. The users write goes
 * through Admin credentials, so the fourteen conditions on the rules' create
 * path cannot reject a document the server itself composed.
 *
 * The double-registration race is narrowed, not closed, and the difference
 * matters. The uniqueness check and the write are in one invocation instead of
 * two round trips, so the window is tens of milliseconds rather than seconds —
 * but they are not in a transaction and no document is keyed on the phone, so
 * two concurrent invocations can still both read empty and both write.
 * deriveUserPhoneKey raises DUPLICATE_PHONE afterwards, which is detection, and
 * that alert's own text says a person has to resolve it. Closing it properly
 * needs a transaction on a phone-keyed index document; until then this is an
 * improvement in odds, not a guarantee.
 *
 * The password never appears here in plaintext. The device still runs PinHasher
 * and sends salt, pinHash and the derived auth password — the same three values
 * it already sends to Firebase Auth and to updatePinHash, over the same TLS.
 * Moving the orchestration does not move the secret.
 */
exports.registerAccount = onCall({ region: "us-central1" }, async (request) => {
  const d        = request.data || {};
  const rawPhone = String(d.phone || "").trim();
  const phone    = normalizePhone(rawPhone);

  // Unauthenticated by necessity — this IS the sign-up. Throttled on both axes
  // for the same reason authenticateWithPassword is: one IP must not mint
  // accounts in bulk, and one number must not be ground at indefinitely.
  //
  // The two limits are deliberately far apart. An IP is not a person here:
  // Afghan mobile operators egress whole cities through a handful of NAT
  // addresses, so every customer of one carrier in Kabul shares a counter. A
  // limit tight enough to be interesting to an attacker would refuse real women
  // during exactly the moment a campaign is working, and it would reach them as
  // the generic failure. 60/hour matches what login and lookup already allow
  // from one address (120/hour each) rather than sitting an order of magnitude
  // under them for no stated reason.
  //
  // The phone is the axis that actually identifies someone, is not shared by
  // NAT, and is required to be unused — so it stays tight. That is where
  // repeated abuse of this endpoint has to show up.
  await enforceRateLimit(`register-ip:${callerIp(request)}`, 60, 60 * 60 * 1000);
  await enforceRateLimit(`register-phone:${phone || "unknown"}`, 5, 60 * 60 * 1000);

  const name         = String(d.name || "").trim();
  const email        = String(d.email || "").trim();
  const salt         = String(d.salt || "");
  const pinHash      = String(d.pinHash || "");
  const authPassword = String(d.authPassword || "");

  // Stated explicitly rather than coerced. The rules' create path refuses an
  // ADMIN self-registration and this is now the writer that path was guarding
  // against, so the refusal has to exist here too — as a rejection, not as a
  // silent downgrade to CUSTOMER that would hide the attempt.
  const role = selfRegisterRole(d.role);
  if (!role) {
    throw new HttpsError("permission-denied", "Accounts may self-register only as CUSTOMER or PROVIDER.");
  }
  const isProvider = role === "PROVIDER";

  if (!name || !rawPhone || !salt || !pinHash || !authPassword) {
    throw new HttpsError("invalid-argument", "name, phone, salt, pinHash and authPassword are required.");
  }

  const salonName = String(d.salonName || "").trim();
  const district  = String(d.district || "").trim();
  const services  = Array.isArray(d.services) ? d.services.map(String).filter(Boolean) : [];
  if (isProvider && (!salonName || !district || services.length === 0)) {
    throw new HttpsError("invalid-argument", "A provider needs a salon name, a district and at least one service.");
  }

  // The phone is the login identifier, so it must be unique — and unlike the
  // client's pre-check, this one runs in the same invocation as the write it
  // guards.
  // Which field collided, carried in details. Both collisions are
  // "already-exists" and the client used to render either as "this phone number
  // is taken" — which is the wrong sentence for the exact people this callable
  // exists to rescue. Someone holding an orphaned credential under a real email
  // has no profile, so the phone check passes and the Auth create fails; being
  // told her PHONE is taken sends her to change the one field that was fine.
  if (await findAccountByPhone(phone, rawPhone)) {
    throw new HttpsError("already-exists", "An account already uses this phone number.",
      { field: "phone" });
  }

  const uid           = crypto.randomUUID();
  const firebaseEmail = email ? email.toLowerCase() : `${uid.replace(/-/g, "")}@sb.app`;

  // The uniqueness check registration has never been able to do.
  //
  // lib/referral records why: the device "writes it with no uniqueness check of
  // any kind ... it cannot do one, because the users collection is not
  // client-listable". True of a client; not true here. Six hex characters is
  // 16.7 million codes and collides at a few percent by a thousand accounts,
  // and a duplicate credits whichever document the referral lookup's limit(1)
  // happens to return first — an invite that pays the wrong person, with
  // nothing on either side showing why.
  //
  // Only this writer is fixed. adminCreateSalon still builds the code inline
  // and unchecked, so a collision remains reachable from that direction; it is
  // a separate change and is not made here.
  let referralCode = "";
  const codeAttempts = maxAttempts(uid);
  for (let attempt = 0; attempt < codeAttempts; attempt += 1) {
    const candidate = deriveReferralCode(uid, attempt);
    if (!candidate) break;
    const taken = await db.collection("users")
      .where("referralCode", "==", candidate).limit(1).get();
    if (taken.empty) { referralCode = candidate; break; }
  }

  const referredBy = acceptedReferral(d.referredBy, referralCode);

  let authUid = "";
  try {
    const created = await admin.auth().createUser({ email: firebaseEmail, password: authPassword });
    authUid = created.uid;
  } catch (e) {
    if (e && e.code === "auth/email-already-exists") {
      throw new HttpsError("already-exists", "An account already uses this email address.",
        { field: "email" });
    }
    logger.error("registerAccount: auth create failed", e);
    throw new HttpsError("internal", "Could not create the account. Please try again.");
  }

  try {
    await db.doc(`users/${uid}`).set(buildRegistrationDocument({
      uid, name, phone, email, role,
      pinHash, salt, firebaseEmail,
      createdAt: Date.now(),
      referralCode, referredBy,
      salonName, district, services,
    }));
  } catch (e) {
    // The compensation the handset could not be trusted with, run where the
    // network did not just fail.
    //
    // BOTH halves are deleted, not just the credential. A single-document set()
    // can raise DEADLINE_EXCEEDED or UNAVAILABLE after the commit has actually
    // landed — that uncertainty is the whole reason compensating deletes are
    // fragile — so "the write threw" does not mean "the document is absent".
    // Deleting only the Auth account would then leave users/{uid} holding her
    // phone number with no credential behind it, which is precisely the pair
    // the salon step below refuses to create: findAccountByPhone would refuse
    // her number forever, sign-in would fail, and deleteUser having SUCCEEDED
    // means nothing would have alerted. adminDeleteUser removes both for the
    // same reason (domains/admin.js).
    //
    // Worse if left: that document carries a real firebaseEmail with no Auth
    // account, so a later registration on the same address succeeds and
    // resolveAppUser's where("firebaseEmail","==",…).limit(1) — no orderBy —
    // starts resolving two people to whichever id sorts first. The users create
    // rule was written against exactly that.
    //
    // Nothing references the document yet: uid_map and the salon come after.
    const failed = [];
    await admin.auth().deleteUser(authUid)
      .catch((err) => failed.push(`auth:${err && err.code ? err.code : err}`));
    await db.doc(`users/${uid}`).delete()
      .catch((err) => failed.push(`users:${err && err.code ? err.code : err}`));

    if (failed.length) {
      // The one outcome nobody can discover on their own: she holds a login
      // that resolves to nothing, or a number that can never be registered
      // again, and no screen anywhere explains either.
      alertable("REGISTRATION_ORPHANED",
        "registerAccount: rollback incomplete after the profile write failed",
        { authUid, uid, failed });
    }
    logger.error("registerAccount: users write failed", e);
    throw new HttpsError("internal", "Could not create the account. Please try again.");
  }

  // Best effort from here down: the account exists and is correct, and nothing
  // below is worth undoing it for.

  // The bridge, written now rather than left to the client's first syncUidMap
  // call — one less round trip that has to survive the same connection.
  await db.doc(`uid_map/${authUid}`).set({ appUid: uid, updatedAt: Date.now() })
    .catch((e) => logger.warn("registerAccount: uid_map write failed; login will retry", e));

  let salonId = "";
  if (isProvider) {
    // Deliberately not rolled back on failure, and deliberately not fatal.
    // Deleting the Auth account here would leave the users document behind, and
    // that pair is unrecoverable: the phone now has an account so registration
    // refuses it, and there is no credential behind it so signing in cannot
    // work either. The number is burned and the person cannot tell why. The
    // details are on the document, so the next sign-in finishes the job.
    try {
      ({ salonId } = await createSalonForProvider(uid, name, { salonName, district, services }));
    } catch (e) {
      logger.error("registerAccount: salon creation failed; a later sign-in will finish it", e);
    }
  }

  return { uid, firebaseEmail, role, salonId };
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
  assertNotSuspended(appUser);
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

  return createSalonForProvider(appUser.uid, appUser.name || "", {
    salonName, district, services,
  });
});

/**
 * The salon itself, shared by the two callers that can create one.
 *
 * registerAccount makes it in the same server call that makes the account;
 * createProviderSalon makes it afterwards, for a provider whose registration
 * got as far as her user document and no further. Both need identical
 * defaults — a salon that differs depending on which path produced it is a
 * salon that behaves differently for reasons nobody can see.
 */
async function createSalonForProvider(uid, providerName, { salonName, district, services }) {
  // One salon per provider — return the existing one instead of duplicating
  // (e.g. if the client retries after a dropped response).
  const existing = await db.collection("salons")
    .where("providerId", "==", uid).limit(1).get();
  if (!existing.empty) {
    await clearPendingSalon(uid);
    return { salonId: existing.docs[0].id, alreadyExisted: true };
  }

  const ref = db.collection("salons").doc();
  await ref.set({
    providerId:          uid,
    providerName:        providerName || "",
    salonName:           String(salonName),
    district:            String(district),
    services:            services.map(String),
    isAvailable:         false,   // hidden until an admin approves the provider
    rating:              0,
    // The same week the provider editor shows by default, so the owner who
    // opens her profile, sees Saturday to Thursday 9–18 and changes nothing has
    // a salon that can actually be booked. Stored empty, it never could be.
    workingHours:        defaultWorkingHours(),
    slotDurationMinutes: 60,
    pricePerService:     {},
    confirmedCount:      0,
    isVerified:          false,
  });
  await clearPendingSalon(uid);
  return { salonId: ref.id };
}

/**
 * Forget the salon details registration parked on the account.
 *
 * They exist only so a sign-in can finish a salon that registration could not
 * (see UserDocument.pendingSalonName). Once the salon is real they are stale
 * copies of data that now lives on the salon itself, and a stale copy is
 * something that will eventually be read as current.
 */
async function clearPendingSalon(uid) {
  await db.doc(`users/${uid}`)
    .update({ pendingSalonName: "", pendingSalonDistrict: "", pendingSalonServices: [] })
    .catch(() => {});   // the salon exists either way; this is only tidying
}

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

  // phoneDigits first, then both stored spellings — the same order the login
  // itself resolves in, so "this number has no account" here and "wrong phone
  // number" at sign-in can never disagree about the same person.
  const doc = await findAccountByPhone(phone, raw);
  if (!doc) return { found: false };
  const u = doc.data();
  return {
    found:         true,
    uid:           doc.id,
    firebaseEmail: u.firebaseEmail || "",
    email:         u.email || "",
  };
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
    // recipientId, not uid. Every one of the notification writers uses
    // recipientId and no document has ever carried a `uid` field, so an
    // equality on it matched nothing: this loop committed cleanly, reported
    // success, and left the departing account's entire notification history
    // in place — salon names, service names, amounts and timestamps, on a
    // path whose whole job is erasing exactly that.
    ["notifications", "recipientId"],
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

// ── adminBackfillPhoneKeys ────────────────────────────────────────────────────
//
// Writes the derived lookup keys — phoneDigits and nameKey — onto accounts that
// predate them.
//
// deriveUserPhoneKey keeps both current from here on, but a trigger only fires
// on a write: an account nobody has touched since the field was introduced
// simply does not have it. For phoneDigits that meant a locked-out admin. For
// nameKey it would mean an admin search that confidently returns nothing, which
// is worse — a lockout announces itself, an empty result set looks like an
// answer.
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
  const after = pageCursor(request.data);

  // Was orderBy("createdAt").limit(limit) with no cursor: it re-read the same
  // first 300 accounts on every press, so account 301 was unreachable, and it
  // dropped every account written before createdAt existed — which is exactly
  // the population missing these keys. See idPage.
  const snap = await idPage("users", limit, after);

  const seen = new Map();   // key -> first uid that claimed it
  const collisions = [];
  let written = 0;

  for (const d of snap.docs) {
    const data = d.data();
    const key  = phoneKey(data.phone);
    // Derived the same way as the trigger. Written even when empty, so that a
    // nameless account still appears in a name-ordered admin list rather than
    // being dropped by the orderBy.
    const name = normalizeName(data.name);

    const patch = {};
    if (key && data.phoneDigits !== key) patch.phoneDigits = key;
    if (data.nameKey !== name) patch.nameKey = name;

    // Only a usable phone can collide. This used to `continue` here, which also
    // skipped everything below it — so an account with an unreadable phone got
    // no keys at all rather than the one key it could still have.
    if (key) {
      if (seen.has(key)) {
        collisions.push({ key, uids: [seen.get(key), d.id] });
      } else {
        seen.set(key, d.id);
      }
    }

    if (Object.keys(patch).length > 0) {
      await d.ref.update(patch);
      written += 1;
    }
  }

  await logAdminAction(me, "BACKFILL_USER_KEYS", {
    scanned: snap.size, written, collisions: collisions.length,
  });
  if (collisions.length) {
    logger.error("adminBackfillPhoneKeys: duplicate phone keys", { collisions });
  }
  return { ok: true, scanned: snap.size, written, collisions, ...pageEnd(snap, limit, after) };
});

// ── adminRevokeKycUrls ───────────────────────────────────────────────────────
//
// Closes the exposure the token URLs left behind.
//
// Every KYC photo uploaded before today has a download URL on its user
// document, and that URL is a capability: `?alt=media&token=…` is served
// without authentication, storage.rules never sees the request, and the string
// was opened in a browser by both admin surfaces. Changing the code stops new
// ones being minted; it does nothing about the ones already issued, which stay
// valid for as long as the token does — which is forever.
//
// So this does two things per account, and the second is the one that matters:
//
//   1. writes the derived path and clears the stored URL, and
//   2. rotates `firebaseStorageDownloadTokens` on the object itself, which
//      invalidates every URL ever handed out for it.
//
// Rotating rather than deleting the token keeps the object reachable through
// the SDK, which is how the app and the console now read it.
exports.adminRevokeKycUrls = onCall(
  { region: "us-central1", timeoutSeconds: 300 },
  async (request) => {
    const me = await assertAdmin(request);
    const data = request.data || {};
    const limit  = Math.min(200, Math.max(1, Number(data.limit || 100)));
    const dryRun = data.dryRun === true;
    const after  = typeof data.cursor === "string" ? data.cursor.trim() : "";

    // Ordered by __name__ for the same reason as the referral backfill: an
    // orderBy on any other field silently drops the documents that lack it, and
    // the accounts holding the oldest URLs are the likeliest to lack anything.
    let q = db.collection("users")
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(limit);
    if (after) q = q.startAfter(db.collection("users").doc(after));
    const snap = await q.get();

    const bucket = admin.storage().bucket();
    let cleared = 0;
    let rotated = 0;
    const failures = [];

    for (const d of snap.docs) {
      const u = d.data() || {};
      const paths = [`kyc/${d.id}/tazkira.jpg`, `kyc/${d.id}/selfie.jpg`];
      const present = [false, false];

      // Rotate whenever an object exists, not only when a URL is on the
      // document: a URL that was copied out and then removed from Firestore is
      // exactly the one still circulating.
      for (let i = 0; i < paths.length; i += 1) {
        const path = paths[i];
        try {
          const file = bucket.file(path);
          const [exists] = await file.exists();
          if (!exists) continue;
          present[i] = true;
          if (dryRun) { rotated += 1; continue; }
          await file.setMetadata({
            metadata: { firebaseStorageDownloadTokens: crypto.randomUUID() },
          });
          rotated += 1;
        } catch (e) {
          failures.push({ path, error: String((e && e.message) || e) });
        }
      }

      // A path is written only where the photo is really there. Writing it for
      // everyone would be simpler and would quietly destroy the meaning of the
      // field: every account would then claim a tazkira, the console would
      // render an <img> for each, and ten of the twelve would fail to load and
      // read as "not uploaded" — which is what an account with no photo should
      // say, but arrived at by a broken fetch rather than by an empty field.
      const patch = {};
      if (present[0]) patch.tazkiraPhotoPath = paths[0];
      if (present[1]) patch.selfiePhotoPath  = paths[1];
      if (String(u.tazkiraPhotoUrl || "")) patch.tazkiraPhotoUrl = "";
      if (String(u.selfiePhotoUrl || ""))  patch.selfiePhotoUrl  = "";
      if (!Object.keys(patch).length) continue;
      if (!dryRun) await d.ref.update(patch);
      cleared += 1;
    }

    const done = snap.size < limit;
    const cursor = snap.size ? snap.docs[snap.docs.length - 1].id : after;

    await logAdminAction(me, "REVOKE_KYC_URLS", {
      scanned: snap.size, cleared, rotated, dryRun, failures: failures.length,
    });
    if (failures.length) {
      logger.error("adminRevokeKycUrls: could not rotate", { failures });
    }
    return { ok: true, dryRun, scanned: snap.size, cleared, rotated, failures, cursor, done };
  });

// ── adminBackfillReferralCodes ───────────────────────────────────────────────
//
// The invite card in the customer's profile is the only place in the app that
// shares SafeBeauty itself, and it opens with `if (code.isBlank()) return` —
// no card, no empty state, nothing. An account with no referralCode has no way
// to invite anyone and no way to find out why.
//
// referralCode is written once, at registration, and only since the referral
// programme shipped on 2026-07-12. Every account older than that has never had
// one; the rules freeze the field against client writes, so the app cannot fix
// itself. This is the only thing that can.
//
// Deliberately unlike adminBackfillPhoneKeys, which this is otherwise modelled
// on, in two ways:
//
//   1. It pages with a cursor. That one restarts from the beginning on every
//      call, so pressing its button twice rescans the same first 300 accounts;
//      account 301 is unreachable no matter how many times you press it.
//   2. It orders by document id, not createdAt. `orderBy` returns none of the
//      documents missing the field, and an account old enough to lack a
//      referralCode is exactly the kind of account that predates createdAt too.
//      Ordering by __name__ can never drop a document, because every document
//      has one.
// ── adminBackfillWorkingHours ─────────────────────────────────────────────────
//
// Every salon created before today was stored with `workingHours: []`, while
// the provider's own editor filled the screen with a default week. So an owner
// opened her profile, saw Saturday to Thursday 9–18, agreed with it, changed
// nothing — and saved nothing. computeSlots then produced no slots on any day
// and her salon could not be booked at all. Not a failure: an absence, and the
// one screen that could have shown it showed the opposite.
//
// Fixing the two creation paths only helps salons made from now on. These are
// the ones already listed, already found in search, and already unbookable.
//
// Walks by document id rather than a field, because a missing field cannot be
// queried for and a salon old enough to lack workingHours is exactly the kind
// that predates whatever else we might have ordered by. Skips any salon whose
// owner has set real hours, so it is safe to run repeatedly and safe to run
// after someone has already fixed theirs by hand.
exports.adminBackfillWorkingHours = onCall(
  { region: "us-central1", timeoutSeconds: 300 },
  async (request) => {
    const me = await assertAdmin(request);
    const data = request.data || {};
    const limit  = Math.min(400, Math.max(1, Number(data.limit || 200)));
    const dryRun = data.dryRun === true;
    const after  = pageCursor(data);

    const snap = await idPage("salons", limit, after);

    let filled = 0;
    let alreadyHad = 0;
    const names = [];
    for (const d of snap.docs) {
      const salon = d.data() || {};
      // An owner who has deliberately closed every day still has entries, and
      // that is a decision rather than an absence — hasBookableWeek would call
      // it unbookable, which it is, but it is hers to make. Only a genuinely
      // empty array is filled in.
      if (Array.isArray(salon.workingHours) && salon.workingHours.length > 0) {
        alreadyHad += 1;
        continue;
      }
      // salonName, not name. Salons have never had a `name` field, so the list
      // an admin reads before pressing this was always a column of document ids.
      if (names.length < 20) names.push(salon.salonName || d.id);
      if (!dryRun) await d.ref.update({ workingHours: defaultWorkingHours() });
      filled += 1;
    }

    if (!dryRun) {
      await logAdminAction(me, "BACKFILL_WORKING_HOURS", {
        scanned: snap.size, filled, alreadyHad,
      });
    }
    return {
      ok: true, dryRun, scanned: snap.size, filled, alreadyHad, names,
      ...pageEnd(snap, limit, after),
    };
  }
);

exports.adminBackfillReferralCodes = onCall(
  // A page costs up to two sequential round trips per account — a uniqueness
  // query and a write — so 100 accounts is a few hundred RPCs in series. The
  // default 60s deadline is enough for that and not enough for much more, and a
  // deadline mid-page is the one failure this design cannot make idempotent-free
  // progress through, so the ceiling is raised and the page kept small.
  { region: "us-central1", timeoutSeconds: 300 },
  async (request) => {
    const me = await assertAdmin(request);
    const data = request.data || {};
    const limit  = Math.min(200, Math.max(1, Number(data.limit || 100)));
    const dryRun = data.dryRun === true;
    const after  = pageCursor(data);

    const snap = await idPage("users", limit, after);

    // Codes handed out during this page. A real run does not depend on it: each
    // code is written before the next account is examined, so the uniqueness
    // query below sees it. A dry run writes nothing, so this Set is the only
    // thing standing between two accounts that want the same code — and it is
    // per-page, which is why a dry run's collision count is a floor rather than a
    // number. The count it exists to report, how many accounts still have none,
    // is unaffected.
    const claimedHere = new Set();
    let written = 0;
    let alreadyHad = 0;
    const collisions = [];   // uid pairs that wanted the same code
    const unresolved = [];   // uids that could not be given one at all

    for (const d of snap.docs) {
      if (String(d.data().referralCode || "").trim()) { alreadyHad += 1; continue; }

      const attempts = maxAttempts(d.id);
      let code = "";
      for (let attempt = BACKFILL_MIN_ATTEMPT; attempt < attempts; attempt += 1) {
        const candidate = deriveReferralCode(d.id, attempt);
        if (!candidate) break;
        if (claimedHere.has(candidate)) { collisions.push({ code: candidate, uid: d.id }); continue; }
        const taken = await db.collection("users")
          .where("referralCode", "==", candidate).limit(1).get();
        if (!taken.empty && taken.docs[0].id !== d.id) {
          collisions.push({ code: candidate, uid: d.id, heldBy: taken.docs[0].id });
          continue;
        }
        code = candidate;
        break;
      }

      if (!code) { unresolved.push(d.id); continue; }

      claimedHere.add(code);
      if (!dryRun) {
        await d.ref.update({ referralCode: code });
        written += 1;
      }
    }

    const { cursor, done } = pageEnd(snap, limit, after);

    await logAdminAction(me, "BACKFILL_REFERRAL_CODES", {
      scanned: snap.size, written, alreadyHad, dryRun,
      collisions: collisions.length, unresolved: unresolved.length,
  });
  if (unresolved.length) {
    logger.error("adminBackfillReferralCodes: could not derive a code", { unresolved });
  }

  return {
    ok: true,
    dryRun,
    scanned: snap.size,
    written,
    alreadyHad,
    // needed counts every account without a code; fixable is the subset this
    // can actually do something about. Reporting only `needed` would let a dry
    // run promise a repair for accounts it will skip.
    needed: snap.size - alreadyHad,
    fixable: snap.size - alreadyHad - unresolved.length,
    collisions,
    unresolved,
    cursor,
    done,
  };
  });


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


// ── deriveUserPhoneKey ────────────────────────────────────────────────────────
//
// Keeps the login lookup key in step with the phone it is derived from.
//
// authenticateWithPassword resolves an account by three indexed attempts: the
// normalized phone, the raw phone, and this key. The first two only match when
// the stored form happens to equal what was typed, and the key is what covers
// everything else — a number stored without its country code, or with a leading
// zero, or in whatever shape an older version of the app wrote.
//
// It used to be populated by a callable an admin had to remember to run. That
// is how the platform's own admin ended up locked out: the account predated the
// app's normalization, the backfill had never been run against production, and
// the tool for fixing it was itself behind assertAdmin — the fix sat behind the
// door it had closed.
//
// So it is derived here instead, on every write, and nobody has to remember
// anything. Server-written, so the rules can keep it frozen against clients: an
// account that could choose its own login key could claim another's.
exports.deriveUserPhoneKey = onDocumentWritten(
  { document: "users/{uid}", region: "us-central1" },
  async (event) => {
    const after = event.data && event.data.after;
    if (!after || !after.exists) return;

    const u = after.data() || {};

    // Two derived keys, both written here for the same reason: a lookup value
    // the client must not choose, kept in step with the field it comes from.
    //
    //   phoneDigits — how authenticateWithPassword finds an account
    //   nameKey     — how the admin console searches for one
    //
    // nameKey is written even when it is empty. Firestore drops documents that
    // lack the orderBy field, so a user with no name would be invisible in a
    // name-ordered admin list — present in the count, absent from the page, and
    // impossible to act on. The salon path learned this the same way (see
    // deriveSalonFields and sortRating).
    const wantPhone = phoneKey(u.phone);
    const wantName  = normalizeName(u.name);

    const patch = {};
    if (wantPhone && u.phoneDigits !== wantPhone) patch.phoneDigits = wantPhone;
    if (u.nameKey !== wantName) patch.nameKey = wantName;

    // Nothing to derive. This is also what stops the update below from
    // retriggering this function forever.
    if (Object.keys(patch).length === 0) return;

    await after.ref.update(patch);

    // Only a phone change can create an ambiguous login.
    if (!patch.phoneDigits) return;
    const want = wantPhone;

    // Two accounts sharing a subscriber number makes login ambiguous for both:
    // whoever the index returns first wins, and the other person signs in to a
    // stranger's account or not at all. It cannot be resolved automatically —
    // only a person knows whether it is one customer registered twice or two
    // customers who typed the same number — so it is surfaced, not guessed at.
    const others = await db.collection("users")
      .where("phoneDigits", "==", want)
      .limit(3)
      .get();
    const clash = others.docs.filter((d) => d.id !== after.id);
    if (clash.length) {
      // Not BOOKING_FAILED, which is documented as "a customer tried to book
      // and could not". Two accounts sharing a number is a real problem and a
      // different one, and mislabelling it means a backfill that touches old
      // accounts — surfacing every historical duplicate at once — reads as a
      // checkout outage to whoever is woken up.
      alertable("DUPLICATE_PHONE", "Two accounts share one phone number", {
        phoneDigits: want,
        uids: [after.id, ...clash.map((d) => d.id)],
      });
    }
  }
);
