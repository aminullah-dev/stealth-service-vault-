const test = require("node:test");
const assert = require("node:assert");
const crypto = require("crypto");
const {
  authIsRecent, isHash, isSalt, rotationProblem,
  HASH_B64_LEN, SALT_B64_LEN, RECENT_AUTH_MS, LEGACY_AUTH_MS,
} = require("../lib/password");

/**
 * Changing a password writes two systems that cannot be written atomically —
 * the Firestore salt + pinHash, and the Firebase Auth password derived from
 * them. When they disagree, neither the old nor the new password opens the
 * account, and the only way back in is an admin reset.
 *
 * The Android client used to do this itself: it changed the Auth password
 * first and then called updatePinHash, keeping the new material only in
 * coroutine locals. A failure of the second call locked the account with no
 * way to retry, because the values needed for the retry were gone with the
 * coroutine. This is the pure half of the replacement.
 */

// The real shapes, produced the way PinHasher produces them.
const salt = () => crypto.randomBytes(16).toString("base64");
const hash = (pw, s) => crypto.pbkdf2Sync(pw, Buffer.from(s, "base64"), 65536, 32, "sha256")
  .toString("base64");

const okSalt = salt();
const good = () => ({
  currentPinHash:  hash("old-password", okSalt),
  newSalt:         okSalt,
  newPinHash:      hash("new-password", okSalt),
  newAuthPassword: hash("AUTH:new-password", okSalt),
});

// ── shapes ───────────────────────────────────────────────────────────────────

test("PinHasher's real output is the shape this accepts", () => {
  // Not asserted from the constants but from the algorithm: 16 bytes of salt
  // and a 256-bit hash, base64 with NO_WRAP, on all three clients.
  assert.strictEqual(okSalt.length, SALT_B64_LEN);
  assert.strictEqual(hash("x", okSalt).length, HASH_B64_LEN);
  assert.ok(isSalt(okSalt));
  assert.ok(isHash(hash("x", okSalt)));
});

test("a well-formed rotation has nothing wrong with it", () => {
  assert.strictEqual(rotationProblem(good()), null);
});

test("a missing or empty field is named, not ignored", () => {
  for (const field of ["currentPinHash", "newPinHash", "newAuthPassword", "newSalt"]) {
    const d = good();
    d[field] = "";
    assert.strictEqual(rotationProblem(d), field, `${field} empty`);
    delete d[field];
    assert.strictEqual(rotationProblem(d), field, `${field} absent`);
  }
});

test("a truncated or re-encoded hash is refused before it is stored", () => {
  // The failure this prevents is silent and permanent: a mangled value is
  // written, and from then on the correct password never matches it.
  const d = good();
  d.newPinHash = d.newPinHash.slice(0, 40);
  assert.strictEqual(rotationProblem(d), "newPinHash", "truncated");

  const e = good();
  e.newPinHash = Buffer.from(e.newPinHash).toString("base64");
  assert.strictEqual(rotationProblem(e), "newPinHash", "double-encoded");

  const f = good();
  f.newSalt = f.newSalt + "\n";
  assert.strictEqual(rotationProblem(f), "newSalt", "base64 with a newline is not NO_WRAP");
});

test("a hash that is not base64 at all is refused", () => {
  const d = good();
  d.currentPinHash = "!".repeat(HASH_B64_LEN);
  assert.strictEqual(rotationProblem(d), "currentPinHash");
});

test("the stored hash may never equal the Auth password", () => {
  // deriveAuthPassword is PBKDF2("AUTH:" + pw) and pinHash is PBKDF2(pw), over
  // the same salt — they cannot collide unless a client derived one of them
  // wrongly. If they did, the value stored on the user document would BE the
  // sign-in credential rather than a verifier for it.
  const d = good();
  d.newAuthPassword = d.newPinHash;
  assert.strictEqual(rotationProblem(d), "newAuthPassword");
});

test("domain separation actually holds for the real deriver", () => {
  const s = salt();
  assert.notStrictEqual(hash("pw", s), hash("AUTH:pw", s));
});

test("a non-string survives validation without throwing", () => {
  // request.data is whatever the caller sent.
  for (const junk of [null, undefined, 42, {}, [], { currentPinHash: 5 }]) {
    assert.strictEqual(typeof rotationProblem(junk), "string", `${JSON.stringify(junk)}`);
  }
});

// ── recency ──────────────────────────────────────────────────────────────────

test("a token minted from a sign-in just now is recent", () => {
  const now = 1_760_000_000_000;
  assert.ok(authIsRecent(now / 1000, now), "this instant");
  assert.ok(authIsRecent((now - 4 * 60 * 1000) / 1000, now), "four minutes ago");
});

test("a session that merely exists is not proof of a password", () => {
  // The whole point. currentPinHash cannot carry this proof: firestore.rules
  // grants the owner `get` on her own document, pinHash included, so any
  // signed-in session can read the stored hash and send it back. Recency of a
  // real authentication is the boundary — and it is exactly what a picked-up
  // unlocked phone does not have.
  const now = 1_760_000_000_000;
  assert.ok(!authIsRecent((now - 6 * 60 * 1000) / 1000, now), "six minutes ago");
  assert.ok(!authIsRecent((now - 8 * 60 * 60 * 1000) / 1000, now), "signed in this morning");
});

test("a missing or nonsense auth_time is never recent", () => {
  const now = 1_760_000_000_000;
  for (const bad of [undefined, null, 0, -1, "", "abc", NaN, Infinity]) {
    assert.ok(!authIsRecent(bad, now), String(bad));
  }
});

test("auth_time is read as seconds, not milliseconds", () => {
  // Firebase issues auth_time in seconds. Reading it as milliseconds would put
  // every real token ~55 years in the past and refuse every change; reading a
  // millisecond value as seconds would put it far in the future.
  const now = 1_760_000_000_000;
  assert.ok(authIsRecent(now / 1000, now), "seconds are accepted");
  assert.ok(!authIsRecent(now, now), "a millisecond value is not mistaken for seconds");
});

test("a token from the future is a clock problem, not a fresh login", () => {
  const now = 1_760_000_000_000;
  assert.ok(!authIsRecent((now + 10 * 60 * 1000) / 1000, now), "ten minutes ahead");
  assert.ok(authIsRecent((now + 30 * 1000) / 1000, now), "small skew is tolerated");
});

test("the legacy window is wider, and still bounded", () => {
  // updatePinHash is reached by app builds already installed, which do not
  // force a token refresh; changePassword is called only by clients this repo
  // controls, which do. Both still refuse a session that is merely old.
  const now = 1_760_000_000_000;
  const ago = (min) => (now - min * 60 * 1000) / 1000;
  assert.ok(!authIsRecent(ago(20), now), "20 min fails the strict window");
  assert.ok(authIsRecent(ago(20), now, LEGACY_AUTH_MS), "and passes the legacy one");
  assert.ok(!authIsRecent(ago(45), now, LEGACY_AUTH_MS), "45 min fails both");
  assert.ok(LEGACY_AUTH_MS > RECENT_AUTH_MS, "legacy must be the wider of the two");
});
