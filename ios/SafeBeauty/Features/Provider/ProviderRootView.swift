import SwiftUI
import SafeBeautyCore

/// The salon owner's app, which iOS did not have.
///
/// An approved owner used to land on ProviderElsewhereView — a card telling her
/// to open the web console on a computer. She could register here, her salon was
/// created, and then the phone in her hand could not show her a single booking
/// request. Android has had six tabs for this the whole time.
///
/// Four tabs, not six. Android splits Calendar and Reviews and Analytics; on a
/// phone those are one diary, one list and a handful of numbers, and iOS folds
/// anything past five into a "More" menu nobody opens. Requests, Calendar,
/// Income and Profile — with reviews under Profile, where the salon's own
/// reputation belongs.
struct ProviderRootView: View {
    @Environment(AuthService.self) private var auth
    @State private var repo = ProviderRepository()
    @State private var lang = LanguageStore.shared

    var body: some View {
        TabView {
            ProviderRequestsView(repo: repo)
                .tabItem { Label(L.tabRequests.t, systemImage: "tray.full") }
                // The only number in the app she must act on. A request nobody
                // answers becomes a customer who books elsewhere.
                .badge(repo.pending.count)
            ProviderCalendarView(repo: repo)
                .tabItem { Label(L.tabCalendar.t, systemImage: "calendar") }
            ProviderIncomeView(repo: repo)
                .tabItem { Label(L.tabIncome.t, systemImage: "banknote") }
            ProviderProfileView(repo: repo)
                .tabItem { Label(L.tabMyProfile.t, systemImage: "person.crop.circle") }
        }
        .tint(Brand.accent)
        // A tab bar caches its item labels in UIKit, so observing the language
        // store is not enough on its own — without this the labels survive a
        // language change.
        .id(lang.current)
        .task(id: auth.session?.uid) {
            if let uid = auth.session?.uid { repo.start(providerId: uid) }
        }
    }
}

/// Shown in place of any provider tab before her salon exists.
struct NoSalonYet: View {
    var body: some View {
        ContentUnavailableView {
            Text(L.noSalonYet.t)
                .font(Brand.font(15))
                .foregroundStyle(Brand.ink)
                .multilineTextAlignment(.center)
        }
    }
}

/// One booking, as the salon sees it: who, what, when, and how she is paid.
struct ProviderBookingRow: View {
    let booking: Appointment
    var showsActions = false
    var onAccept: () -> Void = {}
    var onDecline: () -> Void = {}
    var isWorking = false

    static func identify(_ booking: Appointment) -> String {
        if !booking.customerName.isEmpty { return booking.customerName }
        if !booking.bookingCode.isEmpty { return booking.bookingCode }
        return booking.serviceName
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 8) {
                // Name, then code, then the service. Every appointment written
                // before the app stored a customer name has all three of the
                // first two empty, and a bold blank line is not a row — it is a
                // gap where an identity should be.
                Text(Self.identify(booking))
                    .font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
                Spacer(minLength: 0)
                StatusPill(status: booking.status)
            }
            if !booking.serviceName.isEmpty {
                Text(booking.serviceName)
                    .font(Brand.font(13)).foregroundStyle(Brand.accent)
            }
            Text(TimeChip.dateLabel(booking.appointmentDate))
                .font(Brand.font(13, .medium)).foregroundStyle(Brand.ink.opacity(0.8))

            HStack(spacing: 10) {
                // Cash and online are not the same news. Cash means she takes
                // the money at the door and owes commission on it; online means
                // it is already paid and the platform settles with her.
                Text(booking.paymentMethod == "CASH" ? L.payCash.t : L.payOnline.t)
                    .font(Brand.font(11.5, .medium))
                    .foregroundStyle(Brand.deep)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Brand.petal.opacity(0.45), in: Capsule())
                if booking.total > 0 {
                    HStack(spacing: 3) {
                        Text(verbatim: "\(booking.total)")
                            .environment(\.layoutDirection, .leftToRight)
                        Text(L.afn.t)
                    }
                    .font(Brand.font(12.5, .medium)).foregroundStyle(Brand.deep)
                }
                Spacer(minLength: 0)
                Text(booking.bookingCode)
                    .font(.system(size: 11, design: .monospaced))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.gold)
            }

            if showsActions {
                HStack(spacing: 16) {
                    // .borderless on both: a List row with one Button lets the
                    // whole row trigger it, and a second kills them both.
                    Button(L.accept.t, action: onAccept)
                        .font(Brand.font(14, .bold))
                        .foregroundStyle(Brand.deep)
                        .buttonStyle(.borderless)
                    Button(L.decline.t, role: .destructive, action: onDecline)
                        .font(Brand.font(14, .medium))
                        .buttonStyle(.borderless)
                    if isWorking { ProgressView().tint(Brand.accent) }
                }
                .disabled(isWorking)
                .padding(.top, 3)
            }
        }
        .padding(.vertical, 6)
        .listRowBackground(Color.white)
    }
}
