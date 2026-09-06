import Testing
import Foundation
@testable import SafeBeautyCore

/// The client must agree with functions/lib/money.js about which hours are
/// discounted, or it marks a slot the server then charges full price for.
@Suite("Last-minute discount matches the server's rule")
struct LastMinuteTests {
    private let now = Date(timeIntervalSince1970: 1_780_000_000)
    private func hours(_ h: Double) -> Int64 {
        Int64((now.timeIntervalSince1970 + h * 3600) * 1000)
    }

    @Test("inside the window, at both edges")
    func insideWindow() {
        #expect(LastMinute.applies(start: hours(2), now: now,
                                   enabled: true, percent: 8, windowHours: 4))
        // Exactly on the boundary qualifies: the server uses <=, not <.
        #expect(LastMinute.applies(start: hours(4), now: now,
                                   enabled: true, percent: 8, windowHours: 4))
        #expect(LastMinute.applies(start: hours(0), now: now,
                                   enabled: true, percent: 8, windowHours: 4))
    }

    @Test("outside the window, and in the past")
    func outsideWindow() {
        #expect(!LastMinute.applies(start: hours(4.1), now: now,
                                    enabled: true, percent: 8, windowHours: 4))
        // A slot already gone must not qualify. A negative gap would otherwise
        // pass a bare `<= windowHours` test and discount the whole morning.
        #expect(!LastMinute.applies(start: hours(-1), now: now,
                                    enabled: true, percent: 8, windowHours: 4))
    }

    @Test("switched off, or set to nothing")
    func disabled() {
        #expect(!LastMinute.applies(start: hours(1), now: now,
                                    enabled: false, percent: 8, windowHours: 4))
        #expect(!LastMinute.applies(start: hours(1), now: now,
                                    enabled: true, percent: 0, windowHours: 4))
        #expect(!LastMinute.applies(start: hours(1), now: now,
                                    enabled: true, percent: 8, windowHours: 0))
    }

    @Test("the figure, rounded and capped the way the server does it")
    func amount() {
        // Shaghayeq Ha's live settings: 8% inside four hours, on an 80 AFN cut.
        #expect(LastMinute.discount(listPrice: 80, start: hours(2), now: now,
                                    enabled: true, percent: 8, windowHours: 4) == 6)
        // Math.round: 80 * 8 / 100 = 6.4 → 6. Half rounds up on both sides.
        #expect(LastMinute.discount(listPrice: 75, start: hours(1), now: now,
                                    enabled: true, percent: 10, windowHours: 4) == 8)
        // Never more than the price, whatever the salon typed.
        #expect(LastMinute.discount(listPrice: 50, start: hours(1), now: now,
                                    enabled: true, percent: 300, windowHours: 4) == 50)
        #expect(LastMinute.discount(listPrice: 0, start: hours(1), now: now,
                                    enabled: true, percent: 8, windowHours: 4) == 0)
    }
}
