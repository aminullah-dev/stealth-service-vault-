import Foundation

/// A photo a salon posted.
public struct SalonPost: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var salonId: String = ""
    public var salonName: String = ""
    public var imageUrl: String = ""
    public var caption: String = ""
    public var createdAt: Int64 = 0
    public var likeCount: Int = 0
    public var commentCount: Int = 0

    public var date: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case salonId, salonName, imageUrl, caption, createdAt, likeCount, commentCount
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        func int(_ k: CodingKeys) -> Int { (try? c.decodeIfPresent(Int.self, forKey: k)).flatMap { $0 } ?? 0 }
        salonId = str(.salonId); salonName = str(.salonName)
        imageUrl = str(.imageUrl); caption = str(.caption)
        likeCount = int(.likeCount); commentCount = int(.commentCount)
        createdAt = (try? c.decodeIfPresent(Int64.self, forKey: .createdAt)).flatMap { $0 } ?? 0
    }
}

/// A discount a salon is running.
public struct SalonOffer: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var salonId: String = ""
    public var salonName: String = ""
    public var title: String = ""
    public var description: String = ""
    public var service: String = ""
    public var discountPercent: Int = 0
    public var discountAmount: Int = 0
    public var active: Bool = false
    public var expiresAt: Int64 = 0
    public var createdAt: Int64 = 0

    /// Live means active AND not past its expiry.
    ///
    /// `expiresAt` of 0 means "no expiry" rather than "expired in 1970" —
    /// getting that backwards would hide every offer that never set one, which
    /// is most of them.
    public func isLive(now: Date = Date()) -> Bool {
        guard active else { return false }
        guard expiresAt > 0 else { return true }
        return Double(expiresAt) / 1000 > now.timeIntervalSince1970
    }

    /// Whether this offer actually offers anything.
    ///
    /// The live data has one with a title of "Nakhon 20% Off" and both discount
    /// fields set to zero — the percentage is in the TEXT, not the numbers. So
    /// a card is worth showing when it has a title, and the numeric badge only
    /// appears when there is a number behind it.
    public var hasNumericDiscount: Bool { discountPercent > 0 || discountAmount > 0 }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case salonId, salonName, title, description, service
        case discountPercent, discountAmount, active, expiresAt, createdAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        func int(_ k: CodingKeys) -> Int { (try? c.decodeIfPresent(Int.self, forKey: k)).flatMap { $0 } ?? 0 }
        salonId = str(.salonId); salonName = str(.salonName)
        title = str(.title); description = str(.description); service = str(.service)
        discountPercent = int(.discountPercent); discountAmount = int(.discountAmount)
        active = (try? c.decodeIfPresent(Bool.self, forKey: .active)).flatMap { $0 } ?? false
        expiresAt = (try? c.decodeIfPresent(Int64.self, forKey: .expiresAt)).flatMap { $0 } ?? 0
        createdAt = (try? c.decodeIfPresent(Int64.self, forKey: .createdAt)).flatMap { $0 } ?? 0
    }
}

/// A comment under a salon's photo.
///
/// Public, like the reviews a salon already carries, and signed with the same
/// stored name — the rules check `authorName` against the author's own user
/// document so a comment cannot be signed with somebody else's.
public struct PostComment: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var postId: String = ""
    /// The salon whose photo this sits under. The rules read it back through
    /// the post rather than trusting it, because delete authorises on salonId
    /// while the thread renders by postId, and nothing used to tie the two
    /// together.
    public var salonId: String = ""
    public var userId: String = ""
    public var authorName: String = ""
    public var text: String = ""
    public var createdAt: Int64 = 0

    public var date: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case postId, salonId, userId, authorName, text, createdAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        postId = str(.postId); salonId = str(.salonId)
        userId = str(.userId); authorName = str(.authorName); text = str(.text)
        createdAt = (try? c.decodeIfPresent(Int64.self, forKey: .createdAt)).flatMap { $0 } ?? 0
    }
}
