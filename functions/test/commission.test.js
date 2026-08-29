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

// ── the provider ledger ──────────────────────────────────────────────────────
//
// Wallet credit is not a discount. A gift card was bought with real money the
// platform is holding; a referral reward, a KYC bonus and redeemed loyalty
// points are promotions the platform chose to run. The salon agreed to a price
// and served the appointment either way — so the part of the price the customer
// paid from her wallet is owed to her by the platform, not forgiven by the
// salon. Counting only what changed hands meant a customer with enough credit
// was served for nothing.
const { cashLedgerDelta, onlineLedgerDelta } = require("../lib/commission");

test("cash: the salon owes only the commission on money it actually collected", () => {
  assert.strictEqual(cashLedgerDelta({ commissionAmount: 100, referralUsed: 0 }), -100);
});

test("cash: wallet credit is owed TO the salon, not taken from it", () => {
  // 1000 AFN booking, 10% commission, the whole thing paid from a gift card:
  // she collects nothing at the door, so the platform owes her the lot.
  assert.strictEqual(cashLedgerDelta({ commissionAmount: 0, referralUsed: 1000 }), 1000);
  // Half from the wallet: 500 collected, 100 commission on the full price.
  assert.strictEqual(cashLedgerDelta({ commissionAmount: 100, referralUsed: 500 }), 400);
});

test("online: settlement credits the earnings plus the wallet portion", () => {
  assert.strictEqual(onlineLedgerDelta({ providerNet: 900, referralUsed: 0 }), 900);
  assert.strictEqual(onlineLedgerDelta({ providerNet: 0, referralUsed: 1000 }), 1000);
  assert.strictEqual(onlineLedgerDelta({ providerNet: 450, referralUsed: 500 }), 950);
});

test("no salon is ever left with nothing for work it did", () => {
  // The case the discount cap does not cover, because wallet credit is not a
  // discount: full credit, nothing collected, and she is still paid.
  for (const price of [50, 380, 1000, 12345]) {
    assert.ok(cashLedgerDelta({ commissionAmount: 0, referralUsed: price }) > 0);
    assert.ok(onlineLedgerDelta({ providerNet: 0, referralUsed: price }) > 0);
  }
});

test("every reversal is the exact negation, so the ledger returns to flat", () => {
  // cancelAppointment, and reportCustomer's no-show branch, both increment by
  // the negation. Booking then cancelling must leave the balance untouched.
  for (const p of [
    { commissionAmount: 100, referralUsed: 0 },
    { commissionAmount: 0,   referralUsed: 1000 },
    { commissionAmount: 100, referralUsed: 500 },
    { commissionAmount: 37,  referralUsed: 13 },
  ]) {
    assert.strictEqual(cashLedgerDelta(p) + (-cashLedgerDelta(p)), 0);
  }
  for (const p of [
    { providerNet: 900, referralUsed: 0 },
    { providerNet: 0,   referralUsed: 1000 },
    { providerNet: 450, referralUsed: 500 },
  ]) {
    assert.strictEqual(onlineLedgerDelta(p) + (-onlineLedgerDelta(p)), 0);
  }
});

test("a malformed payment moves the ledger by nothing rather than by NaN", () => {
  // A NaN reaching FieldValue.increment poisons the balance permanently.
  for (const bad of [undefined, null, "", "abc", NaN, {}]) {
    assert.strictEqual(Number.isFinite(cashLedgerDelta(bad)), true, String(bad));
    assert.strictEqual(Number.isFinite(onlineLedgerDelta(bad)), true, String(bad));
  }
  assert.strictEqual(cashLedgerDelta({ commissionAmount: "x", referralUsed: null }), 0);
  assert.strictEqual(onlineLedgerDelta({ providerNet: undefined, referralUsed: "y" }), 0);
})
