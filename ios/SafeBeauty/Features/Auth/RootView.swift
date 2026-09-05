import SwiftUI
import SafeBeautyCore

/// What the app shows depends only on whether there is a session.
///
/// Deliberately not a navigation stack that pushes past the sign-in screen: a
/// signed-out state that is reachable by going "back" is how a shared phone
/// leaks, and on this product the person holding the phone next may not be the
/// person who signed in.
struct RootView: View {
    @State private var auth = AuthService.shared
    @State private var language = AppLanguage.current
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if auth.session == nil {
                SignInView()
            } else {
                SignedInView()
            }
        }
        .environment(auth)
        .environment(\.layoutDirection, language.layoutDirection)
        .environment(\.locale, language.locale)
        .safeAreaInset(edge: .bottom) {
            // The language picker stays reachable from the signed-out screen.
            // A woman who cannot read the interface cannot get to a settings
            // page inside it to change the language.
            Picker("", selection: $language) {
                ForEach(AppLanguage.allCases) { Text(verbatim: $0.endonym).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 26)
            .padding(.bottom, 10)
            .onChange(of: language) { _, new in AppLanguage.current = new }
            .background(Brand.cream)
        }
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

    var body: some View {
        if auth.session?.status == "PENDING" {
            PendingApprovalView()
        } else {
            TabView {
                SalonListView()
                    .tabItem { Label(L.salons.t, systemImage: "scissors") }
                MyBookingsView()
                    .tabItem { Label(L.myBookings.t, systemImage: "calendar") }
            }
            .tint(Brand.accent)
            .safeAreaInset(edge: .top) { SessionBar() }
        }
    }
}

/// Who you are and a way out, on every screen.
///
/// The sign-out control is reachable without navigating anywhere. On a shared
/// phone the fastest possible exit matters more than a tidy toolbar.
struct SessionBar: View {
    @Environment(AuthService.self) private var auth

    var body: some View {
        HStack {
            Text(auth.session?.name ?? "")
                .font(Brand.font(14, .medium))
                .foregroundStyle(Brand.ink)
            Spacer()
            Button(L.signOut.t) { auth.signOut() }
                .font(Brand.font(13, .medium))
                .foregroundStyle(Brand.accent)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 9)
        .background(Brand.cream)
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
