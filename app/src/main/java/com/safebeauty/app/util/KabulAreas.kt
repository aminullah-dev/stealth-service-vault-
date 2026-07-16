package com.safebeauty.app.util

import com.safebeauty.app.ui.theme.AppLanguage

/**
 * Canonical list of Kabul areas — the 22 municipal districts (نواحی) plus the
 * well-known neighborhoods (محله‌ها) people actually search by. This is the
 * single source of truth shared by BOTH the customer neighborhood filter and
 * the provider's salon "district" picker, so a salon's stored `district` (which
 * is always an area [key]) is guaranteed to match a filter option.
 *
 * Place names are proper nouns: the Dari and Pashto forms are written the same
 * (same script), so one Persian-script label serves both; English uses a
 * transliteration. The stable ASCII [key] is what gets stored in Firestore and
 * compared for filtering — never the display label — so changing a translation
 * never breaks existing data.
 */
object KabulAreas {

    data class Area(val key: String, val fa: String, val en: String)

    /** Ordered so the most-used central areas surface first in the dropdown. */
    val areas: List<Area> = listOf(
        // ── Districts 1–13 (with their best-known area) ─────────────────────────
        Area("D1_OldCity",        "ناحیه ۱ – شهر کهنه",            "District 1 – Old City"),
        Area("D2_ShahreNaw",      "ناحیه ۲ – شهرنو",              "District 2 – Shahr-e Naw"),
        Area("D3_KarteChar",      "ناحیه ۳ – کارته چهار",          "District 3 – Karte Char"),
        Area("D4_KoteSangi",      "ناحیه ۴ – کوته سنگی",           "District 4 – Kote Sangi"),
        Area("D5_Company",        "ناحیه ۵ – کمپنی",              "District 5 – Company"),
        Area("D6_Darulaman",      "ناحیه ۶ – دارالامان",           "District 6 – Darulaman"),
        Area("D7_ChihilSutun",    "ناحیه ۷ – چهلستون",            "District 7 – Chihil Sutun"),
        Area("D8_KarteNaw",       "ناحیه ۸ – کارته نو",            "District 8 – Karte Naw"),
        Area("D9_Makroryan",      "ناحیه ۹ – مکروریان",            "District 9 – Makroryan"),
        Area("D10_WazirAkbarKhan","ناحیه ۱۰ – وزیراکبرخان",        "District 10 – Wazir Akbar Khan"),
        Area("D11_KhairKhana",    "ناحیه ۱۱ – خیرخانه",            "District 11 – Khair Khana"),
        Area("D12_AhmadShahBaba",  "ناحیه ۱۲ – احمد شاه بابا مینه", "District 12 – Ahmad Shah Baba Mina"),
        Area("D13_DashteBarchi",  "ناحیه ۱۳ – دشت برچی",           "District 13 – Dasht-e Barchi"),
        // ── Districts 14–22 (by number; newer outer districts) ──────────────────
        Area("D14_RahmanMina",    "ناحیه ۱۴ – رحمان مینه",         "District 14 – Rahman Mina"),
        Area("D15_KhwajaBughra",  "ناحیه ۱۵ – خواجه بغرا",         "District 15 – Khwaja Bughra"),
        Area("D16",               "ناحیه ۱۶",                     "District 16"),
        Area("D17",               "ناحیه ۱۷",                     "District 17"),
        Area("D18_Bagrami",       "ناحیه ۱۸ – بگرامی",             "District 18 – Bagrami"),
        Area("D19",               "ناحیه ۱۹",                     "District 19"),
        Area("D20",               "ناحیه ۲۰",                     "District 20"),
        Area("D21",               "ناحیه ۲۱",                     "District 21"),
        Area("D22",               "ناحیه ۲۲",                     "District 22"),
        // ── Popular named neighborhoods (searchable areas) ──────────────────────
        Area("Shahr_e_Naw",       "شهرنو",                        "Shahr-e Naw"),
        Area("Wazir_Akbar_Khan",  "وزیراکبرخان",                  "Wazir Akbar Khan"),
        Area("Shirpur",           "شیرپور",                       "Shirpur"),
        Area("Bibi_Mahro",        "بی‌بی مهرو",                    "Bibi Mahro"),
        Area("Taimani",           "تایمنی",                       "Taimani"),
        Area("Qala_e_Fathullah",  "قلعه فتح‌الله",                 "Qala-e Fathullah"),
        Area("Karte_Seh",         "کارته سه",                     "Karte Seh"),
        Area("Karte_Char",        "کارته چهار",                   "Karte Char"),
        Area("Karte_Naw",         "کارته نو",                     "Karte Naw"),
        Area("Karte_Parwan",      "کارته پروان",                  "Karte Parwan"),
        Area("Karte_Mamurin",     "کارته مامورین",                "Karte Mamurin"),
        Area("Dehbori",           "دهبوری",                       "Dehbori"),
        Area("Kote_Sangi",        "کوته سنگی",                    "Kote Sangi"),
        Area("Deh_Mazang",        "دهمزنگ",                       "Deh Mazang"),
        Area("Pul_e_Surkh",       "پل سرخ",                       "Pul-e Surkh"),
        Area("Silo",              "سیلو",                         "Silo"),
        Area("Darulaman",         "دارالامان",                    "Darulaman"),
        Area("Chihil_Sutun",      "چهلستون",                      "Chihil Sutun"),
        Area("Makroryan",         "مکروریان",                     "Makroryan"),
        Area("Wazirabad",         "وزیرآباد",                     "Wazirabad"),
        Area("Khair_Khana",       "خیرخانه",                      "Khair Khana"),
        Area("Khair_Khana_1",     "حصه اول خیرخانه",              "Khair Khana Part 1"),
        Area("Khair_Khana_2",     "حصه دوم خیرخانه",              "Khair Khana Part 2"),
        Area("Qala_e_Wahed",      "قلعه واحد",                    "Qala-e Wahed"),
        Area("Khwaja_Bughra",     "خواجه بغرا",                   "Khwaja Bughra"),
        Area("Company",           "کمپنی",                        "Company"),
        Area("Dasht_e_Barchi",    "دشت برچی",                     "Dasht-e Barchi"),
        Area("Afshar",            "افشار",                        "Afshar"),
        Area("Pul_e_Khoshk",      "پل خشک",                       "Pul-e Khoshk"),
        Area("Bagrami",           "بگرامی",                       "Bagrami"),
        Area("Pul_e_Charkhi",     "پل چرخی",                      "Pul-e Charkhi"),
        Area("Ahmad_Shah_Baba",   "احمد شاه بابا مینه",           "Ahmad Shah Baba Mina"),
        Area("Rahman_Mina",       "رحمان مینه",                   "Rahman Mina"),
        Area("Khoshhal_Khan",     "خوشحال خان مینه",              "Khoshhal Khan Mina"),
        Area("Arzan_Qimat",       "ارزان قیمت",                   "Arzan Qimat"),
        Area("Shah_Shahid",       "شاه شهید",                     "Shah Shahid"),
        Area("Sarai_Ghazni",      "سرای غزنی",                    "Sarai Ghazni"),
        Area("Murad_Khani",       "مراد خانی",                    "Murad Khani"),
        Area("Bagh_e_Bala",       "باغ بالا",                     "Bagh-e Bala"),
        Area("Alauddin",          "علاءالدین",                    "Alauddin"),
        Area("Qala_e_Musa",       "قلعه موسی",                    "Qala-e Musa"),
        Area("Tapa_e_Salam",      "تپه سلام",                     "Tapa-e Salam"),
    )

    /** Stable keys only (parallel to [areas]); what gets stored/filtered. */
    val keys: List<String> = areas.map { it.key }

    /** Display label for one area in the given language. */
    fun labelFor(area: Area, lang: AppLanguage): String =
        if (lang == AppLanguage.ENGLISH) area.en else area.fa

    /** All area labels (no "All" option) in the given language, parallel to [keys]. */
    fun labels(lang: AppLanguage): List<String> = areas.map { labelFor(it, lang) }

    /** The display label for a stored [key], or the raw key if it isn't a known
     *  area (e.g. a legacy free-text district) so nothing ever renders blank. */
    fun labelForKey(key: String, lang: AppLanguage): String =
        areas.firstOrNull { it.key == key }?.let { labelFor(it, lang) } ?: key
}
