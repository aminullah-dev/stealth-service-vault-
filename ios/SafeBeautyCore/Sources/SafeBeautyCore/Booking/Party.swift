import Foundation

/// A wedding party — a booking for several people at once.
///
/// A mirror of functions/lib/party.js, and it has to be: the server prices the
/// party from the guest list and computes how much of the salon's day it takes,
/// and a client with different arithmetic offers a start time the server then
/// refuses at checkout.
///
/// The model is the server's, and worth restating because it is not obvious: a
/// party is a block of the SALON's day, not one stylist's. Everyone works, the
/// wall-clock is the total work divided by the number of stylists, and the
/// booking occupies the whole salon rather than a chair. Nobody is assigned to
/// anybody — the salon decides who does what on the day, which is what it
/// already does.
public enum Party {

    /// A party larger than this is a venue booking, not a salon appointment.
    public static let maxGuests = 40
    /// Long enough for a name, short enough not to be a paragraph.
    public static let maxNameLength = 60
    /// No one guest has more services than a salon offers.
    public static let maxServicesPerGuest = 12

    public struct Guest: Identifiable, Hashable, Sendable {
        public var id = UUID()
        public var name: String = ""
        public var services: [String] = []

        public init(name: String = "", services: [String] = []) {
            self.name = name; self.services = services
        }

        /// A guest with nothing to do occupies no time, and the server drops
        /// her rather than letting her silently hold a chair.
        public var isBookable: Bool { !services.isEmpty }
    }

    /// Every service the party has asked for, guest by guest. The order matters
    /// only in that the server prices this same flattened list.
    public static func services(_ guests: [Guest]) -> [String] {
        guests.flatMap(\.services)
    }

    /// How long the party takes, in slots, with `staffCount` stylists working.
    ///
    /// Rounded UP at both steps, exactly as the server does: a party that
    /// overruns its window is a bride waiting in a salon that has already taken
    /// its next booking.
    public static func span(
        guests: [Guest], durations: [String: Int],
        slotMinutes: Int, staffCount: Int
    ) -> Int {
        let step = max(1, slotMinutes)
        let staff = max(1, staffCount)
        let names = services(guests)
        guard !names.isEmpty else { return 1 }

        var totalMinutes = 0
        for name in names {
            let d = durations[name]
            totalMinutes += (d.map { $0 > 0 } ?? false) ? durations[name]! : step
        }
        let wallClock = Int((Double(totalMinutes) / Double(staff)).rounded(.up))
        return max(1, Int((Double(wallClock) / Double(step)).rounded(.up)))
    }

    /// The list the server will accept, with the same caps applied here so a
    /// customer is told before she pays rather than after.
    public static func normalise(_ guests: [Guest], offering: Set<String>) -> [Guest] {
        guests.prefix(maxGuests).compactMap { guest in
            let services = Array(guest.services.filter(offering.contains)
                                     .prefix(maxServicesPerGuest))
            guard !services.isEmpty else { return nil }
            var out = guest
            out.name = String(guest.name.trimmingCharacters(in: .whitespacesAndNewlines)
                                  .prefix(maxNameLength))
            out.services = services
            return out
        }
    }
}
