import SwiftUI
import SafeBeautyCore

/// Her salon as customers see it, plus the account actions.
///
/// Reviews live here rather than in a tab of their own: a salon's reputation is
/// part of how it presents itself, and five tabs is the whole budget before iOS
/// starts hiding things in a "More" menu.
struct ProviderProfileView: View {
    let repo: ProviderRepository

    @Environment(AuthService.self) private var auth
    @State private var lang = LanguageStore.shared
    @State private var showSupport = false
    @State private var confirmSignOut = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if let salon = repo.salon {
                        salonCard(salon)
                        if !salon.services.isEmpty { servicesCard(salon) }
                        hoursCard(salon)
                        reviewsCard
                    } else {
                        NoSalonYet(repo: repo).frame(height: 180)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(L.language.t).font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.ink.opacity(0.75))
                        @Bindable var lang = lang
                        Picker("", selection: $lang.current) {
                            ForEach(AppLanguage.allCases) { Text(verbatim: $0.endonym).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding(15)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.white, in: RoundedRectangle(cornerRadius: 16))

                    Button { showSupport = true } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "bubble.left.fill").foregroundStyle(Brand.accent)
                            Text(L.support.t)
                                .font(Brand.font(14.5, .medium)).foregroundStyle(Brand.ink)
                            Spacer()
                            Image(systemName: "chevron.forward")
                                .font(.system(size: 12)).foregroundStyle(Brand.accent)
                        }
                        .padding(15)
                        .background(.white, in: RoundedRectangle(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)

                    // The editing that is genuinely better on a bigger screen —
                    // prices, working hours, gallery, staff — stays on the
                    // console, and this says where rather than leaving her to
                    // hunt for it.
                    Text(L.providerConsoleHint.t)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.accent)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)

                    Button(role: .destructive) { confirmSignOut = true } label: {
                        Text(L.signOut.t)
                            .font(Brand.font(15, .medium))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(.white, in: RoundedRectangle(cornerRadius: 13))
                    }
                    .padding(.bottom, 30)
                }
                .padding(.horizontal, 20).padding(.top, 12)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.tabMyProfile.t)
            .sheet(isPresented: $showSupport) { SupportView().appDirection() }
            .alert(L.signOut.t, isPresented: $confirmSignOut) {
                Button(L.cancel.t, role: .cancel) {}
                Button(L.signOut.t, role: .destructive) { auth.signOut() }
            } message: {
                Text(L.signOutWarning.t)
            }
        }
    }

    private func salonCard(_ salon: Salon) -> some View {
        VStack(spacing: 8) {
            if let url = URL(string: salon.coverImageUrl), !salon.coverImageUrl.isEmpty {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                    } else { Brand.petal.opacity(0.25) }
                }
                .frame(height: 120)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            HStack(spacing: 6) {
                Text(salon.salonName)
                    .font(Brand.font(19, .bold)).foregroundStyle(Brand.ink)
                if salon.isVerified {
                    Image(systemName: "checkmark.seal.fill").foregroundStyle(Brand.gold)
                        .accessibilityLabel(L.verified.t)
                }
            }
            if !salon.district.isEmpty {
                Text(Areas.address(district: salon.district, areaKey: salon.areaKey))
                    .font(Brand.font(13)).foregroundStyle(Brand.accent)
            }
            HStack(spacing: 14) {
                if salon.rating > 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 12)).foregroundStyle(Brand.gold)
                        Text(String(format: "%.1f", salon.rating))
                            .font(Brand.font(13, .medium)).foregroundStyle(Brand.ink)
                            .environment(\.layoutDirection, .leftToRight)
                    }
                }
                // Whether she is listed at all. isAvailable is what an admin
                // flips on approval, and a salon that thinks it is live when it
                // is not will wonder why nobody books.
                Text(salon.isAvailable ? L.salonListed.t : L.salonHidden.t)
                    .font(Brand.font(11.5, .medium))
                    .foregroundStyle(salon.isAvailable ? Color(hex: 0x1F7A5C) : Color(hex: 0xC0392B))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(14)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
    }

    private func servicesCard(_ salon: Salon) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // Not "choose services" — that is the customer's screen. She is
            // reading her own price list, not picking from it.
            Text(L.servicesAndPrices.t).font(Brand.font(14, .bold)).foregroundStyle(Brand.ink)
            FlowLayout(spacing: 8) {
                ForEach(salon.services, id: \.self) { service in
                    HStack(spacing: 5) {
                        Text(service).font(Brand.font(13))
                        if let price = salon.pricePerService[service] {
                            Text(verbatim: "\(price)")
                                .font(Brand.font(13, .medium))
                                .environment(\.layoutDirection, .leftToRight)
                        }
                    }
                    .foregroundStyle(Brand.deep)
                    .padding(.horizontal, 11).padding(.vertical, 6)
                    .background(Brand.petal.opacity(0.45), in: Capsule())
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(15)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
    }

    private func hoursCard(_ salon: Salon) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L.workingHours.t).font(Brand.font(14, .bold)).foregroundStyle(Brand.ink)
            ForEach(salon.workingHours.sorted { $0.dayOfWeek < $1.dayOfWeek }, id: \.dayOfWeek) { day in
                HStack {
                    Text(Self.dayName(day.dayOfWeek))
                        .font(Brand.font(13)).foregroundStyle(Brand.ink.opacity(0.8))
                    Spacer()
                    if day.isOpen {
                        Text(verbatim: String(format: "%02d:%02d – %02d:%02d",
                                              day.openHour, day.openMinute,
                                              day.closeHour, day.closeMinute))
                            .font(Brand.font(13, .medium))
                            .environment(\.layoutDirection, .leftToRight)
                            .foregroundStyle(Brand.deep)
                    } else {
                        Text(L.closedDay.t).font(Brand.font(13)).foregroundStyle(Brand.accent)
                    }
                }
            }
        }
        .padding(15)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
    }

    /// The salon's own week, named. dayOfWeek follows Firestore's stored
    /// numbering, which DayGrid.weekday(of:) also produces, so this maps the
    /// same integers rather than inventing a second convention.
    private static func dayName(_ dayOfWeek: Int) -> String {
        let f = DateFormatter()
        f.locale = AppLanguage.current.locale
        let symbols = f.standaloneWeekdaySymbols ?? []
        // Firestore stores 1...7; DateFormatter's array is 0-indexed from Sunday.
        let index = ((dayOfWeek - 1) % 7 + 7) % 7
        return symbols.indices.contains(index) ? symbols[index] : ""
    }

    private var reviewsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L.reviews.t).font(Brand.font(14, .bold)).foregroundStyle(Brand.ink)
            if repo.reviews.isEmpty {
                Text(L.noReviewsYet.t).font(Brand.font(13)).foregroundStyle(Brand.accent)
            } else {
                ForEach(repo.reviews.prefix(10)) { review in
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 4) {
                            ForEach(0..<5) { i in
                                Image(systemName: i < review.rating ? "star.fill" : "star")
                                    .font(.system(size: 11)).foregroundStyle(Brand.gold)
                            }
                            Spacer(minLength: 0)
                            // Reviews written before the app stored a name have
                            // none. "A customer" is what she is; an empty gap
                            // beside five stars reads as a name that failed to
                            // load.
                            Text(review.customerName.isEmpty ? L.anonymousCustomer.t
                                                             : review.customerName)
                                .font(Brand.font(12)).foregroundStyle(Brand.accent)
                        }
                        if !review.comment.isEmpty {
                            Text(review.comment)
                                .font(Brand.font(13)).foregroundStyle(Brand.ink.opacity(0.85))
                        }
                        // Her own answer, which the card did not show at all —
                        // and which is the one thing she needs to know before
                        // deciding whether a review still wants replying to.
                        if !review.providerReply.isEmpty {
                            HStack(alignment: .top, spacing: 6) {
                                Text(L.yourReply.t)
                                    .font(Brand.font(11.5, .medium))
                                    .foregroundStyle(Brand.deep)
                                Text(review.providerReply)
                                    .font(Brand.font(12.5))
                                    .foregroundStyle(Brand.ink.opacity(0.75))
                            }
                            .padding(.top, 2)
                        }
                    }
                    .padding(.vertical, 5)
                    if review.id != repo.reviews.prefix(10).last?.id {
                        Divider().overlay(Brand.petal.opacity(0.4))
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(15)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
    }
}
