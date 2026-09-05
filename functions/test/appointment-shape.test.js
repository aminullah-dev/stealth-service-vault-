"use strict";

// What the server stores on an appointment, against what the Android model says
// it is.
//
// createPaymentSession writes `services` as [{name, price}] — the return value of
// resolveServicesTotal — and every backend reader only ever takes its .length,
// so the shape went unexamined for the life of the product. Declaring a matching
// field in Kotlin as List<String> does not make Firestore skip it: CustomClassMapper
// walks into the array and throws converting a HashMap to a String, out of a
// snapshot listener on the main thread. Every customer and every salon owner with
// a single booking would have lost the screen, and the app compiles perfectly.
//
// So the two sides are compared here. A Kotlin field that names a stored key has
// to declare a type that key can actually hold; a field the app does not need is
// better absent than wrong.

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const MODELS = path.join(
  __dirname, "..", "..",
  "app/src/main/java/com/safebeauty/app/data/firebase/FirestoreModels.kt"
);

/** The `val name: Type` lines of one Kotlin data class. */
function fieldsOf(dataClass) {
  const src = fs.readFileSync(MODELS, "utf8");
  const start = src.indexOf(`data class ${dataClass}(`);
  assert.ok(start > -1, `${dataClass} has moved or been renamed`);
  const body = src.slice(start, src.indexOf("\n)", start));
  const out = new Map();
  for (const m of body.matchAll(/^\s*(?:@[^\n]*\n\s*)?(?:var|val)\s+([A-Za-z0-9_]+)\s*:\s*([^=]+?)\s*=/gm)) {
    out.set(m[1], m[2].trim());
  }
  return out;
}

// The keys createPaymentSession writes whose value is NOT a plain scalar, and
// what each actually holds. Anything here that the Kotlin model also declares
// must declare a type able to hold it.
const STORED_SHAPES = {
  // resolveServicesTotal -> [{ name, price }]
  services:     { kotlin: /^List<(?!String>)/, why: "a list of {name, price} maps, not strings" },
  // serviceLayout -> number[]
  busyOffsets:  { kotlin: /^List<(Int|Long|Number)>/, why: "a list of slot offsets (numbers)" },
  // normalizeParty -> [{ name, services }]
  party:        { kotlin: /^List<(?!String>)/, why: "a list of guest maps, not strings" },
};

test("the appointment model does not misdescribe a stored array", () => {
  const fields = fieldsOf("AppointmentDocument");
  for (const [key, rule] of Object.entries(STORED_SHAPES)) {
    if (!fields.has(key)) continue;         // absent is a valid, safe answer
    const declared = fields.get(key);
    assert.match(declared, rule.kotlin,
      `AppointmentDocument.${key} is declared ${declared}, but Firestore holds ${rule.why}. ` +
      "Firestore does not skip a mismatched field — it throws inside the snapshot " +
      "listener, on the main thread, for every user who has one of these documents.");
  }
});

test("resolveServicesTotal really does return maps, so the rule above is not stale", () => {
  const { resolveServicesTotal } = require("../lib/money");
  const { services } = resolveServicesTotal({ Haircut: 500 }, ["Haircut"]);
  assert.strictEqual(typeof services[0], "object");
  assert.deepStrictEqual(Object.keys(services[0]).sort(), ["name", "price"]);
});

test("the appointment writes still store the field this rule is about", () => {
  // If a future change renames or drops `services` on the appointment, the rule
  // above silently stops protecting anything. Fail here instead.
  const payments = fs.readFileSync(path.join(__dirname, "..", "domains", "payments.js"), "utf8");
  const writes = payments.match(/^\s+services,\s*$/gm) || [];
  assert.strictEqual(writes.length, 2,
    "expected `services,` on both appointment-creation paths (cash and online)");
});

test("nothing in the app reads a services field off an appointment", () => {
  // The names are recovered by splitting serviceName (serviceNamesFrom), which
  // works for every booking ever made — including those written before the
  // server stored a breakdown at all.
  const src = fs.readFileSync(MODELS, "utf8");
  const start = src.indexOf("data class AppointmentDocument(");
  const body = src.slice(start, src.indexOf("\n)", start));
  assert.ok(!/^\s*val\s+services\s*:/m.test(body),
    "AppointmentDocument declares `services` again — see the comment in that class");
  assert.match(src, /fun serviceNamesFrom\(/,
    "serviceNamesFrom is what replaced it; reschedule and Book again both use it");
})
