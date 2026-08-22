const test = require("node:test");
const assert = require("node:assert");
const {
  UNCONFIRMED_CANCEL_AFTER_MS,
  CANCEL_BEFORE_APPOINTMENT_MS,
  unconfirmedDeadline,
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
