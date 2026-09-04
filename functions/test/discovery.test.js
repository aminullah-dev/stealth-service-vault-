const test = require("node:test");
const assert = require("node:assert");
const { deriveSalonDiscovery, storedDiscoveryFields } = require("../domains/discovery");

/**
 * A salon can fail to be found in three ways, and only two of them used to be
 * reported.
 *
 * The derivation refuses to guess an address it cannot resolve, which is right:
 * a guessed district puts a salon somewhere it is not. But the refusal has to
 * reach a person. districtKey "" derives city "", and a Firestore equality
 * never matches a document whose field is empty — so an unresolved salon is
 * absent from every filtered query, silently, for as long as nobody notices.
 */
test("a district that resolves to nothing reaches the review queue", () => {
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "پشت مسجد آبی", services: ["Haircut"] })
  );
  assert.strictEqual(d.districtKey, "", "an unrecognisable address must not be guessed at");
  assert.strictEqual(d.city, "", "city is derived from the district, so it is empty too");
  assert.strictEqual(d.needsDiscoveryReview, true, "and that has to be visible to an admin");
});

test("a blank district is not flagged — it is not an error to review", () => {
  // Flagging it would fill the queue with rows nobody can act on, and a queue
  // full of noise is one nobody reads.
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "", services: ["Haircut"] })
  );
  assert.strictEqual(d.needsDiscoveryReview, false);
});

test("a resolvable district yields a key, a city, and no review", () => {
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "KBL_D9_Makroryan", services: ["Haircut"] })
  );
  assert.strictEqual(d.districtKey, "KBL_D9_Makroryan");
  assert.strictEqual(d.city, "KABUL");
  assert.strictEqual(d.needsDiscoveryReview, false);
});

test("a legacy pre-prefix district still resolves, and carries its city", () => {
  // Production is full of these: every salon stored its district before the
  // keys were prefixed, and none of them have been rewritten.
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "D9_Makroryan", services: ["Haircut"] })
  );
  assert.strictEqual(d.districtKey, "KBL_D9_Makroryan");
  assert.strictEqual(d.city, "KABUL");
});

test("a guzar from another district is dropped, not stored", () => {
  // areaKey is only kept when the finer area actually sits inside the district
  // the salon claims. Otherwise the salon would display at an address it is not
  // at — and an unverifiable address should read as absent, not as a guess.
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({
      district: "HRT_D01", areaKey: "MZR_GuzarQarghan", services: ["Haircut"],
    })
  );
  assert.strictEqual(d.districtKey, "HRT_D01");
  assert.strictEqual(d.areaKey, "", "a Mazar guzar cannot sit in a Herat district");
});

test("a guzar that does sit in the district survives", () => {
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({
      district: "MZR_D02", areaKey: "MZR_GuzarQarghan", services: ["Haircut"],
    })
  );
  assert.strictEqual(d.areaKey, "MZR_GuzarQarghan");
});

/**
 * The provider's own explicit pick is honoured; free text is not second-guessed.
 */
test("a neighbourhood with no recorded parent is accepted inside its own city", () => {
  // Kabul's forty-two have no parent — nobody published the pairing, and
  // guessing it would place a salon in a district it is not in. Requiring one
  // made every Kabul محله unusable, so a salon there could never record the one
  // it had picked from its own dropdown; the value was silently discarded.
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "KBL_D17", areaKey: "KBL_Khair_Khana", services: ["Haircut"] })
  );
  assert.strictEqual(d.areaKey, "KBL_Khair_Khana");
});

test("same-city is still a real constraint, not an absence of one", () => {
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "HRT_D01", areaKey: "MZR_GuzarQarghan", services: ["Haircut"] })
  );
  assert.strictEqual(d.areaKey, "", "a Mazar guzar cannot sit in a Herat district");
});

test("ambiguous free text is still handed to a person, not resolved", () => {
  // "خیرخانه مینه ناحیه ۱۷" names a district and a neighbourhood. Picking the
  // district and filing the neighbourhood in areaKey looks like keeping both —
  // but nothing reads areaKey, so a customer searching خیرخانه would not find
  // the salon. Until that field is read, resolving this silently loses her.
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "خیرخانه مینه ناحیه 17", services: ["Haircut"] })
  );
  assert.strictEqual(d.districtKey, "");
  assert.strictEqual(d.needsDiscoveryReview, true);
});

/**
 * An ambiguous district is not an ambiguous city.
 *
 * This is the case the live data hit. "خیرخانه مینه ناحیه ۱۷" names both a
 * neighbourhood and a district number, so the derivation returns two
 * candidates and refuses to pick — correctly, because they are different
 * points on a map.
 *
 * But that refusal was also emptying `city`, and the two candidates are
 * KBL_D17 and KBL_Khair_Khana. Whichever one it is, the salon is in Kabul.
 * Emptying city there is not caution; it is a wrong answer in the other
 * direction, and an expensive one: `where("city", "==", "KABUL")` matches no
 * document whose city is "". Half the live catalogue was invisible to anyone
 * filtering by city, while the salon sat in the review queue looking handled.
 */
test("an ambiguous district still yields a city when the candidates agree on one", () => {
  const d = storedDiscoveryFields(
    deriveSalonDiscovery({ district: "خیرخانه مینه ناحیه 17", services: ["ناخن"] })
  );
  assert.deepStrictEqual(
    d.discoveryReview.districtCandidates, ["KBL_D17", "KBL_Khair_Khana"],
    "the ambiguity is real and must still be reported"
  );
  assert.strictEqual(d.districtKey, "", "and the district must still not be guessed");
  assert.strictEqual(d.city, "KABUL", "but the city was never in doubt");
  assert.strictEqual(d.needsDiscoveryReview, true, "a human still has to pick the district");
});

test("a district ambiguous across two cities yields no city", () => {
  // The guard on the rule above. If the candidates disagree about the city,
  // there is nothing shared to fall back to and "" is the honest answer —
  // otherwise this fix would put salons in cities they are not in, which is
  // the exact failure the original refusal existed to prevent.
  //
  // "ناحیه اول" is district one in Herat, Jalalabad and Mazar alike, so it
  // resolves to candidates in three cities and nothing is shared. Asserted
  // unconditionally: a test that skips its own assertion when the fixture
  // stops being ambiguous would keep passing after the fix regressed.
  const d = deriveSalonDiscovery({ district: "ناحیه اول", services: [] });

  const cities = new Set(d.districtCandidates.map((k) => k.split("_")[0]));
  assert.ok(cities.size > 1, `fixture must span cities, got ${[...cities]}`);
  assert.strictEqual(d.districtKey, "", "and must not resolve to a district");
  assert.strictEqual(d.city, "", "candidates spanning cities must not resolve to one");
});

/**
 * `discoveryUpToDate` compares `salon.city` against `derived.city`, and the
 * derivation did not return a `city` at all — only storedDiscoveryFields
 * computed one. So the comparison was `"KABUL" === undefined` on every salon
 * that had a city, the check never returned true, and the trigger and the
 * nightly sweep both attempted a write on every pass. Firestore absorbing
 * identical writes is the only reason that was invisible, and the comment
 * above the trigger credits the up-to-date check with stopping a loop it was
 * not in fact stopping.
 */
test("the derivation returns the same city that gets stored", () => {
  for (const district of ["KBL_D9_Makroryan", "خیرخانه مینه ناحیه 17", "", "پشت مسجد آبی"]) {
    const derived = deriveSalonDiscovery({ district, services: ["Haircut"] });
    assert.strictEqual(
      storedDiscoveryFields(derived).city, derived.city,
      `stored and derived city must agree for ${JSON.stringify(district)}, ` +
      "or discoveryUpToDate can never return true"
    );
    assert.strictEqual(typeof derived.city, "string", "and it must not be undefined");
  }
});
