// Slot occupancy math for SafeBeauty, extracted so it can be unit-tested without
// Firebase. A booking is not a single instant: a multi-service or group/wedding
// booking realistically takes several back-to-back slots. We approximate one slot
// per service (the salon's slotDurationMinutes is the granularity), so an
// appointment blocks `slotsCount` consecutive slots and can't be silently
// double-booked over. Legacy appointments (no slotsCount/services) fall back to a
// single slot, so nothing old breaks.

// The list of slot start-times a single appointment occupies.
function slotsForAppointment(appt, slotMinutes) {
  const start = Number(appt ? appt.appointmentDate : NaN);
  if (!Number.isFinite(start)) return [];
  const step = Math.max(1, Number(slotMinutes) || 30) * 60000;
  const span = Math.max(
    1,
    Number(appt.slotsCount) ||
      (Array.isArray(appt.services) ? appt.services.length : 0) ||
      1
  );
  const out = [];
  for (let i = 0; i < span; i++) out.push(start + i * step);
  return out;
}

// Expands a list of appointments into the flat set of taken slot times. Returns
// both the legacy `slots` (plain times) and the staff-aware `booked` shape the
// client uses to allow parallel bookings across a multi-staff salon.
function expandBooked(appointments, slotMinutes) {
  const slots = [];
  const booked = [];
  for (const a of appointments || []) {
    const staffId = String((a && a.staffId) || "");
    for (const t of slotsForAppointment(a, slotMinutes)) {
      slots.push(t);
      booked.push({ time: t, staffId });
    }
  }
  return { slots, booked };
}

// How many consecutive slots a booking of these services occupies. Each service
// takes its own duration when set in durationPerService (minutes); services with
// no duration fall back to one whole slot (slotMinutes). The total minutes are
// divided by the slot granularity and rounded up, min 1. When every duration is
// absent this equals the number of services — identical to the previous
// one-slot-per-service behavior, so old salons are unaffected.
function serviceSlotSpan(serviceNames, durationPerService, slotMinutes) {
  const step = Math.max(1, Number(slotMinutes) || 30);
  const names = Array.isArray(serviceNames) ? serviceNames : [];
  const durs = durationPerService && typeof durationPerService === "object"
    ? durationPerService
    : {};
  if (names.length === 0) return 1;
  let totalMinutes = 0;
  for (const name of names) {
    const d = Number(durs[name]);
    totalMinutes += Number.isFinite(d) && d > 0 ? d : step;
  }
  return Math.max(1, Math.ceil(totalMinutes / step));
}

module.exports = { slotsForAppointment, expandBooked, serviceSlotSpan };
