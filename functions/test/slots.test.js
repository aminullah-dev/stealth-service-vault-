// Unit tests for slot-occupancy math (lib/slots.js). Pure, no Firebase.
const test = require("node:test");
const assert = require("node:assert/strict");
const { slotsForAppointment, expandBooked, hasSlotConflict, serviceLayout } = require("../lib/slots");

const T = 1_000_000_000_000; // an arbitrary base time (ms)
const MIN = 60_000;

test("hasSlotConflict: exact same slot + staff conflicts", () => {
  const existing = [{ appointmentDate: T, services: ["A"], staffId: "s1", status: "CONFIRMED" }];
  assert.equal(hasSlotConflict(existing, T, 1, "s1", 30), true);
});

test("hasSlotConflict: different staff is a different chair (no conflict)", () => {
  const existing = [{ appointmentDate: T, services: ["A"], staffId: "s1", status: "CONFIRMED" }];
  assert.equal(hasSlotConflict(existing, T, 1, "s2", 30), false);
});

test("hasSlotConflict: solo salon (staffId '') any overlap conflicts", () => {
  const existing = [{ appointmentDate: T, services: ["A"], staffId: "", status: "PENDING" }];
  assert.equal(hasSlotConflict(existing, T, 1, "", 30), true);
});

test("hasSlotConflict: a multi-slot booking overlapping a later slot conflicts", () => {
  // existing takes T; new 3-slot booking starts one slot earlier and runs over T.
  const existing = [{ appointmentDate: T + 30 * MIN, services: ["A"], staffId: "s1", status: "CONFIRMED" }];
  assert.equal(hasSlotConflict(existing, T, 3, "s1", 30), true); // T, T+30, T+60 → hits T+30
});

test("hasSlotConflict: adjacent non-overlapping slots do not conflict", () => {
  const existing = [{ appointmentDate: T, services: ["A"], staffId: "s1", status: "CONFIRMED" }];
  assert.equal(hasSlotConflict(existing, T + 30 * MIN, 1, "s1", 30), false);
});

test("hasSlotConflict: CANCELLED and excludeId are ignored", () => {
  const cancelled = [{ id: "x", appointmentDate: T, staffId: "s1", status: "CANCELLED" }];
  assert.equal(hasSlotConflict(cancelled, T, 1, "s1", 30), false);
  const self = [{ id: "me", appointmentDate: T, staffId: "s1", status: "CONFIRMED" }];
  assert.equal(hasSlotConflict(self, T, 1, "s1", 30, "me"), false);
});

test("hasSlotConflict: AWAITING_PAYMENT reserves the slot", () => {
  const existing = [{ appointmentDate: T, services: ["A"], staffId: "", status: "AWAITING_PAYMENT" }];
  assert.equal(hasSlotConflict(existing, T, 1, "", 30), true);
});

test("hasSlotConflict: non-finite start never conflicts", () => {
  assert.equal(hasSlotConflict([{ appointmentDate: T, staffId: "", status: "PENDING" }], NaN, 1, "", 30), false);
});

test("slotsForAppointment: single-service booking takes one slot", () => {
  const r = slotsForAppointment({ appointmentDate: T, services: ["Haircut"] }, 30);
  assert.deepEqual(r, [T]);
});

test("slotsForAppointment: multi-service blocks back-to-back slots", () => {
  const r = slotsForAppointment({ appointmentDate: T, services: ["Haircut", "Makeup", "Nails"] }, 30);
  assert.deepEqual(r, [T, T + 30 * MIN, T + 60 * MIN]);
});

test("slotsForAppointment: slotsCount wins over services length", () => {
  const r = slotsForAppointment({ appointmentDate: T, slotsCount: 2, services: ["A", "B", "C"] }, 60);
  assert.deepEqual(r, [T, T + 60 * MIN]);
});

test("slotsForAppointment: legacy appointment (no services/slotsCount) = one slot", () => {
  const r = slotsForAppointment({ appointmentDate: T }, 45);
  assert.deepEqual(r, [T]);
});

test("slotsForAppointment: missing slotMinutes defaults to 30", () => {
  const r = slotsForAppointment({ appointmentDate: T, slotsCount: 2 }, undefined);
  assert.deepEqual(r, [T, T + 30 * MIN]);
});

test("slotsForAppointment: invalid appointmentDate -> no slots", () => {
  assert.deepEqual(slotsForAppointment({ appointmentDate: "nope", services: ["A"] }, 30), []);
  assert.deepEqual(slotsForAppointment(null, 30), []);
});

test("expandBooked: flattens every appointment's slots, tagged with staff", () => {
  const appts = [
    { appointmentDate: T, services: ["A", "B"], staffId: "s1" },  // 2 slots
    { appointmentDate: T + 180 * MIN, services: ["C"], staffId: "s2" }, // 1 slot
  ];
  const { slots, booked } = expandBooked(appts, 30);
  assert.deepEqual(slots, [T, T + 30 * MIN, T + 180 * MIN]);
  assert.deepEqual(booked, [
    { time: T, staffId: "s1" },
    { time: T + 30 * MIN, staffId: "s1" },
    { time: T + 180 * MIN, staffId: "s2" },
  ]);
});

test("expandBooked: empty / missing input -> empty result", () => {
  assert.deepEqual(expandBooked([], 30), { slots: [], booked: [] });
  assert.deepEqual(expandBooked(undefined, 30), { slots: [], booked: [] });
});

test("expandBooked: a 5-service party at 60-min slots blocks a 5-hour span", () => {
  const { slots } = expandBooked(
    [{ appointmentDate: T, services: ["a", "b", "c", "d", "e"], staffId: "" }],
    60
  );
  assert.equal(slots.length, 5);
  assert.equal(slots[4], T + 4 * 60 * MIN);
});

// ── span, from serviceLayout (adaptive per-service duration) ─────────────────
// These were written against serviceSlotSpan, which serviceLayout replaced. Two
// functions that must agree about how long a booking is are one too many.
const spanOf = (names, durs, slot) => serviceLayout(names, {}, durs, slot).span;
test("serviceSlotSpan: all durations absent == number of services (legacy)", () => {
  assert.equal(spanOf(["a", "b", "c"], {}, 30), 3);
  assert.equal(spanOf(["a"], undefined, 30), 1);
});

test("serviceSlotSpan: one 120-min service at a 30-min salon blocks 4 slots", () => {
  assert.equal(spanOf(["haircut"], { haircut: 120 }, 30), 4);
});

test("serviceSlotSpan: mixed set rounds total minutes up", () => {
  // 120 + 30(fallback) + 45 = 195 min / 30 = 6.5 -> 7 slots
  assert.equal(spanOf(["a", "b", "c"], { a: 120, c: 45 }, 30), 7);
});

test("serviceSlotSpan: zero/negative duration falls back to one slot", () => {
  assert.equal(spanOf(["a", "b"], { a: 0, b: -10 }, 30), 2);
});

test("serviceSlotSpan: empty service list -> 1", () => {
  assert.equal(spanOf([], { a: 120 }, 30), 1);
});

test("serviceSlotSpan: missing slotMinutes defaults to 30", () => {
  assert.equal(spanOf(["a"], { a: 60 }), 2);
});

// ── Processing time ──────────────────────────────────────────────────────────
//
// A colour is roughly 45 minutes of application, 30 while it develops, then 20 to
// wash and style. The middle 30 is the stylist's to sell to someone else, and
// these tests are what stop that turning into two customers in one chair.

const COLOUR = { activeBefore: 45, processing: 30, activeAfter: 20 };

test("serviceLayout: a colour at 30-min slots frees the development gap", () => {
  const { span, busyOffsets } = serviceLayout(["Colour"], { Colour: COLOUR }, {}, 30);
  assert.equal(span, 4, "45 up = 2, 30 down = 1, 20 up = 1");
  assert.deepEqual(busyOffsets, [0, 1, 3], "slot 2 is the stylist's to sell");
});

test("serviceLayout: at 60-min slots the gap is too small to exist", () => {
  // Honest consequence of a coarse grid: 30 minutes of processing rounds DOWN to
  // no free slot. The salon loses the gap, which is the safe direction.
  const { span, busyOffsets } = serviceLayout(["Colour"], { Colour: COLOUR }, {}, 60);
  assert.deepEqual(busyOffsets, [0, 1]);
  assert.equal(span, 2, "no slot is claimed free that the stylist might need");
});

test("serviceLayout: processing rounds down, working time rounds up", () => {
  // 50 minutes of processing on a 60-minute grid is not a free hour.
  const t = { X: { activeBefore: 10, processing: 50, activeAfter: 10 } };
  const { span, busyOffsets } = serviceLayout(["X"], t, {}, 60);
  assert.deepEqual(busyOffsets, [0, 1], "both 10-minute halves round up to a slot");
  assert.equal(span, 2);
});

test("serviceLayout: a service the stylist never works still takes a slot", () => {
  // Rounding could otherwise leave a booking that conflicts with nothing and can
  // be booked straight over.
  const t = { Soak: { activeBefore: 0, processing: 90, activeAfter: 0 } };
  const { busyOffsets } = serviceLayout(["Soak"], t, {}, 60);
  assert.ok(busyOffsets.length >= 1, "must occupy the stylist somewhere");
  assert.equal(busyOffsets[0], 0);
});

test("serviceLayout: no timing configured behaves exactly as before", () => {
  const a = serviceLayout(["Cut", "Blowdry"], {}, {}, 60);
  assert.equal(a.span, 2);
  assert.deepEqual(a.busyOffsets, [0, 1], "every slot is working time");
  const b = serviceLayout(["Long"], {}, { Long: 120 }, 30);
  assert.equal(b.span, 4);
  assert.deepEqual(b.busyOffsets, [0, 1, 2, 3]);
});

test("serviceLayout: services run back to back, gap keeps its place", () => {
  const t = { Colour: COLOUR };
  const { span, busyOffsets } = serviceLayout(["Colour", "Cut"], t, { Cut: 30 }, 30);
  assert.equal(span, 5, "colour spans 4, cut adds 1");
  assert.deepEqual(busyOffsets, [0, 1, 3, 4], "the gap stays inside the colour");
});

test("a blow-dry fits inside a colour's development gap", () => {
  const slot = 30, base = 1_800_000_000_000;
  const { span, busyOffsets } = serviceLayout(["Colour"], { Colour: COLOUR }, {}, slot);
  const colour = { appointmentDate: base, slotsCount: span, busyOffsets, staffId: "zahra" };

  // Slot 2 — the gap. One slot of work fits, and the stylist is genuinely free.
  const inGap = base + 2 * slot * 60000;
  assert.equal(hasSlotConflict([colour], inGap, 1, "zahra", slot), false,
    "the whole point: the stylist can sell the gap");
});

test("a booking that runs out of the gap collides with the washout", () => {
  const slot = 30, base = 1_800_000_000_000;
  const { span, busyOffsets } = serviceLayout(["Colour"], { Colour: COLOUR }, {}, slot);
  const colour = { appointmentDate: base, slotsCount: span, busyOffsets, staffId: "zahra" };

  const inGap = base + 2 * slot * 60000;
  assert.equal(hasSlotConflict([colour], inGap, 2, "zahra", slot), true,
    "two slots from the gap runs into the wash and style");
});

test("the gap is not free for a different service on the same chair at the start", () => {
  const slot = 30, base = 1_800_000_000_000;
  const { span, busyOffsets } = serviceLayout(["Colour"], { Colour: COLOUR }, {}, slot);
  const colour = { appointmentDate: base, slotsCount: span, busyOffsets, staffId: "zahra" };
  assert.equal(hasSlotConflict([colour], base, 1, "zahra", slot), true,
    "the application itself is working time");
});

test("hasSlotConflict accepts explicit offsets for the requested booking too", () => {
  const slot = 30, base = 1_800_000_000_000;
  // An existing plain 1-slot booking sitting exactly where a colour's gap would be.
  const other = { appointmentDate: base + 2 * slot * 60000, slotsCount: 1, staffId: "zahra" };
  const { busyOffsets } = serviceLayout(["Colour"], { Colour: COLOUR }, {}, slot);
  assert.equal(hasSlotConflict([other], base, busyOffsets, "zahra", slot), false,
    "a colour can be booked around a short appointment sitting in its gap");
  assert.equal(hasSlotConflict([other], base, 4, "zahra", slot), true,
    "…but not if the colour is treated as busy throughout");
});

test("a free gap on one stylist is not a free gap on another", () => {
  const slot = 30, base = 1_800_000_000_000;
  const { span, busyOffsets } = serviceLayout(["Colour"], { Colour: COLOUR }, {}, slot);
  const colour = { appointmentDate: base, slotsCount: span, busyOffsets, staffId: "zahra" };
  assert.equal(hasSlotConflict([colour], base, 1, "sara", slot), false,
    "a different stylist was never blocked to begin with");
});

test("slotsForAppointment: a legacy appointment is busy throughout", () => {
  const slot = 60, base = 1_800_000_000_000;
  const legacy = { appointmentDate: base, slotsCount: 3 };
  assert.equal(slotsForAppointment(legacy, slot).length, 3,
    "no busyOffsets means every slot, which is what old bookings meant");
});

test("slotsForAppointment: a booking claiming no busy slots falls back to all", () => {
  const slot = 60, base = 1_800_000_000_000;
  const broken = { appointmentDate: base, slotsCount: 3, busyOffsets: [] };
  assert.equal(slotsForAppointment(broken, slot).length, 3,
    "an appointment occupying nothing could be booked straight over");
  const outOfRange = { appointmentDate: base, slotsCount: 2, busyOffsets: [7, 9] };
  assert.equal(slotsForAppointment(outOfRange, slot).length, 2, "nonsense offsets are not trusted");
});
