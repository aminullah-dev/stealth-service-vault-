const test = require("node:test");
const assert = require("node:assert");
const { deriveReferralCode, maxAttempts, BACKFILL_MIN_ATTEMPT } = require("../lib/referral");

/**
 * Attempt 0 must reproduce, byte for byte, what RegisterViewModel.kt:152 writes:
 *
 *     val referralCode = "SB" + uid.replace("-", "").take(6).uppercase()
 *
 * and what adminCreateSalon writes in domains/admin.js. If the backfill derives
 * anything else, an old account gets a code that is not the one its owner would
 * have had, which is harmless — but a code that DISAGREES with a code already
 * written for the same uid would hand two identities to one account, and the
 * referral lookup takes the first match.
 */
test("attempt 0 matches what registration writes", () => {
  const uid = "3f2a91c4-77bd-4e18-9a0e-1c2b3d4e5f60";
  assert.strictEqual(deriveReferralCode(uid, 0), "SB3F2A91");
  // The Kotlin: "3f2a91c477bd4e189a0e1c2b3d4e5f60".take(6) == "3f2a91"
  assert.strictEqual(deriveReferralCode(uid), "SB3F2A91");
});

test("the derivation is stable — running the backfill twice writes the same code", () => {
  const uid = "00000000-1111-2222-3333-444444444444";
  assert.strictEqual(deriveReferralCode(uid, 0), deriveReferralCode(uid, 0));
});

test("escalation takes two more characters each time", () => {
  const uid = "3f2a91c4-77bd-4e18-9a0e-1c2b3d4e5f60";
  assert.strictEqual(deriveReferralCode(uid, 1), "SB3F2A91C4");
  assert.strictEqual(deriveReferralCode(uid, 2), "SB3F2A91C477");
});

test("two uids sharing a prefix collide at attempt 0 and separate at attempt 1", () => {
  const a = "3f2a91c4-0000-0000-0000-000000000000";
  const b = "3f2a91ff-0000-0000-0000-000000000000";
  assert.strictEqual(deriveReferralCode(a, 0), deriveReferralCode(b, 0));
  assert.notStrictEqual(deriveReferralCode(a, 1), deriveReferralCode(b, 1));
});

test("a uid too short to escalate returns '' rather than repeating itself", () => {
  // Without this the caller's loop would keep testing an identical candidate,
  // find it taken every time, and never terminate on its own.
  assert.strictEqual(deriveReferralCode("abcdef", 0), "SBABCDEF");
  assert.strictEqual(deriveReferralCode("abcdef", 1), "");
  assert.strictEqual(maxAttempts("abcdef"), 1);
});

test("an unusable uid yields no code at all", () => {
  for (const bad of ["", null, undefined, "abc", "--", "-----"]) {
    assert.strictEqual(deriveReferralCode(bad, 0), "", `for ${JSON.stringify(bad)}`);
    assert.strictEqual(maxAttempts(bad), 0, `for ${JSON.stringify(bad)}`);
  }
});

test("a full uuid gives the loop 14 attempts and they are all distinct", () => {
  const uid = "3f2a91c4-77bd-4e18-9a0e-1c2b3d4e5f60";   // 32 hex chars
  const n = maxAttempts(uid);
  assert.strictEqual(n, 14);
  const seen = new Set();
  for (let i = 0; i < n; i += 1) {
    const c = deriveReferralCode(uid, i);
    assert.ok(c, `attempt ${i} produced nothing`);
    assert.ok(!seen.has(c), `attempt ${i} repeated ${c}`);
    seen.add(c);
  }
  // One past the end stops the caller rather than repeating the last value.
  assert.strictEqual(deriveReferralCode(uid, n), "");
});

test("hyphens are stripped and the case is normalised", () => {
  assert.strictEqual(deriveReferralCode("AbCdEf12-3456", 0), "SBABCDEF");
});

/**
 * The backfill and registration must not compete for the same codes.
 *
 * Registration derives attempt 0 on the device and writes it with no uniqueness
 * check — it cannot do one, because the users collection is not client-listable.
 * If the backfill also handed out six-character codes, every signup after it
 * would be rolling against a far fuller table, and a duplicate silently credits
 * whichever account the referral lookup's limit(1) returns first.
 *
 * Length is what keeps them apart, so length is what gets tested.
 */
test("a backfilled code can never equal a code registration would write", () => {
  assert.ok(BACKFILL_MIN_ATTEMPT >= 1, "the backfill must not start at attempt 0");
  const uids = [
    "3f2a91c4-77bd-4e18-9a0e-1c2b3d4e5f60",
    "00000000-1111-2222-3333-444444444444",
    "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "abcdef01-2345-6789-abcd-ef0123456789",
  ];
  for (const uid of uids) {
    const registration = deriveReferralCode(uid, 0);
    for (let a = BACKFILL_MIN_ATTEMPT; a < maxAttempts(uid); a += 1) {
      const backfilled = deriveReferralCode(uid, a);
      assert.notStrictEqual(backfilled, registration, `uid ${uid} attempt ${a}`);
      assert.ok(backfilled.length > registration.length,
        `attempt ${a} for ${uid} is not longer than registration's`);
    }
  }
});

test("no backfill attempt can produce a registration-length code, for any uid", () => {
  // The guarantee is structural, not statistical: registration always writes
  // 2 + 6 characters, and every backfill attempt writes strictly more.
  const uid = "3f2a91c4-77bd-4e18-9a0e-1c2b3d4e5f60";
  const registrationLen = deriveReferralCode(uid, 0).length;
  for (let a = BACKFILL_MIN_ATTEMPT; a < maxAttempts(uid); a += 1) {
    assert.notStrictEqual(deriveReferralCode(uid, a).length, registrationLen,
      `attempt ${a} collides with the registration namespace`);
  }
});
