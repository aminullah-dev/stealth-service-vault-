package com.safebeauty.app.util

import com.safebeauty.app.ui.theme.AppLanguage

/**
 * The cities SafeBeauty covers, and the areas inside them.
 *
 * Kabul was the only city, so an area key was just "D1_OldCity" and nothing
 * recorded which city it belonged to. Herat's first district is also numbered
 * one, and so is Mazar-e Sharif's — an unqualified key in a Herat salon's
 * document would be a value nobody reading it later could interpret, and any
 * aggregation that grouped by area alone would silently add two cities
 * together. So every key now carries its city: KBL_D1_OldCity. Globally unique
 * by construction, which is a stronger thing than a rule everyone has to
 * remember to apply.
 *
 * A city is declared here before it opens. [City.live] is what gates it: a
 * planned city appears in this list, and in nothing a user can touch, until its
 * real districts are filled in below. Municipal divisions are facts about a
 * place, not something to approximate — a picker offering an invented
 * neighbourhood would put a salon at an address that does not exist.
 *
 * Place names are proper nouns: the Dari and Pashto forms are written the same
 * (same script), so one Persian-script label serves both; English uses a
 * transliteration. The stable ASCII [key] is what gets stored in Firestore and
 * compared for filtering — never the display label — so changing a translation
 * never breaks existing data.
 *
 * This file is the single source of truth. functions/lib/areas.js is a checked
 * copy, and test/areas.test.js fails if the two ever drift.
 */
object KabulAreas {

    data class City(
        val key: String,
        val fa: String,
        val en: String,
        /** False until the city's real districts are known. Nothing offers it. */
        val live: Boolean,
    )

    /** Every area belongs to exactly one city, named by [cityKey]. */
    data class Area(val key: String, val fa: String, val en: String) {
        val cityKey: String get() = CITY_BY_PREFIX[key.substringBefore('_')] ?: KABUL
    }

    const val KABUL = "KABUL"

    val cities: List<City> = listOf(
        City(KABUL,       "کابل",      "Kabul",          live = true),
        City("HERAT",     "هرات",      "Herat",          live = false),
        City("MAZAR",     "مزار شریف", "Mazar-e Sharif", live = false),
        City("JALALABAD", "جلال‌آباد", "Jalalabad",      live = false),
    )

    /** Key prefix → city key. The prefix is the first segment of an area key. */
    private val CITY_BY_PREFIX = mapOf(
        "KBL" to KABUL,
        "HRT" to "HERAT",
        "MZR" to "MAZAR",
        "JAA" to "JALALABAD",
    )

    val liveCities: List<City> = cities.filter { it.live }

    fun cityLabel(cityKey: String, lang: AppLanguage): String =
        cities.firstOrNull { it.key == cityKey }?.let { if (lang == AppLanguage.ENGLISH) it.en else it.fa }
            ?: cityKey

    /**
     * Kabul's 22 municipal districts (نواحی) plus the well-known neighbourhoods
     * (محله‌ها) people actually search by. Shared by the customer's
     * neighbourhood filter and the provider's district picker, so a salon's
     * stored district always matches a filter option.
     *
     * Herat, Mazar-e Sharif and Jalalabad go here as their districts are
     * confirmed, each prefixed HRT_, MZR_ and JAA_.
     */
    val areas: List<Area> = listOf(
        Area("KBL_D1_OldCity",          "ناحیه ۱ – شهر کهنه",             "District 1 – Old City"),
        Area("KBL_D2_ShahreNaw",        "ناحیه ۲ – شهرنو",                "District 2 – Shahr-e Naw"),
        Area("KBL_D3_KarteChar",        "ناحیه ۳ – کارته چهار",           "District 3 – Karte Char"),
        Area("KBL_D4_KoteSangi",        "ناحیه ۴ – کوته سنگی",            "District 4 – Kote Sangi"),
        Area("KBL_D5_Company",          "ناحیه ۵ – کمپنی",                "District 5 – Company"),
        Area("KBL_D6_Darulaman",        "ناحیه ۶ – دارالامان",            "District 6 – Darulaman"),
        Area("KBL_D7_ChihilSutun",      "ناحیه ۷ – چهلستون",              "District 7 – Chihil Sutun"),
        Area("KBL_D8_KarteNaw",         "ناحیه ۸ – کارته نو",             "District 8 – Karte Naw"),
        Area("KBL_D9_Makroryan",        "ناحیه ۹ – مکروریان",             "District 9 – Makroryan"),
        Area("KBL_D10_WazirAkbarKhan",  "ناحیه ۱۰ – وزیراکبرخان",         "District 10 – Wazir Akbar Khan"),
        Area("KBL_D11_KhairKhana",      "ناحیه ۱۱ – خیرخانه",             "District 11 – Khair Khana"),
        Area("KBL_D12_AhmadShahBaba",   "ناحیه ۱۲ – احمد شاه بابا مینه",  "District 12 – Ahmad Shah Baba Mina"),
        Area("KBL_D13_DashteBarchi",    "ناحیه ۱۳ – دشت برچی",            "District 13 – Dasht-e Barchi"),
        Area("KBL_D14_RahmanMina",      "ناحیه ۱۴ – رحمان مینه",          "District 14 – Rahman Mina"),
        Area("KBL_D15_KhwajaBughra",    "ناحیه ۱۵ – خواجه بغرا",          "District 15 – Khwaja Bughra"),
        Area("KBL_D16",                 "ناحیه ۱۶",                       "District 16"),
        Area("KBL_D17",                 "ناحیه ۱۷",                       "District 17"),
        Area("KBL_D18_Bagrami",         "ناحیه ۱۸ – بگرامی",              "District 18 – Bagrami"),
        Area("KBL_D19",                 "ناحیه ۱۹",                       "District 19"),
        Area("KBL_D20",                 "ناحیه ۲۰",                       "District 20"),
        Area("KBL_D21",                 "ناحیه ۲۱",                       "District 21"),
        Area("KBL_D22",                 "ناحیه ۲۲",                       "District 22"),
        Area("KBL_Shahr_e_Naw",         "شهرنو",                          "Shahr-e Naw"),
        Area("KBL_Wazir_Akbar_Khan",    "وزیراکبرخان",                    "Wazir Akbar Khan"),
        Area("KBL_Shirpur",             "شیرپور",                         "Shirpur"),
        Area("KBL_Bibi_Mahro",          "بی‌بی مهرو",                     "Bibi Mahro"),
        Area("KBL_Taimani",             "تایمنی",                         "Taimani"),
        Area("KBL_Qala_e_Fathullah",    "قلعه فتح‌الله",                  "Qala-e Fathullah"),
        Area("KBL_Karte_Seh",           "کارته سه",                       "Karte Seh"),
        Area("KBL_Karte_Char",          "کارته چهار",                     "Karte Char"),
        Area("KBL_Karte_Naw",           "کارته نو",                       "Karte Naw"),
        Area("KBL_Karte_Parwan",        "کارته پروان",                    "Karte Parwan"),
        Area("KBL_Karte_Mamurin",       "کارته مامورین",                  "Karte Mamurin"),
        Area("KBL_Dehbori",             "دهبوری",                         "Dehbori"),
        Area("KBL_Kote_Sangi",          "کوته سنگی",                      "Kote Sangi"),
        Area("KBL_Deh_Mazang",          "دهمزنگ",                         "Deh Mazang"),
        Area("KBL_Pul_e_Surkh",         "پل سرخ",                         "Pul-e Surkh"),
        Area("KBL_Silo",                "سیلو",                           "Silo"),
        Area("KBL_Darulaman",           "دارالامان",                      "Darulaman"),
        Area("KBL_Chihil_Sutun",        "چهلستون",                        "Chihil Sutun"),
        Area("KBL_Makroryan",           "مکروریان",                       "Makroryan"),
        Area("KBL_Wazirabad",           "وزیرآباد",                       "Wazirabad"),
        Area("KBL_Khair_Khana",         "خیرخانه",                        "Khair Khana"),
        Area("KBL_Khair_Khana_1",       "حصه اول خیرخانه",                "Khair Khana Part 1"),
        Area("KBL_Khair_Khana_2",       "حصه دوم خیرخانه",                "Khair Khana Part 2"),
        Area("KBL_Qala_e_Wahed",        "قلعه واحد",                      "Qala-e Wahed"),
        Area("KBL_Khwaja_Bughra",       "خواجه بغرا",                     "Khwaja Bughra"),
        Area("KBL_Company",             "کمپنی",                          "Company"),
        Area("KBL_Dasht_e_Barchi",      "دشت برچی",                       "Dasht-e Barchi"),
        Area("KBL_Afshar",              "افشار",                          "Afshar"),
        Area("KBL_Pul_e_Khoshk",        "پل خشک",                         "Pul-e Khoshk"),
        Area("KBL_Bagrami",             "بگرامی",                         "Bagrami"),
        Area("KBL_Pul_e_Charkhi",       "پل چرخی",                        "Pul-e Charkhi"),
        Area("KBL_Ahmad_Shah_Baba",     "احمد شاه بابا مینه",             "Ahmad Shah Baba Mina"),
        Area("KBL_Rahman_Mina",         "رحمان مینه",                     "Rahman Mina"),
        Area("KBL_Khoshhal_Khan",       "خوشحال خان مینه",                "Khoshhal Khan Mina"),
        Area("KBL_Arzan_Qimat",         "ارزان قیمت",                     "Arzan Qimat"),
        Area("KBL_Shah_Shahid",         "شاه شهید",                       "Shah Shahid"),
        Area("KBL_Sarai_Ghazni",        "سرای غزنی",                      "Sarai Ghazni"),
        Area("KBL_Murad_Khani",         "مراد خانی",                      "Murad Khani"),
        Area("KBL_Bagh_e_Bala",         "باغ بالا",                       "Bagh-e Bala"),
        Area("KBL_Alauddin",            "علاءالدین",                      "Alauddin"),
        Area("KBL_Qala_e_Musa",         "قلعه موسی",                      "Qala-e Musa"),
        Area("KBL_Tapa_e_Salam",        "تپه سلام",                       "Tapa-e Salam"),
    )

    /** Stable keys only (parallel to [areas]); what gets stored/filtered. */
    val keys: List<String> = areas.map { it.key }

    fun areasIn(cityKey: String): List<Area> = areas.filter { it.cityKey == cityKey }

    fun labelFor(area: Area, lang: AppLanguage): String =
        if (lang == AppLanguage.ENGLISH) area.en else area.fa

    fun labels(lang: AppLanguage): List<String> = areas.map { labelFor(it, lang) }

    fun labelForKey(key: String, lang: AppLanguage): String =
        areas.firstOrNull { it.key == key }?.let { labelFor(it, lang) } ?: key
}
