import Foundation

/// One day of a salon's week.
///
/// `dayOfWeek` follows `java.util.Calendar` — Sunday = 1 … Saturday = 7 —
/// because Android wrote these documents first. Swift's
/// `Calendar.component(.weekday)` uses the same numbering, so no translation is
/// needed; that is luck rather than design and is worth stating, because a
/// silent off-by-one here opens a salon on the wrong day.
public struct WorkingHours: Codable, Hashable, Sendable {
    public var dayOfWeek: Int = 0
    public var isOpen: Bool = false
    public var openHour: Int = 9
    public var openMinute: Int = 0
    public var closeHour: Int = 18
    public var closeMinute: Int = 0

    public init(dayOfWeek: Int, isOpen: Bool = true,
                openHour: Int = 9, openMinute: Int = 0,
                closeHour: Int = 18, closeMinute: Int = 0) {
        self.dayOfWeek = dayOfWeek; self.isOpen = isOpen
        self.openHour = openHour; self.openMinute = openMinute
        self.closeHour = closeHour; self.closeMinute = closeMinute
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func int(_ k: CodingKeys, _ d: Int) -> Int {
            (try? c.decodeIfPresent(Int.self, forKey: k)).flatMap { $0 } ?? d
        }
        dayOfWeek = int(.dayOfWeek, 0)
        isOpen = (try? c.decodeIfPresent(Bool.self, forKey: .isOpen)).flatMap { $0 } ?? false
        openHour = int(.openHour, 9); openMinute = int(.openMinute, 0)
        closeHour = int(.closeHour, 18); closeMinute = int(.closeMinute, 0)
    }

    public var openMinuteOfDay: Int { openHour * 60 + openMinute }
    public var closeMinuteOfDay: Int { closeHour * 60 + closeMinute }
}

/// Building the day's grid of bookable times.
///
/// Everything here is in Asia/Kabul, which is UTC+04:30. The half hour is not a
/// footnote: a grid computed in UTC and shifted by whole hours lands every slot
/// thirty minutes off, and the salon's own opening time stops matching the
/// times its customers are offered. The server does its own check in Kabul
/// (`functions/lib/hours.js`), so a client working in any other zone disagrees
/// with it about what "9am Tuesday" means.
public enum DayGrid {

    public static let kabul = TimeZone(identifier: "Asia/Kabul")!

    /// Minimum slot length, matching the server's MIN_SLOT_MINUTES and the
    /// client's own floor on Android. A salon that stored 5 would otherwise
    /// generate a grid nobody can read and a payload nobody wants to send.
    public static let minimumSlotMinutes = 30

    public static var kabulCalendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = kabul
        return c
    }

    /// The Kabul-local weekday for an instant, Sunday = 1.
    public static func weekday(of date: Date) -> Int {
        kabulCalendar.component(.weekday, from: date)
    }

    /// Midnight in Kabul on the day the instant falls in.
    public static func dayStart(_ date: Date) -> Date {
        kabulCalendar.startOfDay(for: date)
    }

    /// The bookable start times for one day, as epoch milliseconds.
    ///
    /// Returns nothing when the salon is closed that day, which is different
    /// from returning an empty grid because the day is full — the caller shows
    /// different words for each.
    public static func slots(
        for date: Date,
        hours: [WorkingHours],
        slotMinutes: Int,
        now: Date = Date()
    ) -> [Int64] {
        let step = max(minimumSlotMinutes, slotMinutes)
        let weekday = weekday(of: date)
        guard let today = hours.first(where: { $0.dayOfWeek == weekday }), today.isOpen else {
            return []
        }

        let midnight = dayStart(date)
        var out: [Int64] = []
        var minute = today.openMinuteOfDay
        // The last slot must END by closing time, not start at it — a salon
        // that closes at 18:00 does not begin a 90-minute appointment at 17:30.
        while minute + step <= today.closeMinuteOfDay {
            let start = midnight.addingTimeInterval(TimeInterval(minute * 60))
            // A slot already past is not bookable. Compared as instants rather
            // than by comparing hour numbers, so the half-hour offset cannot
            // creep in.
            if start > now {
                out.append(Int64(start.timeIntervalSince1970 * 1000))
            }
            minute += step
        }
        return out
    }

    /// Whether a salon can be booked on any day at all.
    ///
    /// Mirrors `hasBookableWeek` on the server. A salon whose week is entirely
    /// closed is not a bug in the reader — it is a salon nobody can book, and
    /// the integrity sweep raises SALON_UNBOOKABLE for exactly this.
    public static func hasBookableWeek(_ hours: [WorkingHours], slotMinutes: Int) -> Bool {
        let step = max(minimumSlotMinutes, slotMinutes)
        return hours.contains { $0.isOpen && $0.openMinuteOfDay + step <= $0.closeMinuteOfDay }
    }

    /// The Afghan week, opening Saturday and closing Friday.
    ///
    /// Matches `defaultWorkingHours()` on the server, and exists for the same
    /// reason: a salon stored with no hours at all could never be booked, and
    /// the owner who opens her profile, sees a sensible week and changes
    /// nothing should have a salon that works.
    public static func defaultWeek() -> [WorkingHours] {
        [7, 1, 2, 3, 4, 5].map { WorkingHours(dayOfWeek: $0) }
        + [WorkingHours(dayOfWeek: 6, isOpen: false, closeHour: 13)]   // Friday
    }
}
