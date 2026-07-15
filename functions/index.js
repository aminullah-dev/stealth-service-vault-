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
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { defineSecret, defineString } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");
const { promoDiscountFor, computeCheckout, resolveServicesTotal, validateGiftAmount, loyaltyToCredit, offerDiscountFor, lastMinuteDiscount, packageDiscountFor } = require("./lib/money");
const { expandBooked, serviceSlotSpan, hasSlotConflict } = require("./lib/slots");
const { isPaidSignal, isFailSignal, isUnderpaid } = require("./lib/webhook");
const { isValidDocId } = require("./lib/validate");
const { averageRating } = require("./lib/reviews");

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

    // Slot-conflict guard — reject a booking whose slots are already taken on the
    // same chair (different staff = a different chair, so it books in parallel).
    // This is a pre-write check rather than a full transaction because the online
    // path then hands off to HesabPay; it catches the common collision, and the
    // AWAITING_PAYMENT / PENDING rows it counts also reserve the slot against
    // other bookers until they settle or expire (expireAbandonedPayments).
    {
      const slotMinutes = Number(salon.slotDurationMinutes) || 60;
      const existingSnap = await db.collection("appointments")
        .where("salonId", "==", salonId)
        .get();
      const existing = existingSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
      if (hasSlotConflict(existing, appointmentDate, slotSpan, resolvedStaffId, slotMinutes)) {
        throw new HttpsError("failed-precondition", "That time slot is no longer available.", { reason: "SLOT_TAKEN" });
      }
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
      const batch = db.batch();
      batch.set(apptRef, {
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
          title:       "New Cash Booking",
          body:        `${serviceName} — AFN ${price} to collect in person`,
          isRead:      false,
          createdAt:   Date.now(),
          relatedId:   apptRef.id,
        });
      }
      try {
        await batch.commit();
      } catch (err) {
        await refundReservation({ customerId: uid, referralUsed, promoId: promo.promoId });
        logger.error("createPaymentSession cash write failed", err);
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
    const createBatch = db.batch();
    createBatch.set(apptRef, {
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
      await createBatch.commit();
    } catch (err) {
      await refundReservation({ customerId: uid, referralUsed, promoId: promo.promoId });
      logger.error("createPaymentSession online write failed", err);
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
        logger.error("Webhook signature verification failed");
        return res.status(401).send("Invalid signature");
      }
    } catch (err) {
      logger.error("Signature verification error", err);
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

    try {
      const result = await db.runTransaction(async (tx) => {
        // Re-read inside the transaction so two concurrent retries can't both
        // pass the PAID check and double-credit the provider.
        const freshSnap = await tx.get(paymentRef);
        if (!freshSnap.exists) return "not_found";
        const fresh = freshSnap.data();

        // Idempotency — already settled.
        if (fresh.status === "PAID") return "already_paid";

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
              title:       "You received a gift card 🎁",
              body:        `AFN ${fresh.amount} credit was added to your account.`,
              isRead:      false,
              createdAt:   Date.now(),
              relatedId:   fresh.giftCardId || "",
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
          // Track what the provider is owed (platform pays out separately).
          tx.set(
            db.doc(`provider_balances/${fresh.providerId}`),
            {
              providerId: fresh.providerId,
              owedAmount: admin.firestore.FieldValue.increment(fresh.providerNet),
              updatedAt:  Date.now(),
            },
            { merge: true }
          );
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
          tx.set(db.collection("notifications").doc(), {
            recipientId: fresh.providerId,
            type:        "NEW_BOOKING",
            title:       "New Paid Booking",
            body:        `${fresh.serviceName} — paid AFN ${fresh.amount}`,
            isRead:      false,
            createdAt:   Date.now(),
            relatedId:   fresh.appointmentId,
          });
          if (webhookRef) {
            tx.set(webhookRef, { paymentId, settledAt: Date.now() });
          }
          return "paid";
        }

        if (failSignal) {
          tx.update(paymentRef, { status: "FAILED" });
          return "failed";
        }

        // Unknown/intermediate callback — leave the payment untouched.
        return "ignored";
      });

      if (result === "not_found") return res.status(404).send("Payment not found");
      if (result === "replay")    return res.status(409).send("Duplicate transaction");
      return res.status(200).send("OK");
    } catch (err) {
      logger.error("Webhook processing error", err);
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
  const d = request.data || {};
  const phone    = String(d.phone || "").trim();
  const password = String(d.password || "");
  if (!phone || !password) {
    return { mode: "INVALID" };
  }

  // Match on trailing digits so a number stored as "0700..", "+93700..",
  // "93700.." or a legacy un-normalized value all resolve to the same account.
  const inDigits = phone.replace(/\D/g, "");
  const matchable = (stored) => {
    const s = String(stored || "").replace(/\D/g, "");
    if (!s || !inDigits) return false;
    const shorter = s.length <= inDigits.length ? s : inDigits;
    const longer  = s.length <= inDigits.length ? inDigits : s;
    return shorter.length >= 7 && longer.endsWith(shorter);
  };

  const snap = await db.collection("users").get();
  const doc = snap.docs.find((dd) => matchable(dd.data().phone));
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
          tx.update(targetRef, {
            referralRewarded: true,
            referralCredit: admin.firestore.FieldValue.increment(
              refQ.empty ? 0 : REFERRAL_WELCOME_CREDIT
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
async function cancelPaidAppointment(appointmentId, cancelledBy, authorize) {
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
      // (it is, as soon as the webhook marked this payment PAID).
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

    // Notify whichever party didn't initiate the cancellation.
    if (cancelledBy === "CUSTOMER" && providerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "BOOKING_CANCELLED",
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
        title:       "Booking Declined",
        body:        `${appt.serviceName || "Your booking"} at ${appt.salonName || "the salon"} was declined.`,
        isRead:      false,
        createdAt:   Date.now(),
        relatedId:   appointmentId,
      });
    }

    return { refundRequestId, appt };
  });

  // Release the freed slot to the next waitlisted customer (best-effort,
  // outside the transaction since it's a separate, non-critical write).
  // Mirrors FirestoreRepository.notifyFirstWaiting's single-field query +
  // in-memory filter so no new composite index is required.
  try {
    const dayStart = new Date(result.appt.appointmentDate);
    dayStart.setHours(0, 0, 0, 0);
    const startOfDay = dayStart.getTime();

    const entries = await db.collection("waitlist")
      .where("salonId", "==", result.appt.salonId)
      .get();
    const first = entries.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .filter((w) => w.requestedDate === startOfDay && w.status === "WAITING")
      .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))[0];
    if (first) {
      await db.doc(`waitlist/${first.id}`).update({ status: "SLOT_AVAILABLE" });
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
    appt.customerId === appUser.uid
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
    !!payment && payment.providerId === appUser.uid
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
    if (providerId) {
      tx.set(db.collection("notifications").doc(), {
        recipientId: providerId,
        type:        "BOOKING_RESCHEDULED",
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
    if (appt.status !== "CONFIRMED" && appt.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "This booking can't be reviewed.");
    }

    tx.update(apptRef, { customerReported: true });

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
      const payment = doc.data();
      const batch = db.batch();
      batch.update(doc.ref, { status: "EXPIRED" });
      if (payment.appointmentId) {
        batch.update(db.doc(`appointments/${payment.appointmentId}`), { status: "CANCELLED" });
      }
      await batch.commit();
      // A reserved online checkout spent the referral credit + promo use up front;
      // since it was abandoned, hand them back (legacy payments never spent them
      // until settlement, so they have nothing to refund).
      if (payment.reserved) {
        await refundReservation({
          customerId:   payment.customerId,
          referralUsed: payment.referralUsed,
          promoId:      payment.promoCode,
        });
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

    try {
      await admin.messaging().send({
        token,
        notification: { title: String(n.title), body: String(n.body || "") },
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

    const usersSnap = await db.collection("users").get();
    const tokens = [];
    usersSnap.forEach((doc) => {
      const t = String(doc.data().fcmToken || "");
      if (t) tokens.push(t);
    });
    if (tokens.length === 0) return;

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
