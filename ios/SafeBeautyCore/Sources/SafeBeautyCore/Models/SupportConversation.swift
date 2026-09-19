import Foundation

/// One CLOSED support conversation, as the server archives it.
///
/// Written only by `archiveSupportConversation` (functions/domains/support.js)
/// into `support_tickets/{uid}/history/{id}` when an admin closes the ticket.
/// No message is moved: the thread is still the single `support_{uid}`
/// conversation, and a history row only draws a line through it —
/// `openedAt...closedAt` is this conversation, and everything after the newest
/// `closedAt` is the current one.
///
/// The only client write is the rating, once, and the rules allow nothing else:
/// `rating` (Int 1…5), `ratingComment` (String ≤ 500), `ratedAt` (Int), and
/// only while `rating` is still 0.
public struct SupportConversation: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var userId: String = ""
    public var openedAt: Int64 = 0
    public var closedAt: Int64 = 0
    public var messageCount: Int = 0
    public var lastMessage: String = ""
    /// 0 = not rated, else 1…5.
    public var rating: Int = 0
    public var ratingComment: String = ""
    public var ratedAt: Int64 = 0
    /// Rows the server created retroactively for conversations that ended
    /// before this feature existed. Nobody is asked to rate those.
    public var backfilled: Bool = false

    public var openedDate: Date { Date(timeIntervalSince1970: Double(openedAt) / 1000) }
    public var closedDate: Date { Date(timeIntervalSince1970: Double(closedAt) / 1000) }

    /// Whether to offer the rating control — mirrors the rules' `rating == 0`,
    /// plus the product decision not to ask about backfilled conversations.
    public var canBeRated: Bool { rating == 0 && !backfilled }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case userId, openedAt, closedAt, messageCount, lastMessage
        case rating, ratingComment, ratedAt, backfilled
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        func int64(_ k: CodingKeys) -> Int64 {
            if let v = (try? c.decodeIfPresent(Int64.self, forKey: k)).flatMap({ $0 }) { return v }
            // A double that JSONSerialization kept fractional (hand-edited doc).
            if let d = (try? c.decodeIfPresent(Double.self, forKey: k)).flatMap({ $0 }), d.isFinite {
                return Int64(d)
            }
            return 0
        }
        userId = str(.userId)
        lastMessage = str(.lastMessage)
        ratingComment = str(.ratingComment)
        openedAt = int64(.openedAt)
        closedAt = int64(.closedAt)
        ratedAt = int64(.ratedAt)
        messageCount = max(0, Int(clamping: int64(.messageCount)))
        // Clamped like Review.rating: a hand-edited row must not draw six stars.
        rating = min(5, max(0, Int(clamping: int64(.rating))))
        backfilled = (try? c.decodeIfPresent(Bool.self, forKey: .backfilled)).flatMap { $0 } ?? false
    }

    /// The newest line drawn through the thread, or 0 when there is none.
    ///
    /// The max rather than `first`, so the answer does not depend on the
    /// listener's ordering.
    public static func newestClosedAt(_ history: [SupportConversation]) -> Int64 {
        history.map(\.closedAt).max() ?? 0
    }

    /// The most recently closed conversation.
    public static func newest(_ history: [SupportConversation]) -> SupportConversation? {
        history.max { $0.closedAt < $1.closedAt }
    }

    /// The messages of the conversation still running: everything after the
    /// newest close. With no history that is the whole thread.
    public static func currentMessages(
        _ messages: [ChatMessage], history: [SupportConversation]
    ) -> [ChatMessage] {
        let line = newestClosedAt(history)
        return messages.filter { $0.timestamp > line }
    }

    /// Whether a message belongs to this archived conversation.
    public func contains(_ message: ChatMessage) -> Bool {
        message.timestamp >= openedAt && message.timestamp <= closedAt
    }

    /// The maximum comment the rules accept.
    public static let maxCommentLength = 500

    /// Trimmed and cut so the rules' `size() <= 500` holds however it counts.
    ///
    /// Cut on UTF-16 length, the largest of the ways a string can be counted
    /// here (a scalar is one or two UTF-16 units), and on scalar boundaries so
    /// an emoji is never split in half.
    public static func clampComment(_ text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.utf16.count > maxCommentLength else { return trimmed }
        var out = String.UnicodeScalarView()
        var units = 0
        for scalar in trimmed.unicodeScalars {
            let n = scalar.utf16.count
            if units + n > maxCommentLength { break }
            out.append(scalar)
            units += n
        }
        return String(out).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
