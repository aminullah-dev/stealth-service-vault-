/**
 * Phone identity for login lookup.
 *
 * The same person's number reaches this system in at least four shapes —
 * "+93700123456", "0700123456", "93700123456", "700123456" — because numbers
 * were stored inconsistently before normalization existed. Login has to resolve
 * all of them to one account.
 *
 * That used to be done by reading every user document and comparing trailing
 * digits in JavaScript, which is correct and does not scale: at 100,000 users
 * every sign-in read 100,000 documents. This reduces the same idea to a single
 * stored key that can be queried with an index.
 *
 * Pure and dependency-free so the normalization can be tested directly. It
 * decides who gets into an account, so it is worth testing exactly.
 */

/**
 * Afghan mobile subscriber numbers are 9 digits (7XXXXXXXX) after the +93
 * country code. Keying on the last 9 digits makes every stored variant of one
 * number collapse to the same value.
 */
const SUBSCRIBER_DIGITS = 9;

/** The shortest tail we will treat as identifying, mirroring the old matcher. */
const MIN_IDENTIFYING_DIGITS = 7;

/**
 * The indexed lookup key for a phone number, or "" when the input cannot
 * identify anyone.
 *
 * Returning "" rather than a short key matters: a 4-digit key would match many
 * accounts, and login would resolve to whichever one the index returned first.
 */
function phoneKey(raw) {
  const digits = String(raw == null ? "" : raw).replace(/\D/g, "");
  if (digits.length < MIN_IDENTIFYING_DIGITS) return "";
  return digits.slice(-SUBSCRIBER_DIGITS);
}

/**
 * Whether two numbers in any stored shape refer to the same subscriber.
 *
 * Kept so the backfill and any legacy path can agree with the query without
 * duplicating the rule.
 */
function samePhone(a, b) {
  const ka = phoneKey(a);
  const kb = phoneKey(b);
  return ka !== "" && ka === kb;
}

module.exports = {
  SUBSCRIBER_DIGITS,
  MIN_IDENTIFYING_DIGITS,
  phoneKey,
  samePhone,
};
