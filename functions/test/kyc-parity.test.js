"use strict";

// Three clients submit identity verification, and the admin reviews them in one
// queue. If any of them names a field differently, or uploads to a different
// path, the admin's Verification tab shows an incomplete submission and
// submitKyc refuses one whose photos are not where it looks — so the three are
// checked against Android, which was the only one that worked end to end.
//
// Static on purpose: the web console is a single HTML file with no build step,
// and the iOS app needs a Mac to compile. These read the source each client
// actually ships.

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..", "..");
const read = (rel) => fs.readFileSync(path.join(ROOT, rel), "utf8");

const ANDROID_VM = read("app/src/main/java/com/safebeauty/app/viewmodel/KycViewModel.kt");
const ANDROID_STORAGE = read("app/src/main/java/com/safebeauty/app/data/firebase/StorageRepository.kt");
const IOS_SERVICE = read("ios/SafeBeauty/Services/KycService.swift");
const CONSOLE = read("public/provider/index.html");
const SERVER = read("functions/domains/identity.js");

/** The keys Android's submitKyc call sends: `"name" to value`. */
function androidKeys() {
  const call = ANDROID_VM.slice(ANDROID_VM.indexOf('getHttpsCallable("submitKyc")'));
  const block = call.slice(0, call.indexOf(".await()"));
  return [...block.matchAll(/"(\w+)"\s+to\s/g)].map((m) => m[1]).sort();
}

/** The keys the server reads from request.data. */
function serverKeys() {
  const from = SERVER.indexOf("exports.submitKyc = onCall(");
  const body = SERVER.slice(from, SERVER.indexOf("\nexports.", from + 10));
  return [...body.matchAll(/d\.(\w+)/g)].map((m) => m[1]).filter((k, i, a) => a.indexOf(k) === i).sort();
}

test("Android sends exactly what submitKyc reads", () => {
  // The anchor for the other two. If this fails, the server and Android have
  // drifted, which is a bigger problem than any client.
  assert.deepStrictEqual(androidKeys(), serverKeys());
  assert.strictEqual(androidKeys().length, 6);
});

test("the salon console sends the same six fields", () => {
  const at = CONSOLE.indexOf("httpsCallable(fns, 'submitKyc')(");
  assert.ok(at > 0, "the console no longer calls submitKyc");
  const block = CONSOLE.slice(at, CONSOLE.indexOf("});", at));
  const keys = [...block.matchAll(/^\s*(\w+):/gm)].map((m) => m[1]).sort();
  assert.deepStrictEqual(keys, androidKeys());
});

test("iOS sends the same six fields", () => {
  const at = IOS_SERVICE.indexOf('Callables.call("submitKyc"');
  assert.ok(at > 0, "iOS no longer calls submitKyc");
  const block = IOS_SERVICE.slice(at, IOS_SERVICE.indexOf("])", at));
  const keys = [...block.matchAll(/"(\w+)":/g)].map((m) => m[1]).sort();
  assert.deepStrictEqual(keys, androidKeys());
});

test("all three upload to the two paths submitKyc derives", () => {
  for (const file of ["tazkira.jpg", "selfie.jpg"]) {
    assert.match(SERVER, new RegExp("`kyc/\\$\\{appUser\\.uid\\}/" + file.replace(".", "\\.") + "`"));
    assert.match(ANDROID_STORAGE, new RegExp('"kyc/\\$uid/' + file.replace(".", "\\.") + '"'));
    assert.match(IOS_SERVICE, new RegExp('"kyc/\\\\\\(uid\\)/' + file.replace(".", "\\.") + '"'));
    assert.match(CONSOLE, new RegExp("`kyc/\\$\\{provider\\.uid\\}/" + file.replace(".", "\\.") + "`"));
  }
});

test("the console sends a provider who is not verified to KYC, not the dashboard", () => {
  // The bug this exists for: doLogin checked `status` and went straight to
  // enterApp(), so the console never asked anyone for a tazkira.
  const from = CONSOLE.indexOf("async function doLogin(");
  const body = CONSOLE.slice(from, CONSOLE.indexOf("\n}\n", from));
  const gate = body.search(/if\(\(m\.kycStatus \|\| 'NONE'\) !== 'APPROVED'\)\{ enterKyc\(/);
  assert.ok(gate > 0, "doLogin no longer routes an unverified provider to enterKyc()");
  const app = body.indexOf("await enterApp()");
  assert.ok(app > gate, "enterApp() must only be reached after the KYC check");
  assert.match(body.slice(gate, app), /else/, "enterApp() must be the else-branch of the KYC check");
});

test("the console's photo limits are Android's", () => {
  // ImageUtils.kt: MAX_DIMENSION 1024, JPEG_QUALITY 70, MAX_BASE64_BYTES 900_000.
  const android = read("app/src/main/java/com/safebeauty/app/util/ImageUtils.kt");
  assert.match(android, /MAX_DIMENSION = 1024/);
  assert.match(android, /JPEG_QUALITY {2}= 70/);
  assert.match(android, /MAX_BASE64_BYTES = 900_000/);
  assert.match(CONSOLE, /KYC_MAX_EDGE = 1024, KYC_JPEG_Q = 0\.7, KYC_MAX_BYTES = 900000/);
  // And the upload carries the type storage.rules' isValidImage() accepts.
  assert.match(CONSOLE, /const meta = \{ contentType:'image\/jpeg' \}/);
});

test("the console's selfie opens the front camera where there is one", () => {
  assert.match(CONSOLE, /photo\('selfie', 'kycSelfie', 'kycTakeSelfie', 'image\/\*', 'user'\)/);
});
