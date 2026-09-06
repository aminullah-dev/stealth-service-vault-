import SwiftUI
import SafeBeautyCore

struct SignInView: View {
    @Environment(AuthService.self) private var auth
    @State private var phone = ""
    @State private var password = ""
    @State private var error: String?
    /// Kept apart from `error` on purpose. `submit()` clears `error` on every
    /// attempt, and on the bad connection this whole path exists for, her first
    /// attempt fails — which would wipe the one message telling her the account
    /// already exists, leaving her to register again and hit `phoneTaken`.
    /// This survives failed attempts and clears only when she is actually in.
    @State private var notice: L?
    @State private var showRegister = false
    @State private var showForgot = false

    private var canSubmit: Bool { !phone.isEmpty && !password.isEmpty }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Circle()
                    .fill(Brand.gradient)
                    .frame(width: 76, height: 76)
                    .overlay(Text("SB").font(Brand.font(28, .bold)).foregroundStyle(.white))
                    .padding(.top, 48)

                Text("SafeBeauty").font(Brand.font(26, .bold)).foregroundStyle(Brand.ink)
                Text(L.signIn.t).font(Brand.font(15)).foregroundStyle(Brand.accent)
                    .padding(.bottom, 8)

                BrandField(label: .phone, text: $phone, isPhone: true)
                BrandField(label: .password, text: $password, isSecure: true)

                ErrorBanner(message: notice?.t, tone: .notice)
                ErrorBanner(message: error)

                BrandButton(title: .signIn, isLoading: auth.isWorking, isEnabled: canSubmit) {
                    Task { await submit() }
                }
                .padding(.top, 4)

                // L.forgotPassword has existed since the first version of this
                // screen and was rendered nowhere, so a woman who forgot her
                // password had no route at all — only an admin reset.
                Button(L.forgotPassword.t) { showForgot = true }
                    .font(Brand.font(13.5))
                    .foregroundStyle(Brand.accent)
                    .padding(.top, 2)

                Button(L.noAccountYet.t) {
                    // Stale by definition once she opens registration again,
                    // and it names an account that exists — not something to
                    // leave sitting on a signed-out screen someone else may
                    // pick up.
                    notice = nil
                    showRegister = true
                }
                    .font(Brand.font(14, .medium))
                    .foregroundStyle(Brand.accent)
                    .padding(.top, 6)

                Spacer(minLength: 40)
            }
            .padding(.horizontal, 26)
        }
        .background(Brand.cream.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .sheet(isPresented: $showRegister) {
            RegisterView { registeredPhone in
                phone = registeredPhone
                password = ""
                error = nil
                notice = L.errRegisteredNowSignIn
            }
            // Disabling the two buttons was not enough: a sheet drags down by
            // default, so the gesture stayed live for the whole in-flight
            // window and disabling the buttons had made it the ONLY way out.
            // registerAccount is not cancellable once it has reached the
            // server, and the Task outlives the view either way.
            .interactiveDismissDisabled(auth.isWorking)
            .appDirection()
        }
        .sheet(isPresented: $showForgot) { ForgotPasswordView(initialPhone: phone).appDirection() }
    }

    private func submit() async {
        error = nil
        do {
            try await auth.signIn(phone: phone, password: password)
            // Only here. `notice` survives failed attempts by design — it is
            // the message that stops her registering a second time — so it
            // clears when it has actually been acted on, not when it is tried.
            notice = nil
        } catch let e as AuthService.AuthError {
            error = Self.message(for: e)
        } catch {
            // Anything that is not one of ours is a connection problem far more
            // often than not, and "check your internet" is actionable where the
            // underlying NSError text is not.
            self.error = L.errNetwork.t
        }
    }

    /// One place that turns a failure into a sentence someone can act on.
    static func message(for e: AuthService.AuthError) -> String {
        switch e {
        case .wrongPhoneOrPassword: L.errWrongLogin.t
        case .phoneTaken: L.errPhoneTaken.t
        case .emailTaken: L.errEmailTaken.t
        case .registeredButNotSignedIn: L.errRegisteredNowSignIn.t
        case .rateLimited: L.errTooMany.t
        case .server: L.errNetwork.t
        }
    }
}
