import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// What the app shows depends only on whether there is a session.
///
/// Deliberately not a navigation stack that pushes past the sign-in screen: a
/// signed-out state that is reachable by going "back" is how a shared phone
/// leaks, and on this product the person holding the phone next may not be the
/// person who signed in.
struct RootView: View {
    @State private var auth = AuthService.shared
    @State private var lang = LanguageStore.shared
    @State private var theme = ThemeStore.shared
    @Environment(\.colorScheme) private var systemScheme
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if auth.session == nil {
                // The picker lives here and only here. Someone who cannot read
                // the interface cannot navigate into it to change the language,
                // so it has to be on the first screen — and once she is signed
                // in it moves to her account, because pinned to the bottom of a
                // TabView it sat on top of the tab bar and hid it.
                SignInView()
                    .safeAreaInset(edge: .bottom) {
                        @Bindable var lang = lang
                        Picker("", selection: $lang.current) {
                            ForEach(AppLanguage.allCases) { Text(verbatim: $0.endonym).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal, 26)
                        .padding(.bottom, 10)
                        .background(Brand.cream)
                    }
            } else {
                SignedInView()
            }
        }
        .environment(auth)
        // The whole tree is rebuilt when the look changes. Brand.* are static
        // lookups, not observable properties, so nothing would re-render on
        // its own — the same reason the tab bar needed this for language.
        .id(theme.identity)
        // colorScheme is only readable from a view, so the store is told.
        .onAppear { theme.systemIsDark = systemScheme == .dark }
        .onChange(of: systemScheme) { _, s in theme.systemIsDark = s == .dark }
        // Pins the whole app when she has chosen, and follows the phone when
        // she has not.
        .preferredColorScheme(theme.appearance == .system
                              ? nil : (theme.isDark ? .dark : .light))
        .environment(\.layoutDirection, lang.current.layoutDirection)
        .environment(\.locale, lang.current.locale)
        .animation(.easeInOut(duration: 0.25), value: auth.session)
        // Re-read her profile when the app comes forward, so an approval that
        // happened while it was closed is reflected without a sign-out.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await auth.refresh() } }
        }
    }
}

/// What a signed-in customer sees.
///
/// A provider whose account is still PENDING gets the waiting screen rather
/// than the salon list, because a salon owner awaiting approval has nothing to
/// do in a customer's browse view and showing it to her implies she is set up
/// when she is not.
struct SignedInView: View {
    @Environment(AuthService.self) private var auth
    /// Observed here, not only in RootView. `L.x.t` reads AppLanguage.current,
    /// which is a UserDefaults value nothing watches — so this view's body was
    /// never re-evaluated on a language change and the tab bar stayed in the
    /// old language while every screen behind it had switched. Verified on the
    /// simulator: "My account" in English above five Dari tabs.
    @State private var lang = LanguageStore.shared

    var body: some View {
        if auth.session?.status == "SUSPENDED" {
            SuspendedView()
        } else if auth.session?.role == "PROVIDER" && auth.session?.status == "APPROVED" {
            // The salon owner's own app, where a card telling her to open a
            // laptop used to be.
            ProviderRootView()
        } else if auth.session?.role == "PROVIDER" {
            // iOS has no provider side. Without this branch an approved salon
            // owner landed in the CUSTOMER tabs — able to browse salons and
            // book appointments, with no way to see her own booking requests,
            // her calendar or her income. Registration still works, so she can
            // sign up here and her salon is created; she is told where to
            // manage it rather than handed the wrong app in silence.
            ProviderElsewhereView()
        } else if auth.session?.status == "PENDING" {
            PendingApprovalView()
        } else {
            TabView {
                SalonListView()
                    .tabItem { Label(L.salons.t, systemImage: "scissors") }
                FeedView()
                    .tabItem { Label(L.discover.t, systemImage: "sparkles") }
                MyBookingsView()
                    .tabItem { Label(L.myBookings.t, systemImage: "calendar") }
                // Favourites, where notifications used to be. Notifications
                // moved to the bell in the salon list's header — the same trade
                // Android makes, because five tabs is the whole budget and a
                // list she opens once a week was holding a slot the salons she
                // saved had no room in.
                FavoritesView()
                    .tabItem { Label(L.favorites.t, systemImage: "heart") }
                ProfileView()
                    .tabItem { Label(L.profile.t, systemImage: "person") }
            }
            .tint(Brand.accent)
            // Rebuilt on a language change rather than merely re-rendered. A
            // tab bar caches its item labels in UIKit, so observing the store
            // is not enough on its own — without this the labels survive the
            // switch. It costs the selected tab, which resets to the first;
            // that is a fair price for a bar that is legible.
            .id(lang.current)
        }
    }
}

/// What a suspended account sees.
///
/// She is signed in, which is the point: the message tells her to contact
/// support and support is inside the app. Deliberately not the tab bar — a
/// suspended account may not book, and every callable refuses her, so offering
/// the whole product would be a screen of failures. This is the reason and the
/// one door that still opens.
struct SuspendedView: View {
    @Environment(AuthService.self) private var auth
    @State private var reason = ""
    @State private var showSupport = false

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "exclamationmark.shield")
                .font(.system(size: 42))
                .foregroundStyle(Color(hex: 0xC0392B))
            Text(auth.session?.name ?? "")
                .font(Brand.font(22, .bold))
                .foregroundStyle(Brand.ink)
            Text(L.accountSuspended.t)
                .font(Brand.font(15))
                .foregroundStyle(Brand.deep)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            // Shown only when an admin wrote one. "Suspended for: " with
            // nothing after it reads as a system that will not say why.
            if !reason.isEmpty {
                Text(reason)
                    .font(Brand.font(13.5))
                    .foregroundStyle(Brand.accent)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }
            Button(L.support.t) { showSupport = true }
                .font(Brand.font(15, .medium))
                .foregroundStyle(.white)
                .padding(.horizontal, 28).padding(.vertical, 13)
                .background(Brand.gradient, in: RoundedRectangle(cornerRadius: 13))
                .padding(.top, 6)
            Spacer()
            Button(L.signOut.t) { auth.signOut() }
                .font(Brand.font(15, .medium))
                .foregroundStyle(Brand.accent)
                .padding(.bottom, 30)
        }
        .frame(maxWidth: .infinity)
        .background(Brand.cream.ignoresSafeArea())
        .sheet(isPresented: $showSupport) { SupportView().appDirection() }
        .task {
            // Her own document, which the rules already allow her to read.
            guard let uid = auth.session?.uid, !uid.isEmpty,
                  let snap = try? await Firestore.firestore().document("users/\(uid)").getDocument()
            else { return }
            reason = (snap.data()?["suspendedReason"] as? String) ?? ""
        }
    }
}

/// A salon owner on iOS, until the provider screens exist.
struct ProviderElsewhereView: View {
    @Environment(AuthService.self) private var auth
    @State private var showSupport = false

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "laptopcomputer.and.iphone")
                .font(.system(size: 40)).foregroundStyle(Brand.accent)
            Text(auth.session?.name ?? "")
                .font(Brand.font(22, .bold)).foregroundStyle(Brand.ink)
            Text(L.providerUseOtherApp.t)
                .font(Brand.font(15)).foregroundStyle(Brand.deep)
                .multilineTextAlignment(.center).padding(.horizontal, 40)
            Text(verbatim: "safebeauty-salon.web.app")
                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                .environment(\.layoutDirection, .leftToRight)
                .foregroundStyle(Brand.accent)
            Button(L.support.t) { showSupport = true }
                .font(Brand.font(15, .medium)).foregroundStyle(.white)
                .padding(.horizontal, 28).padding(.vertical, 13)
                .background(Brand.gradient, in: RoundedRectangle(cornerRadius: 13))
                .padding(.top, 6)
            Spacer()
            Button(L.signOut.t) { auth.signOut() }
                .font(Brand.font(15, .medium)).foregroundStyle(Brand.accent)
                .padding(.bottom, 30)
        }
        .frame(maxWidth: .infinity)
        .background(Brand.cream.ignoresSafeArea())
        .sheet(isPresented: $showSupport) { SupportView().appDirection() }
    }
}

struct PendingApprovalView: View {
    @Environment(AuthService.self) private var auth

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "clock.badge.checkmark")
                .font(.system(size: 42))
                .foregroundStyle(Brand.accent)
            Text(auth.session?.name ?? "")
                .font(Brand.font(22, .bold))
                .foregroundStyle(Brand.ink)
            Text(L.pendingApproval.t)
                .font(Brand.font(15))
                .foregroundStyle(Brand.deep)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            Spacer()
            Button(L.signOut.t) { auth.signOut() }
                .font(Brand.font(15, .medium))
                .foregroundStyle(Brand.accent)
                .padding(.bottom, 30)
        }
        .frame(maxWidth: .infinity)
        .background(Brand.cream.ignoresSafeArea())
    }
}
