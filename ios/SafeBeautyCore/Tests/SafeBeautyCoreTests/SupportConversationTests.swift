import Testing
import Foundation
@testable import SafeBeautyCore

/// The history row exactly as `archiveSupportConversation` writes it, and the
/// line it draws through the one support thread.
@Suite("Support conversation history")
struct SupportConversationTests {

    /// Field for field as functions/domains/support.js creates it.
    static var archived: [String: Any] { [
        "userId": "bfc0d936-fae0-4aa6-a198-5ad5d3572d73",
        "openedAt": 1_787_542_954_417,
        "closedAt": 1_787_600_000_000,
        "messageCount": 4,
        "lastMessage": "ممنون، حل شد",
        "rating": 0,
        "ratingComment": "",
        "ratedAt": 0,
        "backfilled": false,
    ] }

    static func message(_ ts: Int64) -> ChatMessage {
        var m = ChatMessage()
        m.id = "m\(ts)"
        m.timestamp = ts
        return m
    }

    @Test("the server's row decodes and is rateable")
    func decodesServerRow() throws {
        let c = try DocumentDecoding.decode(SupportConversation.self, from: Self.archived)
        #expect(c.openedAt == 1_787_542_954_417)
        #expect(c.closedAt == 1_787_600_000_000)
        #expect(c.messageCount == 4)
        #expect(c.lastMessage == "ممنون، حل شد")
        #expect(c.rating == 0)
        #expect(!c.backfilled)
        #expect(c.canBeRated)
    }

    @Test("missing fields fall back to defaults instead of failing the row")
    func toleratesMissingFields() throws {
        let c = try DocumentDecoding.decode(SupportConversation.self, from: ["closedAt": 5])
        #expect(c.closedAt == 5)
        #expect(c.openedAt == 0)
        #expect(c.messageCount == 0)
        #expect(c.lastMessage == "")
        #expect(c.rating == 0)
        #expect(!c.backfilled)
    }

    @Test("wrong types and out-of-range ratings do not break or overdraw")
    func toleratesWrongTypes() throws {
        var raw = Self.archived
        raw["rating"] = 9
        raw["messageCount"] = "four"
        raw["backfilled"] = NSNull()
        raw["closedAt"] = 1_787_600_000_000.0
        let c = try DocumentDecoding.decode(SupportConversation.self, from: raw)
        #expect(c.rating == 5)
        #expect(c.messageCount == 0)
        #expect(!c.backfilled)
        #expect(c.closedAt == 1_787_600_000_000)
    }

    @Test("rated or backfilled rows are not offered for rating")
    func rateability() throws {
        var rated = Self.archived
        rated["rating"] = 4
        #expect(!(try DocumentDecoding.decode(SupportConversation.self, from: rated)).canBeRated)
        var backfilled = Self.archived
        backfilled["backfilled"] = true
        #expect(!(try DocumentDecoding.decode(SupportConversation.self, from: backfilled)).canBeRated)
    }

    @Test("the current conversation is what came after the newest close")
    func currentMessages() {
        var older = SupportConversation(); older.openedAt = 10; older.closedAt = 20
        var newer = SupportConversation(); newer.openedAt = 30; newer.closedAt = 40
        let thread = [5, 15, 20, 35, 40, 41, 50].map { Self.message(Int64($0)) }

        #expect(SupportConversation.currentMessages(thread, history: []).count == thread.count)
        // Order of the history array must not matter.
        let current = SupportConversation.currentMessages(thread, history: [older, newer])
        #expect(current.map(\.timestamp) == [41, 50])
        #expect(SupportConversation.currentMessages(thread, history: [newer, older]).map(\.timestamp) == [41, 50])
        #expect(SupportConversation.newest([older, newer])?.closedAt == 40)

        // Both ends of a transcript are inclusive.
        #expect(thread.filter(newer.contains).map(\.timestamp) == [35, 40])
    }

    @Test("comments are trimmed and cut to what the rules accept")
    func clampComment() {
        #expect(SupportConversation.clampComment("  خوب بود \n") == "خوب بود")
        let long = String(repeating: "آ", count: 700)
        #expect(SupportConversation.clampComment(long).utf16.count == 500)
        // An emoji is two UTF-16 units and is never cut in half.
        let emoji = String(repeating: "😀", count: 300)
        let cut = SupportConversation.clampComment(emoji)
        #expect(cut.utf16.count == 500)
        #expect(cut.unicodeScalars.allSatisfy { $0 == "😀".unicodeScalars.first! })
    }
}
