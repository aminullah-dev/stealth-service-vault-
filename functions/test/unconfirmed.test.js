const test = require("node:test");
const assert = require("node:assert");
const {
  UNCONFIRMED_CANCEL_AFTER_MS,
  CANCEL_BEFORE_APPOINTMENT_MS,
  unconfirmedDeadline,
  pendingSince,
  isNudgeDue,
  isAdminDue,
} = require("../lib/unconfirmed");

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const NOW = 1_000_000_000_000;

test("a far-future appointment is bounded by the 24h age limit", () => {
  const d = unconfirmedDeadline({ createdAt: NOW, appointmentDate: NOW + 3 * DAY });
  assert.strictEqual(d, NOW + UNCONFIRMED_CANCEL_AFTER_MS);
});

test("a soon appointment is bounded by its own start time, not by age", () => {
  const appointmentDate = NOW + 3 * HOUR;
  const d = unconfirmedDeadline({ createdAt: NOW, appointmentDate });
  assert.strictEqual(d, appointmentDate - CANCEL_BEFORE_APPOINTMENT_MS);
  assert.ok(d < NOW + UNCONFIRMED_CANCEL_AFTER_MS, "must not wait the full day");
});

test("a booking is never cancelled after the appointment has started", () => {
  // Any appointment date, however far past or future: the deadline must sit
  // before the appointment itself. This is the guarantee that stops a customer
  // being told 'cancelled' while already standing at the salon.
  for (const offset of [-5 * DAY, -HOUR, 0, HOUR, 3 * DAY, 30 * DAY]) {
    const appt = { createdAt: NOW - 5 * DAY, appointmentDate: NOW + offset };
    assert.ok(
      unconfirmedDeadline(appt) < appt.appointmentDate,
      `deadline must precede the appointment (offset ${offset})`
    );
  }
});

test("a booking with no appointment date falls back to the age limit", () => {
  assert.strictEqual(unconfirmedDeadline({ createdAt: NOW }), NOW + UNCONFIRMED_CANCEL_AFTER_MS);
  assert.strictEqual(unconfirmedDeadline({ createdAt: NOW, appointmentDate: 0 }), NOW + UNCONFIRMED_CANCEL_AFTER_MS);
});

test("missing fields never produce NaN", () => {
  for (const appt of [undefined, null, {}, { appointmentDate: undefined }]) {
    assert.ok(Number.isFinite(unconfirmedDeadline(appt)), `finite for ${JSON.stringify(appt)}`);
  }
});

// ── the reschedule clock ─────────────────────────────────────────────────────
//
// Rescheduling puts a booking back to PENDING, so the wait for the salon's
// agreement starts over. Judged by createdAt, a booking made days ago and moved
// to next week was already past its deadline the moment it was moved — the very
// next sweep cancelled and refunded an appointment the customer had just
// successfully rescheduled.
const HOUR_MS = 60 * 60 * 1000;

test("pendingSince: a booking that predates the field keeps today's behaviour", () => {
  // No backfill was run, and none is needed: the fallback IS the old behaviour.
  const legacy = { createdAt: 1000 };
  assert.strictEqual(pendingSince(legacy), 1000);
  assert.strictEqual(unconfirmedDeadline(legacy), 1000 + 24 * HOUR_MS);
  assert.strictEqual(pendingSince({}), 0);
});

test("pendingSince: a reschedule restarts the clock", () => {
  const rescheduled = { createdAt: 1000, pendingSince: 500000 };
  assert.strictEqual(pendingSince(rescheduled), 500000);
});

test("a booking rescheduled just now is not swept, however old it is", () => {
  const now = 10 * 24 * HOUR_MS;                 // ten days into the epoch
  const appt = {
    createdAt: now - 9 * 24 * HOUR_MS,           // made nine days ago
    pendingSince: now,                           // moved a moment ago
    appointmentDate: now + 7 * 24 * HOUR_MS,     // to next week
  };
  assert.ok(now < unconfirmedDeadline(appt), "a freshly rescheduled booking was past its deadline");
  assert.strictEqual(isNudgeDue(appt, now), false);
  assert.strictEqual(isAdminDue(appt, now), false);

  // Judged the old way — the bug — it was a day and a half overdue.
  assert.ok(now > unconfirmedDeadline({ ...appt, pendingSince: undefined }));
});

test("the clock still runs: a salon silent since the reschedule is chased", () => {
  const now = 10 * 24 * HOUR_MS;
  const at = (agoHours) => ({
    createdAt: 0,
    pendingSince: now - agoHours * HOUR_MS,
    appointmentDate: now + 30 * 24 * HOUR_MS,    // far enough not to bind
  });
  assert.strictEqual(isNudgeDue(at(1), now), false);
  assert.strictEqual(isNudgeDue(at(3), now), true);
  assert.strictEqual(isAdminDue(at(3), now), false);
  assert.strictEqual(isAdminDue(at(7), now), true);
  assert.ok(now < unconfirmedDeadline(at(7)));
  assert.ok(now >= unconfirmedDeadline(at(25)), "a booking silent for 25h should be cancelled");
});

test("a stage already done is not repeated", () => {
  const now = 10 * 24 * HOUR_MS;
  const old = { createdAt: 0, pendingSince: now - 7 * HOUR_MS };
  assert.strictEqual(isNudgeDue({ ...old, providerNudged: true }, now), false);
  assert.strictEqual(isAdminDue({ ...old, adminAlerted: true }, now), false);
});

test("the imminent-appointment deadline still wins over the age one", () => {
  const now = 10 * 24 * HOUR_MS;
  // Rescheduled a moment ago to three hours from now: it cannot wait 24 hours,
  // and resetting the clock must not have bought it that time.
  const appt = { createdAt: 0, pendingSince: now, appointmentDate: now + 3 * HOUR_MS };
  assert.strictEqual(unconfirmedDeadline(appt), now + 1 * HOUR_MS);
})
