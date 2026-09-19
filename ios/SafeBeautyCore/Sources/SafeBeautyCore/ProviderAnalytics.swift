import Foundation

/// A salon's lifetime booking tally, as the Analytics tab reads it.
///
/// Mirrors `ProviderAnalytics` in `DashboardViewModel`'s sibling
/// `ProviderViewModel.kt`, and lives here rather than in the app target for the
/// reason the parity tests exist: two platforms showing a salon owner different
/// numbers for the same bookings is worse than either number being wrong, and
/// only a test can hold that.
///
/// Read from `salon_stats/{salonId}`, which a trigger maintains — not counted
/// from the appointments list. The list is bounded at three hundred, so
/// counting locally is right until a salon passes that and then silently wrong,
/// and a busy salon is exactly the one that cares.
public struct ProviderAnalytics: Equatable, Sendable {
    public var total: Int = 0
    public var confirmed: Int = 0
    public var pending: Int = 0
    public var cancelled: Int = 0
    public var byService: [String: Int] = [:]
    public var confirmedByService: [String: Int] = [:]

    public init(total: Int = 0, confirmed: Int = 0, pending: Int = 0, cancelled: Int = 0,
                byService: [String: Int] = [:], confirmedByService: [String: Int] = [:]) {
        self.total = total
        self.confirmed = confirmed
        self.pending = pending
        self.cancelled = cancelled
        self.byService = byService
        self.confirmedByService = confirmedByService
    }

    /// The three rules Android applies to the raw tally, and why each exists.
    ///
    /// COMPLETED counts as confirmed: a scheduled function flips a past
    /// CONFIRMED booking over, so treating them separately would make a salon's
    /// accepted work appear to shrink as time passed.
    ///
    /// Zero buckets are dropped. Firestore's increment leaves a key behind at
    /// zero once its last booking moves away, and a service with no bookings is
    /// not a row in the breakdown — it is a row that says nothing.
    public static func from(total: Int,
                            byStatus: [String: Int],
                            byService: [String: Int],
                            confirmedByService: [String: Int]) -> ProviderAnalytics {
        func n(_ key: String) -> Int { byStatus[key] ?? 0 }
        return ProviderAnalytics(
            total: total,
            confirmed: n("CONFIRMED") + n("COMPLETED"),
            pending: n("PENDING"),
            cancelled: n("CANCELLED"),
            byService: byService.filter { $0.value > 0 },
            confirmedByService: confirmedByService.filter { $0.value > 0 })
    }

    /// What the salon has earned or will earn from accepted work, at today's
    /// prices. An estimate, and named one: the price is read now, not at the
    /// time each booking was made.
    public static func estimatedRevenue(confirmedByService: [String: Int],
                                        prices: [String: Int]) -> Int {
        confirmedByService.reduce(0) { sum, entry in
            sum + (prices[entry.key] ?? 0) * entry.value
        }
    }

    /// The breakdown, biggest first — and by name where two are equal, so the
    /// rows do not reshuffle between reads. `sorted(by:)` is not stable in
    /// Swift, and a dictionary has no order to be stable about.
    public var serviceBreakdown: [(service: String, count: Int)] {
        byService
            .map { (service: $0.key, count: $0.value) }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.service < $1.service }
    }
}
