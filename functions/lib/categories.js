/**
 * Mapping free-text service names onto the app's category vocabulary.
 *
 * Salons type their services as free text — `pf.services.push(v)` in the salon
 * console, `serviceInput.trim()` at registration — and nothing has ever
 * constrained what they type. The customer app's category chips, meanwhile,
 * filter with an English key list (Hair, Makeup, Nails, Skincare, Eyebrows).
 *
 * Those two halves have never met. A salon offering "ناخن" does not match the
 * key "Nails", so every category chip returns nothing, and it looks exactly like
 * a filter working correctly on an empty result. This module is what connects
 * them: it derives a canonical `categories` array the server can index and query.
 *
 * Deliberately conservative. A service it cannot confidently place is reported
 * as unmatched rather than guessed into a bucket — invariant I-15, detection
 * automatic, correction human. A wrong guess is worse than a gap here: it puts a
 * salon under a category it does not serve, and the customer finds out by
 * arriving.
 */

const CANONICAL = ["Hair", "Makeup", "Nails", "Skincare", "Eyebrows"];

/**
 * Synonyms per category, in the three languages the app ships.
 *
 * Matching is substring-based on a normalized form, so entries here are stems
 * rather than whole phrases: "رنگ مو" matches through "مو".
 *
 * Pashto note: eyebrows is وروځې. وریځې means clouds — one letter apart, and
 * the kind of mistake that would silently never match anything.
 */
const SYNONYMS = {
  Hair: [
    // English
    "hair", "haircut", "cut", "blow dry", "blowdry", "keratin", "balayage",
    "highlight", "color", "colour", "dye", "perm", "straighten",
    // Dari / Persian
    "مو", "موي", "مویی", "کوتاهی", "رنگ مو", "فر", "صافی", "کراتین", "بلوند", "هایلایت",
    // Pashto
    "ویښت", "وېښت", "ویښتان", "وېښته",
  ],
  Makeup: [
    "makeup", "make up", "bridal", "bride", "party makeup", "foundation", "contour",
    "آرایش", "ارایش", "میکاپ", "عروس", "مجلسی", "بزم",
    "سینګار", "سینگار", "ښایست",
  ],
  Nails: [
    "nail", "nails", "manicure", "pedicure", "gel", "acrylic",
    "ناخن", "مانیکور", "پدیکور", "ژل", "کاشت ناخن",
    "نوک", "نوکان",
  ],
  Skincare: [
    "skin", "skincare", "facial", "face", "cleansing", "peel", "mask", "hydra",
    // "فیس واش" is real production data — Persian rendering of "face wash".
    // It matched nothing until a run against live service names surfaced it.
    "پوست", "مراقبت پوست", "فیشل", "فیس", "فیس واش", "پاکسازی", "ماسک", "لایه برداری", "میکرودرم",
    "پوستکی", "پوستکي",
  ],
  Eyebrows: [
    "eyebrow", "brow", "brows", "threading", "microblading", "lash", "lashes",
    "ابرو", "ابروها", "بند انداختن", "بندانداختن", "تتو ابرو", "مژه",
    "وروځ", "وروځې", "بڼه",
  ],
};

/**
 * Fold the many ways the same Arabic-script word gets typed into one form.
 *
 * Without this, "آرایش" and "ارایش" are different strings, and so are the Arabic
 * and Persian forms of ye and kaf — which keyboards across the region produce
 * interchangeably. Also strips the zero-width non-joiner, which is invisible and
 * therefore impossible to debug by eye.
 */
function normalize(input) {
  return String(input == null ? "" : input)
    .toLowerCase()
    .replace(/[‌‍ً-ْـ]/g, "")  // ZWNJ/ZWJ, harakat, tatweel
    .replace(/[أإآ]/g, "ا")          // أ إ آ  -> ا
    .replace(/[يى]/g, "ی")                // ي ى    -> ی
    .replace(/ك/g, "ک")                        // ك      -> ک
    .replace(/ة/g, "ه")                        // ة      -> ه
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * The category a single free-text service belongs to, or null when unsure.
 *
 * Longer synonyms are tried first so a specific term wins over a general one
 * that happens to be a substring of it.
 */
function categoryFor(serviceName) {
  const s = normalize(serviceName);
  if (!s) return null;

  let best = null;
  let bestLen = 0;
  for (const category of CANONICAL) {
    for (const syn of SYNONYMS[category]) {
      const n = normalize(syn);
      if (n && s.includes(n) && n.length > bestLen) {
        best = category;
        bestLen = n.length;
      }
    }
  }
  return best;
}

/**
 * Derive the categories for a salon's whole service list.
 *
 * Returns both what was matched and what was not. The unmatched list is the
 * point: it is what an admin reviews, and what tells you the vocabulary needs a
 * new synonym rather than that a salon typed something meaningless.
 */
function categoriesFor(services) {
  const list = Array.isArray(services) ? services : [];
  const matched = new Set();
  const unmatched = [];

  for (const raw of list) {
    const c = categoryFor(raw);
    if (c) matched.add(c);
    else if (String(raw || "").trim()) unmatched.push(String(raw).trim());
  }

  // Ordered by CANONICAL rather than insertion, so the stored array is stable
  // and two salons with the same services produce byte-identical values.
  return {
    categories: CANONICAL.filter((c) => matched.has(c)),
    unmatched,
  };
}

module.exports = { CANONICAL, SYNONYMS, normalize, categoryFor, categoriesFor };
