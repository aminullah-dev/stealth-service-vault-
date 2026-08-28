/**
 * The cities SafeBeauty covers, and the areas inside them.
 *
 * Mirrored from Areas.kt, which is the single source of truth;
 * test/areas.test.js re-extracts the Kotlin file and fails if the two drift.
 *
 * Every area key carries its city — KBL_D1_OldCity, HRT_D01 — because Herat's
 * first district is also numbered one. A ناحیه and a محله are different levels
 * of one address, so `kind` separates them, and `parent` records the pairing
 * only where it is sourced: Herat's twelve come from Herat Municipality's own
 * reports, Kabul's forty-two have none recorded and are left empty rather than
 * guessed.
 *
 * A salon's `district` is supposed to be a [key] — never a display label — so
 * that changing a translation cannot break stored data. In practice some older
 * salons hold free text, and all of them hold the pre-prefix keys, which is why
 * normalizeDistrict exists and why LEGACY_KEYS is consulted before giving up.
 */

const CITIES = [
  { key: "KABUL",     fa: "کابل",      en: "Kabul",          live: true },
  { key: "HERAT",     fa: "هرات",      en: "Herat",          live: true },
  { key: "MAZAR",     fa: "مزار شریف", en: "Mazar-e Sharif", live: true },
  { key: "JALALABAD", fa: "جلال‌آباد", en: "Jalalabad",      live: true },
];

const CITY_BY_PREFIX = { KBL: "KABUL", HRT: "HERAT", MZR: "MAZAR", JAL: "JALALABAD" };

/** The city an area key belongs to, read off its prefix. */
function cityOf(areaKey) {
  return CITY_BY_PREFIX[String(areaKey || "").split("_")[0]] || "";
}

const AREAS = [
  { key: "KBL_D1_OldCity",          fa: "ناحیه ۱ – شهر کهنه",             en: "District 1 – Old City",               kind: "DISTRICT" },
  { key: "KBL_D2_ShahreNaw",        fa: "ناحیه ۲ – شهرنو",                en: "District 2 – Shahr-e Naw",            kind: "DISTRICT" },
  { key: "KBL_D3_KarteChar",        fa: "ناحیه ۳ – کارته چهار",           en: "District 3 – Karte Char",             kind: "DISTRICT" },
  { key: "KBL_D4_KoteSangi",        fa: "ناحیه ۴ – کوته سنگی",            en: "District 4 – Kote Sangi",             kind: "DISTRICT" },
  { key: "KBL_D5_Company",          fa: "ناحیه ۵ – کمپنی",                en: "District 5 – Company",                kind: "DISTRICT" },
  { key: "KBL_D6_Darulaman",        fa: "ناحیه ۶ – دارالامان",            en: "District 6 – Darulaman",              kind: "DISTRICT" },
  { key: "KBL_D7_ChihilSutun",      fa: "ناحیه ۷ – چهلستون",              en: "District 7 – Chihil Sutun",           kind: "DISTRICT" },
  { key: "KBL_D8_KarteNaw",         fa: "ناحیه ۸ – کارته نو",             en: "District 8 – Karte Naw",              kind: "DISTRICT" },
  { key: "KBL_D9_Makroryan",        fa: "ناحیه ۹ – مکروریان",             en: "District 9 – Makroryan",              kind: "DISTRICT" },
  { key: "KBL_D10_WazirAkbarKhan",  fa: "ناحیه ۱۰ – وزیراکبرخان",         en: "District 10 – Wazir Akbar Khan",      kind: "DISTRICT" },
  { key: "KBL_D11_KhairKhana",      fa: "ناحیه ۱۱ – خیرخانه",             en: "District 11 – Khair Khana",           kind: "DISTRICT" },
  { key: "KBL_D12_AhmadShahBaba",   fa: "ناحیه ۱۲ – احمد شاه بابا مینه",  en: "District 12 – Ahmad Shah Baba Mina",  kind: "DISTRICT" },
  { key: "KBL_D13_DashteBarchi",    fa: "ناحیه ۱۳ – دشت برچی",            en: "District 13 – Dasht-e Barchi",        kind: "DISTRICT" },
  { key: "KBL_D14_RahmanMina",      fa: "ناحیه ۱۴ – رحمان مینه",          en: "District 14 – Rahman Mina",           kind: "DISTRICT" },
  { key: "KBL_D15_KhwajaBughra",    fa: "ناحیه ۱۵ – خواجه بغرا",          en: "District 15 – Khwaja Bughra",         kind: "DISTRICT" },
  { key: "KBL_D16",                 fa: "ناحیه ۱۶",                       en: "District 16",                         kind: "DISTRICT" },
  { key: "KBL_D17",                 fa: "ناحیه ۱۷",                       en: "District 17",                         kind: "DISTRICT" },
  { key: "KBL_D18_Bagrami",         fa: "ناحیه ۱۸ – بگرامی",              en: "District 18 – Bagrami",               kind: "DISTRICT" },
  { key: "KBL_D19",                 fa: "ناحیه ۱۹",                       en: "District 19",                         kind: "DISTRICT" },
  { key: "KBL_D20",                 fa: "ناحیه ۲۰",                       en: "District 20",                         kind: "DISTRICT" },
  { key: "KBL_D21",                 fa: "ناحیه ۲۱",                       en: "District 21",                         kind: "DISTRICT" },
  { key: "KBL_D22",                 fa: "ناحیه ۲۲",                       en: "District 22",                         kind: "DISTRICT" },
  { key: "KBL_Shahr_e_Naw",         fa: "شهرنو",                          en: "Shahr-e Naw",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Wazir_Akbar_Khan",    fa: "وزیراکبرخان",                    en: "Wazir Akbar Khan",                    kind: "NEIGHBOURHOOD" },
  { key: "KBL_Shirpur",             fa: "شیرپور",                         en: "Shirpur",                             kind: "NEIGHBOURHOOD" },
  { key: "KBL_Bibi_Mahro",          fa: "بی‌بی مهرو",                     en: "Bibi Mahro",                          kind: "NEIGHBOURHOOD" },
  { key: "KBL_Taimani",             fa: "تایمنی",                         en: "Taimani",                             kind: "NEIGHBOURHOOD" },
  { key: "KBL_Qala_e_Fathullah",    fa: "قلعه فتح‌الله",                  en: "Qala-e Fathullah",                    kind: "NEIGHBOURHOOD" },
  { key: "KBL_Karte_Seh",           fa: "کارته سه",                       en: "Karte Seh",                           kind: "NEIGHBOURHOOD" },
  { key: "KBL_Karte_Char",          fa: "کارته چهار",                     en: "Karte Char",                          kind: "NEIGHBOURHOOD" },
  { key: "KBL_Karte_Naw",           fa: "کارته نو",                       en: "Karte Naw",                           kind: "NEIGHBOURHOOD" },
  { key: "KBL_Karte_Parwan",        fa: "کارته پروان",                    en: "Karte Parwan",                        kind: "NEIGHBOURHOOD" },
  { key: "KBL_Karte_Mamurin",       fa: "کارته مامورین",                  en: "Karte Mamurin",                       kind: "NEIGHBOURHOOD" },
  { key: "KBL_Dehbori",             fa: "دهبوری",                         en: "Dehbori",                             kind: "NEIGHBOURHOOD" },
  { key: "KBL_Kote_Sangi",          fa: "کوته سنگی",                      en: "Kote Sangi",                          kind: "NEIGHBOURHOOD" },
  { key: "KBL_Deh_Mazang",          fa: "دهمزنگ",                         en: "Deh Mazang",                          kind: "NEIGHBOURHOOD" },
  { key: "KBL_Pul_e_Surkh",         fa: "پل سرخ",                         en: "Pul-e Surkh",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Silo",                fa: "سیلو",                           en: "Silo",                                kind: "NEIGHBOURHOOD" },
  { key: "KBL_Darulaman",           fa: "دارالامان",                      en: "Darulaman",                           kind: "NEIGHBOURHOOD" },
  { key: "KBL_Chihil_Sutun",        fa: "چهلستون",                        en: "Chihil Sutun",                        kind: "NEIGHBOURHOOD" },
  { key: "KBL_Makroryan",           fa: "مکروریان",                       en: "Makroryan",                           kind: "NEIGHBOURHOOD" },
  { key: "KBL_Wazirabad",           fa: "وزیرآباد",                       en: "Wazirabad",                           kind: "NEIGHBOURHOOD" },
  { key: "KBL_Khair_Khana",         fa: "خیرخانه",                        en: "Khair Khana",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Khair_Khana_1",       fa: "حصه اول خیرخانه",                en: "Khair Khana Part 1",                  kind: "NEIGHBOURHOOD" },
  { key: "KBL_Khair_Khana_2",       fa: "حصه دوم خیرخانه",                en: "Khair Khana Part 2",                  kind: "NEIGHBOURHOOD" },
  { key: "KBL_Qala_e_Wahed",        fa: "قلعه واحد",                      en: "Qala-e Wahed",                        kind: "NEIGHBOURHOOD" },
  { key: "KBL_Khwaja_Bughra",       fa: "خواجه بغرا",                     en: "Khwaja Bughra",                       kind: "NEIGHBOURHOOD" },
  { key: "KBL_Company",             fa: "کمپنی",                          en: "Company",                             kind: "NEIGHBOURHOOD" },
  { key: "KBL_Dasht_e_Barchi",      fa: "دشت برچی",                       en: "Dasht-e Barchi",                      kind: "NEIGHBOURHOOD" },
  { key: "KBL_Afshar",              fa: "افشار",                          en: "Afshar",                              kind: "NEIGHBOURHOOD" },
  { key: "KBL_Pul_e_Khoshk",        fa: "پل خشک",                         en: "Pul-e Khoshk",                        kind: "NEIGHBOURHOOD" },
  { key: "KBL_Bagrami",             fa: "بگرامی",                         en: "Bagrami",                             kind: "NEIGHBOURHOOD" },
  { key: "KBL_Pul_e_Charkhi",       fa: "پل چرخی",                        en: "Pul-e Charkhi",                       kind: "NEIGHBOURHOOD" },
  { key: "KBL_Ahmad_Shah_Baba",     fa: "احمد شاه بابا مینه",             en: "Ahmad Shah Baba Mina",                kind: "NEIGHBOURHOOD" },
  { key: "KBL_Rahman_Mina",         fa: "رحمان مینه",                     en: "Rahman Mina",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Khoshhal_Khan",       fa: "خوشحال خان مینه",                en: "Khoshhal Khan Mina",                  kind: "NEIGHBOURHOOD" },
  { key: "KBL_Arzan_Qimat",         fa: "ارزان قیمت",                     en: "Arzan Qimat",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Shah_Shahid",         fa: "شاه شهید",                       en: "Shah Shahid",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Sarai_Ghazni",        fa: "سرای غزنی",                      en: "Sarai Ghazni",                        kind: "NEIGHBOURHOOD" },
  { key: "KBL_Murad_Khani",         fa: "مراد خانی",                      en: "Murad Khani",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Bagh_e_Bala",         fa: "باغ بالا",                       en: "Bagh-e Bala",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Alauddin",            fa: "علاءالدین",                      en: "Alauddin",                            kind: "NEIGHBOURHOOD" },
  { key: "KBL_Qala_e_Musa",         fa: "قلعه موسی",                      en: "Qala-e Musa",                         kind: "NEIGHBOURHOOD" },
  { key: "KBL_Tapa_e_Salam",        fa: "تپه سلام",                       en: "Tapa-e Salam",                        kind: "NEIGHBOURHOOD" },
  { key: "HRT_D01",                 fa: "ناحیه اول",                      en: "District 1",                          kind: "DISTRICT" },
  { key: "HRT_D02",                 fa: "ناحیه دوم",                      en: "District 2",                          kind: "DISTRICT" },
  { key: "HRT_D03",                 fa: "ناحیه سوم",                      en: "District 3",                          kind: "DISTRICT" },
  { key: "HRT_D04",                 fa: "ناحیه چهارم",                    en: "District 4",                          kind: "DISTRICT" },
  { key: "HRT_D05",                 fa: "ناحیه پنجم",                     en: "District 5",                          kind: "DISTRICT" },
  { key: "HRT_D06",                 fa: "ناحیه ششم",                      en: "District 6",                          kind: "DISTRICT" },
  { key: "HRT_D07",                 fa: "ناحیه هفتم",                     en: "District 7",                          kind: "DISTRICT" },
  { key: "HRT_D08",                 fa: "ناحیه هشتم",                     en: "District 8",                          kind: "DISTRICT" },
  { key: "HRT_D09",                 fa: "ناحیه نهم",                      en: "District 9",                          kind: "DISTRICT" },
  { key: "HRT_D10",                 fa: "ناحیه دهم",                      en: "District 10",                         kind: "DISTRICT" },
  { key: "HRT_D11",                 fa: "ناحیه یازدهم",                   en: "District 11",                         kind: "DISTRICT" },
  { key: "HRT_D12",                 fa: "ناحیه دوازدهم",                  en: "District 12",                         kind: "DISTRICT" },
  { key: "HRT_D13",                 fa: "ناحیه سیزدهم",                   en: "District 13",                         kind: "DISTRICT" },
  { key: "HRT_D14",                 fa: "ناحیه چهاردهم",                  en: "District 14",                         kind: "DISTRICT" },
  { key: "HRT_D15",                 fa: "ناحیه پانزدهم",                  en: "District 15",                         kind: "DISTRICT" },
  { key: "HRT_BaghMurad",           fa: "باغ مراد",                       en: "Bagh-e Murad",                        kind: "NEIGHBOURHOOD", parent: "HRT_D01" },
  { key: "HRT_SiMetra",             fa: "سی متره",                        en: "Si Metra",                            kind: "NEIGHBOURHOOD", parent: "HRT_D01" },
  { key: "HRT_BaghZaghan",          fa: "باغ زاغان",                      en: "Bagh-e Zaghan",                       kind: "NEIGHBOURHOOD", parent: "HRT_D09" },
  { key: "HRT_Arabha",              fa: "عرب‌ها",                         en: "Arab-ha",                             kind: "NEIGHBOURHOOD", parent: "HRT_D09" },
  { key: "HRT_Chaharsuk",           fa: "چهارسونک",                       en: "Chaharsuk",                           kind: "NEIGHBOURHOOD", parent: "HRT_D09" },
  { key: "HRT_Mohebzada",           fa: "محب‌زاده",                       en: "Mohebzada",                           kind: "NEIGHBOURHOOD", parent: "HRT_D09" },
  { key: "HRT_RafaBaradaran",       fa: "رفا و برادران",                  en: "Rafa wa Baradaran",                   kind: "NEIGHBOURHOOD", parent: "HRT_D09" },
  { key: "HRT_Arbabzadaha",         fa: "ارباب‌زاده‌ها",                  en: "Arbabzada-ha",                        kind: "NEIGHBOURHOOD", parent: "HRT_D09" },
  { key: "HRT_Firozabad",           fa: "فیروزآباد",                      en: "Firozabad",                           kind: "NEIGHBOURHOOD", parent: "HRT_D10" },
  { key: "HRT_Karabad",             fa: "کارآباد",                        en: "Karabad",                             kind: "NEIGHBOURHOOD", parent: "HRT_D10" },
  { key: "HRT_PayanAb",             fa: "پایان آب",                       en: "Payan-e Ab",                          kind: "NEIGHBOURHOOD", parent: "HRT_D10" },
  { key: "HRT_Shaidayi",            fa: "شیدایی",                         en: "Shaidayi",                            kind: "NEIGHBOURHOOD", parent: "HRT_D15" },
  { key: "JAL_D01",                 fa: "ناحیه اول",                      en: "District 1",                          kind: "DISTRICT" },
  { key: "JAL_D02",                 fa: "ناحیه دوم",                      en: "District 2",                          kind: "DISTRICT" },
  { key: "JAL_D03",                 fa: "ناحیه سوم",                      en: "District 3",                          kind: "DISTRICT" },
  { key: "JAL_D04",                 fa: "ناحیه چهارم",                    en: "District 4",                          kind: "DISTRICT" },
  { key: "JAL_D05",                 fa: "ناحیه پنجم",                     en: "District 5",                          kind: "DISTRICT" },
  { key: "JAL_D06",                 fa: "ناحیه ششم",                      en: "District 6",                          kind: "DISTRICT" },
  { key: "JAL_D07",                 fa: "ناحیه هفتم",                     en: "District 7",                          kind: "DISTRICT" },
  { key: "JAL_D08",                 fa: "ناحیه هشتم",                     en: "District 8",                          kind: "DISTRICT" },
  { key: "JAL_D09",                 fa: "ناحیه نهم",                      en: "District 9",                          kind: "DISTRICT" },
  { key: "MZR_D01",                 fa: "ناحیه اول",                      en: "District 1",                          kind: "DISTRICT" },
  { key: "MZR_D02",                 fa: "ناحیه دوم",                      en: "District 2",                          kind: "DISTRICT" },
  { key: "MZR_D03",                 fa: "ناحیه سوم",                      en: "District 3",                          kind: "DISTRICT" },
  { key: "MZR_D04",                 fa: "ناحیه چهارم",                    en: "District 4",                          kind: "DISTRICT" },
  { key: "MZR_D05",                 fa: "ناحیه پنجم",                     en: "District 5",                          kind: "DISTRICT" },
  { key: "MZR_D06",                 fa: "ناحیه ششم",                      en: "District 6",                          kind: "DISTRICT" },
  { key: "MZR_D07",                 fa: "ناحیه هفتم",                     en: "District 7",                          kind: "DISTRICT" },
  { key: "MZR_D08",                 fa: "ناحیه هشتم",                     en: "District 8",                          kind: "DISTRICT" },
  { key: "MZR_D09",                 fa: "ناحیه نهم",                      en: "District 9",                          kind: "DISTRICT" },
  { key: "MZR_D10",                 fa: "ناحیه دهم",                      en: "District 10",                         kind: "DISTRICT" },
  { key: "MZR_D11",                 fa: "ناحیه یازدهم",                   en: "District 11",                         kind: "DISTRICT" },
  { key: "MZR_D12",                 fa: "ناحیه دوازدهم",                  en: "District 12",                         kind: "DISTRICT" },
  { key: "MZR_GuzarQarghan",        fa: "گذر قرغان",                      en: "Guzar-e Qarghan",                     kind: "GUZAR", parent: "MZR_D02" },
  { key: "MZR_GuzarSeDukan",        fa: "گذر سه‌دکان",                    en: "Guzar-e Se Dukan",                    kind: "GUZAR", parent: "MZR_D03" },
  { key: "MZR_JoyAjar",             fa: "جوی آجر",                        en: "Joy-e Ajar",                          kind: "NEIGHBOURHOOD", parent: "MZR_D04" },
  { key: "MZR_Faqirabad",           fa: "فقیرآباد",                       en: "Faqirabad",                           kind: "NEIGHBOURHOOD", parent: "MZR_D05" },
  { key: "MZR_GuzarHayat",          fa: "گذر حیات",                       en: "Guzar-e Hayat",                       kind: "GUZAR", parent: "MZR_D06" },
  { key: "MZR_GuzarTokhta",         fa: "گذر توخته",                      en: "Guzar-e Tokhta",                      kind: "GUZAR", parent: "MZR_D07" },
  { key: "MZR_DashtShor",           fa: "دشت شور",                        en: "Dasht-e Shor",                        kind: "NEIGHBOURHOOD", parent: "MZR_D08" },
  { key: "MZR_KhalidBinWalid",      fa: "پروژه خالد بن ولید",             en: "Khalid bin Walid Project",            kind: "NEIGHBOURHOOD", parent: "MZR_D08" },
  { key: "MZR_GuzarSadeqiya",       fa: "گذر صادقیه",                     en: "Guzar-e Sadeqiya",                    kind: "GUZAR", parent: "MZR_D10" },
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
