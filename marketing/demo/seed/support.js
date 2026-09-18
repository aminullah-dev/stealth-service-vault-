"use strict";
/**
 * A demo support thread: one closed conversation in the history (still ratable),
 * one already rated, and a live current conversation.
 *
 * The "current" conversation is defined by the app as every message in
 * support_{userId} after the newest closedAt, so the timestamps below are what
 * decide which messages belong to which conversation — they are not decoration.
 *
 * Written straight to Firestore because archiveSupportConversation (the callable
 * that normally writes a history row when an admin closes a ticket) is not
 * deployed to safebeauty-staging.
 */
const L = require("./lib.js");
const A = require("./account.json");

const MIN = 60 * 1000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const NOW = Date.now();

const CONV = `support_${A.appUid}`;
const ADMIN_ID = "demo-support-agent";
const ADMIN_NAME = "پشتیبانی سیف بیوتی";

const msg = (at, mine, content) => ({
  conversationId: CONV,
  senderId: mine ? A.appUid : ADMIN_ID,
  senderName: mine ? A.name : ADMIN_NAME,
  content,
  timestamp: at,
  salonId: "",          // a support thread belongs to no salon
});

// ── Conversation 1: closed 26 days ago, already rated ────────────────────────
const C1_OPEN = NOW - 27 * DAY;
const C1_CLOSE = NOW - 26 * DAY;
const C1 = [
  msg(C1_OPEN + 2 * MIN,  true,  "سلام، من چطور می‌توانم وقت رزرو شده‌ام را تغییر بدهم؟"),
  msg(C1_OPEN + 9 * MIN,  false, "سلام و وقت بخیر. از بخش «رزروهای من» روی رزرو مورد نظر «تغییر زمان» را بزنید و وقت جدید را انتخاب کنید."),
  msg(C1_OPEN + 14 * MIN, true,  "پیدا کردم، تشکر از شما."),
  msg(C1_CLOSE - 5 * MIN, false, "خواهش می‌کنیم. اگر باز هم سوالی داشتید در خدمت هستیم."),
];

// ── Conversation 2: closed 3 days ago, NOT yet rated ─────────────────────────
const C2_OPEN = NOW - 4 * DAY;
const C2_CLOSE = NOW - 3 * DAY;
const C2 = [
  msg(C2_OPEN + 3 * MIN,  true,  "پرداخت نقدی را انتخاب کردم، آیا باید پول را پیشکی بدهم؟"),
  msg(C2_OPEN + 11 * MIN, false, "نخیر. در پرداخت نقدی، پول را همان روز و در خود سالن می‌پردازید. فقط لطف کنید مبلغ دقیق را همراه داشته باشید."),
  msg(C2_OPEN + 16 * MIN, true,  "بسیار خوب، تشکر."),
  msg(C2_CLOSE - 20 * MIN, false, "به سلامت باشید. گفتگو را می‌بندیم؛ هر وقت لازم شد دوباره بنویسید."),
];

// ── The CURRENT conversation: after the newest closedAt, still open ──────────
const C3 = [
  msg(NOW - 55 * MIN, true,  "سلام، آیا می‌توانم برای خواهرم هم در همان وقت رزرو کنم؟"),
  msg(NOW - 42 * MIN, false, "سلام. بلی — در مرحله انتخاب خدمات گزینه «رزرو گروهی / عروسی» را بزنید و تعداد نفرات را بنویسید."),
  msg(NOW - 31 * MIN, true,  "تشکر، امتحان می‌کنم."),
];

async function main() {
  let n = 0;
  for (const [i, m] of [...C1, ...C2, ...C3].entries()) {
    await L.setDoc("chat_messages", `demo-support-msg-${String(i + 1).padStart(2, "0")}`, m);
    n += 1;
  }

  // The inbox row the admin console reads. OPEN, because C3 is still going.
  await L.setDoc("support_tickets", A.appUid, {
    userId: A.appUid,
    userName: A.name,
    userRole: "CUSTOMER",
    relatedInfo: "میکاپ مجلسی · سالن یاسمین",
    status: "OPEN",
    updatedAt: C3[C3.length - 1].timestamp,
    unreadForAdmin: false,
  });

  const history = [
    {
      id: "demo-history-01",
      userId: A.appUid,
      openedAt: C1_OPEN, closedAt: C1_CLOSE,
      messageCount: C1.length,
      lastMessage: C1[C1.length - 1].content,
      rating: 5, ratingComment: "خیلی زود جواب دادند.", ratedAt: C1_CLOSE + 2 * HOUR,
      backfilled: false,
    },
    {
      id: "demo-history-02",
      userId: A.appUid,
      openedAt: C2_OPEN, closedAt: C2_CLOSE,
      messageCount: C2.length,
      lastMessage: C2[C2.length - 1].content,
      // rating 0 + backfilled false ⇒ canRate() is true, so the rating card shows.
      rating: 0, ratingComment: "", ratedAt: 0,
      backfilled: false,
    },
  ];
  for (const h of history) {
    const { id, ...doc } = h;
    await L.setDoc(`support_tickets/${A.appUid}/history`, id, doc);
  }

  console.log(`support: ${n} messages, 1 ticket, ${history.length} closed conversations (1 ratable)`);
}

main().catch((e) => { console.error(e); process.exit(1); });
