/**
 * Atomic slot reservation.
 *
 * The conflict check used to run once, well before the write and outside any
 * transaction, so two customers booking the same slot at the same moment both
 * read a clean slate and both succeeded. That is rare at two salons and routine
 * at five hundred, and double-booking is the one failure a booking marketplace
 * cannot absorb: two women arrive for the same chair, and the platform caused
 * it rather than the salon.
 *
 * Extracted here, taking `db` as a parameter, so the guarantee can be tested
 * against a real Firestore rather than argued for by reading the code. Whether
 * a transaction actually serializes concurrent writers is not something
 * inspection can establish.
 */

const { hasSlotConflict } = require("./slots");

/** Thrown when the requested slots are taken. Carries a stable machine reason. */
class SlotTakenError extends Error {
  constructor(message) {
    super(message || "That time slot is no longer available.");
    this.name = "SlotTakenError";
    this.reason = "SLOT_TAKEN";
  }
}

/**
 * Collects the writes a booking performs so they can be replayed inside a
 * transaction.
 *
 * A WriteBatch cannot serve here: once built it does not expose its operations,
 * so it can only be committed on its own — which is precisely the property that
 * stopped the conflict check being atomic with the write.
 *
 * The shape matches WriteBatch.set() so call sites read unchanged.
 */
function pendingWrites() {
  const ops = [];
  return {
    set(ref, data, options) { ops.push({ ref, data, options }); },
    get size() { return ops.length; },
    ops,
  };
}

/**
 * Commit a booking's writes only if its slots are still free.
 *
 * Firestore gives the read a serializable snapshot and aborts the commit if
 * anything the query covered changed in the meantime, retrying the callback —
 * so the check and the write cannot be separated by another booking.
 *
 * The writes replay from plain data, so a retry re-applies exactly the same
 * operations and timestamps captured before the transaction do not drift
 * between attempts.
 *
 * @param {FirebaseFirestore.Firestore} db
 * @param {{ops: Array}} pending          writes collected by pendingWrites()
 * @param {(tx) => Promise<Array>} readNearby  reads candidate appointments via the transaction
 * @throws {SlotTakenError} when the slots are taken
 */
async function commitBookingAtomically(db, pending, readNearby, appointmentDate, slotSpanOrOffsets, staffId, slotMinutes) {
  await db.runTransaction(async (tx) => {
    // Every read must precede every write in a Firestore transaction, which is
    // also the order correctness requires.
    const existing = await readNearby(tx);
    // Either a slot count or the explicit offsets the stylist is working — see
    // hasSlotConflict. A colour's development gap is not in either set, so the
    // stylist can be booked into it without this refusing.
    if (hasSlotConflict(existing, appointmentDate, slotSpanOrOffsets, staffId, slotMinutes)) {
      throw new SlotTakenError();
    }
    for (const op of pending.ops) {
      if (op.options) tx.set(op.ref, op.data, op.options);
      else tx.set(op.ref, op.data);
    }
  });
}

/**
 * How far either side of a requested time a conflicting appointment could start.
 *
 * A booking occupies consecutive slots, so an appointment starting well before
 * the requested time can still overlap it. A day either way is far wider than
 * any real booking span and keeps the read to one salon's immediate
 * neighbourhood rather than its entire history — which is what stops the cost
 * of booking with a salon rising forever as that salon succeeds.
 */
const SLOT_CONFLICT_WINDOW_MS = 24 * 60 * 60 * 1000;

function slotConflictWindow(appointmentDate) {
  const at = Number(appointmentDate) || 0;
  return { start: at - SLOT_CONFLICT_WINDOW_MS, end: at + SLOT_CONFLICT_WINDOW_MS };
}

module.exports = {
  SlotTakenError,
  SLOT_CONFLICT_WINDOW_MS,
  pendingWrites,
  commitBookingAtomically,
  slotConflictWindow,
};
