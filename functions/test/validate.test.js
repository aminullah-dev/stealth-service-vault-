"use strict";

const test = require("node:test");
const assert = require("node:assert");
const { isValidDocId } = require("../lib/validate");

test("isValidDocId accepts real ids", () => {
  assert.ok(isValidDocId("aB3xYz01234567890abc"));                 // Firestore auto-id
  assert.ok(isValidDocId("550e8400-e29b-41d4-a716-446655440000")); // app UUID uid
  assert.ok(isValidDocId("WELCOME10"));                            // promo code
  assert.ok(isValidDocId("support_550e8400"));                     // conversation id style
  assert.ok(isValidDocId("a_b-C9"));
});

test("isValidDocId rejects path-injection and malformed input", () => {
  assert.ok(!isValidDocId("a/b"));          // slash → odd-segment path
  assert.ok(!isValidDocId("../secret"));    // traversal
  assert.ok(!isValidDocId("a.b"));          // dots not path-safe here
  assert.ok(!isValidDocId("a b"));          // whitespace
  assert.ok(!isValidDocId(""));             // empty
  assert.ok(!isValidDocId("a".repeat(129))); // over length cap
  assert.ok(!isValidDocId(null));
  assert.ok(!isValidDocId(undefined));
  assert.ok(!isValidDocId(123));
  assert.ok(!isValidDocId({}));
});
