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

module.exports = { promoDiscountFor, computeCheckout, resolveServicesTotal };
