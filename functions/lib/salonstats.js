/**
 * How one write to an appointment changes a salon's booking tally.
 *
 * Pure and dependency-free, because getting it wrong is not visible: the tally
 * feeds the provider's Income tab, and a counter that drifts produces a number
 * that looks like an answer. There is no screen anywhere that would show the
 * discrepancy — the appointments it was derived from are no longer read.
 *
 * The delta is expressed as plain integers so the caller can turn them into
 * Firestore increments, and so the arithmetic can be checked here instead of
 * against a database.
 */

/** Bookings in these states are what the revenue estimate is built from. */
const CONFIRMED_STATUSES = ["CONFIRMED", "COMPLETED"];

function countsAsConfirmed(status) {
  return CONFIRMED_STATUSES.includes(String(status || ""));
}

/**
 * The change [before] → [after] makes to a salon's tally.
 *
 * Either side may be null: null → doc is a create, doc → null is a delete, and
 * doc → doc is an update. A write that changes nothing the tally tracks returns
 * empty buckets, which is how the caller knows not to write at all.
 *
 * Buckets that cancel out are dropped rather than written as zero — a rename
 * that moved a booking from one service to another and back leaves nothing.
 */
function statsDelta(before, after) {
  const byStatus = {};
  const byService = {};
  const confirmedByService = {};
  const bump = (bag, key, n) => {
    // An unnamed service is not a bucket. Counting it under "" would create a
    // nameless row in the provider's per-service breakdown.
    if (!key) return;
    bag[key] = (bag[key] || 0) + n;
  };

  const side = (doc, sign) => {
    if (!doc) return;
    const status  = String(doc.status || "");
    const service = String(doc.serviceName || "");
    bump(byStatus, status, sign);
    bump(byService, service, sign);
    if (countsAsConfirmed(status)) bump(confirmedByService, service, sign);
  };
  side(before, -1);
  side(after, 1);

  const prune = (bag) => {
    const out = {};
    for (const [k, v] of Object.entries(bag)) if (v !== 0) out[k] = v;
    return out;
  };

  return {
    total: (after ? 1 : 0) - (before ? 1 : 0),
    byStatus: prune(byStatus),
    byService: prune(byService),
    confirmedByService: prune(confirmedByService),
  };
}

/** True when the delta would not change anything, so the write can be skipped. */
function isNoOp(delta) {
  return delta.total === 0 &&
    Object.keys(delta.byStatus).length === 0 &&
    Object.keys(delta.byService).length === 0 &&
    Object.keys(delta.confirmedByService).length === 0;
}

module.exports = { CONFIRMED_STATUSES, countsAsConfirmed, statsDelta, isNoOp };
