import Foundation

/// A review, which carries the reviewer's name.
///
/// `customerName` is stored on the document and `allow read: if isSignedIn()`,
/// so every signed-in user sees who wrote it. That is a product decision, not
/// an accident — but it means the form that collects one has to say so before
/// she writes, not after. A woman leaving a review about a beauty salon is
/// making a small public statement about herself, and she should know that
/// while she is deciding.
public struct Review: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""
    public var salonId: String = ""
    public var customerId: String = ""
    public var customerName: String = ""
    public var rating: Int = 0
    public var comment: String = ""
    public var createdAt: Int64 = 0

    /// The salon's answer, if it gave one. Written only by the owning provider,
    /// through a field-scoped rule that lets it change nothing else.
    public var providerReply: String = ""
    public var repliedAt: Int64 = 0

    public var date: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }
    public var hasReply: Bool { !providerReply.isEmpty }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case salonId, customerId, customerName, rating, comment, createdAt
        case providerReply, repliedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        salonId = str(.salonId); customerId = str(.customerId)
        customerName = str(.customerName); comment = str(.comment)
        providerReply = str(.providerReply)
        createdAt = (try? c.decodeIfPresent(Int64.self, forKey: .createdAt)).flatMap { $0 } ?? 0
        repliedAt = (try? c.decodeIfPresent(Int64.self, forKey: .repliedAt)).flatMap { $0 } ?? 0
        // Clamped rather than trusted. The server validates 1–5 on write, but a
        // document from before that check, or a hand-edited one, would
        // otherwise draw six stars or none.
        let raw = (try? c.decodeIfPresent(Int.self, forKey: .rating)).flatMap { $0 } ?? 0
        rating = min(5, max(0, raw))
    }
}

/// Whether a booking can be reviewed, mirroring what submitReview enforces.
///
/// Kept here rather than in the view so the button is only offered where the
/// callable would actually accept it. Offering a control that the server
/// refuses is how a customer learns not to trust the buttons.
public enum ReviewEligibility {
    /// The visit must have happened: the appointment is in the past and was
    /// not cancelled. The server also refuses a second review per booking,
    /// which the client cannot know without checking, so that one is reported
    /// rather than predicted.
    public static func canReview(_ appointment: Appointment, now: Date = Date()) -> Bool {
        guard appointment.date < now else { return false }
        // submitReview stamps the appointment `reviewed` and refuses a second
        // one, so the button was being offered on bookings already reviewed.
        guard !appointment.reviewed else { return false }
        return appointment.status == .completed || appointment.status == .confirmed
    }
}

/// When she can tell us the visit went wrong.
///
/// Mirrors `canReportVisit` in `functions/lib/visitreport.js` — the same four
/// conditions, so a button she can press is one the server will accept. The
/// window is the server's, and the server is still the one that decides.
public enum VisitReportEligibility {
    /// Fourteen days, matching REPORT_WINDOW_DAYS on the server.
    public static let windowDays = 14

    public static func canReport(_ appointment: Appointment, now: Date = Date()) -> Bool {
        // Only a booking the salon accepted. A PENDING one was never agreed to
        // and a CANCELLED one already has its own resolution.
        guard appointment.status == .completed || appointment.status == .confirmed
        else { return false }
        // Not before it was due. CONFIRMED counts from the appointed hour
        // rather than from COMPLETED, because a woman standing outside a
        // locked salon should not have to wait two hours for a scheduled
        // function to agree with her.
        guard appointment.date <= now else { return false }
        let age = now.timeIntervalSince(appointment.date)
        guard age <= Double(windowDays) * 24 * 3600 else { return false }
        return !appointment.visitReported
    }
}
