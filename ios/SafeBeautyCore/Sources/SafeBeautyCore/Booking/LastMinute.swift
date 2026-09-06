import Foundation

/// The discount a salon offers to fill a chair that is about to go empty.
///
/// A mirror of `lastMinuteDiscount` in functions/lib/money.js, and mirrored on
/// purpose: the server computes the real figure at checkout, and a client that
/// guessed differently would promise a saving it does not deliver. This exists
/// only to decide which hours to MARK — the number a customer is charged still
/// comes from the server's own quote.
public enum LastMinute {

    /// Whether a booking starting at `start` qualifies right now.
    ///
    /// The window is measured forward from now, and a slot in the past does not
    /// qualify — `hoursUntil < 0` is excluded server-side and here, because a
    /// negative gap would otherwise pass a `<= windowHours` test and discount
    /// every hour that has already gone.
    public static func applies(
        start: Int64, now: Date = Date(),
        enabled: Bool, percent: Int, windowHours: Int
    ) -> Bool {
        guard enabled, percent > 0, windowHours > 0 else { return false }
        let hoursUntil = (Double(start) - now.timeIntervalSince1970 * 1000) / 3_600_000
        return hoursUntil >= 0 && hoursUntil <= Double(windowHours)
    }

    /// The saving in whole AFN, rounded the way the server rounds it and capped
    /// at the price. Shown as an estimate beside a slot, never as the charge.
    public static func discount(
        listPrice: Int, start: Int64, now: Date = Date(),
        enabled: Bool, percent: Int, windowHours: Int
    ) -> Int {
        guard listPrice > 0,
              applies(start: start, now: now, enabled: enabled,
                      percent: percent, windowHours: windowHours)
        else { return 0 }
        return min(listPrice, Int((Double(listPrice) * Double(min(percent, 100)) / 100).rounded()))
    }
}
