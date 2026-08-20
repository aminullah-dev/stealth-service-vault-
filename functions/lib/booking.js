/**
 * Booking references — the short code a customer can read down a phone line.
 *
 * A Firestore document id is twenty random characters. Nobody can say one out
 * loud, and support cannot ask for one. This produces something a person can
 * read from a screen, repeat over a bad line, and write on paper.
 *
 * Pure and dependency-free (the caller supplies the randomness) so the alphabet
 * and the normalizer can be tested: getting either wrong means two customers
 * can be handed the same reference, or a correctly-read code fails to resolve.
 */

/**
 * The character set a code is drawn from.
 *
 * Excludes every pair that is ambiguous when spoken or handwritten — 0/O, 1/I/L,
 * 2/Z, 5/S, 8/B — and every vowel, so a code cannot accidentally spell a word in
 * English, Dari or Pashto. 22 characters over 6 places is about 113 million
 * codes, which at any plausible size of this business keeps collisions rare
 * enough that the reservation check almost never has to retry.
 */
const CODE_ALPHABET = "34679CDFGHJKMNPQRTVWXY";

const CODE_LENGTH = 6;
const CODE_PREFIX = "SB";

/**
 * Build a code from caller-supplied random bytes.
 *
 * Takes bytes rather than generating them so the mapping from randomness to
 * characters is testable, and so the caller decides the source of entropy.
 */
function bookingCodeFromBytes(bytes) {
  if (!bytes || bytes.length < CODE_LENGTH) {
    throw new Error(`bookingCodeFromBytes needs at least ${CODE_LENGTH} bytes`);
  }
  let out = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    out += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length];
  }
  return `${CODE_PREFIX}-${out}`;
}

/**
 * Turn what a human typed into the canonical stored form.
 *
 * Codes arrive read aloud over the phone, copied off a screenshot, or typed
 * with the prefix left off. Lower case, spaces, dashes anywhere, and a missing
 * or duplicated "SB" all have to resolve to the same thing, or a customer who
 * read her code correctly is told it does not exist.
 *
 * Returns "" for anything that cannot be a code, so a caller can reject rather
 * than run a query for a value that could never match.
 */
function normalizeBookingCode(raw) {
  const cleaned = String(raw || "").toUpperCase().replace(/[\s\-_]/g, "");
  if (!cleaned) return "";

  const body = cleaned.startsWith(CODE_PREFIX) ? cleaned.slice(CODE_PREFIX.length) : cleaned;
  if (body.length !== CODE_LENGTH) return "";

  // A character outside the alphabet means it was misread rather than mistyped
  // — most often O for 0 or I for 1, which this alphabet deliberately avoids.
  for (const ch of body) {
    if (!CODE_ALPHABET.includes(ch)) return "";
  }
  return `${CODE_PREFIX}-${body}`;
}

module.exports = {
  CODE_ALPHABET,
  CODE_LENGTH,
  CODE_PREFIX,
  bookingCodeFromBytes,
  normalizeBookingCode,
};
