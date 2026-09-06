"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  TARGETS, REASONS, ACTIONS,
  targetSpec, reportId, blockId, isReason, isAction, canReport, planFor,
} = require("../lib/moderation");

test("every reportable target names a real collection and an author field", () => {
  // A target whose author field is wrong produces a report nobody can act on:
  // the admin sees the content but not who wrote it, so REMOVE_AND_SUSPEND has
  // nobody to suspend.
  const known = {
    POST:    ["salon_posts", "salonId"],
    STORY:   ["salon_stories", "salonId"],
    COMMENT: ["post_comments", "userId"],
    REVIEW:  ["reviews", "customerId"],
  };
  assert.deepEqual(Object.keys(TARGETS).sort(), Object.keys(known).sort());
  for (const [type, [collection, authorField]] of Object.entries(known)) {
    assert.equal(TARGETS[type].collection, collection, `${type} collection`);
    assert.equal(TARGETS[type].authorField, authorField, `${type} author field`);
  }
});

test("targetSpec refuses anything not on the list, including inherited keys", () => {
  assert.equal(targetSpec("POST").collection, "salon_posts");
  assert.equal(targetSpec("CHAT"), null);
  // "constructor" and "toString" are on every object; a lookup that used `in`
  // or a bare property read would hand back a function here and the callable
  // would try to query a collection named after it.
  assert.equal(targetSpec("constructor"), null);
  assert.equal(targetSpec("toString"), null);
  assert.equal(targetSpec("__proto__"), null);
});

test("one report per person per item, keyed by the document id", () => {
  // The same trick post_likes uses: a second tap overwrites rather than adds,
  // so one angry person cannot flood the queue and the open-report count is
  // a count of items, not of taps.
  assert.equal(reportId("POST", "p1", "u1"), "POST_p1_u1");
  assert.notEqual(reportId("POST", "p1", "u1"), reportId("POST", "p1", "u2"));
  assert.notEqual(reportId("POST", "p1", "u1"), reportId("COMMENT", "p1", "u1"));
});

test("a block is one-directional and names the blocker first", () => {
  assert.equal(blockId("me", "them"), "me_them");
  assert.notEqual(blockId("me", "them"), blockId("them", "me"));
});

test("reasons and actions are closed sets", () => {
  assert.ok(isReason("HARASSMENT"));
  assert.ok(!isReason("BECAUSE"));
  assert.ok(!isReason(""));
  assert.ok(isAction("REMOVE_AND_SUSPEND"));
  assert.ok(!isAction("DELETE_EVERYTHING"));
  assert.deepEqual(REASONS, ["SPAM", "HARASSMENT", "NUDITY", "HATE", "SCAM", "OTHER"]);
  assert.deepEqual(ACTIONS, ["DISMISS", "REMOVE", "REMOVE_AND_SUSPEND"]);
});

test("canReport: the happy path, and every way it is refused", () => {
  const base = { targetType: "COMMENT", targetId: "c1", reason: "SPAM", reporterUid: "u1", ownerUid: "u2" };
  assert.deepEqual(canReport(base), { ok: true });

  assert.equal(canReport({ ...base, targetType: "CHAT" }).why, "unknown-target");
  assert.equal(canReport({ ...base, targetId: "" }).why, "missing-target");
  assert.equal(canReport({ ...base, reason: "nope" }).why, "unknown-reason");
  assert.equal(canReport({ ...base, reporterUid: "" }).why, "no-reporter");
  // Reporting yourself is a mistake or an attempt to make the queue noisy.
  // Someone who wants their own comment gone can delete it.
  assert.equal(canReport({ ...base, ownerUid: "u1" }).why, "own-content");
});

test("a provider cannot report her own salon's post", () => {
  // The author field for a post is a salonId, not a uid, so comparing the
  // reporter to it never matched and this was allowed. ownerUid is the salon's
  // provider, which is the person the comparison is actually about.
  const post = { targetType: "POST", targetId: "p1", reason: "SPAM", reporterUid: "prov1" };
  assert.equal(canReport({ ...post, ownerUid: "prov1" }).why, "own-content");
  assert.deepEqual(canReport({ ...post, ownerUid: "prov2" }), { ok: true });
});

test("canReport allows a report when the author is not yet known", () => {
  // The callable looks the author up from the document; if the document holds
  // no author field, the report is still worth filing — the admin can see the
  // content and decide. Only a KNOWN self-report is refused.
  const r = canReport({ targetType: "POST", targetId: "p1", reason: "OTHER", reporterUid: "u1", ownerUid: "" });
  assert.deepEqual(r, { ok: true });
});

test("planFor: what each admin action actually does", () => {
  assert.deepEqual(planFor("DISMISS"),            { deleteTarget: false, suspendAuthor: false });
  assert.deepEqual(planFor("REMOVE"),             { deleteTarget: true,  suspendAuthor: false });
  assert.deepEqual(planFor("REMOVE_AND_SUSPEND"), { deleteTarget: true,  suspendAuthor: true });
  // An unknown action plans nothing rather than defaulting to the harmless
  // one: a typo in the console must fail loudly, not quietly dismiss a report.
  assert.equal(planFor("REMOVE_ONLY"), null);
  assert.equal(planFor(""), null);
});

test("suspending always removes as well — a suspension over content that stays up is not a resolution", () => {
  for (const action of ACTIONS) {
    const plan = planFor(action);
    if (plan.suspendAuthor) assert.ok(plan.deleteTarget, `${action} suspends without removing`);
  }
});
