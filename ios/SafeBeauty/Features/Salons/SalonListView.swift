import SwiftUI
import SafeBeautyCore

struct SalonListView: View {
    @Environment(AuthService.self) private var auth
    @State private var repo = SalonRepository()
    @State private var search = ""
    @State private var category: String?
    @State private var city: String?
    @State private var area: String?
    @State private var showMap = false
    @State private var showNotifications = false
    @State private var bookings = BookingsRepository()

    /// Filtered on the client, not by re-querying.
    ///
    /// A city filter as a Firestore equality was exactly the bug fixed on the
    /// server this morning: a salon whose city field was empty matched no
    /// equality and vanished from the results entirely. Filtering here means a
    /// salon with a blank city is still findable by name and still shows under
    /// "all cities" — it is missing a label, not missing.
    private var visible: [Salon] {
        let term = search.trimmingCharacters(in: .whitespaces).lowercased()
        // Both read once, not once per salon. They are computed properties that
        // walk the whole catalogue, and referencing them inside the closure made
        // this quadratic — at the repository's own limit of 200 that is real
        // work on the main thread for every keystroke, since `body` evaluates
        // `visible` twice.
        let city = selectedCity
        let area = activeArea
        let category = self.category
        return repo.salons.filter { salon in
            // The categories array is written by the server from the salon's own
            // free-text service names, and it is deliberately conservative: a
            // service it cannot place confidently is left out rather than
            // guessed into a bucket. So a salon can legitimately have none, and
            // then it appears only under "all" — which is right. A salon filed
            // under a category it does not serve is found out by a customer
            // arriving for a haircut nobody there does.
            guard category == nil || salon.categories.contains(category!) else { return false }
            guard city == nil || cityOf(salon) == city else { return false }
            guard area == nil || areaIdentity(salon) == area else { return false }
            guard !term.isEmpty else { return true }
            // Searches the name, the district and the service names, because a
            // customer looks for "ناخن" as readily as for a salon she knows by
            // name.
            //
            // Both the label and the stored value. She types «شیرپور»; the
            // document holds "KBL_Shirpur", which contains no Persian at all,
            // so the raw field alone found nothing a customer would type — and
            // the label alone stopped matching "shirpur" typed in Latin, which
            // is how the same woman searches with an English keyboard.
            return salon.salonName.lowercased().contains(term)
                || Areas.address(district: salon.district, areaKey: salon.areaKey)
                        .lowercased().contains(term)
                || salon.district.lowercased().contains(term)
                || salon.services.contains { $0.lowercased().contains(term) }
        }
    }

    /// The most specific area this salon is in, as one key.
    ///
    /// Three fields can answer this and they disagree. `district` is what the
    /// owner typed or picked; `districtKey` and `areaKey` are what the server
    /// resolved from it, including the fuzzy match that turns «ناحیه ۱۷» into
    /// KBL_D17. Grouping on `district` alone put a salon that picked the key and
    /// a salon that typed the words into two chips with identical text, each
    /// holding half the answers.
    ///
    /// Most specific first, because `areaKey` is the محله and `districtKey` the
    /// ناحیه above it — and falling back to the raw text last is what keeps a
    /// salon whose address resolved to nothing findable at all. Android drops
    /// that one from every district option; here it gets a chip saying what its
    /// owner wrote.
    private func areaIdentity(_ salon: Salon) -> String {
        if !salon.areaKey.isEmpty { return salon.areaKey }
        if !salon.districtKey.isEmpty { return salon.districtKey }
        return Areas.canonicalKey(salon.district)
    }

    /// The city field when the server has derived one, else the district key's
    /// own prefix.
    ///
    /// `city` is written by the discovery derivation, which runs after the
    /// salon is created — so a salon that registered this morning has a
    /// district and no city, and belonged to no chip until the sweep caught up.
    /// The prefix is the same answer, available immediately.
    private func cityOf(_ salon: Salon) -> String {
        salon.city.isEmpty ? Areas.cityOf(areaIdentity(salon)) : salon.city
    }

    /// The salons the sticky filters above a given row have already narrowed to.
    ///
    /// Each chip row offers only what leads somewhere given the rows above it,
    /// so picking «ناخن» and then a neighbourhood cannot land on an empty
    /// screen — the neighbourhood with no nail salon is simply not offered, and
    /// that absence is the honest answer to "is there one near me".
    ///
    /// Search is deliberately not part of this. It is transient text, and chips
    /// appearing and vanishing under a customer's fingers as she types would be
    /// its own kind of broken.
    private var afterCategory: [Salon] {
        guard let category else { return repo.salons }
        return repo.salons.filter { $0.categories.contains(category) }
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
        let present = Set(afterCategory.map(cityOf).filter { !$0.isEmpty })
        let known = Areas.liveCities.map(\.key).filter(present.contains)
        return known + present.subtracting(known).sorted()
    }

    /// Salons scored against what she has actually booked before.
    ///
    /// Ranked from the salons already loaded rather than from the whole
    /// platform, exactly as Android does: this is a ranking of the candidates
    /// she is scrolling, so a smaller set changes which five come back and not
    /// whether the row works. Hidden entirely for someone with no history —
    /// a "recommendation" row that is just the salon list teaches her to
    /// ignore it.
    private var recommended: [Salon] {
        guard search.isEmpty, category == nil, selectedCity == nil, activeArea == nil
        else { return [] }
        return Recommendations.rank(salons: repo.salons, history: bookings.past)
    }

    /// The chosen city, but only while its chip is on screen — the same clamp
    /// the area has. The row hides itself once one city is left, and her old
    /// choice then filtered on with nothing to deselect. Recoverable, because an
    /// empty result offers Clear filters, but there is no reason to leave the
    /// asymmetry.
    private var selectedCity: String? {
        guard let city, cities.contains(city) else { return nil }
        return city
    }

    /// The city the area chips belong to.
    ///
    /// Falls back to the only city when there is only one, because the city row
    /// hides itself in that case — and without this the neighbourhood chips
    /// waited on a selection the customer was never offered. Today every salon
    /// in production is in Kabul, so that was every customer.
    private var activeCity: String? {
        selectedCity ?? (cities.count == 1 ? cities.first : nil)
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
    /// given here — but only where a salon holds that key. Kabul has 64 areas
    /// and a row of 64 chips of which two lead anywhere is a filter that hides
    /// its own answers.
    ///
    /// Ordered by `areasIn`, which is Areas.kt's own order — ناحیه‌ها first,
    /// then the محله‌ها — rather than by `filterableIn`, which walks parents and
    /// so returns Kabul's 22 districts and none of its 42 neighbourhoods,
    /// because Kabul's neighbourhoods have no parent recorded.
    private var areasHere: [String] {
        guard let city = activeCity else { return [] }
        let present = Set(afterCategory.filter { cityOf($0) == city }
            .map(areaIdentity)
            .filter { !$0.isEmpty })
        let known = Areas.areasIn(city).map(\.key).filter(present.contains)
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
                        Button(L.clearFilters.t) {
                            search = ""; category = nil; city = nil; area = nil
                        }
                            .font(Brand.font(14, .medium))
                            .foregroundStyle(Brand.accent)
                    }
                } else {
                    List {
                        // Above the full list and only when she is not
                        // filtering — a recommendation under an active filter is
                        // answering a question she did not ask.
                        if !recommended.isEmpty {
                            Section {
                                ForEach(recommended) { salon in
                                    NavigationLink { SalonDetailView(salon: salon) } label: {
                                        SalonRow(salon: salon)
                                    }
                                    .listRowBackground(Brand.cream)
                                    .listRowSeparatorTint(Brand.petal.opacity(0.4))
                                }
                            } header: {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(L.recommendedTitle.t)
                                        .font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
                                    Text(L.recommendedSubtitle.t)
                                        .font(Brand.font(12)).foregroundStyle(Brand.accent)
                                }
                                .textCase(nil)
                            }
                        }
                        ForEach(visible) { salon in
                            NavigationLink {
                                SalonDetailView(salon: salon)
                            } label: {
                                SalonRow(salon: salon)
                            }
                            .listRowBackground(Brand.cream)
                            .listRowSeparatorTint(Brand.petal.opacity(0.4))
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .safeAreaInset(edge: .top) {
                VStack(spacing: 0) {
                    // Categories first, above the place filters, the way Android
                    // orders them: what she wants done is a bigger cut than where
                    // she wants it done.
                    //
                    // All five always, unlike the city and area rows. Those are
                    // built from the data because 64 Kabul areas of which two
                    // lead anywhere is noise; this is a fixed vocabulary of five
                    // that a customer expects to see, and a category with no
                    // salon yet is worth showing as a category with no salon yet.
                    if !repo.salons.isEmpty {
                        ChipRow {
                            FilterChip(label: L.categoryAll.t, isSelected: category == nil) {
                                category = nil
                            }
                            ForEach(Categories.canonical, id: \.self) { key in
                                FilterChip(label: Categories.label(key),
                                           isSelected: category == key) { category = key }
                            }
                        }
                    }
                    if cities.count > 1 {
                        ChipRow {
                            FilterChip(label: L.allCities.t, isSelected: selectedCity == nil) {
                                city = nil; area = nil
                            }
                            ForEach(cities, id: \.self) { c in
                                FilterChip(label: Areas.cityName(c), isSelected: selectedCity == c) {
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
                            FilterChip(label: L.allNeighbourhoods.t, isSelected: activeArea == nil) {
                                area = nil
                            }
                            ForEach(areasHere, id: \.self) { a in
                                FilterChip(label: Areas.label(a),
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
                ToolbarItem(placement: .topBarLeading) {
                    // Android puts the bell in the header and gives the fifth
                    // tab to favourites. Five slots is the whole budget, and a
                    // list she opens once a week should not hold one while the
                    // salons she saved have nowhere to live.
                    Button { showNotifications = true } label: {
                        Image(systemName: "bell").foregroundStyle(Brand.accent)
                            .accessibilityLabel(L.notifications.t)
                    }
                    .accessibilityLabel(L.notifications.t)
                }
            }
            .sheet(isPresented: $showMap) { SalonMapView().appDirection() }
            .sheet(isPresented: $showNotifications) { NotificationsView().appDirection() }
        }
        .task { repo.start() }
        .task(id: auth.session?.uid) {
            if let uid = auth.session?.uid { bookings.start(customerId: uid) }
        }
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
                    Text(Areas.address(district: salon.district, areaKey: salon.areaKey))
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
            // Outside the NavigationLink's label would be cleaner, but a List
            // row's whole label is the tap target — so the heart lives here and
            // takes its own tap with .buttonStyle(.plain), which stops the row
            // from also navigating.
            FavoriteButton(salonId: salon.id)
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

struct FilterChip: View {
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
