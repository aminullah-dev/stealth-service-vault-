// content — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { averageRating } = require("../lib/reviews");
const { canReport, isReason, planFor, reportId, targetSpec } = require("../lib/moderation");
const { assertAdmin, assertDocId, assertNotSuspended, logAdminAction, resolveAppUser } = require("../shared");
const { onDocumentCreated, onDocumentDeleted, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { admin, db, logger } = require("../shared");

// ── pushOfferToFavoriters ─────────────────────────────────────────────────────
// When a provider posts a new (active) offer, notify every customer who
// favorited that salon. Favorites are mirrored to Firestore from the on-device
// list (see FirestoreRepository.setFavorite); each notification flows through the
// existing pushOnNotificationCreated → FCM pipeline. Fan-out is capped so one
// offer can't spawn an unbounded batch.
exports.pushOfferToFavoriters = onDocumentCreated(
  "salon_offers/{offerId}",
  async (event) => {
    const offer = event.data && event.data.data();
    if (!offer || offer.active === false || !offer.salonId) return;

    const favs = await db
      .collection("favorites")
      .where("salonId", "==", offer.salonId)
      .limit(500)
      .get();
    if (favs.empty) return;

    const salon = offer.salonName || "A salon you like";
    const title = "New offer 💖";
    const body  = offer.title
      ? `${salon}: ${offer.title}`
      : `${salon} just posted a new offer.`;

    let batch = db.batch();
    let pending = 0;
    let total = 0;
    for (const fav of favs.docs) {
      const customerId = fav.data().customerId;
      if (!customerId) continue;
      batch.set(db.collection("notifications").doc(), {
        recipientId: customerId,
        type:        "OFFER",
        // msgKey is what pushOnNotificationCreated translates by; title and body
        // stay as the English fallback for anything the catalogue does not know.
        msgKey:      "OFFER_NEW",
        msgParams:   { salon, text: offer.title || "" },
        title,
        body,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   offer.salonId,
      });
      pending++;
      total++;
      // Firestore batches cap at 500 writes; commit well under that.
      if (pending >= 400) {
        await batch.commit();
        batch = db.batch();
        pending = 0;
      }
    }
    if (pending > 0) await batch.commit();
    logger.log(`pushOfferToFavoriters: notified ${total} favoriter(s) of salon ${offer.salonId}`);
  }
);

// ── awardReviewPoints ─────────────────────────────────────────────────────────
// Loyalty points for leaving a review, with a bonus for attaching a photo (their
// review + photos help other customers). loyaltyPoints is client-frozen, so this
// server-side trigger is the only path that can grant them.
const REVIEW_POINTS       = 5;

const REVIEW_PHOTO_BONUS  = 5;

// ── submitReview (callable) ───────────────────────────────────────────────────
// The ONLY path that creates a review. Client review creates are blocked in
// firestore.rules; without this gate any approved customer could script
// unlimited review docs to farm loyalty points → wallet credit (real money) and
// forge salon ratings. Here the review is bound to a real, served appointment
// the caller owns, one review per appointment.
exports.submitReview = onCall(
  { region: "us-central1" },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
    const user = await resolveAppUser(request);
    assertNotSuspended(user);

    const { appointmentId, salonId, rating, comment, imageUrls } = request.data || {};
    if (!appointmentId) throw new HttpsError("invalid-argument", "A valid appointmentId is required.");
    assertDocId(appointmentId, "appointmentId");
    const stars = Math.round(Number(rating));
    if (!(stars >= 1 && stars <= 5)) {
      throw new HttpsError("invalid-argument", "Rating must be 1–5.");
    }

    const apptRef  = db.doc(`appointments/${appointmentId}`);
    const reviewRef = db.collection("reviews").doc();

    // Only accept photo URLs that live under THIS user's own reviews/ storage
    // path (the download URL embeds the path, url-encoded), so a client can't
    // pass arbitrary strings to trigger the photo bonus. Max 3.
    // Anchored to the bucket, not merely containing the path.
    //
    // This was `u.includes("/reviews%2F" + uid + "%2F")`, and a substring test
    // constrains nothing about where the URL points:
    // "https://attacker.example/px.gif#/reviews%2F<own-uid>%2F" passes it. The
    // string is stored verbatim on a review that every signed-in user can read,
    // and the app renders each entry with AsyncImage — so every woman who opens
    // that salon's page fetches it, handing the host her IP, coarse location,
    // User-Agent and the time she was looking at a beauty salon. That is
    // precisely the tracking this product exists to avoid, delivered through
    // the product itself.
    //
    // submitKyc solved the same problem by deriving the location server-side
    // rather than accepting one. Reviews cannot quite do that (the client
    // uploads before the review exists), so the check is a real prefix against
    // this bucket's own download host.
    const bucket = admin.storage().bucket().name;
    const ownPrefix =
      `https://firebasestorage.googleapis.com/v0/b/${bucket}/o/reviews%2F${user.uid}%2F`;
    const urls = Array.isArray(imageUrls)
      ? imageUrls
          .filter((u) => typeof u === "string" && u.startsWith(ownPrefix))
          .slice(0, 3)
      : [];

    await db.runTransaction(async (tx) => {
      const apptSnap = await tx.get(apptRef);
      if (!apptSnap.exists) throw new HttpsError("not-found", "Booking not found.");
      const appt = apptSnap.data();
      if (appt.customerId !== user.uid) {
        throw new HttpsError("permission-denied", "That isn't your booking.");
      }
      if (String(appt.salonId || "") !== String(salonId || "")) {
        throw new HttpsError("invalid-argument", "Salon mismatch.");
      }
      // A review only makes sense once the salon accepted/served the visit, and
      // exactly once per booking.
      if (appt.status !== "CONFIRMED" && appt.status !== "COMPLETED") {
        throw new HttpsError("failed-precondition", "You can review a booking after your visit.");
      }
      // CONFIRMED means the salon accepted the booking, not that it happened.
      // The message above already promised "after your visit"; without this the
      // status check alone let a customer rate an appointment she is booked in
      // for next week, and it counted toward the salon's average.
      const startsAt = Number(appt.appointmentDate || 0);
      if (Number.isFinite(startsAt) && startsAt > Date.now()) {
        throw new HttpsError("failed-precondition", "You can review a booking after your visit.");
      }
      if (appt.reviewed === true) {
        throw new HttpsError("failed-precondition", "You've already reviewed this booking.");
      }
      tx.update(apptRef, { reviewed: true });
      tx.set(reviewRef, {
        salonId:      String(salonId),
        customerId:   user.uid,
        customerName: user.name || "",
        rating:       stars,
        comment:      String(comment || "").slice(0, 1000),
        imageUrls:    urls,
        appointmentId,
        createdAt:    Date.now(),
      });
    });

    return { ok: true, reviewId: reviewRef.id };
  }
);

exports.awardReviewPoints = onDocumentCreated(
  "reviews/{reviewId}",
  async (event) => {
    const review = event.data && event.data.data();
    if (!review) return;

    // Recompute the salon's average rating server-side. rating is frozen against
    // client writes in firestore.rules (so a provider can't self-award a 5.0);
    // this Admin-SDK write is the authoritative source. Reviews are immutable
    // except for a provider reply (which doesn't change the score), so
    // recomputing on create covers it. Runs regardless of customerId.
    const salonId = String(review.salonId || "");
    if (salonId) {
      const snap = await db.collection("reviews").where("salonId", "==", salonId).get();
      const avg = averageRating(snap.docs.map((d) => d.data()));
      await db.doc(`salons/${salonId}`).set({ rating: avg }, { merge: true });
    }

    // Award loyalty points for leaving a review (+ a bonus when it has a photo).
    if (!review.customerId) return;
    const hasPhoto = Array.isArray(review.imageUrls) && review.imageUrls.length > 0;
    const points = REVIEW_POINTS + (hasPhoto ? REVIEW_PHOTO_BONUS : 0);

    const batch = db.batch();
    batch.set(
      db.doc(`users/${review.customerId}`),
      { loyaltyPoints: admin.firestore.FieldValue.increment(points) },
      { merge: true }
    );
    batch.set(db.collection("notifications").doc(), {
      recipientId: review.customerId,
      type:        "SYSTEM",
      msgKey:      "REVIEW_THANKS",
      msgParams:   { points },
      title:       "Thanks for your review 💬",
      body:        hasPhoto
        ? `You earned ${points} loyalty points for your review and photo.`
        : `You earned ${points} loyalty points for your review.`,
      isRead:      false,
      createdAt:   Date.now(),
      relatedId:   review.salonId || "",
    });
    await batch.commit();
  }
);

// ═══════════════════════════════════════════════════════════════════════════
// Admin control center — the platform admin's "solve any problem" toolbox and
// multi-admin management. Every callable here is gated to role === "ADMIN"
// (resolved via the uid_map bridge, never the raw auth uid) and writes an
// audit row to `admin_audit` so privileged actions are traceable.
// ═══════════════════════════════════════════════════════════════════════════

/** Resolve the caller and hard-fail unless they are an ADMIN. */

// ── Social feed: notify a salon's followers when it shares a new post ──────────
// Followers are the customers who favorited the salon (same list the offer
// fan-out uses). Mirrors pushOfferToFavoriters.
exports.pushPostToFollowers = onDocumentCreated(
  "salon_posts/{postId}",
  async (event) => {
    const post = event.data && event.data.data();
    if (!post || !post.salonId) return;

    const favs = await db
      .collection("favorites")
      .where("salonId", "==", post.salonId)
      .limit(500)
      .get();
    if (favs.empty) return;

    const salon = post.salonName || "A salon you follow";
    const title = "New photos 📸";
    const body  = post.caption
      ? `${salon}: ${post.caption}`
      : `${salon} shared new work.`;

    let batch = db.batch();
    let pending = 0;
    let total = 0;
    for (const fav of favs.docs) {
      const customerId = fav.data().customerId;
      if (!customerId) continue;
      batch.set(db.collection("notifications").doc(), {
        recipientId: customerId,
        type:        "POST",
        msgKey:      "POST_NEW",
        msgParams:   { salon, text: (post.caption || "").slice(0, 140) },
        title,
        body:        body.slice(0, 180),
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   post.salonId,
      });
      pending++;
      total++;
      if (pending >= 400) {
        await batch.commit();
        batch = db.batch();
        pending = 0;
      }
    }
    if (pending > 0) await batch.commit();
    logger.log(`pushPostToFollowers: notified ${total} follower(s) of salon ${post.salonId}`);
  }
);

// ── Account deletion (Google Play User Data policy) ───────────────────────────
//
// Play requires any app that creates accounts in-app to offer BOTH an in-app
// deletion path and a public web URL (public/delete-account). This is the
// server half: the client can never delete a user document directly — the rules
// leave no such path — so deletion goes through this callable.
//
// What we delete vs. keep, and why:
//   DELETE  the person — users/{uid}, uid_map, KYC images, profile photo, the
//           Firebase Auth account, plus their favorites/waitlist/notifications.
//   KEEP    the money — appointments and payments are the salon's business
//           records (and ours, for commission reconciliation), so they survive
//           with every personal field stripped. A deleted customer's booking
//           history becomes an anonymous row, not a dangling reference.
// Live bookings are cancelled first so neither side is left holding a slot for
// an account that no longer exists.

/** Appointment statuses that still hold a real slot on someone's calendar. */

// ── Like and comment counters ─────────────────────────────────────────────────
//
// The counts live on the post so the grid and the viewer can show them without
// a second query per photo, and they are maintained here rather than by the
// client for two reasons: `salon_posts` allows no client update at all (so a
// client could not write them even if we wanted it to), and a count a client
// can set is a count a client can invent.
//
// Written with increment() rather than a recount, so two people liking at the
// same moment cannot overwrite each other. A create/delete pair on the same id
// (unlike then like again) nets out correctly because each event moves the
// count by exactly one in one direction.
function countDelta(event) {
  const before = event.data && event.data.before && event.data.before.exists;
  const after  = event.data && event.data.after  && event.data.after.exists;
  if (!before && after) return 1;    // created
  if (before && !after) return -1;   // deleted
  return 0;                          // edited — the count did not move
}

async function bumpPostCounter(postId, field, delta) {
  if (!postId || !delta) return;
  try {
    await db.doc(`salon_posts/${postId}`).update({
      [field]: admin.firestore.FieldValue.increment(delta),
    });
  } catch (e) {
    // The post was deleted while its likes/comments were still being cleaned
    // up. Nothing to count any more, and nothing worth failing the trigger for.
    logger.warn(`bumpPostCounter: ${field} ${delta > 0 ? "+" : ""}${delta} on ${postId} failed`, e);
  }
}

exports.countPostLike = onDocumentWritten(
  { document: "post_likes/{likeId}", region: "us-central1" },
  async (event) => {
    const delta = countDelta(event);
    if (!delta) return;
    const snap = (event.data.after.exists ? event.data.after : event.data.before);
    await bumpPostCounter((snap.data() || {}).postId, "likeCount", delta);
  }
);

exports.countPostComment = onDocumentWritten(
  { document: "post_comments/{commentId}", region: "us-central1" },
  async (event) => {
    const delta = countDelta(event);
    if (!delta) return;
    const snap = (event.data.after.exists ? event.data.after : event.data.before);
    await bumpPostCounter((snap.data() || {}).postId, "commentCount", delta);
  }
);

// A deleted post leaves its likes and comments behind, and nothing would ever
// collect them: they are keyed by postId, not nested under it. Left alone they
// are billed storage that no screen can ever reach again.
exports.cleanupDeletedPost = onDocumentDeleted(
  { document: "salon_posts/{postId}", region: "us-central1" },
  async (event) => {
    const postId = event.params.postId;
    for (const col of ["post_likes", "post_comments"]) {
      // Batched rather than one delete per document: a popular photo can carry
      // hundreds of rows, and 500 is the batch ceiling.
      while (true) {
        const snap = await db.collection(col).where("postId", "==", postId).limit(400).get();
        if (snap.empty) break;
        const batch = db.batch();
        snap.docs.forEach((d) => batch.delete(d.ref));
        await batch.commit();
        if (snap.size < 400) break;
      }
    }
    logger.log(`cleanupDeletedPost: cleared likes and comments for ${postId}`);
  }
);

// ── cleanupDeletedReview ──────────────────────────────────────────────────────
//
// A salon's stored rating is the average of its reviews, and until moderation
// existed nothing could ever delete one — awardReviewPoints recomputes on
// create and says so in as many words: "reviews are immutable except for a
// provider reply, so recomputing on create covers it". That stopped being true
// the moment an admin could remove an abusive review.
//
// Without this, removing a 1-star review with abusive text takes the words off
// the page and leaves the star in the average forever: deriveSalonStats copies
// `rating` into `sortRating`, so it skews search ranking too, and it self-heals
// only when some other customer happens to review that salon — which for a
// salon with three reviews may be never.
//
// The appointment is released as well. `reviewed: true` is what stops a second
// review of the same booking; leaving it set after the review is gone means the
// customer is silenced rather than moderated.
exports.cleanupDeletedReview = onDocumentDeleted(
  { document: "reviews/{reviewId}", region: "us-central1" },
  async (event) => {
    const review = event.data && event.data.data();
    if (!review) return;

    const salonId = String(review.salonId || "");
    if (salonId) {
      // The same recompute awardReviewPoints does, from whatever is left.
      // averageRating returns 0 for an empty list, so the last review being
      // removed reads as unrated rather than as NaN.
      const snap = await db.collection("reviews").where("salonId", "==", salonId).get();
      await db.doc(`salons/${salonId}`).set(
        { rating: averageRating(snap.docs.map((d) => d.data())) },
        { merge: true }
      );
    }

    const appointmentId = String(review.appointmentId || "");
    if (appointmentId) {
      await db.doc(`appointments/${appointmentId}`)
        .set({ reviewed: false }, { merge: true })
        .catch(() => {});
    }

    logger.log(`cleanupDeletedReview: recomputed rating for ${salonId || "(no salon)"}`);
  }
);

// ── Seeding a salon's feed on the salon's behalf ──────────────────────────────
//
// A new marketplace has a circular problem: a customer will not browse an empty
// Discover grid, and a salon will not post into a feed nobody reads yet. Someone
// has to break the circle first, and it is the platform — the admin visits the
// salon, photographs the work with the owner's consent, and publishes it here.
//
// Kept server-side like every other admin mutation: the rules give clients no
// write path into a salon they do not own, and loosening them would have handed
// that path to every client. Each seeded document carries `createdByAdmin` so
// the content stays traceable and can be found again when the owner takes over.
exports.adminPostForSalon = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d  = request.data || {};

  const salonId = String(d.salonId || "").trim();
  const kind    = String(d.kind || "POST").trim().toUpperCase();

  if (!salonId) throw new HttpsError("invalid-argument", "salonId is required.");
  if (kind !== "POST" && kind !== "STORY") {
    throw new HttpsError("invalid-argument", "kind must be POST or STORY.");
  }

  const salonSnap = await db.doc(`salons/${salonId}`).get();
  if (!salonSnap.exists) throw new HttpsError("not-found", "No such salon.");
  const salon = salonSnap.data() || {};

  const now = Date.now();

  if (kind === "STORY") {
    const text = String(d.text || "").trim().slice(0, 200);
    if (!text) throw new HttpsError("invalid-argument", "A story needs text.");

    const ref = db.collection("salon_stories").doc();
    await ref.set({
      id:          ref.id,
      salonId,
      salonName:   salon.salonName || "",
      text,
      imageUrl:    "",
      storagePath: "",
      createdAt:   now,
      // The same fixed 24h lifetime the provider console writes, for the same
      // reason: stamped by the author so it cannot drift with a reader's clock.
      expiresAt:   now + 24 * 60 * 60 * 1000,
      createdByAdmin: me.uid,
    });

    await logAdminAction(me, "POST_FOR_SALON", {
      salonId, salonName: salon.salonName || "", kind, docId: ref.id,
    });
    logger.log(`adminPostForSalon: story for ${salonId} by ${me.uid}`);
    return { ok: true, id: ref.id };
  }

  // A post carries an image the console has already uploaded, because the bytes
  // never need to pass through a function to get to Storage. What must be
  // checked here is that the path it points at is the one it claims: an
  // unvalidated storagePath would let a post display any object in the bucket.
  const postId      = String(d.postId || "").trim();
  const imageUrl    = String(d.imageUrl || "").trim();
  const storagePath = String(d.storagePath || "").trim();

  if (!/^[A-Za-z0-9]{16,32}$/.test(postId)) {
    throw new HttpsError("invalid-argument", "A valid postId is required.");
  }
  if (!imageUrl) throw new HttpsError("invalid-argument", "A post needs an image.");
  if (storagePath !== `salon_posts/${salonId}/${postId}.jpg`) {
    throw new HttpsError("invalid-argument", "storagePath does not match this salon and post.");
  }

  const ref = db.doc(`salon_posts/${postId}`);
  if ((await ref.get()).exists) {
    throw new HttpsError("already-exists", "That post already exists.");
  }

  await ref.set({
    id:          postId,
    salonId,
    providerId:  salon.providerId || "",
    salonName:   salon.salonName || "",
    imageUrl,
    storagePath,
    caption:     String(d.caption || "").trim().slice(0, 300),
    createdAt:   now,
    createdByAdmin: me.uid,
  });

  await logAdminAction(me, "POST_FOR_SALON", {
    salonId, salonName: salon.salonName || "", kind, docId: postId,
  });
  logger.log(`adminPostForSalon: post ${postId} for ${salonId} by ${me.uid}`);
  return { ok: true, id: postId };
});

exports.cleanupExpiredStories = onSchedule(
  { schedule: "every 6 hours", region: "us-central1" },
  async () => {
    const snap = await db.collection("salon_stories")
      .where("expiresAt", "<", Date.now())
      .limit(300)
      .get();
    if (snap.empty) return;

    // Delete the images first: losing the document while the file survives
    // would orphan the file with nothing left pointing at it.
    for (const d of snap.docs) {
      const path = String(d.data().storagePath || "");
      if (!path) continue;
      // Derived and compared, never followed as given. This runs with the
      // Admin SDK, which bypasses storage.rules entirely — so an unchecked
      // storagePath here is a delete of ANY object in the bucket, chosen by
      // whoever wrote the story document. firestore.rules now constrains the
      // field on create, and this is the same check on the documents written
      // before it did.
      const expected = `salon_stories/${String(d.data().salonId || "")}/${d.id}.jpg`;
      if (path !== expected) {
        logger.warn("cleanupExpiredStories: storagePath does not match its story; file left in place",
          { storyId: d.id, path });
        continue;
      }
      try {
        await admin.storage().bucket().file(path).delete();
      } catch (e) {
        // Already gone, or never uploaded — not worth failing the sweep over.
        logger.debug("cleanupExpiredStories: image delete skipped", { path });
      }
    }

    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    logger.log(`cleanupExpiredStories: removed ${snap.size} expired story/stories`);
  }
);

// ── reportContent ─────────────────────────────────────────────────────────────
//
// A customer flagging somebody else's post, story, comment or review.
//
// This app carries other people's words and photographs and had no way at all to
// report any of it — not in either app, not on the server. Apple's Guideline 1.2
// asks for exactly three things from an app like this: reporting, blocking, and
// acting within a day. This is the first; `blocks` (client-written, rules-
// enforced) is the second; the admin console's queue is the third.
//
// Server-side rather than a client write, for the same reason reviews are: the
// report has to name the author, and the author is on a document the reporter
// may be able to read but must not be able to lie about. It also has to exist
// even when the content is later deleted, so the admin can see what was
// reported and by whom.
exports.reportContent = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);

  const d = request.data || {};
  const targetType = String(d.targetType || "");
  const targetId   = String(d.targetId || "");
  const reason     = String(d.reason || "");
  const note       = String(d.note || "").trim().slice(0, 500);

  const spec = targetSpec(targetType);
  if (!spec) throw new HttpsError("invalid-argument", "Unknown target type.");
  assertDocId(targetId, "targetId");
  if (!isReason(reason)) throw new HttpsError("invalid-argument", "Unknown reason.");

  // The content itself, read once — both to prove it exists and to capture who
  // wrote it and a snippet of what it said. A report that survives the content
  // it names is the only kind worth keeping: the usual response to being
  // reported is to delete the post.
  const targetSnap = await db.doc(`${spec.collection}/${targetId}`).get();
  if (!targetSnap.exists) throw new HttpsError("not-found", "That content no longer exists.");
  const target = targetSnap.data() || {};
  const authorId = String(target[spec.authorField] || "");

  // The author field and the person are the same string for a comment or a
  // review, and are not for a post or a story: there the field is a salonId.
  // Both are stored — `authorId` is what a client blocks on (it is what the
  // feed filters by), `ownerUid` is who an admin can suspend, and comparing the
  // reporter to the wrong one let a provider report her own salon's photo while
  // leaving the admin nobody to act against.
  let ownerUid = authorId;
  if (spec.authorKind === "SALON" && authorId) {
    const salonSnap = await db.doc(`salons/${authorId}`).get();
    ownerUid = salonSnap.exists ? String(salonSnap.data().providerId || "") : "";
  }

  const verdict = canReport({ targetType, targetId, reason, reporterUid: appUser.uid, ownerUid });
  if (!verdict.ok) {
    if (verdict.why === "own-content") {
      throw new HttpsError("failed-precondition", "You can delete your own content instead.");
    }
    throw new HttpsError("invalid-argument", "This can't be reported.");
  }

  // A snippet, not the whole document: the admin needs enough to judge, and a
  // report row is not a second copy of the content.
  const excerpt = String(
    target.caption || target.comment || target.text || target.title || ""
  ).trim().slice(0, 300);
  const imageUrl = String(
    target.imageUrl || (Array.isArray(target.imageUrls) ? target.imageUrls[0] : "") || ""
  );

  const id = reportId(targetType, targetId, appUser.uid);
  await db.doc(`content_reports/${id}`).set({
    targetType,
    targetId,
    targetCollection: spec.collection,
    authorKind: spec.authorKind,
    authorId,
    ownerUid,
    excerpt,
    imageUrl,
    reason,
    note,
    reporterId: appUser.uid,
    reporterName: appUser.name || "",
    status: "OPEN",
    createdAt: Date.now(),
  });

  return { ok: true, reportId: id };
});

// ── resolveContentReport (admin) ──────────────────────────────────────────────
//
// The other half of Guideline 1.2: the queue has to be actionable, and acting
// has to be one decision rather than three manual Firestore edits.
//
// Every open report against the same piece of content closes together. They are
// separate documents only so that one person cannot report twice; they are one
// decision, and leaving the others OPEN would show the admin a queue of items
// already dealt with.
exports.resolveContentReport = onCall({ region: "us-central1" }, async (request) => {
  const appUser = await assertAdmin(request);

  const d = request.data || {};
  const id     = String(d.reportId || "");
  const action = String(d.action || "");
  if (!id) throw new HttpsError("invalid-argument", "reportId is required.");
  const plan = planFor(action);
  if (!plan) throw new HttpsError("invalid-argument", "Unknown action.");

  const reportRef = db.doc(`content_reports/${id}`);
  const snap = await reportRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Report not found.");
  const report = snap.data();

  // Already dealt with. Without this, anyone holding the id — a second console
  // tab whose snapshot has not caught up, a retried call — could re-apply
  // REMOVE_AND_SUSPEND after another admin had lifted that suspension, and two
  // admins clicking the same row produced two audit rows for one decision.
  if (report.status !== "OPEN") {
    throw new HttpsError("failed-precondition", "This report was already resolved.");
  }

  if (plan.deleteTarget && report.targetCollection && report.targetId) {
    // Deleted, not hidden. A hidden row is one forgetful client away from being
    // visible again, and this content is small and replaceable — cleanupDeletedPost
    // already handles a post's comments and likes going with it.
    await db.doc(`${report.targetCollection}/${report.targetId}`).delete().catch(() => {});
  }

  const ownerUid = String(report.ownerUid || (report.authorKind === "USER" ? report.authorId : ""));
  if (plan.suspendAuthor && ownerUid) {
    // The shape resolveCustomerReport writes, so a suspension decided here is
    // the same suspension the Manage modal can see and lift. `status` alone was
    // once written here-adjacent and the callables never read it, which let a
    // suspended customer keep booking.
    await db.doc(`users/${ownerUid}`).set(
      {
        status:          "SUSPENDED",
        suspended:       true,
        suspendedReason: `Content report ${id}: ${String(report.reason || "")}`.slice(0, 300),
        suspendedAt:     Date.now(),
        suspendedBy:     appUser.uid,
      },
      { merge: true }
    );
    await logAdminAction(appUser, "SUSPEND_USER", {
      targetUid: ownerUid,
      reason: `content report ${id}`,
    });
  }

  // Close this report and every other one filed against the same content.
  const siblings = await db
    .collection("content_reports")
    .where("targetType", "==", report.targetType)
    .where("targetId", "==", report.targetId)
    .where("status", "==", "OPEN")
    .limit(200)
    .get();

  const batch = db.batch();
  const closed = {
    status: "REVIEWED",
    actionTaken: action,
    resolvedBy: appUser.uid,
    resolvedAt: Date.now(),
  };
  batch.set(reportRef, closed, { merge: true });
  for (const doc of siblings.docs) {
    if (doc.id !== id) batch.set(doc.ref, closed, { merge: true });
  }
  await batch.commit();

  await logAdminAction(appUser, "RESOLVE_CONTENT_REPORT", {
    reportId: id,
    action,
    targetType: report.targetType,
    targetId: report.targetId,
  });

  return { ok: true, closed: siblings.size || 1 };
});
