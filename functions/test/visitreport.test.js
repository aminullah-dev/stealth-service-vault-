"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  VISIT_REASONS, VISIT_ACTIONS, REPORT_WINDOW_DAYS, REPORT_WINDOW_MS,
  isVisitReason, isVisitAction, canReportVisit, visitPlan,
} = require("../lib/visitreport");

const NOW = 1_800_000_000_000;
const HOUR = 3600_000;
const appt = (over = {}) => ({
  customerId: "cust1",
  status: "COMPLETED",
  appointmentDate: NOW - 2 * HOUR,
  ...over,
});

test("the customer who booked it can report it", () => {
  assert.deepEqual(canReportVisit({ appointment: appt(), callerUid: "cust1", now: NOW }), { ok: true });
});

test("nobody else can", () => {
  // A salon reporting its own customer has reportCustomer. This is hers.
  assert.equal(canReportVisit({ appointment: appt(), callerUid: "someone", now: NOW }).why, "NOT_YOURS");
  assert.equal(canReportVisit({ appointment: appt(), callerUid: "", now: NOW }).why, "NOT_YOURS");
  assert.equal(canReportVisit({ appointment: null, callerUid: "cust1", now: NOW }).why, "NOT_FOUND");
});

test("a CONFIRMED booking counts, because that is the one she turned up for", () => {
  // completePastAppointments only flips it two hours after the start. She may
  // be standing outside a locked salon well before that.
  assert.deepEqual(canReportVisit({ appointment: appt({ status: "CONFIRMED" }), callerUid: "cust1", now: NOW }),
    { ok: true });
});

test("a booking the salon never accepted, or already cancelled, is not this", () => {
  for (const status of ["PENDING", "CANCELLED", "AWAITING_PAYMENT", "DECLINED"]) {
    assert.equal(canReportVisit({ appointment: appt({ status }), callerUid: "cust1", now: NOW }).why,
      "NOT_REPORTABLE", status);
  }
});

test("not before it was due to happen", () => {
  assert.equal(canReportVisit({ appointment: appt({ appointmentDate: NOW + HOUR }), callerUid: "cust1", now: NOW }).why,
    "TOO_EARLY");
  // The moment it starts is allowed: a salon that is shut at the appointed
  // hour is exactly what NOT_SERVED is for, and she should not have to wait.
  assert.deepEqual(canReportVisit({ appointment: appt({ appointmentDate: NOW }), callerUid: "cust1", now: NOW }),
    { ok: true });
});

test("and not forever afterwards", () => {
  const old = appt({ appointmentDate: NOW - REPORT_WINDOW_MS - 1 });
  assert.equal(canReportVisit({ appointment: old, callerUid: "cust1", now: NOW }).why, "TOO_LATE");
  // Exactly on the boundary is still hers.
  const edge = appt({ appointmentDate: NOW - REPORT_WINDOW_MS });
  assert.deepEqual(canReportVisit({ appointment: edge, callerUid: "cust1", now: NOW }), { ok: true });
});

test("once per visit", () => {
  assert.equal(canReportVisit({ appointment: appt({ visitReported: true }), callerUid: "cust1", now: NOW }).why,
    "ALREADY_REPORTED");
});

test("a booking with no date is refused rather than treated as ancient", () => {
  for (const bad of [0, undefined, null, "yesterday", NaN]) {
    assert.equal(canReportVisit({ appointment: appt({ appointmentDate: bad }), callerUid: "cust1", now: NOW }).why,
      "NOT_REPORTABLE", String(bad));
  }
});

test("reasons and actions are closed sets", () => {
  assert.ok(isVisitReason("NOT_SERVED"));
  assert.ok(!isVisitReason("BECAUSE"));
  assert.ok(isVisitAction("REFUND_AND_SUSPEND"));
  assert.ok(!isVisitAction("DELETE_SALON"));
  assert.deepEqual(VISIT_REASONS,
    ["NOT_SERVED", "TURNED_AWAY", "DIFFERENT_SERVICE", "OVERCHARGED", "SAFETY", "OTHER"]);
  assert.deepEqual(VISIT_ACTIONS, ["DISMISS", "REFUND", "REFUND_AND_SUSPEND"]);
});

test("what each admin action does", () => {
  assert.deepEqual(visitPlan("DISMISS"),            { refund: false, suspendSalon: false });
  assert.deepEqual(visitPlan("REFUND"),             { refund: true,  suspendSalon: false });
  assert.deepEqual(visitPlan("REFUND_AND_SUSPEND"), { refund: true,  suspendSalon: true });
  // An unknown action plans nothing rather than defaulting to the harmless
  // one: a typo must fail loudly, not quietly dismiss her complaint.
  assert.equal(visitPlan("REFUND_ONLY"), null);
  assert.equal(visitPlan(""), null);
});

test("suspending a salon always refunds her too", () => {
  // Deciding a salon is unsafe and leaving the customer out of pocket is not a
  // resolution of what she reported.
  for (const action of VISIT_ACTIONS) {
    const plan = visitPlan(action);
    if (plan.suspendSalon) assert.ok(plan.refund, `${action} suspends without refunding`);
  }
});

test("the app's window is this window", () => {
  // VisitReportEligibility decides whether to draw the button; this decides
  // whether to accept it. If they drift, the app either offers a button the
  // server refuses — which teaches her not to trust the buttons — or hides one
  // it would still have taken.
  const swift = fs.readFileSync(
    path.join(__dirname, "..", "..", "ios/SafeBeautyCore/Sources/SafeBeautyCore/Models/Review.swift"),
    "utf8");
  const m = swift.match(/windowDays\s*=\s*(\d+)/);
  assert.ok(m, "VisitReportEligibility.windowDays is gone");
  assert.equal(Number(m[1]), REPORT_WINDOW_DAYS,
    `iOS offers a ${m[1]}-day window and the server enforces ${REPORT_WINDOW_DAYS}`);
});
