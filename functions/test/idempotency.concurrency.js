/**
 * Does the claim actually stop a duplicate booking, against a real Firestore?
 *
 * The pure decision is unit-tested. What that cannot test is the part that
 * matters: whether `.create()` really lets exactly one of two simultaneous
 * callers through. That is a property of the database, not of our code — the
 * same reason reservation.concurrency.js exists.
 *
 *   npm run test:idempotency
 */

const test = require("node:test");
const assert = require("node:assert");

// Fails rather than skips, for the reason the sibling file states: a
// concurrency guarantee that quietly reports success because no database was
// running is worse than no test at all.
if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("No Firestore emulator. Run these with: npm run test:idempotency");
}

const admin = require("firebase-admin");
if (!admin.apps.length) admin.initializeApp({ projectId: "safebeauty-idem-test" });
const db = admin.firestore();

const { claimPath, claimDecision } = require("../lib/idempotency");

/** Exactly what createPaymentSession does to take a claim. */
async function takeClaim(uid, requestId) {
  const ref = db.doc(claimPath(uid, requestId));
  const decision = claimDecision(
    await ref.get().then((s) => (s.exists ? s.data() : null)),
    Date.now()
  );
  if (decision.action !== "proceed") return decision;
  try {
    await ref.create({ uid, createdAt: Date.now() });
    return { action: "proceed", ref };
  } catch {
    const again = claimDecision(
      await ref.get().then((s) => (s.exists ? s.data() : null)),
      Date.now()
    );
    return again;
  }
}

test("two simultaneous identical requests: exactly one does the work", async () => {
  const uid = `u-${Date.now()}`;
  const rid = "same-request";
  const [a, b] = await Promise.all([takeClaim(uid, rid), takeClaim(uid, rid)]);
  const proceeded = [a, b].filter((r) => r.action === "proceed");
  assert.equal(proceeded.length, 1,
    `${proceeded.length} callers proceeded — a booking would have been made twice`);
  // The loser is told the work is happening, not handed a second booking.
  const other = [a, b].find((r) => r.action !== "proceed");
  assert.equal(other.action, "inFlight");
});

test("ten simultaneous identical requests: still exactly one", async () => {
  const uid = `u-${Date.now()}-ten`;
  const results = await Promise.all(
    Array.from({ length: 10 }, () => takeClaim(uid, "same-request"))
  );
  assert.equal(results.filter((r) => r.action === "proceed").length, 1);
});

test("a retry after the work finished replays the same booking", async () => {
  const uid = `u-${Date.now()}-replay`;
  const rid = "r1";
  const first = await takeClaim(uid, rid);
  assert.equal(first.action, "proceed");
  await first.ref.set({ result: { appointmentId: "appt-1" }, finishedAt: Date.now() },
                       { merge: true });

  const retry = await takeClaim(uid, rid);
  assert.equal(retry.action, "replay");
  assert.deepEqual(retry.result, { appointmentId: "appt-1" });
});

test("two customers using the same request id do not collide", async () => {
  const rid = "shared-id";
  const [a, b] = await Promise.all([
    takeClaim(`u-${Date.now()}-x`, rid),
    takeClaim(`u-${Date.now()}-y`, rid),
  ]);
  // Both must proceed: the id is scoped to the customer, and two people
  // booking at once must never block each other.
  assert.equal(a.action, "proceed");
  assert.equal(b.action, "proceed");
});
