// notifications — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { admin, db, logger } = require("../shared");

// Nudge lapsed customers back. A customer whose last completed visit is older
// than 30 days gets a one-time "we miss you" notification (→ FCM via
// pushOnNotificationCreated), at most once per 30 days (lastNudgedAt cooldown).
// Only people who have actually visited (lastVisitAt set) are ever nudged.
const REENGAGE_AFTER_MS    = 30 * 24 * 60 * 60 * 1000;

const REENGAGE_COOLDOWN_MS = 30 * 24 * 60 * 60 * 1000;

exports.sendReengagementNudges = onSchedule(
  { schedule: "every 24 hours", region: "us-central1" },
  async () => {
    const now = Date.now();
    const lapsed = await db.collection("users")
      .where("lastVisitAt", "<=", now - REENGAGE_AFTER_MS)
      .get();
    if (lapsed.empty) return;

    let count = 0;
    for (const doc of lapsed.docs) {
      const u = doc.data();
      if (Number(u.lastNudgedAt || 0) > now - REENGAGE_COOLDOWN_MS) continue; // cooldown
      const batch = db.batch();
      batch.update(doc.ref, { lastNudgedAt: now });
      batch.set(db.collection("notifications").doc(), {
        recipientId: doc.id,
        type:        "REENGAGEMENT",
        msgKey:      "REENGAGEMENT",
        msgParams:   {},
        title:       "We miss you 💕",
        body:        "It's been a while — book your next beauty appointment on SafeBeauty.",
        isRead:      false,
        createdAt:   now,
        relatedId:   "",
      });
      await batch.commit();
      count++;
    }
    logger.log(`sendReengagementNudges: nudged ${count} lapsed customer(s)`);
  }
);

// ── recordProviderPayout (callable, admin-only) ───────────────────────────────

/**
 * Records that the platform has paid a provider their owed balance (cash,
 * HesabPay transfer, etc. — settled outside the app). Atomically writes a
 * `payouts` history row and resets provider_balances/{providerId}.owedAmount
 * to 0. Admin-only; clients can't touch provider_balances directly.
 */

// ── pushOnNotificationCreated (Firestore trigger) ─────────────────────────────
//
// Single delivery point for real push notifications: every notifications/{id}
// doc — written by the functions above AND by client-side flows (booking
// confirmed, waitlist slot available) — becomes an FCM push to the recipient's
// registered device. Without this, "notifications" only ever appeared inside
// the app's Notification Center while it was open; a salon would never learn
// about a new paid booking until they happened to open the app.
exports.pushOnNotificationCreated = onDocumentCreated(
  { document: "notifications/{notifId}", region: "us-central1" },
  async (event) => {
    const n = event.data ? event.data.data() : null;
    if (!n || !n.recipientId || !n.title) return;

    const userSnap = await db.doc(`users/${n.recipientId}`).get();
    const token = userSnap.exists ? String(userSnap.data().fcmToken || "") : "";
    if (!token) return; // user never logged in on a push-capable device

    // The user doc is already loaded for the token, so reading their language
    // here is free. Falls back to the stored English text for older docs.
    const lang = userSnap.exists ? String(userSnap.data().lang || "") : "";
    const { title, body } = localizeNotification(n, lang);

    try {
      await admin.messaging().send({
        token,
        notification: { title, body },
        // Duplicated under both key styles: the foreground handler
        // (SafeBeautyMessagingService) reads "type"/"relatedId", while a tap on
        // a background notification delivers data keys as raw intent extras,
        // where MainActivity expects "notif_type"/"notif_related_id".
        data: {
          type:             String(n.type || ""),
          relatedId:        String(n.relatedId || ""),
          notif_type:       String(n.type || ""),
          notif_related_id: String(n.relatedId || ""),
        },
        android: { priority: "high" },
      });
    } catch (err) {
      // Expired/rotated tokens are routine — log and move on, never retry-loop.
      logger.warn("pushOnNotificationCreated: send failed", {
        recipientId: n.recipientId,
        error: String(err.message || err),
      });
    }
  }
);

// ── pushOnBroadcastCreated (Firestore trigger) ────────────────────────────────
//
// An admin broadcast is a platform-wide announcement, so it fans out as an FCM
// push to every user with a registered device — the message reaches people even
// when the app is closed (the in-app banner/popup only shows while it's open).
// FCM multicast is capped at 500 tokens per call, so we chunk.
exports.pushOnBroadcastCreated = onDocumentCreated(
  { document: "broadcasts/{bId}", region: "us-central1" },
  async (event) => {
    const b = event.data ? event.data.data() : null;
    if (!b || !b.message) return;

    // Optional audience filters set by the admin console. Absent/blank means
    // "everyone", so existing broadcasts keep their old platform-wide behaviour.
    const wantRole = String(b.targetRole || "").toUpperCase();      // CUSTOMER | PROVIDER
    const wantLang = String(b.targetLang || "").toLowerCase();      // fa | ps | en
    const wantDistrict = String(b.targetDistrict || "");            // salon district

    // A district filter only makes sense for providers, and their district lives
    // on the salon rather than the user, so resolve those owners first.
    let districtOwners = null;
    if (wantDistrict) {
      districtOwners = new Set();
      const sSnap = await db.collection("salons").where("district", "==", wantDistrict).get();
      sSnap.forEach((d) => { const pid = d.data().providerId; if (pid) districtOwners.add(pid); });
      if (districtOwners.size === 0) {
        logger.log(`pushOnBroadcastCreated: no salons in ${wantDistrict}, nothing sent`);
        return;
      }
    }

    // Paged rather than read-all. This used to load every matching user into one
    // invocation and send 500 at a time in sequence -- which at 100,000 users is
    // 100,000 reads and roughly two hundred sequential sends, comfortably past
    // the function timeout. It would then stop halfway with no record of where,
    // so the broadcast could be neither resumed nor safely retried: retrying
    // would message everyone it had already reached a second time.
    //
    // Progress is recorded on the broadcast document, and resumeBroadcasts
    // continues anything left unfinished. Invariant Q-4.
    await event.data.ref.set({
      sendState: "SENDING",
      sentCount: 0,
      startedAt: Date.now(),
    }, { merge: true });

    await deliverBroadcast(event.data.ref, b, districtOwners);
  }
);

/** How many recipients one invocation will attempt before handing over. */

const BROADCAST_PAGE = 500;

const BROADCAST_MAX_PAGES_PER_RUN = 6;

/**
 * Send a broadcast from where it left off, and record how far it got.
 *
 * Bounded per invocation so it always finishes well inside the timeout, leaving
 * a cursor rather than an unknown state. Called on creation and again by
 * resumeBroadcasts until the state reaches DONE.
 */

async function deliverBroadcast(ref, b, districtOwners) {
  const wantRole = String(b.targetRole || "").toUpperCase();
  const wantLang = String(b.targetLang || "").toLowerCase();

  const title = "SafeBeauty";
  const body  = String(b.message);
  const data  = { type: "BROADCAST", relatedId: "", notif_type: "BROADCAST", notif_related_id: "" };

  let cursorId = String(b.sendCursor || "");
  let sent     = Number(b.sentCount || 0);
  let pages    = 0;

  while (pages < BROADCAST_MAX_PAGES_PER_RUN) {
    let q = db.collection("users").orderBy(admin.firestore.FieldPath.documentId());
    if (wantRole) q = q.where("role", "==", wantRole);
    if (wantLang) q = q.where("lang", "==", wantLang);
    if (cursorId) q = q.startAfter(cursorId);

    const page = await q.limit(BROADCAST_PAGE).get();
    if (page.empty) {
      await ref.set({ sendState: "DONE", sentCount: sent, finishedAt: Date.now() }, { merge: true });
      logger.log(`deliverBroadcast: finished, ${sent} device(s)`);
      return;
    }

    const tokens = [];
    page.docs.forEach((doc) => {
      if (districtOwners && !districtOwners.has(doc.id)) return;
      const t = String((doc.data() || {}).fcmToken || "");
      if (t) tokens.push(t);
    });

    if (tokens.length) {
      try {
        await admin.messaging().sendEachForMulticast({
          tokens, notification: { title, body }, data,
          android: { priority: "high" },
        });
        sent += tokens.length;
      } catch (err) {
        logger.warn("deliverBroadcast: batch send failed", { error: String(err.message || err) });
      }
    }

    cursorId = page.docs[page.docs.length - 1].id;
    pages += 1;

    // Written every page, not at the end: an invocation killed mid-run must
    // leave behind where it actually got to.
    await ref.set({ sendState: "SENDING", sentCount: sent, sendCursor: cursorId }, { merge: true });

    if (page.size < BROADCAST_PAGE) {
      await ref.set({ sendState: "DONE", sentCount: sent, finishedAt: Date.now() }, { merge: true });
      logger.log(`deliverBroadcast: finished, ${sent} device(s)`);
      return;
    }
  }

  logger.log(`deliverBroadcast: paused after ${sent} device(s), will resume`);
}

// Continues any broadcast that did not finish in one invocation.
exports.resumeBroadcasts = onSchedule(
  { schedule: "every 5 minutes", region: "us-central1" },
  async () => {
    const stuck = await db.collection("broadcasts")
      .where("sendState", "==", "SENDING")
      .limit(3)
      .get();
    if (stuck.empty) return;

    for (const doc of stuck.docs) {
      const b = doc.data() || {};
      // The district filter has to be rebuilt, since it is derived rather than
      // stored on the broadcast.
      let districtOwners = null;
      const wantDistrict = String(b.targetDistrict || "");
      if (wantDistrict) {
        districtOwners = new Set();
        const sSnap = await db.collection("salons").where("district", "==", wantDistrict).get();
        sSnap.forEach((d) => { const pid = (d.data() || {}).providerId; if (pid) districtOwners.add(pid); });
      }
      await deliverBroadcast(doc.ref, b, districtOwners);
    }
  }
);

const NOTIF_I18N = {
  NEW_BOOKING_CASH: {
    en: { t: "New Cash Booking",  b: (p) => `${p.service} — AFN ${p.price} to collect in person` },
    fa: { t: "رزرو نقدی جدید",     b: (p) => `${p.service} — ${p.price} افغانی نقدی دریافت کنید` },
    ps: { t: "نوی نغدي بکینګ",     b: (p) => `${p.service} — ${p.price} افغانۍ په نغدو واخلئ` },
  },
  NEW_BOOKING_PAID: {
    en: { t: "New Paid Booking",  b: (p) => `${p.service} — paid AFN ${p.amount}` },
    fa: { t: "رزرو پرداخت‌شده جدید", b: (p) => `${p.service} — ${p.amount} افغانی پرداخت شد` },
    ps: { t: "نوی تادیه شوی بکینګ", b: (p) => `${p.service} — ${p.amount} افغانۍ تادیه شوې` },
  },
  BOOKING_CONFIRMED: {
    en: { t: "Booking Confirmed", b: (p) => `${p.service} at ${p.salon}` },
    fa: { t: "رزرو تأیید شد",      b: (p) => `${p.service} در ${p.salon}` },
    ps: { t: "بکینګ تایید شو",     b: (p) => `${p.service} په ${p.salon} کې` },
  },
  BOOKING_CANCELLED_BY_CUSTOMER: {
    en: { t: "Booking Cancelled", b: (p) => `${p.service} was cancelled by the customer.` },
    fa: { t: "رزرو لغو شد",        b: (p) => `${p.service} توسط مشتری لغو شد.` },
    ps: { t: "بکینګ لغوه شو",      b: (p) => `${p.service} د پیرودونکي لخوا لغوه شو.` },
  },
  BOOKING_DECLINED: {
    en: { t: "Booking Declined",  b: (p) => `${p.service} at ${p.salon} was declined.` },
    fa: { t: "رزرو رد شد",         b: (p) => `${p.service} در ${p.salon} رد شد.` },
    ps: { t: "بکینګ رد شو",        b: (p) => `${p.service} په ${p.salon} کې رد شو.` },
  },
  BOOKING_RESCHEDULED: {
    en: { t: "Booking Rescheduled", b: (p) => `${p.service} was moved to a new time — please re-confirm.` },
    fa: { t: "رزرو جابه‌جا شد",      b: (p) => `${p.service} به زمان جدیدی منتقل شد — لطفاً دوباره تأیید کنید.` },
    ps: { t: "بکینګ بدل شو",         b: (p) => `${p.service} نوي وخت ته ولیږدول شو — مهرباني وکړئ بیا یې تایید کړئ.` },
  },
  BOOKING_REMINDER: {
    en: { t: "Upcoming Appointment", b: (p) => `${p.service} at ${p.salon} is coming up soon.` },
    fa: { t: "نوبت پیشِ‌رو",          b: (p) => `${p.service} در ${p.salon} به‌زودی است.` },
    ps: { t: "راتلونکی نوبت",         b: (p) => `${p.service} په ${p.salon} کې ډېر ژر دی.` },
  },
  WAITLIST_SLOT: {
    en: { t: "A slot opened up 🎉", b: (p) => `${p.salon} has a free slot on your waitlisted day — book it before it's gone!` },
    fa: { t: "یک نوبت خالی شد 🎉",   b: (p) => `${p.salon} در روزی که در لیست انتظار بودید جای خالی دارد — قبل از پر شدن رزرو کنید!` },
    ps: { t: "یو ځای خالي شو 🎉",    b: (p) => `${p.salon} په هغه ورځ کې چې د انتظار لیست کې وئ خالي ځای لري — د ډکېدو دمخه یې ونیسئ!` },
  },
  POINTS_REDEEMED: {
    en: { t: "Points redeemed 🎉", b: (p) => `You turned ${p.spend} points into AFN ${p.credit} of wallet credit.` },
    fa: { t: "امتیازها تبدیل شد 🎉", b: (p) => `${p.spend} امتیاز را به ${p.credit} افغانی اعتبار تبدیل کردید.` },
    ps: { t: "ټکي تبادله شول 🎉",   b: (p) => `${p.spend} ټکي مو په ${p.credit} افغانۍ کریډیټ بدل کړل.` },
  },
  PROFILE_COMPLETE: {
    en: { t: "Profile complete 🌟", b: (p) => `You earned ${p.points} loyalty points for completing your profile.` },
    fa: { t: "پروفایل کامل شد 🌟",   b: (p) => `برای تکمیل پروفایل ${p.points} امتیاز وفاداری گرفتید.` },
    ps: { t: "پروفایل بشپړ شو 🌟",   b: (p) => `د پروفایل بشپړولو لپاره مو ${p.points} د وفادارۍ ټکي ترلاسه کړل.` },
  },
  GIFT_RECEIVED: {
    en: { t: "You received a gift card 🎁", b: (p) => `AFN ${p.amount} credit was added to your account.` },
    fa: { t: "کارت هدیه دریافت کردید 🎁",   b: (p) => `${p.amount} افغانی اعتبار به حساب شما اضافه شد.` },
    ps: { t: "د ډالۍ کارت مو ترلاسه کړ 🎁", b: (p) => `${p.amount} افغانۍ کریډیټ ستاسو حساب ته اضافه شو.` },
  },
  WALLET_TOPUP: {
    en: { t: "Wallet topped up 👛", b: (p) => `AFN ${p.amount} was added to your wallet.` },
    fa: { t: "کیف پول شارژ شد 👛",   b: (p) => `${p.amount} افغانی به کیف پول شما اضافه شد.` },
    ps: { t: "بټوه ډکه شوه 👛",      b: (p) => `${p.amount} افغانۍ ستاسو بټوې ته اضافه شوې.` },
  },
  TIP_RECEIVED: {
    en: { t: "You received a tip 💝", b: (p) => `A customer tipped you AFN ${p.amount}.` },
    fa: { t: "انعام دریافت کردید 💝",  b: (p) => `یک مشتری ${p.amount} افغانی انعام داد.` },
    ps: { t: "بخشش مو ترلاسه کړ 💝",   b: (p) => `یو پیرودونکي تاسو ته ${p.amount} افغانۍ بخشش درکړ.` },
  },
  REFERRAL_REWARD: {
    en: { t: "Referral Reward", b: (p) => `A friend you invited just joined — you earned AFN ${p.credit} credit!` },
    fa: { t: "پاداش معرفی",      b: (p) => `دوستی که دعوت کردید عضو شد — ${p.credit} افغانی اعتبار گرفتید!` },
    ps: { t: "د معرفي انعام",     b: (p) => `هغه ملګری چې بلنه مو ورکړې وه غړی شو — ${p.credit} افغانۍ کریډیټ مو ترلاسه کړ!` },
  },
  PAYOUT_SENT: {
    en: { t: "Payout Sent", b: (p) => `You have been paid AFN ${p.amount}.` },
    fa: { t: "پرداخت ارسال شد", b: (p) => `${p.amount} افغانی به شما پرداخت شد.` },
    ps: { t: "تادیه واستول شوه",  b: (p) => `${p.amount} افغانۍ تاسو ته تادیه شوې.` },
  },
  REFUND_PROCESSED: {
    en: { t: "Refund Processed", b: (p) => `Your refund of AFN ${p.amount} has been processed.` },
    fa: { t: "بازپرداخت انجام شد", b: (p) => `بازپرداخت ${p.amount} افغانی شما انجام شد.` },
    ps: { t: "بیرته ورکړه ترسره شوه", b: (p) => `ستاسو د ${p.amount} افغانۍ بیرته ورکړه ترسره شوه.` },
  },
  CREDIT_ADDED: {
    en: { t: "Credit added to your account 🎁", b: (p) => `You've received ${p.amount} AFN in credit${p.reason ? " — " + p.reason : ""}.` },
    fa: { t: "اعتبار به حسابتان اضافه شد 🎁",   b: (p) => `${p.amount} افغانی اعتبار دریافت کردید${p.reason ? " — " + p.reason : ""}.` },
    ps: { t: "کریډیټ ستاسو حساب ته اضافه شو 🎁", b: (p) => `${p.amount} افغانۍ کریډیټ مو ترلاسه کړ${p.reason ? " — " + p.reason : ""}.` },
  },
  REENGAGEMENT: {
    en: { t: "We miss you 💕", b: () => "It's been a while — book your next beauty appointment on SafeBeauty." },
    fa: { t: "دلتنگ شما شدیم 💕", b: () => "مدتی گذشته — نوبت بعدی زیبایی‌تان را در سیف‌بیوتی رزرو کنید." },
    ps: { t: "ستاسو په یاد یو 💕", b: () => "یو څه وخت تېر شو — خپل راتلونکی د ښکلا نوبت په سیف‌بیوتي کې ونیسئ." },
  },
  PENDING_BOOKINGS_WAITING: {
    en: { t: "Bookings waiting for you ⏳", b: (p) => p.count === 1
      ? "A customer has paid and is waiting for you to confirm their booking."
      : `${p.count} customers have paid and are waiting for you to confirm their bookings.` },
    fa: { t: "رزروها در انتظار شما ⏳", b: (p) => p.count === 1
      ? "یک مشتری پرداخت کرده و منتظر تأیید رزرو توسط شماست."
      : `${p.count} مشتری پرداخت کرده‌اند و منتظر تأیید رزروهایشان توسط شما هستند.` },
    ps: { t: "بکینګونه ستاسو په تمه دي ⏳", b: (p) => p.count === 1
      ? "یو پیرودونکي تادیه کړې او ستاسو د بکینګ تایید ته انتظار باسي."
      : `${p.count} پیرودونکو تادیه کړې او ستاسو د خپلو بکینګونو تایید ته انتظار باسي.` },
  },
  BOOKING_AUTO_CANCELLED: {
    en: { t: "Booking cancelled — refund on the way", b: (p) =>
      `${p.salon} did not confirm your booking in time, so we cancelled it. Your payment is being refunded.` },
    fa: { t: "رزرو لغو شد — بازپرداخت در راه است", b: (p) =>
      `${p.salon} رزرو شما را به‌موقع تأیید نکرد، بنابراین آن را لغو کردیم. مبلغ پرداختی شما بازگردانده می‌شود.` },
    ps: { t: "بکینګ لغوه شو — بیرته ورکړه په لاره ده", b: (p) =>
      `${p.salon} ستاسو بکینګ په وخت سره تایید نه کړ، نو موږ یې لغوه کړ. ستاسو تادیه بیرته درکول کیږي.` },
  },
  REVIEW_THANKS: {
    en: { t: "Thanks for your review 💬", b: (p) => `You earned ${p.points} loyalty points.` },
    fa: { t: "از نظر شما ممنونیم 💬",     b: (p) => `${p.points} امتیاز وفاداری گرفتید.` },
    ps: { t: "ستاسو د نظر مننه 💬",       b: (p) => `${p.points} د وفادارۍ ټکي مو ترلاسه کړل.` },
  },
};

/** Resolve a notification's text for [lang], falling back to the stored English
 *  title/body when the doc predates msgKey or the key is unknown. */

function localizeNotification(n, lang) {
  const entry = NOTIF_I18N[n.msgKey];
  if (!entry) return { title: String(n.title || ""), body: String(n.body || "") };
  // Dari is the default: the app's audience is Dari-first, so an unknown or
  // unset language should land there rather than on English.
  const L = entry[lang] || entry.fa || entry.en;
  const params = n.msgParams || {};
  let body;
  try { body = typeof L.b === "function" ? L.b(params) : String(L.b || ""); }
  catch (_) { body = String(n.body || ""); }
  return { title: L.t, body };
}
