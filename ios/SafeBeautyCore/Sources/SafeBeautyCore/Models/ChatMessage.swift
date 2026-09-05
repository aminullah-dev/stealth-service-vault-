import Foundation

/// One message in a conversation.
///
/// Support conversations are ordinary chats with a conversationId of
/// `support_{uid}`; the support_tickets document beside them is only the
/// admin's queue entry, not the conversation.
public struct ChatMessage: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var conversationId: String = ""
    public var senderId: String = ""
    public var senderName: String = ""
    public var content: String = ""
    public var timestamp: Int64 = 0

    public var date: Date { Date(timeIntervalSince1970: Double(timestamp) / 1000) }

    public init() {}

    /// The conversation id for a user's support thread. One per user, which is
    /// why the ticket document is keyed by uid too.
    public static func supportConversationId(for uid: String) -> String { "support_\(uid)" }

    private enum CodingKeys: String, CodingKey {
        case conversationId, senderId, senderName, content, timestamp
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        conversationId = str(.conversationId); senderId = str(.senderId)
        senderName = str(.senderName); content = str(.content)
        timestamp = (try? c.decodeIfPresent(Int64.self, forKey: .timestamp)).flatMap { $0 } ?? 0
    }
}
