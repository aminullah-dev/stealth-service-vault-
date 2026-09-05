const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { AREAS, KEYS, CITIES, cityOf, LEGACY_KEYS, normalizeDistrict } = require("../lib/areas");

const KOTLIN = path.join(
  __dirname, "..", "..",
  "app/src/main/java/com/safebeauty/app/util/Areas.kt"
);

test("the server copy still matches Areas.kt exactly", () => {
  // lib/areas.js is a copy, and a copy nobody checks is a copy that rots. If the
  // app gains an area and the server does not, salons in it become unfilterable;
  // if a key is renamed on one side only, stored districts stop resolving.
  const src = fs.readFileSync(KOTLIN, "utf8");
  const rows = [...src.matchAll(
    /Area\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*(?:,\s*(DISTRICT|GUZAR|NEIGHBOURHOOD)\s*)?(?:,\s*"([^"]*)"\s*)?\)/g
  )].map((m) => {
    const row = { key: m[1], fa: m[2], en: m[3], kind: m[4] || "NEIGHBOURHOOD" };
    if (m[5]) row.parent = m[5];
    return row;
  });

  assert.ok(rows.length > 0, "extracted nothing from Areas.kt — has the shape changed?");
  assert.deepStrictEqual(
    AREAS, rows,
    "lib/areas.js has drifted from Areas.kt — regenerate it"
  );
});

test("keys are unique", () => {
  assert.strictEqual(new Set(KEYS).size, KEYS.length);
});

test("a district already stored as a canonical key passes straight through", () => {
  assert.deepStrictEqual(normalizeDistrict("KBL_D9_Makroryan"), { key: "KBL_D9_Makroryan" });
  assert.deepStrictEqual(normalizeDistrict("KBL_Khair_Khana"), { key: "KBL_Khair_Khana" });
});

test("a pre-prefix key still resolves, because production is full of them", () => {
  // Every salon stored its district before the keys carried a city. If these
  // stopped resolving the day this shipped, every existing salon would drop out
  // of every neighbourhood filter at once — and the backfill runs after the
  // deploy, not before it.
  assert.deepStrictEqual(normalizeDistrict("D9_Makroryan"), { key: "KBL_D9_Makroryan" });
  assert.deepStrictEqual(normalizeDistrict("Shirpur"), { key: "KBL_Shirpur" });
  // Only Kabul has pre-prefix data — Herat's keys were born prefixed, so there
  // is nothing legacy to map for them.
  assert.strictEqual(LEGACY_KEYS.size, KEYS.filter((k) => k.startsWith("KBL_")).length);
  assert.strictEqual(normalizeDistrict("HRT_D01").key, "HRT_D01");
});

test("every area key names the city it is in", () => {
  // The whole reason for the prefix: Herat district 1 and Kabul district 1 must
  // not be one key. Anything that groups by area alone is then safe by
  // construction rather than by remembering to add a filter.
  for (const k of KEYS) assert.ok(cityOf(k), `${k} has no city prefix`);
  assert.deepStrictEqual([...new Set(KEYS.map(cityOf))].sort(), ["HERAT", "JALALABAD", "KABUL", "MAZAR"]);
  assert.deepStrictEqual(CITIES.filter((c) => c.live).map((c) => c.key), ["KABUL", "HERAT", "MAZAR", "JALALABAD"]);
});

test("an unambiguous label resolves to its key", () => {
  assert.deepStrictEqual(normalizeDistrict("شیرپور"), { key: "KBL_Shirpur" });
  assert.deepStrictEqual(normalizeDistrict("Shirpur"), { key: "KBL_Shirpur" });
  assert.deepStrictEqual(normalizeDistrict("  دهبوری "), { key: "KBL_Dehbori" });
});

test("the live legacy value is reported as ambiguous, not guessed", () => {
  // Real production data. "خیرخانه مینه ناحیه 17" names both an area
  // (Khair_Khana) and a district number (D17). Choosing one silently moves the
  // salon for anyone searching by neighbourhood, and nobody would know which.
  const r = normalizeDistrict("خیرخانه مینه ناحیه 17");
  assert.strictEqual(r.key, undefined);
  assert.deepStrictEqual(r.candidates.sort(), ["KBL_D17", "KBL_Khair_Khana"]);
});

test("Persian and Arabic digits are read as digits", () => {
  assert.deepStrictEqual(normalizeDistrict("ناحیه ۱۷"), { key: "KBL_D17" });
  assert.deepStrictEqual(normalizeDistrict("ناحیه 17"), { key: "KBL_D17" });
});

test("nothing recognisable yields nothing, rather than a wrong key", () => {
  for (const bad of ["", null, undefined, "   ", "Toronto", "!!!"]) {
    assert.deepStrictEqual(normalizeDistrict(bad), {}, `should not resolve ${bad}`);
  }
});

test("a ناحیه and a محله are not the same level", () => {
  // They were flattened into one list, so a form could offer a district and a
  // neighbourhood as if they were alternatives. Kind separates them.
  const kinds = new Set(AREAS.map((a) => a.kind));
  assert.deepStrictEqual([...kinds].sort(), ["DISTRICT", "GUZAR", "NEIGHBOURHOOD"]);
  const districts = (c) => AREAS.filter((a) => cityOf(a.key) === c && a.kind === "DISTRICT").length;
  assert.deepStrictEqual(
    { KABUL: districts("KABUL"), HERAT: districts("HERAT"),
      MAZAR: districts("MAZAR"), JALALABAD: districts("JALALABAD") },
    { KABUL: 22, HERAT: 15, MAZAR: 12, JALALABAD: 9 }
  );
});

test("a sub-area's parent is a real district in the same city, or absent", () => {
  // Absent is the honest value where the pairing is not sourced. Kabul's forty-
  // two have none: putting a salon in a district it is not in would be a wrong
  // answer nobody could see. Herat's twelve are named by the municipality.
  const districts = new Set(AREAS.filter((a) => a.kind === "DISTRICT").map((a) => a.key));
  for (const a of AREAS) {
    if (!a.parent) continue;
    assert.ok(districts.has(a.parent), `${a.key} names a parent that is not a district`);
    assert.strictEqual(cityOf(a.parent), cityOf(a.key), `${a.key} names a parent in another city`);
    assert.notStrictEqual(a.kind, "DISTRICT", `${a.key} is a district with a parent`);
  }
  // Herat's twelve plus Mazar's nine. Jalalabad's guzars are numbered rather
  // than named and only two appear in the sources, so none are listed: two of
  // an unknown number would look like the whole list to anyone using the form.
  assert.strictEqual(AREAS.filter((a) => a.parent).length, 21);
  assert.strictEqual(AREAS.filter((a) => a.kind === "GUZAR").length, 5);
});

test("only cities with real districts are live", () => {
  for (const c of CITIES.filter((x) => x.live)) {
    assert.ok(
      AREAS.some((a) => cityOf(a.key) === c.key && a.kind === "DISTRICT"),
      `${c.key} is live with no districts`
    );
  }
  assert.deepStrictEqual(CITIES.filter((c) => c.live).map((c) => c.key), ["KABUL", "HERAT", "MAZAR", "JALALABAD"]);
});
