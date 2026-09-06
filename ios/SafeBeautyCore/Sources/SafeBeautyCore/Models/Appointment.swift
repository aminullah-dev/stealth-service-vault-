import Foundation

/// A service as it was actually booked, with the price agreed at the time.
///
/// This is the shape the SERVER writes — `[{name, price}]`, from
/// `resolveServicesTotal` in functions/lib/money.js — not a list of strings.
/// Android could not model it: declaring `services: List<String>` there did not
/// make Firestore skip the field, it made CustomClassMapper walk into the array
/// and throw converting a HashMap to a String, out of a snapshot listener on the
/// main thread. Every customer with a single booking lost the screen. The fix
/// there was to delete the field and recover the names by splitting
/// `serviceName`.
///
/// Swift can just model it correctly, so it does. The prices are real
/// information and the app has been doing without them.
public struct BookedService: Codable, Hashable, Sendable {
    public let name: String
    public let price: Int

    public init(name: String, price: Int) {
        self.name = name
        self.price = price
    }

    // Price has been written as both a number and, in older documents, absent.
    // A missing price is 0 rather than a decode failure — see the note on
    // Appointment about what a throw costs here.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        price = (try? c.decode(Int.self, forKey: .price))
            ?? Int((try? c.decode(Double.self, forKey: .price)) ?? 0)
    }
}

public struct Appointment: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""

    public var bookingCode: String = ""
    public var customerId: String = ""
    public var customerName: String = ""
    public var customerPhone: String = ""
    public var salonId: String = ""
    public var salonName: String = ""

    /// The comma-joined names, which is what every booking has ever had —
    /// including those written before `services` existed.
    public var serviceName: String = ""
    /// The itemised form. Empty for older bookings, which is why `serviceName`
    /// remains the thing to display.
    public var services: [BookedService] = []

    public var staffId: String = ""
    public var staffName: String = ""
    public var appointmentDate: Int64 = 0
    public var status: AppointmentStatus = .pending
    public var paymentMethod: String = ""
    public var createdAt: Int64 = 0
    public var notes: String = ""

    public var slotsCount: Int = 1
    public var busyOffsets: [Int] = []
    /// Set by submitReview (content.js). Without it the button was offered
    /// again on a booking already reviewed, and the server refused it.
    public var reviewed: Bool = false
    public var isParty: Bool = false
    public var partySize: Int = 0

    public var date: Date { Date(timeIntervalSince1970: Double(appointmentDate) / 1000) }

    /// The service names to show, whichever era the booking is from.
    ///
    /// Prefers the itemised list and falls back to splitting `serviceName` on
    /// both the Arabic comma and the Latin one, because both are in the data.
    public var serviceNames: [String] {
        if !services.isEmpty { return services.map(\.name) }
        return serviceName
            .split(whereSeparator: { $0 == "،" || $0 == "," })
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    public var total: Int { services.reduce(0) { $0 + $1.price } }

    public init() {}

    // Written out rather than synthesised, because Swift's generated decoder
    // does NOT fall back to a property's default value — it requires every key
    // to be present. Firestore documents omit fields constantly: `services`
    // did not exist when the older bookings were written, `staffId` is absent
    // on a solo salon, and `id` is the document name and never in the data at
    // all. With the synthesised decoder, any one of those absences fails the
    // whole document, which is the same blank-screen outcome this app already
    // had once for a different reason.
    //
    // So every field is decodeIfPresent with the default beside it, and a
    // missing field is what it should be: a default, not a failure.
    private enum CodingKeys: String, CodingKey {
        case reviewed
        case bookingCode, customerId, customerName, customerPhone
        case salonId, salonName, serviceName, services
        case staffId, staffName, appointmentDate, status, paymentMethod
        case createdAt, notes, slotsCount, busyOffsets, isParty, partySize
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)) .flatMap { $0 } ?? "" }
        func i64(_ k: CodingKeys) -> Int64 { (try? c.decodeIfPresent(Int64.self, forKey: k)).flatMap { $0 } ?? 0 }
        func int(_ k: CodingKeys, _ d: Int = 0) -> Int { (try? c.decodeIfPresent(Int.self, forKey: k)).flatMap { $0 } ?? d }
        func bool(_ k: CodingKeys) -> Bool { (try? c.decodeIfPresent(Bool.self, forKey: k)).flatMap { $0 } ?? false }

        bookingCode = str(.bookingCode)
        customerId = str(.customerId); customerName = str(.customerName); customerPhone = str(.customerPhone)
        salonId = str(.salonId); salonName = str(.salonName)
        serviceName = str(.serviceName)
        services = (try? c.decodeIfPresent([BookedService].self, forKey: .services)).flatMap { $0 } ?? []
        staffId = str(.staffId); staffName = str(.staffName)
        paymentMethod = str(.paymentMethod); notes = str(.notes)
        createdAt = i64(.createdAt)
        slotsCount = int(.slotsCount, 1)
        partySize = int(.partySize)
        reviewed = (try? c.decodeIfPresent(Bool.self, forKey: .reviewed)).flatMap { $0 } ?? false
        isParty = bool(.isParty)
        busyOffsets = (try? c.decodeIfPresent([Int].self, forKey: .busyOffsets)).flatMap { $0 } ?? []
        status = (try? c.decodeIfPresent(AppointmentStatus.self, forKey: .status)).flatMap { $0 } ?? .unknown

        // appointmentDate is NOT defaulted away. A booking with no time is not
        // a booking, and silently showing it at the epoch would put it at the
        // top of a sorted list in 1970 rather than saying anything is wrong.
        guard let when = try c.decodeIfPresent(Int64.self, forKey: .appointmentDate) else {
            throw DecodingError.keyNotFound(CodingKeys.appointmentDate, .init(
                codingPath: c.codingPath,
                debugDescription: "An appointment must have a time; this document has none."))
        }
        appointmentDate = when
    }
}

/// The statuses the server writes, plus a case for one it has not written yet.
///
/// An unrecognised status decodes to `.unknown` rather than throwing. A backend
/// that adds a status should not be able to blank out the bookings screen of
/// every phone running an older build — which is precisely the shape of the
/// crash this app already had once.
public enum AppointmentStatus: String, Codable, Hashable, Sendable {
    case awaitingPayment = "AWAITING_PAYMENT"
    case pending = "PENDING"
    case confirmed = "CONFIRMED"
    case completed = "COMPLETED"
    case cancelled = "CANCELLED"
    case unknown

    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = AppointmentStatus(rawValue: raw) ?? .unknown
    }

    /// Still ahead of the customer: worth showing, worth reminding about.
    public var isLive: Bool {
        self == .awaitingPayment || self == .pending || self == .confirmed
    }
}
