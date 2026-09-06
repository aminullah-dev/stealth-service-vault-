import Testing
@testable import SafeBeautyCore

@Suite("ProviderAnalytics matches ProviderViewModel.kt")
struct ProviderAnalyticsTests {

    @Test("a finished booking still counts as one the salon accepted")
    func completedCountsAsConfirmed() {
        // completePastAppointments flips CONFIRMED to COMPLETED about two hours
        // after the start time. Counting only CONFIRMED would make a salon's
        // accepted work appear to shrink overnight.
        let a = ProviderAnalytics.from(
            total: 10,
            byStatus: ["CONFIRMED": 3, "COMPLETED": 4, "PENDING": 2, "CANCELLED": 1],
            byService: [:], confirmedByService: [:])
        #expect(a.confirmed == 7)
        #expect(a.pending == 2)
        #expect(a.cancelled == 1)
        #expect(a.total == 10)
    }

    @Test("a status the tally has never seen reads zero, not nil")
    func missingStatusIsZero() {
        let a = ProviderAnalytics.from(total: 1, byStatus: [:], byService: [:], confirmedByService: [:])
        #expect(a.confirmed == 0)
        #expect(a.pending == 0)
        #expect(a.cancelled == 0)
    }

    @Test("emptied buckets are dropped, because increment leaves the key behind")
    func zeroBucketsAreDropped() {
        // Firestore's increment leaves a key at zero once its last booking
        // moves away. A service with no bookings is not a row in the breakdown.
        let a = ProviderAnalytics.from(
            total: 3,
            byStatus: ["CONFIRMED": 3],
            byService: ["مو": 3, "ناخن": 0],
            confirmedByService: ["مو": 3, "ناخن": 0])
        #expect(a.byService == ["مو": 3])
        #expect(a.confirmedByService == ["مو": 3])
    }

    @Test("revenue is accepted work at today's prices, and unpriced work is zero")
    func revenue() {
        #expect(ProviderAnalytics.estimatedRevenue(
            confirmedByService: ["مو": 2, "ناخن": 3],
            prices: ["مو": 80, "ناخن": 75]) == 385)
        // A service the salon has since stopped pricing contributes nothing
        // rather than crashing or guessing.
        #expect(ProviderAnalytics.estimatedRevenue(
            confirmedByService: ["مو": 2], prices: [:]) == 0)
    }

    @Test("the breakdown is biggest first, and ties break by name so rows hold still")
    func breakdownOrder() {
        let a = ProviderAnalytics.from(
            total: 6, byStatus: [:],
            byService: ["b": 2, "a": 2, "c": 5], confirmedByService: [:])
        #expect(a.serviceBreakdown.map(\.service) == ["c", "a", "b"])
    }

    @Test("an empty tally is the empty state, not a zero-filled chart")
    func emptyIsEmpty() {
        let a = ProviderAnalytics.from(total: 0, byStatus: [:], byService: [:], confirmedByService: [:])
        #expect(a == ProviderAnalytics())
        #expect(a.serviceBreakdown.isEmpty)
    }
}
