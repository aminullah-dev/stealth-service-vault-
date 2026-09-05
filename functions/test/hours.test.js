"use strict";

// The grid a booking is allowed to start on.
//
// The customer app offers only times from a grid it builds — open time, one
// step per slotDurationMinutes, stopping while the whole appointment still fits
// before closing. The server checked none of it: it rejected a slot already
// taken and a day the salon had blocked off, and accepted any other timestamp,
// so a reschedule could land at 03:17 on a Friday. hasSlotConflict cannot catch
// that — an off-grid time between two bookings collides with neither.
//
// These tests are written in UTC against Kabul (+04:30, no DST) so the
// expectations stay readable and stay true wherever they run.

const test = require("node:test");
const assert = require("node:assert");
const { defaultWorkingHours, hasBookableWeek, kabulMoment, slotFit } = require("../lib/hours");

const at = (iso) => Date.parse(iso);
const salon = (over = {}) => ({
  workingHours: defaultWorkingHours(),
  slotDurationMinutes: 60,
  ...over,
});

test("the default week is the one the provider editor shows", () => {
  const week = defaultWorkingHours();
  assert.strictEqual(week.length, 7);
  assert.strictEqual(week.filter((d) => d.isOpen).length, 6);
  // Friday, and only Friday, is the day off.
  assert.strictEqual(week.find((d) => d.dayOfWeek === 6).isOpen, false);
  for (const day of week.filter((d) => d.isOpen)) {
    assert.strictEqual(day.openHour, 9);
    assert.strictEqual(day.closeHour, 18);
  }
  assert.strictEqual(hasBookableWeek({ workingHours: week }), true);
  assert.strictEqual(hasBookableWeek({ workingHours: [] }), false);
  assert.strictEqual(hasBookableWeek({}), false);
});

test("kabulMoment reads the day and time a customer would see", () => {
  // 2026-08-31 is a Monday. Calendar.DAY_OF_WEEK: Sunday = 1, so Monday = 2.
  assert.deepStrictEqual(kabulMoment(at("2026-08-31T04:30:00Z")), { dayOfWeek: 2, minuteOfDay: 540 });
  // 23:00 UTC on Monday is already Tuesday 03:30 in Kabul — the +04:30 offset
  // is exactly why this cannot be done in UTC.
  assert.deepStrictEqual(kabulMoment(at("2026-08-31T23:00:00Z")), { dayOfWeek: 3, minuteOfDay: 210 });
  // Midnight, which Intl can render as hour 24.
  assert.deepStrictEqual(kabulMoment(at("2026-08-31T19:30:00Z")), { dayOfWeek: 3, minuteOfDay: 0 });
});

test("a time the customer app offers is accepted", () => {
  for (const h of ["04:30", "05:30", "09:30", "12:30"]) {          // 09:00–17:00 Kabul
    assert.strictEqual(slotFit(salon(), at(`2026-08-31T${h}:00Z`), 1).ok, true, h);
  }
});

test("a time it never offered is refused, and says which way it is wrong", () => {
  const cases = [
    ["2026-08-31T04:47:00Z", 1, "OFF_GRID"],       // 09:17 — between two slots
    ["2026-08-31T03:30:00Z", 1, "BEFORE_OPENING"], // 08:00 — before she opens
    ["2026-08-31T13:30:00Z", 1, "AFTER_CLOSING"],  // 18:00 — closing time itself
    ["2026-09-04T04:30:00Z", 1, "SALON_CLOSED"],   // Friday
    ["2026-08-31T22:47:00Z", 1, "BEFORE_OPENING"], // Tuesday 03:17
    ["2026-09-03T22:47:00Z", 1, "SALON_CLOSED"],   // Friday 03:17, the reported case
  ];
  for (const [iso, span, reason] of cases) {
    assert.deepStrictEqual(slotFit(salon(), at(iso), span), { ok: false, reason }, iso);
  }
});

test("a long appointment must fit entirely before closing", () => {
  const last = at("2026-08-31T12:30:00Z");                  // 17:00, the final slot
  assert.strictEqual(slotFit(salon(), last, 1).ok, true);
  assert.deepStrictEqual(slotFit(salon(), last, 2), { ok: false, reason: "AFTER_CLOSING" });
  // Three hours starting at 15:00 ends exactly at closing, which is allowed —
  // the client's loop condition is `start + duration <= close`.
  assert.strictEqual(slotFit(salon(), at("2026-08-31T10:30:00Z"), 3).ok, true);
  assert.strictEqual(slotFit(salon(), at("2026-08-31T10:30:00Z"), 4).ok, false);
});

test("the grid follows the salon's own slot length", () => {
  const half = salon({ slotDurationMinutes: 30 });
  assert.strictEqual(slotFit(half, at("2026-08-31T05:00:00Z"), 1).ok, true);   // 09:30
  assert.strictEqual(slotFit(salon(), at("2026-08-31T05:00:00Z"), 1).ok, false); // not on a 60 grid
  // The client coerces anything under 30 up to 30; so does this, or the two
  // would disagree about which times exist.
  const tiny = salon({ slotDurationMinutes: 5 });
  assert.strictEqual(slotFit(tiny, at("2026-08-31T05:00:00Z"), 1).ok, true);
  assert.strictEqual(slotFit(tiny, at("2026-08-31T04:35:00Z"), 1).ok, false);
});

test("a salon with no stored hours is not judged", () => {
  // That was its own defect — fixed at both creation paths and backfilled.
  // Refusing these bookings would break a salon that works today.
  assert.deepStrictEqual(slotFit({ workingHours: [] }, at("2026-09-03T22:47:00Z"), 1), { ok: true, reason: "" });
  assert.deepStrictEqual(slotFit({}, at("2026-09-03T22:47:00Z"), 1), { ok: true, reason: "" });
  assert.deepStrictEqual(slotFit(null, at("2026-09-03T22:47:00Z"), 1), { ok: true, reason: "" });
});

test("seconds on the clock mean the time did not come from the grid", () => {
  assert.deepStrictEqual(slotFit(salon(), at("2026-08-31T04:30:37Z"), 1), { ok: false, reason: "OFF_GRID" });
  for (const bad of [0, -1, NaN, undefined]) {
    assert.strictEqual(slotFit(salon(), bad, 1).ok, false);
  }
});

test("nonsense opening hours are left alone rather than guessed at", () => {
  const backwards = salon({ workingHours: [{ dayOfWeek: 2, isOpen: true, openHour: 18, closeHour: 9 }] });
  assert.strictEqual(slotFit(backwards, at("2026-08-31T04:30:00Z"), 1).ok, true);
})
