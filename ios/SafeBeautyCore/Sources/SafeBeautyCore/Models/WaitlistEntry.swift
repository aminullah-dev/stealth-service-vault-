import Foundation

/// A place in the queue for a day that is already full.
///
/// Mirrors Android's WaitlistEntry field for field, because both write the same
/// documents and the provider console reads them: the salon marks one
/// SLOT_AVAILABLE when someone cancels, and the customer who was waiting is the
/// one told first.
public struct WaitlistEntry: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var salonId: String = ""
    public var salonName: String = ""
    public var customerId: String = ""
    public var customerName: String = ""
    /// Start-of-day millis for the day she wants, not a specific hour — the
    /// whole point is that no hour is free yet.
    public var requestedDate: Int64 = 0
    /// "WAITING" | "SLOT_AVAILABLE" | "EXPIRED"
    public var status: String = "WAITING"
    public var createdAt: Int64 = 0

    public init() {}

    public var date: Date { Date(timeIntervalSince1970: Double(requestedDate) / 1000) }
    public var isOffered: Bool { status == "SLOT_AVAILABLE" }
    /// EXPIRED rows are history: she dismissed the offer or it lapsed.
    public var isLive: Bool { status == "WAITING" || status == "SLOT_AVAILABLE" }
}
