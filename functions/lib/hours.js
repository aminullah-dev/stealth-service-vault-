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

module.exports = { defaultWorkingHours, hasBookableWeek };
