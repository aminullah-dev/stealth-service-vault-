import Testing
@testable import SafeBeautyCore

/// The client must agree with functions/lib/party.js about how much of a
/// salon's day a party takes, or it offers a start time the server refuses
/// after she has chosen it and begun paying.
@Suite("Party maths matches the server")
struct PartyTests {

    private func guest(_ name: String, _ services: [String]) -> Party.Guest {
        Party.Guest(name: name, services: services)
    }

    @Test("the wall clock shrinks with the number of stylists")
    func spanDividesByStaff() {
        let guests = [guest("A", ["Cut"]), guest("B", ["Cut"]), guest("C", ["Cut"])]
        let durations = ["Cut": 60]
        // Three hours of work: three hours alone, one hour with three stylists.
        #expect(Party.span(guests: guests, durations: durations,
                           slotMinutes: 60, staffCount: 1) == 3)
        #expect(Party.span(guests: guests, durations: durations,
                           slotMinutes: 60, staffCount: 3) == 1)
        // Two stylists leave a remainder, and it rounds UP — a party that
        // overruns is a bride waiting while the salon serves its next booking.
        #expect(Party.span(guests: guests, durations: durations,
                           slotMinutes: 60, staffCount: 2) == 2)
    }

    @Test("a service with no stored duration costs one slot")
    func unknownDurationFallsBackToOneSlot() {
        #expect(Party.span(guests: [guest("A", ["Mystery"])], durations: [:],
                           slotMinutes: 30, staffCount: 1) == 1)
        #expect(Party.span(guests: [guest("A", ["Mystery", "Mystery"])], durations: [:],
                           slotMinutes: 30, staffCount: 1) == 2)
    }

    @Test("an empty party still occupies a slot rather than nothing")
    func emptyIsOneSlot() {
        #expect(Party.span(guests: [], durations: [:],
                           slotMinutes: 60, staffCount: 3) == 1)
    }

    @Test("normalising drops what the salon does not offer, and who has nothing left")
    func normalising() {
        let offering: Set<String> = ["Cut", "Colour"]
        let result = Party.normalise([
            guest("Zahra", ["Cut", "Massage"]),
            guest("Nobody", ["Massage"]),
            guest("  a very long name that runs past the sixty character limit for sure  ",
                  ["Colour"]),
        ], offering: offering)
        #expect(result.count == 2)
        #expect(result[0].services == ["Cut"])
        #expect(result[1].name.count <= Party.maxNameLength)
    }
}
