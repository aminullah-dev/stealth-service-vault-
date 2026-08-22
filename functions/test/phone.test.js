const test = require("node:test");
const assert = require("node:assert");
const { phoneKey, samePhone } = require("../lib/phone");

test("every stored shape of one number collapses to the same key", () => {
  // These are the four shapes the old full-scan matcher existed to reconcile.
  const shapes = ["+93700123456", "0093700123456", "93700123456", "0700123456", "700123456"];
  const keys = shapes.map(phoneKey);
  assert.strictEqual(new Set(keys).size, 1, `expected one key, got ${JSON.stringify(keys)}`);
  assert.strictEqual(keys[0], "700123456");
});

test("punctuation and spacing do not change the key", () => {
  for (const typed of ["+93 700 123 456", "0700-123-456", "(0700) 123 456", " 700123456 "]) {
    assert.strictEqual(phoneKey(typed), "700123456", `failed for ${typed}`);
  }
});

test("two different subscribers never share a key", () => {
  assert.notStrictEqual(phoneKey("+93700123456"), phoneKey("+93700123457"));
  assert.strictEqual(samePhone("+93700123456", "0700123457"), false);
});

test("a number too short to identify anyone yields no key", () => {
  // A 4-digit key would match many accounts, and login would resolve to
  // whichever the index happened to return first.
  for (const bad of ["", null, undefined, "123", "0700", "12345", "abcdef"]) {
    assert.strictEqual(phoneKey(bad), "", `should reject ${bad}`);
  }
});

test("a seven-digit tail is the shortest accepted, matching the old rule", () => {
  assert.strictEqual(phoneKey("0123456"), "0123456");
  assert.strictEqual(phoneKey("012345"), "");
});

test("samePhone is false when either side cannot identify anyone", () => {
  assert.strictEqual(samePhone("", "+93700123456"), false);
  assert.strictEqual(samePhone("+93700123456", ""), false);
  assert.strictEqual(samePhone("", ""), false);
});

test("samePhone agrees with the key across mixed shapes", () => {
  assert.ok(samePhone("+93700123456", "0700123456"));
  assert.ok(samePhone("93700123456", "700123456"));
});

test("a longer international number still keys on its subscriber tail", () => {
  // Defensive: a non-Afghan number should not throw or produce a key longer
  // than the subscriber length.
  assert.strictEqual(phoneKey("+1 415 555 0123").length, 9);
});
