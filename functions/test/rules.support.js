/**
 * A closed support conversation can be rated once, by its owner, 1–5 — and
 * nothing else about it can be touched from a client.
 *
 * openedAt/closedAt decide which messages the apps show as that conversation,
 * so a client that could write them could re-file her messages; a rating she
 * could rewrite is one an admin cannot act on; and a history row a client could
 * create is a conversation that never happened.
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
const USER_AUTH = "user-auth", USER = "user-app";
const OTHER_AUTH = "other-auth", OTHER = "other-app";
const ADMIN_AUTH = "admin-auth", ADMIN = "admin-app";
const H = `support_tickets/${USER}/history/c_100`;

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "safebeauty-rules-support",
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
    await db.doc(`uid_map/${USER_AUTH}`).set({ appUid: USER });
    await db.doc(`uid_map/${OTHER_AUTH}`).set({ appUid: OTHER });
    await db.doc(`uid_map/${ADMIN_AUTH}`).set({ appUid: ADMIN });
    await db.doc(`users/${USER}`).set({ role: "CUSTOMER", status: "APPROVED" });
    await db.doc(`users/${OTHER}`).set({ role: "CUSTOMER", status: "APPROVED" });
    await db.doc(`users/${ADMIN}`).set({ role: "ADMIN", status: "APPROVED" });
    await db.doc(`support_tickets/${USER}`).set({ userId: USER, status: "CLOSED" });
    await db.doc(H).set({
      userId: USER, openedAt: 100, closedAt: 200, messageCount: 2, lastMessage: "ok",
      rating: 0, ratingComment: "", ratedAt: 0, backfilled: false,
    });
  });
});

test.after(async () => { await env.cleanup(); });

const as = (auth) => env.authenticatedContext(auth).firestore();
const rate = (stars, extra = {}) => ({ rating: stars, ratingComment: "", ratedAt: 300, ...extra });

test("owner reads her history; another user cannot; admin can", async () => {
  await assertSucceeds(as(USER_AUTH).doc(H).get());
  await assertFails(as(OTHER_AUTH).doc(H).get());
  await assertSucceeds(as(ADMIN_AUTH).doc(H).get());
});

test("owner rates 1–5 once, with a short comment", async () => {
  await assertSucceeds(as(USER_AUTH).doc(H).update(rate(5, { ratingComment: "quick and kind" })));
});

test("a rating cannot be changed after it is given", async () => {
  await assertSucceeds(as(USER_AUTH).doc(H).update(rate(2)));
  await assertFails(as(USER_AUTH).doc(H).update(rate(5)));
});

test("out-of-range and non-integer ratings are refused", async () => {
  await assertFails(as(USER_AUTH).doc(H).update(rate(0)));
  await assertFails(as(USER_AUTH).doc(H).update(rate(6)));
  await assertFails(as(USER_AUTH).doc(H).update(rate(4.5)));
  await assertFails(as(USER_AUTH).doc(H).update(rate("5")));
});

test("an overlong comment is refused", async () => {
  await assertFails(as(USER_AUTH).doc(H).update(rate(4, { ratingComment: "x".repeat(501) })));
});

test("rating cannot smuggle in a change to what the conversation covers", async () => {
  await assertFails(as(USER_AUTH).doc(H).update(rate(5, { closedAt: 999999 })));
  await assertFails(as(USER_AUTH).doc(H).update({ lastMessage: "rewritten" }));
});

test("another user cannot rate her conversation", async () => {
  await assertFails(as(OTHER_AUTH).doc(H).update(rate(1)));
});

test("no client creates or deletes a history row, admin included", async () => {
  await assertFails(as(USER_AUTH).doc(`support_tickets/${USER}/history/c_1`).set({ userId: USER, rating: 0 }));
  await assertFails(as(ADMIN_AUTH).doc(`support_tickets/${USER}/history/c_1`).set({ userId: USER, rating: 0 }));
  await assertFails(as(USER_AUTH).doc(H).delete());
  await assertFails(as(ADMIN_AUTH).doc(H).delete());
});
