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
 */

const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

const HESAB_API_KEY = defineSecret("HESAB_API_KEY");
const HESAB_WEBHOOK_SECRET = defineSecret("HESAB_WEBHOOK_SECRET");
const HESAB_BASE_URL = defineString("HESAB_BASE_URL", {
  default: "https://api.hesab.com/api/v1",
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

function hesabHeaders(apiKey) {
  return {
    "Content-Type": "application/json",
    Authorization: `API-KEY ${apiKey}`,
  };
}

// ── createPaymentSession (callable) ────────────────────────────────────────────

exports.createPaymentSession = onCall(
  { secrets: [HESAB_API_KEY], region: "us-central1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before paying.");
    }
    const apiKey = HESAB_API_KEY.value();
    if (!apiKey) {
      throw new HttpsError(
        "failed-precondition",
        "Payment is not configured. Set the HESAB_API_KEY secret."
      );
    }

    const uid = request.auth.uid;
    const { salonId, serviceName, appointmentDate, notes, email } =
      request.data || {};

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
    const price = Number((salon.pricePerService || {})[serviceName]);
    if (!Number.isFinite(price) || price <= 0) {
      throw new HttpsError(
        "failed-precondition",
        "This service has no valid price."
      );
    }

    const userSnap = await db.doc(`users/${uid}`).get();
    const user = userSnap.exists ? userSnap.data() : {};

    const commissionPercent = await getCommissionPercent();
    const commissionAmount = Math.round((price * commissionPercent) / 100);
    const providerNet = price - commissionAmount;

    // Create the appointment up-front in AWAITING_PAYMENT so it won't show to the
    // provider until the payment webhook flips it to PENDING.
    const apptRef = db.collection("appointments").doc();
    await apptRef.set({
      customerId: uid,
      customerName: user.name || "",
      customerPhone: user.phone || "",
      salonId,
      salonName: salon.salonName || "",
      serviceName,
      appointmentDate,
      status: "AWAITING_PAYMENT",
      createdAt: Date.now(),
      notes: notes || "",
    });

    // Create the payment record (PENDING) before contacting HesabPay so the
    // webhook always has a row to update.
    const paymentRef = db.collection("payments").doc();
    await paymentRef.set({
      appointmentId: apptRef.id,
      customerId: uid,
      providerId: salon.providerId || "",
      salonId,
      serviceName,
      amount: price,
      commissionPercent,
      commissionAmount,
      providerNet,
      currency: "AFN",
      status: "PENDING",
      hesabSessionId: "",
      createdAt: Date.now(),
    });

    // Ask HesabPay to create a checkout session.
    let sessionUrl = "";
    let sessionId = "";
    try {
      const res = await fetch(`${HESAB_BASE_URL.value()}/payment/create-session`, {
        method: "POST",
        headers: hesabHeaders(apiKey),
        body: JSON.stringify({
          email: email || user.email || "",
          items: [
            {
              id: paymentRef.id,
              name: `${salon.salonName || "Salon"} — ${serviceName}`,
              price,
              quantity: 1,
            },
          ],
          // HesabPay echoes metadata back on the webhook so we can match the row.
          metadata: { paymentId: paymentRef.id, appointmentId: apptRef.id },
        }),
      });

      const body = await res.json();
      if (!res.ok || !body) {
        throw new Error(`HesabPay create-session failed: ${res.status}`);
      }
      // Log the raw shape ONCE during integration so the exact field names can be
      // confirmed from the Cloud Functions logs, then the fallbacks below trimmed.
      // (Safe to keep — contains no card data, only the checkout URL + ids.)
      logger.info("HesabPay create-session response", { body });
      sessionUrl =
        body.url ||
        body.payment_url ||
        body.sessionUrl ||
        body.checkout_url ||
        (body.data && (body.data.url || body.data.payment_url)) ||
        "";
      sessionId =
        body.id ||
        body.session_id ||
        body.sessionId ||
        (body.data && (body.data.id || body.data.session_id)) ||
        "";
      if (!sessionUrl) throw new Error("HesabPay returned no checkout URL.");
    } catch (err) {
      // Roll back so we don't leave orphaned AWAITING_PAYMENT bookings.
      await paymentRef.update({ status: "FAILED" });
      await apptRef.delete();
      logger.error("create-session failed", err);
      throw new HttpsError("internal", String(err.message || err));
    }

    await paymentRef.update({ hesabSessionId: sessionId });

    return {
      paymentId: paymentRef.id,
      appointmentId: apptRef.id,
      checkoutUrl: sessionUrl,
      amount: price,
      commissionAmount,
      providerNet,
    };
  }
);

// ── hesabPayWebhook (HTTP) ─────────────────────────────────────────────────────

exports.hesabPayWebhook = onRequest(
  { secrets: [HESAB_API_KEY, HESAB_WEBHOOK_SECRET], region: "us-central1" },
  async (req, res) => {
    if (req.method !== "POST") {
      return res.status(405).send("Method not allowed");
    }

    const payload = req.body || {};

    // Log the raw webhook ONCE during integration to confirm field names
    // (status, metadata, signature header) against the HesabPay dashboard.
    logger.info("HesabPay webhook received", {
      headers: { signature: req.get("x-hesab-signature") },
      body: payload,
    });

    // Verify the webhook signature with HesabPay so a forged request can't mark
    // an unpaid booking as PAID.
    try {
      const verifyRes = await fetch(
        `${HESAB_BASE_URL.value()}/hesab/webhooks/verify-signature`,
        {
          method: "POST",
          headers: hesabHeaders(HESAB_API_KEY.value()),
          body: JSON.stringify({
            signature: req.get("x-hesab-signature") || payload.signature || "",
            secret: HESAB_WEBHOOK_SECRET.value(),
            payload,
          }),
        }
      );
      const verifyBody = await verifyRes.json().catch(() => ({}));
      const valid =
        verifyRes.ok &&
        (verifyBody.valid === true || verifyBody.verified === true);
      if (!valid) {
        logger.error("Webhook signature verification failed");
        return res.status(401).send("Invalid signature");
      }
    } catch (err) {
      logger.error("Signature verification error", err);
      return res.status(401).send("Signature verification error");
    }

    const meta = payload.metadata || {};
    const paymentId = meta.paymentId || payload.paymentId;
    const status = String(payload.status || "").toUpperCase();

    if (!paymentId) {
      return res.status(400).send("Missing paymentId");
    }

    const paymentRef = db.doc(`payments/${paymentId}`);
    const paymentSnap = await paymentRef.get();
    if (!paymentSnap.exists) {
      return res.status(404).send("Payment not found");
    }
    const payment = paymentSnap.data();

    // Idempotency — HesabPay may retry the webhook.
    if (payment.status === "PAID") {
      return res.status(200).send("Already processed");
    }

    const paid =
      status === "PAID" || status === "SUCCESS" || status === "COMPLETED";

    const batch = db.batch();
    if (paid) {
      batch.update(paymentRef, { status: "PAID", paidAt: Date.now() });
      // Release the appointment to the provider's pending queue.
      batch.update(db.doc(`appointments/${payment.appointmentId}`), {
        status: "PENDING",
      });
      // Track what the provider is owed (platform pays out separately).
      batch.set(
        db.doc(`provider_balances/${payment.providerId}`),
        {
          providerId: payment.providerId,
          owedAmount: admin.firestore.FieldValue.increment(payment.providerNet),
          updatedAt: Date.now(),
        },
        { merge: true }
      );
      // Notify the provider of the new (paid) booking.
      batch.set(db.collection("notifications").doc(), {
        recipientId: payment.providerId,
        type: "NEW_BOOKING",
        title: "New Paid Booking",
        body: `${payment.serviceName} — paid AFN ${payment.amount}`,
        isRead: false,
        createdAt: Date.now(),
        relatedId: payment.appointmentId,
      });
    } else {
      batch.update(paymentRef, { status: "FAILED" });
    }
    await batch.commit();

    return res.status(200).send("OK");
  }
);

// ── recordProviderPayout (callable, admin-only) ─────────────────────────────────

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
  // Verify the caller is an admin (role is stored on the user document).
  const callerSnap = await db.doc(`users/${request.auth.uid}`).get();
  if (!callerSnap.exists || callerSnap.data().role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admins only.");
  }

  const { providerId, method } = request.data || {};
  if (!providerId) {
    throw new HttpsError("invalid-argument", "providerId is required.");
  }

  const balanceRef = db.doc(`provider_balances/${providerId}`);

  // Transaction so a concurrent webhook crediting the balance can't be lost:
  // we record exactly what was owed at payout time and zero it.
  const result = await db.runTransaction(async (tx) => {
    const balSnap = await tx.get(balanceRef);
    const owed = balSnap.exists ? Number(balSnap.data().owedAmount || 0) : 0;
    if (owed <= 0) {
      throw new HttpsError("failed-precondition", "Nothing owed to this provider.");
    }
    const payoutRef = db.collection("payouts").doc();
    tx.set(payoutRef, {
      providerId,
      amount: owed,
      method: method || "MANUAL",
      paidBy: request.auth.uid,
      createdAt: Date.now(),
    });
    tx.set(
      balanceRef,
      { owedAmount: 0, updatedAt: Date.now() },
      { merge: true }
    );
    return { payoutId: payoutRef.id, amount: owed };
  });

  // Notify the provider (outside the transaction).
  await db.collection("notifications").doc().set({
    recipientId: providerId,
    type: "SYSTEM",
    title: "Payout Sent",
    body: `You have been paid AFN ${result.amount}.`,
    isRead: false,
    createdAt: Date.now(),
    relatedId: result.payoutId,
  });

  return result;
});
