// Unit tests for slot-occupancy math (lib/slots.js). Pure, no Firebase.
const test = require("node:test");
const assert = require("node:assert/strict");
const { slotsForAppointment, expandBooked } = require("../lib/slots");

const T = 1_000_000_000_000; // an arbitrary base time (ms)
const MIN = 60_000;

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
