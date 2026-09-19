"use strict";

const test = require("node:test");
const assert = require("node:assert");
const { summarizeConversation, historyId, PREVIEW_MAX } = require("../lib/support");

test("archives only what came after the previous close", () => {
  const msgs = [
    { timestamp: 100, content: "old question" },
    { timestamp: 200, content: "old answer" },
    { timestamp: 500, content: "new question" },
    { timestamp: 450, content: "sent just before, out of order" },
  ];
  const s = summarizeConversation(msgs, 300);
  assert.deepStrictEqual(s, {
    openedAt: 450, lastMessageAt: 500, messageCount: 2, lastMessage: "new question",
  });
});

test("a close with nothing new archives nothing", () => {
  // Closing a ticket twice, or one a booking opened that nobody wrote in, must
  // not produce a conversation she is asked to rate.
  assert.strictEqual(summarizeConversation([{ timestamp: 100, content: "x" }], 100), null);
  assert.strictEqual(summarizeConversation([], 0), null);
  assert.strictEqual(summarizeConversation(undefined, 0), null);
});

test("the first close takes the whole thread", () => {
  const s = summarizeConversation([{ timestamp: 1, content: "hi" }], 0);
  assert.strictEqual(s.messageCount, 1);
  assert.strictEqual(s.openedAt, 1);
});

test("preview is bounded", () => {
  const s = summarizeConversation([{ timestamp: 1, content: "a".repeat(500) }], 0);
  assert.strictEqual(s.lastMessage.length, PREVIEW_MAX);
  assert.ok(s.lastMessage.endsWith("…"));
});

test("history id is stable for the same conversation", () => {
  // A retried trigger must collide, not duplicate.
  assert.strictEqual(historyId(1726560000000), historyId("1726560000000"));
  assert.notStrictEqual(historyId(1), historyId(2));
  // Normalised, not merely interpolated. Dropping the Number() coercion leaves
  // the line above passing — template interpolation of "1726560000000" and
  // 1726560000000 produce the same string — so it proved nothing about the
  // coercion it was written to protect. These do differ without it, and a
  // second id for one conversation is a second "rate us" push.
  assert.strictEqual(historyId("0007"), historyId(7));
  assert.strictEqual(historyId(7.0), historyId("7"));
});
