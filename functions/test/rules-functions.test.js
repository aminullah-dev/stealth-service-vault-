/**
 * Every function called in firestore.rules / storage.rules must be defined.
 *
 * Written 2026-09-06 after establishing, with a rules file that called
 * `thisHelperDoesNotExist()`, that nothing else catches a mistyped helper:
 *
 *   - `firebase emulators:exec --only firestore` starts, runs the script and
 *     exits 0. Nothing appears in firestore-debug.log. The "Security rules
 *     compile" CI job's whole first step is that command, and its comment says
 *     it "catches a change that would otherwise look successful". For this it
 *     does not.
 *   - Firebase's own compiler (firebaserules projects:test) does report it,
 *     but as `WARNING | Invalid function name`, not an error — so
 *     `firebase deploy --only firestore:rules` would deploy it.
 *
 * What ships then is a rule that raises an evaluation error every time it runs,
 * which Firestore treats as deny. So one typo silently locks every user out of
 * whatever that rule guards, deploys cleanly, and looks healthy from CI. This
 * repository's recurring defect, in its exact shape.
 *
 * A test rather than a deploy-time check on purpose: it needs no credentials,
 * so it runs on a fork and a fresh clone like everything else in this suite.
 */

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

/** Everything the rules language itself provides as a bare call. */
const BUILTINS = new Set([
  "get", "getAfter", "exists", "existsAfter", "debug",
  "float", "int", "string", "bool", "path",
  "duration", "timestamp", "math", "hashing", "latlng",
]);
/** Keywords that can be followed by a parenthesis. */
const KEYWORDS = new Set(["if", "return", "in", "is", "allow", "match", "function"]);

/** Bare `name(` — an identifier not preceded by a dot, so `x.size()` is skipped. */
const CALL = /(^|[^.\w])([A-Za-z_]\w*)\s*\(/g;
const DEF = /function\s+([A-Za-z_]\w*)\s*\(/g;

function undefinedCalls(source) {
  // Comments can hold prose with parentheses; strip them first.
  const code = source.replace(/\/\/[^\n]*/g, "").replace(/\/\*[\s\S]*?\*\//g, "");
  const defined = new Set();
  for (const m of code.matchAll(DEF)) defined.add(m[1]);
  const missing = new Map();
  for (const m of code.matchAll(CALL)) {
    const name = m[2];
    if (defined.has(name) || BUILTINS.has(name) || KEYWORDS.has(name)) continue;
    const line = code.slice(0, m.index).split("\n").length;
    if (!missing.has(name)) missing.set(name, line);
  }
  return missing;
}

for (const file of ["firestore.rules", "storage.rules"]) {
  test(`${file}: every function called is defined`, () => {
    const p = path.join(__dirname, "..", "..", file);
    const missing = undefinedCalls(fs.readFileSync(p, "utf8"));
    assert.deepStrictEqual(
      [...missing.entries()].map(([n, l]) => `${n}() at line ${l}`),
      [],
      `${file} calls a function it does not define. Firebase deploys this with ` +
      "only a warning, and the rule then denies everything it guards."
    );
  });
}

test("the check itself catches a mistyped helper", () => {
  const missing = undefinedCalls(`
    service cloud.firestore {
      function isSignedIn() { return request.auth != null; }
      match /users/{uid} {
        allow read: if isSignedIn() && request.resource.data.keys().hasOnly(['a']);
        allow create: if thisHelperDoesNotExist();
      }
    }
  `);
  assert.deepStrictEqual([...missing.keys()], ["thisHelperDoesNotExist"]);
});
