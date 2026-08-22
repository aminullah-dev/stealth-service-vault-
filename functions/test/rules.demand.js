/**
 * Security-rules tests for demand_signals.
 *
 * The privacy claim in firestore.rules is that these signals carry no identity
 * — "enforced rather than trusting callers to keep leaving it out". That is a
 * claim about behaviour, and behaviour has to be tested or it is just a comment
 * that was true on the day it was written.
 *
 *   npm run test:rules
 *
 * Fails loudly without an emulator rather than skipping: a privacy test that
 * silently passes because nothing ran it is worse than no test.
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
const CUSTOMER = "customer-uid";

const signal = (extra = {}) => ({
  kind: "NO_RESULTS",
  districtKey: "D9_Makroryan",
  category: "Nails",
  serviceName: "",
  salonId: "",
  lang: "dari",
  at: Date.now(),
  ...extra,
});

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "safebeauty-rules-test",
    firestore: {
      host,
      port: Number(port),
      rules: fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8"),
    },
  });
});

test.beforeEach(async () => { await env.clearFirestore(); });
test.after(async () => { if (env) await env.cleanup(); });

const asCustomer = () => env.authenticatedContext(CUSTOMER).firestore();
const asAnon = () => env.unauthenticatedContext().firestore();

test("a signed-in customer may record a signal", async () => {
  await assertSucceeds(asCustomer().collection("demand_signals").add(signal()));
});

test("a signed-out client may not", async () => {
  await assertFails(asAnon().collection("demand_signals").add(signal()));
});

test("attaching an identity is refused", async () => {
  // The whole privacy claim. hasOnly() must reject any key outside the list,
  // so a future client cannot quietly start attaching who searched for what.
  for (const identity of [
    { userId: CUSTOMER },
    { customerId: CUSTOMER },
    { uid: CUSTOMER },
    { phone: "+93700000000" },
    { email: "someone@example.com" },
    { deviceId: "abc123" },
  ]) {
    await assertFails(
      asCustomer().collection("demand_signals").add(signal(identity)),
      `a signal carrying ${Object.keys(identity)[0]} should have been refused`
    );
  }
});

test("an unknown kind is refused", async () => {
  await assertFails(asCustomer().collection("demand_signals").add(signal({ kind: "SOMETHING_ELSE" })));
});

test("oversized free text is refused", async () => {
  await assertFails(
    asCustomer().collection("demand_signals").add(signal({ serviceName: "x".repeat(200) }))
  );
  await assertFails(
    asCustomer().collection("demand_signals").add(signal({ districtKey: "x".repeat(100) }))
  );
});

test("a customer cannot read the demand log back", async () => {
  // A readable log reconstructs which neighbourhoods search for what.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("demand_signals").doc("seed").set(signal());
  });
  await assertFails(asCustomer().collection("demand_signals").doc("seed").get());
  await assertFails(asCustomer().collection("demand_signals").get());
});

test("signals are immutable and undeletable by clients", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("demand_signals").doc("seed").set(signal());
  });
  const db = asCustomer();
  await assertFails(db.collection("demand_signals").doc("seed").update({ category: "Hair" }));
  await assertFails(db.collection("demand_signals").doc("seed").delete());
});
