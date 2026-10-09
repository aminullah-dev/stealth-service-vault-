"use strict";

// A salon owner must pass identity verification before her salon operates.
//
// Android held to this on its own, at sign-in. iOS and the web salon console did
// not, and neither did the server, so the rule depended on which client a
// provider opened. These tests pin the server half: the pure decision in
// lib/kyc.js, and that the two callables which make a salon operate actually
// consult it. The rules half is in rules.kyc.js.

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { kycStatusOf, providerKycSatisfied, salonOwnerMayTakeBookings } = require("../lib/kyc");

test("a verified provider may operate", () => {
  assert.strictEqual(providerKycSatisfied({ role: "PROVIDER", kycStatus: "APPROVED" }), true);
});

test("every other provider state is refused", () => {
  for (const kycStatus of ["NONE", "PENDING", "REJECTED", "", undefined, "approved"]) {
    assert.strictEqual(
      providerKycSatisfied({ role: "PROVIDER", status: "APPROVED", kycStatus }),
      false,
      `kycStatus ${JSON.stringify(kycStatus)} let a provider operate`);
  }
});

test("an approved ACCOUNT is not an approved IDENTITY", () => {
  // The exact shape the Approvals tab and adminCreateSalon produce: status
  // APPROVED, kycStatus NONE. This is the provider the gap let through.
  assert.strictEqual(
    providerKycSatisfied({ role: "PROVIDER", status: "APPROVED", kycStatus: "NONE" }), false);
});

test("customers and admins are not gated here", () => {
  // A customer's KYC is checked at booking; an admin acts for the platform.
  assert.strictEqual(providerKycSatisfied({ role: "CUSTOMER", kycStatus: "NONE" }), true);
  assert.strictEqual(providerKycSatisfied({ role: "ADMIN" }), true);
  assert.strictEqual(providerKycSatisfied(null), true);
});

test("kycStatus defaults the way every client defaults it", () => {
  assert.strictEqual(kycStatusOf({}), "NONE");
  assert.strictEqual(kycStatusOf(null), "NONE");
  assert.strictEqual(kycStatusOf({ kycStatus: "PENDING" }), "PENDING");
});

test("a salon whose owner is unverified takes no bookings", () => {
  assert.strictEqual(
    salonOwnerMayTakeBookings({ role: "PROVIDER", status: "APPROVED", kycStatus: "PENDING" }), false);
  assert.strictEqual(
    salonOwnerMayTakeBookings({ role: "PROVIDER", status: "APPROVED", kycStatus: "APPROVED" }), true);
});

test("a salon with no owner document is left to the availability check", () => {
  assert.strictEqual(salonOwnerMayTakeBookings(null), true);
});

// ── The callables actually consult it ────────────────────────────────────────

const DOMAINS = path.join(__dirname, "..", "domains");

function callableBody(file, name) {
  const src = fs.readFileSync(path.join(DOMAINS, file), "utf8");
  const from = src.indexOf(`exports.${name} = onCall(`);
  assert.ok(from >= 0, `${name} not found in ${file}`);
  const next = src.indexOf("\nexports.", from + 10);
  return src.slice(from, next > 0 ? next : src.length);
}

test("confirmAppointment refuses an unverified provider before touching the booking", () => {
  const body = callableBody("bookings.js", "confirmAppointment");
  const gate = body.indexOf("assertProviderVerified(appUser)");
  assert.ok(gate > 0, "confirmAppointment no longer checks the provider's KYC");
  assert.ok(gate < body.indexOf("runTransaction"),
    "the KYC check must run before the transaction that confirms the booking");
});

test("createPaymentSession refuses a salon whose owner is unverified, before any write", () => {
  const body = callableBody("payments.js", "createPaymentSession");
  const gate = body.indexOf("salonOwnerMayTakeBookings(");
  assert.ok(gate > 0, "createPaymentSession no longer checks the salon owner's KYC");
  // Before the idempotency claim's first write or the booking itself — a refusal
  // after money or a slot has been taken is not a refusal.
  for (const marker of ["commitBookingAtomically(", "recordClaim("]) {
    const at = body.indexOf(marker);
    if (at > 0) assert.ok(gate < at, `the owner check must run before ${marker}`);
  }
});

test("declining stays open to an unverified provider", () => {
  // Declining refunds the customer. Refusing it would strand her money behind
  // the salon owner's paperwork.
  const body = callableBody("bookings.js", "providerDeclineAppointment");
  assert.doesNotMatch(body, /assertProviderVerified\(/);
});
