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

    /// True once the owner has actually pinned the salon.
    ///
    /// 0,0 is the Gulf of Guinea, and one production salon was sitting at
    /// 37.42,-122.08 — the Android emulator's default, which is Mountain View.
    /// Distance sorting has to treat "not pinned" as unknown rather than as a
    /// coordinate, or an unpinned salon sorts as though it were somewhere.
    public var hasLocation: Bool { latitude != 0 || longitude != 0 }

    public var lowestPrice: Int {
        if minPrice > 0 { return minPrice }
        return pricePerService.values.min() ?? 0
    }

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case salonName, providerId, providerName
        case district, districtKey, areaKey, city
        case services, pricePerService, categories
        case rating, sortRating, confirmedCount, minPrice
        case isAvailable, isVerified
        case coverImageUrl, latitude, longitude, slotDurationMinutes, staff
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
        rating = dbl(.rating); sortRating = dbl(.sortRating)
        latitude = dbl(.latitude); longitude = dbl(.longitude)
        confirmedCount = int(.confirmedCount); minPrice = int(.minPrice)
        slotDurationMinutes = int(.slotDurationMinutes, 60)
        isAvailable = bool(.isAvailable); isVerified = bool(.isVerified)
    }
}
