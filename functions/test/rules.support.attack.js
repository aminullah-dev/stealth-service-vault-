/**
 * Attacking the support-rating rules rather than confirming them.
 *
 * rules.support.js asserts the intended paths work and the obvious abuses
 * fail. This file assumes that pass was luck and goes looking for the ways
 * around it: reaching another user's conversation sideways, rating something
 * that was never closed, writing the fields that decide which messages a
 * conversation contains, and getting a second rating in by deleting or
 * re-creating the row.
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
const A_AUTH = "a-auth", A = "user-a";
const B_AUTH = "b-auth", B = "user-b";
const H_A = `support_tickets/${A}/history/c_100`;

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "safebeauty-rules-support-attack",
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
    for (const [auth, uid] of [[A_AUTH, A], [B_AUTH, B]]) {
      await db.doc(`uid_map/${auth}`).set({ appUid: uid });
      await db.doc(`users/${uid}`).set({ role: "CUSTOMER", status: "APPROVED" });
      await db.doc(`support_tickets/${uid}`).set({ userId: uid, status: "CLOSED" });
      await db.doc(`support_tickets/${uid}/history/c_100`).set({
        userId: uid, openedAt: 100, closedAt: 200, messageCount: 2,
        lastMessage: "ok", rating: 0, ratingComment: "", ratedAt: 0, backfilled: false,
      });
    }
  });
});

test.after(async () => { await env.cleanup(); });

const as = (auth) => env.authenticatedContext(auth).firestore();
const rate = (n, extra = {}) => ({ rating: n, ratingComment: "", ratedAt: 300, ...extra });

test("a collection-group query cannot sweep up everyone's ratings", async () => {
  // The rule is written under support_tickets/{ticketId}/history/{id}. A
  // collectionGroup("history") query is a different path and must not be
  // served — otherwise one user reads every user's support history.
  const { collectionGroup, getDocs, query, where } =
    require("firebase/firestore");
  const db = as(A_AUTH);
  await assertFails(getDocs(collectionGroup(db, "history")));
  await assertFails(getDocs(query(collectionGroup(db, "history"), where("userId", "==", A))));
});

test("an unauthenticated client gets nothing", async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(db.doc(H_A).get());
  await assertFails(db.doc(H_A).update(rate(5)));
});

test("a signed-in user with no uid_map bridge is nobody", async () => {
  // me() resolves through uid_map. An auth account that never signed in
  // properly must not inherit somebody's ticket by id collision.
  const db = env.authenticatedContext("ghost-auth").firestore();
  await assertFails(db.doc(H_A).get());
  await assertFails(db.doc(H_A).update(rate(5)));
});

test("B cannot rate A's conversation by any route", async () => {
  await assertFails(as(B_AUTH).doc(H_A).update(rate(5)));
  await assertFails(as(B_AUTH).doc(H_A).set(rate(5), { merge: true }));
  // Nor by deleting it and writing a fresh, already-rated one.
  await assertFails(as(B_AUTH).doc(H_A).delete());
  await assertFails(as(B_AUTH).doc(H_A).set({ userId: A, rating: 5 }));
});

test("the owner cannot delete a rated row and rate it again", async () => {
  await assertSucceeds(as(A_AUTH).doc(H_A).update(rate(3)));
  await assertFails(as(A_AUTH).doc(H_A).delete());
  await assertFails(as(A_AUTH).doc(H_A).set({ ...rate(5), userId: A, openedAt: 100,
    closedAt: 200, messageCount: 2, lastMessage: "ok", backfilled: false }));
});

test("rating cannot move the window that decides which messages this was", async () => {
  // openedAt/closedAt are what every client uses to slice the thread. A client
  // that could widen them could pull later messages into an old conversation.
  await assertFails(as(A_AUTH).doc(H_A).update(rate(5, { openedAt: 0 })));
  await assertFails(as(A_AUTH).doc(H_A).update(rate(5, { closedAt: 9999999 })));
  await assertFails(as(A_AUTH).doc(H_A).update(rate(5, { messageCount: 999 })));
  await assertFails(as(A_AUTH).doc(H_A).update(rate(5, { userId: B })));
  await assertFails(as(A_AUTH).doc(H_A).update(rate(5, { backfilled: true })));
});

test("a rating cannot be nulled out to buy a second attempt", async () => {
  await assertSucceeds(as(A_AUTH).doc(H_A).update(rate(2)));
  await assertFails(as(A_AUTH).doc(H_A).update({ rating: 0, ratedAt: 0, ratingComment: "" }));
});

test("the ticket itself still cannot be forged into somebody else's", async () => {
  // The parent rule predates this work; a change to the child must not have
  // loosened it.
  await assertFails(as(B_AUTH).doc(`support_tickets/${A}`).set({ userId: A, status: "OPEN" }));
  await assertFails(as(B_AUTH).doc(`support_tickets/${A}`).update({ status: "OPEN" }));
  await assertSucceeds(as(B_AUTH).doc(`support_tickets/${B}`).set({ userId: B, status: "OPEN" }, { merge: true }));
});

test("B cannot read A's support messages through the thread either", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("chat_messages").doc("m1").set({
      conversationId: `support_${A}`, senderId: A, content: "private", timestamp: 150,
    });
  });
  await assertFails(as(B_AUTH).doc("chat_messages/m1").get());
  await assertSucceeds(as(A_AUTH).doc("chat_messages/m1").get());
});
