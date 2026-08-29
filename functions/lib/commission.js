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

/** The amount to credit back, floored at zero so a malformed doc can't debit. */
function commissionToReturn(payment) {
  const amount = Number(payment && payment.commissionAmount);
  if (!Number.isFinite(amount) || amount <= 0) return 0;
  return Math.round(amount);
}

module.exports = { shouldReverseCommission, commissionToReturn };
