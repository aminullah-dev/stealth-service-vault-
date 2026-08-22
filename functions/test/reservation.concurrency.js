/**
 * Concurrency tests for slot reservation, against a real Firestore.
 *
 * These do not run with the normal unit suite. Whether a transaction actually
 * serializes competing writers is a property of the database, not of our code,
 * so a mock proves nothing — and this is the exact defect P1 exists to close.
 *
 *   npm run test:emulator
 *
 * Skips itself, loudly, when no emulator is reachable, so a green unit run can
 * never be mistaken for a passing concurrency check.
 */

const test = require("node:test");
const assert = require("node:assert");

// Fails rather than skips. A concurrency guarantee that quietly reports success
// because no database was running is worse than no test at all.
if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error(
    "No Firestore emulator. Run these with: npm run test:emulator"
  );
}

const admin = require("firebase-admin");
const {
  SlotTakenError,
  pendingWrites,
  commitBookingAtomically,
  slotConflictWindow,
} = require("../lib/reservation");

admin.initializeApp({ projectId: "safebeauty-test" });
const db = admin.firestore();

const SLOT_MINUTES = 60;
let seq = 0;
const freshSalon = () => `salon_${Date.now()}_${seq++}`;

/** Mirrors the production reader in createPaymentSession. */
function readerFor(salonId, appointmentDate) {
  const win = slotConflictWindow(appointmentDate);
  return async (tx) => {
    const q = db.collection("appointments")
      .where("salonId", "==", salonId)
      .where("appointmentDate", ">=", win.start)
      .where("appointmentDate", "<", win.end);
    const snap = await tx.get(q);
    return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  };
}

/** One booking attempt, shaped exactly as production builds it. */
function attempt(salonId, at, staffId = "") {
  const ref = db.collection("appointments").doc();
  const pending = pendingWrites();
  pending.set(ref, {
    salonId, staffId,
    appointmentDate: at,
    slotsCount: 1,
    status: "PENDING",
    createdAt: Date.now(),
  });
  return commitBookingAtomically(db, pending, readerFor(salonId, at), at, 1, staffId, SLOT_MINUTES)
    .then(() => ({ ok: true, id: ref.id }))
    .catch((e) => ({ ok: false, taken: e instanceof SlotTakenError, err: e }));
}

const countFor = async (salonId) =>
  (await db.collection("appointments").where("salonId", "==", salonId).get()).size;

test("two simultaneous bookings for one slot: exactly one wins", async () => {
  // Repeated, because a race that loses only occasionally still loses.
  for (let run = 0; run < 15; run++) {
    const salonId = freshSalon();
    const at = Date.now() + 86_400_000;

    const results = await Promise.all([attempt(salonId, at), attempt(salonId, at)]);
    const won = results.filter((r) => r.ok).length;

    assert.strictEqual(won, 1, `run ${run}: ${won} bookings succeeded, expected exactly 1`);
    assert.ok(
      results.filter((r) => !r.ok).every((r) => r.taken),
      `run ${run}: the loser failed for a reason other than SLOT_TAKEN`
    );
    assert.strictEqual(await countFor(salonId), 1, `run ${run}: wrong number of appointments written`);
  }
});

test("eight simultaneous bookings for one slot: still exactly one", async () => {
  for (let run = 0; run < 5; run++) {
    const salonId = freshSalon();
    const at = Date.now() + 172_800_000;

    const results = await Promise.all(
      Array.from({ length: 8 }, () => attempt(salonId, at))
    );
    const won = results.filter((r) => r.ok).length;

    assert.strictEqual(won, 1, `run ${run}: ${won} of 8 succeeded, expected exactly 1`);
    assert.strictEqual(await countFor(salonId), 1, `run ${run}: wrong number of appointments written`);
  }
});

test("different staff are different chairs and book in parallel", async () => {
  const salonId = freshSalon();
  const at = Date.now() + 259_200_000;

  const results = await Promise.all([
    attempt(salonId, at, "staff_a"),
    attempt(salonId, at, "staff_b"),
  ]);

  assert.strictEqual(results.filter((r) => r.ok).length, 2, "both chairs should have been bookable");
  assert.strictEqual(await countFor(salonId), 2);
});

test("adjacent slots do not collide", async () => {
  const salonId = freshSalon();
  const at = Date.now() + 345_600_000;

  const results = await Promise.all([
    attempt(salonId, at),
    attempt(salonId, at + SLOT_MINUTES * 60_000),
  ]);

  assert.strictEqual(results.filter((r) => r.ok).length, 2, "neighbouring hours should both book");
});

test("a cancelled booking releases its slot", async () => {
  const salonId = freshSalon();
  const at = Date.now() + 432_000_000;

  const first = await attempt(salonId, at);
  assert.ok(first.ok);

  const blocked = await attempt(salonId, at);
  assert.ok(blocked.taken, "the slot should be held while the booking is live");

  await db.doc(`appointments/${first.id}`).update({ status: "CANCELLED" });

  const after = await attempt(salonId, at);
  assert.ok(after.ok, "cancelling should free the slot again");
});

test("a booking far outside the window does not block", async () => {
  // Guards the bounded query: an appointment a week away must neither be read
  // nor treated as a conflict.
  const salonId = freshSalon();
  const at = Date.now() + 604_800_000;

  const far = await attempt(salonId, at - 7 * 86_400_000);
  assert.ok(far.ok);

  const near = await attempt(salonId, at);
  assert.ok(near.ok, "a booking a week earlier must not block this one");
});

test.after(async () => { await admin.app().delete(); });
