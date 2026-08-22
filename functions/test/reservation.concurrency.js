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
const { hasSlotConflict, serviceLayout } = require("../lib/slots");

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

/**
 * The conflict check rescheduleAppointment runs, built the same way.
 *
 * Reschedule is a second door onto the same guarantee, and it was missed when
 * the first was fixed: it read every appointment the salon had ever taken, with
 * no date bound, inside a transaction. Nothing tested it, which is also how a
 * `actor is not defined` in the same function survived until lint found it.
 *
 * This mirrors the production read and the production call to hasSlotConflict —
 * including the appointmentId that excludes the booking being moved, without
 * which every reschedule collides with itself.
 */
async function rescheduleWouldCollide(salonId, appointmentId, newDate, staffId = "", span = 1) {
  const win = slotConflictWindow(newDate);
  const snap = await db.collection("appointments")
    .where("salonId", "==", salonId)
    .where("appointmentDate", ">=", win.start)
    .where("appointmentDate", "<", win.end)
    .get();
  const others = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  return hasSlotConflict(others, newDate, span, staffId, SLOT_MINUTES, appointmentId);
}

test("a reschedule onto an occupied slot is refused", async () => {
  // One base timestamp, not two calls to Date.now(). hasSlotConflict matches
  // slot starts exactly, so a few milliseconds of drift between setting the
  // booking up and asking about it is a different slot.
  const salonId = freshSalon();
  const base = Date.now() + 86_400_000;
  const onto = base + 2 * 3_600_000;

  const mine  = await attempt(salonId, base);
  const other = await attempt(salonId, onto);
  assert.ok(mine.ok && other.ok);

  assert.equal(await rescheduleWouldCollide(salonId, mine.id, onto), true);
});

test("a reschedule onto a free slot is allowed", async () => {
  const salonId = freshSalon();
  const base = Date.now() + 86_400_000;
  const mine = await attempt(salonId, base);
  assert.ok(mine.ok);

  assert.equal(await rescheduleWouldCollide(salonId, mine.id, base + 5 * 3_600_000), false);
});

test("a reschedule does not collide with the booking being moved", async () => {
  // Without the appointmentId exclusion this is always true, and rescheduling
  // is impossible — the booking's own current slot blocks it.
  const salonId = freshSalon();
  const at = Date.now() + 86_400_000;
  const mine = await attempt(salonId, at);
  assert.ok(mine.ok);

  assert.equal(await rescheduleWouldCollide(salonId, mine.id, at), false,
    "a booking must not be its own conflict");
  assert.equal(await rescheduleWouldCollide(salonId, "someone-else", at), true,
    "and the same slot must still block anyone else");
});

test("a booking a week away does not block a reschedule", async () => {
  // The bound this test exists for: outside the window the appointment is not
  // read at all, so it cannot be mistaken for a conflict.
  const salonId = freshSalon();
  const onto = Date.now() + 86_400_000;
  const far  = await attempt(salonId, onto + 7 * 86_400_000);
  assert.ok(far.ok);

  assert.equal(await rescheduleWouldCollide(salonId, "any", onto), false);
});

/**
 * A booking with processing time, committed through the real reservation path.
 *
 * The pure maths is covered in slots.test.js. What these check is that the gap
 * survives the whole way through commitBookingAtomically and a live Firestore
 * transaction — because a colour that frees its development gap in one place and
 * not the other is either a stylist double-booked or an hour nobody can sell.
 */
const COLOUR = { activeBefore: 45, processing: 30, activeAfter: 20 };

function attemptWithLayout(salonId, at, layout, staffId = "", slotMinutes = SLOT_MINUTES) {
  const ref = db.collection("appointments").doc();
  const pending = pendingWrites();
  pending.set(ref, {
    salonId, staffId,
    appointmentDate: at,
    slotsCount: layout.span,
    busyOffsets: layout.busyOffsets,
    status: "PENDING",
    createdAt: Date.now(),
  });
  return commitBookingAtomically(db, pending, readerFor(salonId, at), at,
                                 layout.busyOffsets, staffId, slotMinutes)
    .then(() => ({ ok: true, id: ref.id }))
    .catch((e) => ({ ok: false, taken: e instanceof SlotTakenError, err: e }));
}

test("a blow-dry books into a colour's development gap, for real", async () => {
  // A 30-minute grid, because that is the coarsest one on which a 30-minute gap
  // exists at all. At 60 minutes it rounds away and this would quietly become a
  // test that a booking fits AFTER a colour, which proves nothing.
  const FINE = 30;
  const salonId = freshSalon();
  const base = Date.now() + 86_400_000;
  const colour = serviceLayout(["Colour"], { Colour: COLOUR }, {}, FINE);
  assert.deepEqual(colour.busyOffsets, [0, 1, 3], "slot 2 is the gap");

  const first = await attemptWithLayout(salonId, base, colour, "zahra", FINE);
  assert.ok(first.ok, "the colour itself must book");

  const inGap = base + 2 * FINE * 60_000;
  const second = await attemptWithLayout(salonId, inGap,
                                         { span: 1, busyOffsets: [0] }, "zahra", FINE);
  assert.ok(second.ok, "the stylist is free while the colour develops");

  // And the washout is still hers: two slots from the gap must be refused.
  const third = await attemptWithLayout(salonId, inGap,
                                        { span: 2, busyOffsets: [0, 1] }, "sara2", FINE);
  assert.ok(third.ok, "a different stylist was never blocked");
  const fourth = await attemptWithLayout(salonId, inGap,
                                         { span: 2, busyOffsets: [0, 1] }, "zahra", FINE);
  assert.equal(fourth.ok, false, "running out of the gap hits the wash and style");
  assert.ok(fourth.taken);
});

test("a booking cannot start on a colour's working slot", async () => {
  const salonId = freshSalon();
  const base = Date.now() + 86_400_000;
  const colour = serviceLayout(["Colour"], { Colour: COLOUR }, {}, SLOT_MINUTES);

  assert.ok((await attemptWithLayout(salonId, base, colour, "zahra")).ok);
  const clash = await attemptWithLayout(salonId, base, { span: 1, busyOffsets: [0] }, "zahra");
  assert.equal(clash.ok, false);
  assert.ok(clash.taken, "the application is working time and must be refused");
});

test("two colours for one stylist at the same time: exactly one wins", async () => {
  // The gap must not become a hole the concurrency guarantee falls through.
  const salonId = freshSalon();
  const base = Date.now() + 86_400_000;
  const colour = serviceLayout(["Colour"], { Colour: COLOUR }, {}, SLOT_MINUTES);

  const results = await Promise.all([
    attemptWithLayout(salonId, base, colour, "zahra"),
    attemptWithLayout(salonId, base, colour, "zahra"),
  ]);
  assert.equal(results.filter((r) => r.ok).length, 1,
    "processing time must not weaken the one-winner guarantee");
});

test.after(async () => { await admin.app().delete(); });
