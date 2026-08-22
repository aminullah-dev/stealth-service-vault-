/**
 * When to stop waiting for a provider to confirm a paid booking.
 *
 * A booking is paid before the provider confirms it, so an unconfirmed booking
 * is money already taken for a slot nobody has agreed to. This decides the
 * moment we give up and refund. Pure and dependency-free so the boundaries can
 * be tested — it decides whether real money moves.
 */

const HOUR = 60 * 60 * 1000;

const UNCONFIRMED_NUDGE_AFTER_MS   = 2 * HOUR;    // stage 1: remind the salon
const UNCONFIRMED_ADMIN_AFTER_MS   = 6 * HOUR;    // stage 2: escalate to a human
const UNCONFIRMED_CANCEL_AFTER_MS  = 24 * HOUR;   // stage 3: give up and refund
// Cancel far enough ahead that the customer can read the message and make other
// plans, rather than finding out outside a salon that never expected her.
const CANCEL_BEFORE_APPOINTMENT_MS = 2 * HOUR;

/**
 * The moment we stop waiting. Whichever comes first: a full day after booking,
 * or shortly before the appointment itself — a slot booked for three hours from
 * now cannot wait twenty-four.
 *
 * @param {{createdAt?: number, appointmentDate?: number}} appt
 * @returns {number} epoch millis
 */
function unconfirmedDeadline(appt) {
  const a = appt || {};
  const byAge = (a.createdAt || 0) + UNCONFIRMED_CANCEL_AFTER_MS;
  const byStart = (a.appointmentDate || 0) - CANCEL_BEFORE_APPOINTMENT_MS;
  return a.appointmentDate ? Math.min(byAge, byStart) : byAge;
}

module.exports = {
  UNCONFIRMED_NUDGE_AFTER_MS,
  UNCONFIRMED_ADMIN_AFTER_MS,
  UNCONFIRMED_CANCEL_AFTER_MS,
  CANCEL_BEFORE_APPOINTMENT_MS,
  unconfirmedDeadline,
};
