/**
 * A client cannot claim an identity that is not its own.
 *
 * resolveAppUser decides who every callable is talking to with
 *
 *     users.where("firebaseEmail", "==", token.email).limit(1)
 *
 * so that field IS the server's notion of who you are. It was unconstrained on
 * create and unfrozen on update, and the document id is chosen by the client —
 * which made planting a document enough to capture somebody else's resolution.
 * A limit(1) with no orderBy breaks ties by ascending __name__, so a low id wins,
 * deterministically and for good.
 *
 * Not a takeover: the password still derives from the victim's own salt, and role
 * and status stay frozen, so there is no way in and no privilege gained. What it
 * buys is a permanent, silent lockout — and unlike the phone-planting variant,
 * which deriveUserPhoneKey alerts on, nothing anywhere would have noticed.
 *
 * UPDATED 2026-09-06. Commit 4975d16 closed the create rule outright — it is
 * now `allow create: if false`, because the client-chosen document id opened
 * four other holes besides this one and registration had already moved to the
 * `registerAccount` callable (Admin SDK, so rules do not apply). These tests
 * still asserted the old front door and had been failing since that commit;
 * the CI job that runs them is "Security rules compile", which was red for it.
 * The create-side tests below now assert the closure, and the update-side ones
 * seed the document the way registerAccount does instead of writing it as the
 * client, which no client may do any more.
 *
 *   npm run test:rules
 */

const test = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("No Firestore emulator. Run these with: npm run test:rules");
}
const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(":");

let env;
const MINE   = "mine@sb.app";
const HERS   = "hers@sb.app";
const MY_AUTH = "my-auth-uid";
const HER_APP = "her-app-uid";

/** A registration payload that satisfies every other clause of the create rule. */
const signup = (firebaseEmail) => ({
  name: "A Customer",
  phone: "+93700111222",
  role: "CUSTOMER",
  status: "APPROVED",
  firebaseEmail,
  referralCredit: 0,
  referralRewarded: false,
  loyaltyPoints: 0,
  customerRatingSum: 0,
  customerRatingCount: 0,
  noShowCount: 0,
  kycStatus: "NONE",
  profileRewardClaimed: false,
  createdAt: 1780000000000,
});

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "safebeauty-rules-identity",
    firestore: {
      host,
      port: Number(port),
      rules: fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8"),
    },
  });
});

test.beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    // The victim: an ordinary account the attacker wants to impersonate.
    await db.doc(`users/${HER_APP}`).set(signup(HERS));
    // The bridge me() resolves through. The update rule needs it; the create
    // rule deliberately cannot use it, because at registration it does not
    // exist yet — which is why create has to constrain the token email instead.
    await db.doc(`uid_map/${MY_AUTH}`).set({ appUid: "my-app-uid" });
  });
});

test.after(async () => { if (env) await env.cleanup(); });

const asMe = () => env.authenticatedContext(MY_AUTH, { email: MINE }).firestore();

/**
 * What registerAccount does: writes users/{uid} with the Admin SDK, which is
 * not subject to rules. Every update test needs the document to exist, and
 * since 4975d16 the client cannot be the one to create it.
 */
const seedMine = () => env.withSecurityRulesDisabled(
  (ctx) => ctx.firestore().doc("users/my-app-uid").set(signup(MINE))
);

test("no client may create a users document — not even its own", async () => {
  // This test used to assert the opposite, because registration used to be a
  // client write. It is not: RegisterViewModel.kt:142 and AuthService.swift:400
  // both call the registerAccount callable, which writes users/{uid} with the
  // Admin SDK. Leaving any client create path open meant a client-chosen
  // document id, and that id was the primitive behind a suspension escape, a
  // permanent lockout of a named woman, referral-credit theft and stored XSS in
  // the admin console — see 4975d16. Correct payload, own email, own uid, still
  // refused.
  await assertFails(asMe().doc("users/my-app-uid").set(signup(MINE)));
});

// These two now pass because create is closed to everyone rather than because
// firebaseEmail is constrained. Kept deliberately: they are the tests that fail
// first if anyone ever reopens create with a narrower condition, which is the
// obvious-looking change somebody will eventually propose.
test("a client cannot plant a document claiming somebody else's identity", async () => {
  await assertFails(asMe().doc("users/attacker-doc").set(signup(HERS)));
});

test("a low document id does not help", async () => {
  // The id is client-chosen and limit(1) breaks ties by ascending __name__, so
  // this is the shape that would have won the race.
  await assertFails(asMe().doc("users/0").set(signup(HERS)));
  await assertFails(
    asMe().doc("users/00000000-0000-0000-0000-000000000000").set(signup(HERS))
  );
});

test("an account cannot change its own identity afterwards", async () => {
  await seedMine();
  await assertFails(asMe().doc("users/my-app-uid").update({ firebaseEmail: HERS }));
});

test("an account cannot rewrite somebody else's identity", async () => {
  await assertFails(asMe().doc(`users/${HER_APP}`).update({ firebaseEmail: MINE }));
});

test("an account may still edit the things that are genuinely its own", async () => {
  // The guard against fixing the hole by breaking the front door. Closing
  // create must not also close the profile edit every customer uses.
  await seedMine();
  await assertSucceeds(asMe().doc("users/my-app-uid").update({ name: "A New Name" }));
});
