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
    }
}

/// A placeholder for the signed-in half, which is the next piece of work. It
/// shows what the session actually resolved to rather than a welcome message,
/// so a wrong role or a pending status is visible immediately instead of at
/// the first screen that depends on it.
struct SignedInView: View {
    @Environment(AuthService.self) private var auth

    var body: some View {
        VStack(spacing: 14) {
            Spacer()
            Text(auth.session?.name ?? "").font(Brand.font(24, .bold)).foregroundStyle(Brand.ink)
            if let s = auth.session {
                Text(verbatim: "\(s.role) · \(s.status) · KYC \(s.kycStatus)")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(Brand.accent)
                if s.status == "PENDING" {
                    Text(L.pendingApproval.t)
                        .font(Brand.font(14)).foregroundStyle(Brand.deep)
                        .multilineTextAlignment(.center).padding(.horizontal, 40)
                }
            }
            Spacer()
            Button(L.signOut.t) { auth.signOut() }
                .font(Brand.font(15, .medium)).foregroundStyle(Brand.accent)
                .padding(.bottom, 30)
        }
        .frame(maxWidth: .infinity)
        .background(Brand.cream.ignoresSafeArea())
    }
}
