// Slot occupancy math for SafeBeauty, extracted so it can be unit-tested without
// Firebase. A booking is not a single instant: a multi-service or group/wedding
// booking realistically takes several back-to-back slots. We approximate one slot
// per service (the salon's slotDurationMinutes is the granularity), so an
// appointment blocks `slotsCount` consecutive slots and can't be silently
// double-booked over. Legacy appointments (no slotsCount/services) fall back to a
// single slot, so nothing old breaks.
//
// ── Processing time ─────────────────────────────────────────────────────────
//
// Some services leave the stylist free in the middle of them. Colouring hair is
// roughly 45 minutes of application, 30 minutes while the colour develops, then
// 20 minutes of washing and styling — and during those 30 minutes the stylist
// can be cutting somebody else's hair. Without modelling that, the salon either
// wastes the gap or overbooks into it by hand, and the gap is on the most
// expensive service in the market.
//
// So a booking spans a number of slots, and only SOME of them are ones the
// stylist is working. `busyOffsets` names which; everything else here — conflict
// detection, the customer's slot picker — already works on sets of slot times,
// so leaving the free ones out of that set is the whole change.
//
// Quantised to whole slots, because a 30-minute gap cannot be expressed inside a
// 60-minute grid. A salon that wants finer control sets a shorter
// slotDurationMinutes. The rounding is deliberately asymmetric:
//
//     working time rounds UP, processing time rounds DOWN
//
// so that every rounding error costs the salon a little capacity rather than
// handing the same stylist to two customers at once. An error that loses money
// is recoverable; an error that double-books is a woman turned away at the door.

/** 0, 1, … n-1 */
function range(n) {
  const out = [];
  for (let i = 0; i < n; i++) out.push(i);
  return out;
}

// The slot offsets an appointment's stylist is actually working, relative to its
// start. Stored on the appointment at booking time rather than recomputed from
// the salon, so that changing a service's timing later cannot retroactively move
// a booking that is already in someone's calendar.
function busyOffsetsOf(appt, span) {
  const raw = appt && appt.busyOffsets;
  if (!Array.isArray(raw) || raw.length === 0) return range(span);
  const clean = raw
    .map(Number)
    .filter((n) => Number.isInteger(n) && n >= 0 && n < span);
  // An appointment that claims to occupy nothing would be invisible to the
  // conflict check and could be booked straight over. Fall back to the whole
  // span, which is what every appointment written before this existed means.
  return clean.length ? clean : range(span);
}

// The list of slot start-times a single appointment occupies — meaning the times
// its stylist is unavailable, which for a service with processing time is fewer
// than the times the client is in the chair.
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
  return busyOffsetsOf(appt, span).map((i) => start + i * step);
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


/**
 * Where a booking's working time and its idle time fall.
 *
 * Returns the number of slots the booking spans and, of those, the offsets the
 * stylist is occupied. Services run back to back, so a colour followed by a
 * blow-dry puts the colour's development gap in the middle of the whole booking.
 *
 * [timings] is the salon's per-service breakdown — { activeBefore, processing,
 * activeAfter } in minutes. A service with no entry there falls back to
 * durationPerService and is treated as working throughout, which is exactly what
 * every salon means today, so nothing changes for one that never sets this.
 */
function serviceLayout(serviceNames, timings, durationPerService, slotMinutes) {
  const step = Math.max(1, Number(slotMinutes) || 30);
  const names = Array.isArray(serviceNames) ? serviceNames : [];
  const tim = timings && typeof timings === "object" ? timings : {};
  const durs = durationPerService && typeof durationPerService === "object" ? durationPerService : {};

  if (names.length === 0) return { span: 1, busyOffsets: [0] };

  const busy = [];
  let cursor = 0;
  for (const name of names) {
    const t = tim[name] || {};
    const before = Math.max(0, Number(t.activeBefore) || 0);
    const proc   = Math.max(0, Number(t.processing)   || 0);
    const after  = Math.max(0, Number(t.activeAfter)  || 0);

    let beforeSlots, procSlots, afterSlots;
    if (before + proc + after > 0) {
      // Working time up, idle time down — see the asymmetry note at the top.
      beforeSlots = Math.ceil(before / step);
      procSlots   = Math.floor(proc / step);
      afterSlots  = Math.ceil(after / step);
      // A service the stylist never works is not a service. If the rounding left
      // no working slots at all, keep one rather than produce a booking that
      // conflicts with nothing.
      if (beforeSlots + afterSlots === 0) beforeSlots = 1;
    } else {
      const d = Number(durs[name]);
      beforeSlots = Math.ceil((Number.isFinite(d) && d > 0 ? d : step) / step);
      procSlots = 0;
      afterSlots = 0;
    }

    for (let i = 0; i < beforeSlots; i++) busy.push(cursor + i);
    cursor += beforeSlots + procSlots;
    for (let i = 0; i < afterSlots; i++) busy.push(cursor + i);
    cursor += afterSlots;
  }

  const span = Math.max(1, cursor);
  return { span, busyOffsets: busy.length ? busy : [0] };
}

// True when a booking starting at [reqStart] for staff [staffId] would overlap
// any existing appointment. A different staffId is a different chair, so it never
// conflicts (parallel bookings across a multi-staff salon); a solo salon uses
// staffId "" for everything, so any overlap there conflicts. CANCELLED
// appointments are ignored, and [excludeId] (the appointment being rescheduled
// onto a new time) is skipped so it can't conflict with its own current slot.
// Each existing item may carry an `id`.
//
// [reqSpanOrOffsets] is either a slot count — every slot from the start is
// working time, which is what a booking without processing time means — or the
// explicit list of working offsets from serviceLayout. Both sides of the
// comparison are then just sets of times the stylist is busy, and a colour's
// development gap is simply not in either set.
function hasSlotConflict(existing, reqStart, reqSpanOrOffsets, staffId, slotMinutes, excludeId) {
  const start = Number(reqStart);
  if (!Number.isFinite(start)) return false;
  const step = Math.max(1, Number(slotMinutes) || 30) * 60000;
  const offsets = Array.isArray(reqSpanOrOffsets) && reqSpanOrOffsets.length
    ? reqSpanOrOffsets.map(Number).filter((n) => Number.isInteger(n) && n >= 0)
    : range(Math.max(1, Number(reqSpanOrOffsets) || 1));
  const wanted = new Set();
  for (const i of (offsets.length ? offsets : [0])) wanted.add(start + i * step);
  const wantStaff = String(staffId || "");
  for (const a of existing || []) {
    if (!a || a.status === "CANCELLED") continue;
    if (excludeId && a.id === excludeId) continue;
    if (String(a.staffId || "") !== wantStaff) continue;
    for (const t of slotsForAppointment(a, slotMinutes)) {
      if (wanted.has(t)) return true;
    }
  }
  return false;
}

module.exports = { slotsForAppointment, expandBooked, serviceLayout, hasSlotConflict };
