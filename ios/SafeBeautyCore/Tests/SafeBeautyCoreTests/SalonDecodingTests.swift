import Testing
import Foundation
@testable import SafeBeautyCore

/// Decoded against the two salons that are actually in production, not against
/// a fixture written to match the model.
///
/// They differ in the ways that matter: one has a staff array and one has no
/// `staff` key at all, one has an empty `districtKey` because its address is
/// genuinely ambiguous, and their price maps use Dari service names as keys.
/// Every one of those is a shape a hand-written fixture would have smoothed
/// over.
@Suite("Salon decoding against the live catalogue")
struct SalonDecodingTests {

    /// "سالن آرایشی عروس خانم" — no staff key, ambiguous district, Dari keys.
    static var brideSalon: [String: Any] { [
        "id": "a1mQurrJNPfUFyFBQSE8",
        "salonName": "سالن آرایشی عروس خانم",
        "providerId": "417f475b-bf28-4789-9656-c23e2c502760",
        "providerName": "مریم احمدزی",
        "district": "خیرخانه مینه ناحیه 17",
        "districtKey": "",                  // ambiguous, and that is allowed
        "areaKey": "",
        "city": "KABUL",                    // derived from the shared prefix
        "services": ["ناخن", "ارایش"],
        "pricePerService": ["ناخن": 75, "ارایش": 200],
        "categories": ["Makeup", "Nails"],
        "isAvailable": true,
        "isVerified": true,
        "rating": 0,
        "sortRating": 0,
        "minPrice": 75,
        "confirmedCount": 0,
        "slotDurationMinutes": 60,
        // no "staff" key at all
        // no latitude/longitude at all
    ] }

    /// "Shaghayeq Ha" — has staff, a resolved district, and was the salon whose
    /// coordinates were the Android emulator's default until they were cleared.
    static var shaghayeq: [String: Any] { [
        "id": "rwbjNnfWDU1xeBT5Y2qP",
        "salonName": "Shaghayeq Ha",
        "providerId": "4f0fa227-1382-4218-999c-6b7359686060",
        "providerName": "Navida",
        "district": "D9_Makroryan",
        "districtKey": "KBL_D9_Makroryan",
        "city": "KABUL",
        "services": ["mo"],
        "pricePerService": ["mo": 80],
        "categories": [],
        "isAvailable": true,
        "isVerified": true,
        "rating": 0,
        "sortRating": 0,
        "minPrice": 80,
        "confirmedCount": 11,
        "slotDurationMinutes": 90,
        "latitude": 0,
        "longitude": 0,
        "staff": [["id": "183f6c4a", "name": "Maryam", "specialty": "Nakhon", "active": true]],
    ] }

    @Test("both live salons decode")
    func bothDecode() throws {
        let a = try DocumentDecoding.decode(Salon.self, from: Self.brideSalon)
        #expect(a.salonName == "سالن آرایشی عروس خانم")
        #expect(a.pricePerService["ناخن"] == 75)
        #expect(a.lowestPrice == 75)
        #expect(a.staff.isEmpty, "a missing staff key is not a failure")

        let b = try DocumentDecoding.decode(Salon.self, from: Self.shaghayeq)
        #expect(b.salonName == "Shaghayeq Ha")
        #expect(b.staff.count == 1)
        #expect(b.staff.first?.name == "Maryam")
        #expect(b.slotDurationMinutes == 90)
    }

    /// The bug fixed on 2026-09-04, from the client's side.
    ///
    /// An ambiguous district resolves to no district key — correctly, since the
    /// two candidates are different points on a map. The city used to be
    /// derived from that key and so came out empty, and a Firestore equality
    /// never matches an empty field, so this salon was absent from every
    /// city-filtered query while looking perfectly healthy in the console.
    @Test("an ambiguous district still carries a city")
    func ambiguousDistrictStillHasCity() throws {
        let a = try DocumentDecoding.decode(Salon.self, from: Self.brideSalon)
        #expect(a.districtKey.isEmpty, "the district is genuinely ambiguous")
        #expect(a.city == "KABUL", "but the city was never in doubt")
        // The address the owner typed is what the customer sees, whether or not
        // the key resolved.
        #expect(a.district == "خیرخانه مینه ناحیه 17")
    }

    /// 0,0 is a real coordinate — the Gulf of Guinea — so it cannot mean
    /// "unset" by accident.
    @Test("an unpinned salon reports no location rather than a wrong one")
    func unpinnedLocation() throws {
        let missing = try DocumentDecoding.decode(Salon.self, from: Self.brideSalon)
        #expect(!missing.hasLocation, "no coordinates at all")

        let zeroed = try DocumentDecoding.decode(Salon.self, from: Self.shaghayeq)
        #expect(!zeroed.hasLocation, "0,0 means unpinned, not the Gulf of Guinea")

        // A salon that IS pinned says so.
        var pinned = Self.shaghayeq
        pinned["latitude"] = 34.5553
        pinned["longitude"] = 69.2075
        #expect(try DocumentDecoding.decode(Salon.self, from: pinned).hasLocation)
    }

    @Test("lowestPrice falls back to the price map when minPrice is unwritten")
    func priceFallback() throws {
        // minPrice is derived, so a salon the derivation has not reached yet
        // would otherwise show no price at all.
        var noMin = Self.brideSalon
        noMin.removeValue(forKey: "minPrice")
        let a = try DocumentDecoding.decode(Salon.self, from: noMin)
        #expect(a.lowestPrice == 75, "the cheapest service in the map")
    }

    /// A malformed field costs that field, not the salon.
    ///
    /// I first wrote this expecting the document to fail and be reported, which
    /// is what Appointment does when `appointmentDate` is missing. Salon is
    /// deliberately stronger: every field falls back to its default, so nothing
    /// in a salon document can make the salon disappear from the list.
    ///
    /// That is the right trade here and it is not the same trade as
    /// Appointment's. A booking with no time is not a booking and must be
    /// reported. A salon with an unreadable price map is still a real salon a
    /// customer can find, call and visit — showing it without a price is far
    /// better than hiding a business because one of its fields is malformed.
    @Test("a malformed field degrades that field, and never loses the salon")
    func malformedFieldDegradesGracefully() throws {
        var broken = Self.shaghayeq
        broken["pricePerService"] = "not a map"
        broken["staff"] = "not an array"
        broken["confirmedCount"] = ["nope": true]

        let salon = try DocumentDecoding.decode(Salon.self, from: broken)
        #expect(salon.salonName == "Shaghayeq Ha", "the salon is still findable")
        #expect(salon.isAvailable, "and still bookable")
        #expect(salon.pricePerService.isEmpty, "the bad field is empty, not fatal")
        #expect(salon.staff.isEmpty)
        #expect(salon.confirmedCount == 0)
        // minPrice was still readable, so a price still shows.
        #expect(salon.lowestPrice == 80)
    }

    @Test("a whole list survives a salon with several malformed fields")
    func listSurvivesBadSalon() {
        var broken = Self.shaghayeq
        broken["pricePerService"] = "not a map"
        let result = DocumentDecoding.decodeAll(
            Salon.self,
            documents: [(id: "a", data: Self.brideSalon), (id: "b", data: broken)],
            assigningID: { $0.id = $1 }
        )
        #expect(result.values.count == 2, "both salons render")
        #expect(result.failures.isEmpty)
        #expect(result.values.map(\.id).sorted() == ["a", "b"])
    }
}

/// Offers, decoded against the one that is actually in production.
@Suite("Offer liveness")
struct SalonOfferTests {

    private func offer(active: Bool, expiresAt: Int64) throws -> SalonOffer {
        try DocumentDecoding.decode(SalonOffer.self, from: [
            "salonId": "s1", "salonName": "S", "title": "Nakhon 20% Off",
            "active": active, "expiresAt": expiresAt,
            "discountPercent": 0, "discountAmount": 0,
        ])
    }

    @Test("no expiry means no expiry, not expired in 1970")
    func zeroExpiryIsForever() throws {
        // The live offer has expiresAt 0. Reading that as a timestamp would
        // hide every offer that never set one — which is most of them.
        #expect(try offer(active: true, expiresAt: 0).isLive())
    }

    @Test("an inactive offer is never live, whatever its expiry")
    func inactiveIsNeverLive() throws {
        #expect(try !offer(active: false, expiresAt: 0).isLive())
    }

    @Test("a past expiry ends it")
    func pastExpiry() throws {
        let past = Int64(Date().addingTimeInterval(-3600).timeIntervalSince1970 * 1000)
        #expect(try !offer(active: true, expiresAt: past).isLive())
        let future = Int64(Date().addingTimeInterval(3600).timeIntervalSince1970 * 1000)
        #expect(try offer(active: true, expiresAt: future).isLive())
    }

    /// The live offer's discount is in its title, not its numbers.
    @Test("an offer with no numeric discount is still worth showing")
    func textOnlyDiscount() throws {
        let o = try offer(active: true, expiresAt: 0)
        #expect(o.title == "Nakhon 20% Off")
        #expect(!o.hasNumericDiscount, "both discount fields are zero in production")
        #expect(o.isLive(), "and it is still a real, live offer")
    }
}
