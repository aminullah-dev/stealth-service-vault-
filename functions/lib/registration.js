/**
 * The two decisions in registration that are pure, extracted so they can be
 * tested without an emulator.
 *
 * Both used to live on the device. firestore.rules could afford to be the only
 * check because the client was the only writer and every write went through the
 * rules — the fourteen conditions on the users create path ARE the spec. The
 * moment registerAccount writes that document with Admin credentials, the rules
 * stop being consulted at all, and every one of those conditions has to hold
 * because the code produces it rather than because something rejects it.
 *
 * So these functions exist to be asserted against, one test per condition the
 * rules used to enforce. Losing a check by moving the writer is not a
 * hypothetical: it is the specific way this kind of migration goes wrong.
 */

/**
 * The role a stranger may claim for themselves, or "" if they may not.
 *
 * ADMIN is the whole point. The rules refuse `role == 'ADMIN'` on create and
 * comment that "the first ADMIN is bootstrapped out-of-band"; a server-side
 * writer that accepted request.data.role verbatim would hand out the platform.
 * Returning "" rather than quietly substituting CUSTOMER keeps the attempt
 * visible to the caller, which decides how loudly to refuse.
 */
function selfRegisterRole(raw) {
  const role = String(raw == null ? "CUSTOMER" : raw).trim().toUpperCase();
  if (role === "CUSTOMER" || role === "PROVIDER") return role;
  return "";
}

/**
 * A provider is not approved by registering; a customer is.
 *
 * Separated from the role because they were separate conditions in the rules —
 * CUSTOMER had to be APPROVED and PROVIDER had to be PENDING, and a provider
 * who self-approved would be bookable before anyone checked her identity.
 */
function statusForRole(role) {
  return role === "PROVIDER" ? "PENDING" : "APPROVED";
}

/**
 * The users document a self-registration produces.
 *
 * Composed field by field from named arguments rather than spread from the
 * request, so a field the caller did not ask for cannot arrive. That is the
 * property the rules were enforcing one condition at a time — no seeded
 * referralCredit, no loyaltyPoints, no self-asserted kycStatus, no
 * pre-claimed profileReward, no derived lookup keys — and composing rather than
 * merging enforces all of them at once, including the ones nobody thought to
 * list.
 */
function buildRegistrationDocument(f) {
  const role       = selfRegisterRole(f.role);
  const isProvider = role === "PROVIDER";
  return {
    uid:           String(f.uid || ""),
    name:          String(f.name || ""),
    phone:         String(f.phone || ""),
    email:         String(f.email || ""),
    role,
    pinHash:       String(f.pinHash || ""),
    salt:          String(f.salt || ""),
    firebaseEmail: String(f.firebaseEmail || ""),
    status:        statusForRole(role),
    createdAt:     Number(f.createdAt) || 0,
    referralCode:  String(f.referralCode || ""),
    referredBy:    String(f.referredBy || ""),
    // Parked so a sign-in can finish a salon this registration could not.
    pendingSalonName:     isProvider ? String(f.salonName || "") : "",
    pendingSalonDistrict: isProvider ? String(f.district || "")  : "",
    pendingSalonServices: isProvider && Array.isArray(f.services)
      ? f.services.map(String)
      : [],
  };
}

/**
 * The invite code a referral may claim, or "" if it may not.
 *
 * You cannot refer yourself — the client checked this and the server has to
 * keep checking it, because the client is no longer the writer.
 */
function acceptedReferral(referredBy, ownCode) {
  const code = String(referredBy || "").trim().toUpperCase();
  if (!code) return "";
  return code === String(ownCode || "").toUpperCase() ? "" : code;
}

module.exports = {
  selfRegisterRole,
  statusForRole,
  buildRegistrationDocument,
  acceptedReferral,
};
