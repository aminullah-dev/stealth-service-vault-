const test = require("node:test");
const assert = require("node:assert");
const { CANONICAL, normalize, categoryFor, categoriesFor } = require("../lib/categories");

test("the live production data that exposed the bug now maps", () => {
  // These are the actual stored services of the two live salons. Before this
  // module, tapping the ناخن chip on the salon that offers ناخن showed nothing.
  assert.strictEqual(categoryFor("ناخن"), "Nails");
  assert.strictEqual(categoryFor("ارایش"), "Makeup");
});

test("a salon's whole list becomes a stable category array", () => {
  const r = categoriesFor(["ناخن", "ارایش"]);
  assert.deepStrictEqual(r.categories, ["Makeup", "Nails"]); // CANONICAL order
  assert.deepStrictEqual(r.unmatched, []);
});

test("order of services does not change the stored array", () => {
  const a = categoriesFor(["ارایش", "ناخن"]).categories;
  const b = categoriesFor(["ناخن", "ارایش"]).categories;
  assert.deepStrictEqual(a, b);
});

test("all three languages reach the same category", () => {
  for (const term of ["Hair", "haircut", "مو", "رنگ مو", "ویښتان"]) {
    assert.strictEqual(categoryFor(term), "Hair", `failed for ${term}`);
  }
  for (const term of ["Makeup", "bridal", "آرایش", "میکاپ", "سینګار"]) {
    assert.strictEqual(categoryFor(term), "Makeup", `failed for ${term}`);
  }
  for (const term of ["Nails", "manicure", "ناخن", "مانیکور", "نوکان"]) {
    assert.strictEqual(categoryFor(term), "Nails", `failed for ${term}`);
  }
  for (const term of ["Skincare", "facial", "پوست", "فیشل"]) {
    assert.strictEqual(categoryFor(term), "Skincare", `failed for ${term}`);
  }
  for (const term of ["Eyebrow", "threading", "ابرو", "وروځې"]) {
    assert.strictEqual(categoryFor(term), "Eyebrows", `failed for ${term}`);
  }
});

test("eyebrows is وروځې, not وریځې — clouds must not match", () => {
  // One letter apart. Getting it wrong would mean the Pashto synonym silently
  // never matches, which looks identical to a salon offering no brow service.
  assert.strictEqual(categoryFor("وروځې"), "Eyebrows");
  assert.strictEqual(categoryFor("وریځې"), null);
});

test("alef and ye variants fold to one form", () => {
  assert.strictEqual(normalize("آرایش"), normalize("ارایش"));
  assert.strictEqual(normalize("مويي"), normalize("مویی"));
  assert.strictEqual(categoryFor("آرایش"), categoryFor("ارایش"));
});

test("an invisible zero-width non-joiner does not break matching", () => {
  assert.strictEqual(categoryFor("مراقبت‌پوست"), "Skincare");
});

test("case and surrounding whitespace do not matter", () => {
  assert.strictEqual(categoryFor("  HAIR Cut  "), "Hair");
  assert.strictEqual(categoryFor("Manicure"), "Nails");
});

test("a longer synonym wins over a shorter one inside it", () => {
  // "کاشت ناخن" must land on Nails, not on whatever "ناخن" alone would give if
  // a shorter synonym from another category also appeared.
  assert.strictEqual(categoryFor("کاشت ناخن"), "Nails");
});

test("terms found only by checking real production data", () => {
  // A run of this mapper over every service string in production appointments
  // matched 9 of 11. "فیس واش" -- face wash -- was a real gap no hand-written
  // test had thought of.
  assert.strictEqual(categoryFor("فیس واش"), "Skincare");
  assert.strictEqual(categoryFor("فیس"), "Skincare");
});

test("something unrecognisable is reported, never guessed", () => {
  // 'mo' is real live data. It might be a truncated مو, or a typo. Guessing puts
  // a salon under a category it may not serve, and the customer finds out by
  // turning up.
  assert.strictEqual(categoryFor("mo"), null);

  const r = categoriesFor(["mo", "ناخن", "   "]);
  assert.deepStrictEqual(r.categories, ["Nails"]);
  assert.deepStrictEqual(r.unmatched, ["mo"]);
});

test("empty and malformed input is safe", () => {
  assert.strictEqual(categoryFor(""), null);
  assert.strictEqual(categoryFor(null), null);
  assert.strictEqual(categoryFor(undefined), null);
  assert.deepStrictEqual(categoriesFor(null), { categories: [], unmatched: [] });
  assert.deepStrictEqual(categoriesFor([]), { categories: [], unmatched: [] });
});

test("the canonical list matches the app's chips", () => {
  // CATEGORY_KEYS in DashboardViewModel.kt:62 is ["All", ...these]. If the app
  // gains a chip and this list does not, that chip silently returns nothing —
  // the exact bug this module exists to fix.
  assert.deepStrictEqual(CANONICAL, ["Hair", "Makeup", "Nails", "Skincare", "Eyebrows"]);
});
