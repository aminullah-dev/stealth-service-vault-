// payments — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { capDiscount, computeCheckout, DEFAULT_MAX_DISCOUNT_FRACTION, lastMinuteDiscount, loyaltyToCredit, offerDiscountFor, packageDiscountFor, promoDiscountFor, resolveServicesTotal, validateGiftAmount } = require("../lib/money");
const { SlotTakenError, commitBookingAtomically, pendingWrites, slotConflictWindow } = require("../lib/reservation");
const { hasSlotConflict, serviceLayout } = require("../lib/slots");
const { cashAllowed } = require("../lib/commitment");
const { LEDGER_VERSION, cashLedgerDelta, onlineLedgerDelta } = require("../lib/commission");
const { slotFit } = require("../lib/hours");
const { normalizeParty, partyServices, partySpan } = require("../lib/party");
const { isValidDocId } = require("../lib/validate");
const { isFailSignal, isPaidSignal, isUnderpaid } = require("../lib/webhook");
const { assertAdmin, assertDocId, assertNotSuspended, findAccountByPhone, logAdminAction, logAppointmentEvent, normalizePhone, refundReservation, reserveBookingCode, resolveAppUser } = require("../shared");
const crypto = require("crypto");
const { defineSecret, defineString } = require("firebase-functions/params");
const { HttpsError, onCall, onRequest } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { admin, alertable, db, logger } = require("../shared");

const HESAB_API_KEY        = defineSecret("HESAB_API_KEY");

const HESAB_WEBHOOK_SECRET = defineSecret("HESAB_WEBHOOK_SECRET");

const HESAB_BASE_URL       = defineString("HESAB_BASE_URL", {
  default: "https://api.hesab.com/api/v1",
});

// Redirect URLs after checkout — the mobile app polls Firestore for status so
// these just need to be valid, reachable URLs; the page content is cosmetic.
// Served by Firebase Hosting (see public/payment/{success,failure}.html).
const HESAB_REDIRECT_BASE = defineString("HESAB_REDIRECT_BASE", {
  default: "https://safebeauty.web.app",
});

// Default commission if the platform_config doc is missing (percent).
const DEFAULT_COMMISSION_PERCENT = 10;

/** The whole config document, for the settings that are not the commission. */
async function getPlatformConfig() {
  const snap = await db.doc("platform_config/general").get();
  return snap.exists ? (snap.data() || {}) : {};
}

/**
 * The share of a booking that discounts may take, from platform_config/general.
 *
 * A number the admin can move, because how deep a promo may cut is a business
 * decision and not one to leave buried in a constant. Out-of-range values fall
 * back to the default rather than being trusted — a 0 there would make every
 * booking free.
 */
async function getMaxDiscountFraction() {
  const snap = await db.doc("platform_config/general").get();
  const value = Number(snap.exists ? snap.data().maxDiscountFraction : undefined);
  // Strictly below 1. A fraction of exactly 1 discounts the whole price away,
  // which is the outcome this cap exists to prevent — so it is refused like any
  // other out-of-range value rather than honoured as a deliberate choice. There
  // is no version of "the salon receives nothing" that is a business decision;
  // a free appointment is a promotion someone has to fund, and that is what
  // wallet credit is for.
  if (!Number.isFinite(value) || value <= 0 || value >= 1) {
    return DEFAULT_MAX_DISCOUNT_FRACTION;
  }
  return value;
}

async function getCommissionPercent() {
  const snap = await db.doc("platform_config/general").get();
  const value = snap.exists ? snap.data().commissionPercent : undefined;
  const percent = Number(value);
  if (!Number.isFinite(percent) || percent < 0 || percent > 100) {
    return DEFAULT_COMMISSION_PERCENT;
  }
  return percent;
}

// Promo codes are stored at promo_codes/{CODE} keyed by the uppercased code, so
// a customer's entered code maps to exactly one document with an O(1) lookup and
// no way to enumerate the whole set. Codes are never exposed to clients — they
// only ever pass a code string here for server-side validation.
//
// Resolves the discount (in AFN) for [codeRaw] against a booking of [priceAfn].
// Throws a friendly HttpsError if the code was given but is invalid/expired/used
// up, so the client can surface exactly why. Returns { discount, promoId, code }.
async function resolvePromoDiscount(codeRaw, priceAfn) {
  const code = String(codeRaw || "").trim().toUpperCase();
  if (!code) return { discount: 0, promoId: null, code: "" };

  const snap = await db.doc(`promo_codes/${code}`).get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "This promo code doesn't exist.");
  }
  const p = snap.data();
  if (p.active === false) {
    throw new HttpsError("failed-precondition", "This promo code is no longer active.");
  }
  if (p.expiresAt && Number(p.expiresAt) > 0 && Date.now() > Number(p.expiresAt)) {
    throw new HttpsError("failed-precondition", "This promo code has expired.");
  }
  const maxUses  = Number(p.maxUses || 0);
  const usedCount = Number(p.usedCount || 0);
  if (maxUses > 0 && usedCount >= maxUses) {
    throw new HttpsError("failed-precondition", "This promo code has reached its usage limit.", { reason: "PROMO_LIMIT" });
  }

  // Percentage takes precedence when both are set; discount can never exceed the
  // price (so the final amount is always >= 0). See lib/money.js (unit-tested).
  const discount = promoDiscountFor(p, priceAfn);

  return { discount, promoId: code, code };
}

function hesabHeaders(apiKey) {
  return {
    "Content-Type":  "application/json",
    "Accept":        "application/json",
    "Authorization": `API-KEY ${apiKey}`,
  };
}

exports.createPaymentSession = onCall(
  { secrets: [HESAB_API_KEY], region: "us-central1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before paying.");
    }

    const appUser = await resolveAppUser(request);
    assertNotSuspended(appUser);
    // Identity must be verified before booking. The client gates this too and
    // routes to the KYC screen; this is the non-bypassable server enforcement.
    if ((appUser.kycStatus || "NONE") !== "APPROVED") {
      throw new HttpsError("failed-precondition", "Verify your identity before booking.");
    }
    const uid     = appUser.uid;
    const user    = appUser;
    const { salonId, serviceName: serviceNameInput, serviceNames, appointmentDate, notes, email, method, promoCode, staffId, packageId, party } =
      request.data || {};
    // A wedding party is a booking for several people at once. It arrives as a
    // guest list rather than a flat service list, so the salon can see who is
    // having what — and so the slot maths can account for everyone working at
    // the same time instead of queueing them onto one stylist.
    const isParty = Array.isArray(party) && party.length > 0;
    const paymentMethod = method === "CASH" ? "CASH" : "ONLINE";

    // Whether this customer may pay at the salon at all. Cash is the pleasant way
    // to book and the one most customers here want; it is also the one that costs
    // a salon a chair and an hour when nobody arrives, because nothing was at
    // stake. noShowCount has been counted since the two-way ratings went in and
    // has never governed anything — this is where it starts to. See lib/commitment.
    if (paymentMethod === "CASH") {
      const verdict = cashAllowed({
        noShowCount: appUser.noShowCount,
        isParty,
        config: await getPlatformConfig(),
      });
      if (!verdict.allowed) {
        throw new HttpsError(
          "failed-precondition",
          verdict.reason === "PARTY"
            ? "A group booking is paid in advance."
            : "This booking must be paid in advance.",
          { reason: verdict.reason, noShowCount: verdict.noShowCount || 0 }
        );
      }
    }

    // The HesabPay secret is only needed for the online path — cash bookings
    // never call out to HesabPay, so a missing/unconfigured key must not block
    // customers who chose to pay in person.
    const apiKey = paymentMethod === "ONLINE" ? HESAB_API_KEY.value() : "";
    if (paymentMethod === "ONLINE" && !apiKey) {
      throw new HttpsError(
        "failed-precondition",
        "Payment is not configured. Set the HESAB_API_KEY secret."
      );
    }

    // Accept either a single serviceName (legacy) or a serviceNames[] array
    // (multi-service and group/wedding bookings). Everything downstream works off
    // the resolved list, so both shapes flow through the exact same path.
    const requestedServiceNames =
      Array.isArray(serviceNames) && serviceNames.length
        ? serviceNames
        : (serviceNameInput ? [serviceNameInput] : []);

    if (!salonId || (requestedServiceNames.length === 0 && !isParty) || !appointmentDate) {
      throw new HttpsError(
        "invalid-argument",
        "salonId, at least one service, and appointmentDate are required."
      );
    }
    // appointmentDate flows into date math (blocked-day, last-minute window, slot
    // conflict) and is stored — reject a non-finite / garbage value up front so it
    // can't produce a NaN downstream or a malformed booking document.
    if (!Number.isFinite(Number(appointmentDate)) || Number(appointmentDate) <= 0) {
      throw new HttpsError("invalid-argument", "A valid appointment time is required.");
    }
    // A booking must be in the future. The normal UI can't produce a past slot,
    // so this only stops a modified client from planting bookings at past
    // timestamps (which would corrupt history/analytics and dodge reminders).
    // 5-minute grace covers clock skew and a slot picked right at its boundary.
    if (Number(appointmentDate) < Date.now() - 5 * 60 * 1000) {
      throw new HttpsError("invalid-argument", "That time is already in the past.");
    }
    // Cap free-text notes so a client can't store an oversized document.
    const safeNotes = String(notes || "").slice(0, 500);

    assertDocId(salonId, "salonId");
    // Read the salon + price server-side so the client can't spoof the amount.
    const salonSnap = await db.doc(`salons/${salonId}`).get();
    if (!salonSnap.exists) {
      throw new HttpsError("not-found", "Salon not found.");
    }
    const salon = salonSnap.data();

    // Existence was the whole gate. isAvailable is what discovery and both apps
    // filter on, and what requestAccountDeletion now clears when an owner
    // leaves — so without this a salon that had closed, been hidden by an
    // admin, or whose owner had deleted her account was still bookable by
    // anyone holding a link or a stale list, and took the customer's money.
    if (salon.isAvailable !== true) {
      throw new HttpsError("failed-precondition", "This salon is not taking bookings.",
        { reason: "SALON_UNAVAILABLE" });
    }

    // A provider may not book her own salon.
    //
    // Nothing else in the chain stops it, and the chain pays out. confirmAppointment
    // authorises on salon.providerId === caller and then unconditionally credits the
    // CUSTOMER ten loyalty points and increments the salon's confirmedCount — where
    // the customer may be the same person. Since pricePerService is provider-editable,
    // she can set a service to 1 AFN, where commission rounds to zero, so each cycle
    // is free; staff[] is provider-editable too, and hasSlotConflict treats a
    // different staffId as a different chair, so the cycles run in parallel. At a
    // hundred points redeemLoyaltyPoints converts them 1:1 into referralCredit, and
    // spending that credit on a cash booking at her own salon makes cashLedgerDelta
    // add it to owedAmount — a real balance recordProviderPayout pays in cash.
    //
    // Every step is individually legitimate and the result is platform money out of
    // nothing, indistinguishable in any report from ordinary cash bookings. It also
    // hands over confirmedCount, which firestore.rules freezes specifically so a
    // provider cannot self-award a GOLD or SILVER badge.
    //
    // createGiftCardSession already refuses the same shape of self-dealing.
    if (salon.providerId && salon.providerId === uid) {
      throw new HttpsError("failed-precondition", "You can't book your own salon.");
    }

    // Reject bookings on a day the provider blocked off (time-off/holiday). The
    // client already hides these days; this is defense in depth. Dates are stored
    // as "yyyy-MM-dd" in Kabul-local time, so map the requested instant the same way.
    const blockedDates = Array.isArray(salon.blockedDates) ? salon.blockedDates : [];
    if (blockedDates.length > 0) {
      const bookingDay = new Date(Number(appointmentDate))
        .toLocaleDateString("en-CA", { timeZone: "Asia/Kabul" });
      if (blockedDates.includes(bookingDay)) {
        throw new HttpsError("failed-precondition", "The salon is closed on that day.", { reason: "SALON_CLOSED" });
      }
    }

    // Price every requested service server-side and sum them. One shared path for
    // single-service, multi-service, and group bookings (see lib/money.js, tested).
    // A party is priced from its guest list. Normalised against what the salon
    // actually offers, because this arrives from a phone: a guest cannot conjure
    // a service into existence, and a guest having nothing done is not a guest.
    const partyGuests = isParty ? normalizeParty(party, salon.services) : [];
    if (isParty && partyGuests.length === 0) {
      throw new HttpsError("failed-precondition", "No guest in the group has a bookable service.");
    }
    const effectiveNames = isParty ? partyServices(partyGuests) : requestedServiceNames;

    const { services, total, invalid } = resolveServicesTotal(salon.pricePerService, effectiveNames);
    if (invalid.length > 0) {
      throw new HttpsError("failed-precondition", `No valid price for: ${invalid.join(", ")}`);
    }
    if (services.length === 0 || total <= 0) {
      throw new HttpsError("failed-precondition", "This service has no valid price.");
    }
    const listPrice = total;
    // Combined display name so every downstream string (stored serviceName,
    // notifications, the HesabPay line item) reads naturally for multi-service.
    const serviceName = services.map((s) => s.name).join("، ");

    // How many consecutive slots this booking occupies. Each service takes its
    // own duration when the salon set one (durationPerService, minutes); services
    // with no duration fall back to one whole slot. When no durations are set this
    // equals services.length — identical to the previous behavior. Tested in
    // lib/slots.js.
    // busyOffsets names which of those slots the stylist is actually working. A
    // colour leaves her free while the colour develops, and that gap is hers to
    // sell — so it is left out of the set the conflict check compares.
    // A party is the salon's block of the day rather than one stylist's: everyone
    // works, so the wall-clock is the total work divided by however many stylists
    // there are. Processing time does not apply — nobody is idle during a wedding
    // — so a party is busy throughout its span.
    const activeStaffCount = Math.max(
      1,
      (Array.isArray(salon.staff) ? salon.staff : []).filter((m) => m && m.active !== false).length
    );
    const { span: slotSpan, busyOffsets } = isParty
      ? { span: partySpan(partyGuests, salon.durationPerService, salon.slotDurationMinutes, activeStaffCount),
          busyOffsets: null }
      : serviceLayout(
          services.map((s) => s.name),
          salon.serviceTiming,
          salon.durationPerService,
          salon.slotDurationMinutes
        );
    // What the conflict check compares: the working offsets for an ordinary
    // booking, the whole span for a party.
    const occupies = isParty ? slotSpan : busyOffsets;
    // What the OPENING-HOURS check compares, which is a different question.
    // busyOffsets is where the stylist is working; while a colour develops she
    // is free and the chair is not. The customer is in the salon for the whole
    // span, so that is what has to fit before closing.
    const occupiedSpan = slotSpan;

    // Resolve the requested staff member (if any) server-side, so the stored
    // staffName can't be spoofed and a booking can't reference a staff member
    // who doesn't work here. An empty/omitted staffId means "any available".
    let resolvedStaffId = "";
    let resolvedStaffName = "";
    const wantStaffId = String(staffId || "");
    if (wantStaffId) {
      const member = (salon.staff || []).find(
        (s) => s && s.id === wantStaffId && s.active !== false
      );
      if (!member) {
        throw new HttpsError("failed-precondition", "That staff member is not available.", { reason: "STAFF_UNAVAILABLE" });
      }
      resolvedStaffId = member.id;
      resolvedStaffName = String(member.name || "");
    }

    // Slot-conflict guard, first pass.
    //
    // This used to read EVERY appointment the salon had ever had, with no date
    // bound, on every booking attempt — so the platform's busiest and most
    // valuable salons became its most expensive to book with, forever. The
    // window below is what makes the cost constant: conflicts can only involve
    // appointments near the requested time, so nothing else needs reading.
    //
    // This pass is an early rejection, not the guarantee. It runs before promo
    // and referral credit are reserved, so the common collision fails without
    // any reservation to unwind. The authoritative check runs inside the write
    // transaction below, where it cannot race.
    const slotMinutes = Number(salon.slotDurationMinutes) || 60;
    const conflictWindow = slotConflictWindow(appointmentDate);

    // Whether this start time exists on the salon's own grid.
    //
    // Recorded rather than refused, deliberately, and only here. The customer
    // app builds the grid from the device clock, so a phone set to another
    // timezone computes a real-looking time that is half an hour off Kabul's —
    // and today that booking succeeds and lands in the salon's calendar at an
    // hour she may not be open. Refusing it outright is the right end state and
    // the wrong thing to ship into a payment path hours before a release: it
    // turns a rare wrong-time booking into a hard failure at the till, for a
    // population I cannot measure from here.
    //
    // So it is measured. The flag rides on the appointment and the alert reaches
    // an admin, which is what makes this a staged change rather than a field
    // nobody reads. rescheduleAppointment, where no money is moving, refuses.
    // The span, not the busy count. `occupies` is busyOffsets — the slots the
    // stylist is working — and for anything with processing time that is fewer
    // than the slots the customer is in the chair for. Passing its length let a
    // three-slot colour start in the salon's last hour: the client would not
    // offer it, and the one check that exists to catch that agreed with the
    // server instead of with her.
    const fit = slotFit(salon, appointmentDate, occupiedSpan);
    if (!fit.ok) {
      // Not BOOKING_FAILED. That label is documented as "a customer tried to
      // book and could not", and the two write-failure alerts below use it — so
      // a booking that SUCCEEDED would page as an outage and inflate the count
      // any log-based alert watches. This one succeeded; it is a disagreement
      // between the app's grid and the salon's hours, and it is a different
      // thing to be woken up for.
      alertable("SLOT_MISMATCH", "A booking was made at a time the salon does not offer", {
        salonId, uid, appointmentDate, reason: fit.reason,
        kabulTime: new Date(appointmentDate).toLocaleString("en-CA", { timeZone: "Asia/Kabul" }),
      });
    }
    const offGridReason = fit.ok ? "" : fit.reason;

    const readNearbyAppointments = async (reader) => {
      const q = db.collection("appointments")
        .where("salonId", "==", salonId)
        .where("appointmentDate", ">=", conflictWindow.start)
        .where("appointmentDate", "<", conflictWindow.end);
      const snap = await (reader ? reader.get(q) : q.get());
      return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    };

    if (hasSlotConflict(await readNearbyAppointments(null), appointmentDate, occupies, resolvedStaffId, slotMinutes, undefined, isParty)) {
      // A customer chose this salon, this service and this time, and could not
      // have it. That is the most specific demand signal the system can observe.
      await recordDemandSignal({
        kind: "SLOT_TAKEN",
        districtKey: salon.districtKey || "",
        serviceName,
        salonId,
        lang: user.lang || "",
      });
      throw new HttpsError("failed-precondition", "That time slot is no longer available.", { reason: "SLOT_TAKEN" });
    }

    // Apply a promo code if one was entered (throws a clear error if invalid).
    // The customer is charged the discounted price; commission is computed on
    // that same discounted amount so the platform's cut scales with what was
    // actually paid, not the list price.
    const promo = await resolvePromoDiscount(promoCode, listPrice);

    // Apply the best live salon offer (phase-2 deals actually reduce the price,
    // not just show a badge). Offers stack with a promo code — both are genuine
    // discounts — and are folded into the same discount channel as the promo. The
    // per-offer math is pure/tested in lib/money.js; a lookup failure is ignored
    // so a bad offer never blocks a booking.
    let offerDiscount = 0;
    let appliedOfferId = "";
    try {
      const now = Date.now();
      const offersSnap = await db.collection("salon_offers")
        .where("salonId", "==", salonId)
        .where("active", "==", true)
        .get();
      for (const doc of offersSnap.docs) {
        const o = doc.data();
        if (o.expiresAt && Number(o.expiresAt) <= now) continue; // expired
        const d = offerDiscountFor(o, services);
        if (d > offerDiscount) { offerDiscount = d; appliedOfferId = doc.id; }
      }
    } catch (err) {
      logger.warn("createPaymentSession: offer lookup failed (ignored)", {
        error: String(err.message || err),
      });
    }

    // Last-minute deal: an extra auto-discount for booking a soon slot, to help
    // the salon fill an empty chair. Folded into the same discount channel; math
    // is pure/tested in lib/money.js.
    const lastMinuteDisc = lastMinuteDiscount(listPrice, appointmentDate, Date.now(), {
      enabled:     salon.lastMinuteEnabled === true,
      percent:     salon.lastMinutePercent,
      windowHours: salon.lastMinuteWindowHours,
    });

    // Package bundle discount: when the customer books a named package, apply its
    // discount if all its services are actually in the booking. Validated
    // server-side against the salon's stored packages; pure math in lib/money.js.
    let packageDiscount = 0;
    let appliedPackageId = "";
    if (packageId) {
      const pkg = (salon.packages || []).find((p) => p && p.id === packageId);
      const d = packageDiscountFor(pkg, services);
      if (d > 0) { packageDiscount = d; appliedPackageId = packageId; }
    }

    // Full checkout split (promo + offer + last-minute → referral credit → commission).
    // The customer is
    // charged the discounted price; commission is computed on that same discounted
    // amount so the platform's cut scales with what was actually paid, not the list
    // price. Referral credit auto-applies on top of any promo, capped at the
    // remaining amount; the used portion is deducted from their balance when the
    // booking completes (immediately for cash; in the webhook for online). The
    // arithmetic lives in lib/money.js so it can be unit-tested without Firebase.
    const commissionPercent = await getCommissionPercent();
    // Capped, not just summed. Four discounts land on one booking and three of
    // them are the salon's own — it may discount itself as deeply as it likes.
    // The promo code is not: an admin issues it and it stacks on top of whatever
    // the salon had already given away, and the four together reached the whole
    // list price. computeCheckout clamps at zero, so nothing ever went negative
    // and the real outcome was hidden: a salon doing the work for nothing.
    const rawDiscount = promo.discount + offerDiscount + lastMinuteDisc + packageDiscount;
    const totalDiscount = capDiscount(listPrice, rawDiscount, await getMaxDiscountFraction());

    // Reserve referral credit + the promo use ATOMICALLY at checkout, reading the
    // LIVE balance/usedCount inside the transaction — so two of the customer's
    // bookings in flight can't over-spend the same credit, and a limited code
    // can't exceed maxUses (the earlier reads were only a plan). The split is
    // recomputed from the live credit here; the payment is written reserved:true
    // so settlement does NOT spend again, and the abandon / write-failure /
    // HesabPay-failure paths refund it (refundReservation).
    let referralUsed, price, commissionAmount, providerNet;
    {
      const split = await db.runTransaction(async (tx) => {
        const uRef  = db.doc(`users/${uid}`);
        const uSnap = await tx.get(uRef);
        const liveCredit = uSnap.exists ? Math.max(0, Number(uSnap.data().referralCredit || 0)) : 0;

        let promoRef = null;
        if (promo.promoId) {
          promoRef = db.doc(`promo_codes/${promo.promoId}`);
          const pSnap = await tx.get(promoRef);
          if (pSnap.exists) {
            const pd = pSnap.data();
            const maxUses = Number(pd.maxUses || 0);
            if (maxUses > 0 && Number(pd.usedCount || 0) >= maxUses) {
              throw new HttpsError("failed-precondition", "This promo code has reached its usage limit.", { reason: "PROMO_LIMIT" });
            }
          }
        }

        const s = computeCheckout({
          listPrice,
          promoDiscount:  totalDiscount,
          referralCredit: liveCredit,
          commissionPercent,
        });

        // A fully-discounted booking can't go through HesabPay (it can't charge
        // 0) — reject BEFORE reserving (the throw rolls the transaction back, so
        // nothing is spent) and the customer re-books as cash.
        if (paymentMethod === "ONLINE" && s.price <= 0) {
          throw new HttpsError(
            "failed-precondition",
            "Your discount makes this booking free — please choose Cash payment.",
            { reason: "FREE_USE_CASH" }
          );
        }

        if (s.referralUsed > 0) {
          tx.update(uRef, { referralCredit: admin.firestore.FieldValue.increment(-s.referralUsed) });
        }
        if (promoRef) {
          tx.update(promoRef, { usedCount: admin.firestore.FieldValue.increment(1) });
        }
        return s;
      });
      ({ referralUsed, price, commissionAmount, providerNet } = split);
    }

    const providerId        = salon.providerId || "";

    // ── Cash path: the customer pays the salon in person, so the platform
    // never receives the money. The booking is confirmed immediately (no
    // payment to await) and the platform's commission becomes a debt the
    // provider owes, deducted automatically from their next online-payment
    // payout (see recordProviderPayout, which refuses to pay out <= 0).
    if (paymentMethod === "CASH") {
      const apptRef    = db.collection("appointments").doc();
      const paymentRef = db.collection("payments").doc();
      const bookingCode = await reserveBookingCode();
      const batch = pendingWrites();
      batch.set(apptRef, {
        bookingCode,
        offGridReason,
        customerId:     uid,
        customerName:   user.name  || "",
        customerPhone:  user.phone || "",
        salonId,
        salonName:      salon.salonName || "",
        serviceName,
        services,
        slotsCount:     slotSpan,
        busyOffsets:     busyOffsets || [],
        isParty:     isParty,
        party:     partyGuests,
        partySize:     partyGuests.length,
        staffId:        resolvedStaffId,
        staffName:      resolvedStaffName,
        appointmentDate,
        status:         "PENDING",
        paymentMethod:  "CASH",
        createdAt:      Date.now(),
        // When the wait for the salon's confirmation started — reset on
        // reschedule, which createdAt cannot be. See lib/unconfirmed.js.
        pendingSince:   Date.now(),
        notes:          safeNotes,
        reminderSent:   false,
        customerReported:    false,
        customerRatingSum:   Number(user.customerRatingSum || 0),
        customerRatingCount: Number(user.customerRatingCount || 0),
        noShowCount:         Number(user.noShowCount || 0),
      });
      batch.set(paymentRef, {
        appointmentId:     apptRef.id,
        customerId:        uid,
        providerId,
        salonId,
        serviceName,
        amount:            price,
        listPrice,
        promoCode:         promo.code,
        discountAmount:    promo.discount,
        offerDiscount,
        offerId:           appliedOfferId,
        lastMinuteDiscount: lastMinuteDisc,
        packageDiscount,
        packageId:         appliedPackageId,
        referralUsed,
        reserved:          true,
        // Which balance formula wrote this payment's entry, so a reversal
        // undoes exactly what the booking did even across this release.
        ledgerVersion:     LEDGER_VERSION,
        commissionPercent,
        commissionAmount,
        providerNet,
        currency:          "AFN",
        status:            "PENDING_CASH",
        method:            "CASH",
        hesabSessionId:    "",
        createdAt:         Date.now(),
      });
      // Referral credit + promo use were already reserved atomically at checkout
      // (reserved:true above), so the batch doesn't touch them here.
      if (providerId) {
        batch.set(
          db.doc(`provider_balances/${providerId}`),
          {
            providerId,
            // Commission owed, less the part of the price the customer did not
            // hand over in cash because she spent wallet credit.
            //
            // That credit is the platform's obligation, never the salon's. A
            // gift card was bought with real money the platform is holding; a
            // referral reward, a KYC bonus and redeemed loyalty points are
            // promotions the platform chose to run. The salon agreed to a price
            // and served the appointment either way. Counting only what was
            // handed over meant a customer with enough credit was served for
            // nothing — and if the credit came from a gift card, the platform
            // kept the money and the salon got none of it.
            owedAmount: admin.firestore.FieldValue.increment(
              cashLedgerDelta({ referralUsed, commissionAmount })
            ),
            updatedAt:  Date.now(),
          },
          { merge: true }
        );
        batch.set(db.collection("notifications").doc(), {
          recipientId: providerId,
          type:        "NEW_BOOKING",
          msgKey:      "NEW_BOOKING_CASH",
        msgParams:   { service: serviceName, price },
        title:       "New Cash Booking",
          body:        `${serviceName} — AFN ${price} to collect in person`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   apptRef.id,
        });
      }
      try {
        await commitBookingAtomically(db, batch, readNearbyAppointments,
          appointmentDate, occupies, resolvedStaffId, slotMinutes, isParty);
        await logAppointmentEvent(
          { bookingCode, salonId, customerId: uid, status: "" },
          apptRef.id, "PENDING",
          { uid, role: "CUSTOMER", name: user.name || "" },
          "Booked, paying the salon in cash"
        );
      } catch (err) {
        // The reservation is unwound either way: the promo use and referral credit
        // were spent before the transaction ran, so losing the race must hand them
        // back exactly as an internal failure does.
        await refundReservation({ customerId: uid, referralUsed, promoId: promo.promoId });
        if (err instanceof SlotTakenError) {
          throw new HttpsError("failed-precondition", err.message, { reason: err.reason });
        }
        alertable("BOOKING_FAILED", "createPaymentSession cash write failed", { salonId, uid });
        throw new HttpsError("internal", "Could not create the booking. Please try again.");
      }

      return {
        paymentId:     paymentRef.id,
        appointmentId: apptRef.id,
        checkoutUrl:   "",
        method:        "CASH",
        amount:        price,
        listPrice,
        discountAmount: promo.discount + referralUsed,
        commissionAmount,
        providerNet,
      };
    }

    // Create the appointment (AWAITING_PAYMENT, hidden from the provider until
    // the webhook flips it to PENDING) and the payment row (PENDING, so the
    // webhook always has a row to update) atomically in one batch — so we can
    // never end up with one without the other if the function dies mid-write.
    const apptRef    = db.collection("appointments").doc();
    const paymentRef = db.collection("payments").doc();
    const bookingCode = await reserveBookingCode();
    const createBatch = pendingWrites();
    createBatch.set(apptRef, {
      bookingCode,
      offGridReason,
      customerId:    uid,
      customerName:  user.name  || "",
      customerPhone: user.phone || "",
      salonId,
      salonName:     salon.salonName || "",
      serviceName,
      services,
      slotsCount:    slotSpan,
      busyOffsets:    busyOffsets || [],
      isParty:    isParty,
      party:    partyGuests,
      partySize:    partyGuests.length,
      staffId:       resolvedStaffId,
      staffName:     resolvedStaffName,
      appointmentDate,
      status:        "AWAITING_PAYMENT",
      paymentMethod: "ONLINE",
      createdAt:     Date.now(),
      notes:         safeNotes,
      reminderSent:  false,
      customerReported:    false,
      customerRatingSum:   Number(user.customerRatingSum || 0),
      customerRatingCount: Number(user.customerRatingCount || 0),
      noShowCount:         Number(user.noShowCount || 0),
    });
    createBatch.set(paymentRef, {
      appointmentId:     apptRef.id,
      customerId:        uid,
      providerId,
      salonId,
      serviceName,
      amount:            price,
      listPrice,
      promoCode:         promo.code,
      discountAmount:    promo.discount,
      offerDiscount,
      offerId:           appliedOfferId,
      lastMinuteDiscount: lastMinuteDisc,
      packageDiscount,
      packageId:         appliedPackageId,
      referralUsed,
      // Reserved atomically at checkout (see the reservation transaction), so the
      // webhook settlement must NOT spend referral / promo again — it checks
      // `reserved` first. The abandon/failure paths refund it instead.
      reserved:          true,
      // Legacy flag, kept so any in-flight pre-reservation payment still settles.
      promoCounted:      false,
      // Which balance formula wrote this payment's entry, so a reversal undoes
      // exactly what the booking did even across this release.
      ledgerVersion:     LEDGER_VERSION,
      commissionPercent,
      commissionAmount,
      providerNet,
      currency:          "AFN",
      status:            "PENDING",
      method:            "ONLINE",
      hesabSessionId:    "",
      createdAt:         Date.now(),
    });
    try {
      await commitBookingAtomically(db, createBatch, readNearbyAppointments,
        appointmentDate, occupies, resolvedStaffId, slotMinutes, isParty);
      await logAppointmentEvent(
        { bookingCode, salonId, customerId: uid, status: "" },
        apptRef.id, "AWAITING_PAYMENT",
        { uid, role: "CUSTOMER", name: user.name || "" },
        "Booked, awaiting online payment"
      );
    } catch (err) {
      // The reservation is unwound either way: the promo use and referral credit
      // were spent before the transaction ran, so losing the race must hand them
      // back exactly as an internal failure does.
      await refundReservation({ customerId: uid, referralUsed, promoId: promo.promoId });
      if (err instanceof SlotTakenError) {
        throw new HttpsError("failed-precondition", err.message, { reason: err.reason });
      }
      alertable("BOOKING_FAILED", "createPaymentSession online write failed", { salonId, uid });
      throw new HttpsError("internal", "Could not create the booking. Please try again.");
    }

    // ── Call HesabPay create-session ─────────────────────────────────────────
    // Request : items[]{id, name, price}, email,
    //           redirect_success_url, redirect_failure_url
    // Response (confirmed live from the create-session call): { success,
    //           session_id, url, expires_at } — the checkout link field is
    //           `url`, NOT `payment_url` as developers.hesab.com's docs imply.
    let sessionUrl = "";
    let sessionId  = "";
    try {
      const redirectBase = HESAB_REDIRECT_BASE.value();
      const res = await fetch(
        `${HESAB_BASE_URL.value()}/payment/create-session`,
        {
          method:  "POST",
          headers: hesabHeaders(apiKey),
          body: JSON.stringify({
            email: email || user.email || `user_${uid}@safebeauty.af`,
            items: [
              {
                id:    paymentRef.id,
                name:  `${salon.salonName || "Salon"} — ${serviceName}`,
                price: price,
              },
            ],
            redirect_success_url: `${redirectBase}/payment/success?paymentId=${paymentRef.id}`,
            redirect_failure_url: `${redirectBase}/payment/failure?paymentId=${paymentRef.id}`,
          }),
        }
      );

      const body = await res.json();

      if (!res.ok || !body.success) {
        throw new Error(
          `HesabPay error ${res.status}: ${body.message || JSON.stringify(body)}`
        );
      }

      sessionUrl = body.url || body.payment_url || "";
      sessionId  = body.session_id  || "";

      if (!sessionUrl) {
        logger.error("HesabPay create-session response missing url", { body });
        throw new Error("HesabPay returned no checkout url.");
      }
    } catch (err) {
      // Roll back so we don't leave orphaned AWAITING_PAYMENT bookings, and
      // refund the checkout reservation (referral credit + promo use).
      await paymentRef.update({ status: "FAILED" });
      await apptRef.delete();
      await refundReservation({ customerId: uid, referralUsed, promoId: promo.promoId });
      logger.error("createPaymentSession failed", err);
      throw new HttpsError("internal", String(err.message || err));
    }

    await paymentRef.update({ hesabSessionId: sessionId });

    return {
      paymentId:     paymentRef.id,
      appointmentId: apptRef.id,
      checkoutUrl:   sessionUrl,
      amount:        price,
      listPrice,
      discountAmount: promo.discount + referralUsed,
      commissionAmount,
      providerNet,
    };
  }
);

// ── createGiftCardSession (callable) ──────────────────────────────────────────
// Buy AFN credit for another registered user, identified by phone. On payment
// success (see hesabPayWebhook) the amount is added to the recipient's
// referralCredit wallet, which auto-applies at their next checkout — no
// redemption step. Online (HesabPay) only; the amount is validated/recomputed
// server-side so the client can't spoof it.
exports.createGiftCardSession = onCall(
  { secrets: [HESAB_API_KEY], region: "us-central1" },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
    const buyer = await resolveAppUser(request);
    assertNotSuspended(buyer);

    const { recipientPhone, amount, message } = request.data || {};
    const gift = validateGiftAmount(amount);
    if (!gift.ok) {
      throw new HttpsError("invalid-argument", "Enter a valid gift amount (50–50,000 AFN).");
    }

    // Recipient must be a registered user (we credit their existing wallet).
    const phone = normalizePhone(recipientPhone);
    // Matched the way a login matches — an account stored before normalization
    // existed could not be sent a gift card, and the buyer was told no such
    // account existed while looking at the person's number in her contacts.
    const recipient = await findAccountByPhone(phone, String(recipientPhone || ""));
    if (!recipient) throw new HttpsError("not-found", "No account uses that phone number.");
    if (recipient.id === buyer.uid) {
      throw new HttpsError("failed-precondition", "You can't send a gift card to yourself.");
    }

    const apiKey = HESAB_API_KEY.value();
    if (!apiKey) throw new HttpsError("failed-precondition", "Payment is not configured.");

    // A gift card rides the same payments collection as bookings, tagged
    // type:"GIFT_CARD", so the existing webhook finds it by items[0].id and just
    // branches on the type instead of releasing an appointment.
    const giftRef    = db.collection("gift_cards").doc();
    const paymentRef = db.collection("payments").doc();
    const batch = db.batch();
    batch.set(giftRef, {
      buyerUid:       buyer.uid,
      buyerName:      buyer.name || "",
      recipientUid:   recipient.id,
      recipientPhone: phone,
      amount:         gift.value,
      message:        String(message || "").slice(0, 200),
      status:         "PENDING",
      paymentId:      paymentRef.id,
      createdAt:      Date.now(),
    });
    batch.set(paymentRef, {
      type:           "GIFT_CARD",
      giftCardId:     giftRef.id,
      buyerUid:       buyer.uid,
      recipientUid:   recipient.id,
      amount:         gift.value,
      currency:       "AFN",
      status:         "PENDING",
      method:         "ONLINE",
      hesabSessionId: "",
      createdAt:      Date.now(),
    });
    await batch.commit();

    let sessionUrl = "";
    let sessionId  = "";
    try {
      const redirectBase = HESAB_REDIRECT_BASE.value();
      const res = await fetch(`${HESAB_BASE_URL.value()}/payment/create-session`, {
        method:  "POST",
        headers: hesabHeaders(apiKey),
        body: JSON.stringify({
          email: buyer.email || `user_${buyer.uid}@safebeauty.af`,
          items: [{ id: paymentRef.id, name: `SafeBeauty gift card — AFN ${gift.value}`, price: gift.value }],
          redirect_success_url: `${redirectBase}/payment/success?paymentId=${paymentRef.id}`,
          redirect_failure_url: `${redirectBase}/payment/failure?paymentId=${paymentRef.id}`,
        }),
      });
      const body = await res.json();
      if (!res.ok || !body.success) {
        throw new Error(`HesabPay error ${res.status}: ${body.message || JSON.stringify(body)}`);
      }
      sessionUrl = body.url || body.payment_url || "";
      sessionId  = body.session_id || "";
      if (!sessionUrl) throw new Error("HesabPay returned no checkout url.");
    } catch (err) {
      // Roll back so a failed create-session leaves no dangling PENDING gift.
      await paymentRef.update({ status: "FAILED" });
      await giftRef.update({ status: "FAILED" });
      logger.error("createGiftCardSession failed", err);
      throw new HttpsError("internal", String(err.message || err));
    }

    await paymentRef.update({ hesabSessionId: sessionId });
    return { paymentId: paymentRef.id, checkoutUrl: sessionUrl, amount: gift.value };
  }
);

// ── createWalletTopUp (callable) ──────────────────────────────────────────────
// Top up your OWN wallet (referralCredit) with HesabPay. On payment success (see
// hesabPayWebhook) the amount is added to the caller's referralCredit, which
// auto-applies at their next checkout — no redemption step. referralCredit is
// client-frozen, so this Admin-SDK callable + webhook is the only credit path.
// Online (HesabPay) only; the amount is validated/recomputed server-side so the
// client can't spoof it. Rides the same payments collection tagged
// type:"WALLET_TOPUP".
exports.createWalletTopUp = onCall(
  { secrets: [HESAB_API_KEY], region: "us-central1" },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
    const buyer = await resolveAppUser(request);
    assertNotSuspended(buyer);

    const { amount } = request.data || {};
    const top = validateGiftAmount(amount);
    if (!top.ok) {
      throw new HttpsError("invalid-argument", "Enter a valid top-up amount (50–50,000 AFN).");
    }

    const apiKey = HESAB_API_KEY.value();
    if (!apiKey) throw new HttpsError("failed-precondition", "Payment is not configured.");

    const paymentRef = db.collection("payments").doc();
    await paymentRef.set({
      type:           "WALLET_TOPUP",
      buyerUid:       buyer.uid,
      amount:         top.value,
      currency:       "AFN",
      status:         "PENDING",
      method:         "ONLINE",
      hesabSessionId: "",
      createdAt:      Date.now(),
    });

    let sessionUrl = "";
    let sessionId  = "";
    try {
      const redirectBase = HESAB_REDIRECT_BASE.value();
      const res = await fetch(`${HESAB_BASE_URL.value()}/payment/create-session`, {
        method:  "POST",
        headers: hesabHeaders(apiKey),
        body: JSON.stringify({
          email: buyer.email || `user_${buyer.uid}@safebeauty.af`,
          items: [{ id: paymentRef.id, name: `SafeBeauty wallet top-up — AFN ${top.value}`, price: top.value }],
          redirect_success_url: `${redirectBase}/payment/success?paymentId=${paymentRef.id}`,
          redirect_failure_url: `${redirectBase}/payment/failure?paymentId=${paymentRef.id}`,
        }),
      });
      const body = await res.json();
      if (!res.ok || !body.success) {
        throw new Error(`HesabPay error ${res.status}: ${body.message || JSON.stringify(body)}`);
      }
      sessionUrl = body.url || body.payment_url || "";
      sessionId  = body.session_id || "";
      if (!sessionUrl) throw new Error("HesabPay returned no checkout url.");
    } catch (err) {
      // Roll back so a failed create-session leaves no dangling PENDING top-up.
      await paymentRef.update({ status: "FAILED" });
      logger.error("createWalletTopUp failed", err);
      throw new HttpsError("internal", String(err.message || err));
    }

    await paymentRef.update({ hesabSessionId: sessionId });
    return { paymentId: paymentRef.id, checkoutUrl: sessionUrl, amount: top.value };
  }
);

// ── createTipSession (callable) ───────────────────────────────────────────────
// Tip the provider for a completed visit. The whole amount goes to the provider
// (no commission) — it's added to provider_balances.owedAmount on payment success
// (see hesabPayWebhook), paid out with their normal balance. Online (HesabPay)
// only; the amount and the provider are recomputed server-side so neither can be
// spoofed. Rides the same payments collection tagged type:"TIP".
exports.createTipSession = onCall(
  { secrets: [HESAB_API_KEY], region: "us-central1" },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
    const customer = await resolveAppUser(request);
    assertNotSuspended(customer);

    const { appointmentId, amount } = request.data || {};
    if (!appointmentId) throw new HttpsError("invalid-argument", "appointmentId is required.");
    const tip = validateGiftAmount(amount, { min: 10, max: 20000 });
    if (!tip.ok) {
      throw new HttpsError("invalid-argument", "Enter a valid tip amount (10–20,000 AFN).");
    }

    // The tip must belong to the caller's own booking; resolve the provider from
    // the salon server-side so the client can't redirect a tip to someone else.
    const apptSnap = await db.doc(`appointments/${appointmentId}`).get();
    if (!apptSnap.exists) throw new HttpsError("not-found", "Booking not found.");
    const appt = apptSnap.data();
    if (appt.customerId !== customer.uid) {
      throw new HttpsError("permission-denied", "That isn't your booking.");
    }
    const salonSnap = await db.doc(`salons/${appt.salonId}`).get();
    const providerId = salonSnap.exists ? (salonSnap.data().providerId || "") : "";
    if (!providerId) throw new HttpsError("failed-precondition", "This salon can't receive tips yet.");

    const apiKey = HESAB_API_KEY.value();
    if (!apiKey) throw new HttpsError("failed-precondition", "Payment is not configured.");

    const paymentRef = db.collection("payments").doc();
    await paymentRef.set({
      type:           "TIP",
      appointmentId,
      customerId:     customer.uid,
      providerId,
      salonId:        appt.salonId,
      amount:         tip.value,
      currency:       "AFN",
      status:         "PENDING",
      method:         "ONLINE",
      hesabSessionId: "",
      createdAt:      Date.now(),
    });

    let sessionUrl = "";
    let sessionId  = "";
    try {
      const redirectBase = HESAB_REDIRECT_BASE.value();
      const res = await fetch(`${HESAB_BASE_URL.value()}/payment/create-session`, {
        method:  "POST",
        headers: hesabHeaders(apiKey),
        body: JSON.stringify({
          email: customer.email || `user_${customer.uid}@safebeauty.af`,
          items: [{ id: paymentRef.id, name: `SafeBeauty tip — AFN ${tip.value}`, price: tip.value }],
          redirect_success_url: `${redirectBase}/payment/success?paymentId=${paymentRef.id}`,
          redirect_failure_url: `${redirectBase}/payment/failure?paymentId=${paymentRef.id}`,
        }),
      });
      const body = await res.json();
      if (!res.ok || !body.success) {
        throw new Error(`HesabPay error ${res.status}: ${body.message || JSON.stringify(body)}`);
      }
      sessionUrl = body.url || body.payment_url || "";
      sessionId  = body.session_id || "";
      if (!sessionUrl) throw new Error("HesabPay returned no checkout url.");
    } catch (err) {
      await paymentRef.update({ status: "FAILED" });
      logger.error("createTipSession failed", err);
      throw new HttpsError("internal", String(err.message || err));
    }

    await paymentRef.update({ hesabSessionId: sessionId });
    return { paymentId: paymentRef.id, checkoutUrl: sessionUrl, amount: tip.value };
  }
);

// ── redeemLoyaltyPoints (callable) ────────────────────────────────────────────
// Spend loyalty points for wallet credit (referralCredit), which auto-applies at
// the next checkout. loyaltyPoints and referralCredit are both client-frozen, so
// this Admin-SDK callable is the only path. Points redeem in whole 100s at 1 AFN
// each, minimum 100 — the math lives in lib/money.js (tested).
exports.redeemLoyaltyPoints = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);

  const conv = loyaltyToCredit((request.data || {}).points);
  if (!conv.ok) {
    throw new HttpsError("failed-precondition", "You need at least 100 points to redeem.");
  }

  const userRef = db.doc(`users/${appUser.uid}`);
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    if (!snap.exists) return { ok: false };
    const have = Number(snap.data().loyaltyPoints || 0);
    if (have < conv.spend) return { ok: false, have };
    tx.update(userRef, {
      loyaltyPoints:  admin.firestore.FieldValue.increment(-conv.spend),
      referralCredit: admin.firestore.FieldValue.increment(conv.credit),
    });
    tx.set(db.collection("notifications").doc(), {
      recipientId: appUser.uid,
      type:        "SYSTEM",
      msgKey:      "POINTS_REDEEMED",
      msgParams:   { spend: conv.spend, credit: conv.credit },
      title:       "Points redeemed 🎉",
      body:        `You turned ${conv.spend} points into AFN ${conv.credit} of wallet credit.`,
      isRead:      false,
      createdAt:   Date.now(),
      relatedId:   "",
    });
    return { ok: true };
  });

  if (!result.ok) {
    throw new HttpsError("failed-precondition", "You don't have enough points.");
  }
  return { spent: conv.spend, credited: conv.credit };
});

// ── claimProfileReward (callable) ─────────────────────────────────────────────
// One-time loyalty bonus for completing a profile (name + phone + photo). Awarded
// exactly once (guarded by profileRewardClaimed, which is client-frozen) so the
// client can safely call this whenever the profile looks complete. loyaltyPoints
// is client-frozen, so this Admin-SDK callable is the only path.
const PROFILE_REWARD_POINTS = 20;

exports.claimProfileReward = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  assertNotSuspended(appUser);
  const userRef = db.doc(`users/${appUser.uid}`);

  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    if (!snap.exists) return { awarded: false };
    const u = snap.data();
    if (u.profileRewardClaimed === true) return { awarded: false };
    const complete =
      String(u.name || "").trim() !== "" &&
      String(u.phone || "").trim() !== "" &&
      (String(u.profilePhotoUrl || "").trim() !== "" ||
       String(u.profilePhotoBase64 || "").trim() !== "");
    if (!complete) return { awarded: false };
    tx.update(userRef, {
      profileRewardClaimed: true,
      loyaltyPoints: admin.firestore.FieldValue.increment(PROFILE_REWARD_POINTS),
    });
    tx.set(db.collection("notifications").doc(), {
      recipientId: appUser.uid,
      type:        "SYSTEM",
      msgKey:      "PROFILE_COMPLETE",
      msgParams:   { points: PROFILE_REWARD_POINTS },
      title:       "Profile complete 🌟",
      body:        `You earned ${PROFILE_REWARD_POINTS} loyalty points for completing your profile.`,
      isRead:      false,
      createdAt:   Date.now(),
      relatedId:   "",
    });
    return { awarded: true };
  });

  return { awarded: result.awarded === true, points: PROFILE_REWARD_POINTS };
});

// ── hesabPayWebhook (HTTP) ────────────────────────────────────────────────────

/**
 * Settle one HesabPay payment, inside a transaction.
 *
 * Extracted from the webhook handler so the reconciler can run exactly the same
 * settlement rather than a second implementation of it. Two implementations of
 * how money settles is how they drift, and the one that drifts is discovered by
 * a customer.
 *
 * The single value this needs to hand back besides its outcome -- the
 * appointment transition to record once the transaction commits -- is written
 * onto [ctx] rather than returned, so every `return "..."` below is the code
 * that has been running in production, moved and not rewritten.
 *
 * @param {FirebaseFirestore.Transaction} tx
 * @param {{paymentRef, paidSignal, failSignal, transactionId, signature, paymentId, apptEvent}} ctx
 * @returns {Promise<"paid"|"failed"|"ignored"|"replay"|"stale"|"already_paid"|"not_found">}
 */

async function settlePaymentInTransaction(tx, ctx) {
  const { paymentRef, paidSignal, failSignal, transactionId, signature, paymentId } = ctx;
  // Re-read inside the transaction so two concurrent retries can't both
  // pass the PAID check and double-credit the provider.
  const freshSnap = await tx.get(paymentRef);
  if (!freshSnap.exists) return "not_found";
  const fresh = freshSnap.data();

  // Idempotency — already settled.
  if (fresh.status === "PAID") return "already_paid";

  // A settleable payment is always still "PENDING". If it has already been
  // moved to a terminal non-paid state (EXPIRED by the abandoned-payment
  // sweep, FAILED, CANCELLED, REFUND_PENDING) a late webhook must NOT
  // revive it — otherwise an expired booking whose slot was re-sold gets
  // re-opened and the provider double-credited while the customer already
  // had their reserved credit refunded.
  //
  // Not reviving it was right. Saying nothing was not. On a slow Afghan
  // connection a customer can complete a HesabPay checkout after the two-hour
  // sweep has already expired her booking, and the paid webhook then arrives
  // for a payment nobody is waiting on. This returned 200 and dropped it: her
  // money had left her account, the booking was gone, and the only trace was a
  // log line. Someone has to give it back, so record the debt, put an admin on
  // it, and tell her it is coming — the slot is still not re-opened.
  if (fresh.status !== "PENDING") {
    if (paidSignal && fresh.lateSettlement !== true) {
      tx.update(paymentRef, {
        lateSettlement:   true,
        lateSettlementAt: Date.now(),
        lateTransactionId: String(transactionId || ""),
      });
      const refundRef = db.collection("refund_requests").doc();
      tx.set(refundRef, {
        appointmentId: fresh.appointmentId || "",
        paymentId,
        customerId:    fresh.customerId || "",
        providerId:    fresh.providerId || "",
        salonId:       fresh.salonId || "",
        amount:        Number(fresh.amount || 0),
        reason:        "LATE_PAYMENT",
        status:        "PENDING",
        createdAt:     Date.now(),
      });
      ctx.lateSettlement = {
        paymentId,
        refundRequestId: refundRef.id,
        customerId: fresh.customerId || "",
        amount: Number(fresh.amount || 0),
        previousStatus: String(fresh.status || ""),
      };
    }
    return "stale";
  }

  // Replay guard — a webhook settles exactly one payment, ever. Prefer the
  // transaction_id; when the payload omits it (or it isn't path-safe), fall
  // back to a hash of the signature so a captured (signature, timestamp)
  // pair can't be replayed against a DIFFERENT payment. Previously the guard
  // was skipped entirely with no transaction_id, so the only defense left
  // was the same-payment status check — a replay aimed at another paymentId
  // sailed through. The key is always present and path-safe now.
  const replayKey = (transactionId && isValidDocId(transactionId))
    ? transactionId
    : "sig_" + crypto.createHash("sha256")
        .update(String(signature) + "|" + String(transactionId)).digest("hex");
  const webhookRef = db.doc(`processed_webhooks/${replayKey}`);
  const seen = await tx.get(webhookRef);
  if (seen.exists) return "replay";

  if (paidSignal) {
    // Gift-card payment: credit the recipient's wallet (referralCredit) so
    // it auto-applies at their next checkout — no appointment to release.
    // The status/replay guards above make this run exactly once.
    if (fresh.type === "GIFT_CARD") {
      tx.update(paymentRef, {
        status: "PAID",
        paidAt: Date.now(),
        transactionId: transactionId || null,
      });
      tx.update(db.doc(`users/${fresh.recipientUid}`), {
        referralCredit: admin.firestore.FieldValue.increment(Number(fresh.amount || 0)),
      });
      if (fresh.giftCardId) {
        tx.update(db.doc(`gift_cards/${fresh.giftCardId}`), { status: "PAID", paidAt: Date.now() });
      }
      tx.set(db.collection("notifications").doc(), {
        recipientId: fresh.recipientUid,
        type:        "GIFT_RECEIVED",
        msgKey:      "GIFT_RECEIVED",
        msgParams:   { amount: fresh.amount },
        title:       "You received a gift card 🎁",
        body:        `AFN ${fresh.amount} credit was added to your account.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   fresh.giftCardId || "",
      });
      if (webhookRef) tx.set(webhookRef, { paymentId, settledAt: Date.now() });
      return "paid";
    }

    // Wallet top-up: credit the buyer's own wallet (referralCredit) so it
    // auto-applies at their next checkout. No appointment to release. The
    // status/replay guards above make this run exactly once.
    if (fresh.type === "WALLET_TOPUP") {
      tx.update(paymentRef, {
        status: "PAID",
        paidAt: Date.now(),
        transactionId: transactionId || null,
      });
      tx.update(db.doc(`users/${fresh.buyerUid}`), {
        referralCredit: admin.firestore.FieldValue.increment(Number(fresh.amount || 0)),
      });
      tx.set(db.collection("notifications").doc(), {
        recipientId: fresh.buyerUid,
        type:        "WALLET_TOPUP",
        msgKey:      "WALLET_TOPUP",
        msgParams:   { amount: fresh.amount },
        title:       "Wallet topped up 👛",
        body:        `AFN ${fresh.amount} was added to your wallet.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   paymentId || "",
      });
      if (webhookRef) tx.set(webhookRef, { paymentId, settledAt: Date.now() });
      return "paid";
    }

    // Tip payment: the full amount is owed to the provider (no commission),
    // paid out with their normal balance. No appointment to release.
    if (fresh.type === "TIP") {
      tx.update(paymentRef, {
        status: "PAID",
        paidAt: Date.now(),
        transactionId: transactionId || null,
      });
      if (fresh.providerId) {
        tx.set(
          db.doc(`provider_balances/${fresh.providerId}`),
          {
            providerId: fresh.providerId,
            owedAmount: admin.firestore.FieldValue.increment(Number(fresh.amount || 0)),
            updatedAt:  Date.now(),
          },
          { merge: true }
        );
        tx.set(db.collection("notifications").doc(), {
          recipientId: fresh.providerId,
          type:        "TIP_RECEIVED",
          msgKey:      "TIP_RECEIVED",
          msgParams:   { amount: fresh.amount },
          title:       "You received a tip 💝",
          body:        `A customer tipped you AFN ${fresh.amount}.`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   fresh.appointmentId || "",
        });
      }
      if (webhookRef) tx.set(webhookRef, { paymentId, settledAt: Date.now() });
      return "paid";
    }

    tx.update(paymentRef, {
      status: "PAID",
      paidAt: Date.now(),
      transactionId: transactionId || null,
    });
    // Release the appointment to the provider's pending queue.
    // pendingSince starts here, not at checkout: until the money arrived the
    // salon had nothing to confirm, and an abandoned-then-paid booking would
    // otherwise arrive up to two hours into its own confirmation deadline.
    tx.update(db.doc(`appointments/${fresh.appointmentId}`),
      { status: "PENDING", pendingSince: Date.now() });
    ctx.apptEvent = { id: fresh.appointmentId, to: "PENDING", reason: "Online payment received" };
    // Track what the provider is owed (platform pays out separately).
    // Guarded: an empty providerId would make db.doc("provider_balances/")
    // throw synchronously, 500-ing every webhook retry and stranding the
    // customer's PAID booking in AWAITING_PAYMENT forever.
    if (fresh.providerId) {
      tx.set(
        db.doc(`provider_balances/${fresh.providerId}`),
        {
          // What she is owed for the appointment, plus the part of the price the
          // customer paid with wallet credit rather than at the checkout.
          //
          // That credit is the platform's obligation and never the salon's: a
          // gift card was bought with real money the platform is holding, and a
          // referral reward, a KYC bonus or redeemed loyalty points are
          // promotions the platform chose to run. Counting only what HesabPay
          // moved meant the platform kept the gift-card money and paid the salon
          // out of its own price.
          owedAmount: admin.firestore.FieldValue.increment(onlineLedgerDelta(fresh)),
          updatedAt:  Date.now(),
        },
        { merge: true }
      );
    }
    // Count the promo use and spend any referral credit. Reserved payments
    // (reserved:true) already did this atomically at checkout, so settlement
    // must NOT spend again — only legacy pre-reservation payments fall here,
    // and the status/replay guards make even those apply exactly once.
    if (!fresh.reserved && !fresh.promoCounted) {
      tx.update(paymentRef, { promoCounted: true });
      if (fresh.promoCode) {
        tx.update(db.doc(`promo_codes/${fresh.promoCode}`), {
          usedCount: admin.firestore.FieldValue.increment(1),
        });
      }
      if (Number(fresh.referralUsed || 0) > 0 && fresh.customerId) {
        tx.update(db.doc(`users/${fresh.customerId}`), {
          referralCredit: admin.firestore.FieldValue.increment(-Number(fresh.referralUsed)),
        });
      }
    }
    // Notify the provider of the new (paid) booking.
    if (fresh.providerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: fresh.providerId,
        type:        "NEW_BOOKING",
        msgKey:      "NEW_BOOKING_PAID",
        msgParams:   { service: fresh.serviceName || "", amount: fresh.amount },
        title:       "New Paid Booking",
        body:        `${fresh.serviceName} — paid AFN ${fresh.amount}`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   fresh.appointmentId,
      });
    }
    if (webhookRef) {
      tx.set(webhookRef, { paymentId, settledAt: Date.now() });
    }
    return "paid";
  }

  if (failSignal) {
    tx.update(paymentRef, { status: "FAILED" });
    // A reserved booking spent the customer's referral credit + a promo use
    // up front and parked the appointment in AWAITING_PAYMENT. On an explicit
    // payment failure we must release both, or the customer loses that money
    // forever and the chair stays occupied by a dead booking (hasSlotConflict
    // only skips CANCELLED). Cancel the appointment here (atomic with the
    // FAILED write); the reservation refund runs just after the transaction.
    if (fresh.appointmentId && !fresh.type) {
      tx.update(db.doc(`appointments/${fresh.appointmentId}`), { status: "CANCELLED" });
      ctx.apptEvent = { id: fresh.appointmentId, to: "CANCELLED", reason: "Online payment failed" };
    }
    // Keep the linked gift-card doc in sync — otherwise it sits PENDING
    // forever (the create-session rollback only covers pre-checkout errors).
    if (fresh.type === "GIFT_CARD" && fresh.giftCardId) {
      tx.update(db.doc(`gift_cards/${fresh.giftCardId}`), { status: "FAILED" });
    }
    return "failed";
  }

  // Unknown/intermediate callback — leave the payment untouched.
  return "ignored";
}

exports.hesabPayWebhook = onRequest(
  { secrets: [HESAB_API_KEY, HESAB_WEBHOOK_SECRET], region: "us-central1" },
  async (req, res) => {
    if (req.method !== "POST") {
      return res.status(405).send("Method not allowed");
    }

    const payload = req.body || {};

    const { signature, timestamp } = payload;
    if (!signature || !timestamp) {
      return res.status(400).send("Missing signature or timestamp");
    }

    // Freshness is checked locally BEFORE the remote verification call. Until
    // now the entire question of whether a payload was recent was delegated to
    // HesabPay, which means a captured (signature, timestamp) pair replayed a
    // month later still cost a network round trip to reject — and if their
    // endpoint ever stopped checking age, nothing here would notice.
    //
    // Generous window: clock skew between two servers is real, and a legitimate
    // webhook retried after an outage is worth accepting.
    const WEBHOOK_MAX_AGE_MS = 24 * 60 * 60 * 1000;
    const sentAtMs = (() => {
      const n = Number(timestamp);
      if (!Number.isFinite(n)) return Date.parse(String(timestamp));
      // Seconds or milliseconds, both seen in the wild.
      return n > 1e12 ? n : n * 1000;
    })();
    if (Number.isFinite(sentAtMs) && Math.abs(Date.now() - sentAtMs) > WEBHOOK_MAX_AGE_MS) {
      alertable("PAYMENT_FAILED", "Webhook rejected: timestamp outside the accepted window", {
        timestamp: String(timestamp),
      });
      return res.status(401).send("Stale webhook");
    }

    // Verify the webhook signature before trusting the payload so a forged
    // request can't mark an unpaid booking as PAID.
    try {
      // Per developers.hesab.com step 4: send signature + timestamp from the
      // payload to the verify-signature endpoint. The secret was registered in
      // HesabPay portal so the server can verify without us resending it.
      const verifyRes = await fetch(
        `${HESAB_BASE_URL.value()}/hesab/webhooks/verify-signature`,
        {
          method:  "POST",
          headers: hesabHeaders(HESAB_API_KEY.value()),
          body: JSON.stringify({ signature, timestamp }),
        }
      );
      const verifyBody = await verifyRes.json().catch(() => ({}));
      const valid =
        verifyRes.ok &&
        (verifyBody.valid === true || verifyBody.verified === true || verifyBody.success === true);
      if (!valid) {
        alertable("PAYMENT_FAILED", "Webhook signature verification failed");
        return res.status(401).send("Invalid signature");
      }
    } catch (err) {
      alertable("PAYMENT_FAILED", "Webhook signature verification error", { error: String(err && err.message || err) });
      return res.status(401).send("Signature verification error");
    }

    // ── Resolve paymentId from the webhook payload ────────────────────────────
    // Actual HesabPay webhook payload (from developers.hesab.com):
    //   { status_code, success, message, sender_account, transaction_id,
    //     amount, memo, signature, timestamp, transaction_date,
    //     items[]{id, name, price}, email }
    //
    // We set items[0].id = paymentRef.id at session creation, so we can look
    // up the payment document directly by ID — no Firestore query needed.
    const itemId    = (Array.isArray(payload.items) && payload.items.length > 0)
      ? String(payload.items[0].id || "")
      : "";
    // Keep session_id fallback for any future HesabPay API changes.
    const webhookSessionId = payload.session_id || payload.sessionId || "";

    let paymentId   = "";
    let paymentSnap = null;

    if (itemId && isValidDocId(itemId)) {
      // Direct document lookup — O(1), no index required. itemId comes straight
      // from the (attacker-controllable) payload, so it's path-validated first;
      // a malformed value falls through to the session-id lookup / 404 below.
      const snap = await db.collection("payments").doc(itemId).get();
      if (snap.exists) {
        paymentSnap = snap;
        paymentId   = snap.id;
      }
    }

    if (!paymentId && webhookSessionId) {
      // Fallback: look up by stored HesabPay session ID.
      const q = await db
        .collection("payments")
        .where("hesabSessionId", "==", webhookSessionId)
        .limit(1)
        .get();
      if (!q.empty) {
        paymentSnap = q.docs[0];
        paymentId   = paymentSnap.id;
      }
    }

    if (!paymentId || !paymentSnap) {
      logger.error("Webhook: no payment found", { itemId, webhookSessionId, payload });
      return res.status(404).send("Payment not found");
    }

    const payment = paymentSnap.data();
    const paymentRef = db.doc(`payments/${paymentId}`);

    // HesabPay's signature is computed over the timestamp, not the body — so a
    // captured (signature, timestamp) pair could be replayed with a swapped
    // items[0].id to mark a DIFFERENT booking as paid. Two defenses below:
    //   1. transaction_id replay guard (a transaction_id can settle exactly one
    //      payment, ever) — see the processed_webhooks doc in the transaction.
    //   2. amount binding — the amount HesabPay reports must equal what we asked
    //      the customer to pay. A AFN 1 payment can't settle a AFN 5000 booking.
    const transactionId = String(payload.transaction_id || payload.transactionId || "");
    const reportedAmount = Number(payload.amount);

    // Interpret the callback via the pure helpers in lib/webhook.js (unit-tested).
    // paidSignal: success:true / status_code:10 / legacy status strings.
    // failSignal: only an EXPLICIT failure — an unknown/intermediate callback is a
    // no-op (returns 200) so it can't destroy a payment still in flight.
    const paidSignal = isPaidSignal(payload);
    const failSignal = isFailSignal(payload);

    // Amount binding — reject only a clear UNDERPAYMENT (paid less than the
    // recorded price), which is the actual attack: settle a AFN 5000 booking
    // with a AFN 1 payment. We don't hard-reject other mismatches because the
    // exact unit of HesabPay's `amount` field isn't yet confirmed against a real
    // sample (could be AFN vs. pul), and a false reject would block real
    // customers. The transaction_id replay guard is the primary defense.
    if (paidSignal && Number.isFinite(reportedAmount)) {
      if (isUnderpaid(reportedAmount, payment.amount)) {
        logger.error("Webhook: underpayment rejected", {
          paymentId, expected: payment.amount, reported: reportedAmount,
        });
        return res.status(400).send("Amount too low");
      }
      if (reportedAmount !== Number(payment.amount)) {
        logger.warn("Webhook: amount differs from expected (allowed)", {
          paymentId, expected: payment.amount, reported: reportedAmount,
        });
      }
    }

    // Recorded inside the transaction, written after it commits: the trail
    // entry needs the appointment's own previous status and booking code, and
    // Firestore requires every read in a transaction to precede every write --
    // by the time the appointment is updated here, writes have already begun.
    // Assigning a plain descriptor is safe against transaction retries because
    // a retry recomputes exactly the same value.
    let apptEventAfter = null;

    try {
      // Same settlement the reconciler runs. ctx carries the one value the
      // transaction produces besides its outcome.
      const settleCtx = {
        paymentRef, paidSignal, failSignal, transactionId, signature, payload, paymentId,
        apptEvent: null,
        lateSettlement: null,
      };
      const result = await db.runTransaction((tx) => settlePaymentInTransaction(tx, settleCtx));
      apptEventAfter = settleCtx.apptEvent;

      if (result === "not_found") return res.status(404).send("Payment not found");
      if (result === "replay")    return res.status(409).send("Duplicate transaction");

      if (apptEventAfter) {
        const snap = await db.doc(`appointments/${apptEventAfter.id}`).get();
        if (snap.exists) {
          // `from` is reconstructed rather than read: the document already
          // carries the new status by now, and the only state the webhook ever
          // moves an appointment out of is AWAITING_PAYMENT.
          await logAppointmentEvent(
            { ...snap.data(), status: "AWAITING_PAYMENT" },
            apptEventAfter.id, apptEventAfter.to,
            { uid: "hesabpay", role: "SYSTEM", name: "HesabPay" },
            apptEventAfter.reason
          );
        }
      }

      // On an explicit failure of a reserved booking, hand back the referral
      // credit + promo use that were spent atomically at checkout (best-effort,
      // outside the transaction — mirrors expireAbandonedPayments). Uses the
      // pre-transaction snapshot; the FAILED transition already ran exactly once.
      // Money arrived for a booking that had already been given up on. The
      // transaction recorded the refund owed; the customer needs to hear it
      // from us before she hears it from her bank statement.
      if (settleCtx.lateSettlement) {
        const late = settleCtx.lateSettlement;
        alertable("PAYMENT_FAILED", "Payment arrived after the booking was closed — refund owed", {
          paymentId: late.paymentId,
          refundRequestId: late.refundRequestId,
          amount: late.amount,
          previousStatus: late.previousStatus,
        });
        if (late.customerId) {
          await db.collection("notifications").add({
            recipientId: late.customerId,
            type:        "PAYMENT",
            msgKey:      "PAYMENT_LATE_REFUND",
            msgParams:   { amount: late.amount },
            title:       "Payment received late — refund on the way",
            body:        `Your payment of ${late.amount} AFN arrived after the booking had already been cancelled, so we are refunding it.`,
            isRead:      false,
            createdAt:   Date.now(),
            relatedId:   late.paymentId,
          });
        }
      }

      if (result === "failed" && payment.reserved && !payment.type) {
        await refundReservation({
          customerId:   payment.customerId,
          referralUsed: payment.referralUsed,
          promoId:      payment.promoCode,
        });
      }
      return res.status(200).send("OK");
    } catch (err) {
      alertable("PAYMENT_FAILED", "Webhook processing error", { error: String(err && err.message || err) });
      return res.status(500).send("Processing error");
    }
  }
);

// ── expireAbandonedPayments (scheduled) ───────────────────────────────────────
//
// A customer who opens HesabPay checkout and never completes (or never
// returns) leaves an AWAITING_PAYMENT appointment + PENDING payment forever —
// invisible clutter that also makes "is this slot really free" ambiguous.
// Runs hourly; anything older than 2 hours and still unpaid is expired.
const ABANDONED_PAYMENT_WINDOW_MS = 2 * 60 * 60 * 1000;

exports.expireAbandonedPayments = onSchedule(
  { schedule: "every 60 minutes", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - ABANDONED_PAYMENT_WINDOW_MS;
    const stale = await db.collection("payments")
      .where("status", "==", "PENDING")
      .where("createdAt", "<", cutoff)
      .get();

    if (stale.empty) return;

    let count = 0;
    for (const doc of stale.docs) {
      // Transaction with a status re-check so we never clobber a payment the
      // webhook just settled (PAID) in the same window — a blind batch.update
      // could overwrite it with EXPIRED and cancel an appointment the provider
      // was already credited for.
      const outcome = await db.runTransaction(async (tx) => {
        const fresh = await tx.get(doc.ref);
        if (!fresh.exists) return null;
        const payment = fresh.data();
        if (payment.status !== "PENDING") return null; // already settled/handled
        tx.update(doc.ref, { status: "EXPIRED" });
        // Only a booking payment owns the appointment. TIP payments also carry an
        // appointmentId (of a real, already-paid booking) — cancelling it here
        // would wrongly kill a live booking, so skip anything with a `type`.
        if (payment.appointmentId && !payment.type) {
          tx.update(db.doc(`appointments/${payment.appointmentId}`), { status: "CANCELLED" });
        }
        return payment;
      });
      if (!outcome) continue;
      // A reserved online checkout spent the referral credit + promo use up front;
      // since it was abandoned, hand them back (legacy payments never spent them
      // until settlement, so they have nothing to refund).
      if (outcome.reserved && !outcome.type) {
        await refundReservation({
          customerId:   outcome.customerId,
          referralUsed: outcome.referralUsed,
          promoId:      outcome.promoCode,
        });
      }
      if (outcome.appointmentId && !outcome.type) {
        await recordDemandSignal({
          kind: "CHECKOUT_ABANDONED",
          serviceName: outcome.serviceName || "",
          salonId: outcome.salonId || "",
        });
        const snap = await db.doc(`appointments/${outcome.appointmentId}`).get();
        if (snap.exists) {
          await logAppointmentEvent(
            { ...snap.data(), status: "AWAITING_PAYMENT" },
            outcome.appointmentId, "CANCELLED", null,
            "Checkout abandoned — payment never completed"
          );
        }
      }
      count++;
    }
    logger.log(`expireAbandonedPayments: expired ${count} stale payment(s)`);
  }
);

exports.recordProviderPayout = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admins only.");
  }

  const { providerId, method } = request.data || {};
  if (!providerId) {
    throw new HttpsError("invalid-argument", "providerId is required.");
  }
  assertDocId(providerId, "providerId");

  const balanceRef = db.doc(`provider_balances/${providerId}`);

  const result = await db.runTransaction(async (tx) => {
    const balSnap = await tx.get(balanceRef);
    const owed    = balSnap.exists ? Number(balSnap.data().owedAmount || 0) : 0;
    if (owed <= 0) {
      throw new HttpsError("failed-precondition", "Nothing owed to this provider.");
    }
    const payoutRef = db.collection("payouts").doc();
    tx.set(payoutRef, {
      providerId,
      amount:    owed,
      method:    method || "MANUAL",
      paidBy:    appUser.uid,
      createdAt: Date.now(),
    });
    tx.set(
      balanceRef,
      { owedAmount: 0, updatedAt: Date.now() },
      { merge: true }
    );
    return { payoutId: payoutRef.id, amount: owed };
  });

  await db.collection("notifications").doc().set({
    recipientId: providerId,
    type:        "SYSTEM",
    msgKey:      "PAYOUT_SENT",
      msgParams:   { amount: result.amount },
      title:       "Payout Sent",
    body:        `You have been paid AFN ${result.amount}.`,
    isRead:      false,
    createdAt:   Date.now(),
    relatedId:   result.payoutId,
  });

  return result;
});

// ── recordRefundProcessed (callable, admin-only) ──────────────────────────────
//
// Marks a refund_requests entry as PROCESSED once the admin has actually sent
// the money back outside the app (HesabPay has no automated refund API wired).
// Also flips the underlying payment to REFUNDED so it stops showing as a
// pending refund. Admin-only; clients cannot write refund_requests or payments.
exports.recordRefundProcessed = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admins only.");
  }

  const { refundRequestId } = request.data || {};
  if (!refundRequestId) {
    throw new HttpsError("invalid-argument", "refundRequestId is required.");
  }
  assertDocId(refundRequestId, "refundRequestId");

  const refundRef = db.doc(`refund_requests/${refundRequestId}`);

  await db.runTransaction(async (tx) => {
    const refundSnap = await tx.get(refundRef);
    if (!refundSnap.exists) throw new HttpsError("not-found", "Refund request not found.");
    const refund = refundSnap.data();
    if (refund.status === "PROCESSED") return;

    tx.update(refundRef, {
      status:      "PROCESSED",
      processedBy: appUser.uid,
      processedAt: Date.now(),
    });
    if (refund.paymentId) {
      tx.update(db.doc(`payments/${refund.paymentId}`), { status: "REFUNDED" });
    }
    tx.set(db.collection("notifications").doc(), {
      recipientId: refund.customerId,
      type:        "SYSTEM",
      msgKey:      "REFUND_PROCESSED",
      msgParams:   { amount: refund.amount },
      title:       "Refund Processed",
      body:        `Your refund of AFN ${refund.amount} has been processed.`,
      isRead:      false,
      createdAt:   Date.now(),
      relatedId:   refundRequestId,
    });
  });

  return { processed: true };
});

// previewPromo (any signed-in user): validates a code against a specific salon
// service and returns the discount so the customer can see it applied BEFORE
// committing to the booking. Reuses the exact same resolver createPaymentSession
// uses, so what's previewed is what's charged.
exports.previewPromo = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const { code, salonId, serviceName, serviceNames } = request.data || {};
  const requested =
    Array.isArray(serviceNames) && serviceNames.length ? serviceNames : (serviceName ? [serviceName] : []);
  if (!code || !salonId || requested.length === 0) {
    throw new HttpsError("invalid-argument", "code, salonId and at least one service are required.");
  }
  const salonSnap = await db.doc(`salons/${salonId}`).get();
  if (!salonSnap.exists) throw new HttpsError("not-found", "Salon not found.");
  const { total: listPrice, invalid } = resolveServicesTotal(salonSnap.data().pricePerService, requested);
  if (invalid.length > 0 || listPrice <= 0) {
    throw new HttpsError("failed-precondition", "This service has no valid price.");
  }
  const promo = await resolvePromoDiscount(code, listPrice);
  return {
    valid:          true,
    code:           promo.code,
    listPrice,
    discountAmount: promo.discount,
    finalPrice:     Math.max(0, listPrice - promo.discount),
  };
});

// upsertPromoCode (admin): create or update a code. The code string is the
// document ID (uppercased), so re-saving the same code edits it in place.
exports.upsertPromoCode = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");

  const d = request.data || {};
  const code = String(d.code || "").trim().toUpperCase();
  if (!code || !/^[A-Z0-9]{3,20}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Code must be 3–20 letters/numbers.");
  }
  const discountPercent = Math.max(0, Math.min(100, Number(d.discountPercent || 0)));
  const discountAmount  = Math.max(0, Number(d.discountAmount || 0));
  if (discountPercent <= 0 && discountAmount <= 0) {
    throw new HttpsError("invalid-argument", "Set a percentage or a fixed discount.");
  }
  const maxUses   = Math.max(0, Math.floor(Number(d.maxUses || 0)));
  const expiresAt = Math.max(0, Math.floor(Number(d.expiresAt || 0)));

  const ref = db.doc(`promo_codes/${code}`);
  const existing = await ref.get();
  await ref.set({
    code,
    discountPercent,
    discountAmount,
    maxUses,
    expiresAt,
    active:    d.active === false ? false : true,
    usedCount: existing.exists ? Number(existing.data().usedCount || 0) : 0,
    createdAt: existing.exists ? (existing.data().createdAt || Date.now()) : Date.now(),
    updatedAt: Date.now(),
  });
  return { code };
});

// setPromoActive (admin): enable/disable a code without deleting it (keeps its
// usage history intact).
exports.setPromoActive = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");
  const code = String((request.data || {}).code || "").trim().toUpperCase();
  const active = (request.data || {}).active === true;
  if (!code) throw new HttpsError("invalid-argument", "code is required.");
  await db.doc(`promo_codes/${code}`).update({ active, updatedAt: Date.now() });
  return { code, active };
});

const STUCK_PAYMENT_GRACE_MS = 30 * 60 * 1000;

/** Payments that were started, never settled, and are past any live checkout. */

exports.adminStuckPayments = onCall({ region: "us-central1" }, async (request) => {
  await assertAdmin(request);
  const cutoff = Date.now() - STUCK_PAYMENT_GRACE_MS;

  const snap = await db.collection("payments")
    .where("status", "==", "PENDING")
    .where("createdAt", "<", cutoff)
    .orderBy("createdAt", "desc")
    .limit(50)
    .get();

  const rows = [];
  for (const d of snap.docs) {
    const p = d.data() || {};
    // The appointment's state is what decides whether this matters: a customer
    // waiting on a booking nobody has seen is the case worth acting on.
    let appointment = null;
    if (p.appointmentId) {
      const a = await db.doc(`appointments/${p.appointmentId}`).get();
      if (a.exists) appointment = { id: a.id, ...a.data() };
    }
    rows.push({
      paymentId:     d.id,
      amount:        Number(p.amount || 0),
      currency:      p.currency || "AFN",
      method:        p.method || "",
      createdAt:     Number(p.createdAt || 0),
      hesabSessionId: p.hesabSessionId || "",
      customerId:    p.customerId || "",
      salonId:       p.salonId || "",
      serviceName:   p.serviceName || "",
      appointmentId: p.appointmentId || "",
      bookingCode:   (appointment && appointment.bookingCode) || "",
      appointmentStatus: (appointment && appointment.status) || "",
      customerName:  (appointment && appointment.customerName) || "",
      customerPhone: (appointment && appointment.customerPhone) || "",
    });
  }
  return { ok: true, graceMinutes: STUCK_PAYMENT_GRACE_MS / 60000, rows };
});

/**
 * Settle a stuck payment, after a human has confirmed it really was paid.
 *
 * Runs settlePaymentInTransaction -- the same code the webhook runs, not a
 * second implementation of it -- so every guard still applies: the replay key,
 * the status re-read, the provider credit, the appointment transition. Settling
 * one that was already settled is a no-op rather than a double credit.
 */

exports.adminSettleStuckPayment = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const paymentId = String(d.paymentId || "").trim();
  const reference = String(d.transactionId || "").trim();

  if (!isValidDocId(paymentId)) {
    throw new HttpsError("invalid-argument", "A valid paymentId is required.");
  }
  if (!reference) {
    throw new HttpsError(
      "invalid-argument",
      "Enter the HesabPay transaction reference you verified, so the settlement is traceable to it."
    );
  }

  const paymentRef = db.doc(`payments/${paymentId}`);
  const before = await paymentRef.get();
  if (!before.exists) throw new HttpsError("not-found", "No such payment.");

  const ctx = {
    paymentRef,
    paidSignal: true,
    failSignal: false,
    // Recorded as the replay key, so settling the same reference twice -- from
    // this screen or from a webhook that arrives late -- cannot credit twice.
    transactionId: reference,
    signature: `admin:${me.uid}`,
    payload: { adminSettled: true, by: me.uid },
    paymentId,
    apptEvent: null,
    lateSettlement: null,
  };

  const outcome = await db.runTransaction((tx) => settlePaymentInTransaction(tx, ctx));

  if (ctx.apptEvent) {
    const snap = await db.doc(`appointments/${ctx.apptEvent.id}`).get();
    if (snap.exists) {
      await logAppointmentEvent(
        { ...snap.data(), status: "AWAITING_PAYMENT" },
        ctx.apptEvent.id, ctx.apptEvent.to,
        { uid: me.uid, role: "ADMIN", name: me.name },
        `Settled by hand after verifying HesabPay reference ${reference}`
      );
    }
  }

  await logAdminAction(me, "SETTLE_STUCK_PAYMENT", {
    paymentId, reference, outcome,
    amount: Number((before.data() || {}).amount || 0),
    refundRequestId: ctx.lateSettlement ? ctx.lateSettlement.refundRequestId : null,
  });

  // "stale" here means the booking was already closed, so the settlement became
  // a refund the admin now owes the customer. Hand the id back rather than
  // leaving them to hunt for it in the Refunds tab.
  return {
    ok: true,
    outcome,
    refundRequestId: ctx.lateSettlement ? ctx.lateSettlement.refundRequestId : null,
  };
});

// ── Demand signals ────────────────────────────────────────────────────────────
//
// What a customer wanted and did not get. Successful bookings are recorded in
// detail and failure is invisible, which means supply decisions -- which salon
// to recruit next, in which district, for which service -- are made on instinct.
//
// Carries no identity, by design and not by omission. The question is how much
// demand a district has for a service, which is answerable in aggregate, while a
// per-person record of what a woman in Kabul searched for is a trail this
// platform should not accumulate. The rules enforce the absence rather than
// trusting callers to keep leaving it out.
//
// Best-effort throughout: a signal that fails to write must never affect the
// request that produced it. Losing one data point is nothing; failing a booking
// to record that a booking failed would be absurd.
async function recordDemandSignal(signal) {
  try {
    await db.collection("demand_signals").add({
      kind:        String(signal.kind || ""),
      districtKey: String(signal.districtKey || "").slice(0, 64),
      category:    String(signal.category || "").slice(0, 32),
      serviceName: String(signal.serviceName || "").slice(0, 120),
      salonId:     String(signal.salonId || ""),
      lang:        String(signal.lang || ""),
      at:          Date.now(),
    });
  } catch (e) {
    logger.warn("recordDemandSignal failed", e);
  }
}

// Exposed only to the integration tests, which exercise settlement against a
// real Firestore rather than a mock. Behind an env var so the deploy analyser
// never sees an extra export on the money path; index.js re-exports it under
// the same guard, so functions/test/settlement.integration.js is unchanged.
if (process.env.SAFEBEAUTY_TEST_HOOKS === "1") {
  exports.__testHooks = { settlePaymentInTransaction, db };
}
