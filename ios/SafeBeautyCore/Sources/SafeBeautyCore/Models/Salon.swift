import Foundation

public struct StaffMember: Codable, Hashable, Sendable, Identifiable {
    public var id: String = ""
    public var name: String = ""
    public var specialty: String = ""
    public var active: Bool = true

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decodeIfPresent(String.self, forKey: .id)).flatMap { $0 } ?? ""
        name = (try? c.decodeIfPresent(String.self, forKey: .name)).flatMap { $0 } ?? ""
        specialty = (try? c.decodeIfPresent(String.self, forKey: .specialty)).flatMap { $0 } ?? ""
        active = (try? c.decodeIfPresent(Bool.self, forKey: .active)).flatMap { $0 } ?? true
    }
}

public struct Salon: Codable, Identifiable, Hashable, Sendable {
    public var id: String = ""

    public var salonName: String = ""
    public var providerId: String = ""
    public var providerName: String = ""

    /// What the owner typed, e.g. "خیرخانه مینه ناحیه ۱۷".
    public var district: String = ""
    /// The resolved key, or "" when the address was ambiguous — which is normal
    /// and does not mean the salon is unusable.
    public var districtKey: String = ""
    public var areaKey: String = ""

    /// Derived server-side from the district, and the field city filters use.
    ///
    /// This was empty on half the live catalogue until 2026-09-04: an ambiguous
    /// district produced no district key, and the city was being derived from
    /// that key, so a salon whose address named two possible Kabul areas ended
    /// up in no city at all. A Firestore equality never matches an empty field,
    /// so it was invisible to anyone filtering. The derivation now falls back to
    /// the shared prefix of the candidates, and this list is where that shows.
    public var city: String = ""

    public var services: [String] = []
    public var pricePerService: [String: Int] = [:]
    public var categories: [String] = []

    public var rating: Double = 0
    public var sortRating: Double = 0
    public var confirmedCount: Int = 0
    public var minPrice: Int = 0

    public var isAvailable: Bool = false
    public var isVerified: Bool = false

    public var coverImageUrl: String = ""
    public var latitude: Double = 0
    public var longitude: Double = 0
    public var slotDurationMinutes: Int = 60
    public var staff: [StaffMember] = []
    public var workingHours: [WorkingHours] = []

    /// Days the owner has closed, as "yyyy-MM-dd" in KABUL local time — the
    /// form createPaymentSession compares against. Not decoding this was not a
    /// cosmetic gap: the grid rendered a blocked day as an ordinary full one
    /// and every booking on it was refused at the till.
    public var blockedDates: [String] = []

    /// How long each service takes, in minutes, when the salon has said.
    /// Absent means one slot, which is what most salons mean.
    public var durationPerService: [String: Int] = [:]

    /// The per-service breakdown for services with a gap in the middle — dye
    /// developing, where the chair is free but the booking is not over. The
    /// server lays the booking out from this; a client that ignores it computes
    /// a shorter footprint than the server does, which is the direction that
    /// gets refused at payment after she has chosen a time.
    public var serviceTiming: [String: Slots.Timing] = [:]

    /// True once the owner has actually pinned the salon.
    ///
    /// 0,0 is the Gulf of Guinea, and one production salon was sitting at
    /// 37.42,-122.08 — the Android emulator's default, which is Mountain View.
    /// Distance sorting has to treat "not pinned" as unknown rather than as a
    /// coordinate, or an unpinned salon sorts as though it were somewhere.
    public var hasLocation: Bool { latitude != 0 || longitude != 0 }

    /// The server writes NO_PRICE into `minPrice` when a salon has priced
    /// nothing, because Firestore drops documents that LACK an orderBy field —
    /// so "no price" has to be a number that sorts last rather than an absent
    /// one. It is a sort key, not an amount, and rendering it put "from
    /// 9,999,999 AFN" on the card of every salon that had not set a price yet.
    public static let noPriceSentinel = 9_999_999

    public var lowestPrice: Int {
        if minPrice > 0 && minPrice < Self.noPriceSentinel { return minPrice }
        let priced = pricePerService.values.filter { $0 > 0 }
        return priced.min() ?? 0
    }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case salonName, providerId, providerName
        case district, districtKey, areaKey, city
        case services, pricePerService, categories
        case rating, sortRating, confirmedCount, minPrice
        case isAvailable, isVerified
        case coverImageUrl, latitude, longitude, slotDurationMinutes, staff, workingHours
        case blockedDates, durationPerService, serviceTiming
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { (try? c.decodeIfPresent(String.self, forKey: k)).flatMap { $0 } ?? "" }
        func dbl(_ k: CodingKeys) -> Double { (try? c.decodeIfPresent(Double.self, forKey: k)).flatMap { $0 } ?? 0 }
        func int(_ k: CodingKeys, _ d: Int = 0) -> Int { (try? c.decodeIfPresent(Int.self, forKey: k)).flatMap { $0 } ?? d }
        func bool(_ k: CodingKeys) -> Bool { (try? c.decodeIfPresent(Bool.self, forKey: k)).flatMap { $0 } ?? false }

        salonName = str(.salonName); providerId = str(.providerId); providerName = str(.providerName)
        district = str(.district); districtKey = str(.districtKey)
        areaKey = str(.areaKey); city = str(.city)
        coverImageUrl = str(.coverImageUrl)
        services = (try? c.decodeIfPresent([String].self, forKey: .services)).flatMap { $0 } ?? []
        categories = (try? c.decodeIfPresent([String].self, forKey: .categories)).flatMap { $0 } ?? []
        pricePerService = (try? c.decodeIfPresent([String: Int].self, forKey: .pricePerService)).flatMap { $0 } ?? [:]
        staff = (try? c.decodeIfPresent([StaffMember].self, forKey: .staff)).flatMap { $0 } ?? []
        // A salon with no stored week gets the Afghan default rather than
        // an empty one, matching defaultWorkingHours() on the server. Stored
        // empty, it could never be booked at all.
        let storedHours = (try? c.decodeIfPresent([WorkingHours].self, forKey: .workingHours)).flatMap { $0 } ?? []
        workingHours = storedHours.isEmpty ? DayGrid.defaultWeek() : storedHours
        rating = dbl(.rating); sortRating = dbl(.sortRating)
        latitude = dbl(.latitude); longitude = dbl(.longitude)
        confirmedCount = int(.confirmedCount); minPrice = int(.minPrice)
        slotDurationMinutes = int(.slotDurationMinutes, 60)
        isAvailable = bool(.isAvailable); isVerified = bool(.isVerified)

        blockedDates = (try? c.decodeIfPresent([String].self, forKey: .blockedDates)).flatMap { $0 } ?? []
        durationPerService =
            (try? c.decodeIfPresent([String: Int].self, forKey: .durationPerService)).flatMap { $0 } ?? [:]
        // Decoded field by field rather than through a synthesised Codable:
        // a salon that has set the timing for one service and left another
        // half-written should lose that one entry, not the whole map.
        let rawTiming =
            (try? c.decodeIfPresent([String: [String: Int]].self, forKey: .serviceTiming)).flatMap { $0 } ?? [:]
        serviceTiming = rawTiming.reduce(into: [:]) { out, pair in
            out[pair.key] = Slots.Timing(
                activeBefore: pair.value["activeBefore"] ?? 0,
                processing:   pair.value["processing"]   ?? 0,
                activeAfter:  pair.value["activeAfter"]  ?? 0)
        }
    }
}
