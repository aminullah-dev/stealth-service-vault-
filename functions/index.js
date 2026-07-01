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

function hesabHeaders(apiKey) {
  return {
    "Content-Type":  "application/json",
    "Accept":        "application/json",
    "Authorization": `API-KEY ${apiKey}`,
  };
}

// ── createPaymentSession (callable) ──────────────────────────────────────────

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
    const user     = userSnap.exists ? userSnap.data() : {};

    const commissionPercent = await getCommissionPercent();
    const commissionAmount  = Math.round((price * commissionPercent) / 100);
    const providerNet       = price - commissionAmount;

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
      status:    "AWAITING_PAYMENT",
      createdAt: Date.now(),
      notes:     notes || "",
    });
    createBatch.set(paymentRef, {
      appointmentId:     apptRef.id,
      customerId:        uid,
      providerId:        salon.providerId || "",
      salonId,
      serviceName,
      amount:            price,
      commissionPercent,
      commissionAmount,
      providerNet,
      currency:          "AFN",
      status:            "PENDING",
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
        firebaseEmail:  u.firebaseEmail || "",
        salt:           u.salt,
      };
    }
  }

  return { mode: "INVALID" };
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
    if (payment && payment.status === "PAID") {
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
  return cancelPaidAppointment(appointmentId, "CUSTOMER", (appt) =>
    appt.customerId === request.auth.uid
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
  return cancelPaidAppointment(appointmentId, "PROVIDER", (appt, payment) =>
    !!payment && payment.providerId === request.auth.uid
  );
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
  const callerSnap = await db.doc(`users/${request.auth.uid}`).get();
  if (!callerSnap.exists || callerSnap.data().role !== "ADMIN") {
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
      paidBy:    request.auth.uid,
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
  const callerSnap = await db.doc(`users/${request.auth.uid}`).get();
  if (!callerSnap.exists || callerSnap.data().role !== "ADMIN") {
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
      processedBy: request.auth.uid,
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
