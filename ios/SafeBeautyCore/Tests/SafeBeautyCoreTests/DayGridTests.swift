import Testing
import Foundation
@testable import SafeBeautyCore

/// The grid a customer picks from, in Asia/Kabul.
///
/// The half-hour offset is the thing to get wrong here. The server checks every
/// booking against the salon's hours in Kabul (`functions/lib/hours.js`), so a
/// client that computes in UTC and shifts by whole hours is thirty minutes out
/// on every slot — and every one of them is refused at payment, with no
/// explanation a customer could act on.
@Suite("Day grid in Asia/Kabul")
struct DayGridTests {

    /// A Tuesday. Chosen rather than "today" so the test does not change its
    /// mind depending on when it runs.
    static var tuesdayNoonKabul: Date {
        var c = DateComponents()
        c.year = 2026; c.month = 9; c.day = 8; c.hour = 12
        return DayGrid.kabulCalendar.date(from: c)!
    }

    /// Far enough back that nothing is filtered as "already past".
    static let longAgo = Date(timeIntervalSince1970: 0)

    @Test("Kabul is UTC+4:30, and the calendar knows it")
    func kabulOffset() {
        #expect(DayGrid.kabul.secondsFromGMT(for: Self.tuesdayNoonKabul) == 4 * 3600 + 1800)
    }

    @Test("weekday numbering matches java.util.Calendar")
    func weekdayNumbering() {
        // 2026-09-08 is a Tuesday. Sunday = 1, so Tuesday = 3.
        #expect(DayGrid.weekday(of: Self.tuesdayNoonKabul) == 3)
    }

    @Test("a normal day yields slots from opening to closing")
    func normalDay() {
        let hours = [WorkingHours(dayOfWeek: 3, openHour: 9, closeHour: 12)]
        let slots = DayGrid.slots(for: Self.tuesdayNoonKabul, hours: hours,
                                  slotMinutes: 60, now: Self.longAgo)
        #expect(slots.count == 3, "09:00, 10:00, 11:00")

        // The first slot is 09:00 Kabul, which is 04:30 UTC — the check that
        // catches a whole-hour shift.
        let first = Date(timeIntervalSince1970: Double(slots[0]) / 1000)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        #expect(utc.component(.hour, from: first) == 4)
        #expect(utc.component(.minute, from: first) == 30)
    }

    /// The rule that stops a salon starting work it cannot finish.
    @Test("the last slot must end by closing time, not start at it")
    func lastSlotEndsByClosing() {
        let hours = [WorkingHours(dayOfWeek: 3, openHour: 9, closeHour: 18)]
        // 90-minute appointments: the last one that fits starts at 16:30, not
        // 17:30, because a salon closing at 18:00 does not begin 90 minutes of
        // work at half past five.
        let slots = DayGrid.slots(for: Self.tuesdayNoonKabul, hours: hours,
                                  slotMinutes: 90, now: Self.longAgo)
        #expect(slots.count == 6, "09:00 through 16:30")
        let last = Date(timeIntervalSince1970: Double(slots.last!) / 1000)
        #expect(DayGrid.kabulCalendar.component(.hour, from: last) == 16)
        #expect(DayGrid.kabulCalendar.component(.minute, from: last) == 30)
    }

    @Test("a closed day yields nothing")
    func closedDay() {
        let hours = [WorkingHours(dayOfWeek: 3, isOpen: false)]
        #expect(DayGrid.slots(for: Self.tuesdayNoonKabul, hours: hours,
                              slotMinutes: 30, now: Self.longAgo).isEmpty)
    }

    @Test("a day with no entry at all yields nothing")
    func missingDay() {
        // A salon that never configured Tuesday is closed on Tuesday, not open
        // with defaults — inventing hours would sell a slot nobody is there for.
        let hours = [WorkingHours(dayOfWeek: 4)]
        #expect(DayGrid.slots(for: Self.tuesdayNoonKabul, hours: hours,
                              slotMinutes: 30, now: Self.longAgo).isEmpty)
    }

    @Test("slots already past are not offered")
    func pastSlotsFiltered() {
        let hours = [WorkingHours(dayOfWeek: 3, openHour: 9, closeHour: 18)]
        // 15:00 Kabul on the same day: everything before it is gone.
        var c = DateComponents()
        c.year = 2026; c.month = 9; c.day = 8; c.hour = 15
        let now = DayGrid.kabulCalendar.date(from: c)!

        let slots = DayGrid.slots(for: Self.tuesdayNoonKabul, hours: hours,
                                  slotMinutes: 60, now: now)
        // 09:00–17:00 would be nine hourly slots; at 15:00 only 16:00 and
        // 17:00 remain. 15:00 itself is excluded — a slot starting this second
        // is not something a customer can travel to.
        #expect(slots.count == 2, "16:00 and 17:00")
        for ms in slots {
            #expect(Date(timeIntervalSince1970: Double(ms) / 1000) > now)
        }
    }

    @Test("a slot length below the floor is raised to it")
    func minimumSlotEnforced() {
        // A salon that stored 5 minutes would otherwise generate a grid nobody
        // can read. The server floors at 30 too.
        let hours = [WorkingHours(dayOfWeek: 3, openHour: 9, closeHour: 12)]
        let slots = DayGrid.slots(for: Self.tuesdayNoonKabul, hours: hours,
                                  slotMinutes: 5, now: Self.longAgo)
        #expect(slots.count == 6, "three hours at the 30-minute floor")
    }

    @Test("the default week opens Saturday and closes Friday")
    func defaultWeek() {
        let week = DayGrid.defaultWeek()
        #expect(week.count == 7)
        // Friday is 6 in this numbering, and is the day off.
        #expect(week.first { $0.dayOfWeek == 6 }?.isOpen == false)
        #expect(week.filter(\.isOpen).count == 6)
        #expect(DayGrid.hasBookableWeek(week, slotMinutes: 60))
    }

    @Test("a week that is open but has no room is not bookable")
    func unbookableWeek() {
        // Open 09:00–09:20 with 30-minute slots: technically open, and no slot
        // fits. The integrity sweep raises SALON_UNBOOKABLE for this shape.
        let hours = [WorkingHours(dayOfWeek: 3, openHour: 9, openMinute: 0,
                                  closeHour: 9, closeMinute: 20)]
        #expect(!DayGrid.hasBookableWeek(hours, slotMinutes: 30))
        #expect(DayGrid.slots(for: Self.tuesdayNoonKabul, hours: hours,
                              slotMinutes: 30, now: Self.longAgo).isEmpty)
    }
}
