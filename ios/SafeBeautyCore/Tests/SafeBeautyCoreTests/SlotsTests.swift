import Testing
import Foundation
@testable import SafeBeautyCore

/// Every expected value here was produced by running `functions/lib/slots.js`,
/// not by reasoning about what the answer should be.
///
/// The server re-checks each booking with its own `hasSlotConflict`, so the
/// cost of disagreement is asymmetric and both directions are bad: a client
/// grid that is wider than the server's hides bookable times, and one that is
/// narrower offers times that get refused at payment, after she has chosen a
/// slot and started paying for it.
@Suite("Slot maths parity with functions/lib/slots.js")
struct SlotsTests {

    // MARK: serviceLayout

    @Test("no services still occupies one slot")
    func emptyServices() {
        let l = Slots.serviceLayout(serviceNames: [], slotMinutes: 30)
        #expect(l == Slots.Layout(span: 1, busyOffsets: [0]))
    }

    @Test("a service with no timing takes one slot")
    func untimedService() {
        let l = Slots.serviceLayout(serviceNames: ["cut"], slotMinutes: 30)
        #expect(l == Slots.Layout(span: 1, busyOffsets: [0]))
    }

    @Test("a duration rounds up to whole slots")
    func durationRoundsUp() {
        // 60 minutes at 30 = exactly two.
        #expect(Slots.serviceLayout(serviceNames: ["cut"],
                                    durationPerService: ["cut": 60], slotMinutes: 30)
                == Slots.Layout(span: 2, busyOffsets: [0, 1]))
        // 45 minutes at 30 is also two: a stylist mid-slot still owns the chair.
        #expect(Slots.serviceLayout(serviceNames: ["cut"],
                                    durationPerService: ["cut": 45], slotMinutes: 30)
                == Slots.Layout(span: 2, busyOffsets: [0, 1]))
    }

    /// The rule the whole file exists for.
    @Test("processing time is free for someone else, active time is not")
    func processingFreesTheChair() {
        // 30 active, 60 developing, 15 active. Four slots of wall clock, but the
        // chair is only taken for the first and the last: slots 1 and 2 are
        // bookable by another customer while the dye develops.
        let l = Slots.serviceLayout(
            serviceNames: ["dye"],
            timings: ["dye": .init(activeBefore: 30, processing: 60, activeAfter: 15)],
            slotMinutes: 30)
        #expect(l == Slots.Layout(span: 4, busyOffsets: [0, 3]))
    }

    @Test("processing rounds down, active rounds up")
    func roundingDirection() {
        // 45 minutes of processing at a 30-minute step is ONE free slot, not
        // two. Rounding it up would blank a slot somebody could have had.
        let l = Slots.serviceLayout(
            serviceNames: ["dye"],
            timings: ["dye": .init(activeBefore: 30, processing: 45, activeAfter: 0)],
            slotMinutes: 30)
        #expect(l == Slots.Layout(span: 2, busyOffsets: [0]))
    }

    /// The guard that stops a booking colliding with itself.
    @Test("a purely unattended service still claims one slot")
    func pureProcessingStillBlocksOne() {
        // No attended time at all: both counts round to zero, and without the
        // guard the booking would occupy the chair for NO slots — bookable over
        // by anyone, including itself.
        let l = Slots.serviceLayout(
            serviceNames: ["dye"],
            timings: ["dye": .init(activeBefore: 0, processing: 60, activeAfter: 0)],
            slotMinutes: 30)
        #expect(l == Slots.Layout(span: 3, busyOffsets: [0]))
        #expect(!l.busyOffsets.isEmpty, "a booking must always block something")
    }

    @Test("services stack, and a gap in one is usable by the next")
    func servicesStack() {
        let l = Slots.serviceLayout(
            serviceNames: ["cut", "dye"],
            timings: ["dye": .init(activeBefore: 30, processing: 60, activeAfter: 30)],
            durationPerService: ["cut": 30],
            slotMinutes: 30)
        #expect(l == Slots.Layout(span: 5, busyOffsets: [0, 1, 4]))
    }

    // MARK: slotsFor

    @Test("a booking occupies its span from its start")
    func slotsFromStart() {
        #expect(Slots.slotsFor(startMillis: 1000, slotsCount: 1, slotMinutes: 30) == [1000])
        #expect(Slots.slotsFor(startMillis: 1000, slotsCount: 3, slotMinutes: 30)
                == [1000, 1_801_000, 3_601_000])
    }

    @Test("stored busyOffsets narrow what is blocked")
    func storedOffsetsRespected() {
        #expect(Slots.slotsFor(startMillis: 1000, slotsCount: 3,
                               storedBusyOffsets: [0, 2], slotMinutes: 30)
                == [1000, 3_601_000])
    }

    /// Both fallbacks go the safe way: block more, never less.
    @Test("nonsense offsets block the whole span rather than nothing")
    func badOffsetsFallBackToEverything() {
        // 9 is past the end and -1 is impossible, so nothing survives the
        // filter. Blocking nothing would let this booking be booked over.
        #expect(Slots.slotsFor(startMillis: 1000, slotsCount: 3,
                               storedBusyOffsets: [9, -1], slotMinutes: 30)
                == [1000, 1_801_000, 3_601_000])
    }

    @Test("an older booking falls back to its service count")
    func fallsBackToServiceCount() {
        // Written before slotsCount existed.
        #expect(Slots.slotsFor(startMillis: 1000, slotsCount: nil,
                               servicesCount: 2, slotMinutes: 60)
                == [1000, 3_601_000])
    }

    // MARK: conflicts

    private func appointment(
        id: String = "x", start: Int64, span: Int = 1, staff: String = "",
        party: Bool = false, status: AppointmentStatus = .confirmed,
        offsets: [Int] = []
    ) -> Appointment {
        var a = Appointment()
        a.id = id; a.appointmentDate = start; a.slotsCount = span
        a.staffId = staff; a.isParty = party; a.status = status
        a.busyOffsets = offsets
        return a
    }

    @Test("the same chair at the same time conflicts")
    func sameChairConflicts() {
        let existing = [appointment(start: 1000, staff: "s1")]
        #expect(Slots.hasConflict(existing: existing, requestedStart: 1000,
                                  requestedOffsets: [0], staffId: "s1", slotMinutes: 30))
    }

    @Test("a different chair at the same time does not")
    func differentChairIsFine() {
        // Two stylists work at once. Treating this as a conflict would halve
        // the salon's capacity.
        let existing = [appointment(start: 1000, staff: "s1")]
        #expect(!Slots.hasConflict(existing: existing, requestedStart: 1000,
                                   requestedOffsets: [0], staffId: "s2", slotMinutes: 30))
    }

    @Test("a party takes the whole salon, from either side")
    func partyBlocksEveryChair() {
        let party = [appointment(start: 1000, staff: "s1", party: true)]
        #expect(Slots.hasConflict(existing: party, requestedStart: 1000,
                                  requestedOffsets: [0], staffId: "s9", slotMinutes: 30),
                "an existing party blocks a different chair")

        let single = [appointment(start: 1000, staff: "s1")]
        #expect(Slots.hasConflict(existing: single, requestedStart: 1000,
                                  requestedOffsets: [0], staffId: "s9",
                                  slotMinutes: 30, requestIsParty: true),
                "a requested party is blocked by any existing booking")
    }

    @Test("a cancelled booking never conflicts")
    func cancelledIsFree() {
        // The document still exists. Counting it would slowly make a busy salon
        // unbookable as cancellations accumulate.
        let existing = [appointment(start: 1000, staff: "s1", status: .cancelled)]
        #expect(!Slots.hasConflict(existing: existing, requestedStart: 1000,
                                   requestedOffsets: [0], staffId: "s1", slotMinutes: 30))
    }

    @Test("rescheduling does not conflict with itself")
    func excludeSelf() {
        let existing = [appointment(id: "a1", start: 1000, staff: "s1")]
        #expect(!Slots.hasConflict(existing: existing, requestedStart: 1000,
                                   requestedOffsets: [0], staffId: "s1",
                                   slotMinutes: 30, excludeId: "a1"))
    }

    @Test("a gap inside a long booking is bookable")
    func gapIsBookable() {
        // The dye case: slots 1 and 2 are free while it develops, and someone
        // else may have them. This is the payoff for the floor/ceil asymmetry —
        // if it were wrong, the salon would lose those two slots every time.
        let dye = appointment(id: "d", start: 1000, span: 4, staff: "s1", offsets: [0, 3])
        let step: Int64 = 30 * 60_000
        #expect(!Slots.hasConflict(existing: [dye], requestedStart: 1000 + step,
                                   requestedOffsets: [0], staffId: "s1", slotMinutes: 30),
                "slot 1 is free during processing")
        #expect(Slots.hasConflict(existing: [dye], requestedStart: 1000 + 3 * step,
                                  requestedOffsets: [0], staffId: "s1", slotMinutes: 30),
                "slot 3 is the attended finish and is taken")
    }
}

/// Whether the review button is offered at all.
///
/// Mirrors what submitReview enforces, so the control only appears where the
/// callable would accept it. A button the server refuses teaches a customer not
/// to trust the buttons.
@Suite("Review eligibility mirrors submitReview")
struct ReviewEligibilityTests {

    private func booking(_ status: AppointmentStatus, daysFromNow: Double) -> Appointment {
        var a = Appointment()
        a.status = status
        a.appointmentDate = Int64(Date().addingTimeInterval(daysFromNow * 86400)
                                    .timeIntervalSince1970 * 1000)
        return a
    }

    @Test("a visit that has happened can be reviewed")
    func pastVisitIsReviewable() {
        #expect(ReviewEligibility.canReview(booking(.completed, daysFromNow: -1)))
        // CONFIRMED and past counts too: the visit happened even if nothing has
        // marked it complete yet, and the server accepts it.
        #expect(ReviewEligibility.canReview(booking(.confirmed, daysFromNow: -1)))
    }

    @Test("a visit that has not happened yet cannot")
    func futureVisitIsNot() {
        #expect(!ReviewEligibility.canReview(booking(.confirmed, daysFromNow: 1)))
        #expect(!ReviewEligibility.canReview(booking(.pending, daysFromNow: 1)))
    }

    @Test("a cancelled booking is never reviewable")
    func cancelledIsNot() {
        // She did not go. Reviewing it would rate a visit that never happened,
        // and the server refuses it.
        #expect(!ReviewEligibility.canReview(booking(.cancelled, daysFromNow: -1)))
    }

    @Test("a booking already reviewed is not offered again")
    func alreadyReviewedIsNot() {
        // submitReview stamps `reviewed` and refuses a second review, so the
        // button was being offered on something the server would turn down.
        var a = booking(.completed, daysFromNow: -1)
        #expect(ReviewEligibility.canReview(a))
        a.reviewed = true
        #expect(!ReviewEligibility.canReview(a))
    }

    @Test("an unpaid booking in the past is not reviewable")
    func awaitingPaymentIsNot() {
        // AWAITING_PAYMENT that aged out is an abandoned checkout, not a visit.
        #expect(!ReviewEligibility.canReview(booking(.awaitingPayment, daysFromNow: -1)))
    }
}

/// The grid the customer actually sees, against the rules the server enforces.
///
/// Every expectation here mirrors a specific server check: AFTER_CLOSING in
/// `functions/lib/hours.js` (`minuteOfDay + span * step > close`) and
/// SALON_CLOSED in `createPaymentSession`. A grid that is wider than either
/// offers a time that is refused at payment, after she has chosen it.
@Suite("The day grid refuses what the server would refuse")
struct DayGridBoundsTests {

    private func week(open: Int, close: Int) -> [WorkingHours] {
        (1...7).map { WorkingHours(dayOfWeek: $0, openHour: open, closeHour: close) }
    }

    /// A fixed day well in the past for `now`, so nothing is filtered as gone.
    private var day: Date { Date(timeIntervalSince1970: 1_800_000_000) }
    private var longAgo: Date { Date(timeIntervalSince1970: 0) }

    @Test("the whole booking must end by closing, not just its first slot")
    func spanMustFit() {
        let hours = week(open: 9, close: 18)
        let one = DayGrid.slots(for: day, hours: hours, slotMinutes: 30,
                                span: 1, now: longAgo)
        let three = DayGrid.slots(for: day, hours: hours, slotMinutes: 30,
                                  span: 3, now: longAgo)
        // 09:00–18:00 at 30 minutes is 18 starts for a one-slot booking. A
        // three-slot booking cannot start in the last two of them.
        #expect(one.count == 18)
        #expect(three.count == 16)
        #expect(three.last! < one.last!, "the 90-minute booking cannot take 17:30")
    }

    @Test("a span of one is unchanged, so nothing shifts for a simple booking")
    func spanOneIsTheOldBehaviour() {
        let hours = week(open: 9, close: 18)
        #expect(DayGrid.slots(for: day, hours: hours, slotMinutes: 60, now: longAgo)
                == DayGrid.slots(for: day, hours: hours, slotMinutes: 60,
                                 span: 1, now: longAgo))
    }

    @Test("a booking too long for the whole day is offered no times at all")
    func nothingFitsAtAll() {
        // Better than offering a start the server refuses: she is told the day
        // has nothing rather than choosing a time and being turned away.
        let hours = week(open: 9, close: 12)
        #expect(DayGrid.slots(for: day, hours: hours, slotMinutes: 60,
                              span: 4, now: longAgo).isEmpty)
    }

    @Test("a blocked day is empty, whatever the working hours say")
    func blockedDayIsEmpty() {
        let hours = week(open: 9, close: 18)
        let key = DayGrid.dayKey(day)
        #expect(!DayGrid.slots(for: day, hours: hours, slotMinutes: 30, now: longAgo).isEmpty)
        #expect(DayGrid.slots(for: day, hours: hours, slotMinutes: 30,
                              blockedDates: [key], now: longAgo).isEmpty)
    }

    @Test("another day's block does not close this one")
    func onlyTheBlockedDay() {
        let hours = week(open: 9, close: 18)
        #expect(!DayGrid.slots(for: day, hours: hours, slotMinutes: 30,
                               blockedDates: ["1999-01-01"], now: longAgo).isEmpty)
    }

    @Test("the day key is Kabul's, not the phone's")
    func dayKeyIsKabul() {
        // 2027-01-15 20:00 UTC is already the 16th in Kabul (UTC+4:30). A phone
        // in Toronto formatting locally would call it the 15th and miss a block
        // the salon set for the 16th.
        let d = Date(timeIntervalSince1970: 1_800_216_000)
        let kabul = DateFormatter()
        kabul.timeZone = DayGrid.kabul
        kabul.locale = Locale(identifier: "en_US_POSIX")
        kabul.dateFormat = "yyyy-MM-dd"
        #expect(DayGrid.dayKey(d) == kabul.string(from: d))
    }
}
