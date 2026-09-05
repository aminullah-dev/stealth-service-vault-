/**
 * Password rotation, as the part that can be tested without a network.
 *
 * Changing a password touches two systems that cannot be written atomically:
 * the Firestore user document (salt + pinHash) and the Firebase Auth password
 * derived from them. They must agree, because sign-in uses both — the server
 * verifies the typed password against the stored hash and hands back the salt,
 * and the device then derives the Auth password from that salt. If the two
 * halves disagree, NEITHER the old nor the new password works:
 *
 *   stored hash old, Auth password new -> the new password fails the hash
 *     check; the old password passes it, returns the old salt, derives the old
 *     Auth password, and Firebase refuses it.
 *   stored hash new, Auth password old -> the mirror image.
 *
 * So the order is not a style question. The rotation writes Firestore first,
 * because the old salt and hash have just been read and are therefore in hand
 * to put back; the reverse order would need the old *Auth password*, which the
 * server never sees. See changePassword in domains/identity.js.
 *
 * Pure and dependency-free: it decides whether someone can still get into their
 * account, so it is worth testing exactly.
 */

/** Base64 as PinHasher emits it — NO_WRAP, so no newlines, and padded. */
const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;

/**
 * PinHasher's fixed shapes, on all three clients: a 16-byte salt and a
 * 256-bit hash, both base64. 16 bytes -> 24 characters, 32 bytes -> 44.
 * Checking the length is what stops a client sending a truncated or
 * double-encoded value that would be stored and never match again.
 */
const SALT_B64_LEN = 24;
const HASH_B64_LEN = 44;

function isHash(s) {
  return typeof s === "string" && s.length === HASH_B64_LEN && BASE64.test(s);
}

function isSalt(s) {
  return typeof s === "string" && s.length === SALT_B64_LEN && BASE64.test(s);
}

/**
 * The name of the first field that is wrong, or null when the request is
 * well-formed. Returning which field rather than a boolean keeps the thrown
 * message specific without the caller re-deriving it.
 */
function rotationProblem(d) {
  const data = d || {};
  if (!isHash(data.currentPinHash)) return "currentPinHash";
  if (!isHash(data.newPinHash)) return "newPinHash";
  if (!isHash(data.newAuthPassword)) return "newAuthPassword";
  if (!isSalt(data.newSalt)) return "newSalt";
  // Domain separation, checked rather than assumed. pinHash is PBKDF2(pw) and
  // the Auth password is PBKDF2("AUTH:" + pw) over the same salt, so they can
  // never be equal unless a client has derived one of them wrongly. Storing a
  // pinHash that IS the Auth password would make the stored hash a working
  // credential rather than a verifier for one.
  if (data.newPinHash === data.newAuthPassword) return "newAuthPassword";
  return null;
}

/**
 * How recently the caller must have actually entered their password.
 *
 * Five minutes is Firebase's own notion of a "recent login" for sensitive
 * operations, and it is long enough to type a new password twice.
 */
const RECENT_AUTH_MS = 5 * 60 * 1000;

/**
 * The same gate, widened, for updatePinHash only.
 *
 * changePassword is called by a client this repository controls, which forces
 * a token refresh after reauthenticating, so five minutes is exact. updatePinHash
 * is also called by builds already on phones — v2.1.0's change-password path —
 * whose token may still be the one minted at sign-in, because nothing in that
 * build forces a refresh. Holding those to five minutes would tell a woman who
 * signed in this morning to sign in again in order to change her password.
 *
 * Thirty minutes still shuts what the gate is for: a long-lived stolen session
 * being used to overwrite the credential pair. It does not admit anything a
 * five-minute window would have kept out for more than half an hour.
 */
const LEGACY_AUTH_MS = 30 * 60 * 1000;

/**
 * Whether the token was minted from a real authentication just now.
 *
 * This, and not the hash comparison, is what proves the caller knows the
 * current password. `currentPinHash` cannot carry that proof: firestore.rules
 * grants `allow get: if isSignedIn() && ownsDoc(uid)` on the WHOLE user
 * document, pinHash included, and the Android client already reads it for its
 * local pre-check. Anyone holding a signed-in session can therefore read the
 * stored hash and send it straight back — so a rotation gated on that alone
 * would let a picked-up unlocked phone change the password without knowing it,
 * which is strictly weaker than the client-side reauthenticate it replaced.
 *
 * `auth_time` is the time of the last authentication event, not of token
 * issuance, so it cannot be advanced by refreshing a token — only by actually
 * signing in or reauthenticating.
 */
function authIsRecent(authTimeSeconds, nowMs, windowMs = RECENT_AUTH_MS) {
  const t = Number(authTimeSeconds);
  if (!Number.isFinite(t) || t <= 0) return false;
  const age = Number(nowMs) - t * 1000;
  // A token from the future is a clock problem, not a fresh login.
  if (age < -60 * 1000) return false;
  return age <= windowMs;
}

module.exports = {
  BASE64,
  SALT_B64_LEN,
  HASH_B64_LEN,
  RECENT_AUTH_MS,
  LEGACY_AUTH_MS,
  authIsRecent,
  isHash,
  isSalt,
  rotationProblem,
};
