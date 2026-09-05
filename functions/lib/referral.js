/**
 * A user's own shareable invite code.
 *
 * Pure, because the derivation has to agree in three places that never run
 * together: RegisterViewModel.kt writes it on the device at sign-up,
 * adminCreateSalon writes it on the server, and the backfill writes it for
 * every account that predates the referral programme. A code that disagrees
 * with the one a friend was told is not a wrong answer on a screen — it is an
 * invite that credits nobody, and neither side ever finds out why.
 *
 * The code is derived from the account's uid rather than stored randomly so it
 * is reproducible: the same account always yields the same code, and a backfill
 * that runs twice writes the same value the second time.
 */

const PREFIX = "SB";
const BASE_LEN = 6;
const STEP = 2;

/**
 * The shortest code a backfill may hand out.
 *
 * Registration derives attempt 0 on the device and writes it with no
 * uniqueness check of any kind (RegisterViewModel.kt:152) — it cannot do one,
 * because the users collection is not client-listable. That is survivable while
 * only accounts created since July hold codes. It stops being survivable if a
 * backfill fills the same six-character space with thousands more: every later
 * signup would then be rolling against a much fuller table, and a duplicate
 * credits whichever document the referral lookup's limit(1) returns first.
 *
 * So the backfill does not compete for that space. Starting at attempt 1 makes
 * every backfilled code eight characters, and an eight-character code can never
 * equal a six-character one. The two writers stop being able to collide at all,
 * which is a stronger guarantee than checking for collisions after the fact.
 */
const BACKFILL_MIN_ATTEMPT = 1;

/**
 * The code for [uid], or "" if this uid cannot supply one at this length.
 *
 * [attempt] is the collision escalation. Attempt 0 is the canonical form and
 * the one registration produces; each later attempt takes two more characters
 * of the uid. Six hex characters is 16.7 million codes, which collides sooner
 * than it sounds — at a thousand accounts the chance is already a few percent —
 * so the caller must be able to ask for a longer one rather than silently hand
 * two people the same code and credit the wrong one.
 *
 * Returns "" once the uid has no more characters to give, so an exhausted
 * search is reported rather than looping.
 */
function deriveReferralCode(uid, attempt = 0) {
  const hex = String(uid || "").replace(/-/g, "").toUpperCase();
  if (!hex) return "";
  const want = BASE_LEN + Math.max(0, Number(attempt) || 0) * STEP;
  // A shorter uid cannot be escalated: slicing past its end returns the same
  // string as the previous attempt, so the search would spin without changing
  // the candidate.
  if (hex.length < want) return "";
  return PREFIX + hex.slice(0, want);
}

/** How many attempts [uid] can supply, so a caller can bound its loop. */
function maxAttempts(uid) {
  const hex = String(uid || "").replace(/-/g, "");
  if (hex.length < BASE_LEN) return 0;
  return Math.floor((hex.length - BASE_LEN) / STEP) + 1;
}

module.exports = { deriveReferralCode, maxAttempts, PREFIX, BASE_LEN, BACKFILL_MIN_ATTEMPT };
