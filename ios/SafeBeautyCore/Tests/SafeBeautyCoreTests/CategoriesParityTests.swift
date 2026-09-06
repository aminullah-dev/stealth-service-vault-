import Testing
import Foundation
@testable import SafeBeautyCore

/// The category list is a copy of the server's, and a copy nobody checks rots.
///
/// The server derives `salons/{id}.categories` from free text and is therefore
/// the authority on what a category can be. If it grows one and this does not,
/// the new chip simply never appears on iOS — no error, no empty state, nothing
/// to notice. Same guard as AreasParityTests, for the same reason.
@Suite("Categories match the server's vocabulary")
struct CategoriesParityTests {

    @Test("the canonical list is the one functions/lib/categories.js writes")
    func matchesServer() throws {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // SafeBeautyCoreTests
            .deletingLastPathComponent()   // Tests
            .deletingLastPathComponent()   // SafeBeautyCore
            .deletingLastPathComponent()   // ios
            .deletingLastPathComponent()   // repo root
        let js = try #require(
            try? String(contentsOf: root.appending(path: "functions/lib/categories.js"),
                        encoding: .utf8),
            "could not read functions/lib/categories.js")

        let re = try NSRegularExpression(pattern: #"const CANONICAL = \[([^\]]*)\]"#)
        let m = try #require(
            re.firstMatch(in: js, range: NSRange(js.startIndex..., in: js)),
            "CANONICAL not found — has categories.js changed shape?")
        let body = String(js[Range(m.range(at: 1), in: js)!])
        let expected = body
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines)
                     .trimmingCharacters(in: CharacterSet(charactersIn: "\"")) }
            .filter { !$0.isEmpty }

        #expect(!expected.isEmpty, "parsed no categories from categories.js")
        #expect(Categories.canonical == expected,
                "Categories.swift has drifted from functions/lib/categories.js")
    }

    @Test("and the one Android offers, in the same order")
    func matchesAndroid() throws {
        // Android prepends "All" as a sentinel for no filter; iOS models that as
        // nil rather than as a key, so it is dropped before comparing.
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
        let kt = try #require(
            try? String(contentsOf: root.appending(
                path: "app/src/main/java/com/safebeauty/app/viewmodel/DashboardViewModel.kt"),
                encoding: .utf8))
        let re = try NSRegularExpression(pattern: #"CATEGORY_KEYS = listOf\(([^)]*)\)"#)
        let m = try #require(re.firstMatch(in: kt, range: NSRange(kt.startIndex..., in: kt)))
        let keys = String(kt[Range(m.range(at: 1), in: kt)!])
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines)
                     .trimmingCharacters(in: CharacterSet(charactersIn: "\"")) }
            .filter { !$0.isEmpty && $0 != "All" }
        #expect(Categories.canonical == keys)
    }
}
