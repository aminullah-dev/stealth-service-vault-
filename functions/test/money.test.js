// Unit tests for the checkout money math (lib/money.js). Pure arithmetic, no
// Firebase — run with `npm test` (uses Node's built-in test runner, no deps).
const test = require("node:test");
const assert = require("node:assert/strict");
const { promoDiscountFor, computeCheckout, resolveServicesTotal, validateGiftAmount, loyaltyToCredit, offerDiscountFor } = require("../lib/money");

test("loyaltyToCredit: exact multiple redeems fully at 1:1", () => {
  assert.deepEqual(loyaltyToCredit(100), { ok: true, spend: 100, credit: 100, reason: "" });
  assert.deepEqual(loyaltyToCredit(300), { ok: true, spend: 300, credit: 300, reason: "" });
});

test("loyaltyToCredit: rounds the request down to whole 100s", () => {
  assert.deepEqual(loyaltyToCredit(250), { ok: true, spend: 200, credit: 200, reason: "" });
});

test("loyaltyToCredit: below the minimum is rejected", () => {
  assert.deepEqual(loyaltyToCredit(50), { ok: false, spend: 0, credit: 0, reason: "too_few" });
});

test("loyaltyToCredit: zero / negative / garbage rejected", () => {
  assert.equal(loyaltyToCredit(0).ok, false);
  assert.equal(loyaltyToCredit(-100).ok, false);
  assert.equal(loyaltyToCredit("nope").ok, false);
});

test("loyaltyToCredit: custom ratio applies to the credit only", () => {
  // Half-AFN per point: 200 points -> spend 200, credit 100.
  assert.deepEqual(loyaltyToCredit(200, { ratio: 0.5 }), { ok: true, spend: 200, credit: 100, reason: "" });
});

test("validateGiftAmount: accepts a normal amount", () => {
  assert.deepEqual(validateGiftAmount(500), { ok: true, value: 500, reason: "" });
});

test("validateGiftAmount: floors fractional input", () => {
  assert.equal(validateGiftAmount(500.9).value, 500);
});

test("validateGiftAmount: rejects zero / negative / garbage", () => {
  assert.equal(validateGiftAmount(0).ok, false);
  assert.equal(validateGiftAmount(-100).ok, false);
  assert.equal(validateGiftAmount("nope").ok, false);
});

test("validateGiftAmount: enforces min and max", () => {
  assert.deepEqual(validateGiftAmount(10), { ok: false, value: 10, reason: "too_small" });
  assert.deepEqual(validateGiftAmount(999999), { ok: false, value: 999999, reason: "too_large" });
  assert.equal(validateGiftAmount(50).ok, true);   // boundary
  assert.equal(validateGiftAmount(50000).ok, true); // boundary
});

test("validateGiftAmount: custom bounds override defaults", () => {
  assert.equal(validateGiftAmount(20, { min: 10, max: 100 }).ok, true);
  assert.equal(validateGiftAmount(200, { min: 10, max: 100 }).reason, "too_large");
});

const PRICES = { Haircut: 300, Makeup: 800, Manicure: 250 };

test("resolveServicesTotal: single service", () => {
  const r = resolveServicesTotal(PRICES, ["Haircut"]);
  assert.deepEqual(r.services, [{ name: "Haircut", price: 300 }]);
  assert.equal(r.total, 300);
  assert.deepEqual(r.invalid, []);
});

test("resolveServicesTotal: accepts a bare string (legacy single service)", () => {
  const r = resolveServicesTotal(PRICES, "Makeup");
  assert.equal(r.total, 800);
  assert.equal(r.services.length, 1);
});

test("resolveServicesTotal: multi-service sums the prices", () => {
  const r = resolveServicesTotal(PRICES, ["Haircut", "Makeup", "Manicure"]);
  assert.equal(r.total, 1350);
  assert.equal(r.services.length, 3);
});

test("resolveServicesTotal: duplicates allowed (e.g. two guests, same service)", () => {
  const r = resolveServicesTotal(PRICES, ["Makeup", "Makeup", "Haircut"]);
  assert.equal(r.total, 1900);
  assert.equal(r.services.length, 3);
});

test("resolveServicesTotal: unknown / unpriced services collected in invalid", () => {
  const r = resolveServicesTotal(PRICES, ["Haircut", "Facial", "Tattoo"]);
  assert.equal(r.total, 300);
  assert.deepEqual(r.invalid, ["Facial", "Tattoo"]);
  assert.equal(r.services.length, 1);
});

test("resolveServicesTotal: invalid names are de-duplicated", () => {
  const r = resolveServicesTotal(PRICES, ["Facial", "Facial"]);
  assert.deepEqual(r.invalid, ["Facial"]);
});

test("resolveServicesTotal: blank/whitespace names are dropped, not invalid", () => {
  const r = resolveServicesTotal(PRICES, ["Haircut", "  ", "", null]);
  assert.equal(r.total, 300);
  assert.deepEqual(r.invalid, []);
  assert.equal(r.services.length, 1);
});

test("resolveServicesTotal: trims names before lookup", () => {
  const r = resolveServicesTotal(PRICES, ["  Haircut  "]);
  assert.equal(r.total, 300);
});

test("resolveServicesTotal: missing price map -> everything invalid, zero total", () => {
  const r = resolveServicesTotal(undefined, ["Haircut"]);
  assert.equal(r.total, 0);
  assert.deepEqual(r.invalid, ["Haircut"]);
});

test("resolveServicesTotal: zero/negative priced service is invalid", () => {
  const r = resolveServicesTotal({ Freebie: 0, Bad: -50 }, ["Freebie", "Bad"]);
  assert.equal(r.total, 0);
  assert.deepEqual(r.invalid, ["Freebie", "Bad"]);
});

test("resolveServicesTotal + computeCheckout: end-to-end multi-service booking", () => {
  const { total } = resolveServicesTotal(PRICES, ["Haircut", "Makeup"]); // 1100
  const c = computeCheckout({ listPrice: total, promoDiscount: 100, referralCredit: 0, commissionPercent: 10 });
  assert.equal(c.price, 1000);
  assert.equal(c.commissionAmount, 100);
  assert.equal(c.providerNet, 900);
});

test("promoDiscountFor: percentage discount", () => {
  assert.equal(promoDiscountFor({ discountPercent: 20 }, 1000), 200);
  assert.equal(promoDiscountFor({ discountPercent: 100 }, 1000), 1000);
});

test("promoDiscountFor: fixed-amount discount", () => {
  assert.equal(promoDiscountFor({ discountAmount: 150 }, 1000), 150);
  assert.equal(promoDiscountFor({ discountAmount: 150.7 }, 1000), 151); // rounds
});

test("promoDiscountFor: percentage wins when both are set", () => {
  assert.equal(promoDiscountFor({ discountPercent: 20, discountAmount: 999 }, 1000), 200);
});

test("promoDiscountFor: discount never exceeds the price", () => {
  assert.equal(promoDiscountFor({ discountAmount: 5000 }, 1000), 1000);
  assert.equal(promoDiscountFor({ discountPercent: 150 }, 1000), 1000); // pct capped at 100
});

test("promoDiscountFor: rounds to whole AFN", () => {
  assert.equal(promoDiscountFor({ discountPercent: 50 }, 999), 500); // round(499.5)
});

test("promoDiscountFor: missing / zero / garbage fields -> 0", () => {
  assert.equal(promoDiscountFor({}, 1000), 0);
  assert.equal(promoDiscountFor({ discountPercent: 0, discountAmount: 0 }, 1000), 0);
  assert.equal(promoDiscountFor(null, 1000), 0);
  assert.equal(promoDiscountFor({ discountPercent: "abc" }, 1000), 0);
  assert.equal(promoDiscountFor({ discountPercent: 20 }, "oops"), 0); // price NaN -> 0
});

test("computeCheckout: plain booking, no discounts", () => {
  assert.deepEqual(
    computeCheckout({ listPrice: 1000, commissionPercent: 10 }),
    { afterPromo: 1000, referralUsed: 0, price: 1000, commissionAmount: 100, providerNet: 900 }
  );
});

test("computeCheckout: promo only", () => {
  assert.deepEqual(
    computeCheckout({ listPrice: 1000, promoDiscount: 200, commissionPercent: 10 }),
    { afterPromo: 800, referralUsed: 0, price: 800, commissionAmount: 80, providerNet: 720 }
  );
});

test("computeCheckout: referral credit only", () => {
  assert.deepEqual(
    computeCheckout({ listPrice: 1000, referralCredit: 300, commissionPercent: 10 }),
    { afterPromo: 1000, referralUsed: 300, price: 700, commissionAmount: 70, providerNet: 630 }
  );
});

test("computeCheckout: promo + referral stack, in that order", () => {
  assert.deepEqual(
    computeCheckout({ listPrice: 1000, promoDiscount: 200, referralCredit: 100, commissionPercent: 10 }),
    { afterPromo: 800, referralUsed: 100, price: 700, commissionAmount: 70, providerNet: 630 }
  );
});

test("computeCheckout: referral credit is capped at the post-promo amount", () => {
  // 1000 - 800 promo = 200 left; 5000 credit can only consume 200.
  assert.deepEqual(
    computeCheckout({ listPrice: 1000, promoDiscount: 800, referralCredit: 5000, commissionPercent: 10 }),
    { afterPromo: 200, referralUsed: 200, price: 0, commissionAmount: 0, providerNet: 0 }
  );
});

test("computeCheckout: commission rounds to whole AFN", () => {
  const r = computeCheckout({ listPrice: 995, commissionPercent: 10 }); // 99.5 -> 100
  assert.equal(r.commissionAmount, 100);
  assert.equal(r.providerNet, 895);
});

test("computeCheckout: 15% commission on a referral-discounted price", () => {
  assert.deepEqual(
    computeCheckout({ listPrice: 1000, referralCredit: 300, commissionPercent: 15 }),
    { afterPromo: 1000, referralUsed: 300, price: 700, commissionAmount: 105, providerNet: 595 }
  );
});

test("computeCheckout: never goes negative on over-discount", () => {
  const r = computeCheckout({ listPrice: 500, promoDiscount: 999, referralCredit: 999, commissionPercent: 10 });
  assert.equal(r.afterPromo, 0);
  assert.equal(r.referralUsed, 0);
  assert.equal(r.price, 0);
  assert.equal(r.providerNet, 0);
});

test("computeCheckout: garbage / missing inputs degrade to 0, not NaN", () => {
  const r = computeCheckout({ listPrice: "abc", commissionPercent: 10 });
  assert.equal(r.price, 0);
  assert.equal(r.commissionAmount, 0);
  const d = computeCheckout({ listPrice: 1000 }); // commissionPercent missing -> 0%
  assert.equal(d.commissionAmount, 0);
  assert.equal(d.providerNet, 1000);
});

// ── offerDiscountFor (price-affecting deals, phase 2) ────────────────────────
const S = [{ name: "haircut", price: 400 }, { name: "nails", price: 200 }];

test("offerDiscountFor: salon-wide percentage applies to the whole subtotal", () => {
  // 20% of (400 + 200) = 120
  assert.equal(offerDiscountFor({ discountPercent: 20 }, S), 120);
});

test("offerDiscountFor: salon-wide flat amount, capped at the subtotal", () => {
  assert.equal(offerDiscountFor({ discountAmount: 100 }, S), 100);
  assert.equal(offerDiscountFor({ discountAmount: 9999 }, S), 600);
});

test("offerDiscountFor: per-service offer only discounts the matching service", () => {
  // 25% of just the 400 haircut = 100
  assert.equal(offerDiscountFor({ service: "haircut", discountPercent: 25 }, S), 100);
});

test("offerDiscountFor: per-service offer for a service not booked -> 0", () => {
  assert.equal(offerDiscountFor({ service: "massage", discountPercent: 50 }, S), 0);
});

test("offerDiscountFor: percentage wins when both percent and amount are set", () => {
  // 10% of 600 = 60 (beats/overrides the flat 50, mirroring promoDiscountFor)
  assert.equal(offerDiscountFor({ discountPercent: 10, discountAmount: 50 }, S), 60);
});

test("offerDiscountFor: no offer / empty services -> 0", () => {
  assert.equal(offerDiscountFor(null, S), 0);
  assert.equal(offerDiscountFor({ discountPercent: 20 }, []), 0);
});
