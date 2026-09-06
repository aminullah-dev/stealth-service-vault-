import SwiftUI
import SafeBeautyCore

/// How the salon list is ordered. Mirrors `SalonSort` in DashboardViewModel.kt.
enum SalonSort: String, CaseIterable, Identifiable {
    case recommended, nearest, topRated, priceLow

    var id: String { rawValue }

    var label: String {
        switch self {
        case .recommended: L.sortRecommended.t
        case .nearest:     L.sortNearest.t
        case .topRated:    L.sortTopRated.t
        case .priceLow:    L.sortCheapest.t
        }
    }
}

/// The three narrowings that live behind the Filters button, as one value so
/// resetting them is one assignment and "is anything on?" is one question.
struct SalonFilters: Equatable {
    var sort: SalonSort = .recommended
    var minRating: Double = 0     // 0 = any
    var maxPrice: Int = 0         // 0 = any, in AFN

    /// What lights the Filters chip up. The same test Android makes.
    var isActive: Bool { sort != .recommended || minRating > 0 || maxPrice > 0 }
}

/// Filter & sort, the sheet iOS did not have.
///
/// Android has offered these since the customer dashboard was written: sort by
/// distance, rating or price, a rating floor and a price ceiling. On iPhone the
/// only narrowing available was category, city, area and the search box — so a
/// customer with a budget had to open every salon to find out.
///
/// The four sort chips and the two rows of thresholds are Android's own values,
/// not a redesign: 3.0/4.0/4.5 and 500/1000/2000 AFN are the numbers that make
/// sense for this market and they should not differ per platform.
struct FilterSheet: View {
    @Binding var filters: SalonFilters
    /// Nearest cannot just be selected — it needs a fix first. The parent owns
    /// that, so this passes the tap up rather than setting `filters.sort`.
    let onSelectSort: (SalonSort) -> Void
    /// Reset goes back to the parent too, because clearing the sort also has
    /// to cancel a Nearest that is still waiting on a location fix — otherwise
    /// the list jumps to distance order a second after she cleared it.
    let onReset: () -> Void
    /// The provider itself, not the sentence it produces.
    ///
    /// A `.sheet` content closure runs once, at presentation: a `String?`
    /// passed in is frozen there, so the "we need permission" line stayed on
    /// screen after permission was granted and the list had already re-sorted.
    /// An @Observable object read here is live.
    let location: LocationProvider

    @Environment(\.dismiss) private var dismiss

    /// Android's own four, as literals — see L.ratingAtLeast.
    private static let ratings: [(value: Double, label: String)] =
        [(0, ""), (3, "3.0"), (4, "4.0"), (4.5, "4.5")]
    private static let prices: [Int] = [0, 500, 1000, 2000]

    /// Why a location request came back with nothing. Two sentences, because
    /// "you said no" and "the phone could not get a fix" are different problems
    /// and only one of them is fixed by walking outside.
    private var locationNote: String? {
        switch location.failure {
        case .denied: L.locationPermissionNeeded.t
        case .noFix: L.locationUnavailable.t
        case nil: nil
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    group(L.sortByLabel.t) {
                        ForEach(SalonSort.allCases) { mode in
                            FilterChip(label: mode.label,
                                       isSelected: filters.sort == mode) {
                                onSelectSort(mode)
                            }
                        }
                    }
                    if let note = locationNote {
                        Text(note)
                            .font(Brand.font(12))
                            .foregroundStyle(Brand.warning)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    group(L.minRatingLabel.t) {
                        ForEach(Self.ratings, id: \.value) { rating in
                            FilterChip(label: rating.value == 0
                                            ? L.filterAny.t
                                            : L.ratingAtLeast(rating.label),
                                       isSelected: filters.minRating == rating.value) {
                                filters.minRating = rating.value
                            }
                        }
                    }

                    group(L.maxPriceLabel.t) {
                        ForEach(Self.prices, id: \.self) { price in
                            FilterChip(label: price == 0 ? L.filterAny.t
                                                         : L.priceUnder(price),
                                       isSelected: filters.maxPrice == price) {
                                filters.maxPrice = price
                            }
                        }
                    }
                    Spacer(minLength: 12)
                }
                .padding(.horizontal, 20).padding(.top, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.filtersTitle.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.close.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button(L.filtersReset.t, action: onReset)
                        .foregroundStyle(Brand.accent)
                        .disabled(!filters.isActive)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// A label with its chips wrapping under it.
    @ViewBuilder
    private func group(_ title: String, @ViewBuilder chips: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(title)
                .font(Brand.font(12, .semibold))
                .foregroundStyle(Brand.accent)
            // FlowLayout from Components/Chips.swift — Compose's FlowRow, which
            // SwiftUI has no equivalent of. A horizontal scroller would hide
            // the option on the end, which is the one she is looking for.
            FlowLayout(spacing: 8) { chips() }
        }
    }
}
