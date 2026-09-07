// bookings — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { normalizeBookingCode } = require("../lib/booking");
const { slotFit } = require("../lib/hours");
const { cashLedgerDelta, onlineLedgerDelta, shouldReverseCommission } = require("../lib/commission");
const { expandBooked, hasSlotConflict } = require("../lib/slots");
const { slotConflictWindow } = require("../lib/reservation");
const { UNCONFIRMED_NUDGE_AFTER_MS, isAdminDue, isNudgeDue, unconfirmedDeadline } = require("../lib/unconfirmed");
const { isValidDocId } = require("../lib/validate");
const { assertAdmin, assertDocId, assertNotSuspended, enforceRateLimit, idPage, logAdminAction, logAppointmentEvent, pageCursor, pageEnd, refundReservation, reserveBookingCode, resolveAppUser, writeAppointmentEvent } = require("../shared");
const { averageRating } = require("../lib/reviews");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { canReportVisit, isVisitReason, visitPlan } = require("../lib/visitreport");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { countsAsConfirmed, statsDelta, isNoOp } = require("../lib/salonstats");
const { admin, alertable, db, logger } = require("../shared");

// Kabul-local calendar day for a timestamp.
//
// The client writes requestedDate as device-local (Kabul, UTC+4:30) midnight
// while this code runs in UTC, so comparing UTC midnight to Kabul midnight never
// matched and the waitlist promotion silently never fired. Afghanistan has no
// DST, so a fixed offset is exact.
//
// At module scope because two separate jobs now need it, and a copy in each is
// how two definitions of "the same day" start disagreeing.
const KABUL_OFFSET_MS = 4.5 * 3600 * 1000;

const kabulDay = (ts) => Math.floor((Number(ts) + KABUL_OFFSET_MS) / 86400000);

// ── cancelPaidAppointment (shared helper) ─────────────────────────────────────
//
// Every appointment that reaches PENDING or CONFIRMED has already been paid
// (the webhook only flips AWAITING_PAYMENT -> PENDING after payment succeeds),
// so ANY cancellation — by the customer OR by the provider declining — is a
// paid-booking cancellation. The old client paths just flipped status to
// CANCELLED via a direct Firestore write and never touched the payment —
// money taken, no refund record, no one notified. This shared transaction
// cancels the appointment AND creates a refund request the admin must action
// (HesabPay has no automated refund API wired), used by both
// cancelAppointment (customer) and providerDeclineAppointment (provider).
//
// `cancelledBy` is "CUSTOMER" or "PROVIDER" — it decides who gets notified
// (the other party) and who is authorized to act, via `authorize(appt, payment)`.
async function cancelPaidAppointment(appointmentId, cancelledBy, authorize, actor = null, reason = "") {
  const apptRef = db.doc(`appointments/${appointmentId}`);

  const result = await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();
    if (appt.status !== "PENDING" && appt.status !== "CONFIRMED") {
      throw new HttpsError("failed-precondition", "This booking can no longer be cancelled.", { reason: "CANCEL_TOO_LATE" });
    }

    // AppointmentDocument has no providerId field — it lives on the payment
    // (and salon) record, so the payment must be read before authorization.
    const paySnap = await tx.get(
      db.collection("payments").where("appointmentId", "==", appointmentId).limit(1)
    );
    const payDoc  = paySnap.empty ? null : paySnap.docs[0];
    const payment = payDoc ? payDoc.data() : null;

    if (!authorize(appt, payment)) {
      throw new HttpsError("permission-denied", "Not authorized to cancel this booking.");
    }

    tx.update(apptRef, { status: "CANCELLED" });
    writeAppointmentEvent(
      tx, appt, appointmentId, "CANCELLED",
      actor || { uid: "system", role: cancelledBy, name: "" },
      reason || (cancelledBy === "PROVIDER"
        ? "Salon declined the booking"
        : cancelledBy === "CUSTOMER"
          ? "Cancelled by the customer"
          : "Cancelled automatically")
    );

    let refundRequestId = null;
    const providerId = payment ? (payment.providerId || "") : "";
    if (payment && payment.method === "CASH") {
      // No online money ever moved, so there's nothing to refund — just undo
      // the commission debt that was charged to the provider at booking time.
      tx.update(payDoc.ref, { status: "CANCELLED", commissionReversed: true });
      // commissionReversed guards the one overlap: a salon that reports a
      // no-show (reportCustomer gives the commission back) and then cancels the
      // same booking would otherwise be credited for it twice.
      if (providerId && payment.commissionReversed !== true) {
        tx.set(
          db.doc(`provider_balances/${providerId}`),
          {
            providerId,
            // Exactly the negation of what booking it did. The commission goes
            // back, and so does the wallet-credit portion the platform was
            // covering — nothing happened, so neither side owes the other.
            owedAmount: admin.firestore.FieldValue.increment(-cashLedgerDelta(payment)),
            updatedAt:  Date.now(),
          },
          { merge: true }
        );
      }
    } else if (payment && payment.status === "PAID") {
      tx.update(payDoc.ref, { status: "REFUND_PENDING" });
      const refundRef = db.collection("refund_requests").doc();
      tx.set(refundRef, {
        appointmentId,
        paymentId:   payDoc.id,
        customerId:  appt.customerId,
        providerId,
        salonId:     appt.salonId,
        amount:      payment.amount,
        status:      "PENDING",
        createdAt:   Date.now(),
      });
      refundRequestId = refundRef.id;

      // Reverse the provider's owed balance if it was already credited
      // (it is, as soon as the webhook marked this payment PAID). Guarded:
      // an empty providerId would make db.doc() throw and every cancel of
      // such a booking fail outright.
      if (providerId) {
        tx.set(
          db.doc(`provider_balances/${providerId}`),
          {
            providerId,
            // The negation of what settlement credited, wallet portion included.
            owedAmount: admin.firestore.FieldValue.increment(-onlineLedgerDelta(payment)),
            updatedAt:  Date.now(),
          },
          { merge: true }
        );
      }
    }

    // Notify whichever party didn't initiate the cancellation.
    if (cancelledBy === "CUSTOMER" && providerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "BOOKING_CANCELLED",
        msgKey:      "BOOKING_CANCELLED_BY_CUSTOMER",
        msgParams:   { service: appt.serviceName || "A booking" },
        title:       "Booking Cancelled",
        body:        `${appt.serviceName || "A booking"} was cancelled by the customer.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    } else if (cancelledBy === "PROVIDER" && appt.customerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: appt.customerId,
        type:        "BOOKING_CANCELLED",
        msgKey:      "BOOKING_DECLINED",
        msgParams:   { service: appt.serviceName || "Your booking", salon: appt.salonName || "the salon" },
        title:       "Booking Declined",
        body:        `${appt.serviceName || "Your booking"} at ${appt.salonName || "the salon"} was declined.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    }

    return { refundRequestId, appt, payment };
  });

  // Refund the referral credit + promo use that were reserved atomically at
  // checkout (both online and cash bookings are written reserved:true). The
  // refund_requests row above only covers the online-paid `amount`; the wallet
  // credit portion lives on the user doc and must be handed back separately, or
  // a customer loses it on every cancel/decline of a credit-assisted booking.
  if (result.payment && result.payment.reserved && !result.payment.type) {
    await refundReservation({
      customerId:   result.appt.customerId,
      referralUsed: result.payment.referralUsed,
      promoId:      result.payment.promoCode,
    });
  }

  // Release the freed slot to the next waitlisted customer (best-effort,
  // outside the transaction since it's a separate, non-critical write).
  // Mirrors FirestoreRepository.notifyFirstWaiting's single-field query +
  // in-memory filter so no new composite index is required.
  try {
    // Match by KABUL-local day, not exact millis: the client writes requestedDate
    // as device-local (Kabul, UTC+4:30) midnight while this code runs in UTC —
    // comparing UTC midnight to Kabul midnight never matched, so the promotion
    // silently never fired. Afghanistan has no DST, so a fixed offset is exact.
    const wantedDay = kabulDay(result.appt.appointmentDate);

    const entries = await db.collection("waitlist")
      .where("salonId", "==", result.appt.salonId)
      .get();
    const first = entries.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .filter((w) => w.status === "WAITING" && kabulDay(w.requestedDate) === wantedDay)
      .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))[0];
    if (first) {
      // offeredAt is what lets rotateWaitlistOffers age an unclaimed offer and
      // pass it on. Without it the entry has no clock and the queue stalls.
      await db.doc(`waitlist/${first.id}`).update({
        status: "SLOT_AVAILABLE",
        offeredAt: Date.now(),
      });
      // Also tell the customer directly — the doc flip alone was invisible until
      // they happened to open the bookings sheet. The notifications trigger turns
      // this into an FCM push, and the Notification Center's Waitlist filter
      // (type "WAITLIST") finally has a producer.
      if (first.customerId) {
        await db.collection("notifications").add({
          recipientId: first.customerId,
          type:        "WAITLIST",
          msgKey:      "WAITLIST_SLOT",
          msgParams:   { salon: first.salonName || "A salon" },
          title:       "A slot opened up 🎉",
          body:        `${first.salonName || "A salon"} has a free slot on your waitlisted day — book it before it's gone!`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   first.salonId || "",
        });
      }
    }
  } catch (err) {
    logger.error("cancelPaidAppointment: waitlist notify failed (non-fatal)", err);
  }

  return { cancelled: true, refundRequestId: result.refundRequestId };
}

// ── cancelAppointment (callable, customer) ────────────────────────────────────
exports.cancelAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId } = request.data || {};
  if (!appointmentId) {
    throw new HttpsError("invalid-argument", "appointmentId is required.");
  }
  const appUser = await resolveAppUser(request);
  return cancelPaidAppointment(appointmentId, "CUSTOMER", (appt) =>
    appt.customerId === appUser.uid,
    { uid: appUser.uid, role: "CUSTOMER", name: appUser.name }
  );
});

// ── providerDeclineAppointment (callable, provider) ───────────────────────────
//
// Replaces ProviderViewModel.declineAppointment's direct Firestore write,
// which had the exact same gap as the old customer-cancel path: a PENDING
// appointment is already paid, and a plain status flip to CANCELLED left that
// payment marked PAID forever with no refund trail.
exports.providerDeclineAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId } = request.data || {};
  if (!appointmentId) {
    throw new HttpsError("invalid-argument", "appointmentId is required.");
  }
  const appUser = await resolveAppUser(request);
  return cancelPaidAppointment(appointmentId, "PROVIDER", (appt, payment) =>
    !!payment && payment.providerId === appUser.uid,
    { uid: appUser.uid, role: "PROVIDER", name: appUser.name }
  );
});

// ── getBookedSlots (callable) ─────────────────────────────────────────────────
//
// Returns only the taken time-slots for a salon within a day window. The
// client must not read other customers' appointment docs (they carry names
// and phone numbers), and the rules rightly deny such a query — which
// silently broke the client-side availability check: the denied read came
// back empty, so every slot looked free and double-booking was possible.
exports.getBookedSlots = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { salonId, dayStart, dayEnd } = request.data || {};
  const start = Number(dayStart);
  const end   = Number(dayEnd);
  if (!salonId || !Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
    throw new HttpsError("invalid-argument", "salonId, dayStart and dayEnd are required.");
  }

  const salonSnap = await db.doc(`salons/${salonId}`).get();
  const slotMinutes = Number((salonSnap.exists ? salonSnap.data().slotDurationMinutes : 0)) || 60;

  // Bounded to the day being shown. This read used to fetch every appointment the
  // salon had ever taken and then keep the ones falling in the window — the third
  // door onto the same defect P1 closed in createPaymentSession, and the one a
  // customer hits most often, since it runs every time she opens a date.
  //
  // The lower bound is a day early rather than exactly dayStart, so a long
  // booking that began the previous evening is still read. The in-memory filter
  // below is unchanged, so the set returned is identical to before — this is a
  // cost fix, not a behaviour change.
  const snap = await db.collection("appointments")
    .where("salonId", "==", salonId)
    .where("appointmentDate", ">=", start - 24 * 60 * 60 * 1000)
    .where("appointmentDate", "<=", end)
    .get();
  const inWindow = snap.docs
    // The id comes along so the picker can leave out the booking being moved,
    // exactly as hasSlotConflict does server-side.
    .map((d) => ({ id: d.id, ...d.data() }))
    .filter((a) =>
      a.status !== "CANCELLED" &&
      Number(a.appointmentDate) >= start &&
      Number(a.appointmentDate) <= end);

  // A multi-service / group booking occupies several back-to-back slots, so expand
  // each appointment into every slot it actually takes (see lib/slots.js, tested).
  // `slots` is the legacy plain-times shape; `booked` tags each with its staff so
  // the client only treats a time as full when every active staff member is taken.
  const { slots, booked } = expandBooked(inWindow, slotMinutes);

  return { slots, booked };
});

// ── rescheduleAppointment (callable, customer) ────────────────────────────────
//
// Replaces the customer's direct Firestore write, which the rules had no
// customer branch for (appointment updates were provider/admin-only) — so
// rescheduling always failed, silently. Runs server-side like every other
// appointment mutation: validates ownership + status, moves the date, drops
// the booking back to PENDING for re-confirmation, and notifies the provider.
exports.rescheduleAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId, newDate } = request.data || {};
  const dateMs = Number(newDate);
  if (!appointmentId || !Number.isFinite(dateMs)) {
    throw new HttpsError("invalid-argument", "appointmentId and newDate are required.");
  }
  if (dateMs < Date.now()) {
    throw new HttpsError("invalid-argument", "The new time must be in the future.");
  }
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  const apptRef = db.doc(`appointments/${appointmentId}`);

  await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();

    if (appt.customerId !== appUser.uid && appUser.role !== "ADMIN") {
      throw new HttpsError("permission-denied", "Not authorized to reschedule this booking.");
    }
    if (appt.status !== "PENDING" && appt.status !== "CONFIRMED") {
      throw new HttpsError("failed-precondition", "This booking can no longer be rescheduled.", { reason: "RESCHEDULE_TOO_LATE" });
    }

    const salonSnap  = await tx.get(db.doc(`salons/${appt.salonId}`));
    const salon      = salonSnap.exists ? salonSnap.data() : {};
    const providerId = salon.providerId || "";

    // Reject a move onto a day the salon blocked off (defense in depth; the
    // client hides them). Kabul-local "yyyy-MM-dd", matching createPaymentSession.
    const blocked = Array.isArray(salon.blockedDates) ? salon.blockedDates : [];
    if (blocked.length > 0) {
      const day = new Date(dateMs).toLocaleDateString("en-CA", { timeZone: "Asia/Kabul" });
      if (blocked.includes(day)) {
        throw new HttpsError("failed-precondition", "The salon is closed on that day.", { reason: "SALON_CLOSED" });
      }
    }

    // Reject a move that collides with another live booking on the same chair.
    // Read inside the transaction (all reads precede the write) so two concurrent
    // reschedules can't both land on the same slot. Exclude this booking so it
    // never conflicts with its own current slot.
    const slotMinutes = Number(salon.slotDurationMinutes) || 60;
    const span = Math.max(
      1,
      Number(appt.slotsCount) ||
        (Array.isArray(appt.services) ? appt.services.length : 0) ||
        1
    );
    //
    // Bounded to the requested time's neighbourhood, exactly as
    // createPaymentSession is. Unbounded, this read every appointment the salon
    // had ever taken — and did it inside a transaction, which Firestore may
    // retry, so a busy salon paid its whole history again on every retry. The
    // booking path was fixed in P1 and this one was missed: same defect, second
    // door. A conflict can only involve an appointment near the new time, so
    // nothing outside the window can change the answer.
    const conflictWindow = slotConflictWindow(dateMs);
    const otherSnap = await tx.get(
      db.collection("appointments")
        .where("salonId", "==", appt.salonId)
        .where("appointmentDate", ">=", conflictWindow.start)
        .where("appointmentDate", "<", conflictWindow.end)
    );
    const others = otherSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    // The offsets the booking was made with, not a recomputed span: if the salon
    // has since changed that service's timing, this booking still occupies what
    // it occupied when the customer made it.
    const busy = Array.isArray(appt.busyOffsets) && appt.busyOffsets.length
      ? appt.busyOffsets
      : span;
    // A party keeps taking the whole salon when it moves. Rescheduling one onto a
    // day the salon is otherwise busy has to be refused for the same reason
    // booking it did.
    if (hasSlotConflict(others, dateMs, busy, String(appt.staffId || ""), slotMinutes,
                        appointmentId, appt.isParty === true)) {
      throw new HttpsError("failed-precondition", "That time is no longer available.", { reason: "SLOT_TAKEN" });
    }

    // And on the grid the salon actually keeps. hasSlotConflict cannot see this:
    // an off-grid time between two bookings collides with neither, so a booking
    // could be moved to 03:17 on a Friday and the conflict check would agree.
    // `span`, not `busy`. busyOffsets is where the stylist is working; the
    // customer is in the chair for the whole span, and that is what has to fit
    // before closing.
    const fit = slotFit(salon, dateMs, span);
    if (!fit.ok) {
      // Say which way it is wrong. "The salon is not open at that time" for an
      // off-grid 14:30 is false — she is open at 14:30, the appointment simply
      // does not start there — and a wrong reason sends the customer looking in
      // the wrong place. The app now offers only the salon's real free times, so
      // reaching any of these means the two disagreed and that is worth knowing
      // from the message alone.
      const message = {
        SALON_CLOSED:   "The salon is closed on that day.",
        BEFORE_OPENING: "That is before the salon opens.",
        AFTER_CLOSING:  "The appointment would not finish before the salon closes.",
        OFF_GRID:       "The salon does not start appointments at that time.",
        BAD_TIME:       "That is not a valid time.",
      }[fit.reason] || "The salon is not available at that time.";
      throw new HttpsError("failed-precondition", message, { reason: fit.reason });
    }

    // Back to PENDING means the wait for the salon's agreement starts over, and
    // every flag that tracks that wait has to start over with it. Without this,
    // the hourly sweep judged the new booking by the old one's clock: it was
    // already nudged, already escalated, and already past its 24h deadline, so
    // the next run cancelled and refunded an appointment the customer had just
    // successfully moved.
    tx.update(apptRef, {
      appointmentDate: dateMs,
      status:          "PENDING",
      reminderSent:    false,
      pendingSince:    Date.now(),
      providerNudged:  false,
      adminAlerted:    false,
    });
    writeAppointmentEvent(tx, appt, appointmentId, "PENDING", appUser,
      `Rescheduled to ${new Date(dateMs).toISOString()}`);
    if (providerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "BOOKING_RESCHEDULED",
        msgKey:      "BOOKING_RESCHEDULED",
        msgParams:   { service: appt.serviceName || "A booking" },
        title:       "Booking Rescheduled",
        body:        `${appt.serviceName || "A booking"} was moved to a new time — please re-confirm.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    }
  });

  return { rescheduled: true };
});

// ── confirmAppointment (callable, provider) ───────────────────────────────────
//
// Replaces ProviderViewModel.acceptAppointment's four separate client writes
// (status flip, confirmedCount increment, loyalty points, notification) with
// one atomic transaction. Also required so `notifications` creation could be
// locked to the Admin SDK — with real FCM pushes now sent for every
// notification doc, an open client create rule would let any signed-in user
// push arbitrary text to any user's phone.
exports.confirmAppointment = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { appointmentId } = request.data || {};
  if (!appointmentId) {
    throw new HttpsError("invalid-argument", "appointmentId is required.");
  }
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  const apptRef = db.doc(`appointments/${appointmentId}`);

  await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();

    if (appt.status === "CONFIRMED") return; // idempotent re-tap
    if (appt.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Only a pending booking can be confirmed.", { reason: "NOT_PENDING" });
    }

    const salonRef  = db.doc(`salons/${appt.salonId}`);
    const salonSnap = await tx.get(salonRef);
    const salon     = salonSnap.exists ? salonSnap.data() : null;
    if ((!salon || salon.providerId !== appUser.uid) && appUser.role !== "ADMIN") {
      throw new HttpsError("permission-denied", "Not authorized to confirm this booking.");
    }

    // The customer doc can be missing (e.g. legacy data written under a
    // different uid scheme) — never let that block the confirmation itself.
    let customerRef = null;
    let customerExists = false;
    if (appt.customerId) {
      customerRef = db.doc(`users/${appt.customerId}`);
      customerExists = (await tx.get(customerRef)).exists;
    }

    tx.update(apptRef, { status: "CONFIRMED" });
    writeAppointmentEvent(tx, appt, appointmentId, "CONFIRMED",
      { uid: appUser.uid, role: "PROVIDER", name: appUser.name }, "Salon accepted the booking");
    if (salonSnap.exists) {
      tx.update(salonRef, {
        confirmedCount: admin.firestore.FieldValue.increment(1),
      });
    }
    if (customerExists) {
      tx.update(customerRef, {
        loyaltyPoints: admin.firestore.FieldValue.increment(10),
      });
    }
    if (appt.customerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: appt.customerId,
        type:        "BOOKING_CONFIRMED",
        msgKey:      "BOOKING_CONFIRMED",
        msgParams:   { service: appt.serviceName || "Your booking", salon: appt.salonName || "the salon" },
        title:       "Booking Confirmed",
        body:        `${appt.serviceName || "Your booking"} at ${appt.salonName || "the salon"}`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    }
  });

  return { confirmed: true };
});

// ── reportCustomer (callable, provider) ───────────────────────────────────────
//
// A provider's post-appointment feedback about a customer — the other half of
// the two-way rating system. One report per appointment: an optional 1–5 star
// rating, an optional no-show flag, and an optional escalation to admin for
// misconduct. Every change is bound to a real appointment the provider owns and
// guarded against duplicates, so a provider can't repeatedly tank a customer's
// reputation. The aggregates live on the customer's user doc (server-only) and
// are shown to providers on future booking requests.
exports.reportCustomer = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  if (appUser.role !== "PROVIDER") {
    throw new HttpsError("permission-denied", "Only providers can rate customers.");
  }

  const d = request.data || {};
  const appointmentId = String(d.appointmentId || "");
  const rating  = Math.max(0, Math.min(5, Math.floor(Number(d.rating || 0))));
  const noShow  = d.noShow === true;
  const flagged = d.flagged === true;
  const comment = String(d.comment || "").trim().slice(0, 500);
  if (!appointmentId) throw new HttpsError("invalid-argument", "appointmentId is required.");
  if (rating === 0 && !noShow && !flagged) {
    throw new HttpsError("invalid-argument", "Give a rating, mark a no-show, or flag an issue.");
  }

  const apptRef = db.doc(`appointments/${appointmentId}`);

  const result = await db.runTransaction(async (tx) => {
    const apptSnap = await tx.get(apptRef);
    if (!apptSnap.exists) throw new HttpsError("not-found", "Appointment not found.");
    const appt = apptSnap.data();

    // Only the provider who owns this salon may report, and only once per booking.
    const salonSnap = await tx.get(db.doc(`salons/${appt.salonId}`));
    const providerId = salonSnap.exists ? (salonSnap.data().providerId || "") : "";
    if (providerId !== appUser.uid) {
      throw new HttpsError("permission-denied", "Not your booking to review.");
    }
    if (appt.customerReported === true) {
      throw new HttpsError("failed-precondition", "You've already reviewed this booking.", { reason: "ALREADY_REVIEWED" });
    }
    // Feedback only makes sense once a booking was actually accepted/served.
    // COMPLETED is included because completePastAppointments auto-flips
    // CONFIRMED → COMPLETED ~2h after the start time — which is exactly when a
    // provider sits down to report a no-show, so excluding it would kill the
    // primary post-visit reporting window.
    if (appt.status !== "CONFIRMED" && appt.status !== "PENDING" && appt.status !== "COMPLETED") {
      throw new HttpsError("failed-precondition", "This booking can't be reviewed.", { reason: "NOT_REVIEWABLE" });
    }

    // Read before any write — a transaction allows no read after one. Needed
    // only for a no-show, but a Firestore transaction cannot fetch it later.
    const paySnap = noShow
      ? await tx.get(db.collection("payments").where("appointmentId", "==", appointmentId).limit(1))
      : null;
    const payDoc  = paySnap && !paySnap.empty ? paySnap.docs[0] : null;
    const payment = payDoc ? payDoc.data() : null;

    tx.update(apptRef, { customerReported: true });
    writeAppointmentEvent(tx, appt, appointmentId, appt.status,
      { uid: appUser.uid, role: "PROVIDER", name: appUser.name },
      noShow ? "Salon reported a no-show" : `Salon rated the customer ${rating}/5`);

    const reportRef = db.collection("customer_reports").doc();
    tx.set(reportRef, {
      appointmentId,
      customerId:   appt.customerId,
      customerName: appt.customerName || "",
      providerId:   appUser.uid,
      salonId:      appt.salonId,
      salonName:    appt.salonName || "",
      rating,
      noShow,
      flagged,
      comment,
      status:       flagged ? "OPEN" : "REVIEWED",
      createdAt:    Date.now(),
    });

    // Fold into the customer's reputation aggregates (server-controlled).
    if (appt.customerId) {
      const agg = {};
      if (rating > 0) {
        agg.customerRatingSum   = admin.firestore.FieldValue.increment(rating);
        agg.customerRatingCount = admin.firestore.FieldValue.increment(1);
      }
      if (noShow) {
        agg.noShowCount = admin.firestore.FieldValue.increment(1);
      }
      if (Object.keys(agg).length > 0) {
        tx.set(db.doc(`users/${appt.customerId}`), agg, { merge: true });
      }
    }

    // A cash no-show means no cash. The commission was debited to the salon at
    // booking time (createPaymentSession), on the assumption the customer would
    // arrive and pay — so a salon that was stood up was left owing the platform
    // a cut of money it never received. Give it back, exactly as cancelling the
    // same booking already does.
    //
    // Online payments are deliberately untouched: that money did change hands,
    // the slot was held, and whether the customer gets it back is the refund
    // flow's decision, not this one's.
    let commissionReversed = false;
    if (payDoc && shouldReverseCommission(noShow, payment)) {
      tx.update(payDoc.ref, { status: "NO_SHOW", commissionReversed: true });
      tx.set(
        db.doc(`provider_balances/${payment.providerId}`),
        {
          providerId: payment.providerId,
          // The negation of the cash booking's own entry. The commission comes
          // back because no money was collected to take a cut of, and the wallet
          // portion goes back to the platform because the appointment never
          // happened — she held the slot, but nobody was served.
          owedAmount: admin.firestore.FieldValue.increment(-cashLedgerDelta(payment)),
          updatedAt:  Date.now(),
        },
        { merge: true }
      );
      commissionReversed = true;
    }
    return { reportId: reportRef.id, commissionReversed };
  });

  return { reported: true, reportId: result.reportId, commissionReversed: result.commissionReversed };
});

// ── sendBookingReminders (scheduled) ──────────────────────────────────────────
//
// Confirmed appointments starting within the next ~2 hours get a one-time
// reminder notification (reused notifications → FCM pipeline, same as every
// other notification in this file). reminderSent starts false at booking
// time (see createPaymentSession) and is reset to false on reschedule (see
// rescheduleAppointment) — flipping it to true here makes a slow or retried
// run idempotent instead of double-sending.
const REMINDER_WINDOW_MS = 2 * 60 * 60 * 1000;

exports.sendBookingReminders = onSchedule(
  { schedule: "every 15 minutes", region: "us-central1" },
  async () => {
    const now       = Date.now();
    const windowEnd = now + REMINDER_WINDOW_MS;

    const upcoming = await db.collection("appointments")
      .where("status", "==", "CONFIRMED")
      .where("reminderSent", "==", false)
      .where("appointmentDate", "<=", windowEnd)
      .get();

    if (upcoming.empty) return;

    let count = 0;
    for (const doc of upcoming.docs) {
      const appt = doc.data();
      // Already in the past (e.g. this function was down for a while) —
      // nothing useful to remind about; just stop it from being re-scanned.
      if (appt.appointmentDate < now) {
        await doc.ref.update({ reminderSent: true });
        continue;
      }
      const batch = db.batch();
      batch.update(doc.ref, { reminderSent: true });
      if (appt.customerId) {
        batch.set(db.collection("notifications").doc(), {
          recipientId: appt.customerId,
          type:        "BOOKING_REMINDER",
          msgKey:      "BOOKING_REMINDER",
          msgParams:   { service: appt.serviceName || "Your appointment", salon: appt.salonName || "the salon" },
          title:       "Upcoming Appointment",
          body:        `${appt.serviceName || "Your appointment"} at ${appt.salonName || "the salon"} is coming up soon.`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   doc.id,
        });
      }
      await batch.commit();
      count++;
    }
    logger.log(`sendBookingReminders: reminded ${count} upcoming appointment(s)`);
  }
);

// A CONFIRMED appointment whose time has passed (plus a grace window so one that's
// literally happening now isn't closed early) is finished — flip it to COMPLETED.
// This gives "past" a real status instead of only being inferred from the date, so
// review prompts, "book again", and provider stats read a booking as done. Once
// flipped it no longer matches status=="CONFIRMED", so the scanned set stays small
// and no composite index is needed (single-field equality + in-code date filter).
const COMPLETE_GRACE_MS = 2 * 60 * 60 * 1000;

exports.completePastAppointments = onSchedule(
  { schedule: "every 60 minutes", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - COMPLETE_GRACE_MS;
    const confirmed = await db.collection("appointments")
      .where("status", "==", "CONFIRMED")
      .get();
    if (confirmed.empty) return;

    let count = 0;
    for (const doc of confirmed.docs) {
      const a = doc.data();
      if (Number(a.appointmentDate) <= cutoff) {
        const now = Date.now();
        await doc.ref.update({ status: "COMPLETED", completedAt: now });
        await logAppointmentEvent({ ...doc.data(), status: "CONFIRMED" }, doc.id, "COMPLETED",
          null, "Visit time passed without a cancellation");
        // Stamp the customer's last visit — a cheap recency signal the
        // re-engagement nudge reads instead of scanning appointment history.
        if (a.customerId) {
          await db.doc(`users/${a.customerId}`).set({ lastVisitAt: now }, { merge: true });
        }
        count++;
      }
    }
    logger.log(`completePastAppointments: completed ${count} appointment(s)`);
  }
);

// ── adminLookupBooking ────────────────────────────────────────────────────────
//
// Everything known about one booking, from a code a customer can read down the
// phone. Assembled server-side because the pieces live in five collections the
// admin console would otherwise have to query separately, and because two of
// them (payments, refund_requests) are not client-readable at all.
//
// Accepts either the booking code or the raw document id, so a support call and
// a log line both lead to the same place.
exports.adminLookupBooking = onCall({ region: "us-central1" }, async (request) => {
  await assertAdmin(request);
  const raw = String((request.data || {}).code || "").trim();
  if (!raw) throw new HttpsError("invalid-argument", "A booking code is required.");

  // Typed by a human off a screen or read down a phone line: lower case, a
  // missing prefix and stray punctuation all resolve to the same code. Anything
  // that could not be a code normalizes to "" and skips the query entirely.
  const code = normalizeBookingCode(raw);

  let apptDoc = null;
  if (code) {
    const snap = await db.collection("appointments")
      .where("bookingCode", "==", code).limit(1).get();
    apptDoc = snap.empty ? null : snap.docs[0];
  }

  // Fall back to the raw document id, so a value copied out of a log line finds
  // the same booking a customer's code does. Validated first: an unchecked
  // string interpolated into a path throws a 500 on the first stray slash.
  if (!apptDoc && isValidDocId(raw)) {
    const byId = await db.doc(`appointments/${raw}`).get();
    if (byId.exists) apptDoc = byId;
  }
  if (!apptDoc) throw new HttpsError("not-found", "No booking with that reference.");

  const appointmentId = apptDoc.id;
  const appt = apptDoc.data();

  const [eventsSnap, paySnap, refundSnap, reportSnap, reviewSnap] = await Promise.all([
    db.collection("appointment_events").where("appointmentId", "==", appointmentId)
      .orderBy("at", "asc").get(),
    db.collection("payments").where("appointmentId", "==", appointmentId).get(),
    db.collection("refund_requests").where("appointmentId", "==", appointmentId).get(),
    db.collection("customer_reports").where("appointmentId", "==", appointmentId).get(),
    db.collection("reviews").where("appointmentId", "==", appointmentId).get(),
  ]);

  const rows = (q) => q.docs.map((d) => ({ id: d.id, ...d.data() }));

  return {
    appointment: { id: appointmentId, ...appt },
    events:      rows(eventsSnap),
    payments:    rows(paySnap),
    refunds:     rows(refundSnap),
    reports:     rows(reportSnap),
    reviews:     rows(reviewSnap),
  };
});

// ── adminBackfillBookingCodes ─────────────────────────────────────────────────
//
// Bookings made before codes existed have none, so support cannot look them up
// and their history starts mid-story. Walks a page at a time and is safe to run
// repeatedly: it only ever touches appointments with no code, so a second run
// after a timeout continues where the first stopped.
exports.adminBackfillBookingCodes = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const limit = Math.min(400, Math.max(1, Number((request.data || {}).limit || 200)));
  const after = pageCursor(request.data);

  // A missing field cannot be queried for, so this walks the whole collection
  // and skips the ones already done. It walked by creation order with no cursor
  // until now, which re-read the same first 200 bookings on every press — and
  // dropped every booking taken before createdAt existed, which is the same
  // era as the bookings that have no reference. See idPage.
  const snap = await idPage("appointments", limit, after);

  let assigned = 0;
  for (const d of snap.docs) {
    if (d.data().bookingCode) continue;
    const code = await reserveBookingCode();
    await d.ref.update({ bookingCode: code });
    await logAppointmentEvent(
      { ...d.data(), bookingCode: code, status: d.data().status },
      d.id, d.data().status, { uid: me.uid, role: "ADMIN", name: me.name },
      "Reference assigned retroactively"
    );
    assigned += 1;
  }

  await logAdminAction(me, "BACKFILL_CODES", { scanned: snap.size, assigned });
  return { ok: true, scanned: snap.size, assigned, ...pageEnd(snap, limit, after) };
});

// ── rotateWaitlistOffers ──────────────────────────────────────────────────────
//
// When a slot frees, the first person waiting is told and given the chance to
// book it. That is the right shape for a paid marketplace — booking on someone's
// behalf would charge them for an appointment they never chose.
//
// But nothing ever moved the offer on. If that first person did not act, their
// entry sat in SLOT_AVAILABLE permanently: they were never told again about a
// later opening, and nobody behind them in the queue was told at all. One
// unanswered notification stalled the whole waitlist for that salon, silently.
//
// So an unclaimed offer expires and passes to the next person. The window is
// generous — these are women who may see the notification hours later — but it
// is finite, because a queue that never advances is not a queue.
const WAITLIST_OFFER_WINDOW_MS = 6 * 60 * 60 * 1000;

exports.rotateWaitlistOffers = onSchedule(
  { schedule: "every 30 minutes", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - WAITLIST_OFFER_WINDOW_MS;

    const stale = await db.collection("waitlist")
      .where("status", "==", "SLOT_AVAILABLE")
      .where("offeredAt", "<", cutoff)
      .limit(50)
      .get();
    if (stale.empty) return;

    let passed = 0;
    for (const doc of stale.docs) {
      const w = doc.data() || {};
      await doc.ref.update({ status: "EXPIRED", expiredAt: Date.now() });

      // The next person still waiting for the same salon and the same day.
      const queue = await db.collection("waitlist")
        .where("salonId", "==", w.salonId || "")
        .where("status", "==", "WAITING")
        .limit(50)
        .get();

      const next = queue.docs
        .map((d) => ({ id: d.id, ref: d.ref, ...d.data() }))
        .filter((x) => kabulDay(x.requestedDate) === kabulDay(w.requestedDate))
        .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))[0];

      if (!next) continue;

      await next.ref.update({ status: "SLOT_AVAILABLE", offeredAt: Date.now() });
      if (next.customerId) {
        await db.collection("notifications").add({
          recipientId: next.customerId,
          type:        "WAITLIST",
          msgKey:      "WAITLIST_SLOT",
          msgParams:   { salon: next.salonName || "A salon" },
          title:       "A slot opened up 🎉",
          body:        `${next.salonName || "A salon"} has a free slot on your waitlisted day — book it before it's gone!`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   next.salonId || "",
        });
      }
      passed += 1;
    }

    logger.log(`rotateWaitlistOffers: expired ${stale.size}, passed on ${passed}`);
  }
);

exports.nudgeUnconfirmedBookings = onSchedule(
  { schedule: "every 1 hours", region: "us-central1" },
  async () => {
    const now = Date.now();
    // Queried on createdAt, judged on pendingSince. createdAt is on every
    // appointment ever written and pendingSince is not, so querying the newer
    // field would silently skip every booking that predates it — this codebase
    // has shipped that bug before. createdAt <= pendingSince always, so this
    // query is a superset: it can return a rescheduled booking too early, and
    // isNudgeDue/isAdminDue/unconfirmedDeadline are what decline it.
    const cutoff = now - UNCONFIRMED_NUDGE_AFTER_MS;
    const snap = await db.collection("appointments")
      .where("status", "==", "PENDING")
      .where("createdAt", "<", cutoff)
      .get();
    if (snap.empty) return;

    // One notification per provider, however many bookings are waiting —
    // five separate pushes would read as noise and get the app muted.
    //
    // The provider is resolved through the salon, because an appointment does
    // not carry providerId and never has. This job used to read a.providerId
    // directly and skip anything without one, which is every appointment ever
    // written — so the whole nudge, auto-cancel and refund flow had never run
    // for a single booking since it was added. It exists precisely so a
    // customer who has paid is not left waiting indefinitely on a salon that
    // never answers, and that is the case it was silently not covering.
    const providerBySalon = new Map();
    const providerFor = async (salonId) => {
      if (!salonId) return "";
      if (providerBySalon.has(salonId)) return providerBySalon.get(salonId);
      const snap = await db.doc(`salons/${salonId}`).get();
      const pid = snap.exists ? String((snap.data() || {}).providerId || "") : "";
      providerBySalon.set(salonId, pid);
      return pid;
    };

    const byProvider = new Map();
    const orphaned = [];
    for (const d of snap.docs) {
      const a = d.data();
      if (!isNudgeDue(a, now)) continue;         // already chased, or freshly rescheduled
      const providerId = await providerFor(a.salonId);
      if (!providerId) {
        // The salon is gone, or the booking predates salons having owners.
        // Nobody can be nudged, and nobody ever will be: this booking cannot be
        // served, so it is closed rather than left PENDING forever in a
        // customer's list.
        //
        // It is reported the FIRST time only. The comment that used to sit here
        // said an unactionable row reappearing in every sweep is how a report
        // becomes noise — and then this alerted hourly on two June rows whose
        // salon will never exist, which is 48 emails a day teaching someone to
        // ignore the channel that also carries payment failures. Alert on the
        // transition, never on the state.
        if (!a.orphanReported) {
          orphaned.push({ appointmentId: d.id, salonId: a.salonId || "", salonName: a.salonName || "" });
        }
        await d.ref.update({
          status:         "CANCELLED",
          orphanReported: true,
          cancelledAt:    Date.now(),
        });
        await logAppointmentEvent(
          { ...a, status: "PENDING" }, d.id, "CANCELLED", null,
          "Closed automatically: the salon no longer exists, so nobody can serve it"
        );
        continue;
      }
      if (!byProvider.has(providerId)) byProvider.set(providerId, []);
      byProvider.get(providerId).push(d);
    }

    if (orphaned.length) {
      alertable("BOOKING_FAILED", "Bookings closed because their salon no longer exists", {
        count: orphaned.length, orphaned: orphaned.slice(0, 20),
      });
    }
    if (byProvider.size === 0) return;

    let notified = 0;
    for (const [providerId, docs] of byProvider) {
      const allCash = docs.every((x) => x.data().paymentMethod === "CASH");
      const batch = db.batch();
      batch.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "NEW_BOOKING",             // taps route to the requests tab
        // "has paid" is false for a cash booking, and cash bookings are written
        // PENDING like any other, so they reach this sweep too. Only claimed
        // when every booking in the group actually was paid.
        msgKey:      allCash ? "PENDING_BOOKINGS_WAITING_CASH" : "PENDING_BOOKINGS_WAITING",
        msgParams:   { count: docs.length },
        title:       "Bookings waiting for you ⏳",
        body:        allCash
          ? (docs.length === 1
              ? "A customer is waiting for you to confirm their booking."
              : `${docs.length} customers are waiting for you to confirm their bookings.`)
          : (docs.length === 1
              ? "A customer has paid and is waiting for you to confirm their booking."
              : `${docs.length} customers have paid and are waiting for you to confirm their bookings.`),
        isRead:      false,
        createdAt:   now,
        relatedId:   docs[0].id,
      });
      docs.forEach((d) => batch.update(d.ref, { providerNudged: true }));
      try {
        await batch.commit();
        notified++;
      } catch (e) {
        logger.warn("nudgeUnconfirmedBookings: batch failed", { providerId, error: String(e.message || e) });
      }
    }
    logger.log(`nudgeUnconfirmedBookings: nudged ${notified} provider(s)`);

    // ── Stage 2: put a human on it ──────────────────────────────────────────
    // With a handful of salons the admin personally onboarded every owner and
    // has their phone number, so one call converts most of these into a
    // confirmed booking. That is worth far more than a refund.
    const needsAdmin = snap.docs.filter((d) => isAdminDue(d.data(), now));
    if (needsAdmin.length) {
      const admins = await db.collection("users").where("role", "==", "ADMIN").get();
      const batch = db.batch();
      admins.docs.forEach((adminDoc) => {
        batch.set(db.collection("notifications").doc(), {
          recipientId: adminDoc.id,
          type:        "SYSTEM",
          msgKey:      "ADMIN_UNCONFIRMED_BOOKINGS",
          msgParams:   { count: needsAdmin.length },
          title:       "Bookings still unconfirmed",
          body:        `${needsAdmin.length} paid booking(s) have gone unconfirmed for over 6 hours. Contact the salon before they are auto-cancelled.`,
          isRead:      false,
          createdAt:   now,
          relatedId:   needsAdmin[0].id,
        });
      });
      needsAdmin.forEach((d) => batch.update(d.ref, { adminAlerted: true }));
      try {
        await batch.commit();
        logger.log(`nudgeUnconfirmedBookings: alerted admins about ${needsAdmin.length} booking(s)`);
      } catch (e) {
        logger.warn("nudgeUnconfirmedBookings: admin alert failed", e);
      }
    }

    // ── Stage 3: stop waiting, refund, and say so ───────────────────────────
    // Deliberately NOT auto-confirm. The provider never agreed to this booking;
    // sending a customer across Kabul to a salon that is not expecting her is a
    // worse outcome than any refund.
    const expired = snap.docs.filter((d) => now >= unconfirmedDeadline(d.data()));
    let cancelled = 0;
    for (const d of expired) {
      const appt = d.data();
      const isCash = appt.paymentMethod === "CASH";
      try {
        // Reuses the same path as a provider decline, so the refund request,
        // the payment status and the provider's owed balance all unwind exactly
        // as they do for a manual cancellation.
        await cancelPaidAppointment(d.id, "SYSTEM", () => true, null,
          "Salon never confirmed before the deadline");
        await db.collection("notifications").add({
          recipientId: appt.customerId,
          type:        "BOOKING_CANCELLED",
          // A CASH booking took no money, so there is no refund to promise and
          // no refund_requests row behind one. She would have waited for it.
          msgKey:      isCash ? "BOOKING_AUTO_CANCELLED_CASH" : "BOOKING_AUTO_CANCELLED",
          msgParams:   { salon: appt.salonName || "The salon" },
          title:       isCash ? "Booking cancelled" : "Booking cancelled — refund on the way",
          body:        isCash
            ? `${appt.salonName || "The salon"} did not confirm your booking in time, so we cancelled it. You were not charged.`
            : `${appt.salonName || "The salon"} did not confirm your booking in time, so we cancelled it. Your payment is being refunded.`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   d.id,
        });
        cancelled++;
      } catch (e) {
        // A booking the provider confirmed or cancelled in the same window
        // throws failed-precondition here; that is the correct outcome, not an
        // error worth retrying.
        logger.warn("nudgeUnconfirmedBookings: could not auto-cancel", {
          appointmentId: d.id, error: String(e.message || e),
        });
      }
    }
    if (cancelled) {
      logger.log(`nudgeUnconfirmedBookings: auto-cancelled ${cancelled} unconfirmed booking(s)`);
    }
  }
);

// ── deriveSalonStats ─────────────────────────────────────────────────────────
//
// Keeps a running tally of a salon's bookings so the provider's Income tab does
// not have to count them.
//
// That tab shows lifetime totals — bookings by status, bookings by service, and
// the revenue estimate built from the confirmed ones. It got them by reading
// every appointment the salon had ever taken into the phone and counting there,
// which is the one listener in the app a limit could not fix: bounding it would
// not have shortened a list, it would have quietly under-reported a salon
// owner's earnings, and a wrong number that looks right is worse than a slow
// screen.
//
// A trigger rather than an increment at each call site. Status changes happen in
// createPaymentSession, the payment webhook, confirm, decline, cancel, the
// scheduled sweep that completes past bookings, and the admin tools — and a
// counter that is only right when every one of those remembers to update it is a
// counter that will be wrong. Here it sees the before and after of any write,
// whatever made it.
//
// Same shape as confirmedCount on the salon document, which is maintained this
// way already, and as deriveSalonFields.
exports.deriveSalonStats = onDocumentWritten(
  { document: "appointments/{appointmentId}", region: "us-central1" },
  async (event) => {
    const beforeSnap = event.data && event.data.before;
    const afterSnap  = event.data && event.data.after;
    const before = beforeSnap && beforeSnap.exists ? beforeSnap.data() : null;
    const after  = afterSnap  && afterSnap.exists  ? afterSnap.data()  : null;
    if (!before && !after) return;

    // An appointment never moves between salons; a reschedule keeps the same
    // one. Taking the salon from whichever side exists covers create and delete.
    const salonId = String((after || before).salonId || "");
    if (!salonId) return;

    // The arithmetic lives in lib/salonstats, where it is unit-tested. A tally
    // that drifts is invisible: the appointments it was derived from are no
    // longer read, so nothing would ever disagree with it.
    const delta = statsDelta(before, after);
    if (isNoOp(delta)) return;

    const inc = admin.firestore.FieldValue.increment;
    const toIncrements = (bag) => {
      const out = {};
      for (const [k, v] of Object.entries(bag)) out[k] = inc(v);
      return out;
    };
    const patch = {};
    if (delta.total !== 0) patch.total = inc(delta.total);
    if (Object.keys(delta.byStatus).length)  patch.byStatus  = toIncrements(delta.byStatus);
    if (Object.keys(delta.byService).length) patch.byService = toIncrements(delta.byService);
    if (Object.keys(delta.confirmedByService).length) {
      patch.confirmedByService = toIncrements(delta.confirmedByService);
    }

    patch.salonId   = salonId;
    patch.updatedAt = Date.now();

    // merge, so the document is created on the salon's first booking and the
    // map keys are treated as keys rather than as dotted field paths — service
    // names are whatever the salon typed, in any script.
    await db.doc(`salon_stats/${salonId}`).set(patch, { merge: true });
  }
);

// ── adminRebuildSalonStats ───────────────────────────────────────────────────
//
// Recomputes salon_stats from the appointments themselves.
//
// deriveSalonStats keeps the tally current from here on, but a trigger only
// fires on a write: every appointment that already exists has never been seen by
// it, so without this every salon's Income tab reads zero. That is the same
// shape as the phone-key lockout — a derived value that only new writes populate
// — and it is worse here than an empty search, because zero looks like an
// answer. A salon owner has no way to tell "no bookings yet" from "the tally was
// never built".
//
// Recomputes rather than adjusts, so it is safe to run twice, and so a tally
// that has drifted for any reason is repaired rather than compounded.
exports.adminRebuildSalonStats = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);

  const tallies = new Map();   // salonId -> { total, byStatus, byService, confirmedByService }
  const blank = () => ({ total: 0, byStatus: {}, byService: {}, confirmedByService: {} });
  const bump = (bag, key, n) => { if (key) bag[key] = (bag[key] || 0) + n; };

  // Paged, so a platform with more appointments than fit in one read still
  // completes rather than timing out partway and leaving half a tally.
  let cursor = null;
  let scanned = 0;
  for (;;) {
    let q = db.collection("appointments").orderBy("__name__").limit(500);
    if (cursor) q = q.startAfter(cursor);
    const snap = await q.get();
    if (snap.empty) break;
    for (const d of snap.docs) {
      const a = d.data();
      const salonId = String(a.salonId || "");
      if (!salonId) continue;
      if (!tallies.has(salonId)) tallies.set(salonId, blank());
      const t = tallies.get(salonId);
      t.total += 1;
      bump(t.byStatus, String(a.status || ""), 1);
      bump(t.byService, String(a.serviceName || ""), 1);
      if (countsAsConfirmed(a.status)) bump(t.confirmedByService, String(a.serviceName || ""), 1);
    }
    scanned += snap.size;
    cursor = snap.docs[snap.docs.length - 1];
    if (snap.size < 500) break;
  }

  // A tally for a salon that no longer exists is a number nobody can read: the
  // provider is gone, and the Income tab it feeds went with them. Production has
  // four of these — 18 finished bookings across salons that were deleted — and
  // the first run of this created a document for each. Skipped rather than
  // deleted: they are harmless, and quietly removing production data to tidy a
  // count is not this function's business.
  const salonDocs = await db.getAll(
    ...[...tallies.keys()].map((id) => db.doc(`salons/${id}`))
  );
  const alive = new Set(salonDocs.filter((d) => d.exists).map((d) => d.id));

  let written = 0;
  let skipped = 0;
  for (const [salonId, t] of tallies) {
    if (!alive.has(salonId)) { skipped += 1; continue; }
    // Not merged: a recompute replaces the tally outright, so a bucket for a
    // service the salon has since renamed away does not survive as a ghost.
    await db.doc(`salon_stats/${salonId}`).set({
      salonId,
      total:              t.total,
      byStatus:           t.byStatus,
      byService:          t.byService,
      confirmedByService: t.confirmedByService,
      updatedAt:          Date.now(),
    });
    written += 1;
  }

  // The salon's average rating, which nothing repaired.
  //
  // submitReview recomputes salons/{id}.rating on every review it creates, so
  // a salon whose reviews predate that code has rating 0 for good — the exact
  // "derived field only new writes populate" shape this codebase keeps
  // producing. This callable's own comment promises to repair drift "for any
  // reason" and covered only the booking tallies. Recomputed from the reviews
  // themselves, which is the same source submitReview uses.
  let ratingsFixed = 0;
  const salonSnap = await db.collection("salons").select().get();
  for (const salonDoc of salonSnap.docs) {
    const reviews = await db.collection("reviews")
      .where("salonId", "==", salonDoc.id).get();
    if (reviews.empty) continue;
    const avg = averageRating(reviews.docs.map((d) => d.data()));
    await db.doc(`salons/${salonDoc.id}`).set({ rating: avg }, { merge: true });
    ratingsFixed += 1;
  }

  await logAdminAction(me, "REBUILD_SALON_STATS",
    { scanned, salons: written, skipped, ratingsFixed });
  return { ok: true, scanned, salons: written, skipped, ratingsFixed };
});

// ── adminCancelAppointment ───────────────────────────────────────────────────
//
// The one booking action an admin could not take.
//
// confirmAppointment and rescheduleAppointment have always let an admin act —
// both check `role !== "ADMIN"` before refusing — but cancelling went through
// cancelPaidAppointment's authorize predicate, and the two callers that use it
// ask "is this your booking" and "is this your salon". An admin is neither, so
// the person whose job is to sort out a booking nobody else can was the only one
// who could not cancel it. Support's answer was to talk the customer through
// doing it herself, or to ask the salon to decline.
//
// This does not open a new path to the money. It calls the same helper with the
// same transaction: the payment is flagged for refund, the provider's balance is
// unwound, the event trail is written. What changes is who is allowed, and that
// an admin's reason is recorded — a cancellation with no author is the kind of
// thing that later has to be reconstructed from a customer's memory.
exports.adminCancelAppointment = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const { appointmentId, reason } = request.data || {};
  if (!appointmentId) {
    throw new HttpsError("invalid-argument", "appointmentId is required.");
  }
  assertDocId(appointmentId, "appointmentId");

  // A reason is required rather than optional. An admin cancelling somebody's
  // booking is an intervention, and one without a stated cause is indistinguish-
  // able afterwards from a mistake.
  const why = String(reason || "").trim().slice(0, 300);
  if (why.length < 3) {
    throw new HttpsError("invalid-argument", "Say why this booking is being cancelled.");
  }

  const result = await cancelPaidAppointment(
    appointmentId,
    "ADMIN",
    () => true,
    { uid: me.uid, role: "ADMIN", name: me.name || "" },
    why
  );

  await logAdminAction(me, "CANCEL_APPOINTMENT", { appointmentId, reason: why });
  return result;
});

// Exported for requestAccountDeletion, which was cancelling paid bookings with
// a bare status write and therefore never flagging the money. Not a callable —
// index.js does not re-export it, and deploy.sh now skips helper names.
exports.cancelPaidAppointment = cancelPaidAppointment;

// ── reportVisit ───────────────────────────────────────────────────────────────
//
// The other half of reportCustomer.
//
// A salon has been able to rate its customer, mark her a no-show and flag her
// for misconduct since this shipped, and an admin can suspend her over it.
// She had no way to say the visit did not happen. Meanwhile
// completePastAppointments flips a CONFIRMED booking to COMPLETED two hours
// after its start time — so "completed" means the clock passed, not that
// anyone served her, and the platform's commission is booked on that. A salon
// that took an online payment and served nobody kept the money and she was
// left with the support chat.
exports.reportVisit = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  await enforceRateLimit(`visitreport:${appUser.uid}`, 10, 60 * 60 * 1000);

  const d = request.data || {};
  const appointmentId = String(d.appointmentId || "");
  const reason = String(d.reason || "");
  const note = String(d.note || "").trim().slice(0, 500);
  assertDocId(appointmentId, "appointmentId");
  if (!isVisitReason(reason)) throw new HttpsError("invalid-argument", "Unknown reason.");

  const apptRef = db.doc(`appointments/${appointmentId}`);

  const reportId = await db.runTransaction(async (tx) => {
    const snap = await tx.get(apptRef);
    const appt = snap.exists ? snap.data() : null;

    // Every refusal carries its own code. "You cannot report this" with no
    // reason is how a person concludes the app is on the salon's side.
    const verdict = canReportVisit({ appointment: appt, callerUid: appUser.uid, now: Date.now() });
    if (!verdict.ok) {
      if (verdict.why === "NOT_FOUND") throw new HttpsError("not-found", "Booking not found.");
      if (verdict.why === "NOT_YOURS") {
        throw new HttpsError("permission-denied", "That isn't your booking.");
      }
      throw new HttpsError("failed-precondition", "This visit can't be reported.",
        { reason: `VISIT_${verdict.why}` });
    }

    const ref = db.collection("visit_reports").doc();
    tx.set(ref, {
      appointmentId,
      customerId:    appUser.uid,
      customerName:  appUser.name || "",
      salonId:       appt.salonId || "",
      salonName:     appt.salonName || "",
      providerId:    appt.providerId || "",
      serviceName:   appt.serviceName || "",
      bookingCode:   appt.bookingCode || "",
      appointmentDate: Number(appt.appointmentDate || 0),
      amount:        Number(appt.price || appt.amount || 0),
      reason,
      note,
      status:        "OPEN",
      createdAt:     Date.now(),
    });
    // The same one-per-booking flag reportCustomer uses from the other side.
    tx.update(apptRef, { visitReported: true });
    return ref.id;
  });

  await logAppointmentEvent(
    { customerId: appUser.uid }, appointmentId, "VISIT_REPORTED",
    { uid: appUser.uid, role: "CUSTOMER", name: appUser.name },
    `Customer reported the visit (${reason})`
  ).catch(() => {});

  return { ok: true, reportId };
});

// ── resolveVisitReport (admin) ────────────────────────────────────────────────
//
// A queue that cannot do anything is a complaints box. Refunding goes through
// the refund_requests row the rest of this file already uses, so a refund
// decided here is the same refund the finance tab pays out.
exports.resolveVisitReport = onCall({ region: "us-central1" }, async (request) => {
  const appUser = await assertAdmin(request);
  const d = request.data || {};
  const reportId = String(d.reportId || "");
  const action = String(d.action || "");
  if (!reportId) throw new HttpsError("invalid-argument", "reportId is required.");
  const plan = visitPlan(action);
  if (!plan) throw new HttpsError("invalid-argument", "Unknown action.");

  const reportRef = db.doc(`visit_reports/${reportId}`);
  const snap = await reportRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Report not found.");
  const report = snap.data();
  // The same guard resolveContentReport carries: a report already dealt with
  // must not be re-applied by a stale console tab or a retried call.
  if (report.status !== "OPEN") {
    throw new HttpsError("failed-precondition", "This report was already resolved.");
  }

  let refundRequestId = "";
  if (plan.refund && report.appointmentId) {
    // Only against a payment that was actually taken. A cash booking she was
    // never served has no platform money to return — the admin sees the row
    // and settles the commission separately.
    const pays = await db.collection("payments")
      .where("appointmentId", "==", report.appointmentId).limit(1).get();
    const payDoc = pays.empty ? null : pays.docs[0];
    if (payDoc && payDoc.data().status === "PAID") {
      await payDoc.ref.update({ status: "REFUND_PENDING" });
      const refundRef = db.collection("refund_requests").doc();
      await refundRef.set({
        appointmentId: report.appointmentId,
        paymentId:     payDoc.id,
        customerId:    report.customerId || "",
        providerId:    report.providerId || "",
        salonId:       report.salonId || "",
        amount:        Number(payDoc.data().amount || 0),
        reason:        "VISIT_REPORT",
        status:        "PENDING",
        createdAt:     Date.now(),
      });
      refundRequestId = refundRef.id;
    }
  }

  if (plan.suspendSalon && report.providerId) {
    // The shape every other suspension in this codebase writes, so the Manage
    // modal can see it and lift it.
    await db.doc(`users/${report.providerId}`).set({
      status:          "SUSPENDED",
      suspended:       true,
      suspendedReason: `Visit report ${reportId}: ${String(report.reason || "")}`.slice(0, 300),
      suspendedAt:     Date.now(),
      suspendedBy:     appUser.uid,
    }, { merge: true });
    // And the salon comes off the listings, or it keeps taking bookings its
    // owner cannot accept.
    if (report.salonId) {
      await db.doc(`salons/${report.salonId}`)
        .set({ isAvailable: false }, { merge: true }).catch(() => {});
    }
    await logAdminAction(appUser, "SUSPEND_USER", {
      targetUid: report.providerId, reason: `visit report ${reportId}`,
    });
  }

  await reportRef.set({
    status: "REVIEWED",
    actionTaken: action,
    refundRequestId,
    resolvedBy: appUser.uid,
    resolvedAt: Date.now(),
  }, { merge: true });

  await logAdminAction(appUser, "RESOLVE_VISIT_REPORT", { reportId, action, refundRequestId });
  return { ok: true, refundRequestId };
});
