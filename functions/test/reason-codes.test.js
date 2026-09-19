"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..", "..");
const DOMAINS = path.join(__dirname, "..", "domains");
const IOS_STRINGS = path.join(ROOT, "ios/SafeBeauty/Localization/Strings.swift");

/**
 * A refusal the customer caused must arrive as a code, not as a sentence.
 *
 * `HttpsError`'s message is an English line written for a log. Eight iOS
 * screens were rendering it straight to the user, so a woman whose slot had
 * just been taken read "That time is no longer available." in English inside an
 * app she had set to Dari. It was accurate and unreadable.
 *
 * `details.reason` is the fix and already existed for the booking path. These
 * tests keep the two halves in step: a code the server sends and the app cannot
 * translate is an English sentence again, quietly.
 */
function serverReasonCodes() {
  const codes = new Set();
  for (const file of fs.readdirSync(DOMAINS).filter((f) => f.endsWith(".js"))) {
    const src = fs.readFileSync(path.join(DOMAINS, file), "utf8");
    // Only the third argument of an HttpsError. A bare /reason:/ also matched
    // refund_requests.reason ("LATE_PAYMENT"), which is a document field and
    // never travels to a client as an error — the test then demanded a
    // translation for something no screen can ever be shown.
    for (const m of src.matchAll(/new HttpsError\([^;]{0,400}?reason:\s*"([A-Z_]+)"/g)) {
      codes.add(m[1]);
    }
  }
  return codes;
}

/** The cases of `L.reason(_:)`, and whether each carries all three languages. */
function iosReasonCases() {
  const src = fs.readFileSync(IOS_STRINGS, "utf8");
  const start = src.indexOf("static func reason(");
  assert.ok(start > 0, "L.reason(_:) is gone — the app can no longer translate a refusal");
  const block = src.slice(start, src.indexOf("\n    }\n", start));
  const cases = new Map();
  for (const m of block.matchAll(/case "([A-Z_]+)":\s*return L\(([\s\S]*?)\)\.t/g)) {
    cases.set(m[1], m[2]);
  }
  return cases;
}

test("every reason code the server sends, the app can say in her language", () => {
  const server = serverReasonCodes();
  const app = iosReasonCases();
  assert.ok(server.size > 10, `found only ${server.size} codes — check the parser`);
  const untranslated = [...server].filter((c) => !app.has(c)).sort();
  assert.deepEqual(untranslated, [], `sent by the server, not translatable by the app: ${untranslated}`);
});

test("every translated reason carries all three languages", () => {
  const incomplete = [];
  for (const [code, body] of iosReasonCases()) {
    for (const lang of ["fa:", "ps:", "en:"]) {
      if (!body.includes(lang)) incomplete.push(`${code} (${lang.slice(0, 2)})`);
    }
  }
  assert.deepEqual(incomplete, [], `missing a language: ${incomplete}`);
});

test("no screen decides what went wrong by reading the English sentence", () => {
  // RescheduleSheet used `message.lowercased().contains("no longer")`. The
  // server's wording is a log line, not a contract: rewording it would have
  // sent "too late to move this booking" down the "somebody took your slot"
  // branch and told her to pick another time for a booking that can no longer
  // be moved at all.
  // Features AND Services. The first version walked only Features and missed
  // BookingService, which decided whether to send a customer to identity
  // verification by testing whether the server's sentence contained "verify".
  const roots = [path.join(ROOT, "ios/SafeBeauty/Features"),
                 path.join(ROOT, "ios/SafeBeauty/Services")];
  const offenders = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) { walk(full); continue; }
      if (!entry.name.endsWith(".swift")) continue;
      // Comments stripped first — the fix for this carries a comment quoting
      // the old line, and a test that fails on its own explanation is a test
      // nobody keeps.
      const src = fs.readFileSync(full, "utf8")
        .split("\n").filter((l) => !l.trim().startsWith("//")).join("\n");
      if (/message\s*\.\s*(lowercased\(\)\s*\.\s*)?contains\(/.test(src)) {
        offenders.push(path.relative(ROOT, full));
      }
    }
  };
  roots.forEach(walk);
  assert.deepEqual(offenders, [], `matching on the server's English text: ${offenders}`);
});
