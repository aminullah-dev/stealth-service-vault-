// Unit tests for the checkout money math (lib/money.js). Pure arithmetic, no
// Firebase — run with `npm test` (uses Node's built-in test runner, no deps).
const test = require("node:test");
const assert = require("node:assert/strict");
const { promoDiscountFor, computeCheckout } = require("../lib/money");

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
