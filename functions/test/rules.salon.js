/**
 * A salon cannot write its own derived fields.
 *
 * The discovery fields are derived from what the salon actually typed into
 * `services` and `district`. A salon that could write them directly would file
 * itself under every category and every neighbourhood, which is what filtering
 * exists to prevent.
 *
 * The newest of them is the flag saying a person should look at this salon —
 * raised when a service matches no category, or a district could be two places.
 * One that could clear its own would vanish from the admin's review queue while
 * staying unfindable, which is worse than never having flagged it: the queue
 * would then be evidence that everything is fine.
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
const PROV_AUTH = "prov-auth", PROV_APP = "prov-app", SALON = "salon-1";

test.before(async () => {
  env = await initializeTestEnvironment({
    // Its own project: node --test runs files in parallel and clearFirestore()
    // in one wipes another's fixtures mid-test.
    projectId: "safebeauty-rules-salon",
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
    await db.doc(`uid_map/${PROV_AUTH}`).set({ appUid: PROV_APP });
    await db.doc(`users/${PROV_APP}`).set({ role: "PROVIDER", status: "APPROVED" });
    await db.doc(`salons/${SALON}`).set({
      providerId: PROV_APP,
      salonName: "Test Salon",
      services: ["mo"],
      district: "somewhere",
      isVerified: false,
      categories: [],
      districtKey: "",
      needsDiscoveryReview: true,
      discoveryReview: { unmatchedServices: ["mo"], districtCandidates: [] },
    });
  });
});

test.after(async () => { if (env) await env.cleanup(); });

const asProvider = () => env.authenticatedContext(PROV_AUTH).firestore();

test("a salon may still edit what the derivation reads", async () => {
  // The whole design: services and district are the salon's to change, and the
  // trigger re-derives from them. Freezing those would be the wrong fix.
  await assertSucceeds(
    asProvider().doc(`salons/${SALON}`).update({ services: ["Nails"], district: "Karte Naw" })
  );
});

test("a salon cannot clear its own review flag", async () => {
  await assertFails(
    asProvider().doc(`salons/${SALON}`).update({ needsDiscoveryReview: false })
  );
});

test("a salon cannot rewrite the review detail", async () => {
  await assertFails(
    asProvider().doc(`salons/${SALON}`).update({
      discoveryReview: { unmatchedServices: [], districtCandidates: [] },
    })
  );
});

test("a salon cannot file itself under a category it did not earn", async () => {
  await assertFails(
    asProvider().doc(`salons/${SALON}`).update({ categories: ["Nails", "Hair", "Makeup"] })
  );
  await assertFails(
    asProvider().doc(`salons/${SALON}`).update({ districtKey: "D10" })
  );
});

test("a salon cannot award itself the verified badge", async () => {
  await assertFails(asProvider().doc(`salons/${SALON}`).update({ isVerified: true }));
});
