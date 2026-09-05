const test = require("node:test");
const assert = require("node:assert");
const {
  selfRegisterRole, statusForRole, buildRegistrationDocument, acceptedReferral,
} = require("../lib/registration");

/**
 * Registration moved from the device to registerAccount, and with it the users
 * document moved from a client write — which firestore.rules inspected, field
 * by field, on every attempt — to a server write with Admin credentials, which
 * the rules never see.
 *
 * That is the dangerous half of the migration. The fourteen conditions on the
 * users create path were the specification for what a self-registration may
 * contain, and they are now unenforced on this path. Each one below is a test
 * because it used to be a rule.
 */

test("a stranger may not register themselves as an admin", () => {
  // The rules refuse role == 'ADMIN' on create and note that the first admin is
  // bootstrapped out-of-band. registerAccount is now the writer that refusal
  // was protecting against.
  assert.strictEqual(selfRegisterRole("ADMIN"), "", "ADMIN must be refused outright");
  assert.strictEqual(selfRegisterRole("admin"), "", "and not by matching on case");
  assert.strictEqual(selfRegisterRole(" Admin "), "", "nor defeated by whitespace");
  assert.strictEqual(buildRegistrationDocument({ role: "ADMIN" }).role, "",
    "and the document must not carry it either");
});

test("only customer and provider are self-registerable", () => {
  assert.strictEqual(selfRegisterRole("CUSTOMER"), "CUSTOMER");
  assert.strictEqual(selfRegisterRole("PROVIDER"), "PROVIDER");
  assert.strictEqual(selfRegisterRole("provider"), "PROVIDER", "case is normalised, not rejected");
  // Absent means customer, and JSON says absent two ways — a client that omits
  // the key and one that sends an explicit null must not get different roles.
  assert.strictEqual(selfRegisterRole(undefined), "CUSTOMER", "omitted means customer");
  assert.strictEqual(selfRegisterRole(null), "CUSTOMER", "and so does an explicit null");
  // Anything else present but unrecognised is refused rather than defaulted:
  // defaulting would turn a typo — or a probe — into a silent success.
  for (const junk of ["", "SUPERUSER", "OWNER", 0, [], {}]) {
    assert.strictEqual(selfRegisterRole(junk), "", `refused: ${JSON.stringify(junk)}`);
  }
});

test("a provider starts pending and a customer starts approved", () => {
  // Separate rule conditions, and the provider half is the one that matters: a
  // provider who self-approved would be bookable before anyone checked who she
  // is.
  assert.strictEqual(statusForRole("PROVIDER"), "PENDING");
  assert.strictEqual(statusForRole("CUSTOMER"), "APPROVED");
  assert.strictEqual(buildRegistrationDocument({ role: "PROVIDER" }).status, "PENDING");
  assert.strictEqual(buildRegistrationDocument({ role: "CUSTOMER" }).status, "APPROVED");
});

test("a registration cannot seed itself money, reputation or verification", () => {
  // referralCredit, referralRewarded, loyaltyPoints, customerRatingSum,
  // customerRatingCount, noShowCount, kycStatus and profileRewardClaimed were
  // eight separate conditions in the rules. Composing the document rather than
  // spreading the request satisfies all eight, so the test is that the caller's
  // values do not survive at all.
  const doc = buildRegistrationDocument({
    uid: "u1", name: "Test", role: "CUSTOMER",
    referralCredit: 99999, referralRewarded: true, loyaltyPoints: 500,
    customerRatingSum: 50, customerRatingCount: 10, noShowCount: -5,
    kycStatus: "APPROVED", profileRewardClaimed: true,
    // and the two derived lookup keys, which only deriveUserPhoneKey may write
    phoneDigits: "700000000", nameKey: "test",
  });
  for (const field of [
    "referralCredit", "referralRewarded", "loyaltyPoints",
    "customerRatingSum", "customerRatingCount", "noShowCount",
    "kycStatus", "profileRewardClaimed", "phoneDigits", "nameKey",
  ]) {
    assert.ok(!(field in doc), `${field} must not appear in a self-registration`);
  }
});

test("the document carries exactly the fields registration is allowed to set", () => {
  // Pinned deliberately. A field added here later is a field the rules are no
  // longer checking, and this assertion is the only thing left that would
  // notice.
  const doc = buildRegistrationDocument({ uid: "u1", role: "PROVIDER" });
  assert.deepStrictEqual(Object.keys(doc).sort(), [
    "createdAt", "email", "firebaseEmail", "name",
    "pendingSalonDistrict", "pendingSalonName", "pendingSalonServices",
    "phone", "pinHash", "referralCode", "referredBy", "role", "salt",
    "status", "uid",
  ]);
});

test("salon details are parked only for providers", () => {
  const provider = buildRegistrationDocument({
    role: "PROVIDER", salonName: "Gul", district: "KBL_D9_Makroryan", services: ["Haircut"],
  });
  assert.strictEqual(provider.pendingSalonName, "Gul");
  assert.deepStrictEqual(provider.pendingSalonServices, ["Haircut"]);

  // A customer who sends salon fields must not have them stored: they would be
  // read later by the sign-in path that finishes an unfinished salon, and it
  // does not check the role before acting on them.
  const customer = buildRegistrationDocument({
    role: "CUSTOMER", salonName: "Gul", district: "KBL_D9_Makroryan", services: ["Haircut"],
  });
  assert.strictEqual(customer.pendingSalonName, "");
  assert.strictEqual(customer.pendingSalonDistrict, "");
  assert.deepStrictEqual(customer.pendingSalonServices, []);
});

test("you cannot refer yourself", () => {
  assert.strictEqual(acceptedReferral("SBABC123", "SBABC123"), "", "own code earns nothing");
  assert.strictEqual(acceptedReferral("sbabc123", "SBABC123"), "", "including in lower case");
  assert.strictEqual(acceptedReferral("SBOTHER", "SBABC123"), "SBOTHER", "someone else's is kept");
  assert.strictEqual(acceptedReferral("  sbother  ", "SBABC123"), "SBOTHER", "trimmed and upper-cased");
  assert.strictEqual(acceptedReferral("", "SBABC123"), "", "blank stays blank");
  assert.strictEqual(acceptedReferral(undefined, "SBABC123"), "", "so does missing");
});

test("every string field is a string, whatever the caller sent", () => {
  // The request is JSON from an unauthenticated caller. A number where a string
  // belongs reaches Firestore as a number and then fails an equality filter
  // that everything else in the codebase writes as text.
  const doc = buildRegistrationDocument({
    uid: 12345, name: 678, phone: 700123456, email: null,
    role: "CUSTOMER", pinHash: undefined, salt: {}, firebaseEmail: [],
    referralCode: 0, referredBy: false, createdAt: "not-a-number",
  });
  for (const [k, v] of Object.entries(doc)) {
    if (k === "createdAt") { assert.strictEqual(typeof v, "number", "createdAt is a number"); continue; }
    if (k === "pendingSalonServices") { assert.ok(Array.isArray(v), "services is an array"); continue; }
    assert.strictEqual(typeof v, "string", `${k} must be a string, got ${typeof v}`);
  }
  assert.strictEqual(doc.createdAt, 0, "an unparseable createdAt becomes 0, not NaN");
});
