import SwiftUI
import SafeBeautyCore

struct SalonListView: View {
    @State private var repo = SalonRepository()
    @State private var search = ""
    @State private var city: String?
    @State private var showMap = false

    /// Filtered on the client, not by re-querying.
    ///
    /// A city filter as a Firestore equality was exactly the bug fixed on the
    /// server this morning: a salon whose city field was empty matched no
    /// equality and vanished from the results entirely. Filtering here means a
    /// salon with a blank city is still findable by name and still shows under
    /// "all cities" — it is missing a label, not missing.
    private var visible: [Salon] {
        let term = search.trimmingCharacters(in: .whitespaces).lowercased()
        return repo.salons.filter { salon in
            let cityOK = city == nil || salon.city == city
            guard cityOK else { return false }
            guard !term.isEmpty else { return true }
            // Searches the name, the district and the service names, because a
            // customer looks for "ناخن" as readily as for a salon she knows by
            // name.
            return salon.salonName.lowercased().contains(term)
                || salon.district.lowercased().contains(term)
                || salon.services.contains { $0.lowercased().contains(term) }
        }
    }

    /// Only cities that actually have a salon.
    ///
    /// Built from the data rather than from Areas.kt's four supported cities.
    /// Offering Herat as a filter when no salon is there sends a woman to an
    /// empty screen — which is the same overclaim the marketing rules had to
    /// be corrected for.
    private var cities: [String] {
        Array(Set(repo.salons.map(\.city).filter { !$0.isEmpty })).sorted()
    }

    var body: some View {
        NavigationStack {
            Group {
                if repo.isLoading && repo.salons.isEmpty {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if repo.error != nil && repo.salons.isEmpty {
                    // "We could not load" is a different sentence from "there
                    // are none", and showing the wrong one teaches a customer
                    // that the app is empty when it is actually broken.
                    ContentUnavailableView {
                        Text(L.couldNotLoad.t).font(Brand.font(17, .bold))
                    } actions: {
                        Button(L.retry.t) { repo.start() }
                            .font(Brand.font(15, .medium))
                            .foregroundStyle(Brand.accent)
                    }
                } else if repo.salons.isEmpty {
                    ContentUnavailableView {
                        Text(L.noSalonsYet.t)
                            .font(Brand.font(17, .medium))
                            .foregroundStyle(Brand.ink)
                    }
                } else if visible.isEmpty {
                    // A third case, and a different sentence again: there ARE
                    // salons, her filter just excluded them all.
                    ContentUnavailableView {
                        Text(L.noMatches.t)
                            .font(Brand.font(16, .medium))
                            .foregroundStyle(Brand.ink)
                    } actions: {
                        Button(L.clearFilters.t) { search = ""; city = nil }
                            .font(Brand.font(14, .medium))
                            .foregroundStyle(Brand.accent)
                    }
                } else {
                    List(visible) { salon in
                        NavigationLink {
                            SalonDetailView(salon: salon)
                        } label: {
                            SalonRow(salon: salon)
                        }
                        .listRowBackground(Brand.cream)
                        .listRowSeparatorTint(Brand.petal.opacity(0.4))
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .safeAreaInset(edge: .top) {
                if cities.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            CityChip(label: L.allCities.t, isSelected: city == nil) { city = nil }
                            ForEach(cities, id: \.self) { c in
                                CityChip(label: CityNames.label(c),
                                         isSelected: city == c) { city = c }
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 8)
                    }
                    .defaultScrollAnchor(.leading)
                    .background(Brand.cream)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.salons.t)
            .navigationBarTitleDisplayMode(.large)
            .searchable(text: $search, prompt: L.searchSalons.t)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    // In the toolbar rather than as a sixth tab: iOS collapses
                    // anything past five into a "More" list, which buries both
                    // the map and support behind an extra tap and a menu nobody
                    // looks in.
                    Button { showMap = true } label: {
                        Image(systemName: "map").foregroundStyle(Brand.accent)
                            .accessibilityLabel(L.map.t)
                    }
                    .accessibilityLabel(L.map.t)
                }
            }
            .sheet(isPresented: $showMap) { SalonMapView() }
        }
        .task { repo.start() }
    }
}

struct SalonRow: View {
    let salon: Salon

    private var letterTile: some View {
        Brand.gradient.overlay(
            Text(salon.salonName.prefix(1))
                .font(Brand.font(22, .bold))
                .foregroundStyle(.white)
        )
    }

    var body: some View {
        HStack(spacing: 13) {
            // The salon's own photo when it has one. A beauty salon sells a
            // room and a look; a coloured tile with a letter in it says
            // nothing about either, and coverImageUrl was on the model and
            // rendered nowhere. The letter stays as the fallback, because most
            // salons have not uploaded a photo yet and an empty grey box would
            // be worse than the tile.
            Group {
                if let url = URL(string: salon.coverImageUrl), !salon.coverImageUrl.isEmpty {
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase {
                            image.resizable().scaledToFill()
                        } else {
                            letterTile
                        }
                    }
                } else {
                    letterTile
                }
            }
            .frame(width: 56, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 13))

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(salon.salonName)
                        .font(Brand.font(16, .bold))
                        .foregroundStyle(Brand.ink)
                        .lineLimit(1)
                    if salon.isVerified {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(Brand.gold)
                            .accessibilityLabel(L.verified.t)
                    }
                }

                // Rating before district. It is the field that decides which
                // salon she opens, it was already on the model, and the list
                // showed neither it nor the number of visits behind it.
                if salon.rating > 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 11)).foregroundStyle(Brand.gold)
                        Text(String(format: "%.1f", salon.rating))
                            .font(Brand.font(12.5, .medium))
                            .foregroundStyle(Brand.ink)
                            .environment(\.layoutDirection, .leftToRight)
                        if salon.confirmedCount > 0 {
                            Text(L.visitCount(salon.confirmedCount))
                                .font(Brand.font(11.5))
                                .foregroundStyle(Brand.accent)
                        }
                    }
                    // One label for the pair, so VoiceOver says "4.8 stars,
                    // 12 visits" instead of reading a star glyph and a decimal.
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(L.ratingLabel(salon.rating, salon.confirmedCount))
                }

                if !salon.district.isEmpty {
                    Text(salon.district)
                        .font(Brand.font(12.5))
                        .foregroundStyle(Brand.accent)
                        .lineLimit(1)
                }

                if salon.lowestPrice > 0 {
                    // The number stays left-to-right inside a right-to-left
                    // row, so "۸۰ افغانی" does not render with the figure on
                    // the wrong side of its unit.
                    HStack(spacing: 4) {
                        Text(L.from.t)
                        Text(verbatim: "\(salon.lowestPrice)")
                            .environment(\.layoutDirection, .leftToRight)
                        Text(L.afn.t)
                    }
                    .font(Brand.font(12.5, .medium))
                    .foregroundStyle(Brand.deep)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 7)
    }
}

/// A city name in the reader's language.
///
/// The stored value is a key like "KABUL", which is not what anyone calls the
/// place. Falls back to the key itself for a city added on the server before
/// this app knew about it — showing "MZR" is worse than showing nothing, but
/// far better than the filter silently omitting a city that has salons in it.
enum CityNames {
    static func label(_ key: String) -> String {
        switch key {
        case "KABUL": L(fa: "کابل", ps: "کابل", en: "Kabul").t
        case "HERAT": L(fa: "هرات", ps: "هرات", en: "Herat").t
        case "MAZAR": L(fa: "مزارشریف", ps: "مزارشریف", en: "Mazar-e-Sharif").t
        case "JALALABAD": L(fa: "جلال\u{200C}آباد", ps: "جلال\u{200C}آباد", en: "Jalalabad").t
        default: key
        }
    }
}

struct CityChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(Brand.font(13.5, .medium))
                .padding(.horizontal, 14).padding(.vertical, 7)
                .background(isSelected ? AnyShapeStyle(Brand.gradient)
                                       : AnyShapeStyle(Color.white))
                .foregroundStyle(isSelected ? Color.white : Brand.ink)
                .clipShape(Capsule())
                .overlay(Capsule().strokeBorder(
                    isSelected ? .clear : Brand.petal.opacity(0.6), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
