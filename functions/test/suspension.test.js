"use strict";

// What a suspended account may and may not still do.
//
// assertNotSuspended's own docstring is the policy: a suspended person must
// still be able to sign in, read her own history and reach support, or a
// suspension is indistinguishable from a broken account and the one route for
// disputing it is the route that gets closed. What stops is ACTING — creating
// things, and minting value.
//
// That policy lived in one function's comment while nineteen of twenty-three
// callables never called it, so a customer suspended for misconduct could still
// post reviews, earn loyalty points, turn them into wallet credit and spend it.
// The list below is the decision, written down. A new callable is not required
// to refuse a suspended caller — it IS required to appear here, so that whoever
// adds it makes the choice deliberately rather than by omission.

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const DOMAINS = path.join(__dirname, "..", "domains");

// Acting: creates something, or mints something that can become money.
const MUST_REFUSE = [
  "createPaymentSession",     // books an appointment
  "rescheduleAppointment",    // moves one
  "confirmAppointment",       // a salon accepting work
  "reportCustomer",           // affects another person's reputation
  "submitReview",             // affects a salon's, and earns loyalty points
  "createProviderSalon",      // creates a listing
  "createGiftCardSession",    // buys credit
  "createWalletTopUp",        // buys credit
  "createTipSession",         // moves money
  "redeemLoyaltyPoints",      // points into spendable credit
  "claimProfileReward",       // points out of nothing
  // Reporting is a safety action, so refusing it needs a reason: a suspended
  // account cannot reach the feed at all — RootView shows her the suspended
  // screen instead of the app — so there is no content in front of her to
  // report, and the only thing this admits is a suspended account filling the
  // moderation queue, which is often what she was suspended for.
  "reportContent",            // files a moderation report against someone
];

// Deliberately still reachable, each for a stated reason.
const MAY_PROCEED = {
  syncUidMap:             "signing in — the bridge is built at login",
  updatePinHash:          "resetting a password she still owns",
  requestAccountDeletion: "leaving is not an action a suspension should trap her out of",
  submitKyc:              "verifying identity may be part of appealing the suspension",
  cancelAppointment:      "releasing a slot is better for everyone than stranding it",
  providerDeclineAppointment: "same, from the salon's side",
  // Admin-only callables that gate on role rather than assertAdmin. An admin
  // cannot be suspended — adminSetUserStatus refuses to — so the check would
  // never fire.
  resolveCustomerReport: "admin only", reviewKyc: "admin only",
  upsertPromoCode: "admin only", setPromoActive: "admin only",
  recordProviderPayout: "admin only", recordRefundProcessed: "admin only",
  // Pre-auth or read-only. These never resolve an app user at all, so there is
  // nobody to be suspended: authenticateWithPassword IS signing in, and refusing
  // it would make a suspension look like a wrong password.
  authenticateWithPassword: "signing in — a suspension must not read as a wrong password",
  lookupAccountByPhone:     "pre-auth, and answers only whether an account exists",
  // There is no account yet, so there is nobody to be suspended. A suspended
  // person registering a second account under another number is a real gap, but
  // not one this check could close: the caller is anonymous by definition, and
  // the only handle on them is the phone, which is required to be unused.
  // Rate limiting is what bounds this endpoint, not suspension.
  registerAccount:          "pre-auth — the account it would check does not exist until it returns",
  getBookedSlots:           "read-only, and the same answer for everyone",
  previewPromo:             "read-only; the discount is recomputed server-side at checkout anyway",
  // Suspension stops someone ACTING on the platform; it does not take their
  // account away from them. A suspended person keeps read access to her own
  // history and to support precisely so she can appeal, and both of those need
  // her to be able to sign in. Freezing the password would mean an account
  // someone else may know the password to cannot be secured by its owner, and
  // would make a suspension indistinguishable from a stolen account.
  changePassword:           "changes only her own credential; acting on the platform is what suspension stops",
};

/** Every onCall in domains/, with the body between it and the next export. */
function callables() {
  const found = new Map();
  for (const file of fs.readdirSync(DOMAINS).filter((f) => f.endsWith(".js"))) {
    const src = fs.readFileSync(path.join(DOMAINS, file), "utf8");
    for (const m of src.matchAll(/exports\.(\w+)\s*=\s*onCall\(/g)) {
      const from = m.index;
      const next = src.indexOf("\nexports.", from + 10);
      found.set(m[1], { file, body: src.slice(from, next > 0 ? next : src.length) });
    }
  }
  return found;
}

test("every callable that acts on the platform refuses a suspended account", () => {
  const all = callables();
  for (const name of MUST_REFUSE) {
    const fn = all.get(name);
    assert.ok(fn, `${name} no longer exists — update this list deliberately`);
    assert.match(fn.body, /assertNotSuspended\(/,
      `${name} (${fn.file}) lets a suspended account act. If that is now intended, ` +
      "move it to MAY_PROCEED with the reason.");
  }
});

test("every callable is accounted for, one way or the other", () => {
  const all = callables();
  const unlisted = [...all.keys()].filter((n) => {
    if (MUST_REFUSE.includes(n) || n in MAY_PROCEED) return false;
    // Callables gated by assertAdmin are a separate population: an admin cannot
    // be suspended, so the question does not arise.
    return !/assertAdmin\(/.test(all.get(n).body);
  }).sort();
  assert.deepStrictEqual(unlisted, [],
    "these callables have not been considered against the suspension policy: " +
    unlisted.join(", ") + ". Add each to MUST_REFUSE or to MAY_PROCEED with a reason.");
});

test("the reachable ones really are reachable — no accidental guard", () => {
  // A guard added to one of these without updating the list would silently
  // close the door on someone appealing a suspension, which is the failure the
  // policy exists to prevent.
  const all = callables();
  for (const [name, why] of Object.entries(MAY_PROCEED)) {
    const fn = all.get(name);
    if (!fn) continue;                       // renamed or removed; the test above catches that
    if (/assertAdmin\(/.test(fn.body)) continue;
    assert.ok(!/assertNotSuspended\(/.test(fn.body),
      `${name} now refuses a suspended account, but is listed as reachable because: ${why}. ` +
      "If the guard is intended, move it to MUST_REFUSE.");
  }
});

test("isSuspended reads both fields a suspension has been written to", () => {
  const { isSuspended } = require("../shared");
  assert.strictEqual(isSuspended({ suspended: true }), true);
  assert.strictEqual(isSuspended({ status: "SUSPENDED" }), true);   // the legacy shape
  assert.strictEqual(isSuspended({ suspended: false, status: "APPROVED" }), false);
  assert.strictEqual(isSuspended({}), false);
  assert.strictEqual(isSuspended(null), false);
  assert.strictEqual(isSuspended(undefined), false);
  // Not truthiness: a stray string must not suspend someone.
  assert.strictEqual(isSuspended({ suspended: "no" }), false);
})
