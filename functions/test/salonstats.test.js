const test = require("node:test");
const assert = require("node:assert");
const { statsDelta, isNoOp, countsAsConfirmed } = require("../lib/salonstats");

const appt = (status, serviceName = "Haircut") => ({ status, serviceName, salonId: "s1" });

test("a new booking counts once, in its status and its service", () => {
  const d = statsDelta(null, appt("PENDING"));
  assert.equal(d.total, 1);
  assert.deepEqual(d.byStatus, { PENDING: 1 });
  assert.deepEqual(d.byService, { Haircut: 1 });
  assert.deepEqual(d.confirmedByService, {},
    "a pending booking is not revenue");
});

test("confirming moves the booking between status buckets, not the total", () => {
  const d = statsDelta(appt("PENDING"), appt("CONFIRMED"));
  assert.equal(d.total, 0, "the same booking must not be counted twice");
  assert.deepEqual(d.byStatus, { PENDING: -1, CONFIRMED: 1 });
  assert.deepEqual(d.byService, {}, "the service did not change");
  assert.deepEqual(d.confirmedByService, { Haircut: 1 });
});

test("completing a confirmed booking does not double-count revenue", () => {
  // Both states count as confirmed, so the revenue bucket must not move.
  const d = statsDelta(appt("CONFIRMED"), appt("COMPLETED"));
  assert.deepEqual(d.confirmedByService, {},
    "CONFIRMED → COMPLETED is the same accepted booking");
  assert.deepEqual(d.byStatus, { CONFIRMED: -1, COMPLETED: 1 });
});

test("cancelling a confirmed booking takes it back out of revenue", () => {
  const d = statsDelta(appt("CONFIRMED"), appt("CANCELLED"));
  assert.deepEqual(d.confirmedByService, { Haircut: -1 });
  assert.deepEqual(d.byStatus, { CONFIRMED: -1, CANCELLED: 1 });
  assert.equal(d.total, 0);
});

test("deleting a booking removes everything it contributed", () => {
  const d = statsDelta(appt("COMPLETED"), null);
  assert.equal(d.total, -1);
  assert.deepEqual(d.byStatus, { COMPLETED: -1 });
  assert.deepEqual(d.byService, { Haircut: -1 });
  assert.deepEqual(d.confirmedByService, { Haircut: -1 });
});

test("a booking that changes service moves between service buckets", () => {
  const d = statsDelta(appt("CONFIRMED", "Haircut"), appt("CONFIRMED", "Nails"));
  assert.deepEqual(d.byService, { Haircut: -1, Nails: 1 });
  assert.deepEqual(d.confirmedByService, { Haircut: -1, Nails: 1 });
  assert.deepEqual(d.byStatus, {}, "the status did not change");
});

test("a write that changes nothing the tally tracks is a no-op", () => {
  // Rescheduling touches appointmentDate, which no bucket depends on.
  const d = statsDelta(appt("CONFIRMED"), appt("CONFIRMED"));
  assert.ok(isNoOp(d), "must not write to Firestore on every unrelated edit");
});

test("buckets that cancel out are dropped rather than written as zero", () => {
  const d = statsDelta(appt("PENDING", "Nails"), appt("PENDING", "Nails"));
  assert.deepEqual(d.byService, {});
  assert.deepEqual(d.byStatus, {});
});

test("a booking with no service name gets no service bucket", () => {
  // Counting it under "" would put a nameless row in the provider's breakdown.
  const d = statsDelta(null, appt("PENDING", ""));
  assert.deepEqual(d.byService, {});
  assert.equal(d.total, 1, "it still counts toward the total");
  assert.deepEqual(d.byStatus, { PENDING: 1 });
});

test("only CONFIRMED and COMPLETED count as accepted", () => {
  assert.equal(countsAsConfirmed("CONFIRMED"), true);
  assert.equal(countsAsConfirmed("COMPLETED"), true);
  for (const s of ["PENDING", "CANCELLED", "DECLINED", "AWAITING_PAYMENT", "", null]) {
    assert.equal(countsAsConfirmed(s), false, `${s} must not count as revenue`);
  }
});

test("an unpaid booking is counted but earns nothing", () => {
  const d = statsDelta(null, appt("AWAITING_PAYMENT"));
  assert.equal(d.total, 1);
  assert.deepEqual(d.confirmedByService, {});
});
