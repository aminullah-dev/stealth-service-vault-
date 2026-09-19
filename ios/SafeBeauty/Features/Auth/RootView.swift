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
    @State private var showOnboarding = !OnboardingState.seen
    /// Owned at the root so one block list serves the feed, the comment threads
    /// and a salon's reviews — three screens that must agree about who she has
    /// blocked, and would each hold a different answer if each read its own.
    @State private var moderation = Moderation()
    /// Owned here for the same reason: booking, favouriting, messaging a
    /// salon and the notifications bell all live under `BrowsingRootView`,
    /// several screens deep in different tabs, and each needs to raise the
    /// same sheet rather than four independent ones with four independent
    /// dismiss states.
    @State private var signInPrompt = SignInPrompt()
    @Environment(\.colorScheme) private var systemScheme
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if showOnboarding && auth.session == nil {
                // Before the sign-in screen, and only ever once. Opening
                // straight on a phone-number field asks a woman to hand over
                // her number before anything has told her what the app is for.
                OnboardingView {
                    OnboardingState.seen = true
                    showOnboarding = false
                }
            } else if auth.session == nil {
                // Apple rejected 1.0 build 4 under 5.1.1(v): the app forced
                // registration before showing anything, including the salon
                // list, which is not an account-based feature. She now lands
                // on the same browsing a signed-in customer gets — the salons
                // tab, search, filters, a salon's page — and is asked to sign
                // in only where an account is actually needed: booking,
                // favouriting, messaging a salon, or notifications. Those all
                // route through `signInPrompt` rather than each gating itself.
                //
                // Still not a navigation stack she could push past a signed-in
                // state to reach — the concern the old comment here named is
                // about a shared phone leaking a PREVIOUS person's account,
                // and that is untouched: signing out still clears `session`
                // and lands back here, at a screen with no one's data on it.
                BrowsingRootView()
                    // Rebuilt on a language change, exactly as SignedInView's
                    // tab bar is. Build 5 shipped without this and a TestFlight
                    // user switched to English on 2026-09-17: the chips stayed
                    // Dari and every salon row rendered MIRRORED — glyphs
                    // reversed. The List's cells are UIKit, created while
                    // UIView.appearance() said forceRightToLeft; appearance
                    // only reaches views created after it changes, so those
                    // cells kept RTL while the SwiftUI environment went LTR,
                    // and SwiftUI flipped their contents to reconcile the two.
                    // A fresh tree gets fresh cells under the new direction.
                    // Applied before the picker's inset so the picker itself
                    // is not torn down mid-tap.
                    .id(lang.current)
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
        .environment(moderation)
        .environment(signInPrompt)
        // Fires once, independent of the branches above — a browsing visitor
        // needs `isSignedIn()` satisfied before her very first salon-list
        // read, not only after she opens the sign-in sheet.
        .task { await auth.ensureBrowsingSession() }
        .task(id: auth.session?.uid) {
            if let uid = auth.session?.uid { moderation.start(uid: uid) }
            else { moderation.stop() }
        }
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

/// What a customer with no account sees: the same salon list a signed-in one
/// gets, plus a way to sign in, either from the toolbar or from wherever she
/// hits something that needs an account.
///
/// Deliberately just the one screen rather than the five-tab bar
/// `SignedInView` shows. Favourites, My bookings and Profile have nothing to
/// display for her yet, and a tab bar with three tabs that open a sign-in
/// prompt on tap is worse than not offering them until she has a reason to —
/// browsing salons is the one thing Apple's 5.1.1(v) requires working without
/// an account, not the whole app's navigation shape.
struct BrowsingRootView: View {
    @Environment(SignInPrompt.self) private var signInPrompt

    var body: some View {
        @Bindable var signInPrompt = signInPrompt
        SalonListView()
            .sheet(isPresented: $signInPrompt.isPresented) {
                SignInView().appDirection()
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
                .foregroundStyle(Brand.danger)
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
