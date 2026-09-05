import Foundation

/// One thing the server wanted her to know.
///
/// Written exclusively by Cloud Functions — `allow create: if false` — because
/// every notification document becomes a real push, and an open create path
/// would let any signed-in user push arbitrary text to any user's phone.
public struct AppNotification: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var recipientId: String = ""
    public var type: NotificationKind = .other
    public var title: String = ""
    public var body: String = ""
    public var isRead: Bool = false
    public var createdAt: Int64 = 0
    /// The appointment, payment or salon this is about, if any.
    public var relatedId: String = ""

    public var date: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case recipientId, type, title, body, isRead, createdAt, relatedId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        recipientId = str(.recipientId); title = str(.title)
        body = str(.body); relatedId = str(.relatedId)
        isRead = (try? c.decodeIfPresent(Bool.self, forKey: .isRead)).flatMap { $0 } ?? false
        createdAt = (try? c.decodeIfPresent(Int64.self, forKey: .createdAt)).flatMap { $0 } ?? 0
        type = (try? c.decodeIfPresent(NotificationKind.self, forKey: .type)).flatMap { $0 } ?? .other
    }
}

/// The kinds the server writes today, plus a catch-all.
///
/// `.other` rather than a decode failure, for the same reason AppointmentStatus
/// has `.unknown`: a backend that adds a kind must not blank the notification
/// list of every phone running an older build. The title and body are already
/// written by the server in the user's language, so an unrecognised kind still
/// displays correctly — only its icon falls back.
public enum NotificationKind: String, Codable, Hashable, Sendable {
    case newBooking = "NEW_BOOKING"
    case bookingConfirmed = "BOOKING_CONFIRMED"
    case bookingCancelled = "BOOKING_CANCELLED"
    case bookingReminder = "BOOKING_REMINDER"
    case paymentReceived = "PAYMENT_RECEIVED"
    case kycApproved = "KYC_APPROVED"
    case kycRejected = "KYC_REJECTED"
    case broadcast = "BROADCAST"
    case other

    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = NotificationKind(rawValue: raw) ?? .other
    }

    /// An SF Symbol per kind. Chosen so the list is scannable without reading —
    /// a reminder and a cancellation should not look alike at a glance.
    public var symbolName: String {
        switch self {
        case .newBooking, .bookingConfirmed: "calendar.badge.checkmark"
        case .bookingCancelled: "calendar.badge.minus"
        case .bookingReminder: "bell.badge"
        case .paymentReceived: "banknote"
        case .kycApproved: "checkmark.shield"
        case .kycRejected: "exclamationmark.shield"
        case .broadcast: "megaphone"
        case .other: "bell"
        }
    }

    /// Whether this is something gone wrong, so the list can weight it.
    public var isNegative: Bool {
        self == .bookingCancelled || self == .kycRejected
    }
}
