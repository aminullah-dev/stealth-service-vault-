import Testing
@testable import SafeBeautyCore

@Suite("Recommendations rank the way Android ranks")
struct RecommendationsTests {

    private func salon(_ id: String, services: [String],
                       district: String = "", rating: Double = 0) -> Salon {
        var s = Salon()
        s.id = id; s.services = services; s.district = district; s.rating = rating
        return s
    }

    private func booking(_ salonId: String, _ service: String) -> Appointment {
        var a = Appointment()
        a.salonId = salonId; a.serviceName = service
        return a
    }

    @Test("nothing is recommended to someone who has never booked")
    func needsHistory() {
        #expect(Recommendations.rank(salons: [salon("a", services: ["Cut"])],
                                     history: []).isEmpty)
    }

    @Test("a service she books repeatedly outranks a rating bonus")
    func serviceBeatsRating() {
        let salons = [salon("a", services: ["Cut"]), salon("b", services: ["Other"], rating: 5)]
        let history = [booking("z", "Cut"), booking("z", "Cut")]
        let ranked = Recommendations.rank(salons: salons, history: history)
        // Two bookings of Cut = 4; a 5.0 rating alone = 1.
        #expect(ranked.first?.id == "a")
        #expect(ranked.count == 2)
    }

    @Test("a salon matching nothing is left out, not padded in")
    func zeroScoreExcluded() {
        let salons = [salon("a", services: ["Cut"]), salon("b", services: ["Other"])]
        let ranked = Recommendations.rank(salons: salons, history: [booking("z", "Cut")])
        #expect(ranked.map(\.id) == ["a"])
    }

    @Test("her usual neighbourhood breaks a tie")
    func districtBreaksTie() {
        let salons = [salon("a", services: ["Cut"], district: "KBL_D9_Makroryan"),
                      salon("b", services: ["Cut"], district: "KBL_D17"),
                      salon("home", services: [], district: "KBL_D17")]
        // She has been to "home", which is in D17 — so D17 is her district.
        let ranked = Recommendations.rank(salons: salons, history: [booking("home", "Cut")])
        #expect(ranked.first?.id == "b")
    }

    @Test("at most five, however many match")
    func capped() {
        let salons = (0..<9).map { salon("s\($0)", services: ["Cut"]) }
        #expect(Recommendations.rank(salons: salons,
                                     history: [booking("z", "Cut")]).count == 5)
    }
}
