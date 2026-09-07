"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { claimPath, claimDecision, IN_FLIGHT_STALE_MS } = require("../lib/idempotency");

test("a request id nobody has claimed proceeds", () => {
  assert.deepEqual(claimDecision(null, 1000), { action: "proceed" });
  assert.deepEqual(claimDecision(undefined, 1000), { action: "proceed" });
});

test("a finished request replays its own result rather than doing the work twice", () => {
  // This is the whole point: the retry that follows a timeout must return the
  // booking the first attempt made, not make a second one.
  const existing = { createdAt: 500, result: { appointmentId: "a1", checkoutUrl: "u" } };
  assert.deepEqual(claimDecision(existing, 1000),
    { action: "replay", result: { appointmentId: "a1", checkoutUrl: "u" } });
});

test("a request still running is refused, not duplicated", () => {
  const existing = { createdAt: 1000 };
  assert.deepEqual(claimDecision(existing, 1000 + 1), { action: "inFlight" });
  assert.deepEqual(claimDecision(existing, 1000 + IN_FLIGHT_STALE_MS), { action: "inFlight" });
});

test("a claim whose attempt died is taken over rather than locked forever", () => {
  // A crash between creating the claim and writing the result would otherwise
  // burn that request id permanently — and the customer's retry with it.
  const existing = { createdAt: 1000 };
  assert.deepEqual(claimDecision(existing, 1000 + IN_FLIGHT_STALE_MS + 1), { action: "proceed" });
});

test("a stale claim that nonetheless has a result still replays", () => {
  // Age only decides whether an UNFINISHED attempt may be taken over. A
  // finished one is finished however long ago it was, or a retry after a long
  // pause would book twice.
  const existing = { createdAt: 1, result: { appointmentId: "a1" } };
  assert.deepEqual(claimDecision(existing, 1 + IN_FLIGHT_STALE_MS * 100),
    { action: "replay", result: { appointmentId: "a1" } });
});

test("the claim is scoped to the customer, so two people cannot collide on one id", () => {
  assert.equal(claimPath("u1", "r1"), "booking_claims/u1_r1");
  assert.notEqual(claimPath("u1", "r1"), claimPath("u2", "r1"));
});
