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
 * When the current wait began — which is not always when the booking was made.
 *
 * Rescheduling puts a booking back to PENDING: the salon has to agree to the
 * new time, and the clock on that agreement starts now. Measured from
 * createdAt, a booking made three days ago and moved to next week was already
 * a day past its deadline the moment it was rescheduled, so the very next
 * sweep cancelled and refunded a booking the customer had just successfully
 * moved. The customer did everything right and lost the appointment for it.
 *
 * createdAt is left alone — it is when the booking was made, it is shown to
 * both parties, and overwriting it to fix a scheduling clock would corrupt the
 * record. The fallback is what makes this safe without a backfill: every
 * booking written before pendingSince existed keeps exactly today's behaviour.
 *
 * @param {{pendingSince?: number, createdAt?: number}} appt
 * @returns {number} epoch millis
 */
function pendingSince(appt) {
  const a = appt || {};
  return Number(a.pendingSince) || Number(a.createdAt) || 0;
}

/**
 * The moment we stop waiting. Whichever comes first: a full day after the wait
 * began, or shortly before the appointment itself — a slot booked for three
 * hours from now cannot wait twenty-four.
 *
 * @param {{pendingSince?: number, createdAt?: number, appointmentDate?: number}} appt
 * @returns {number} epoch millis
 */
function unconfirmedDeadline(appt) {
  const a = appt || {};
  const byAge = pendingSince(a) + UNCONFIRMED_CANCEL_AFTER_MS;
  const byStart = (a.appointmentDate || 0) - CANCEL_BEFORE_APPOINTMENT_MS;
  return a.appointmentDate ? Math.min(byAge, byStart) : byAge;
}

/** Stage 1: has the salon been silent long enough to deserve a reminder? */
function isNudgeDue(appt, now) {
  const a = appt || {};
  if (a.providerNudged) return false;
  return now - pendingSince(a) >= UNCONFIRMED_NUDGE_AFTER_MS;
}

/** Stage 2: silent long enough that a person should pick up the phone. */
function isAdminDue(appt, now) {
  const a = appt || {};
  if (a.adminAlerted) return false;
  return now - pendingSince(a) >= UNCONFIRMED_ADMIN_AFTER_MS;
}

module.exports = {
  UNCONFIRMED_NUDGE_AFTER_MS,
  UNCONFIRMED_ADMIN_AFTER_MS,
  UNCONFIRMED_CANCEL_AFTER_MS,
  CANCEL_BEFORE_APPOINTMENT_MS,
  unconfirmedDeadline,
  pendingSince,
  isNudgeDue,
  isAdminDue,
};
