const test = require("node:test");
const assert = require("node:assert");
const { normalize } = require("../lib/categories");

/**
 * The table below is duplicated verbatim in
 * app/src/test/java/com/safebeauty/app/util/SearchKeyTest.kt.
 *
 * It has to be. Firestore matches a prefix against the STORED value, so the
 * device must derive the same string the server wrote or the query is a range
 * over spelling that never existed. The server stored a salon as
 * "سالن ارایشی عروس خانم" while the app sent "سالن آرایشی…", and a customer
 * typing the name exactly as it appeared on her screen was told there were no
 * results.
 */
const CASES = [
  ["سالن آرایشی عروس خانم", "سالن ارایشی عروس خانم"],   // madda folded
  ["آرایشگاه زیبایی",       "ارایشگاه زیبایی"],
  ["نوري",                  "نوری"],                     // Arabic yeh → Persian
  ["حکيم الله",             "حکیم الله"],
  ["مکياژ",                 "مکیاژ"],
  ["زيبايي كابل",           "زیبایی کابل"],              // Arabic yeh AND kaf
  ["Shaghayeq Ha",          "shaghayeq ha"],
  ["  spaced   out  ",      "spaced out"],
  ["فاطمة",                 "فاطمه"],                    // teh marbuta → heh
  ["",                      ""],
];

test("normalize folds the spellings Dari and Pashto keyboards actually produce", () => {
  for (const [input, expected] of CASES) {
    assert.equal(normalize(input), expected, `normalize(${JSON.stringify(input)})`);
  }
});

test("normalizing twice changes nothing", () => {
  // The device normalizes a search term; the server normalized the stored name.
  // If the operation were not idempotent the two could still disagree.
  for (const [input] of CASES) {
    assert.equal(normalize(normalize(input)), normalize(input));
  }
});

test("a name and the same name typed on the other keyboard collapse together", () => {
  // The whole point: two spellings of one salon must not be two salons.
  assert.equal(normalize("زيبايي"), normalize("زیبایی"));
  assert.equal(normalize("كابل"), normalize("کابل"));
  assert.equal(normalize("آرایش"), normalize("ارایش"));
});

test("null and undefined are the empty key, not a crash", () => {
  assert.equal(normalize(null), "");
  assert.equal(normalize(undefined), "");
});
