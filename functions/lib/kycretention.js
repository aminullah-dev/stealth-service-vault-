"use strict";

// How long a photograph of somebody's identity document is kept.
//
// This app asks a woman in Afghanistan to photograph her tazkira and her own
// face so a stranger can confirm she is real. Those two images are the most
// dangerous thing the system holds — they are the reason every safety rule in
// this product exists — and once an admin has looked at them they serve no
// further purpose. What opens a booking is `kycStatus: "APPROVED"` on her user
// document, not the picture.
//
// So the picture is deleted and the fact is kept. Nothing about her
// verification changes; there is simply less to lose.

/** After this long, a reviewed account's photographs go. */
const RETENTION_DAYS = 30;
const RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000;

/**
 * Whether this account's KYC images should be deleted now.
 *
 * `reviewedAt` is when an admin decided. Missing for accounts reviewed before
 * this existed — the caller supplies the storage object's creation time as a
 * floor, which is when the photograph was uploaded and therefore no later than
 * when it was reviewed.
 */
function shouldPurge({ kycStatus, reviewedAt, now, hasImages }) {
  if (!hasImages) return { purge: false, why: "no-images" };

  // Still waiting on a human. Deleting these deletes her application.
  if (kycStatus === "PENDING") return { purge: false, why: "pending" };

  // Never submitted, or submitted and withdrawn. Nothing to keep, but nothing
  // to time either — a stray object here is handled by the same rule as a
  // reviewed one once its age is known.
  if (kycStatus !== "APPROVED" && kycStatus !== "REJECTED") {
    return { purge: false, why: "not-reviewed" };
  }

  if (!Number.isFinite(reviewedAt) || reviewedAt <= 0) {
    return { purge: false, why: "no-timestamp" };
  }

  if (now - reviewedAt < RETENTION_MS) return { purge: false, why: "within-window" };

  // A rejected account keeps nothing either. She can submit again, which
  // uploads again — holding the rejected photograph in the meantime protects
  // nobody.
  return { purge: true, why: kycStatus === "APPROVED" ? "approved-expired" : "rejected-expired" };
}

module.exports = { RETENTION_DAYS, RETENTION_MS, shouldPurge };
