/**
 * Security-rules tests for the collections the admin console's Health tab reads.
 *
 * These exist because that tab was broken from the day it was written and nobody
 * knew. system_alerts and system_backups are written by Cloud Functions through
 * the Admin SDK, which bypasses rules entirely — so neither collection ever had
 * a rule, and every browser read of them was denied. The tab reads both before
 * rendering anything, so the whole page became "Missing or insufficient
 * permissions", taking the platform's own repair tools down with it.
 *
 * A page nobody can open looks exactly like a page nobody needs. That is what
 * made this survive: there was no failure to notice, only an absence.
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
const ADMIN_AUTH = "admin-auth-uid";
const ADMIN_APP  = "admin-app-uid";
const PLAIN_AUTH = "plain-auth-uid";
const PLAIN_APP  = "plain-app-uid";

test.before(async () => {
  env = await initializeTestEnvironment({
    // Its own project. node --test runs test files in parallel, and clearFirestore()
    // in one file wipes another's fixtures out from under it if they share one —
    // which failed exactly one test here, intermittently, and looked like a rules
    // bug. The emulator is happy to hold several projects at once.
    projectId: "safebeauty-rules-health",
    firestore: {
      host,
      port: Number(port),
      rules: fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8"),
    },
  });
});

test.beforeEach(async () => {
  await env.clearFirestore();
  // isAdmin() resolves through the uid_map bridge, so both halves have to exist
  // or an admin looks like a stranger — which is its own way of breaking a page.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`uid_map/${ADMIN_AUTH}`).set({ appUid: ADMIN_APP });
    await db.doc(`users/${ADMIN_APP}`).set({ role: "ADMIN", status: "APPROVED" });
    await db.doc(`uid_map/${PLAIN_AUTH}`).set({ appUid: PLAIN_APP });
    await db.doc(`users/${PLAIN_APP}`).set({ role: "CUSTOMER", status: "APPROVED" });
    await db.doc("system_alerts/2026-08-22").set({ ranAt: Date.now(), findings: [] });
    await db.doc("system_backups/2026-08-22T01-00").set({ startedAt: Date.now(), state: "DONE" });
  });
});

test.after(async () => { if (env) await env.cleanup(); });

const asAdmin    = () => env.authenticatedContext(ADMIN_AUTH).firestore();
const asCustomer = () => env.authenticatedContext(PLAIN_AUTH).firestore();
const asAnon     = () => env.unauthenticatedContext().firestore();

test("an admin can read the integrity sweep's findings", async () => {
  // Ordered and limited, exactly as the Health tab queries it — a rule can allow
  // get and still refuse list, and the tab only ever lists.
  await assertSucceeds(
    asAdmin().collection("system_alerts").orderBy("ranAt", "desc").limit(1).get()
  );
});

test("an admin can read the backup history", async () => {
  await assertSucceeds(
    asAdmin().collection("system_backups").orderBy("startedAt", "desc").limit(14).get()
  );
});

test("a customer cannot read either", async () => {
  // A backup listing says when and where the platform's data is written.
  await assertFails(asCustomer().collection("system_alerts").get());
  await assertFails(asCustomer().collection("system_backups").get());
});

test("a signed-out client cannot read either", async () => {
  await assertFails(asAnon().collection("system_alerts").get());
  await assertFails(asAnon().collection("system_backups").get());
});

test("nobody writes them from a client, not even an admin", async () => {
  // Both are the server's own record of what it did. An admin who could edit them
  // could hide a failed backup from the person the alert exists to warn.
  await assertFails(asAdmin().doc("system_alerts/2026-08-22").set({ findings: [] }));
  await assertFails(asAdmin().doc("system_backups/forged").set({ state: "DONE" }));
  await assertFails(asCustomer().doc("system_backups/forged").set({ state: "DONE" }));
});
