const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { AREAS, KEYS, CITIES, cityOf, LEGACY_KEYS, normalizeDistrict } = require("../lib/areas");

const KOTLIN = path.join(
  __dirname, "..", "..",
  "app/src/main/java/com/safebeauty/app/util/KabulAreas.kt"
);

test("the server copy still matches KabulAreas.kt exactly", () => {
  // lib/areas.js is a copy, and a copy nobody checks is a copy that rots. If the
  // app gains an area and the server does not, salons in it become unfilterable;
  // if a key is renamed on one side only, stored districts stop resolving.
  const src = fs.readFileSync(KOTLIN, "utf8");
  const rows = [...src.matchAll(/Area\(\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\s*\)/g)]
    .map((m) => ({ key: m[1], fa: m[2], en: m[3] }));

  assert.ok(rows.length > 0, "extracted nothing from KabulAreas.kt — has the shape changed?");
  assert.deepStrictEqual(
    AREAS, rows,
    "lib/areas.js has drifted from KabulAreas.kt — regenerate it"
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
  assert.strictEqual(LEGACY_KEYS.size, KEYS.length);
});

test("every area key names the city it is in", () => {
  // The whole reason for the prefix: Herat district 1 and Kabul district 1 must
  // not be one key. Anything that groups by area alone is then safe by
  // construction rather than by remembering to add a filter.
  for (const k of KEYS) assert.ok(cityOf(k), `${k} has no city prefix`);
  assert.strictEqual(new Set(KEYS.map(cityOf)).size, 1, "only Kabul is populated yet");
  assert.deepStrictEqual(CITIES.filter((c) => c.live).map((c) => c.key), ["KABUL"]);
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
