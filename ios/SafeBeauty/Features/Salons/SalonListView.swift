import SwiftUI
import SafeBeautyCore

struct SalonListView: View {
    @State private var repo = SalonRepository()
    @State private var search = ""
    @State private var city: String?
    @State private var area: String?
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
            guard city == nil || cityOf(salon) == city else { return false }
            guard activeArea == nil || Areas.canonicalKey(salon.district) == activeArea
            else { return false }
            guard !term.isEmpty else { return true }
            // Searches the name, the district and the service names, because a
            // customer looks for "ناخن" as readily as for a salon she knows by
            // name.
            //
            // The district is matched on its LABEL, not its stored value. She
            // types "شیرپور"; the document holds "KBL_Shirpur", which contains
            // no Persian at all, so searching the raw field found nothing a
            // customer would ever type.
            return salon.salonName.lowercased().contains(term)
                || Areas.label(salon.district).lowercased().contains(term)
                || salon.services.contains { $0.lowercased().contains(term) }
        }
    }

    /// The city field when the server has derived one, else the district key's
    /// own prefix.
    ///
    /// `city` is written by the discovery derivation, which runs after the
    /// salon is created — so a salon that registered this morning has a
    /// district and no city, and belonged to no chip until the sweep caught up.
    /// The prefix is the same answer, available immediately.
    private func cityOf(_ salon: Salon) -> String {
        salon.city.isEmpty ? Areas.cityOf(Areas.canonicalKey(salon.district)) : salon.city
    }

    /// Only cities that actually have a salon, in Areas' own order.
    ///
    /// Built from the data rather than from the four supported cities. Offering
    /// Herat as a filter when no salon is there sends a woman to an empty
    /// screen — which is the same overclaim the marketing rules had to be
    /// corrected for. Ordered by `liveCities` rather than alphabetically so the
    /// chips read in the order both platforms list them, with anything the
    /// server knows about and this build does not falling in after.
    private var cities: [String] {
        let present = Set(repo.salons.map(cityOf).filter { !$0.isEmpty })
        let known = Areas.liveCities.map(\.key).filter(present.contains)
        return known + present.subtracting(known).sorted()
    }

    /// The city the area chips belong to.
    ///
    /// Falls back to the only city when there is only one, because the city row
    /// hides itself in that case — and without this the neighbourhood chips
    /// waited on a selection the customer was never offered. Today every salon
    /// in production is in Kabul, so that was every customer.
    private var activeCity: String? {
        city ?? (cities.count == 1 ? cities.first : nil)
    }

    /// The chosen area, but only while its chip is on screen.
    ///
    /// A filter the customer cannot see is a filter she cannot undo. The area
    /// row hides itself when a second city appears — the live snapshot can do
    /// that mid-session — and without this her old Kabul neighbourhood went on
    /// filtering with no chip left to clear it. The list stays populated, so
    /// nothing looks wrong; it is just quietly missing salons.
    private var activeArea: String? {
        guard let area, areasHere.contains(area) else { return nil }
        return area
    }

    /// The areas of that city that a salon is actually in.
    ///
    /// The ناحیه and محله levels both appear, because both are how an address is
    /// given here — but only where a salon holds that key. Kabul alone has 64
    /// filterable areas, and a row of 64 chips of which two lead anywhere is a
    /// filter that hides its own answers.
    private var areasHere: [String] {
        guard let city = activeCity else { return [] }
        let present = Set(repo.salons.filter { cityOf($0) == city }
            .map { Areas.canonicalKey($0.district) }
            .filter { !$0.isEmpty })
        let known = Areas.filterableIn(city).map(\.key).filter(present.contains)
        // Free text an older salon typed sorts in after the known keys rather
        // than being dropped: it is where that salon says it is, and hiding the
        // chip would hide the salon.
        return known + present.subtracting(known).sorted()
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
                        Button(L.clearFilters.t) { search = ""; city = nil; area = nil }
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
                VStack(spacing: 0) {
                    if cities.count > 1 {
                        ChipRow {
                            CityChip(label: L.allCities.t, isSelected: city == nil) {
                                city = nil; area = nil
                            }
                            ForEach(cities, id: \.self) { c in
                                CityChip(label: Areas.cityName(c), isSelected: city == c) {
                                    // Her old area belongs to the city she just
                                    // left, so keeping it would filter the new
                                    // city down to nothing.
                                    if city != c { area = nil }
                                    city = c
                                }
                            }
                        }
                    }
                    // Second level, and only inside one city: areas are only
                    // meaningful within a city, and «ناحیه ۱» of Kabul beside
                    // «ناحیه ۱» of Herat is two chips with nothing to tell them
                    // apart.
                    if activeCity != nil && areasHere.count > 1 {
                        ChipRow {
                            CityChip(label: L.allNeighbourhoods.t, isSelected: activeArea == nil) {
                                area = nil
                            }
                            ForEach(areasHere, id: \.self) { a in
                                CityChip(label: Areas.label(a),
                                         isSelected: activeArea == a) { area = a }
                            }
                        }
                    }
                }
                .background(Brand.cream)
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
            .sheet(isPresented: $showMap) { SalonMapView().appDirection() }
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
                    // The label, not the key. "KBL_Shirpur" is what the document
                    // holds and never what anyone calls the place; Android has
                    // always resolved it here, so the same salon read as two
                    // different addresses depending on the phone.
                    Text(Areas.label(salon.district))
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

/// One horizontal row of filter chips.
///
/// Two of these now stack, and a second copy of the scroll view's settings is
/// how the second row ends up anchored to the other edge from the first.
struct ChipRow<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { content }
                .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .defaultScrollAnchor(.leading)
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
