"use strict";

// Idempotency for the one callable where a duplicate costs real money.
//
// createPaymentSession creates an appointment and a payment row and opens a
// HesabPay checkout. It was not idempotent, so a call that timed out on a bad
// connection could not be retried: the client had no way to know whether the
// server had already done the work, and retrying could produce two bookings
// and two charges. The app therefore did not retry at all, and a customer on a
// Kabul connection simply lost the booking.
//
// The claim is a document created with `.create()`, which fails if it already
// exists — the same primitive reserveBookingCode uses, and the reason this is
// race-safe rather than a read-then-write.

const IN_FLIGHT_STALE_MS = 90 * 1000;

/** Shape a caller-supplied id into something safe to use as a document id. */
function claimPath(uid, clientRequestId) {
  return `booking_claims/${uid}_${clientRequestId}`;
}

/**
 * Decides what to do about a repeated request.
 *
 * Pure so the three outcomes can be tested without a database:
 *   proceed  — nothing has claimed this id, or the previous attempt died
 *   replay   — the work is done; hand back exactly what it returned
 *   inFlight — another copy of this request is still running
 */
function claimDecision(existing, now) {
  if (!existing) return { action: "proceed" };
  if (existing.result) return { action: "replay", result: existing.result };
  const startedAt = Number(existing.createdAt || 0);
  // A crash between creating the claim and writing the result would otherwise
  // lock this request id out forever, and the customer's retry with it too.
  if (now - startedAt > IN_FLIGHT_STALE_MS) return { action: "proceed" };
  return { action: "inFlight" };
}

module.exports = { claimPath, claimDecision, IN_FLIGHT_STALE_MS };
