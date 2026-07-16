// Unit tests for slot-occupancy math (lib/slots.js). Pure, no Firebase.
const test = require("node:test");
const assert = require("node:assert/strict");
const { slotsForAppointment, expandBooked, serviceSlotSpan, hasSlotConflict } = require("../lib/slots");

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

// ── serviceSlotSpan (adaptive per-service duration) ──────────────────────────
test("serviceSlotSpan: all durations absent == number of services (legacy)", () => {
  assert.equal(serviceSlotSpan(["a", "b", "c"], {}, 30), 3);
  assert.equal(serviceSlotSpan(["a"], undefined, 30), 1);
});

test("serviceSlotSpan: one 120-min service at a 30-min salon blocks 4 slots", () => {
  assert.equal(serviceSlotSpan(["haircut"], { haircut: 120 }, 30), 4);
});

test("serviceSlotSpan: mixed set rounds total minutes up", () => {
  // 120 + 30(fallback) + 45 = 195 min / 30 = 6.5 -> 7 slots
  assert.equal(serviceSlotSpan(["a", "b", "c"], { a: 120, c: 45 }, 30), 7);
});

test("serviceSlotSpan: zero/negative duration falls back to one slot", () => {
  assert.equal(serviceSlotSpan(["a", "b"], { a: 0, b: -10 }, 30), 2);
});

test("serviceSlotSpan: empty service list -> 1", () => {
  assert.equal(serviceSlotSpan([], { a: 120 }, 30), 1);
});

test("serviceSlotSpan: missing slotMinutes defaults to 30", () => {
  assert.equal(serviceSlotSpan(["a"], { a: 60 }), 2);
});
