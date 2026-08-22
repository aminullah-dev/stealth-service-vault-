/**
 * SafeBeauty Cloud Functions — HesabPay payment integration.
 *
 * Why this lives server-side:
 *   The HesabPay API key is a secret that grants access to the platform's
 *   merchant account. It must NEVER ship inside the Android APK (which can be
 *   decompiled). All HesabPay calls happen here, where the key is held as a
 *   Firebase Secret and never leaves Google's servers.
 *
 * Money flow (prepay-at-booking, fixed global commission):
 *   1. Customer picks a service + slot → app calls createPaymentSession().
 *   2. We read the salon's price + the platform commission %, create an
 *      appointment in AWAITING_PAYMENT, create a HesabPay checkout session,
 *      and return the checkout URL to the app.
 *   3. Customer pays in the HesabPay web checkout.
 *   4. HesabPay calls hesabPayWebhook() → we verify the signature, mark the
 *      payment PAID, split the amount (platform commission vs. provider net),
 *      flip the appointment to PENDING so the provider can confirm it, and
 *      notify the provider.
 *
 * Configuration (set before deploy):
 *   firebase functions:secrets:set HESAB_API_KEY
 *   firebase functions:secrets:set HESAB_WEBHOOK_SECRET
 *   # Optional non-secret base URL via functions/.env:
 *   #   HESAB_BASE_URL=https://api.hesab.com/api/v1
 *
 * HesabPay API — confirmed live field names (create-session):
 *   Request  : items[]{id, name, price}, email, redirect_success_url, redirect_failure_url
 *   Response : { success, session_id, url, expires_at } — checkout link is `url`
 *   Webhook  : lookup by session_id stored at payment creation time
 */

const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated, onDocumentWritten, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { defineSecret, defineString } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");
const { promoDiscountFor, computeCheckout, resolveServicesTotal, validateGiftAmount, loyaltyToCredit, offerDiscountFor, lastMinuteDiscount, packageDiscountFor } = require("./lib/money");
const { expandBooked, serviceSlotSpan, hasSlotConflict } = require("./lib/slots");
const { isPaidSignal, isFailSignal, isUnderpaid } = require("./lib/webhook");
const { isValidDocId } = require("./lib/validate");
const { averageRating } = require("./lib/reviews");
const { bookingCodeFromBytes, normalizeBookingCode } = require("./lib/booking");
const { phoneKey } = require("./lib/phone");
const { SlotTakenError, pendingWrites, commitBookingAtomically, slotConflictWindow } = require("./lib/reservation");

// Reject a malformed / path-unsafe document id before it is interpolated into a
// Firestore doc path — defense-in-depth: a value with a slash makes an
// odd-segment path (unhandled 500), and untrusted strings don't belong in paths.
function assertDocId(id, field) {
  if (!isValidDocId(id)) {
    throw new HttpsError("invalid-argument", `Invalid ${field}.`);
  }
}

admin.initializeApp();
const db = admin.firestore();

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

// Referral rewards (AFN). Both are granted when a referred user's identity is
// verified (see reviewKyc): the new user gets a welcome credit, the friend who
// invited them gets a referrer credit. Both are auto-applied at checkout.
const REFERRAL_WELCOME_CREDIT  = 100;
const REFERRAL_REFERRER_CREDIT = 100;

// ── Helpers ──────────────────────────────────────────────────────────────────

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

// Refund a checkout-time reservation (referral credit + one promo use) when a
// reserved booking never durably completes — a HesabPay create/session failure,
// a failed write, or an abandoned online payment that later expires. Only
// payments written with `reserved:true` are ever routed here; legacy
// spend-at-settlement payments are untouched. Best-effort with logged failures.
async function refundReservation({ customerId, referralUsed, promoId }) {
  const used = Number(referralUsed || 0);
  if (used > 0 && customerId) {
    await db.doc(`users/${customerId}`)
      .set({ referralCredit: admin.firestore.FieldValue.increment(used) }, { merge: true })
      .catch((e) => logger.error("refundReservation: referral", e));
  }
  if (promoId) {
    await db.doc(`promo_codes/${promoId}`)
      .set({ usedCount: admin.firestore.FieldValue.increment(-1) }, { merge: true })
      .catch((e) => logger.error("refundReservation: promo", e));
  }
}

// The app's `users/{uid}` documents are keyed by a UUID the client generates
// at registration (see RegisterViewModel) — NOT by the Firebase Auth uid that
// `request.auth.uid` carries. The two are only linked via `firebaseEmail`.
// Every callable that needs "who is this app user" must resolve through here
// instead of using request.auth.uid directly, or it silently tags data with
// an identity the rest of the app (which queries by the app-level uid) can
// never match — the appointment becomes invisible everywhere.
async function resolveAppUser(request) {
  // Lowercased on both sides: Firebase Auth normalizes emails to lowercase,
  // and registration stores firebaseEmail lowercased to match.
  const email = String(request.auth.token.email || "").toLowerCase();
  if (!email) {
    throw new HttpsError("failed-precondition", "No email on the auth token.");
  }
  const q = await db.collection("users").where("firebaseEmail", "==", email).limit(1).get();
  if (q.empty) {
    // Fallback for older docs whose stored firebaseEmail casing differs from
    // the (always-lowercase) token email: the uid_map bridge written at login
    // (syncUidMap verifies ownership case-insensitively) still resolves them.
    const mapSnap = await db.doc(`uid_map/${request.auth.uid}`).get();
    if (mapSnap.exists) {
      const mapped = await db.doc(`users/${mapSnap.data().appUid}`).get();
      if (mapped.exists) return { uid: mapped.id, ...mapped.data() };
    }
    throw new HttpsError("not-found", "User profile not found.");
  }
  const doc = q.docs[0];
  // Opportunistically keep uid_map fresh (see syncUidMap) so firestore.rules'
  // me() resolves correctly even for app versions that predate the explicit
  // post-signin sync call. Best-effort — never blocks the actual request.
  db.doc(`uid_map/${request.auth.uid}`)
    .set({ appUid: doc.id, updatedAt: Date.now() })
    .catch((err) => logger.error("resolveAppUser: uid_map sync failed", err));
  return { uid: doc.id, ...doc.data() };
}

// ── createPaymentSession (callable) ──────────────────────────────────────────

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
    const { salonId, serviceName: serviceNameInput, serviceNames, appointmentDate, notes, email, method, promoCode, staffId, packageId } =
      request.data || {};
    const paymentMethod = method === "CASH" ? "CASH" : "ONLINE";

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

    if (!salonId || requestedServiceNames.length === 0 || !appointmentDate) {
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
    const { services, total, invalid } = resolveServicesTotal(salon.pricePerService, requestedServiceNames);
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
    const slotSpan = serviceSlotSpan(
      services.map((s) => s.name),
      salon.durationPerService,
      salon.slotDurationMinutes
    );

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

    const readNearbyAppointments = async (reader) => {
      const q = db.collection("appointments")
        .where("salonId", "==", salonId)
        .where("appointmentDate", ">=", conflictWindow.start)
        .where("appointmentDate", "<", conflictWindow.end);
      const snap = await (reader ? reader.get(q) : q.get());
      return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    };

    if (hasSlotConflict(await readNearbyAppointments(null), appointmentDate, slotSpan, resolvedStaffId, slotMinutes)) {
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
    const totalDiscount = promo.discount + offerDiscount + lastMinuteDisc + packageDiscount;

    // Reserve referral credit + the promo use ATOMICALLY at checkout, reading the
    // LIVE balance/usedCount inside the transaction — so two of the customer's
    // bookings in flight can't over-spend the same credit, and a limited code
    // can't exceed maxUses (the earlier reads were only a plan). The split is
    // recomputed from the live credit here; the payment is written reserved:true
    // so settlement does NOT spend again, and the abandon / write-failure /
    // HesabPay-failure paths refund it (refundReservation).
    let afterPromo, referralUsed, price, commissionAmount, providerNet;
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
      ({ afterPromo, referralUsed, price, commissionAmount, providerNet } = split);
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
        customerId:     uid,
        customerName:   user.name  || "",
        customerPhone:  user.phone || "",
        salonId,
        salonName:      salon.salonName || "",
        serviceName,
        services,
        slotsCount:     slotSpan,
        staffId:        resolvedStaffId,
        staffName:      resolvedStaffName,
        appointmentDate,
        status:         "PENDING",
        paymentMethod:  "CASH",
        createdAt:      Date.now(),
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
            owedAmount: admin.firestore.FieldValue.increment(-commissionAmount),
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
          appointmentDate, slotSpan, resolvedStaffId, slotMinutes);
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
      customerId:    uid,
      customerName:  user.name  || "",
      customerPhone: user.phone || "",
      salonId,
      salonName:     salon.salonName || "",
      serviceName,
      services,
      slotsCount:    slotSpan,
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
        appointmentDate, slotSpan, resolvedStaffId, slotMinutes);
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

    const { recipientPhone, amount, message } = request.data || {};
    const gift = validateGiftAmount(amount);
    if (!gift.ok) {
      throw new HttpsError("invalid-argument", "Enter a valid gift amount (50–50,000 AFN).");
    }

    // Recipient must be a registered user (we credit their existing wallet).
    const phone = normalizePhone(recipientPhone);
    const q = await db.collection("users").where("phone", "==", phone).limit(1).get();
    if (q.empty) throw new HttpsError("not-found", "No account uses that phone number.");
    const recipient = q.docs[0];
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
      const result = await db.runTransaction(async (tx) => {
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
        // had their reserved credit refunded. Ignore it (200, no-op).
        if (fresh.status !== "PENDING") return "stale";

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
          tx.update(db.doc(`appointments/${fresh.appointmentId}`), { status: "PENDING" });
          apptEventAfter = { id: fresh.appointmentId, to: "PENDING", reason: "Online payment received" };
          // Track what the provider is owed (platform pays out separately).
          // Guarded: an empty providerId would make db.doc("provider_balances/")
          // throw synchronously, 500-ing every webhook retry and stranding the
          // customer's PAID booking in AWAITING_PAYMENT forever.
          if (fresh.providerId) {
            tx.set(
              db.doc(`provider_balances/${fresh.providerId}`),
              {
                providerId: fresh.providerId,
                owedAmount: admin.firestore.FieldValue.increment(fresh.providerNet),
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
            apptEventAfter = { id: fresh.appointmentId, to: "CANCELLED", reason: "Online payment failed" };
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
      });

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

// ── Server-side PIN authentication ────────────────────────────────────────────
//
// Why this is server-side:
//   Verifying client-side would require downloading the ENTIRE users
//   collection (every pinHash + salt) to the device — meaning anyone who
//   reached the login screen could exfiltrate the whole credential table and
//   brute-force 6-digit PINs offline. Login sends only the PIN here; the
//   hashes never leave the server. Firestore rules lock `users` reads to the
//   owner/admin, so the table can no longer be dumped.

// Byte-for-byte mirror of the Android PinHasher: PBKDF2WithHmacSHA256, 65,536
// iterations, 256-bit output, salt is Base64(NO_WRAP) bytes. Must match exactly
// or every PIN verification fails.
function pbkdf2Hash(pin, saltB64) {
  const salt = Buffer.from(String(saltB64), "base64");
  return crypto.pbkdf2Sync(String(pin), salt, 65536, 32, "sha256").toString("base64");
}

// Constant-time string compare to avoid leaking match progress via timing.
function hashesEqual(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

// NOTE: authenticateWithPin was removed. It was a pre-auth callable that hashed
// a submitted numeric string against EVERY user's pinHash and, on a match,
// returned that user's salt + firebaseEmail — a mass account-takeover oracle
// (one unauthenticated request tested the whole user base, and password login
// stores its hash in the same pinHash field). PINs are gone; the client only
// uses authenticateWithPassword (which resolves ONE account by phone first).
// Deleting the export removes the function on the next `firebase deploy
// --only functions`.

/**
 * Password login, keyed by phone number (the app's login identifier now that
 * PINs are gone). Unlike authenticateWithPin — which matched a numeric PIN
 * against EVERY user — this resolves the ONE account for the given phone and
 * checks its password hash, so two users sharing a password can never collide.
 * Returns the same shape as authenticateWithPin: on success the client derives
 * the auth password from (password + salt) and signs in; the hash never leaves
 * the server. No auth required (this IS the pre-auth login step).
 */
exports.authenticateWithPassword = onCall({ region: "us-central1" }, async (request) => {
  // Unauthenticated by necessity — this IS the login. Throttle both the caller
  // and the targeted phone so an attacker can neither grind one account from
  // many IPs nor sweep many accounts from one.
  const _ip = callerIp(request);
  const _phoneKey = normalizePhone(String((request.data || {}).phone || "").trim() || "unknown");
  await enforceRateLimit(`login-ip:${_ip}`, 30, 15 * 60 * 1000);
  await enforceRateLimit(`login-phone:${_phoneKey}`, 10, 15 * 60 * 1000);
  const d = request.data || {};
  const phone    = String(d.phone || "").trim();
  const password = String(d.password || "");
  if (!phone || !password) {
    return { mode: "INVALID" };
  }

  // Resolve the account with indexed lookups instead of reading the whole users
  // collection and matching in JavaScript. That scan was correct and did not
  // scale: at 100,000 users every sign-in read 100,000 documents, which is a
  // cost problem, a latency ceiling, and — on an endpoint that by definition
  // cannot require auth — a denial-of-service surface.
  //
  // Three bounded attempts, in descending order of how most accounts are stored:
  //
  //   1. the normalized form the app has written since PhoneUtils existed,
  //   2. the raw string, for a record stored exactly as it was typed,
  //   3. phoneDigits, the subscriber-tail key that adminBackfillPhoneKeys
  //      writes onto older records whose phone field was never normalized.
  //
  // phoneDigits is written ONLY by the server. Registration is a client write,
  // so a client-supplied login key would let one account claim another's key and
  // lock its owner out — the password check would then run against the wrong
  // record. Accounts created by the current app are always found by attempt 1,
  // so the key is a recovery path for legacy records rather than the norm.
  const attempts = [
    ["phone",       normalizePhone(phone)],
    ["phone",       phone],
    ["phoneDigits", phoneKey(phone)],
  ];

  let doc = null;
  for (const [field, value] of attempts) {
    if (!value) continue;
    const q = await db.collection("users").where(field, "==", value).limit(1).get();
    if (!q.empty) { doc = q.docs[0]; break; }
  }
  if (!doc) return { mode: "INVALID" };

  const u = doc.data();
  if (!u.pinHash || !u.salt) return { mode: "INVALID" };
  if (!hashesEqual(pbkdf2Hash(password, u.salt), u.pinHash)) {
    return { mode: "INVALID" };
  }

  return {
    mode:            "REAL",
    uid:             doc.id,
    name:            u.name  || "",
    role:            u.role  || "CUSTOMER",
    status:          u.status || "",
    rejectionReason: u.rejectionReason || "",
    kycStatus:       u.kycStatus || "NONE",
    firebaseEmail:   u.firebaseEmail || "",
    salt:            u.salt,
  };
});

/**
 * Bridges the two identity schemes: Firebase Auth's uid (request.auth.uid,
 * what firestore.rules' me() sees) and the app's own uid (the client-
 * generated UUID that is the actual users/{uid} document ID — see
 * RegisterViewModel). The client calls this right after firebaseAuth.signIn
 * succeeds so security rules can resolve `me()` to the real app uid via this
 * map. Verifies the claimed appUid actually belongs to the signed-in account
 * (its firebaseEmail must match the auth token's email) before trusting it,
 * so a client can't claim someone else's identity.
 */
exports.syncUidMap = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const appUid = String((request.data || {}).appUid || "");
  if (!appUid) {
    throw new HttpsError("invalid-argument", "appUid is required.");
  }
  const email = String(request.auth.token.email || "").toLowerCase();
  if (!email) {
    throw new HttpsError("failed-precondition", "No email on the auth token.");
  }
  const userSnap = await db.doc(`users/${appUid}`).get();
  // Case-insensitive: older docs may store firebaseEmail as typed, while the
  // auth token's email is always lowercase.
  if (!userSnap.exists ||
      String(userSnap.data().firebaseEmail || "").toLowerCase() !== email) {
    throw new HttpsError("permission-denied", "appUid does not match the signed-in account.");
  }
  await db.doc(`uid_map/${request.auth.uid}`).set({
    appUid,
    updatedAt: Date.now(),
  });
  return { synced: true };
});

/**
 * Updates the caller's OWN pinHash + salt (used by both Change-PIN and
 * Forgot-PIN). Server-side because the Forgot-PIN flow signs in fresh and has
 * no uid_map entry yet, so a direct client write can be denied by the rules'
 * me() lookup AFTER the Firebase Auth password was already reset — leaving
 * pinHash pointing at the old PIN and locking the account out entirely.
 * resolveAppUser identifies the caller by their auth-token email (the same
 * ownership the rules grant for direct writes) and also repopulates uid_map,
 * so the session is fully usable right after a PIN reset.
 */
exports.updatePinHash = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const pinHash = String((request.data || {}).pinHash || "");
  const salt    = String((request.data || {}).salt || "");
  if (!pinHash || !salt) {
    throw new HttpsError("invalid-argument", "pinHash and salt are required.");
  }
  const appUser = await resolveAppUser(request);
  await db.doc(`users/${appUser.uid}`).update({ pinHash, salt });
  return { updated: true };
});

/**
 * Submits the caller's identity-verification (KYC) documents for admin review.
 * The tazkira/selfie photos were already uploaded client-side to the private
 * kyc/{uid}/ Storage path; only their URLs + the text fields are passed here.
 * Server-side so kycStatus can't be self-set to APPROVED — the whole point of
 * verification. Allowed only from NONE/REJECTED (can't resubmit while PENDING
 * or overwrite an APPROVED verification).
 */
exports.submitKyc = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const d = request.data || {};
  const tazkiraNumber   = String(d.tazkiraNumber || "").trim();
  const addressProvince = String(d.addressProvince || "").trim();
  const addressDetail   = String(d.addressDetail || "").trim();
  const tazkiraPhotoUrl = String(d.tazkiraPhotoUrl || "").trim();
  const selfiePhotoUrl  = String(d.selfiePhotoUrl || "").trim();
  // Optional identity details (also editable later by the admin). Capped.
  const birthYear         = String(d.birthYear || "").trim().slice(0, 40);
  const tazkiraIssueDate  = String(d.tazkiraIssueDate || "").trim().slice(0, 40);
  const tazkiraExpiryDate = String(d.tazkiraExpiryDate || "").trim().slice(0, 40);

  if (!tazkiraNumber || !addressProvince || !addressDetail ||
      !tazkiraPhotoUrl || !selfiePhotoUrl) {
    throw new HttpsError(
      "invalid-argument",
      "Tazkira number, address, and both photos are required."
    );
  }

  const appUser = await resolveAppUser(request);
  const current = appUser.kycStatus || "NONE";
  if (current === "PENDING") {
    throw new HttpsError("failed-precondition", "Your verification is already under review.");
  }
  if (current === "APPROVED") {
    throw new HttpsError("failed-precondition", "You are already verified.");
  }

  await db.doc(`users/${appUser.uid}`).update({
    kycStatus:          "PENDING",
    kycRejectionReason: "",
    tazkiraNumber,
    birthYear,
    tazkiraIssueDate,
    tazkiraExpiryDate,
    addressProvince,
    addressDetail,
    tazkiraPhotoUrl,
    selfiePhotoUrl,
  });
  return { submitted: true };
});

/**
 * Admin approves or rejects a user's KYC submission. Admin-only. On approval,
 * kycStatus → APPROVED (unlocking booking for customers / go-live for
 * providers); on rejection, → REJECTED with a reason the user sees so they can
 * resubmit. Notifies the user either way.
 */
exports.reviewKyc = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const reviewer = await resolveAppUser(request);
  if (reviewer.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admins only.");
  }

  const d = request.data || {};
  const targetUid = String(d.targetUid || "");
  const approve   = d.approve === true;
  const reason    = String(d.rejectionReason || "").trim();
  if (!targetUid) {
    throw new HttpsError("invalid-argument", "targetUid is required.");
  }
  assertDocId(targetUid, "targetUid");
  if (!approve && !reason) {
    throw new HttpsError("invalid-argument", "A rejection reason is required.");
  }

  const targetRef = db.doc(`users/${targetUid}`);
  const targetSnap = await targetRef.get();
  if (!targetSnap.exists) {
    throw new HttpsError("not-found", "User not found.");
  }

  await targetRef.update({
    kycStatus:          approve ? "APPROVED" : "REJECTED",
    kycRejectionReason: approve ? "" : reason,
  });

  await db.collection("notifications").doc().set({
    recipientId: targetUid,
    type:        "SYSTEM",
    title:       approve ? "Identity Verified" : "Verification Rejected",
    body:        approve
      ? "Your identity has been verified. You can now continue."
      : `Your verification was rejected: ${reason}`,
    isRead:      false,
    createdAt:   Date.now(),
    relatedId:   targetUid,
  });

  // ── Referral reward ──────────────────────────────────────────────────────────
  // Rewards are granted here, at identity verification, rather than at
  // registration — passing KYC needs a real tazkira + selfie + admin review, so
  // this gates the reward against someone farming credit with fake accounts. The
  // newly-verified user gets a welcome credit; the friend whose code they used
  // gets a referrer credit. Runs once per user (guarded by referralRewarded).
  if (approve) {
    const target = targetSnap.data();
    const referredBy = String(target.referredBy || "").trim().toUpperCase();
    if (referredBy && target.referralRewarded !== true) {
      try {
        await db.runTransaction(async (tx) => {
          const freshTarget = await tx.get(targetRef);
          if (freshTarget.data().referralRewarded === true) return; // already done
          const refQ = await tx.get(
            db.collection("users").where("referralCode", "==", referredBy).limit(1)
          );
          // Mark rewarded regardless so a bad/self code can't be retried forever.
          // The welcome credit is granted only when the matched referrer is a
          // DIFFERENT user — a user whose referredBy equals their own code must
          // not self-grant AFN at approval.
          const referrerIsOther = !refQ.empty && refQ.docs[0].id !== targetUid;
          tx.update(targetRef, {
            referralRewarded: true,
            referralCredit: admin.firestore.FieldValue.increment(
              referrerIsOther ? REFERRAL_WELCOME_CREDIT : 0
            ),
          });
          if (!refQ.empty && refQ.docs[0].id !== targetUid) {
            const referrerRef = refQ.docs[0].ref;
            tx.update(referrerRef, {
              referralCredit: admin.firestore.FieldValue.increment(REFERRAL_REFERRER_CREDIT),
            });
            tx.set(db.collection("notifications").doc(), {
              recipientId: refQ.docs[0].id,
              type:        "SYSTEM",
              msgKey:      "REFERRAL_REWARD",
              msgParams:   { credit: REFERRAL_REFERRER_CREDIT },
              title:       "Referral Reward",
              body:        `A friend you invited just joined — you earned AFN ${REFERRAL_REFERRER_CREDIT} credit!`,
              isRead:      false,
              createdAt:   Date.now(),
              relatedId:   targetUid,
            });
          }
        });
      } catch (err) {
        logger.error("reviewKyc: referral reward failed (non-fatal)", err);
      }
    }
  }

  return { reviewed: true };
});

/**
 * Creates a provider's salon at registration. Server-side because the salon's
 * providerId must be authoritative (the app-level uid, not the Firebase Auth
 * uid) and because at registration time the uid_map bridge isn't populated yet,
 * so firestore.rules' me() can't resolve — a direct client write can't be
 * verified. resolveAppUser looks the provider up by their auth-token email
 * (their users/{uid} doc already exists at this point) and sets providerId to
 * the real app uid. The salon starts hidden (isAvailable=false) and unverified
 * until an admin approves the provider.
 */
exports.createProviderSalon = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "PROVIDER") {
    throw new HttpsError("permission-denied", "Only providers can create a salon.");
  }

  const { salonName, district, services } = request.data || {};
  if (!salonName || !district || !Array.isArray(services) || services.length === 0) {
    throw new HttpsError(
      "invalid-argument",
      "salonName, district and at least one service are required."
    );
  }

  // One salon per provider — return the existing one instead of duplicating
  // (e.g. if the client retries after a dropped response).
  const existing = await db.collection("salons")
    .where("providerId", "==", appUser.uid).limit(1).get();
  if (!existing.empty) {
    return { salonId: existing.docs[0].id, alreadyExisted: true };
  }

  const ref = db.collection("salons").doc();
  await ref.set({
    providerId:          appUser.uid,
    providerName:        appUser.name || "",
    salonName:           String(salonName),
    district:            String(district),
    services:            services.map(String),
    isAvailable:         false,   // hidden until an admin approves the provider
    rating:              0,
    workingHours:        [],
    slotDurationMinutes: 60,
    pricePerService:     {},
    confirmedCount:      0,
    isVerified:          false,
  });
  return { salonId: ref.id };
});

/**
 * Pre-auth lookup of an account's Firebase Auth email by phone, for the
 * password-reset flows (Forgot-PIN / Set-New-PIN). Returns ONLY the email
 * fields — never pinHash/salt — so `users` reads can stay locked to owner/admin.
 */
// Phone normalization mirroring the app's PhoneUtils.normalizeForLogin, so a
// number typed in any common format (0700…, 700…, +93700…, 0093700…) resolves
// to the single canonical "+93…" (or an international "+…") that registration
// stored — otherwise the same person looks like "no account" or a new one.
function cleanPhone(raw) {
  const t = String(raw || "").trim();
  const plus = t.startsWith("+");
  const digits = t.replace(/\D/g, "");
  return plus ? "+" + digits : digits;
}
function normalizeAfghanPhone(raw) {
  let c = cleanPhone(raw);
  if (c.startsWith("+93")) return c;
  if (c.startsWith("0093")) return "+93" + c.slice(4);
  if (c.startsWith("93") && c.length >= 11) return "+" + c;
  c = c.replace(/^\+/, "");
  if (c.startsWith("0")) c = c.slice(1);
  return "+93" + c;
}
function normalizePhone(raw) {
  const c = cleanPhone(raw);
  return c.startsWith("+") ? c : normalizeAfghanPhone(raw);
}

// Resolves an account by phone (used by password recovery AND the registration
// uniqueness check). Normalizes the input, and falls back to the raw string so
// any legacy record still matches.
exports.lookupAccountByPhone = onCall({ region: "us-central1" }, async (request) => {
  const raw = String((request.data || {}).phone || "").trim();
  if (!raw) return { found: false };
  // Throttle before touching the database: this endpoint is unauthenticated and
  // answers "does this phone have an account?", which is exactly what an
  // enumeration sweep wants. 20 lookups per IP per 10 minutes is far above any
  // real signup/recovery flow and far below a useful sweep.
  await enforceRateLimit(`lookup:${callerIp(request)}`, 20, 10 * 60 * 1000);
  const phone = normalizePhone(raw);

  let q = await db.collection("users").where("phone", "==", phone).limit(1).get();
  if (q.empty && phone !== raw) {
    q = await db.collection("users").where("phone", "==", raw).limit(1).get();
  }
  if (q.empty) return { found: false };

  const doc = q.docs[0];
  const u   = doc.data();
  return {
    found:         true,
    uid:           doc.id,
    firebaseEmail: u.firebaseEmail || "",
    email:         u.email || "",
  };
});

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
      throw new HttpsError("failed-precondition", "This booking can no longer be cancelled.");
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
      tx.update(payDoc.ref, { status: "CANCELLED" });
      if (providerId) {
        tx.set(
          db.doc(`provider_balances/${providerId}`),
          {
            providerId,
            owedAmount: admin.firestore.FieldValue.increment(payment.commissionAmount || 0),
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
            owedAmount: admin.firestore.FieldValue.increment(-payment.providerNet),
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
    const KABUL_OFFSET_MS = 4.5 * 3600 * 1000;
    const kabulDay = (ts) => Math.floor((Number(ts) + KABUL_OFFSET_MS) / 86400000);
    const wantedDay = kabulDay(result.appt.appointmentDate);

    const entries = await db.collection("waitlist")
      .where("salonId", "==", result.appt.salonId)
      .get();
    const first = entries.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .filter((w) => w.status === "WAITING" && kabulDay(w.requestedDate) === wantedDay)
      .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))[0];
    if (first) {
      await db.doc(`waitlist/${first.id}`).update({ status: "SLOT_AVAILABLE" });
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

  const snap = await db.collection("appointments")
    .where("salonId", "==", salonId)
    .get();
  const inWindow = snap.docs
    .map((d) => d.data())
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
      throw new HttpsError("failed-precondition", "This booking can no longer be rescheduled.");
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
    const otherSnap = await tx.get(
      db.collection("appointments").where("salonId", "==", appt.salonId)
    );
    const others = otherSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    if (hasSlotConflict(others, dateMs, span, String(appt.staffId || ""), slotMinutes, appointmentId)) {
      throw new HttpsError("failed-precondition", "That time is no longer available.");
    }

    tx.update(apptRef, { appointmentDate: dateMs, status: "PENDING", reminderSent: false });
    writeAppointmentEvent(tx, appt, appointmentId, "PENDING", actor,
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
      throw new HttpsError("failed-precondition", "Only a pending booking can be confirmed.");
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
      throw new HttpsError("failed-precondition", "You've already reviewed this booking.");
    }
    // Feedback only makes sense once a booking was actually accepted/served.
    // COMPLETED is included because completePastAppointments auto-flips
    // CONFIRMED → COMPLETED ~2h after the start time — which is exactly when a
    // provider sits down to report a no-show, so excluding it would kill the
    // primary post-visit reporting window.
    if (appt.status !== "CONFIRMED" && appt.status !== "PENDING" && appt.status !== "COMPLETED") {
      throw new HttpsError("failed-precondition", "This booking can't be reviewed.");
    }

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
    return { reportId: reportRef.id };
  });

  return { reported: true, reportId: result.reportId };
});

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

// ── Promo codes ───────────────────────────────────────────────────────────────

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

// ── resolveCustomerReport (admin) ─────────────────────────────────────────────
//
// Admin closing out a flagged misconduct report: marks it REVIEWED so it leaves
// the open-reports queue, and — if [suspend] is true — suspends the reported
// customer's account. customer_reports is client-write-locked, so this status
// flip can only happen here.
exports.resolveCustomerReport = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");
  const d = request.data || {};
  const reportId = String(d.reportId || "");
  const suspend  = d.suspend === true;
  if (!reportId) throw new HttpsError("invalid-argument", "reportId is required.");

  const reportRef = db.doc(`customer_reports/${reportId}`);
  const snap = await reportRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Report not found.");
  const report = snap.data();

  await reportRef.update({
    status: "REVIEWED",
    resolvedBy: appUser.uid,
    resolvedAt: Date.now(),
    actionTaken: suspend ? "SUSPENDED" : "DISMISSED",
  });

  if (suspend && report.customerId) {
    await db.doc(`users/${report.customerId}`).set(
      { status: "SUSPENDED" }, { merge: true }
    );
  }
  return { reportId, actionTaken: suspend ? "SUSPENDED" : "DISMISSED" };
});

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

    let q = db.collection("users");
    if (wantRole) q = q.where("role", "==", wantRole);
    if (wantLang) q = q.where("lang", "==", wantLang);
    const usersSnap = await q.get();

    const tokens = [];
    usersSnap.forEach((doc) => {
      if (districtOwners && !districtOwners.has(doc.id)) return;
      const t = String(doc.data().fcmToken || "");
      if (t) tokens.push(t);
    });
    if (tokens.length === 0) {
      logger.log("pushOnBroadcastCreated: no recipients matched the filters");
      return;
    }

    const title = "SafeBeauty";
    const body  = String(b.message);
    const data  = {
      type:             "BROADCAST",
      relatedId:        "",
      notif_type:       "BROADCAST",
      notif_related_id: "",
    };

    for (let i = 0; i < tokens.length; i += 500) {
      const batch = tokens.slice(i, i + 500);
      try {
        await admin.messaging().sendEachForMulticast({
          tokens: batch,
          notification: { title, body },
          data,
          android: { priority: "high" },
        });
      } catch (err) {
        logger.warn("pushOnBroadcastCreated: batch send failed", {
          error: String(err.message || err),
        });
      }
    }
    logger.log(`pushOnBroadcastCreated: sent to ${tokens.length} device(s)` +
      ` [role=${wantRole || "any"} lang=${wantLang || "any"} district=${wantDistrict || "any"}]`);
  }
);

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
    const ownPathFragment = `/reviews%2F${user.uid}%2F`;
    const urls = Array.isArray(imageUrls)
      ? imageUrls
          .filter((u) => typeof u === "string" && u.includes(ownPathFragment))
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
async function assertAdmin(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const appUser = await resolveAppUser(request);
  if (appUser.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admins only.");
  }
  return appUser;
}

/**
 * Refuse an action by a suspended account.
 *
 * Deliberately not folded into resolveAppUser: a suspended person must still be
 * able to sign in, read their own history and reach support -- otherwise a
 * suspension is indistinguishable from a broken account, and the one route for
 * disputing it is the route that gets closed. What stops is acting.
 */
function assertNotSuspended(appUser) {
  if (appUser && appUser.suspended === true) {
    throw new HttpsError(
      "permission-denied",
      "This account is suspended. Please contact support."
    );
  }
}

/**
 * Log something a person should be told about, under a stable machine label.
 *
 * Alert policies match on `jsonPayload.alert` rather than on the message text.
 * A policy that greps prose breaks the day someone rewords a log line, and it
 * breaks silently — the alert simply stops firing, which is indistinguishable
 * from nothing going wrong. The label is the contract; the message is for the
 * human who reads it afterwards.
 *
 * Kinds in use:
 *   BOOKING_FAILED    a customer tried to book and could not
 *   PAYMENT_FAILED    money moved, or failed to, without the record agreeing
 *   BACKUP_FAILED     the nightly export did not complete
 *   INTEGRITY_CRITICAL the nightly sweep found something that loses money
 *   ALERT_PIPELINE_TEST a deliberate drill — see adminTestAlert
 */
function alertable(kind, message, details) {
  logger.error(message, { alert: kind, ...(details || {}) });
}

/** Append a tamper-evident record of a privileged admin action. Best-effort. */
async function logAdminAction(adminUser, action, details) {
  try {
    await db.collection("admin_audit").add({
      adminUid:  adminUser.uid,
      adminName: adminUser.name || "",
      action,
      details:   details || {},
      createdAt: Date.now(),
    });
  } catch (e) {
    logger.error("logAdminAction failed", e);
  }
}

// ── Multi-admin management ────────────────────────────────────────────────────

/** Promote a user to ADMIN (idempotent). The target is also marked APPROVED so
 *  a pending/suspended account can still administer. */
exports.grantAdmin = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const targetUid = String((request.data || {}).targetUid || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");
  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  await ref.update({ role: "ADMIN", status: "APPROVED" });
  await logAdminAction(me, "GRANT_ADMIN", { targetUid, targetName: snap.data().name || "" });
  return { ok: true };
});

/** Demote an admin back to a normal CUSTOMER. Refuses to remove the LAST admin
 *  (so the platform can never lock itself out). */
exports.revokeAdmin = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const targetUid = String((request.data || {}).targetUid || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");
  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  if (snap.data().role !== "ADMIN") throw new HttpsError("failed-precondition", "That user is not an admin.");

  // Never leave the platform with zero admins.
  const admins = await db.collection("users").where("role", "==", "ADMIN").get();
  if (admins.size <= 1) {
    throw new HttpsError("failed-precondition", "Can't remove the last remaining admin.");
  }
  await ref.update({ role: "CUSTOMER", status: "APPROVED" });
  await logAdminAction(me, "REVOKE_ADMIN", { targetUid, targetName: snap.data().name || "" });
  return { ok: true };
});

// ── "Solve any problem" toolbox ───────────────────────────────────────────────

/** Reset a user's password. Sets a new salt + pinHash (Firestore) AND the
 *  derived Firebase Auth password (Admin SDK), so the user can immediately sign
 *  in with `newPassword`. The admin then tells the user the temporary password. */
exports.adminResetPassword = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const targetUid   = String(d.targetUid || "");
  const newPassword = String(d.newPassword || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");
  if (newPassword.length < 4)   throw new HttpsError("invalid-argument", "Password must be at least 4 characters.");

  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  const u = snap.data();
  const email = String(u.firebaseEmail || "");
  if (!email) throw new HttpsError("failed-precondition", "This account has no Firebase Auth email.");

  // Mirror PinHasher exactly: 16-byte base64 salt; pinHash = PBKDF2(pw),
  // authPassword = PBKDF2("AUTH:"+pw).
  const salt         = crypto.randomBytes(16).toString("base64");
  const pinHash      = pbkdf2Hash(newPassword, salt);
  const authPassword = pbkdf2Hash("AUTH:" + newPassword, salt);

  let authRecord;
  try {
    authRecord = await admin.auth().getUserByEmail(email);
  } catch (e) {
    throw new HttpsError("not-found", "No Firebase Auth user for this account.");
  }
  await admin.auth().updateUser(authRecord.uid, { password: authPassword });
  await ref.update({ pinHash, salt });
  await logAdminAction(me, "RESET_PASSWORD", { targetUid, targetName: u.name || "" });
  return { ok: true };
});

/** Fix a user's name and/or phone. Phone is normalized and uniqueness-checked
 *  (it is the login identifier), which is why a client can't self-edit it. */
exports.adminUpdateUser = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const targetUid = String(d.targetUid || "");
  if (!isValidDocId(targetUid)) throw new HttpsError("invalid-argument", "Bad targetUid.");

  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");

  const updates = {};
  if (d.name != null) {
    const name = String(d.name).trim();
    if (name) updates.name = name;
  }
  if (d.phone != null) {
    const phone = normalizePhone(String(d.phone));
    if (!phone || phone.replace(/\D/g, "").length < 7) {
      throw new HttpsError("invalid-argument", "Invalid phone number.");
    }
    const dup = await db.collection("users").where("phone", "==", phone).limit(1).get();
    if (!dup.empty && dup.docs[0].id !== targetUid) {
      throw new HttpsError("already-exists", "Another account already uses that phone.");
    }
    updates.phone = phone;
  }
  // Identity / address fields the admin curates on behalf of a provider. These
  // are frozen against client self-edits in firestore.rules; the Admin SDK
  // bypasses those rules. An empty string is allowed (clearing a value).
  const CURATED = ["addressProvince","addressDetail","tazkiraNumber",
                   "birthYear","tazkiraIssueDate","tazkiraExpiryDate"];
  for (const f of CURATED) {
    if (d[f] != null) updates[f] = String(d[f]).trim().slice(0, 200);
  }
  if (Object.keys(updates).length === 0) {
    throw new HttpsError("invalid-argument", "Nothing to update.");
  }
  await ref.update(updates);
  await logAdminAction(me, "UPDATE_USER", { targetUid, updates });
  return { ok: true, updates };
});

/** Adjust a provider's balance for dispute resolution / goodwill. `delta` is in
 *  AFN and may be negative (they owe more) or positive (platform owes more). */
exports.adminAdjustProviderBalance = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const providerId = String(d.providerId || "");
  const delta = Math.round(Number(d.delta));
  const reason = String(d.reason || "").slice(0, 500);
  if (!isValidDocId(providerId)) throw new HttpsError("invalid-argument", "Bad providerId.");
  if (!Number.isFinite(delta) || delta === 0) throw new HttpsError("invalid-argument", "delta must be a non-zero number.");

  await db.doc(`provider_balances/${providerId}`).set(
    { owedAmount: admin.firestore.FieldValue.increment(delta) },
    { merge: true }
  );
  await logAdminAction(me, "ADJUST_BALANCE", { providerId, delta, reason });
  return { ok: true };
});

/** Grant (or deduct) referral credit to a customer — real checkout money used
 *  for goodwill / manual refunds. Notifies the customer when credit is added. */
exports.adminGrantCredit = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const customerId = String(d.customerId || "");
  const amount = Math.round(Number(d.amount));
  const reason = String(d.reason || "").slice(0, 500);
  if (!isValidDocId(customerId)) throw new HttpsError("invalid-argument", "Bad customerId.");
  if (!Number.isFinite(amount) || amount === 0) throw new HttpsError("invalid-argument", "amount must be a non-zero number.");

  const ref  = db.doc(`users/${customerId}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "User not found.");
  await ref.update({ referralCredit: admin.firestore.FieldValue.increment(amount) });

  if (amount > 0) {
    await db.collection("notifications").add({
      recipientId: customerId,
      type:        "SYSTEM",
      msgKey:      "CREDIT_ADDED",
      msgParams:   { amount, reason: reason || "" },
      title:       "Credit added to your account 🎁",
      body:        `You've received ${amount} AFN in credit${reason ? " — " + reason : ""}.`,
      isRead:      false,
      createdAt:   Date.now(),
      relatedId:   "",
    });
  }
  await logAdminAction(me, "GRANT_CREDIT", { customerId, amount, reason });
  return { ok: true };
});

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
const LIVE_APPOINTMENT_STATUSES = ["AWAITING_PAYMENT", "PENDING", "CONFIRMED"];

/** Commit a batch every 400 writes (Firestore's hard limit is 500). */
async function flushIfFull(batch, count) {
  if (count >= 400) { await batch.commit(); return { batch: db.batch(), count: 0 }; }
  return { batch, count };
}

exports.requestAccountDeletion = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");

  const user    = await resolveAppUser(request);
  const uid     = user.uid;              // app-level id (users/{uid})
  const authUid = request.auth.uid;      // Firebase Auth uid

  // An admin deleting themselves could orphan the platform. Refuse — another
  // admin must revoke the role first (revokeAdmin already blocks the last one).
  if (user.role === "ADMIN") {
    throw new HttpsError(
      "failed-precondition",
      "Admin accounts can't be self-deleted. Have another admin revoke your admin role first."
    );
  }

  const now = Date.now();
  let batch = db.batch();
  let count = 0;
  const add = async (fn) => { fn(batch); count++; ({ batch, count } = await flushIfFull(batch, count)); };

  // 1. Cancel every live appointment on both sides of the marketplace.
  let cancelled = 0;
  for (const field of ["customerId", "providerId"]) {
    const snap = await db.collection("appointments")
      .where(field, "==", uid)
      .where("status", "in", LIVE_APPOINTMENT_STATUSES)
      .get();
    for (const d of snap.docs) {
      await add((b) => b.update(d.ref, {
        status: "CANCELLED",
        cancelledAt: now,
        cancelReason: "ACCOUNT_DELETED",
      }));
      cancelled++;
    }
  }

  // 2. Strip personal fields from the financial records we keep.
  const asCustomer = await db.collection("appointments").where("customerId", "==", uid).get();
  for (const d of asCustomer.docs) {
    await add((b) => b.update(d.ref, { customerName: "", customerPhone: "", notes: "" }));
  }

  // 3. Reviews stay (they inform other customers) but lose their author.
  const reviews = await db.collection("reviews").where("customerId", "==", uid).get();
  for (const d of reviews.docs) {
    await add((b) => b.update(d.ref, { customerName: "" }));
  }

  // 4. Purely personal rows are deleted outright.
  for (const [coll, field] of [
    ["favorites",     "customerId"],
    ["waitlist",      "customerId"],
    ["notifications", "uid"],
  ]) {
    try {
      const snap = await db.collection(coll).where(field, "==", uid).get();
      for (const d of snap.docs) await add((b) => b.delete(d.ref));
    } catch (e) {
      logger.warn(`requestAccountDeletion: skipping ${coll}`, e);
    }
  }

  // 5. A departing provider's salon must stop taking bookings.
  const salons = await db.collection("salons").where("providerId", "==", uid).get();
  for (const d of salons.docs) {
    await add((b) => b.update(d.ref, { hidden: true, isVerified: false, deletedAt: now }));
  }

  // 6. The uid_map bridge (there may be several, one per Auth account used).
  const maps = await db.collection("uid_map").where("appUid", "==", uid).get();
  for (const d of maps.docs) await add((b) => b.delete(d.ref));
  await add((b) => b.delete(db.doc(`uid_map/${authUid}`)));

  // 7. The user document itself — the home of every remaining PII field
  //    (name, phone, email, tazkira number, KYC photo URLs, fcmToken).
  await add((b) => b.delete(db.doc(`users/${uid}`)));

  if (count > 0) await batch.commit();

  // 8. Private images. Best-effort: a Storage hiccup must not resurrect an
  //    account whose Firestore identity is already gone.
  for (const prefix of [`kyc/${uid}/`, `profile/${uid}/`, `reviews/${uid}/`]) {
    try {
      await admin.storage().bucket().deleteFiles({ prefix });
    } catch (e) {
      logger.warn(`requestAccountDeletion: storage cleanup failed for ${prefix}`, e);
    }
  }

  // 9. Finally the credential itself. Last, so a failure above leaves the user
  //    able to sign in and retry rather than locked out mid-deletion.
  try {
    await admin.auth().deleteUser(authUid);
  } catch (e) {
    logger.error("requestAccountDeletion: auth delete failed", e);
    throw new HttpsError("internal", "Your data was removed but the sign-in could not be closed. Contact support.");
  }

  await db.collection("admin_audit").add({
    adminUid: uid, adminName: "(self)", action: "DELETE_ACCOUNT",
    details: { role: user.role || "", cancelledAppointments: cancelled, salonsHidden: salons.size },
    createdAt: now,
  });

  logger.log(`requestAccountDeletion: deleted ${uid} (cancelled ${cancelled} appointment(s))`);
  return { ok: true, cancelledAppointments: cancelled };
});

// ── Admin: onboard a salon directly ───────────────────────────────────────────
//
// A marketplace has a cold-start problem: salon owners in Kabul are signed up in
// person, not by self-registration. This lets the admin create the owner's
// account AND their salon in one step, so a salon can be live before its owner
// ever opens the app. The owner signs in afterwards with the phone + password
// the admin sets here.
//
// Ordering is deliberate: Auth account -> user doc -> salon doc, with rollback
// of the Auth account if either write fails. A stranded Auth account with no
// user doc permanently bricks that phone number (every retry hits
// "email-already-exists" while login-by-phone finds nothing).

exports.adminCreateSalon = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d  = request.data || {};

  const ownerName = String(d.ownerName || "").trim();
  const rawPhone  = String(d.phone || "").trim();
  const password  = String(d.password || "");
  const salonName = String(d.salonName || "").trim();
  const district  = String(d.district || "").trim();
  const services  = Array.isArray(d.services)
    ? d.services.map((s) => String(s).trim()).filter(Boolean)
    : [];
  const prices    = (d.pricePerService && typeof d.pricePerService === "object")
    ? d.pricePerService : {};

  if (!ownerName)        throw new HttpsError("invalid-argument", "Owner name is required.");
  if (!rawPhone)         throw new HttpsError("invalid-argument", "Phone number is required.");
  if (password.length < 6) throw new HttpsError("invalid-argument", "Password must be at least 6 characters.");
  if (!salonName)        throw new HttpsError("invalid-argument", "Salon name is required.");
  if (!district)         throw new HttpsError("invalid-argument", "District is required.");
  if (!services.length)  throw new HttpsError("invalid-argument", "At least one service is required.");

  const phone = normalizePhone(rawPhone);

  // The phone is the login identifier, so it must be unique platform-wide.
  const clash = await db.collection("users").where("phone", "==", phone).limit(1).get();
  if (!clash.empty) {
    throw new HttpsError("already-exists", "An account with this phone number already exists.");
  }

  // Mirror PinHasher / RegisterViewModel exactly so the owner can sign in from
  // the app with the plain password the admin hands them.
  const uid           = crypto.randomUUID();
  const salt          = crypto.randomBytes(16).toString("base64");
  const pinHash       = pbkdf2Hash(password, salt);
  const authPassword  = pbkdf2Hash("AUTH:" + password, salt);
  const firebaseEmail = `${uid.replace(/-/g, "")}@sb.app`;
  const referralCode  = "SB" + uid.replace(/-/g, "").slice(0, 6).toUpperCase();
  const now           = Date.now();

  let authUid = null;
  try {
    const rec = await admin.auth().createUser({ email: firebaseEmail, password: authPassword });
    authUid = rec.uid;

    await db.doc(`users/${uid}`).set({
      uid, name: ownerName, phone, email: "",
      role: "PROVIDER",
      // Admin-created salons are live immediately — the admin has met the owner,
      // which is exactly what the approval queue exists to establish.
      status: "APPROVED",
      pinHash, salt, firebaseEmail,
      createdAt: now,
      referralCode, referredBy: "",
      loyaltyPoints: 0,
      kycStatus: "NONE",
    });

    const salonRef = db.collection("salons").doc();
    await salonRef.set({
      providerId:          uid,
      providerName:        ownerName,
      salonName,
      district,
      services,
      pricePerService:     prices,
      isAvailable:         false,   // owner opens for business by setting hours
      rating:              0,
      workingHours:        [],
      slotDurationMinutes: 60,
      confirmedCount:      0,
      isVerified:          true,    // vouched for by the admin who added it
      createdAt:           now,
      createdByAdmin:      me.uid,
    });

    await logAdminAction(me, "CREATE_SALON", {
      targetUid: uid, targetName: ownerName, salonName, district, salonId: salonRef.id,
    });

    logger.log(`adminCreateSalon: ${salonName} (${salonRef.id}) for ${phone} by ${me.uid}`);
    return { ok: true, salonId: salonRef.id, providerUid: uid, phone };
  } catch (e) {
    // Roll back the Auth account so the phone number stays usable.
    if (authUid) {
      try { await admin.auth().deleteUser(authUid); } catch (_) {}
      try { await db.doc(`users/${uid}`).delete(); } catch (_) {}
    }
    if (e instanceof HttpsError) throw e;
    logger.error("adminCreateSalon failed", e);
    throw new HttpsError("internal", "Could not create the salon.");
  }
});

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

// ── Booking codes and the appointment event trail ────────────────────────────
//
// A booking used to be identifiable only by its Firestore document id: twenty
// random characters that nobody can read down a phone line. When a customer
// rings to ask what happened to her appointment, the person answering needs
// something she can say out loud, and a record of what actually happened to it.
//
// The alphabet and the normalizer live in lib/booking, which is unit-tested:
// getting either wrong means two customers can hold the same reference, or a
// correctly-read code fails to resolve.
function randomBookingCode() {
  return bookingCodeFromBytes(crypto.randomBytes(6));
}

/**
 * Reserve a booking code nobody else holds.
 *
 * The reservation is a document create, which fails if the id is taken — so
 * uniqueness is decided by Firestore rather than by a read-then-write that two
 * simultaneous bookings could both pass. A code reserved by a booking that then
 * fails to write is simply never used; that costs one tiny document, which is
 * the cheaper end of the trade against ever issuing the same code twice.
 */
async function reserveBookingCode(attempts = 6) {
  for (let i = 0; i < attempts; i++) {
    const code = randomBookingCode();
    try {
      await db.doc(`booking_codes/${code}`).create({ createdAt: Date.now() });
      return code;
    } catch (e) {
      if (i === attempts - 1) {
        logger.error("reserveBookingCode: exhausted attempts", e);
        throw new HttpsError("internal", "Could not allocate a booking reference.");
      }
    }
  }
}

/**
 * The shape of one entry in an appointment's history.
 *
 * Denormalizes salonId/customerId so the trail can be queried for "everything
 * that happened to this salon's bookings" without joining back through the
 * appointment, and the actor so the record still reads correctly after that
 * person's name or role changes.
 */
function appointmentEvent(appt, appointmentId, to, actor, reason) {
  return {
    appointmentId,
    bookingCode: appt.bookingCode || "",
    salonId:     appt.salonId     || "",
    customerId:  appt.customerId  || "",
    from:        appt.status      || "",
    to,
    at:          Date.now(),
    actorUid:    (actor && actor.uid)  || "system",
    actorRole:   (actor && actor.role) || "SYSTEM",
    actorName:   (actor && actor.name) || "",
    reason:      String(reason || ""),
  };
}

/**
 * Append to the trail from inside a transaction or batch.
 *
 * Preferred over the fire-and-forget version: the history entry then commits
 * with the status change it describes, so the two can never disagree.
 */
function writeAppointmentEvent(txOrBatch, appt, appointmentId, to, actor, reason) {
  txOrBatch.set(
    db.collection("appointment_events").doc(),
    appointmentEvent(appt, appointmentId, to, actor, reason)
  );
}

/** Append outside a transaction. Best-effort: never fail a booking over history. */
async function logAppointmentEvent(appt, appointmentId, to, actor, reason) {
  try {
    await db.collection("appointment_events").add(
      appointmentEvent(appt, appointmentId, to, actor, reason)
    );
  } catch (e) {
    logger.error("logAppointmentEvent failed", e);
  }
}

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

  // A missing field cannot be queried for, so this walks by creation order and
  // skips the ones already done rather than filtering server-side.
  const snap = await db.collection("appointments").orderBy("createdAt", "asc").limit(limit).get();

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
  return { ok: true, scanned: snap.size, assigned, done: snap.size < limit };
});

// ── adminTestAlert ────────────────────────────────────────────────────────────
//
// Fires a synthetic alert so the whole path can be checked end to end: log →
// log-based metric → alert policy → notification → a person's phone.
//
// Monitoring that has never fired is monitoring nobody knows works, and the
// moment you find out is the moment you needed it. Each link here can break
// quietly — a metric filter that matches nothing, a notification channel that
// was never verified, a policy left disabled — and none of those failures
// announce themselves.
//
// Safe to run any time: it touches no data and describes itself as a drill in
// the alert body, so whoever receives it is not misled into thinking the
// platform is broken.
exports.adminTestAlert = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const note = String((request.data || {}).note || "").slice(0, 200);

  alertable(
    "ALERT_PIPELINE_TEST",
    "DRILL — this is a test of the alerting pipeline, not a real failure",
    { triggeredBy: me.uid, triggeredByName: me.name || "", note, at: Date.now() }
  );

  await logAdminAction(me, "TEST_ALERT", { note });
  return {
    ok: true,
    firedAt: Date.now(),
    expect: "A notification should arrive within about five minutes.",
  };
});

// ── adminBackfillPhoneKeys ────────────────────────────────────────────────────
//
// Writes the phoneDigits lookup key onto accounts that predate it.
//
// Only needed for records whose phone field was never normalized — anything the
// current app wrote is already found by an exact match on `phone`. Server-only
// by design: a client-supplied login key would let one account claim another's
// and lock its owner out.
//
// Also reports collisions rather than silently picking a winner. Two accounts
// sharing a subscriber key means login is ambiguous for that number, and that is
// a fact a human needs to see, not something a backfill should paper over.
exports.adminBackfillPhoneKeys = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const limit = Math.min(500, Math.max(1, Number((request.data || {}).limit || 300)));

  const snap = await db.collection("users").orderBy("createdAt", "asc").limit(limit).get();

  const seen = new Map();   // key -> first uid that claimed it
  const collisions = [];
  let written = 0;

  for (const d of snap.docs) {
    const data = d.data();
    const key  = phoneKey(data.phone);
    if (!key) continue;

    if (seen.has(key)) {
      collisions.push({ key, uids: [seen.get(key), d.id] });
    } else {
      seen.set(key, d.id);
    }

    if (data.phoneDigits !== key) {
      await d.ref.update({ phoneDigits: key });
      written += 1;
    }
  }

  await logAdminAction(me, "BACKFILL_PHONE_KEYS", {
    scanned: snap.size, written, collisions: collisions.length,
  });
  if (collisions.length) {
    logger.error("adminBackfillPhoneKeys: duplicate phone keys", { collisions });
  }
  return {
    ok: true,
    scanned: snap.size,
    written,
    collisions,
    done: snap.size < limit,
  };
});

// ── adminSetUserStatus ────────────────────────────────────────────────────────
//
// Suspend or reinstate an account. The platform could already adjust a user's
// money and rewrite their password, but not stop them booking -- the only
// lever against someone abusing the service was deleting them outright, which
// destroys the evidence along with the account.
//
// A suspension is recorded on the user document rather than in Firebase Auth so
// the person can still sign in and read their own history; what stops is the
// ability to act, which the booking callables check.
exports.adminSetUserStatus = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d  = request.data || {};
  const targetUid = String(d.targetUid || "").trim();
  const suspend   = d.suspend === true;
  const reason    = String(d.reason || "").trim().slice(0, 300);

  if (!targetUid) throw new HttpsError("invalid-argument", "targetUid is required.");
  if (suspend && !reason) {
    throw new HttpsError("invalid-argument", "A suspension needs a reason on the record.");
  }

  const ref  = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "No such user.");
  const target = snap.data();

  if (target.role === "ADMIN" && suspend) {
    throw new HttpsError("failed-precondition", "Remove admin access before suspending this account.");
  }

  await ref.update({
    suspended:       suspend,
    suspendedReason: suspend ? reason : "",
    suspendedAt:     suspend ? Date.now() : 0,
    suspendedBy:     suspend ? me.uid : "",
  });

  await logAdminAction(me, suspend ? "SUSPEND_USER" : "REINSTATE_USER", {
    targetUid, targetName: target.name || "", reason,
  });
  return { ok: true, suspended: suspend };
});

// ── adminUserDossier ──────────────────────────────────────────────────────────
//
// Everything about one account in a single call: who they are, what they have
// booked, what they have paid, what has been said about them and by them.
//
// The console used to answer a support question by opening four tabs and
// eyeballing across them, which is slow at the moment it matters and easy to
// get wrong. Bounded to the most recent rows of each kind so the response stays
// a fixed size no matter how long the account has been active.
exports.adminUserDossier = onCall({ region: "us-central1" }, async (request) => {
  await assertAdmin(request);
  const targetUid = String((request.data || {}).targetUid || "").trim();
  if (!targetUid) throw new HttpsError("invalid-argument", "targetUid is required.");

  const snap = await db.doc(`users/${targetUid}`).get();
  if (!snap.exists) throw new HttpsError("not-found", "No such user.");
  const user = snap.data();

  // Strip the credential material. An admin never needs it, and a dossier is
  // exactly the kind of payload that ends up pasted into a chat window.
  const { pinHash, salt, firebaseEmail, ...safeUser } = user;

  const isProvider = user.role === "PROVIDER";
  const bookingsQ  = isProvider
    ? db.collection("appointments").where("salonId", "==", String((request.data || {}).salonId || "___none___"))
    : db.collection("appointments").where("customerId", "==", targetUid);

  const [bookings, reviews, reportsAbout, balance, salons] = await Promise.all([
    bookingsQ.orderBy("createdAt", "desc").limit(25).get().catch(() => ({ docs: [] })),
    db.collection("reviews").where("customerId", "==", targetUid)
      .limit(15).get().catch(() => ({ docs: [] })),
    db.collection("customer_reports").where("customerId", "==", targetUid)
      .limit(15).get().catch(() => ({ docs: [] })),
    db.doc(`provider_balances/${targetUid}`).get().catch(() => ({ exists: false })),
    db.collection("salons").where("providerId", "==", targetUid).get().catch(() => ({ docs: [] })),
  ]);

  const rows = (q) => (q.docs || []).map((d) => ({ id: d.id, ...d.data() }));

  return {
    user:     { id: targetUid, ...safeUser },
    salons:   rows(salons),
    bookings: rows(bookings),
    reviews:  rows(reviews),
    reports:  rows(reportsAbout),
    balance:  balance.exists ? balance.data() : null,
  };
});

// ── Abuse protection for the pre-login callables ──────────────────────────────
//
// `lookupAccountByPhone` and `authenticateWithPassword` cannot require auth —
// they run BEFORE the user has a session. That leaves two open doors:
//
//   1. Enumeration. Afghan mobile numbers are a small, guessable space, so an
//      unthrottled lookup reveals which numbers belong to SafeBeauty users. For
//      an app used by women in Kabul that is a personal-safety problem, not just
//      a privacy one.
//   2. Brute force. An unthrottled login endpoint lets an attacker grind
//      passwords for a phone number they already know.
//
// A fixed window in Firestore is enough here: the traffic is low, the counter is
// cheap, and a transaction keeps concurrent calls honest. Limits are per-caller
// (IP) and, for login, additionally per-phone so one victim can't be targeted
// from many IPs.

/**
 * Fixed-window rate limit. Throws resource-exhausted once [max] calls have been
 * made under [key] inside [windowMs]. Fails OPEN on infrastructure errors — a
 * Firestore hiccup must never lock legitimate users out of signing in.
 */
async function enforceRateLimit(key, max, windowMs) {
  const ref = db.doc(`rate_limits/${encodeURIComponent(key)}`);
  const now = Date.now();
  try {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.exists ? snap.data() : null;
      if (!d || now - (d.windowStart || 0) >= windowMs) {
        tx.set(ref, { windowStart: now, count: 1, updatedAt: now });
        return;
      }
      if ((d.count || 0) >= max) {
        const retryInSec = Math.ceil((d.windowStart + windowMs - now) / 1000);
        throw new HttpsError(
          "resource-exhausted",
          `Too many attempts. Please try again in ${retryInSec} second(s).`
        );
      }
      tx.update(ref, { count: (d.count || 0) + 1, updatedAt: now });
    });
  } catch (e) {
    if (e instanceof HttpsError) throw e;   // the limit itself — propagate
    logger.warn("enforceRateLimit failed open", e);
  }
}

/** Best-effort caller IP for a v2 callable. */
function callerIp(request) {
  const r = request.rawRequest || {};
  const fwd = (r.headers && (r.headers["x-forwarded-for"] || r.headers["X-Forwarded-For"])) || "";
  return String(fwd).split(",")[0].trim() || r.ip || "unknown";
}

/** Purge stale rate-limit counters so the collection can't grow without bound. */
exports.cleanupRateLimits = onSchedule(
  { schedule: "every 24 hours", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const snap = await db.collection("rate_limits")
      .where("updatedAt", "<", cutoff).limit(500).get();
    if (snap.empty) return;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    logger.log(`cleanupRateLimits: removed ${snap.size} stale counter(s)`);
  }
);

// ── Scheduled Firestore backup ────────────────────────────────────────────────
//
// Everything the business depends on — salons, appointments, payments, provider
// balances, KYC decisions — lives in one Firestore database with no history. A
// bad admin action, a bad deploy, or an accidental bulk delete is unrecoverable
// without an export. This writes a full daily export to a Cloud Storage bucket,
// which is the only thing that turns "we lost the bookings" into "we restore
// yesterday's".
//
// Restore (manual, deliberately not automated):
//   gcloud firestore import gs://safebeauty-backups/<TIMESTAMP>
//
// Requires, one time:
//   gcloud storage buckets create gs://safebeauty-backups --location=us-central1
//   gcloud projects add-iam-policy-binding safebeauty \
//     --member=serviceAccount:238802374530-compute@developer.gserviceaccount.com \
//     --role=roles/datastore.importExportAdmin
//   gcloud storage buckets add-iam-policy-binding gs://safebeauty-backups \
//     --member=serviceAccount:238802374530-compute@developer.gserviceaccount.com \
//     --role=roles/storage.admin

// Derived from the running project rather than hardcoded. With a staging
// project deploying the same code, a fixed bucket meant staging would export
// its own data into production's backup folder — quietly corrupting the one
// artefact a real recovery depends on, and only discovered while trying to use
// it. The bucket for each project is created by scripts/setup-backup-bucket.sh.
const BACKUP_BUCKET = `gs://${process.env.GCLOUD_PROJECT || "safebeauty"}-backups`;

exports.scheduledFirestoreBackup = onSchedule(
  { schedule: "every day 02:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const projectId = process.env.GCLOUD_PROJECT || "safebeauty";
    const client = new admin.firestore.v1.FirestoreAdminClient();
    const databaseName = client.databasePath(projectId, "(default)");
    // Kabul-local date, so a backup folder name matches the day the operator
    // would ask for ("restore Tuesday's data").
    const stamp = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kabul" });

    // exportDocuments STARTS an export and returns immediately; the operation can
    // still fail afterwards. Recording the run in Firestore, and having
    // verifyFirestoreBackup finish the story, is what turns "we called the API"
    // into "we have a backup" — the difference the comment below warns about.
    const runRef = db.doc(`system_backups/${stamp}`);

    try {
      const [response] = await client.exportDocuments({
        name: databaseName,
        outputUriPrefix: `${BACKUP_BUCKET}/${stamp}`,
        collectionIds: [],   // empty = every collection
      });
      await runRef.set({
        stamp,
        state:         "RUNNING",
        operationName: response.name || "",
        outputUri:     `${BACKUP_BUCKET}/${stamp}`,
        startedAt:     Date.now(),
        finishedAt:    0,
        error:         "",
      });
      logger.log(`scheduledFirestoreBackup: started ${response.name} -> ${BACKUP_BUCKET}/${stamp}`);
    } catch (e) {
      // Loud: a silently failing backup is worse than no backup, because you
      // only discover it the day you need to restore.
      await runRef.set({
        stamp, state: "FAILED", operationName: "",
        outputUri: `${BACKUP_BUCKET}/${stamp}`,
        startedAt: Date.now(), finishedAt: Date.now(),
        error: String((e && e.message) || e).slice(0, 500),
      }, { merge: true });
      alertable("BACKUP_FAILED", "scheduledFirestoreBackup FAILED", { error: String(e && e.message || e) });
      throw e;
    }
  }
);

// ── verifyFirestoreBackup ─────────────────────────────────────────────────────
//
// The export is asynchronous: scheduledFirestoreBackup only learns that it
// started. This closes the loop by asking the operation how it ended, so the
// console can say "last good backup: 02:00 today" rather than "we asked for one".
//
// Runs a few times after the nightly window rather than once, because a full
// export of a growing database takes an unpredictable while.
exports.verifyFirestoreBackup = onSchedule(
  { schedule: "0 3,4,6,9 * * *", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const running = await db.collection("system_backups")
      .where("state", "==", "RUNNING").limit(10).get();
    if (running.empty) return;

    const client = new admin.firestore.v1.FirestoreAdminClient();

    for (const doc of running.docs) {
      const { operationName, startedAt } = doc.data();
      if (!operationName) continue;
      const stuck = Date.now() - Number(startedAt || 0) > 12 * 60 * 60 * 1000;

      // Two independent checks, because they fail in different ways.
      //
      // Asking the long-running-operation directly is the precise answer, but it
      // reaches through a generated client whose surface is not part of any
      // stability promise — so it is attempted, and never trusted to be there.
      // The elapsed-time rule needs nothing but the clock, and is what actually
      // guarantees a stalled export cannot sit in RUNNING forever looking fine.
      let op = null;
      try {
        if (client.operationsClient && typeof client.operationsClient.getOperation === "function") {
          const [fetched] = await client.operationsClient.getOperation({ name: operationName });
          op = fetched;
        }
      } catch (e) {
        logger.warn(`verifyFirestoreBackup: could not read operation for ${doc.id}`, e);
      }

      try {
        if (op && op.done === true) {
          if (op.error && op.error.message) {
            await doc.ref.update({
              state: "FAILED", finishedAt: Date.now(),
              error: String(op.error.message).slice(0, 500),
            });
            logger.error(`verifyFirestoreBackup: ${doc.id} failed`, op.error);
          } else {
            await doc.ref.update({ state: "DONE", finishedAt: Date.now(), error: "" });
            logger.log(`verifyFirestoreBackup: ${doc.id} completed`);
          }
        } else if (stuck) {
          await doc.ref.update({
            state: "FAILED", finishedAt: Date.now(),
            error: "Export never reported completion within 12 hours.",
          });
          logger.error(`verifyFirestoreBackup: ${doc.id} stuck in RUNNING`);
        }
      } catch (e) {
        logger.error(`verifyFirestoreBackup: could not update ${doc.id}`, e);
      }
    }
  }
);

// ── pruneOldBackups ───────────────────────────────────────────────────────────
//
// Every night's export is a full copy of the database. Kept forever they are a
// bill that grows quadratically with the life of the product, for copies nobody
// will ever restore. Thirty days is long enough to notice that something was
// corrupted weeks ago and short enough that the cost stays flat.
//
// Only ever deletes a prefix that has its own DONE record older than the
// window — never a folder it does not recognise, and never the newest one.
const BACKUP_RETENTION_DAYS = 30;

exports.pruneOldBackups = onSchedule(
  { schedule: "every day 05:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - BACKUP_RETENTION_DAYS * 24 * 60 * 60 * 1000;
    const old = await db.collection("system_backups")
      .where("state", "==", "DONE")
      .where("finishedAt", "<", cutoff)
      .limit(20).get();
    if (old.empty) return;

    const bucketName = BACKUP_BUCKET.replace("gs://", "");
    const bucket = admin.storage().bucket(bucketName);

    for (const doc of old.docs) {
      try {
        await bucket.deleteFiles({ prefix: `${doc.id}/`, force: true });
        await doc.ref.update({ state: "PRUNED", prunedAt: Date.now() });
        logger.log(`pruneOldBackups: removed ${doc.id}`);
      } catch (e) {
        logger.error(`pruneOldBackups: could not remove ${doc.id}`, e);
      }
    }
  }
);

// ── reconcileIntegrity ────────────────────────────────────────────────────────
//
// Nothing in this system notices when it has quietly gone wrong.
//
// Every failure mode below has the same shape: two records that should agree
// stop agreeing, and neither side complains, because each one is individually
// valid. A payment settles but the webhook retry that flips the appointment
// never lands; a booking sits in AWAITING_PAYMENT because the customer closed
// the tab; a visit passes and nobody marks it done. Each is invisible until a
// person happens to look at exactly the right row -- usually because a customer
// is already angry.
//
// This looks for the disagreements on a schedule and writes what it finds to
// system_alerts, so the console can show them and the platform learns about its
// own problems before its customers explain them.
//
// Findings only. Nothing here repairs anything on its own: an automatic fix
// applied to a case nobody has understood yet turns one wrong record into two.
const INTEGRITY_LOOKBACK_DAYS = 30;

/** Firestore's `in` operator takes at most 10 values, so queries go in tens. */
function chunkArray(arr, size) {
  const out = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

exports.reconcileIntegrity = onSchedule(
  { schedule: "every day 04:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const now    = Date.now();
    const since  = now - INTEGRITY_LOOKBACK_DAYS * 24 * 60 * 60 * 1000;
    const findings = [];

    const add = (kind, severity, ref, detail) =>
      findings.push({ kind, severity, ref, detail });

    // 1. Paid, but the booking never moved out of AWAITING_PAYMENT.
    //    The customer has been charged and the salon has never seen the request.
    //    This is the one that costs money and trust at the same time.
    const paid = await db.collection("payments")
      .where("status", "==", "PAID").where("createdAt", ">", since).get();
    const paidApptIds = paid.docs
      .map((d) => d.data())
      .filter((p) => p.appointmentId && !p.type)
      .map((p) => p.appointmentId);

    for (const chunk of chunkArray(paidApptIds, 10)) {
      const snap = await db.collection("appointments")
        .where(admin.firestore.FieldPath.documentId(), "in", chunk).get();
      snap.docs.forEach((d) => {
        const a = d.data();
        if (a.status === "AWAITING_PAYMENT") {
          add("PAID_BUT_AWAITING", "critical", a.bookingCode || d.id,
            "Payment settled but the booking never reached the salon.");
        }
      });
    }

    // 2. Bookings stuck in AWAITING_PAYMENT well past any live checkout.
    //    expireStalePayments should have collected these; if they are here, it
    //    did not run or the payment row is missing.
    const stuck = await db.collection("appointments")
      .where("status", "==", "AWAITING_PAYMENT")
      .where("createdAt", "<", now - 24 * 60 * 60 * 1000)
      .limit(50).get();
    stuck.docs.forEach((d) => {
      const a = d.data();
      add("STUCK_AWAITING_PAYMENT", "warn", a.bookingCode || d.id,
        "Awaiting payment for over a day — expiry should have cleared it.");
    });

    // 3. Visits that happened and were never closed. completePastAppointments
    //    flips CONFIRMED past its time; a PENDING one it never touches, so a
    //    booking the salon never accepted just rots.
    const past = await db.collection("appointments")
      .where("status", "==", "PENDING")
      .where("appointmentDate", "<", now - 48 * 60 * 60 * 1000)
      .limit(50).get();
    past.docs.forEach((d) => {
      const a = d.data();
      add("NEVER_ANSWERED", "warn", a.bookingCode || d.id,
        "The visit time passed while the salon had still not accepted or declined.");
    });

    // 4. Refunds nobody has actioned. HesabPay has no automated refund API
    //    wired, so every one of these is a person owed money who is waiting on
    //    a human — and the only thing tracking that human is this list.
    const refunds = await db.collection("refund_requests")
      .where("status", "==", "PENDING")
      .where("createdAt", "<", now - 7 * 24 * 60 * 60 * 1000)
      .limit(50).get();
    refunds.docs.forEach((d) => {
      add("REFUND_OVERDUE", "critical", d.id,
        `Refund pending for more than a week (${Number(d.data().amount || 0)} AFN).`);
    });

    // 5. A backup that is not recent is not a backup.
    const lastGood = await db.collection("system_backups")
      .where("state", "==", "DONE").orderBy("finishedAt", "desc").limit(1).get();
    const lastGoodAt = lastGood.empty ? 0 : Number(lastGood.docs[0].data().finishedAt || 0);
    if (now - lastGoodAt > 48 * 60 * 60 * 1000) {
      add("BACKUP_STALE", "critical", "system_backups",
        lastGoodAt
          ? `Last verified backup finished ${Math.floor((now - lastGoodAt) / 86400000)} days ago.`
          : "No verified backup has ever been recorded.");
    }

    // 6. Negative provider balances. A payout that overshot, or commission debt
    //    that never cleared — either way the arithmetic has drifted.
    const balances = await db.collection("provider_balances").get();
    balances.docs.forEach((d) => {
      const owed = Number(d.data().owed || 0);
      if (owed < 0) {
        add("NEGATIVE_BALANCE", "warn", d.id, `Provider balance is ${owed} AFN.`);
      }
    });

    const critical = findings.filter((f) => f.severity === "critical").length;

    await db.doc(`system_alerts/${new Date(now).toLocaleDateString("en-CA", { timeZone: "Asia/Kabul" })}`)
      .set({
        ranAt: now,
        total: findings.length,
        critical,
        // Bounded: a genuinely broken day could produce thousands, and a
        // document that cannot be written tells nobody anything.
        findings: findings.slice(0, 200),
        truncated: findings.length > 200,
      });

    if (critical > 0) {
      alertable("INTEGRITY_CRITICAL",
        `reconcileIntegrity: ${critical} critical finding(s)`,
        { critical, findings: findings.slice(0, 20) });
    } else {
      logger.log(`reconcileIntegrity: ${findings.length} finding(s), none critical`);
    }
  }
);

// ── Notification localization ─────────────────────────────────────────────────
//
// Every push and in-app notification used to be written in English, to an
// audience that reads Dari and Pashto. The text is composed inside payment and
// booking transactions, so rather than restructure that money-handling code,
// each notification carries an additive `msgKey` + `msgParams`. The single push
// trigger resolves them against the recipient's language — it already reads the
// user document for the FCM token, so localization costs nothing extra.
//
// Docs written before this (or by any path that forgets msgKey) still push their
// stored English title/body, so nothing regresses.
//
// `type` is NOT the key: several distinct messages share type "SYSTEM".

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

// ── Chase unconfirmed bookings ────────────────────────────────────────────────
//
// A booking is PAID before the provider confirms it: the webhook flips it
// AWAITING_PAYMENT -> PENDING, and it stays there until the provider taps
// confirm. Nothing chased that. A provider who simply doesn't open the app
// leaves a paying customer waiting indefinitely — no confirmation, no
// cancellation, no refund, and no one told either side.
//
// That is the two-sided marketplace failure mode: the supply side going quiet
// silently ruins the demand side's experience, and the customer blames the
// platform, not the salon. This nudges the provider once per booking so the
// booking either gets confirmed or gets declined (which refunds), instead of
// hanging.
//
// Nudged once per appointment (providerNudged), so a provider who is genuinely
// away is not spammed every run.

// Timings and the give-up deadline live in lib/unconfirmed.js so the boundary
// cases are unit-tested — this decides whether real money is refunded.
const {
  UNCONFIRMED_NUDGE_AFTER_MS,
  UNCONFIRMED_ADMIN_AFTER_MS,
  unconfirmedDeadline,
} = require("./lib/unconfirmed");

exports.nudgeUnconfirmedBookings = onSchedule(
  { schedule: "every 1 hours", region: "us-central1" },
  async () => {
    const cutoff = Date.now() - UNCONFIRMED_NUDGE_AFTER_MS;
    const snap = await db.collection("appointments")
      .where("status", "==", "PENDING")
      .where("createdAt", "<", cutoff)
      .get();
    if (snap.empty) return;

    // One notification per provider, however many bookings are waiting —
    // five separate pushes would read as noise and get the app muted.
    const byProvider = new Map();
    snap.docs.forEach((d) => {
      const a = d.data();
      if (a.providerNudged) return;              // already chased this one
      if (!a.providerId) return;
      if (!byProvider.has(a.providerId)) byProvider.set(a.providerId, []);
      byProvider.get(a.providerId).push(d);
    });
    if (byProvider.size === 0) return;

    const now = Date.now();
    let notified = 0;
    for (const [providerId, docs] of byProvider) {
      const batch = db.batch();
      batch.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "NEW_BOOKING",             // taps route to the requests tab
        msgKey:      "PENDING_BOOKINGS_WAITING",
        msgParams:   { count: docs.length },
        title:       "Bookings waiting for you ⏳",
        body:        docs.length === 1
          ? "A customer has paid and is waiting for you to confirm their booking."
          : `${docs.length} customers have paid and are waiting for you to confirm their bookings.`,
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
    const adminCutoff = now - UNCONFIRMED_ADMIN_AFTER_MS;
    const needsAdmin = snap.docs.filter((d) => {
      const a = d.data();
      return !a.adminAlerted && (a.createdAt || 0) < adminCutoff;
    });
    if (needsAdmin.length) {
      const admins = await db.collection("users").where("role", "==", "ADMIN").get();
      const batch = db.batch();
      admins.docs.forEach((adminDoc) => {
        batch.set(db.collection("notifications").doc(), {
          recipientId: adminDoc.id,
          type:        "SYSTEM",
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
      try {
        // Reuses the same path as a provider decline, so the refund request,
        // the payment status and the provider's owed balance all unwind exactly
        // as they do for a manual cancellation.
        await cancelPaidAppointment(d.id, "SYSTEM", () => true, null,
          "Salon never confirmed before the deadline");
        await db.collection("notifications").add({
          recipientId: appt.customerId,
          type:        "BOOKING_CANCELLED",
          msgKey:      "BOOKING_AUTO_CANCELLED",
          msgParams:   { salon: appt.salonName || "The salon" },
          title:       "Booking cancelled — refund on the way",
          body:        `${appt.salonName || "The salon"} did not confirm your booking in time, so we cancelled it. Your payment is being refunded.`,
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

// ── Expire stories ────────────────────────────────────────────────────────────
//
// A story is a 24-hour announcement. Clients already hide expired ones by
// comparing expiresAt, so this is not what makes them disappear — it is what
// stops the collection growing forever, and what removes the photo from Storage,
// which no client-side filter can do.

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
