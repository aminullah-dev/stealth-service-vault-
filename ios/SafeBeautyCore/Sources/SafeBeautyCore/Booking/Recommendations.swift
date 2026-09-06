import Foundation

/// Salons scored against what this customer has actually booked before.
///
/// A translation of `recommendedSalons` in DashboardViewModel, kept as a pure
/// function so the ranking is testable without a phone. Deliberately simple:
/// it is a ranking of candidates already on screen, not a recommender system.
public enum Recommendations {

    /// Requires at least one past booking, and returns at most five.
    ///
    /// Scored from the customer's own history: a service she has booked before
    /// is worth two each time she booked it, being in the neighbourhood she
    /// goes to most is worth one, and a rating of 4.5 or better is worth one.
    /// A salon that matches nothing scores zero and is left out rather than
    /// padding the row — a "recommendation" that is just another salon teaches
    /// her to ignore the row.
    public static func rank(salons: [Salon], history: [Appointment], limit: Int = 5) -> [Salon] {
        guard !history.isEmpty else { return [] }

        var serviceFrequency: [String: Int] = [:]
        for appointment in history where !appointment.serviceName.isEmpty {
            serviceFrequency[appointment.serviceName, default: 0] += 1
        }

        // The district she goes to most, found through the salons she booked
        // rather than stored on the appointment — the booking records where she
        // went, not where that is.
        var districtFrequency: [String: Int] = [:]
        for appointment in history {
            guard let salon = salons.first(where: { $0.id == appointment.salonId }),
                  !salon.district.isEmpty else { continue }
            districtFrequency[salon.district, default: 0] += 1
        }
        let topDistrict = districtFrequency.max { $0.value < $1.value }?.key

        return salons
            .map { salon -> (Salon, Int) in
                let serviceScore = salon.services.reduce(0) {
                    $0 + (serviceFrequency[$1] ?? 0) * 2
                }
                let districtScore = (topDistrict != nil && salon.district == topDistrict) ? 1 : 0
                let ratingBonus = salon.rating >= 4.5 ? 1 : 0
                return (salon, serviceScore + districtScore + ratingBonus)
            }
            .filter { $0.1 > 0 }
            .sorted { $0.1 > $1.1 }
            .prefix(limit)
            .map(\.0)
    }
}
