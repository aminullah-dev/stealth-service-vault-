const test = require("node:test");
const assert = require("node:assert");
const {
  CODE_ALPHABET,
  CODE_LENGTH,
  bookingCodeFromBytes,
  normalizeBookingCode,
} = require("../lib/booking");

test("the alphabet excludes every character pair people confuse", () => {
  // 0/O, 1/I/L, 2/Z, 5/S, 8/B — a code is read aloud and written by hand, so
  // any of these turns a correct reading into a failed lookup.
  for (const ch of "OIL01ZS258B") {
    assert.ok(!CODE_ALPHABET.includes(ch), `${ch} must not be in the alphabet`);
  }
});

test("the alphabet contains no vowels, so a code cannot spell a word", () => {
  for (const ch of "AEIOU") {
    assert.ok(!CODE_ALPHABET.includes(ch), `${ch} must not be in the alphabet`);
  }
});

test("the alphabet has no duplicates", () => {
  assert.strictEqual(new Set(CODE_ALPHABET).size, CODE_ALPHABET.length);
});

test("a code is the prefix plus six characters from the alphabet", () => {
  const code = bookingCodeFromBytes(Buffer.from([0, 1, 2, 3, 4, 5]));
  assert.match(code, /^SB-[A-Z0-9]{6}$/);
  for (const ch of code.slice(3)) {
    assert.ok(CODE_ALPHABET.includes(ch));
  }
});

test("the same bytes always give the same code", () => {
  const bytes = Buffer.from([200, 17, 99, 3, 250, 41]);
  assert.strictEqual(bookingCodeFromBytes(bytes), bookingCodeFromBytes(bytes));
});

test("byte values above the alphabet length still land inside it", () => {
  // 255 % 22 must not index past the end — an undefined here would silently
  // produce the string "undefined" inside a customer's booking reference.
  const code = bookingCodeFromBytes(Buffer.from([255, 255, 255, 255, 255, 255]));
  assert.ok(!code.includes("undefined"));
  assert.strictEqual(code.length, 3 + CODE_LENGTH);
});

test("too few bytes is refused rather than producing a short code", () => {
  assert.throws(() => bookingCodeFromBytes(Buffer.from([1, 2, 3])));
});

test("normalizing accepts every way a human hands over a code", () => {
  const canonical = "SB-4C7GHJ";
  for (const typed of [
    "SB-4C7GHJ",
    "sb-4c7ghj",
    "4C7GHJ",
    "4c7ghj",
    " SB - 4C7 GHJ ",
    "SB_4C7GHJ",
    "SB4C7GHJ",
  ]) {
    assert.strictEqual(normalizeBookingCode(typed), canonical, `failed for ${typed}`);
  }
});

test("normalizing rejects anything that could not be a code", () => {
  for (const bad of [
    "",
    null,
    undefined,
    "SB-",
    "SB-12345",          // too short
    "SB-4C7GHJK",        // too long
    "SB-4C7GHO",         // O is not in the alphabet
    "SB-4C7GH1",         // 1 is not in the alphabet
    "aBc20DefGhi45jkl",  // a document id, not a code
  ]) {
    assert.strictEqual(normalizeBookingCode(bad), "", `should reject ${bad}`);
  }
});

test("a normalized code round-trips through normalizing again", () => {
  const code = bookingCodeFromBytes(Buffer.from([9, 18, 3, 21, 7, 12]));
  assert.strictEqual(normalizeBookingCode(code), code);
});
