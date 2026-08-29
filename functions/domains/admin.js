// admin — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { isValidDocId } = require("../lib/validate");
const { defaultWorkingHours } = require("../lib/hours");
const { assertAdmin, findAccountByPhone, logAdminAction, normalizePhone, pbkdf2Hash, resolveAppUser } = require("../shared");
const crypto = require("crypto");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { admin, alertable, db, logger } = require("../shared");

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
    // The same shape adminSuspendUser writes, because a suspension decided here
    // must be the same suspension. This wrote only `status`, which the security
    // rules read and the callables did not, so a customer suspended for
    // misconduct kept booking and paying — through the one flow whose entire
    // purpose is to stop her. Writing both fields also means the Manage modal
    // can see it and lift it.
    await db.doc(`users/${report.customerId}`).set(
      {
        status:          "SUSPENDED",
        suspended:       true,
        suspendedReason: `Report ${reportId}: ${String(report.comment || "misconduct report")}`.slice(0, 300),
        suspendedAt:     Date.now(),
        suspendedBy:     appUser.uid,
      },
      { merge: true }
    );
    await logAdminAction(appUser, "SUSPEND_USER", {
      targetUid: report.customerId,
      targetName: report.customerName || "",
      reason: `Customer report ${reportId}`,
    });
  }
  return { reportId, actionTaken: suspend ? "SUSPENDED" : "DISMISSED" };
});

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
    // Asked the way a login asks it — see findAccountByPhone. Matching only the
    // normalized spelling missed accounts stored before normalization existed,
    // which let an admin hand one number to two accounts and lock both owners
    // out of the one they could no longer reach.
    const dup = await findAccountByPhone(phone, String(d.phone), targetUid);
    if (dup) {
      throw new HttpsError("already-exists",
        `Another account (${dup.data().name || dup.id}) already uses that phone.`);
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
  const clash = await findAccountByPhone(phone, rawPhone);
  if (clash) {
    throw new HttpsError("already-exists",
      `An account (${clash.data().name || clash.id}) already uses this phone number.`);
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
      // The same week the provider editor shows by default, so the owner who
      // opens her profile, sees Saturday to Thursday 9–18 and changes nothing has
      // a salon that can actually be booked. Stored empty, it never could be.
      workingHours:        defaultWorkingHours(),
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

  // `status` is carried along because the security rules gate direct writes on
  // it (isApproved) while the callables gate on `suspended` — one suspension,
  // two readers. Lifting one restores the status the account had before it was
  // suspended rather than assuming APPROVED: a provider suspended while still
  // PENDING approval would otherwise be quietly promoted by being reinstated.
  const wasSuspended = target.suspended === true || target.status === "SUSPENDED";
  const restoreTo = String(target.statusBeforeSuspension || "") ||
    (target.status === "SUSPENDED" ? (target.role === "PROVIDER" ? "PENDING" : "APPROVED") : target.status);

  await ref.update({
    suspended:       suspend,
    suspendedReason: suspend ? reason : "",
    suspendedAt:     suspend ? Date.now() : 0,
    suspendedBy:     suspend ? me.uid : "",
    status:          suspend ? "SUSPENDED" : restoreTo,
    statusBeforeSuspension: suspend
      ? (wasSuspended ? String(target.statusBeforeSuspension || "") : String(target.status || ""))
      : "",
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

  // The salon is resolved here rather than taken from the caller. The console
  // was sending `u.salons[0]` off the user document, which has no `salons`
  // field and never has — so salonId arrived empty, became "___none___", and
  // every provider dossier reported zero bookings. The salons are queried for
  // the response anyway; this just needs them before the bookings query rather
  // than beside it. A salonId in the request still wins, so an admin can pin
  // the dossier to one salon of a provider who has several.
  const salons = isProvider
    ? await db.collection("salons").where("providerId", "==", targetUid).get()
        .catch(() => ({ docs: [] }))
    : { docs: [] };
  const salonId = String((request.data || {}).salonId || "").trim() ||
                  ((salons.docs[0] && salons.docs[0].id) || "");

  const bookingsQ = isProvider
    ? db.collection("appointments").where("salonId", "==", salonId || "___none___")
    : db.collection("appointments").where("customerId", "==", targetUid);

  const [bookings, reviews, reportsAbout, balance] = await Promise.all([
    bookingsQ.orderBy("createdAt", "desc").limit(25).get().catch(() => ({ docs: [] })),
    db.collection("reviews").where("customerId", "==", targetUid)
      .limit(15).get().catch(() => ({ docs: [] })),
    db.collection("customer_reports").where("customerId", "==", targetUid)
      .limit(15).get().catch(() => ({ docs: [] })),
    db.doc(`provider_balances/${targetUid}`).get().catch(() => ({ exists: false })),
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


// ── adminDeleteUser ───────────────────────────────────────────────────────────
//
// Removes an account that should never have existed — a duplicate
// registration, a test row — and nothing else.
//
// Almost all of this function is refusals, and that is the point. Deleting a
// user is the one admin action with no undo inside the app, and the damage it
// does is not to the account: it is to everything that referenced it. An
// appointment whose customer is gone still shows in a salon's calendar with a
// name and a phone and nobody to contact. A review loses its author. A salon
// loses its owner and becomes unmanageable.
//
// So an account with any history at all is refused, with the reason given, and
// the admin is pointed at suspension instead — which stops someone acting
// without destroying what they did.
//
// The Firebase Auth record is deliberately left behind. Firestore has
// point-in-time recovery, so a wrongly deleted document can be restored for
// seven days; Auth has no equivalent. An orphaned Auth shell can authenticate
// and then fail to resolve a profile, which is recoverable. A deleted one is
// not.
exports.adminDeleteUser = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const d = request.data || {};
  const targetUid = String(d.targetUid || "").trim();
  const reason = String(d.reason || "").trim().slice(0, 300);

  if (!isValidDocId(targetUid)) {
    throw new HttpsError("invalid-argument", "A valid targetUid is required.");
  }
  if (!reason) {
    throw new HttpsError("invalid-argument", "Deleting an account needs a reason on the record.");
  }
  if (targetUid === me.uid) {
    throw new HttpsError("failed-precondition", "You cannot delete your own account from here.");
  }

  const ref = db.doc(`users/${targetUid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "No such user.");
  const target = snap.data() || {};

  if (target.role === "ADMIN") {
    throw new HttpsError(
      "failed-precondition",
      "Remove admin access first. Deleting an administrator outright is never the quick fix it looks like."
    );
  }

  // Anything that would be orphaned. Counted rather than read, so a long-lived
  // account does not make this expensive.
  const countOf = async (coll, field) =>
    (await db.collection(coll).where(field, "==", targetUid).count().get()).data().count;

  const [appointments, payments, reviews, salons, reports] = await Promise.all([
    countOf("appointments", "customerId"),
    countOf("payments", "customerId"),
    countOf("reviews", "customerId"),
    countOf("salons", "providerId"),
    countOf("customer_reports", "customerId"),
  ]);

  const holds = { appointments, payments, reviews, salons, reports };
  const blocking = Object.entries(holds).filter(([, n]) => n > 0);

  if (blocking.length) {
    throw new HttpsError(
      "failed-precondition",
      "This account has history and cannot be deleted: " +
        blocking.map(([k, n]) => `${n} ${k}`).join(", ") +
        ". Suspend it instead — that stops them acting without erasing what they did.",
      { reason: "HAS_HISTORY", holds }
    );
  }

  // Recorded before the delete, so the row survives even if what follows fails.
  await logAdminAction(me, "DELETE_USER", {
    targetUid,
    targetName:    target.name || "",
    phone:         target.phone || "",
    firebaseEmail: target.firebaseEmail || "",
    reason,
    holds,
  });

  // uid_map is keyed by the Firebase Auth uid, so it has to be found by value.
  const maps = await db.collection("uid_map").where("appUid", "==", targetUid).limit(10).get();
  const batch = db.batch();
  maps.docs.forEach((m) => batch.delete(m.ref));
  batch.delete(ref);
  await batch.commit();

  logger.log(`adminDeleteUser: ${targetUid} removed by ${me.uid}`);
  return {
    ok: true,
    deletedUidMaps: maps.size,
    authRecordKept: target.firebaseEmail || "",
  };
});
