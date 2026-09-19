"use strict";
/**
 * Create the demo CUSTOMER account in safebeauty-staging, exactly the way
 * registerAccount (functions/domains/identity.js) does it:
 *
 *   salt         = 16 random bytes, base64 NO_WRAP
 *   pinHash      = PBKDF2-SHA256(password,          salt, 65536, 32 bytes) b64
 *   authPassword = PBKDF2-SHA256("AUTH:" + password, salt, 65536, 32 bytes) b64
 *   firebaseEmail= {uid without dashes}@sb.app
 *
 * The Firebase Auth password is authPassword — never the typed password.
 */
const crypto = require("crypto");
const L = require("./lib.js");

const PROJECT = L.PROJECT;
const API_KEY = process.env.DEMO_API_KEY;   // from app/src/demo/google-services.json

const PHONE_TYPED = "0700000099";   // what she types on the login screen
const PHONE_STORED = "+93700000099";
const NAME = "مهمان دیمو";          // "Demo guest" — not a person

function pbkdf2(pw, saltB64) {
  const salt = Buffer.from(saltB64, "base64");
  return crypto.pbkdf2Sync(String(pw), salt, 65536, 32, "sha256").toString("base64");
}

async function main() {
  const password = process.env.DEMO_PASSWORD;
  if (!password) throw new Error("DEMO_PASSWORD not set");

  const uid = crypto.randomUUID();
  const salt = crypto.randomBytes(16).toString("base64");
  const pinHash = pbkdf2(password, salt);
  const authPassword = pbkdf2("AUTH:" + password, salt);
  const firebaseEmail = `${uid.replace(/-/g, "")}@sb.app`;

  if (pinHash === authPassword) throw new Error("domain separation broken");
  if (salt.length !== 24 || pinHash.length !== 44 || authPassword.length !== 44) {
    throw new Error(`bad shapes: salt=${salt.length} hash=${pinHash.length} auth=${authPassword.length}`);
  }

  // ── Firebase Auth account ──────────────────────────────────────────────────
  const url = `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: firebaseEmail, password: authPassword, returnSecureToken: false }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`signUp failed: ${JSON.stringify(body)}`);
  const authUid = body.localId;

  // ── users/{appUid} ─────────────────────────────────────────────────────────
  // Composed field by field, the same shape buildRegistrationDocument produces,
  // plus the demo-only extras (loyaltyPoints, kycStatus) a real account earns
  // over time rather than at registration.
  await L.setDoc("users", uid, {
    uid,
    name: NAME,
    phone: PHONE_STORED,
    email: "",
    role: "CUSTOMER",
    pinHash,
    salt,
    firebaseEmail,
    status: "APPROVED",
    createdAt: Date.now() - 120 * 24 * 60 * 60 * 1000,
    referralCode: "DEMO01",
    referredBy: "",
    pendingSalonName: "",
    pendingSalonDistrict: "",
    pendingSalonServices: [],
    // Demo state: enough loyalty points to sit in the REGULAR tier, and a KYC
    // status of APPROVED so the deals section is unlocked. No identity document
    // is stored — tazkiraPhotoPath/selfiePhotoPath stay empty on purpose.
    loyaltyPoints: 80,
    kycStatus: "APPROVED",
    tazkiraNumber: "",
    tazkiraPhotoPath: "",
    selfiePhotoPath: "",
    referralCredit: 0,
    profileRewardClaimed: false,
    customerRatingSum: 24,
    customerRatingCount: 5,
    noShowCount: 0,
    lastVisitAt: Date.now() - 9 * 24 * 60 * 60 * 1000,
  });

  // ── uid_map/{authUid} ──────────────────────────────────────────────────────
  await L.setDoc("uid_map", authUid, { appUid: uid, updatedAt: Date.now() });

  console.log(JSON.stringify({
    project: PROJECT, appUid: uid, authUid, firebaseEmail,
    phoneTyped: PHONE_TYPED, phoneStored: PHONE_STORED, name: NAME,
  }, null, 1));
}

main().catch((e) => { console.error(e); process.exit(1); });
