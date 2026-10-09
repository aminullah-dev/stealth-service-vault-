"use strict";

// Whether a salon owner has passed identity verification.
//
// The privacy policy and the terms both say it plainly: a salon owner verifies
// her identity "before their salon goes live". The Android app enforced that by
// routing every provider whose kycStatus is not APPROVED to the KYC screen at
// sign-in (AppNavGraph.kt). Nothing else did. The iOS app and the web salon
// console let a provider whose ACCOUNT was approved — `status: APPROVED`, which
// an admin sets from the Approvals tab without looking at any document — open
// her salon, take bookings and confirm them, and the server agreed with
// whichever client she happened to use. So the rule lived in one client.
//
// `status` and `kycStatus` are two different approvals and stay two different
// fields: `status` is "the admin has accepted this salon application" and
// `kycStatus` is "the admin has seen her tazkira and her face". A provider needs
// both to operate. Customers and admins are not providers and are not gated
// here — a customer's KYC is enforced at booking (createPaymentSession), and an
// admin acts on the platform's behalf.

/** kycStatus as stored, defaulting the way every client does. */
function kycStatusOf(user) {
  return String((user && user.kycStatus) || "NONE");
}

/**
 * True unless `user` is a PROVIDER whose identity is not verified.
 *
 * Deliberately says nothing about `status` or suspension — those have their own
 * checks (isApproved() in the rules, assertNotSuspended here) and folding them
 * in would make one refusal impossible to tell apart from another.
 */
function providerKycSatisfied(user) {
  if (!user || user.role !== "PROVIDER") return true;
  return kycStatusOf(user) === "APPROVED";
}

/**
 * Whether a customer may book at a salon owned by `provider`.
 *
 * `provider` is the salon's owner document, or null when there is none to read.
 * A missing owner is not this function's question: requestAccountDeletion
 * already clears isAvailable when an owner leaves, and the availability check
 * runs first. Refusing here on a missing document would turn a legacy salon
 * written under an older uid scheme into a hard failure for no safety gain.
 */
function salonOwnerMayTakeBookings(provider) {
  if (!provider) return true;
  return providerKycSatisfied(provider);
}

module.exports = { kycStatusOf, providerKycSatisfied, salonOwnerMayTakeBookings };
