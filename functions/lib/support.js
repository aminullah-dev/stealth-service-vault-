"use strict";

// Support conversations: where one ends and the next begins.
//
// A user has ONE chat thread with support ("support_{uid}") and one ticket doc
// (support_tickets/{uid}), and that is deliberately unchanged — the Android app
// in customers' hands writes exactly that shape, and overwrites the whole ticket
// doc with .set() every time she contacts support, so nothing new can live on
// the ticket and survive.
//
// What changed is that closing a ticket now draws a line. The server archives
// everything since the previous line into support_tickets/{uid}/history/{id},
// and "the current conversation" is simply the messages after the newest
// line. No message is moved or rewritten, so an old client that still shows the
// whole thread is showing true data, just all of it.

/** How much of the last message the history row keeps as a preview. */
const PREVIEW_MAX = 140;

/**
 * The conversation a close is archiving: every message after [lastClosedAt].
 *
 * Returns null when there is nothing to archive — an admin closing a ticket that
 * a booking opened but nobody wrote in, or closing the same ticket twice. A
 * history row with no messages would be a conversation she is asked to rate
 * that never happened.
 *
 * @param {Array<{timestamp:number, content?:string}>} messages
 * @param {number} lastClosedAt  closedAt of the newest existing history row, or 0
 */
function summarizeConversation(messages, lastClosedAt) {
  const since = Number(lastClosedAt) || 0;
  const mine = (messages || [])
    .filter((m) => m && Number(m.timestamp) > since)
    .sort((a, b) => Number(a.timestamp) - Number(b.timestamp));
  if (mine.length === 0) return null;
  const last = mine[mine.length - 1];
  const text = String(last.content || "").trim();
  return {
    openedAt: Number(mine[0].timestamp),
    lastMessageAt: Number(last.timestamp),
    messageCount: mine.length,
    lastMessage: text.length > PREVIEW_MAX ? text.slice(0, PREVIEW_MAX - 1) + "…" : text,
  };
}

/**
 * Deterministic history id for a conversation.
 *
 * Firestore triggers are delivered at least once. Keyed on the conversation's
 * first message, a retried close collides with the row it already wrote and
 * fails its create() instead of filing the same conversation twice — and
 * sending her a second "rate us" push.
 */
function historyId(openedAt) {
  return `c_${Number(openedAt)}`;
}

module.exports = { summarizeConversation, historyId, PREVIEW_MAX };
