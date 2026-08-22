/**
 * The canonical Kabul areas, mirrored from the Android app.
 *
 * The single source of truth is KabulAreas.kt; this is a checked copy so the
 * server can validate and normalize a salon's stored district. test/areas.test.js
 * re-extracts the Kotlin file and fails if the two ever drift apart, which is
 * what stops a copy like this from quietly rotting.
 *
 * A salon's `district` is supposed to be a [key] — never a display label — so
 * that changing a translation cannot break stored data. In practice some older
 * salons hold free text, which is why normalizeDistrict exists.
 */

const AREAS = [
  { key: "D1_OldCity", fa: "ناحیه ۱ – شهر کهنه", en: "District 1 – Old City" },
  { key: "D2_ShahreNaw", fa: "ناحیه ۲ – شهرنو", en: "District 2 – Shahr-e Naw" },
  { key: "D3_KarteChar", fa: "ناحیه ۳ – کارته چهار", en: "District 3 – Karte Char" },
  { key: "D4_KoteSangi", fa: "ناحیه ۴ – کوته سنگی", en: "District 4 – Kote Sangi" },
  { key: "D5_Company", fa: "ناحیه ۵ – کمپنی", en: "District 5 – Company" },
  { key: "D6_Darulaman", fa: "ناحیه ۶ – دارالامان", en: "District 6 – Darulaman" },
  { key: "D7_ChihilSutun", fa: "ناحیه ۷ – چهلستون", en: "District 7 – Chihil Sutun" },
  { key: "D8_KarteNaw", fa: "ناحیه ۸ – کارته نو", en: "District 8 – Karte Naw" },
  { key: "D9_Makroryan", fa: "ناحیه ۹ – مکروریان", en: "District 9 – Makroryan" },
  { key: "D10_WazirAkbarKhan", fa: "ناحیه ۱۰ – وزیراکبرخان", en: "District 10 – Wazir Akbar Khan" },
  { key: "D11_KhairKhana", fa: "ناحیه ۱۱ – خیرخانه", en: "District 11 – Khair Khana" },
  { key: "D12_AhmadShahBaba", fa: "ناحیه ۱۲ – احمد شاه بابا مینه", en: "District 12 – Ahmad Shah Baba Mina" },
  { key: "D13_DashteBarchi", fa: "ناحیه ۱۳ – دشت برچی", en: "District 13 – Dasht-e Barchi" },
  { key: "D14_RahmanMina", fa: "ناحیه ۱۴ – رحمان مینه", en: "District 14 – Rahman Mina" },
  { key: "D15_KhwajaBughra", fa: "ناحیه ۱۵ – خواجه بغرا", en: "District 15 – Khwaja Bughra" },
  { key: "D16", fa: "ناحیه ۱۶", en: "District 16" },
  { key: "D17", fa: "ناحیه ۱۷", en: "District 17" },
  { key: "D18_Bagrami", fa: "ناحیه ۱۸ – بگرامی", en: "District 18 – Bagrami" },
  { key: "D19", fa: "ناحیه ۱۹", en: "District 19" },
  { key: "D20", fa: "ناحیه ۲۰", en: "District 20" },
  { key: "D21", fa: "ناحیه ۲۱", en: "District 21" },
  { key: "D22", fa: "ناحیه ۲۲", en: "District 22" },
  { key: "Shahr_e_Naw", fa: "شهرنو", en: "Shahr-e Naw" },
  { key: "Wazir_Akbar_Khan", fa: "وزیراکبرخان", en: "Wazir Akbar Khan" },
  { key: "Shirpur", fa: "شیرپور", en: "Shirpur" },
  { key: "Bibi_Mahro", fa: "بی‌بی مهرو", en: "Bibi Mahro" },
  { key: "Taimani", fa: "تایمنی", en: "Taimani" },
  { key: "Qala_e_Fathullah", fa: "قلعه فتح‌الله", en: "Qala-e Fathullah" },
  { key: "Karte_Seh", fa: "کارته سه", en: "Karte Seh" },
  { key: "Karte_Char", fa: "کارته چهار", en: "Karte Char" },
  { key: "Karte_Naw", fa: "کارته نو", en: "Karte Naw" },
  { key: "Karte_Parwan", fa: "کارته پروان", en: "Karte Parwan" },
  { key: "Karte_Mamurin", fa: "کارته مامورین", en: "Karte Mamurin" },
  { key: "Dehbori", fa: "دهبوری", en: "Dehbori" },
  { key: "Kote_Sangi", fa: "کوته سنگی", en: "Kote Sangi" },
  { key: "Deh_Mazang", fa: "دهمزنگ", en: "Deh Mazang" },
  { key: "Pul_e_Surkh", fa: "پل سرخ", en: "Pul-e Surkh" },
  { key: "Silo", fa: "سیلو", en: "Silo" },
  { key: "Darulaman", fa: "دارالامان", en: "Darulaman" },
  { key: "Chihil_Sutun", fa: "چهلستون", en: "Chihil Sutun" },
  { key: "Makroryan", fa: "مکروریان", en: "Makroryan" },
  { key: "Wazirabad", fa: "وزیرآباد", en: "Wazirabad" },
  { key: "Khair_Khana", fa: "خیرخانه", en: "Khair Khana" },
  { key: "Khair_Khana_1", fa: "حصه اول خیرخانه", en: "Khair Khana Part 1" },
  { key: "Khair_Khana_2", fa: "حصه دوم خیرخانه", en: "Khair Khana Part 2" },
  { key: "Qala_e_Wahed", fa: "قلعه واحد", en: "Qala-e Wahed" },
  { key: "Khwaja_Bughra", fa: "خواجه بغرا", en: "Khwaja Bughra" },
  { key: "Company", fa: "کمپنی", en: "Company" },
  { key: "Dasht_e_Barchi", fa: "دشت برچی", en: "Dasht-e Barchi" },
  { key: "Afshar", fa: "افشار", en: "Afshar" },
  { key: "Pul_e_Khoshk", fa: "پل خشک", en: "Pul-e Khoshk" },
  { key: "Bagrami", fa: "بگرامی", en: "Bagrami" },
  { key: "Pul_e_Charkhi", fa: "پل چرخی", en: "Pul-e Charkhi" },
  { key: "Ahmad_Shah_Baba", fa: "احمد شاه بابا مینه", en: "Ahmad Shah Baba Mina" },
  { key: "Rahman_Mina", fa: "رحمان مینه", en: "Rahman Mina" },
  { key: "Khoshhal_Khan", fa: "خوشحال خان مینه", en: "Khoshhal Khan Mina" },
  { key: "Arzan_Qimat", fa: "ارزان قیمت", en: "Arzan Qimat" },
  { key: "Shah_Shahid", fa: "شاه شهید", en: "Shah Shahid" },
  { key: "Sarai_Ghazni", fa: "سرای غزنی", en: "Sarai Ghazni" },
  { key: "Murad_Khani", fa: "مراد خانی", en: "Murad Khani" },
  { key: "Bagh_e_Bala", fa: "باغ بالا", en: "Bagh-e Bala" },
  { key: "Alauddin", fa: "علاءالدین", en: "Alauddin" },
  { key: "Qala_e_Musa", fa: "قلعه موسی", en: "Qala-e Musa" },
  { key: "Tapa_e_Salam", fa: "تپه سلام", en: "Tapa-e Salam" },
];

const KEYS = AREAS.map((a) => a.key);
const KEY_SET = new Set(KEYS);

/** Same Arabic-script folding as lib/categories, for the same reasons. */
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

module.exports = { AREAS, KEYS, normalize, normalizeDistrict };
