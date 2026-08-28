/**
 * The cities SafeBeauty covers, and the areas inside them.
 *
 * Mirrored from KabulAreas.kt, which is the single source of truth;
 * test/areas.test.js re-extracts the Kotlin file and fails if the two drift.
 *
 * Every area key carries its city — KBL_D1_OldCity — because Herat's first
 * district is also numbered one. An unqualified key in a Herat salon's document
 * would be uninterpretable, and any aggregation grouping by area alone would
 * silently add two cities together.
 *
 * A salon's `district` is supposed to be a [key] — never a display label — so
 * that changing a translation cannot break stored data. In practice some older
 * salons hold free text, and all of them hold the pre-prefix keys, which is why
 * normalizeDistrict exists and why LEGACY_KEYS is consulted before giving up.
 */

const CITIES = [
  { key: "KABUL",     fa: "کابل",      en: "Kabul",          live: true  },
  { key: "HERAT",     fa: "هرات",      en: "Herat",          live: false },
  { key: "MAZAR",     fa: "مزار شریف", en: "Mazar-e Sharif", live: false },
  { key: "JALALABAD", fa: "جلال‌آباد", en: "Jalalabad",      live: false },
];

const CITY_BY_PREFIX = { KBL: "KABUL", HRT: "HERAT", MZR: "MAZAR", JAA: "JALALABAD" };

/** The city an area key belongs to, read off its prefix. */
function cityOf(areaKey) {
  return CITY_BY_PREFIX[String(areaKey || "").split("_")[0]] || "";
}

const AREAS = [
  { key: "KBL_D1_OldCity",          fa: "ناحیه ۱ – شهر کهنه",             en: "District 1 – Old City" },
  { key: "KBL_D2_ShahreNaw",        fa: "ناحیه ۲ – شهرنو",                en: "District 2 – Shahr-e Naw" },
  { key: "KBL_D3_KarteChar",        fa: "ناحیه ۳ – کارته چهار",           en: "District 3 – Karte Char" },
  { key: "KBL_D4_KoteSangi",        fa: "ناحیه ۴ – کوته سنگی",            en: "District 4 – Kote Sangi" },
  { key: "KBL_D5_Company",          fa: "ناحیه ۵ – کمپنی",                en: "District 5 – Company" },
  { key: "KBL_D6_Darulaman",        fa: "ناحیه ۶ – دارالامان",            en: "District 6 – Darulaman" },
  { key: "KBL_D7_ChihilSutun",      fa: "ناحیه ۷ – چهلستون",              en: "District 7 – Chihil Sutun" },
  { key: "KBL_D8_KarteNaw",         fa: "ناحیه ۸ – کارته نو",             en: "District 8 – Karte Naw" },
  { key: "KBL_D9_Makroryan",        fa: "ناحیه ۹ – مکروریان",             en: "District 9 – Makroryan" },
  { key: "KBL_D10_WazirAkbarKhan",  fa: "ناحیه ۱۰ – وزیراکبرخان",         en: "District 10 – Wazir Akbar Khan" },
  { key: "KBL_D11_KhairKhana",      fa: "ناحیه ۱۱ – خیرخانه",             en: "District 11 – Khair Khana" },
  { key: "KBL_D12_AhmadShahBaba",   fa: "ناحیه ۱۲ – احمد شاه بابا مینه",  en: "District 12 – Ahmad Shah Baba Mina" },
  { key: "KBL_D13_DashteBarchi",    fa: "ناحیه ۱۳ – دشت برچی",            en: "District 13 – Dasht-e Barchi" },
  { key: "KBL_D14_RahmanMina",      fa: "ناحیه ۱۴ – رحمان مینه",          en: "District 14 – Rahman Mina" },
  { key: "KBL_D15_KhwajaBughra",    fa: "ناحیه ۱۵ – خواجه بغرا",          en: "District 15 – Khwaja Bughra" },
  { key: "KBL_D16",                 fa: "ناحیه ۱۶",                       en: "District 16" },
  { key: "KBL_D17",                 fa: "ناحیه ۱۷",                       en: "District 17" },
  { key: "KBL_D18_Bagrami",         fa: "ناحیه ۱۸ – بگرامی",              en: "District 18 – Bagrami" },
  { key: "KBL_D19",                 fa: "ناحیه ۱۹",                       en: "District 19" },
  { key: "KBL_D20",                 fa: "ناحیه ۲۰",                       en: "District 20" },
  { key: "KBL_D21",                 fa: "ناحیه ۲۱",                       en: "District 21" },
  { key: "KBL_D22",                 fa: "ناحیه ۲۲",                       en: "District 22" },
  { key: "KBL_Shahr_e_Naw",         fa: "شهرنو",                          en: "Shahr-e Naw" },
  { key: "KBL_Wazir_Akbar_Khan",    fa: "وزیراکبرخان",                    en: "Wazir Akbar Khan" },
  { key: "KBL_Shirpur",             fa: "شیرپور",                         en: "Shirpur" },
  { key: "KBL_Bibi_Mahro",          fa: "بی‌بی مهرو",                     en: "Bibi Mahro" },
  { key: "KBL_Taimani",             fa: "تایمنی",                         en: "Taimani" },
  { key: "KBL_Qala_e_Fathullah",    fa: "قلعه فتح‌الله",                  en: "Qala-e Fathullah" },
  { key: "KBL_Karte_Seh",           fa: "کارته سه",                       en: "Karte Seh" },
  { key: "KBL_Karte_Char",          fa: "کارته چهار",                     en: "Karte Char" },
  { key: "KBL_Karte_Naw",           fa: "کارته نو",                       en: "Karte Naw" },
  { key: "KBL_Karte_Parwan",        fa: "کارته پروان",                    en: "Karte Parwan" },
  { key: "KBL_Karte_Mamurin",       fa: "کارته مامورین",                  en: "Karte Mamurin" },
  { key: "KBL_Dehbori",             fa: "دهبوری",                         en: "Dehbori" },
  { key: "KBL_Kote_Sangi",          fa: "کوته سنگی",                      en: "Kote Sangi" },
  { key: "KBL_Deh_Mazang",          fa: "دهمزنگ",                         en: "Deh Mazang" },
  { key: "KBL_Pul_e_Surkh",         fa: "پل سرخ",                         en: "Pul-e Surkh" },
  { key: "KBL_Silo",                fa: "سیلو",                           en: "Silo" },
  { key: "KBL_Darulaman",           fa: "دارالامان",                      en: "Darulaman" },
  { key: "KBL_Chihil_Sutun",        fa: "چهلستون",                        en: "Chihil Sutun" },
  { key: "KBL_Makroryan",           fa: "مکروریان",                       en: "Makroryan" },
  { key: "KBL_Wazirabad",           fa: "وزیرآباد",                       en: "Wazirabad" },
  { key: "KBL_Khair_Khana",         fa: "خیرخانه",                        en: "Khair Khana" },
  { key: "KBL_Khair_Khana_1",       fa: "حصه اول خیرخانه",                en: "Khair Khana Part 1" },
  { key: "KBL_Khair_Khana_2",       fa: "حصه دوم خیرخانه",                en: "Khair Khana Part 2" },
  { key: "KBL_Qala_e_Wahed",        fa: "قلعه واحد",                      en: "Qala-e Wahed" },
  { key: "KBL_Khwaja_Bughra",       fa: "خواجه بغرا",                     en: "Khwaja Bughra" },
  { key: "KBL_Company",             fa: "کمپنی",                          en: "Company" },
  { key: "KBL_Dasht_e_Barchi",      fa: "دشت برچی",                       en: "Dasht-e Barchi" },
  { key: "KBL_Afshar",              fa: "افشار",                          en: "Afshar" },
  { key: "KBL_Pul_e_Khoshk",        fa: "پل خشک",                         en: "Pul-e Khoshk" },
  { key: "KBL_Bagrami",             fa: "بگرامی",                         en: "Bagrami" },
  { key: "KBL_Pul_e_Charkhi",       fa: "پل چرخی",                        en: "Pul-e Charkhi" },
  { key: "KBL_Ahmad_Shah_Baba",     fa: "احمد شاه بابا مینه",             en: "Ahmad Shah Baba Mina" },
  { key: "KBL_Rahman_Mina",         fa: "رحمان مینه",                     en: "Rahman Mina" },
  { key: "KBL_Khoshhal_Khan",       fa: "خوشحال خان مینه",                en: "Khoshhal Khan Mina" },
  { key: "KBL_Arzan_Qimat",         fa: "ارزان قیمت",                     en: "Arzan Qimat" },
  { key: "KBL_Shah_Shahid",         fa: "شاه شهید",                       en: "Shah Shahid" },
  { key: "KBL_Sarai_Ghazni",        fa: "سرای غزنی",                      en: "Sarai Ghazni" },
  { key: "KBL_Murad_Khani",         fa: "مراد خانی",                      en: "Murad Khani" },
  { key: "KBL_Bagh_e_Bala",         fa: "باغ بالا",                       en: "Bagh-e Bala" },
  { key: "KBL_Alauddin",            fa: "علاءالدین",                      en: "Alauddin" },
  { key: "KBL_Qala_e_Musa",         fa: "قلعه موسی",                      en: "Qala-e Musa" },
  { key: "KBL_Tapa_e_Salam",        fa: "تپه سلام",                       en: "Tapa-e Salam" },
];
const KEYS = AREAS.map((a) => a.key);
const KEY_SET = new Set(KEYS);

/** Same Arabic-script folding as lib/categories, for the same reasons. */

/**
 * What a district looked like before the keys carried their city.
 *
 * Every stored district in production today is one of these, so resolving them
 * is not a nicety: without it, the moment this file ships every existing salon
 * has an unrecognised district and drops out of every neighbourhood filter.
 * The backfill rewrites the documents; this keeps them working until it has.
 */
const LEGACY_KEYS = new Map(
  AREAS.filter((a) => a.key.startsWith("KBL_")).map((a) => [a.key.slice(4), a.key])
);

function normalize(input) {
  return String(input == null ? "" : input)
    .toLowerCase()
    .replace(/[\u200c\u200d\u064b-\u0652\u0640]/g, "")
    .replace(/[\u0623\u0625\u0622]/g, "\u0627")
    .replace(/[\u064a\u0649]/g, "\u06cc")
    .replace(/\u0643/g, "\u06a9")
    .replace(/\u0629/g, "\u0647")
    .replace(/[\u06f0-\u06f9]/g, (d) => String(d.charCodeAt(0) - 0x06f0))
    .replace(/[\u0660-\u0669]/g, (d) => String(d.charCodeAt(0) - 0x0660))
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim();
}

/**
 * Resolve a stored district to a canonical key.
 *
 * Returns { key } when certain, or { candidates } when a free-text value could
 * plausibly be more than one area, or {} when nothing matches. Ambiguity is
 * reported rather than resolved: the live value "\u062e\u06cc\u0631\u062e\u0627\u0646\u0647 \u0645\u06cc\u0646\u0647 \u0646\u0627\u062d\u06cc\u0647 17" contains both a named
 * area and a district number, and picking one would silently move a salon on
 * the map for whoever is searching by neighbourhood.
 */
function normalizeDistrict(stored) {
  const raw = String(stored == null ? "" : stored).trim();
  if (!raw) return {};
  if (KEY_SET.has(raw)) return { key: raw };
  // A pre-prefix key, which is what every salon in production still holds.
  if (LEGACY_KEYS.has(raw)) return { key: LEGACY_KEYS.get(raw) };

  const s = normalize(raw);
  if (!s) return {};

  const hits = new Set();
  for (const a of AREAS) {
    for (const label of [a.fa, a.en, a.key.replace(/_/g, " ")]) {
      const n = normalize(label);
      if (n && (s === n || s.includes(n))) hits.add(a.key);
    }
  }

  const candidates = [...hits];
  if (candidates.length === 1) return { key: candidates[0] };
  if (candidates.length > 1) return { candidates };
  return {};
}

module.exports = { AREAS, KEYS, CITIES, cityOf, LEGACY_KEYS, normalize, normalizeDistrict };
