package com.safebeauty.app.util

import com.safebeauty.app.ui.theme.AppLanguage

/**
 * The cities SafeBeauty covers, and the areas inside them.
 *
 * Kabul was the only city, so an area key was just "D1_OldCity" and nothing
 * recorded which city it belonged to. Herat's first district is also numbered
 * one — an unqualified key in a Herat salon's document would be a value nobody
 * reading it later could interpret, and any aggregation grouping by area alone
 * would add two cities together. Every key now carries its city: KBL_D1_OldCity,
 * HRT_D01. Unique by construction, which outlasts a rule everyone must remember.
 *
 * A ناحیه and a محله are different levels of the same address, and this list
 * used to flatten them: Kabul's 22 municipal districts sat beside 42
 * neighbourhoods with nothing marking which was which. [AreaKind] separates
 * them, so a form can ask for a district and then narrow to a neighbourhood
 * inside it rather than offering both at once as if they were alternatives.
 *
 * [Area.parent] is filled in only where the pairing is sourced. Herat's twelve
 * come from Herat Municipality's own reports. Kabul's forty-two have no parent
 * recorded, and are left empty rather than guessed — putting a salon in a
 * district it is not in would be a wrong answer no one could see.
 *
 * A city is declared here before it opens. [City.live] gates it: nothing offers
 * a planned city until its real districts are known. Municipal divisions are
 * facts about a place, not something to approximate.
 *
 * The authority on WHERE a salon is remains its coordinates
 * (SalonDocument.latitude/longitude). District and neighbourhood are how people
 * search, not where the salon is.
 *
 * Place names are proper nouns: the Dari and Pashto forms are written the same
 * (same script), so one Persian-script label serves both; English uses a
 * transliteration. The stable ASCII [key] is what gets stored in Firestore and
 * compared for filtering — never the display label — so changing a translation
 * never breaks existing data.
 *
 * This file is the single source of truth. functions/lib/areas.js is a checked
 * copy, and test/areas.test.js fails if the two drift.
 */
object Areas {

    /**
     * The levels of an Afghan city address, which are not interchangeable.
     *
     * ناحیه is the municipal district. گذر is the formal unit below it. محله is
     * the informal name people actually use, and does not always line up with
     * either. Flattening them lets a form offer a district and a guzar side by
     * side as if they were alternatives, which is how a salon ends up filed at
     * a level nobody searches.
     */
    enum class AreaKind { DISTRICT, GUZAR, NEIGHBOURHOOD }

    data class City(
        val key: String,
        val fa: String,
        val en: String,
        /** False until the city's real districts are known. Nothing offers it. */
        val live: Boolean,
    )

    data class Area(
        val key: String,
        val fa: String,
        val en: String,
        val kind: AreaKind = AreaKind.NEIGHBOURHOOD,
        /** The district this neighbourhood sits in, "" when not sourced. */
        val parent: String = "",
    ) {
        val cityKey: String get() = CITY_BY_PREFIX[key.substringBefore('_')] ?: ""
    }

    const val KABUL = "KABUL"

    val cities: List<City> = listOf(
        City(KABUL,       "کابل",      "Kabul",          live = true),
        City("HERAT",     "هرات",      "Herat",          live = true),
        City("MAZAR",     "مزار شریف", "Mazar-e Sharif", live = true),
        City("JALALABAD", "جلال‌آباد", "Jalalabad",      live = true),
    )

    /** Key prefix → city key. The prefix is the first segment of an area key. */
    private val CITY_BY_PREFIX = mapOf(
        "KBL" to KABUL,
        "HRT" to "HERAT",
        "MZR" to "MAZAR",
        "JAL" to "JALALABAD",
    )

    val liveCities: List<City> = cities.filter { it.live }

    fun cityLabel(cityKey: String, lang: AppLanguage): String =
        cities.firstOrNull { it.key == cityKey }?.let { if (lang == AppLanguage.ENGLISH) it.en else it.fa }
            ?: cityKey

    private val DISTRICT = AreaKind.DISTRICT
    private val GUZAR = AreaKind.GUZAR
    private val NEIGHBOURHOOD = AreaKind.NEIGHBOURHOOD

    /**
     * Kabul's 22 municipal districts (نواحی) and the neighbourhoods (محله‌ها)
     * people search by, then Herat's 15 districts and the twelve neighbourhoods
     * Herat Municipality names in its own reports.
     *
     * Herat's rural districts (ولسوالی‌ها) — انجیل، گذره، زنده‌جان، غوریان —
     * are deliberately absent: they are not part of the city, and listing them
     * beside its neighbourhoods would offer a salon an address in the wrong
     * administrative unit.
     */
    val areas: List<Area> = listOf(
        Area("KBL_D1_OldCity",          "ناحیه ۱ – شهر کهنه",             "District 1 – Old City"               , DISTRICT),
        Area("KBL_D2_ShahreNaw",        "ناحیه ۲ – شهرنو",                "District 2 – Shahr-e Naw"            , DISTRICT),
        Area("KBL_D3_KarteChar",        "ناحیه ۳ – کارته چهار",           "District 3 – Karte Char"             , DISTRICT),
        Area("KBL_D4_KoteSangi",        "ناحیه ۴ – کوته سنگی",            "District 4 – Kote Sangi"             , DISTRICT),
        Area("KBL_D5_Company",          "ناحیه ۵ – کمپنی",                "District 5 – Company"                , DISTRICT),
        Area("KBL_D6_Darulaman",        "ناحیه ۶ – دارالامان",            "District 6 – Darulaman"              , DISTRICT),
        Area("KBL_D7_ChihilSutun",      "ناحیه ۷ – چهلستون",              "District 7 – Chihil Sutun"           , DISTRICT),
        Area("KBL_D8_KarteNaw",         "ناحیه ۸ – کارته نو",             "District 8 – Karte Naw"              , DISTRICT),
        Area("KBL_D9_Makroryan",        "ناحیه ۹ – مکروریان",             "District 9 – Makroryan"              , DISTRICT),
        Area("KBL_D10_WazirAkbarKhan",  "ناحیه ۱۰ – وزیراکبرخان",         "District 10 – Wazir Akbar Khan"      , DISTRICT),
        Area("KBL_D11_KhairKhana",      "ناحیه ۱۱ – خیرخانه",             "District 11 – Khair Khana"           , DISTRICT),
        Area("KBL_D12_AhmadShahBaba",   "ناحیه ۱۲ – احمد شاه بابا مینه",  "District 12 – Ahmad Shah Baba Mina"  , DISTRICT),
        Area("KBL_D13_DashteBarchi",    "ناحیه ۱۳ – دشت برچی",            "District 13 – Dasht-e Barchi"        , DISTRICT),
        Area("KBL_D14_RahmanMina",      "ناحیه ۱۴ – رحمان مینه",          "District 14 – Rahman Mina"           , DISTRICT),
        Area("KBL_D15_KhwajaBughra",    "ناحیه ۱۵ – خواجه بغرا",          "District 15 – Khwaja Bughra"         , DISTRICT),
        Area("KBL_D16",                 "ناحیه ۱۶",                       "District 16"                         , DISTRICT),
        Area("KBL_D17",                 "ناحیه ۱۷",                       "District 17"                         , DISTRICT),
        Area("KBL_D18_Bagrami",         "ناحیه ۱۸ – بگرامی",              "District 18 – Bagrami"               , DISTRICT),
        Area("KBL_D19",                 "ناحیه ۱۹",                       "District 19"                         , DISTRICT),
        Area("KBL_D20",                 "ناحیه ۲۰",                       "District 20"                         , DISTRICT),
        Area("KBL_D21",                 "ناحیه ۲۱",                       "District 21"                         , DISTRICT),
        Area("KBL_D22",                 "ناحیه ۲۲",                       "District 22"                         , DISTRICT),
        Area("KBL_Shahr_e_Naw",         "شهرنو",                          "Shahr-e Naw"                         , NEIGHBOURHOOD),
        Area("KBL_Wazir_Akbar_Khan",    "وزیراکبرخان",                    "Wazir Akbar Khan"                    , NEIGHBOURHOOD),
        Area("KBL_Shirpur",             "شیرپور",                         "Shirpur"                             , NEIGHBOURHOOD),
        Area("KBL_Bibi_Mahro",          "بی‌بی مهرو",                     "Bibi Mahro"                          , NEIGHBOURHOOD),
        Area("KBL_Taimani",             "تایمنی",                         "Taimani"                             , NEIGHBOURHOOD),
        Area("KBL_Qala_e_Fathullah",    "قلعه فتح‌الله",                  "Qala-e Fathullah"                    , NEIGHBOURHOOD),
        Area("KBL_Karte_Seh",           "کارته سه",                       "Karte Seh"                           , NEIGHBOURHOOD),
        Area("KBL_Karte_Char",          "کارته چهار",                     "Karte Char"                          , NEIGHBOURHOOD),
        Area("KBL_Karte_Naw",           "کارته نو",                       "Karte Naw"                           , NEIGHBOURHOOD),
        Area("KBL_Karte_Parwan",        "کارته پروان",                    "Karte Parwan"                        , NEIGHBOURHOOD),
        Area("KBL_Karte_Mamurin",       "کارته مامورین",                  "Karte Mamurin"                       , NEIGHBOURHOOD),
        Area("KBL_Dehbori",             "دهبوری",                         "Dehbori"                             , NEIGHBOURHOOD),
        Area("KBL_Kote_Sangi",          "کوته سنگی",                      "Kote Sangi"                          , NEIGHBOURHOOD),
        Area("KBL_Deh_Mazang",          "دهمزنگ",                         "Deh Mazang"                          , NEIGHBOURHOOD),
        Area("KBL_Pul_e_Surkh",         "پل سرخ",                         "Pul-e Surkh"                         , NEIGHBOURHOOD),
        Area("KBL_Silo",                "سیلو",                           "Silo"                                , NEIGHBOURHOOD),
        Area("KBL_Darulaman",           "دارالامان",                      "Darulaman"                           , NEIGHBOURHOOD),
        Area("KBL_Chihil_Sutun",        "چهلستون",                        "Chihil Sutun"                        , NEIGHBOURHOOD),
        Area("KBL_Makroryan",           "مکروریان",                       "Makroryan"                           , NEIGHBOURHOOD),
        Area("KBL_Wazirabad",           "وزیرآباد",                       "Wazirabad"                           , NEIGHBOURHOOD),
        Area("KBL_Khair_Khana",         "خیرخانه",                        "Khair Khana"                         , NEIGHBOURHOOD),
        Area("KBL_Khair_Khana_1",       "حصه اول خیرخانه",                "Khair Khana Part 1"                  , NEIGHBOURHOOD),
        Area("KBL_Khair_Khana_2",       "حصه دوم خیرخانه",                "Khair Khana Part 2"                  , NEIGHBOURHOOD),
        Area("KBL_Qala_e_Wahed",        "قلعه واحد",                      "Qala-e Wahed"                        , NEIGHBOURHOOD),
        Area("KBL_Khwaja_Bughra",       "خواجه بغرا",                     "Khwaja Bughra"                       , NEIGHBOURHOOD),
        Area("KBL_Company",             "کمپنی",                          "Company"                             , NEIGHBOURHOOD),
        Area("KBL_Dasht_e_Barchi",      "دشت برچی",                       "Dasht-e Barchi"                      , NEIGHBOURHOOD),
        Area("KBL_Afshar",              "افشار",                          "Afshar"                              , NEIGHBOURHOOD),
        Area("KBL_Pul_e_Khoshk",        "پل خشک",                         "Pul-e Khoshk"                        , NEIGHBOURHOOD),
        Area("KBL_Bagrami",             "بگرامی",                         "Bagrami"                             , NEIGHBOURHOOD),
        Area("KBL_Pul_e_Charkhi",       "پل چرخی",                        "Pul-e Charkhi"                       , NEIGHBOURHOOD),
        Area("KBL_Ahmad_Shah_Baba",     "احمد شاه بابا مینه",             "Ahmad Shah Baba Mina"                , NEIGHBOURHOOD),
        Area("KBL_Rahman_Mina",         "رحمان مینه",                     "Rahman Mina"                         , NEIGHBOURHOOD),
        Area("KBL_Khoshhal_Khan",       "خوشحال خان مینه",                "Khoshhal Khan Mina"                  , NEIGHBOURHOOD),
        Area("KBL_Arzan_Qimat",         "ارزان قیمت",                     "Arzan Qimat"                         , NEIGHBOURHOOD),
        Area("KBL_Shah_Shahid",         "شاه شهید",                       "Shah Shahid"                         , NEIGHBOURHOOD),
        Area("KBL_Sarai_Ghazni",        "سرای غزنی",                      "Sarai Ghazni"                        , NEIGHBOURHOOD),
        Area("KBL_Murad_Khani",         "مراد خانی",                      "Murad Khani"                         , NEIGHBOURHOOD),
        Area("KBL_Bagh_e_Bala",         "باغ بالا",                       "Bagh-e Bala"                         , NEIGHBOURHOOD),
        Area("KBL_Alauddin",            "علاءالدین",                      "Alauddin"                            , NEIGHBOURHOOD),
        Area("KBL_Qala_e_Musa",         "قلعه موسی",                      "Qala-e Musa"                         , NEIGHBOURHOOD),
        Area("KBL_Tapa_e_Salam",        "تپه سلام",                       "Tapa-e Salam"                        , NEIGHBOURHOOD),
        Area("HRT_D01",                 "ناحیه اول",                      "District 1"                          , DISTRICT),
        Area("HRT_D02",                 "ناحیه دوم",                      "District 2"                          , DISTRICT),
        Area("HRT_D03",                 "ناحیه سوم",                      "District 3"                          , DISTRICT),
        Area("HRT_D04",                 "ناحیه چهارم",                    "District 4"                          , DISTRICT),
        Area("HRT_D05",                 "ناحیه پنجم",                     "District 5"                          , DISTRICT),
        Area("HRT_D06",                 "ناحیه ششم",                      "District 6"                          , DISTRICT),
        Area("HRT_D07",                 "ناحیه هفتم",                     "District 7"                          , DISTRICT),
        Area("HRT_D08",                 "ناحیه هشتم",                     "District 8"                          , DISTRICT),
        Area("HRT_D09",                 "ناحیه نهم",                      "District 9"                          , DISTRICT),
        Area("HRT_D10",                 "ناحیه دهم",                      "District 10"                         , DISTRICT),
        Area("HRT_D11",                 "ناحیه یازدهم",                   "District 11"                         , DISTRICT),
        Area("HRT_D12",                 "ناحیه دوازدهم",                  "District 12"                         , DISTRICT),
        Area("HRT_D13",                 "ناحیه سیزدهم",                   "District 13"                         , DISTRICT),
        Area("HRT_D14",                 "ناحیه چهاردهم",                  "District 14"                         , DISTRICT),
        Area("HRT_D15",                 "ناحیه پانزدهم",                  "District 15"                         , DISTRICT),
        Area("HRT_BaghMurad",           "باغ مراد",                       "Bagh-e Murad"                        , NEIGHBOURHOOD, "HRT_D01"),
        Area("HRT_SiMetra",             "سی متره",                        "Si Metra"                            , NEIGHBOURHOOD, "HRT_D01"),
        Area("HRT_BaghZaghan",          "باغ زاغان",                      "Bagh-e Zaghan"                       , NEIGHBOURHOOD, "HRT_D09"),
        Area("HRT_Arabha",              "عرب‌ها",                         "Arab-ha"                             , NEIGHBOURHOOD, "HRT_D09"),
        Area("HRT_Chaharsuk",           "چهارسونک",                       "Chaharsuk"                           , NEIGHBOURHOOD, "HRT_D09"),
        Area("HRT_Mohebzada",           "محب‌زاده",                       "Mohebzada"                           , NEIGHBOURHOOD, "HRT_D09"),
        Area("HRT_RafaBaradaran",       "رفا و برادران",                  "Rafa wa Baradaran"                   , NEIGHBOURHOOD, "HRT_D09"),
        Area("HRT_Arbabzadaha",         "ارباب‌زاده‌ها",                  "Arbabzada-ha"                        , NEIGHBOURHOOD, "HRT_D09"),
        Area("HRT_Firozabad",           "فیروزآباد",                      "Firozabad"                           , NEIGHBOURHOOD, "HRT_D10"),
        Area("HRT_Karabad",             "کارآباد",                        "Karabad"                             , NEIGHBOURHOOD, "HRT_D10"),
        Area("HRT_PayanAb",             "پایان آب",                       "Payan-e Ab"                          , NEIGHBOURHOOD, "HRT_D10"),
        Area("HRT_Shaidayi",            "شیدایی",                         "Shaidayi"                            , NEIGHBOURHOOD, "HRT_D15"),
        Area("JAL_D01",                 "ناحیه اول",                      "District 1"                          , DISTRICT),
        Area("JAL_D02",                 "ناحیه دوم",                      "District 2"                          , DISTRICT),
        Area("JAL_D03",                 "ناحیه سوم",                      "District 3"                          , DISTRICT),
        Area("JAL_D04",                 "ناحیه چهارم",                    "District 4"                          , DISTRICT),
        Area("JAL_D05",                 "ناحیه پنجم",                     "District 5"                          , DISTRICT),
        Area("JAL_D06",                 "ناحیه ششم",                      "District 6"                          , DISTRICT),
        Area("JAL_D07",                 "ناحیه هفتم",                     "District 7"                          , DISTRICT),
        Area("JAL_D08",                 "ناحیه هشتم",                     "District 8"                          , DISTRICT),
        Area("JAL_D09",                 "ناحیه نهم",                      "District 9"                          , DISTRICT),
        Area("MZR_D01",                 "ناحیه اول",                      "District 1"                          , DISTRICT),
        Area("MZR_D02",                 "ناحیه دوم",                      "District 2"                          , DISTRICT),
        Area("MZR_D03",                 "ناحیه سوم",                      "District 3"                          , DISTRICT),
        Area("MZR_D04",                 "ناحیه چهارم",                    "District 4"                          , DISTRICT),
        Area("MZR_D05",                 "ناحیه پنجم",                     "District 5"                          , DISTRICT),
        Area("MZR_D06",                 "ناحیه ششم",                      "District 6"                          , DISTRICT),
        Area("MZR_D07",                 "ناحیه هفتم",                     "District 7"                          , DISTRICT),
        Area("MZR_D08",                 "ناحیه هشتم",                     "District 8"                          , DISTRICT),
        Area("MZR_D09",                 "ناحیه نهم",                      "District 9"                          , DISTRICT),
        Area("MZR_D10",                 "ناحیه دهم",                      "District 10"                         , DISTRICT),
        Area("MZR_D11",                 "ناحیه یازدهم",                   "District 11"                         , DISTRICT),
        Area("MZR_D12",                 "ناحیه دوازدهم",                  "District 12"                         , DISTRICT),
        Area("MZR_GuzarQarghan",        "گذر قرغان",                      "Guzar-e Qarghan"                     , GUZAR, "MZR_D02"),
        Area("MZR_GuzarSeDukan",        "گذر سه‌دکان",                    "Guzar-e Se Dukan"                    , GUZAR, "MZR_D03"),
        Area("MZR_JoyAjar",             "جوی آجر",                        "Joy-e Ajar"                          , NEIGHBOURHOOD, "MZR_D04"),
        Area("MZR_Faqirabad",           "فقیرآباد",                       "Faqirabad"                           , NEIGHBOURHOOD, "MZR_D05"),
        Area("MZR_GuzarHayat",          "گذر حیات",                       "Guzar-e Hayat"                       , GUZAR, "MZR_D06"),
        Area("MZR_GuzarTokhta",         "گذر توخته",                      "Guzar-e Tokhta"                      , GUZAR, "MZR_D07"),
        Area("MZR_DashtShor",           "دشت شور",                        "Dasht-e Shor"                        , NEIGHBOURHOOD, "MZR_D08"),
        Area("MZR_KhalidBinWalid",      "پروژه خالد بن ولید",             "Khalid bin Walid Project"            , NEIGHBOURHOOD, "MZR_D08"),
        Area("MZR_GuzarSadeqiya",       "گذر صادقیه",                     "Guzar-e Sadeqiya"                    , GUZAR, "MZR_D10"),
    )

    /** Stable keys only (parallel to [areas]); what gets stored/filtered. */
    val keys: List<String> = areas.map { it.key }

    fun areasIn(cityKey: String): List<Area> = areas.filter { it.cityKey == cityKey }

    fun districtsIn(cityKey: String): List<Area> =
        areasIn(cityKey).filter { it.kind == DISTRICT }

    /** Neighbourhoods known to sit in [districtKey]. Empty where none are sourced. */
    fun neighbourhoodsIn(districtKey: String): List<Area> =
        areas.filter { it.kind != DISTRICT && it.parent == districtKey }

    /** Only the گذرها of [districtKey] — the formal unit, not the colloquial one. */
    fun guzarsIn(districtKey: String): List<Area> =
        areas.filter { it.kind == GUZAR && it.parent == districtKey }

    fun labelFor(area: Area, lang: AppLanguage): String =
        if (lang == AppLanguage.ENGLISH) area.en else area.fa

    fun labels(lang: AppLanguage): List<String> = areas.map { labelFor(it, lang) }

    fun labelForKey(key: String, lang: AppLanguage): String =
        areas.firstOrNull { it.key == key }?.let { labelFor(it, lang) } ?: key
}
