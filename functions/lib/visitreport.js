"use strict";

// A customer saying what happened at a visit.
//
// The salon has been able to report the customer since this product shipped —
// reportCustomer records a rating, a no-show and a misconduct flag, and an
// admin can suspend her over it. Nothing existed in the other direction.
//
// That asymmetry sits on top of another one. completePastAppointments flips a
// CONFIRMED booking to COMPLETED two hours after its start time, so
// "completed" means the clock passed, not that she was served — and the
// platform's commission is booked on that basis. A salon that took an online
// payment and served nobody kept the money, the platform kept its cut, and her
// only route was the support chat.
//
// In a marketplace whose product is trust, the side that can be reported and
// the side that can report should not be decided by which of them the software
// was written for first.

/** What she says went wrong. Closed set; the server refuses anything else. */
const VISIT_REASONS = [
  "NOT_SERVED",       // she went and was not served, or nobody was there
  "TURNED_AWAY",      // the salon refused her at the door
  "DIFFERENT_SERVICE",// she was given something other than what she booked
  "OVERCHARGED",      // she was asked for more than the agreed price
  "SAFETY",           // how she was treated — the one that can suspend a salon
  "OTHER",
];

/** What an admin can do about it. */
const VISIT_ACTIONS = ["DISMISS", "REFUND", "REFUND_AND_SUSPEND"];

/**
 * How long after the appointment she can still report it.
 *
 * Long enough that she does not have to act while she is still upset or still
 * in the salon, short enough that the salon can remember the visit and the
 * payment is still traceable.
 */
const REPORT_WINDOW_DAYS = 14;
const REPORT_WINDOW_MS = REPORT_WINDOW_DAYS * 24 * 60 * 60 * 1000;

function isVisitReason(value) { return VISIT_REASONS.includes(value); }
function isVisitAction(value) { return VISIT_ACTIONS.includes(value); }

/**
 * Whether this appointment can be reported by this caller, right now.
 *
 * Every refusal has its own code so she is told which one it is. "You cannot
 * report this" with no reason is how a person concludes the app is on the
 * salon's side.
 */
function canReportVisit({ appointment, callerUid, now }) {
  if (!appointment) return { ok: false, why: "NOT_FOUND" };
  if (!callerUid || appointment.customerId !== callerUid) {
    return { ok: false, why: "NOT_YOURS" };
  }

  // Only a booking the salon actually accepted. A PENDING one was never
  // agreed to and a CANCELLED one already has its own resolution.
  const status = String(appointment.status || "");
  if (status !== "CONFIRMED" && status !== "COMPLETED") {
    return { ok: false, why: "NOT_REPORTABLE" };
  }

  // Not before it was due to happen. Reporting a visit that has not occurred
  // yet is either a mistake or an attempt to pre-empt one.
  const startedAt = Number(appointment.appointmentDate || 0);
  if (!Number.isFinite(startedAt) || startedAt <= 0) return { ok: false, why: "NOT_REPORTABLE" };
  if (now < startedAt) return { ok: false, why: "TOO_EARLY" };

  if (now - startedAt > REPORT_WINDOW_MS) return { ok: false, why: "TOO_LATE" };

  // Once. A second report of the same visit is the same complaint, and the
  // queue should show one row per thing that happened.
  if (appointment.visitReported === true) return { ok: false, why: "ALREADY_REPORTED" };

  return { ok: true };
}

/** What resolving with [action] actually does. */
function visitPlan(action) {
  switch (action) {
    case "DISMISS":            return { refund: false, suspendSalon: false };
    case "REFUND":             return { refund: true,  suspendSalon: false };
    case "REFUND_AND_SUSPEND": return { refund: true,  suspendSalon: true };
    default:                   return null;
  }
}

module.exports = {
  VISIT_REASONS, VISIT_ACTIONS, REPORT_WINDOW_DAYS, REPORT_WINDOW_MS,
  isVisitReason, isVisitAction, canReportVisit, visitPlan,
};
