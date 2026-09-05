"use strict";

/**
 * The week a salon is assumed to keep until its owner says otherwise.
 *
 * A salon was created with `workingHours: []` while the provider's editor shows
 * `workingHours.ifEmpty { defaultWorkingHours() }` — a full Saturday-to-Thursday
 * week, already filled in. So the owner opened her profile, saw the hours she
 * expected, changed nothing, and saved nothing. The stored array stayed empty,
 * computeSlots produced no slots for any day, and her salon could never be
 * booked. Nothing failed; it simply never worked, and the one screen that could
 * have told her showed the opposite.
 *
 * These are the same seven entries the editor defaults to, so what she sees on
 * her first visit is what is already stored. Friday is the day off, which is the
 * Afghan working week rather than a guess.
 *
 * dayOfWeek follows java.util.Calendar: Sunday = 1 … Saturday = 7.
 */
function defaultWorkingHours() {
  const open = (dayOfWeek) => ({
    dayOfWeek, isOpen: true, openHour: 9, openMinute: 0, closeHour: 18, closeMinute: 0,
  });
  return [
    open(7),  // Saturday
    open(1),  // Sunday
    open(2),  // Monday
    open(3),  // Tuesday
    open(4),  // Wednesday
    open(5),  // Thursday
    { dayOfWeek: 6, isOpen: false, openHour: 9, openMinute: 0, closeHour: 13, closeMinute: 0 }, // Friday
  ];
}

/** Whether a salon can be booked on any day at all. */
function hasBookableWeek(salon) {
  const hours = salon && Array.isArray(salon.workingHours) ? salon.workingHours : [];
  return hours.some((h) => h && h.isOpen === true);
}


// ── Where a booking is allowed to start ──────────────────────────────────────
//
// The customer app builds a grid — open time, one step per slotDurationMinutes,
// stopping while the whole appointment still fits before closing — and offers
// only those times. The server checked none of it. It rejected a slot already
// taken and a day the salon had blocked off, and accepted any other timestamp
// at all, so a booking or a reschedule could land at 03:17 on a Friday the
// salon is closed. hasSlotConflict cannot see that: an off-grid time between
// two bookings collides with neither.
//
// Kabul time, explicitly. The client reads the device clock, and the server has
// no device — a check on UTC would reject the whole afternoon.

const KABUL = "Asia/Kabul";
const MIN_SLOT_MINUTES = 30;   // matches the client's coerceAtLeast(30)

/**
 * The weekday and minute-of-day a timestamp falls on in Kabul.
 *
 * dayOfWeek follows java.util.Calendar — Sunday = 1 … Saturday = 7 — because
 * that is what the salon's stored workingHours use, having been written by the
 * Android app.
 */
function kabulMoment(ms) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: KABUL,
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date(ms));

  const get = (type) => (parts.find((p) => p.type === type) || {}).value || "";
  const DAYS = { Sun: 1, Mon: 2, Tue: 3, Wed: 4, Thu: 5, Fri: 6, Sat: 7 };
  const hour = Number(get("hour"));
  return {
    dayOfWeek: DAYS[get("weekday")] || 0,
    // Intl can render midnight as 24 under hour12:false.
    minuteOfDay: (hour === 24 ? 0 : hour) * 60 + Number(get("minute")),
  };
}

/**
 * Whether an appointment of [spanSlots] slots may start at [startMs].
 *
 * Mirrors computeSlots in DashboardViewModel: the same open/close bounds, the
 * same step, and the same requirement that every slot the appointment covers
 * lies inside opening hours — a two-slot booking cannot start in the last one.
 *
 * A salon with no stored hours is not judged here. That was its own defect, it
 * is fixed at both creation paths and backfilled, and refusing those bookings
 * would take a salon that works today and break it.
 *
 * @returns {{ok: boolean, reason: string}}
 */
function slotFit(salon, startMs, spanSlots) {
  const s = salon || {};
  const hoursList = Array.isArray(s.workingHours) ? s.workingHours : [];
  if (hoursList.length === 0) return { ok: true, reason: "" };

  const start = Number(startMs);
  if (!Number.isFinite(start) || start <= 0) return { ok: false, reason: "BAD_TIME" };
  // Every slot the client offers sits on a whole minute; seconds here mean the
  // time did not come from the grid.
  if (start % 60000 !== 0) return { ok: false, reason: "OFF_GRID" };

  const { dayOfWeek, minuteOfDay } = kabulMoment(start);
  const today = hoursList.find((h) => h && Number(h.dayOfWeek) === dayOfWeek);
  if (!today || today.isOpen !== true) return { ok: false, reason: "SALON_CLOSED" };

  const step  = Math.max(MIN_SLOT_MINUTES, Number(s.slotDurationMinutes) || 60);
  const open  = Number(today.openHour) * 60 + Number(today.openMinute || 0);
  const close = Number(today.closeHour) * 60 + Number(today.closeMinute || 0);
  if (!Number.isFinite(open) || !Number.isFinite(close) || close <= open) {
    return { ok: true, reason: "" };    // nonsense hours are not this check's to judge
  }

  if (minuteOfDay < open) return { ok: false, reason: "BEFORE_OPENING" };
  if ((minuteOfDay - open) % step !== 0) return { ok: false, reason: "OFF_GRID" };

  const span = Math.max(1, Math.floor(Number(spanSlots) || 1));
  if (minuteOfDay + span * step > close) return { ok: false, reason: "AFTER_CLOSING" };

  return { ok: true, reason: "" };
}

module.exports = {
  defaultWorkingHours,
  hasBookableWeek,
  kabulMoment,
  slotFit,
  MIN_SLOT_MINUTES,
};
