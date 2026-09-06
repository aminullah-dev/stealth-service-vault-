#!/usr/bin/env python3
"""Generate the iOS area list from Android's Areas.kt.

Areas.kt says it is the single source of truth and names one checked copy,
functions/lib/areas.js, kept honest by functions/test/areas.test.js. iOS was a
third copy that did not exist at all: an iPhone customer saw the raw key
"KBL_Shirpur" on a salon card where Android shows «شیرپور», and a salon owner
registering from an iPhone typed her district as free text into a field the
server stores verbatim — so her salon matched no district filter on either
platform.

Transcribing 122 areas by hand would drift on the first change, so this
regenerates them, the way scripts/gen-palettes.py regenerates the brand
palettes. AreasParityTests fails if this has not been re-run.

    python3 scripts/gen-areas.py
"""
import re, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
KT = ROOT / "app/src/main/java/com/safebeauty/app/util/Areas.kt"
OUT = ROOT / "ios/SafeBeautyCore/Sources/SafeBeautyCore/Areas.swift"

src = KT.read_text()

# The same pattern functions/test/areas.test.js uses to check the server copy.
# Deliberately identical: three copies parsed three ways is three chances to
# disagree about what Areas.kt says.
AREA_RE = re.compile(
    r'Area\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*'
    r'(?:,\s*(DISTRICT|GUZAR|NEIGHBOURHOOD)\s*)?(?:,\s*"([^"]*)"\s*)?\)'
)
CITY_RE = re.compile(
    r'City\(\s*(?:"([^"]+)"|(\w+))\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*live\s*=\s*(true|false)\s*,?\s*\)'
)
PREFIX_RE = re.compile(r'"(\w+)"\s+to\s+(?:"(\w+)"|(\w+))')

areas = [
    (m[0], m[1], m[2], m[3] or "NEIGHBOURHOOD", m[4] or "")
    for m in AREA_RE.findall(src)
]

kabul = re.search(r'const val KABUL = "(\w+)"', src)
if not kabul:
    sys.exit("KABUL constant not found — has Areas.kt changed shape?")
KABUL = kabul.group(1)


def city_const(name):
    """`KABUL` in the Kotlin is a const; every other city is a literal."""
    return KABUL if name == "KABUL" else name


cities = [
    (m[0] or city_const(m[1]), m[2], m[3], m[4] == "true")
    for m in CITY_RE.findall(src)
]

prefix_block = re.search(r"CITY_BY_PREFIX = mapOf\((.*?)\n\s*\)", src, re.S)
if not prefix_block:
    sys.exit("CITY_BY_PREFIX not found — has Areas.kt changed shape?")
prefixes = [
    (m[0], m[1] or city_const(m[2])) for m in PREFIX_RE.findall(prefix_block.group(1))
]

if not areas or not cities or not prefixes:
    sys.exit(
        f"parsed {len(areas)} areas, {len(cities)} cities, {len(prefixes)} prefixes "
        "— at least one is empty, so Areas.kt's shape has changed"
    )

# Invisible formatting characters — the ZWNJ inside «جلال‌آباد» is the one that
# actually occurs — become escapes. Left raw they are a zero-width difference
# nobody reviewing this file can see, and a copy-paste silently drops them.
INVISIBLE = re.compile(r"[​-‏‪-‮⁦-⁩﻿]")


def lit(s):
    return '"' + INVISIBLE.sub(lambda m: "\\u{%04X}" % ord(m.group()), s) + '"'


out = ['''import Foundation

// GENERATED from app/src/main/java/com/safebeauty/app/util/Areas.kt by
// scripts/gen-areas.py. Do not hand-edit — regenerate instead.
//
// Areas.kt is the single source of truth; this is its third checked copy, after
// functions/lib/areas.js. AreasParityTests fails if the two drift, the way
// functions/test/areas.test.js does for the server copy.
//
// Why it has to exist at all: the stable ASCII key is what Firestore stores and
// what filtering compares, and it is never what a customer should read. iOS was
// printing the key.

/// The levels of an Afghan city address, which are not interchangeable.
///
/// ناحیه is the municipal district. گذر is the formal unit below it. محله is
/// the informal name people actually use. A form that offers a district and a
/// گذر side by side, as if they were alternatives, is how a salon ends up filed
/// at a level nobody searches.
public enum AreaKind: String, Sendable {
    case district = "DISTRICT"
    case guzar = "GUZAR"
    case neighbourhood = "NEIGHBOURHOOD"
}

public struct Area: Identifiable, Hashable, Sendable {
    public let key: String
    public let fa: String
    public let en: String
    public let kind: AreaKind
    /// The district this sits in, "" where the pairing is not sourced.
    public let parent: String

    public var id: String { key }
    public var cityKey: String { Areas.cityOf(key) }

    /// Place names are proper nouns: Dari and Pashto are written in the same
    /// script, so one Persian-script label serves both.
    public func label(english: Bool) -> String { english ? en : fa }
}

public struct City: Identifiable, Hashable, Sendable {
    public let key: String
    public let fa: String
    public let en: String
    /// False until the city's real districts are known. Nothing offers it.
    public let live: Bool

    public var id: String { key }
    public func label(english: Bool) -> String { english ? en : fa }
}

public enum Areas {''']

out.append(f"    public static let kabul = {lit(KABUL)}\n")

out.append("    public static let cities: [City] = [")
for key, fa, en, live in cities:
    out.append(
        f"        City(key: {lit(key)}, fa: {lit(fa)}, en: {lit(en)}, live: {str(live).lower()}),"
    )
out.append("    ]\n")

out.append("    /// Key prefix → city key. The prefix is the first segment of an area key.")
out.append("    private static let cityByPrefix: [String: String] = [")
for prefix, city in prefixes:
    out.append(f"        {lit(prefix)}: {lit(city)},")
out.append("    ]\n")

out.append("""    /// The city an area key belongs to, read off its prefix.
    ///
    /// Herat's first district is numbered one too. An unqualified key would be a
    /// value nobody reading it later could interpret, and any aggregation
    /// grouping by area alone would add two cities together.
    public static func cityOf(_ areaKey: String) -> String {
        // `split(separator:)` drops empty segments, so it would read "_KBL_x"
        // as Kabul where Kotlin's substringBefore and the server's split("_")[0]
        // both read it as no city. omittingEmptySubsequences keeps the three
        // copies answering the same way for every input, not just the real ones.
        let prefix = areaKey.split(separator: "_", omittingEmptySubsequences: false).first
        return cityByPrefix[prefix.map(String.init) ?? ""] ?? ""
    }

    public static let liveCities: [City] = cities.filter(\\.live)

    public static func cityLabel(_ cityKey: String, english: Bool) -> String {
        cities.first { $0.key == cityKey }?.label(english: english) ?? cityKey
    }
""")

out.append("""    /// Kabul's 22 municipal districts and the neighbourhoods people search by,
    /// then Herat's, Mazar's and Jalalabad's.
    ///
    /// Herat's rural districts (ولسوالی‌ها) are deliberately absent: they are
    /// not part of the city, and listing them beside its neighbourhoods would
    /// offer a salon an address in the wrong administrative unit.""")
out.append("    public static let areas: [Area] = [")
for key, fa, en, kind, parent in areas:
    swift_kind = {
        "DISTRICT": ".district",
        "GUZAR": ".guzar",
        "NEIGHBOURHOOD": ".neighbourhood",
    }[kind]
    out.append(
        f"        Area(key: {lit(key)}, fa: {lit(fa)}, en: {lit(en)}, "
        f"kind: {swift_kind}, parent: {lit(parent)}),"
    )
out.append("    ]\n")

out.append('''    /// Stable keys only (parallel to `areas`); what gets stored and filtered.
    public static let keys: [String] = areas.map(\\.key)

    public static func areasIn(_ cityKey: String) -> [Area] {
        areas.filter { $0.cityKey == cityKey }
    }

    public static func districtsIn(_ cityKey: String) -> [Area] {
        areasIn(cityKey).filter { $0.kind == .district }
    }

    /// Neighbourhoods known to sit in `districtKey`. Empty where none are
    /// sourced — Kabul's 42 have no parent recorded and are left empty rather
    /// than guessed, because putting a salon in a district it is not in would be
    /// a wrong answer no one could see.
    public static func neighbourhoodsIn(_ districtKey: String) -> [Area] {
        areas.filter { $0.kind != .district && $0.parent == districtKey }
    }

    /// True when `key` is a ناحیه — the level a salon's districtKey holds.
    public static func isDistrict(_ key: String) -> Bool {
        areas.first { $0.key == key }?.kind == .district
    }

    /// Every area a customer can filter by in `cityKey`: its ناحیه‌ها, and under
    /// each, the گذرها and محله‌ها recorded inside it.
    ///
    /// Ordered district-then-its-children so the list reads as an address rather
    /// than as two alphabetical lists stapled together.
    public static func filterableIn(_ cityKey: String) -> [Area] {
        districtsIn(cityKey).flatMap { [$0] + neighbourhoodsIn($0.key) }
    }

    /// Only the گذرها of `districtKey` — the formal unit, not the colloquial one.
    public static func guzarsIn(_ districtKey: String) -> [Area] {
        areas.filter { $0.kind == .guzar && $0.parent == districtKey }
    }

    /// Every salon in production stored its district before the keys carried a
    /// city, so "D9_Makroryan" is what is on the document and "KBL_D9_Makroryan"
    /// is what this list holds. The server resolves these through LEGACY_KEYS;
    /// without the same map here the customer reads the raw key on the card.
    /// Kabul only — it was the only city when those rows were written.
    private static let legacyKeys: [String: String] = Dictionary(
        uniqueKeysWithValues: areas
            .filter { $0.key.hasPrefix("KBL_") }
            .map { (String($0.key.dropFirst("KBL_".count)), $0.key) }
    )

    /// What `key` is called in this list, for a value that may be a pre-prefix
    /// key or free text the salon typed.
    ///
    /// The server's normalizeDistrict has a third branch that fuzzy-matches free
    /// text; that one stays server-side, where it runs once at write time rather
    /// than on every row of every list.
    public static func canonicalKey(_ key: String) -> String {
        areas.contains { $0.key == key } ? key : (legacyKeys[key] ?? key)
    }

    /// The label for `key`, or `key` itself when it is not one.
    ///
    /// Falling back to the key is deliberate: older salons hold free text like
    /// "خیرخانه مینه ناحیه ۱۷", and showing what the salon actually wrote is
    /// better than showing nothing.
    public static func labelForKey(_ key: String, english: Bool) -> String {
        areas.first { $0.key == canonicalKey(key) }?.label(english: english) ?? key
    }
}''')

OUT.write_text("\n".join(out) + "\n")
print(f"{OUT.relative_to(ROOT)}: {len(cities)} cities, {len(areas)} areas, {len(prefixes)} prefixes")
