// support — conversations with the platform, and where each one ends.
//
// See lib/support.js for the model. In short: a user keeps one thread
// ("support_{uid}") and one ticket doc forever, both unchanged for the sake of
// the Android build already on customers' phones. Closing a ticket archives the
// messages since the previous close into support_tickets/{uid}/history/{id};
// the app shows only what came after the newest archive as "the current
// conversation", lists the archives as history, and lets her rate each one.

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { db, logger } = require("../shared");
const { summarizeConversation, historyId } = require("../lib/support");

/** Enough for any real support conversation; bounded like every other read. */
const ARCHIVE_SCAN = 500;

// ── archiveSupportConversation (Firestore trigger) ────────────────────────────
//
// On the ticket, not in the admin console's Close button, because there are two
// Close buttons — the web console and the Android admin tab — and an archive
// that only one of them wrote would leave the other's conversations running on
// into the next one forever.
exports.archiveSupportConversation = onDocumentUpdated(
  { document: "support_tickets/{uid}", region: "us-central1" },
  async (event) => {
    const before = event.data && event.data.before.exists ? event.data.before.data() : {};
    const after  = event.data && event.data.after.exists  ? event.data.after.data()  : null;
    if (!after) return;
    if (after.status !== "CLOSED" || before.status === "CLOSED") return;

    const uid = event.params.uid;
    const ticketRef = db.doc(`support_tickets/${uid}`);

    const lastSnap = await ticketRef.collection("history")
      .orderBy("closedAt", "desc").limit(1).get();
    const lastClosedAt = lastSnap.empty ? 0 : Number(lastSnap.docs[0].data().closedAt || 0);

    // Descending, because the index that exists is conversationId ASC +
    // timestamp DESC — the one every client listener already uses. Ascending
    // would need a second index for no difference in what comes back.
    // Bounded above by the moment of the close, so a message she sends in the
    // seconds before this trigger runs starts the NEXT conversation instead of
    // being filed into the one that was just closed.
    const closedAt = Date.parse(event.time) || Date.now();
    const msgSnap = await db.collection("chat_messages")
      .where("conversationId", "==", `support_${uid}`)
      .where("timestamp", ">", lastClosedAt)
      .where("timestamp", "<=", closedAt)
      .orderBy("timestamp", "desc")
      .limit(ARCHIVE_SCAN)
      .get();
    const summary = summarizeConversation(msgSnap.docs.map((d) => d.data()), lastClosedAt);
    if (!summary) return;

    const id = historyId(summary.openedAt);
    const batch = db.batch();
    // create(), not set(): a redelivered trigger collides here and the whole
    // batch — including the push below — fails instead of running twice.
    batch.create(ticketRef.collection("history").doc(id), {
      userId:        uid,
      openedAt:      summary.openedAt,
      closedAt,
      messageCount:  summary.messageCount,
      lastMessage:   summary.lastMessage,
      rating:        0,      // 0 = not rated; the user may set 1–5 once (rules)
      ratingComment: "",
      ratedAt:       0,
      backfilled:    false,
    });
    batch.set(db.doc(`notifications/support_closed_${uid}_${summary.openedAt}`), {
      recipientId: uid,
      // CHAT_MESSAGE so both apps route a tap to the support thread they already
      // open for a new reply — which is where the rating card is waiting.
      type:        "CHAT_MESSAGE",
      msgKey:      "SUPPORT_CLOSED",
      msgParams:   {},
      title:       "Support conversation closed",
      body:        "How did we do? Rate the conversation.",
      isRead:      false,
      createdAt:   closedAt,
      relatedId:   `support_${uid}`,
    });
    try {
      await batch.commit();
    } catch (e) {
      if (e && (e.code === 6 || /ALREADY_EXISTS/.test(String(e.message)))) return;
      throw e;
    }
    logger.info("support conversation archived", { uid, id, messages: summary.messageCount });
  }
);

/**
 * A user's message reopens her ticket, whichever app sent it.
 *
 * iOS sets the ticket OPEN itself when she writes, and Android does it only on
 * the "contact support about this booking" path. A message sent into a CLOSED
 * ticket any other way — replying from the thread a "conversation closed" push
 * opens, which exists from this change on — reached the thread and never the
 * admin's inbox, which only lists OPEN tickets. Called from notifyOnChatMessage,
 * which already runs once per message.
 */
async function reopenSupportTicket(uid, message) {
  const ref = db.doc(`support_tickets/${uid}`);
  const snap = await ref.get();
  const t = snap.exists ? snap.data() : {};
  if (t.status === "OPEN" && t.unreadForAdmin === true) return;

  const patch = {
    id: uid,
    userId: uid,
    status: "OPEN",
    unreadForAdmin: true,
    updatedAt: Number(message.timestamp) || Date.now(),
  };
  // The inbox row names her. A ticket this creates from nothing would otherwise
  // show a bare uuid.
  if (!t.userName) {
    const u = await db.doc(`users/${uid}`).get();
    if (u.exists) {
      patch.userName = String(u.data().name || message.senderName || "");
      patch.userRole = String(u.data().role || "");
    }
  }
  await ref.set(patch, { merge: true });
}

exports.reopenSupportTicket = reopenSupportTicket;
