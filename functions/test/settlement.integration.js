/**
 * Integration tests for payment settlement, against a real Firestore.
 *
 * This is the 208 lines that decide whether money moves: whether a provider is
 * credited, whether a customer's booking reaches the salon, whether a webhook
 * replayed twice pays twice. It had no direct test of any kind — the extraction
 * in P6 was done so it could have one.
 *
 *   npm run test:settlement
 *
 * Runs the production function, not a reimplementation of it, so what passes
 * here is what the webhook and the manual settle-a-stuck-payment path both run.
 */

const test = require("node:test");
const assert = require("node:assert");

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("No Firestore emulator. Run these with: npm run test:settlement");
}
process.env.SAFEBEAUTY_TEST_HOOKS = "1";
process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || "safebeauty-settlement-test";

const { __testHooks } = require("../index.js");
const { settlePaymentInTransaction, db } = __testHooks;

let seq = 0;
const uniq = (p) => `${p}_${Date.now()}_${seq++}`;

/** A booking that has been paid for online and is waiting on the webhook. */
async function seedAwaitingPayment({ amount = 1000, providerNet = 900, providerId = null } = {}) {
  const provider = providerId || uniq("provider");
  const apptRef = db.collection("appointments").doc();
  const payRef = db.collection("payments").doc();

  await apptRef.set({
    customerId: uniq("customer"),
    salonId: uniq("salon"),
    serviceName: "ناخن",
    appointmentDate: Date.now() + 86_400_000,
    status: "AWAITING_PAYMENT",
    createdAt: Date.now(),
  });
  await payRef.set({
    appointmentId: apptRef.id,
    customerId: uniq("customer"),
    providerId: provider,
    amount,
    providerNet,
    commissionAmount: amount - providerNet,
    currency: "AFN",
    status: "PENDING",
    method: "ONLINE",
    createdAt: Date.now(),
  });

  return { apptRef, payRef, provider };
}

/** Run settlement exactly as the webhook does. */
function settle(payRef, { paid = true, failed = false, transactionId, signature = "sig" } = {}) {
  const ctx = {
    paymentRef: payRef,
    paidSignal: paid,
    failSignal: failed,
    transactionId: transactionId || uniq("txn"),
    signature,
    payload: {},
    paymentId: payRef.id,
    apptEvent: null,
  };
  return db.runTransaction((tx) => settlePaymentInTransaction(tx, ctx)).then((outcome) => ({ outcome, ctx }));
}

const balanceOf = async (providerId) => {
  const s = await db.doc(`provider_balances/${providerId}`).get();
  return s.exists ? Number((s.data() || {}).owedAmount || 0) : 0;
};

test("a paid signal settles the payment and releases the booking", async () => {
  const { apptRef, payRef, provider } = await seedAwaitingPayment();

  const { outcome, ctx } = await settle(payRef);
  assert.strictEqual(outcome, "paid");

  assert.strictEqual((await payRef.get()).data().status, "PAID");
  assert.strictEqual((await apptRef.get()).data().status, "PENDING",
    "the salon must now see the booking");
  assert.strictEqual(await balanceOf(provider), 900,
    "the provider is owed their net");
  assert.deepStrictEqual(
    { id: ctx.apptEvent.id, to: ctx.apptEvent.to },
    { id: apptRef.id, to: "PENDING" },
    "the transition is handed back so it can be recorded"
  );
});

test("the same transaction replayed does not pay twice", async () => {
  // The property that matters most here. Payment processors retry, and a retry
  // that credits a salon a second time is money the platform does not have.
  const { payRef, provider } = await seedAwaitingPayment();
  const txn = uniq("txn");

  const first = await settle(payRef, { transactionId: txn });
  assert.strictEqual(first.outcome, "paid");
  assert.strictEqual(await balanceOf(provider), 900);

  const second = await settle(payRef, { transactionId: txn });
  assert.ok(["replay", "already_paid"].includes(second.outcome), `got ${second.outcome}`);
  assert.strictEqual(await balanceOf(provider), 900, "credited exactly once");
});

test("a different transaction against an already-paid payment is refused", async () => {
  const { payRef, provider } = await seedAwaitingPayment();
  await settle(payRef, { transactionId: uniq("txn") });

  const again = await settle(payRef, { transactionId: uniq("other") });
  assert.strictEqual(again.outcome, "already_paid");
  assert.strictEqual(await balanceOf(provider), 900);
});

test("an expired payment is not revived by a late webhook", async () => {
  // The abandoned-checkout sweep may already have released the slot and handed
  // back the customer's credit. Reviving it would re-open a booking whose time
  // has been resold and credit the provider for it.
  const { apptRef, payRef, provider } = await seedAwaitingPayment();
  await payRef.update({ status: "EXPIRED" });

  const { outcome } = await settle(payRef);
  assert.strictEqual(outcome, "stale");
  assert.strictEqual((await payRef.get()).data().status, "EXPIRED");
  assert.strictEqual((await apptRef.get()).data().status, "AWAITING_PAYMENT");
  assert.strictEqual(await balanceOf(provider), 0, "no credit for a stale settlement");
});

test("a failure signal cancels the booking and credits nobody", async () => {
  const { payRef, provider } = await seedAwaitingPayment();

  const { outcome, ctx } = await settle(payRef, { paid: false, failed: true });
  assert.strictEqual(outcome, "failed");
  assert.strictEqual((await payRef.get()).data().status, "FAILED");
  assert.strictEqual(ctx.apptEvent.to, "CANCELLED");
  assert.strictEqual(await balanceOf(provider), 0);
});

test("an unknown signal leaves everything untouched", async () => {
  const { apptRef, payRef, provider } = await seedAwaitingPayment();

  const { outcome } = await settle(payRef, { paid: false, failed: false });
  assert.strictEqual(outcome, "ignored");
  assert.strictEqual((await payRef.get()).data().status, "PENDING");
  assert.strictEqual((await apptRef.get()).data().status, "AWAITING_PAYMENT");
  assert.strictEqual(await balanceOf(provider), 0);
});

test("a missing payment is reported rather than throwing", async () => {
  const ghost = db.collection("payments").doc();
  const { outcome } = await settle(ghost);
  assert.strictEqual(outcome, "not_found");
});

test("two providers' balances do not bleed into each other", async () => {
  const a = await seedAwaitingPayment({ providerNet: 500 });
  const b = await seedAwaitingPayment({ providerNet: 700 });

  await settle(a.payRef);
  await settle(b.payRef);

  assert.strictEqual(await balanceOf(a.provider), 500);
  assert.strictEqual(await balanceOf(b.provider), 700);
});
