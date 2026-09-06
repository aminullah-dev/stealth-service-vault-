import Testing
import Foundation
@testable import SafeBeautyCore

/// Areas.swift is a copy, and a copy nobody checks is a copy that rots.
///
/// Areas.kt says it is the single source of truth and names one checked copy,
/// functions/lib/areas.js, which functions/test/areas.test.js holds to it. This
/// is the same guard for the third copy: if the app gains an area and iOS does
/// not, salons in it are unfilterable on iPhone; if a key is renamed on one side
/// only, stored districts stop resolving and the customer reads a raw key.
///
/// The fix when this fails is `python3 scripts/gen-areas.py`, never an edit here
/// or in Areas.swift.
@Suite("Areas.swift still matches Areas.kt")
struct AreasParityTests {

    /// Walk up from this file to the repository root. The package sits at
    /// ios/SafeBeautyCore, so the Kotlin is three levels above Sources.
    private static var kotlin: String? {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // SafeBeautyCoreTests
            .deletingLastPathComponent()   // Tests
            .deletingLastPathComponent()   // SafeBeautyCore
            .deletingLastPathComponent()   // ios
            .deletingLastPathComponent()   // repo root
        return try? String(
            contentsOf: root.appending(path: "app/src/main/java/com/safebeauty/app/util/Areas.kt"),
            encoding: .utf8
        )
    }

    /// The same pattern functions/test/areas.test.js uses. Deliberately
    /// identical: three copies parsed three ways is three chances to disagree
    /// about what Areas.kt says.
    private static let pattern = #"Area\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*(?:,\s*(DISTRICT|GUZAR|NEIGHBOURHOOD)\s*)?(?:,\s*"([^"]*)"\s*)?\)"#

    @Test("every area, in order, with the same kind and parent")
    func areasMatchKotlin() throws {
        let src = try #require(Self.kotlin, "could not read Areas.kt from the repository")
        let re = try NSRegularExpression(pattern: Self.pattern)
        let all = re.matches(in: src, range: NSRange(src.startIndex..., in: src))

        func group(_ m: NSTextCheckingResult, _ i: Int) -> String {
            guard let r = Range(m.range(at: i), in: src) else { return "" }
            return String(src[r])
        }

        let expected = all.map { m in
            Area(key: group(m, 1), fa: group(m, 2), en: group(m, 3),
                 kind: AreaKind(rawValue: group(m, 4)) ?? .neighbourhood,
                 parent: group(m, 5))
        }

        #expect(!expected.isEmpty, "extracted nothing from Areas.kt — has its shape changed?")
        #expect(Areas.areas == expected,
                "Areas.swift has drifted from Areas.kt — run python3 scripts/gen-areas.py")
    }

    @Test("keys are unique")
    func keysAreUnique() {
        #expect(Set(Areas.keys).count == Areas.keys.count)
    }

    @Test("every area key names the city it is in")
    func everyKeyHasACity() {
        // The whole reason for the prefix: Herat district 1 and Kabul district 1
        // must not be one key. Anything grouping by area alone is then safe by
        // construction rather than by remembering to add a filter.
        for k in Areas.keys {
            #expect(!Areas.cityOf(k).isEmpty, "\(k) has no city prefix")
        }
        #expect(Set(Areas.keys.map(Areas.cityOf)).sorted()
                == ["HERAT", "JALALABAD", "KABUL", "MAZAR"])
        #expect(Areas.liveCities.map(\.key) == ["KABUL", "HERAT", "MAZAR", "JALALABAD"])
    }

    @Test("a pre-prefix key still resolves, because production is full of them")
    func legacyKeysResolve() {
        // Every salon stored its district before the keys carried a city. If
        // these stopped resolving, every existing salon would show a raw key on
        // its card the day this shipped.
        #expect(Areas.labelForKey("D9_Makroryan", english: true) == "District 9 – Makroryan")
        #expect(Areas.labelForKey("Shirpur", english: true) == "Shirpur")
        #expect(Areas.labelForKey("KBL_Shirpur", english: false) == "شیرپور")
        // Herat's keys were born prefixed, so there is nothing legacy to map.
        #expect(Areas.labelForKey("HRT_D01", english: true) == "District 1")
    }

    @Test("free text a salon typed comes back unchanged rather than blank")
    func unknownKeyFallsBackToItself() {
        #expect(Areas.labelForKey("خیرخانه مینه ناحیه ۱۷", english: false) == "خیرخانه مینه ناحیه ۱۷")
        #expect(Areas.labelForKey("", english: false) == "")
    }

    @Test("the two districts production actually holds")
    func productionValues() {
        // Read off the live salons collection on 2026-09-06, because these are
        // the only two strings this code path has ever been given. One is a
        // pre-prefix key and one is free text, which is the whole range: iOS
        // printed both of them raw.
        #expect(Areas.labelForKey("D9_Makroryan", english: false) == "ناحیه ۹ – مکروریان")
        #expect(Areas.labelForKey("خیرخانه مینه ناحیه 17", english: false)
                == "خیرخانه مینه ناحیه 17")
        // And the city each one resolves to, which is what puts a salon under a
        // city chip before the discovery sweep has written its `city` field.
        #expect(Areas.cityOf(Areas.canonicalKey("D9_Makroryan")) == "KABUL")
        #expect(Areas.cityOf(Areas.canonicalKey("خیرخانه مینه ناحیه 17")) == "")
    }

    @Test("a district's children are the level below it, never itself")
    func filterableReadsAsAnAddress() {
        let kabul = Areas.filterableIn("KABUL")
        #expect(kabul.first?.kind == .district)
        #expect(Set(kabul.map(\.key)).count == kabul.count)
        // Mazar is the city with sourced parents, so it is where the nesting is
        // actually visible.
        let d02 = Areas.neighbourhoodsIn("MZR_D02")
        #expect(!d02.isEmpty)
        #expect(d02.allSatisfy { $0.kind != .district && $0.parent == "MZR_D02" })
        #expect(Areas.isDistrict("MZR_D02"))
        #expect(!Areas.isDistrict("MZR_GuzarQarghan"))
    }
}
