"use strict";

const test = require("node:test");
const assert = require("node:assert");
const { commissionToReturn, shouldReverseCommission } = require("../lib/commission");

const cash = (over = {}) => ({
  method: "CASH",
  providerId: "prov-1",
  commissionAmount: 100,
  ...over,
});

test("a cash no-show returns the commission the salon never collected", () => {
  assert.strictEqual(shouldReverseCommission(true, cash()), true);
  assert.strictEqual(commissionToReturn(cash()), 100);
});

test("a rating that is not a no-show changes no money", () => {
  assert.strictEqual(shouldReverseCommission(false, cash()), false);
  assert.strictEqual(shouldReverseCommission(undefined, cash()), false);
  // A truthy non-true value must not pass — the callable reads d.noShow === true.
  assert.strictEqual(shouldReverseCommission("yes", cash()), false);
});

test("online money is left alone — that payment actually happened", () => {
  assert.strictEqual(shouldReverseCommission(true, cash({ method: "ONLINE" })), false);
  assert.strictEqual(shouldReverseCommission(true, cash({ method: "" })), false);
});

test("the commission is credited once, not twice", () => {
  // The one overlap: report a no-show, then cancel the same booking. Both
  // paths stamp commissionReversed, and both refuse to act on the stamp.
  assert.strictEqual(shouldReverseCommission(true, cash({ commissionReversed: true })), false);
});

test("a booking with no payment, or no provider, credits nobody", () => {
  assert.strictEqual(shouldReverseCommission(true, null), false);
  assert.strictEqual(shouldReverseCommission(true, undefined), false);
  assert.strictEqual(shouldReverseCommission(true, cash({ providerId: "" })), false);
});

test("a zero or malformed commission cannot move the balance", () => {
  for (const bad of [0, -50, NaN, "abc", null, undefined]) {
    assert.strictEqual(shouldReverseCommission(true, cash({ commissionAmount: bad })), false,
      `commissionAmount ${String(bad)} should not reverse`);
    assert.strictEqual(commissionToReturn(cash({ commissionAmount: bad })), 0);
  }
});

test("the credit exactly matches the debit taken at booking time", () => {
  // createPaymentSession does increment(-commissionAmount); this must be its
  // mirror image, or the ledger drifts a little on every no-show.
  for (const amount of [1, 37, 250, 1999]) {
    assert.strictEqual(commissionToReturn(cash({ commissionAmount: amount })), amount);
  }
});
