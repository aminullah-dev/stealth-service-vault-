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
    throw new HttpsError("failed-precondition", "This promo code has reached its usage limit.");
  }

  // Percentage takes precedence when both are set; discount can never exceed the
  // price (so the final amount is always >= 0).
  let discount = 0;
  const pct = Number(p.discountPercent || 0);
  const amt = Number(p.discountAmount || 0);
  if (pct > 0) discount = Math.round((priceAfn * Math.min(pct, 100)) / 100);
  else if (amt > 0) discount = Math.min(Math.round(amt), priceAfn);
  discount = Math.max(0, Math.min(discount, priceAfn));

  return { discount, promoId: code, code };
}

function hesabHeaders(apiKey) {
  return {
    "Content-Type":  "application/json",
    "Accept":        "application/json",
    "Authorization": `API-KEY ${apiKey}`,
  };
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
    const { salonId, serviceName, appointmentDate, notes, email, method, promoCode } =
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

    if (!salonId || !serviceName || !appointmentDate) {
      throw new HttpsError(
        "invalid-argument",
        "salonId, serviceName and appointmentDate are required."
      );
    }

    // Read the salon + price server-side so the client can't spoof the amount.
    const salonSnap = await db.doc(`salons/${salonId}`).get();
    if (!salonSnap.exists) {
      throw new HttpsError("not-found", "Salon not found.");
    }
    const salon = salonSnap.data();
    const listPrice = Number((salon.pricePerService || {})[serviceName]);
    if (!Number.isFinite(listPrice) || listPrice <= 0) {
      throw new HttpsError(
        "failed-precondition",
        "This service has no valid price."
      );
    }

    // Apply a promo code if one was entered (throws a clear error if invalid).
    // The customer is charged the discounted price; commission is computed on
    // that same discounted amount so the platform's cut scales with what was
    // actually paid, not the list price.
    const promo = await resolvePromoDiscount(promoCode, listPrice);
    const price = Math.max(0, listPrice - promo.discount);

    // A fully-discounted booking can't go through HesabPay (it can't charge 0),
    // so route those to cash instead of failing opaquely.
    if (paymentMethod === "ONLINE" && price <= 0) {
      throw new HttpsError(
        "failed-precondition",
        "This code makes your booking free — please choose Cash payment."
      );
    }

    const commissionPercent = await getCommissionPercent();
    const commissionAmount  = Math.round((price * commissionPercent) / 100);
    const providerNet       = price - commissionAmount;
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
        appointmentDate,
        status:         "PENDING",
        paymentMethod:  "CASH",
        createdAt:      Date.now(),
        notes:          notes || "",
        reminderSent:   false,
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
        commissionPercent,
        commissionAmount,
        providerNet,
        currency:          "AFN",
        status:            "PENDING_CASH",
        method:            "CASH",
        hesabSessionId:    "",
        createdAt:         Date.now(),
      });
      // Cash bookings are confirmed immediately, so count the promo use now.
      if (promo.promoId) {
        batch.update(db.doc(`promo_codes/${promo.promoId}`), {
          usedCount: admin.firestore.FieldValue.increment(1),
        });
      }
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
      await batch.commit();

      return {
        paymentId:     paymentRef.id,
        appointmentId: apptRef.id,
        checkoutUrl:   "",
        method:        "CASH",
        amount:        price,
        listPrice,
        discountAmount: promo.discount,
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
      appointmentDate,
      status:        "AWAITING_PAYMENT",
      paymentMethod: "ONLINE",
      createdAt:     Date.now(),
      notes:         notes || "",
      reminderSent:  false,
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
      // Counted in the webhook only when the payment actually succeeds, so an
      // abandoned or failed online checkout never burns a promo use.
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
    await createBatch.commit();

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
      // Roll back so we don't leave orphaned AWAITING_PAYMENT bookings.
      await paymentRef.update({ status: "FAILED" });
      await apptRef.delete();
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
      discountAmount: promo.discount,
      commissionAmount,
      providerNet,
    };
  }
);

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

    if (itemId) {
      // Direct document lookup — O(1), no index required.
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

    // HesabPay marks success with success:true / status_code:10.
    // Accept legacy status strings as a defensive fallback.
    const statusStr = String(payload.status || "").toUpperCase();
    const paidSignal =
      payload.success === true ||
      payload.status_code === 10 ||
      statusStr === "PAID" || statusStr === "SUCCESS" || statusStr === "COMPLETED";
    // Only an EXPLICIT failure marks the payment FAILED. An unknown/intermediate
    // callback is a no-op (returns 200) so it can't destroy a payment that is
    // still in flight — which would show the customer a false "payment failed".
    const failSignal =
      payload.success === false ||
      statusStr === "FAILED" || statusStr === "CANCELLED" || statusStr === "DECLINED";

    // Amount binding — reject only a clear UNDERPAYMENT (paid less than the
    // recorded price), which is the actual attack: settle a AFN 5000 booking
    // with a AFN 1 payment. We don't hard-reject other mismatches because the
    // exact unit of HesabPay's `amount` field isn't yet confirmed against a real
    // sample (could be AFN vs. pul), and a false reject would block real
    // customers. The transaction_id replay guard is the primary defense.
    if (paidSignal && Number.isFinite(reportedAmount)) {
      if (reportedAmount < Number(payment.amount)) {
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

        // Replay guard — a transaction_id may settle exactly one payment.
        let webhookRef = null;
        if (transactionId) {
          webhookRef = db.doc(`processed_webhooks/${transactionId}`);
          const seen = await tx.get(webhookRef);
          if (seen.exists) return "replay";
        }

        if (paidSignal) {
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
          // Count the promo use now that the payment actually succeeded — this
          // branch runs exactly once per payment (the status/replay guards above
          // short-circuit any retry), so the increment can't double-count.
          if (fresh.promoCode && !fresh.promoCounted) {
            tx.update(paymentRef, { promoCounted: true });
            tx.update(db.doc(`promo_codes/${fresh.promoCode}`), {
              usedCount: admin.firestore.FieldValue.increment(1),
            });
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

/**
 * Verifies a PIN against every user server-side. Returns:
 *   { mode: "REAL", uid, name, role, firebaseEmail, salt } — client then derives
 *       the auth password and signs in (the hash never leaves the server).
 *   { mode: "INVALID" } — no match.
 * No auth required (this IS the pre-auth login step).
 */
exports.authenticateWithPin = onCall({ region: "us-central1" }, async (request) => {
  const pin = String((request.data || {}).pin || "");
  if (!/^\d{4,}$/.test(pin)) {
    return { mode: "INVALID" };
  }

  const snap = await db.collection("users").get();

  // We return the account status too so the client can gate a non-APPROVED
  // provider (PENDING → "under review", SUSPENDED → blocked) instead of
  // dropping them into a live dashboard. Provider WRITES are also blocked
  // server-side by the Firestore rules' isApproved() checks, so this is
  // defense-in-depth, not the only gate.
  for (const doc of snap.docs) {
    const u = doc.data();
    if (!u.pinHash || !u.salt) continue;
    if (hashesEqual(pbkdf2Hash(pin, u.salt), u.pinHash)) {
      return {
        mode:           "REAL",
        uid:            doc.id,
        name:           u.name  || "",
        role:           u.role  || "CUSTOMER",
        status:         u.status || "",
        rejectionReason: u.rejectionReason || "",
        kycStatus:      u.kycStatus || "NONE",
        firebaseEmail:  u.firebaseEmail || "",
        salt:           u.salt,
      };
    }
  }

  return { mode: "INVALID" };
});

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
exports.lookupAccountByPhone = onCall({ region: "us-central1" }, async (request) => {
  const phone = String((request.data || {}).phone || "").trim();
  if (!phone) return { found: false };

  const q = await db.collection("users").where("phone", "==", phone).limit(1).get();
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

  const snap = await db.collection("appointments")
    .where("salonId", "==", salonId)
    .get();
  const slots = snap.docs
    .map((d) => d.data())
    .filter((a) =>
      a.status !== "CANCELLED" &&
      Number(a.appointmentDate) >= start &&
      Number(a.appointmentDate) <= end)
    .map((a) => Number(a.appointmentDate));

  return { slots };
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
    const providerId = salonSnap.exists ? (salonSnap.data().providerId || "") : "";

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
  const { code, salonId, serviceName } = request.data || {};
  if (!code || !salonId || !serviceName) {
    throw new HttpsError("invalid-argument", "code, salonId and serviceName are required.");
  }
  const salonSnap = await db.doc(`salons/${salonId}`).get();
  if (!salonSnap.exists) throw new HttpsError("not-found", "Salon not found.");
  const listPrice = Number((salonSnap.data().pricePerService || {})[serviceName]);
  if (!Number.isFinite(listPrice) || listPrice <= 0) {
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
