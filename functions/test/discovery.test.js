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
