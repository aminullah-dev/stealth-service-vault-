/**
 * A salon owner whose identity is not verified cannot operate her salon.
 *
 * The privacy policy promises identity verification for salon owners "before
 * their salon goes live". Android enforced that at sign-in and nothing else
 * did: the rules checked `status` (the salon application, approved from the
 * admin's Approvals tab without any document) and never `kycStatus` (the
 * tazkira and selfie). So from the web console or iOS a provider nobody had
 * verified could switch her salon to available, post offers and publish to the
 * feed. These tests pin the closure, and that a verified owner — every salon
 * Android already lets through — keeps every write she had.
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
const AUTH = "prov-auth", APP = "prov-app", SALON = "salon-1";
const CUST_AUTH = "cust-auth", CUST_APP = "cust-app";

test.before(async () => {
  env = await initializeTestEnvironment({
    // Its own project: node --test runs files in parallel and clearFirestore()
    // in one wipes another's fixtures mid-test.
    projectId: "safebeauty-rules-kyc",
    firestore: {
      host,
      port: Number(port),
      rules: fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8"),
    },
  });
});

test.after(async () => { if (env) await env.cleanup(); });

/** Seed one provider in the given KYC state, with an approved account. */
async function seed(kycStatus) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`uid_map/${AUTH}`).set({ appUid: APP });
    const user = { role: "PROVIDER", status: "APPROVED", name: "Owner" };
    if (kycStatus !== undefined) user.kycStatus = kycStatus;
    await db.doc(`users/${APP}`).set(user);
    await db.doc(`salons/${SALON}`).set({
      providerId: APP,
      salonName: "Test Salon",
      services: ["Nails"],
      district: "Karte Naw",
      isAvailable: false,
      isVerified: false,
    });
    await db.doc(`uid_map/${CUST_AUTH}`).set({ appUid: CUST_APP });
    await db.doc(`users/${CUST_APP}`).set({ role: "CUSTOMER", status: "APPROVED", kycStatus: "NONE" });
  });
}

const asProvider = () => env.authenticatedContext(AUTH).firestore();

const offer   = { salonId: SALON, title: "20% off", isActive: true };
const gallery = (id) => ({ salonId: SALON, storagePath: `salon_gallery/${SALON}/${id}.jpg` });
const post    = (id) => ({ salonId: SALON, storagePath: `salon_posts/${SALON}/${id}.jpg` });
const story   = (id) => ({ salonId: SALON, storagePath: `salon_stories/${SALON}/${id}.jpg` });

// Every state except APPROVED, including a document that predates the field.
for (const kyc of ["NONE", "PENDING", "REJECTED", undefined]) {
  const label = kyc === undefined ? "no kycStatus at all" : kyc;

  test(`unverified (${label}): cannot put her salon live`, async () => {
    await seed(kyc);
    await assertFails(asProvider().doc(`salons/${SALON}`).update({ isAvailable: true }));
  });

  test(`unverified (${label}): cannot edit the salon at all`, async () => {
    await seed(kyc);
    await assertFails(asProvider().doc(`salons/${SALON}`).update({ salonName: "Renamed" }));
  });

  test(`unverified (${label}): cannot post an offer, a photo, a feed post or a story`, async () => {
    await seed(kyc);
    const db = asProvider();
    await assertFails(db.doc("salon_offers/o1").set(offer));
    await assertFails(db.doc("salon_gallery/g1").set(gallery("g1")));
    await assertFails(db.doc("salon_posts/p1").set(post("p1")));
    await assertFails(db.doc("salon_stories/s1").set(story("s1")));
  });

  test(`unverified (${label}): can still read her own document and edit her name`, async () => {
    // The KYC screen reads her own document to show the review state and the
    // rejection reason. Locking her out of it would make the screen blank.
    await seed(kyc);
    await assertSucceeds(asProvider().doc(`users/${APP}`).get());
    await assertSucceeds(asProvider().doc(`users/${APP}`).update({ name: "New name" }));
  });

  test(`unverified (${label}): cannot verify herself`, async () => {
    await seed(kyc);
    await assertFails(asProvider().doc(`users/${APP}`).update({ kycStatus: "APPROVED" }));
  });
}

test("verified: every write an operating salon needs still goes through", async () => {
  // The regression to fear: every salon Android already lets operate must keep
  // working exactly as before.
  await seed("APPROVED");
  const db = asProvider();
  await assertSucceeds(db.doc(`salons/${SALON}`).update({ isAvailable: true }));
  await assertSucceeds(db.doc(`salons/${SALON}`).update({ salonName: "Renamed" }));
  await assertSucceeds(db.doc("salon_offers/o1").set(offer));
  await assertSucceeds(db.doc("salon_offers/o1").update({ isActive: false }));
  await assertSucceeds(db.doc("salon_gallery/g1").set(gallery("g1")));
  await assertSucceeds(db.doc("salon_posts/p1").set(post("p1")));
  await assertSucceeds(db.doc("salon_stories/s1").set(story("s1")));
});

test("verified but suspended: still refused, as before", async () => {
  await seed("APPROVED");
  await env.withSecurityRulesDisabled((ctx) =>
    ctx.firestore().doc(`users/${APP}`).update({ suspended: true }));
  await assertFails(asProvider().doc(`salons/${SALON}`).update({ isAvailable: true }));
});

test("verified but account still pending: still refused, as before", async () => {
  await seed("APPROVED");
  await env.withSecurityRulesDisabled((ctx) =>
    ctx.firestore().doc(`users/${APP}`).update({ status: "PENDING" }));
  await assertFails(asProvider().doc(`salons/${SALON}`).update({ isAvailable: true }));
});

test("a customer is untouched by the provider gate", async () => {
  // isApproved() itself did not change — the new check is a separate helper
  // that only the provider-side writes call.
  await seed("APPROVED");
  await assertSucceeds(
    env.authenticatedContext(CUST_AUTH).firestore().doc(`users/${CUST_APP}`).get());
});
