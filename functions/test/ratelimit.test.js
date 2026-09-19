"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const DOMAINS = path.join(__dirname, "..", "domains");
const SHARED = path.join(__dirname, "..", "shared.js");

/**
 * Which callables must be throttled, and why each one.
 *
 * Not every callable needs a limit — most are bounded by something else. A
 * review is one per appointment; confirming a booking needs a booking. These
 * are the ones where one account can repeat the call as often as it likes and
 * each repetition costs somebody something.
 */
const MUST_THROTTLE = {
  authenticateWithPassword: "guessing passwords",
  registerAccount:          "creating accounts",
  changePassword:           "guessing the current password",
  lookupAccountByPhone:     "enumerating who has an account",
  createGiftCardSession:    "opens an outbound HesabPay session",
  createWalletTopUp:        "opens an outbound HesabPay session",
  createTipSession:         "opens an outbound HesabPay session",
  reportContent:            "fills a queue a human has to read, one item at a time",
  reportVisit:              "same queue, same human, and this one can suspend a salon",
};

/** The body of each onCall, from its export to the next one. */
function callables() {
  const found = new Map();
  for (const file of fs.readdirSync(DOMAINS).filter((f) => f.endsWith(".js"))) {
    const src = fs.readFileSync(path.join(DOMAINS, file), "utf8");
    for (const m of src.matchAll(/exports\.(\w+)\s*=\s*onCall\(/g)) {
      const next = src.indexOf("\nexports.", m.index + 10);
      found.set(m[1], { file, body: src.slice(m.index, next > 0 ? next : src.length) });
    }
  }
  return found;
}

test("every callable that one account can repeat without limit is throttled", () => {
  const all = callables();
  for (const [name, why] of Object.entries(MUST_THROTTLE)) {
    const fn = all.get(name);
    assert.ok(fn, `${name} no longer exists — update this list deliberately`);
    assert.match(fn.body, /enforceRateLimit\(/,
      `${name} (${fn.file}) has no rate limit. It should: ${why}. ` +
      "If that is no longer true, remove it from MUST_THROTTLE with a reason.");
  }
});

test("the limiter lives in shared, not in one domain other domains reach into", () => {
  // It was private to identity.js, so payments.js had to `require("./identity")`
  // just for it and nothing else could use it at all — which is why every
  // callable outside those two files was unthrottled. Narrow on purpose: this
  // says nothing about the other cross-domain imports in this codebase, which
  // are deliberate and older than me.
  assert.match(fs.readFileSync(SHARED, "utf8"), /function enforceRateLimit\(/);
  for (const file of fs.readdirSync(DOMAINS).filter((f) => f.endsWith(".js"))) {
    const src = fs.readFileSync(path.join(DOMAINS, file), "utf8");
    for (const m of src.matchAll(/const \{([^}]*)\} = require\("\.\/(\w+)"\)/g)) {
      assert.ok(!/\benforceRateLimit\b/.test(m[1]),
        `${file} imports enforceRateLimit from ./${m[2]} — it is in shared.js now.`);
    }
  }
});

test("the limiter fails open", () => {
  // A throttle that turns a Firestore hiccup into "nobody can book today" is
  // worse than the abuse it prevents. Only the limit itself may throw.
  const src = fs.readFileSync(SHARED, "utf8");
  const fn = src.slice(src.indexOf("async function enforceRateLimit("));
  const body = fn.slice(0, fn.indexOf("\n}\n"));
  assert.match(body, /if \(e instanceof HttpsError\) throw e/);
  assert.match(body, /logger\.warn/);
});
