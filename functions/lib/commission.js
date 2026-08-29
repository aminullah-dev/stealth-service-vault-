"use strict";

/**
 * Whether reporting a no-show should give the salon its commission back.
 *
 * A cash booking debits the commission to the salon the moment it is made
 * (createPaymentSession), on the assumption that the customer will arrive and
 * hand over the money. When nobody arrives, that assumption was wrong and the
 * salon is left owing the platform a cut of cash it never touched. Cancelling
 * the same booking already refunds that debt; reporting the no-show did not,
 * which meant the salon was punished for using the feature honestly.
 *
 * Online payments are deliberately excluded. That money genuinely moved, the
 * slot was genuinely held, and whether the customer gets any of it back is the
 * refund flow's decision — not this one's.
 *
 * `commissionReversed` is the idempotency stamp. Both this path and
 * cancelAppointment set it, so a salon that reports a no-show and then cancels
 * the same booking is credited once rather than twice.
 */
function shouldReverseCommission(noShow, payment) {
  if (noShow !== true) return false;
  if (!payment || typeof payment !== "object") return false;
  if (payment.method !== "CASH") return false;
  if (payment.commissionReversed === true) return false;
  if (!payment.providerId) return false;
  return commissionToReturn(payment) > 0;
}

/**
 * What a cash booking does to the salon's balance the moment it is made.
 *
 * Two halves that move in opposite directions. The commission is a debt: the
 * customer hands the whole price to the salon, so the platform's cut has to be
 * collected back out of a later payout. The referral credit is the reverse — it
 * is the part of the price the customer did NOT hand over, because she paid it
 * from her wallet, and that wallet is the platform's obligation and never the
 * salon's. A gift card was bought with real money the platform is holding; a
 * referral reward, a KYC bonus and redeemed loyalty points are promotions the
 * platform chose to run. The salon agreed to a price and served the appointment
 * either way.
 *
 * Counting only the commission meant a customer with enough credit was served
 * for nothing, and if that credit came from a gift card the platform kept the
 * money too.
 *
 * Positive means the platform owes the salon; negative means the salon owes the
 * platform. Every reversal — a cancellation, a no-show — is exactly the
 * negation of this, which is the only way the ledger stays flat.
 */
function cashLedgerDelta(payment) {
  const p = payment || {};
  return whole(p.referralUsed) - whole(p.commissionAmount);
}

/**
 * The same, for a booking paid online. providerNet is what she earns from the
 * money that actually moved; referralUsed is the rest of the price, which
 * reached the platform earlier or was never charged at all.
 */
function onlineLedgerDelta(payment) {
  const p = payment || {};
  return whole(p.providerNet) + whole(p.referralUsed);
}

/** A stored amount as whole AFN, treating anything malformed as nothing. */
function whole(value) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.round(n) : 0;
}

/** The amount to credit back, floored at zero so a malformed doc can't debit. */
function commissionToReturn(payment) {
  const amount = Number(payment && payment.commissionAmount);
  if (!Number.isFinite(amount) || amount <= 0) return 0;
  return Math.round(amount);
}

module.exports = {
  shouldReverseCommission,
  commissionToReturn,
  cashLedgerDelta,
  onlineLedgerDelta,
};
