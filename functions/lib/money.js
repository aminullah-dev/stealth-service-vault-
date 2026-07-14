// Pure money math for SafeBeauty checkout, extracted from index.js so it can be
// unit-tested without Firebase. These functions do NO I/O and NO throwing — they
// are the arithmetic only; the callable in index.js still owns the Firestore
// reads, validation, and HttpsError messages. Keeping the math here means the
// commission / promo / referral rules can be locked down with fast unit tests.
//
// All amounts are in whole AFN (Afghanis). Inputs are coerced with Number() the
// same way the callable does, so bad/missing fields degrade to 0 rather than NaN.

// Discount (AFN) a promo grants against a booking of [priceAfn].
// Percentage wins when both are set; the discount can never exceed the price, so
// the amount the customer pays is always >= 0. Mirrors the original inline logic.
function promoDiscountFor(promo, priceAfn) {
  const price = Number(priceAfn) || 0;
  let discount = 0;
  const pct = Number((promo && promo.discountPercent) || 0);
  const amt = Number((promo && promo.discountAmount) || 0);
  if (pct > 0) discount = Math.round((price * Math.min(pct, 100)) / 100);
  else if (amt > 0) discount = Math.min(Math.round(amt), price);
  discount = Math.max(0, Math.min(discount, price));
  return discount;
}

// Discount (AFN) a live salon offer grants against a resolved [services]
// breakdown (each {name, price}). A salon-wide offer (blank `service`) applies to
// the whole subtotal; a per-service offer applies only to matching services'
// prices. Uses the same percentage-wins-over-amount rule as a promo, capped at
// the applicable base. The caller is responsible for only passing a live offer.
function offerDiscountFor(offer, services) {
  if (!offer) return 0;
  const list = Array.isArray(services) ? services : [];
  const svc = String(offer.service || "").trim();
  const base = (svc
    ? list.filter((s) => s && String(s.name) === svc)
    : list
  ).reduce((sum, s) => sum + (Number(s && s.price) || 0), 0);
  if (base <= 0) return 0;
  return promoDiscountFor(
    { discountPercent: offer.discountPercent, discountAmount: offer.discountAmount },
    base
  );
}

// Full checkout split for one booking. Given the list price, an already-resolved
// promo discount, the customer's available referral credit, and the platform
// commission %, returns every derived amount the callable stores:
//   afterPromo      – price after the promo discount (never below 0)
//   referralUsed    – referral credit actually consumed (capped at afterPromo)
//   price           – what the customer is charged
//   commissionAmount– platform's cut, rounded to whole AFN
//   providerNet     – what the salon receives / is credited
function computeCheckout({ listPrice, promoDiscount = 0, referralCredit = 0, commissionPercent = 0 }) {
  const list = Number(listPrice) || 0;
  const afterPromo = Math.max(0, list - (Number(promoDiscount) || 0));
  const availableCredit = Math.max(0, Number(referralCredit) || 0);
  const referralUsed = Math.min(availableCredit, afterPromo);
  const price = afterPromo - referralUsed;
  const commissionAmount = Math.round((price * (Number(commissionPercent) || 0)) / 100);
  const providerNet = price - commissionAmount;
  return { afterPromo, referralUsed, price, commissionAmount, providerNet };
}

// Resolves a list of requested service names against a salon's pricePerService
// map into a priced breakdown + total. This is the shared money path for every
// booking shape: a single service, several services for one person (multi-service),
// or many services across several people (group / wedding) — the caller just
// flattens the party into one list of names. Duplicates are allowed (two guests
// booking the same service). Names with no valid positive price are collected in
// `invalid` so the callable can tell the customer exactly which one is the problem.
function resolveServicesTotal(pricePerService, serviceNames) {
  const prices = pricePerService || {};
  const list = Array.isArray(serviceNames) ? serviceNames : [serviceNames];
  const cleaned = list.map((n) => String(n == null ? "" : n).trim()).filter(Boolean);
  const services = [];
  const invalid = [];
  let total = 0;
  for (const name of cleaned) {
    const price = Number(prices[name]);
    if (!Number.isFinite(price) || price <= 0) {
      if (!invalid.includes(name)) invalid.push(name);
      continue;
    }
    services.push({ name, price });
    total += price;
  }
  return { services, total, invalid };
}

// Validates a gift-card amount (whole AFN). Bounds keep a typo from charging a
// fortune and block zero/negative gifts. Returns the coerced integer value so the
// caller always stores a clean number.
function validateGiftAmount(amount, opts) {
  const min = (opts && opts.min) || 50;
  const max = (opts && opts.max) || 50000;
  const n = Math.floor(Number(amount));
  if (!Number.isFinite(n) || n <= 0) return { ok: false, value: 0, reason: "invalid" };
  if (n < min) return { ok: false, value: n, reason: "too_small" };
  if (n > max) return { ok: false, value: n, reason: "too_large" };
  return { ok: true, value: n, reason: "" };
}

// Converts a requested number of loyalty [points] into spendable wallet credit.
// Points are redeemed in whole [step] increments (so a request of 250 at step 100
// spends 200), with a [min] floor and a points→AFN [ratio]. Returns the exact
// points to deduct (`spend`) and AFN to grant (`credit`); the callable still
// re-checks the balance inside a transaction before applying either.
function loyaltyToCredit(points, opts) {
  const ratio = (opts && opts.ratio) || 1;
  const min   = (opts && opts.min)   || 100;
  const step  = (opts && opts.step)  || 100;
  const p = Math.floor(Number(points));
  if (!Number.isFinite(p) || p <= 0) return { ok: false, spend: 0, credit: 0, reason: "invalid" };
  if (p < min) return { ok: false, spend: 0, credit: 0, reason: "too_few" };
  const spend  = Math.floor(p / step) * step;
  const credit = Math.round(spend * ratio);
  return { ok: true, spend, credit, reason: "" };
}

module.exports = {
  promoDiscountFor, computeCheckout, resolveServicesTotal, validateGiftAmount, loyaltyToCredit,
  offerDiscountFor,
};
