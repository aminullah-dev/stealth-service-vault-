// Unit tests for the HesabPay webhook decision helpers (lib/webhook.js). Pure,
// no Firebase / no network.
const test = require("node:test");
const assert = require("node:assert/strict");
const { isPaidSignal, isFailSignal, isUnderpaid } = require("../lib/webhook");

// ── isPaidSignal ─────────────────────────────────────────────────────────────
test("isPaidSignal: success:true is paid", () => {
  assert.equal(isPaidSignal({ success: true }), true);
});

test("isPaidSignal: status_code 10 is paid", () => {
  assert.equal(isPaidSignal({ status_code: 10 }), true);
});

test("isPaidSignal: legacy status strings (any case) are paid", () => {
  assert.equal(isPaidSignal({ status: "paid" }), true);
  assert.equal(isPaidSignal({ status: "SUCCESS" }), true);
  assert.equal(isPaidSignal({ status: "Completed" }), true);
});

test("isPaidSignal: unknown / intermediate callback is NOT paid", () => {
  assert.equal(isPaidSignal({ status: "PENDING" }), false);
  assert.equal(isPaidSignal({}), false);
  assert.equal(isPaidSignal(null), false);
  assert.equal(isPaidSignal({ status_code: 5 }), false);
});

// ── isFailSignal ─────────────────────────────────────────────────────────────
test("isFailSignal: success:false is a failure", () => {
  assert.equal(isFailSignal({ success: false }), true);
});

test("isFailSignal: failed/cancelled/declined strings are failures", () => {
  assert.equal(isFailSignal({ status: "failed" }), true);
  assert.equal(isFailSignal({ status: "CANCELLED" }), true);
  assert.equal(isFailSignal({ status: "Declined" }), true);
});

test("isFailSignal: unknown callback is NOT a failure (no-op)", () => {
  assert.equal(isFailSignal({ status: "PENDING" }), false);
  assert.equal(isFailSignal({}), false);
  assert.equal(isFailSignal(null), false);
});

test("paid and fail are mutually exclusive for real callbacks", () => {
  const paid = { success: true, status_code: 10 };
  const failed = { success: false, status: "FAILED" };
  assert.equal(isPaidSignal(paid) && isFailSignal(paid), false);
  assert.equal(isPaidSignal(failed) && isFailSignal(failed), false);
});

// ── isUnderpaid ──────────────────────────────────────────────────────────────
test("isUnderpaid: paying less than the recorded price is underpayment", () => {
  assert.equal(isUnderpaid(1, 5000), true);
});

test("isUnderpaid: paying the exact or a higher amount is not underpayment", () => {
  assert.equal(isUnderpaid(5000, 5000), false);
  assert.equal(isUnderpaid(6000, 5000), false);
});

test("isUnderpaid: a non-finite / missing reported amount is not rejected", () => {
  assert.equal(isUnderpaid(undefined, 5000), false);
  assert.equal(isUnderpaid(NaN, 5000), false);
  assert.equal(isUnderpaid("abc", 5000), false);
});

test("isUnderpaid: numeric strings are compared numerically", () => {
  assert.equal(isUnderpaid("100", 5000), true);
  assert.equal(isUnderpaid("5000", "5000"), false);
});
