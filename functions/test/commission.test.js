"use strict";

const test = require("node:test");
const assert = require("node:assert");
const { commissionToReturn, shouldReverseCommission , isCommissionFree } = require("../lib/commission");

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

// ledgerVersion: 2 on these is not decoration. An unstamped payment is one
// written before this release and is deliberately reversed by the old
// commission-only formula, so a test of the NEW arithmetic has to say which
// arithmetic it means. See writtenWithWalletCredit.
const paid = (over = {}) => ({ ledgerVersion: 2, ...over });

test("cash: the salon owes only the commission on money it actually collected", () => {
  assert.strictEqual(cashLedgerDelta(paid({ commissionAmount: 100, referralUsed: 0 })), -100);
});

test("cash: wallet credit is owed TO the salon, not taken from it", () => {
  // 1000 AFN booking, 10% commission, the whole thing paid from a gift card:
  // she collects nothing at the door, so the platform owes her the lot.
  assert.strictEqual(cashLedgerDelta(paid({ commissionAmount: 0, referralUsed: 1000 })), 1000);
  // Half from the wallet: 500 collected, 100 commission on the full price.
  assert.strictEqual(cashLedgerDelta(paid({ commissionAmount: 100, referralUsed: 500 })), 400);
});

test("online: settlement credits the earnings plus the wallet portion", () => {
  assert.strictEqual(onlineLedgerDelta(paid({ providerNet: 900, referralUsed: 0 })), 900);
  assert.strictEqual(onlineLedgerDelta(paid({ providerNet: 0, referralUsed: 1000 })), 1000);
  assert.strictEqual(onlineLedgerDelta(paid({ providerNet: 450, referralUsed: 500 })), 950);
});

test("no salon is ever left with nothing for work it did", () => {
  // The case the discount cap does not cover, because wallet credit is not a
  // discount: full credit, nothing collected, and she is still paid.
  for (const price of [50, 380, 1000, 12345]) {
    assert.ok(cashLedgerDelta(paid({ commissionAmount: 0, referralUsed: price })) > 0);
    assert.ok(onlineLedgerDelta(paid({ providerNet: 0, referralUsed: price })) > 0);
  }
});

test("book then cancel leaves the balance exactly where it started", () => {
  // The previous version of this test asserted x + (-x) === 0, which is true of
  // every number and proves nothing about the ledger. What matters is that the
  // SITES agree: the increment the booking applies and the increment the
  // reversal applies are computed from the same payment document by the same
  // function, so this simulates the real sequence instead of the identity.
  const apply = (balance, delta) => balance + delta;
  for (const p of [
    { ledgerVersion: 2, commissionAmount: 100, referralUsed: 0 },
    { ledgerVersion: 2, commissionAmount: 0,   referralUsed: 1000 },
    { ledgerVersion: 2, commissionAmount: 100, referralUsed: 500 },
    { commissionAmount: 100, referralUsed: 1000 },   // written before this release
  ]) {
    let bal = 4200;                                   // whatever she was owed before
    bal = apply(bal, cashLedgerDelta(p));             // createPaymentSession, cash
    assert.notStrictEqual(bal, 4200, "the booking should have moved the balance");
    bal = apply(bal, -cashLedgerDelta(p));            // cancelAppointment / no-show
    assert.strictEqual(bal, 4200, "cancelling did not return the balance");
  }
  for (const p of [
    { ledgerVersion: 2, providerNet: 900, referralUsed: 0 },
    { ledgerVersion: 2, providerNet: 0,   referralUsed: 1000 },
    { providerNet: 900, referralUsed: 1000 },         // written before this release
  ]) {
    let bal = 4200;
    bal = apply(bal, onlineLedgerDelta(p));           // webhook settlement
    bal = apply(bal, -onlineLedgerDelta(p));          // cancel / refund
    assert.strictEqual(bal, 4200);
  }
});

test("a booking made before this release is reversed the way it was made", () => {
  // The hazard the version stamp exists for. A cash booking taken yesterday
  // debited only its commission. Cancelled tomorrow under the new formula it
  // would be reversed by commission-minus-wallet-credit, leaving the salon
  // short by the whole credit — silently, on a balance nobody recomputes.
  const unstamped = { method: "CASH", providerId: "p", commissionAmount: 100, referralUsed: 1000 };
  assert.strictEqual(cashLedgerDelta(unstamped), -100,
    "an unstamped payment must use the old commission-only formula");
  assert.strictEqual(onlineLedgerDelta({ providerNet: 900, referralUsed: 1000 }), 900);

  // And the same payment, written by this release, uses the new one.
  assert.strictEqual(cashLedgerDelta({ ...unstamped, ledgerVersion: 2 }), 900);
  assert.strictEqual(onlineLedgerDelta({ providerNet: 900, referralUsed: 1000, ledgerVersion: 2 }), 1900);
});

test("a no-show reverses a wallet-funded booking too, not just a commission", () => {
  // Gated on the commission alone, a booking paid entirely from a customer's
  // wallet had no commission to return and reversed nothing — so the salon kept
  // a positive balance for an appointment nobody attended.
  const walletFunded = {
    method: "CASH", providerId: "p", ledgerVersion: 2,
    commissionAmount: 0, referralUsed: 1000,
  };
  assert.strictEqual(shouldReverseCommission(true, walletFunded), true);
  assert.strictEqual(cashLedgerDelta(walletFunded), 1000);

  // A booking that moved nothing still reverses nothing, and the idempotency
  // stamp still stops a second reversal.
  assert.strictEqual(shouldReverseCommission(true, { ...walletFunded, referralUsed: 0 }), false);
  assert.strictEqual(shouldReverseCommission(true, { ...walletFunded, commissionReversed: true }), false);
});

test("a malformed payment moves the ledger by nothing rather than by NaN", () => {
  // A NaN reaching FieldValue.increment poisons the balance permanently.
  for (const bad of [undefined, null, "", "abc", NaN, {}]) {
    assert.strictEqual(Number.isFinite(cashLedgerDelta(bad)), true, String(bad));
    assert.strictEqual(Number.isFinite(onlineLedgerDelta(bad)), true, String(bad));
  }
  assert.strictEqual(cashLedgerDelta(paid({ commissionAmount: "x", referralUsed: null })), 0);
  assert.strictEqual(onlineLedgerDelta(paid({ providerNet: undefined, referralUsed: "y" })), 0);
})

// ── the founding-salon offer ─────────────────────────────────────────────────
//
// sb-owner-founding.json promises "your first 50 bookings, no commission" to
// the salons recruited at launch. It had nothing behind it: commission was one
// global percentage applied from the first booking onward.

test("a founding salon pays no commission until it has used the offer", () => {
  assert.strictEqual(isCommissionFree({ foundingSalon: true, confirmedCount: 0 }), true);
  assert.strictEqual(isCommissionFree({ foundingSalon: true, confirmedCount: 49 }), true);
  assert.strictEqual(isCommissionFree({ foundingSalon: true, confirmedCount: 50 }), false,
    "the 51st booking is the first that pays");
  assert.strictEqual(isCommissionFree({ foundingSalon: true, confirmedCount: 900 }), false);
});

test("an ordinary salon is never commission-free", () => {
  // The flag is the whole gate. Without it the offer would quietly apply to
  // every salon that ever joins, which is a different business than the one
  // being advertised.
  assert.strictEqual(isCommissionFree({ confirmedCount: 0 }), false);
  assert.strictEqual(isCommissionFree({ foundingSalon: false, confirmedCount: 0 }), false);
  assert.strictEqual(isCommissionFree({ foundingSalon: "true", confirmedCount: 0 }), false,
    "a string is not the flag");
});

test("a missing or nonsense count does not hand out a free booking", () => {
  for (const bad of [undefined, null, "", "12", NaN, -1, {}]) {
    assert.strictEqual(isCommissionFree({ foundingSalon: true, confirmedCount: bad }), false,
      String(bad));
  }
  assert.strictEqual(isCommissionFree(null), false);
  assert.strictEqual(isCommissionFree(undefined), false);
});
