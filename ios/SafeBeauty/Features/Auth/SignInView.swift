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
    /// Offered only when the phone can do it AND she has stored a credential.
    /// Read once at appear rather than on every body pass: it is a keychain
    /// query, and `isEnabled` is the one that touches the item.
    @State private var biometry: String?
    @State private var offerToRemember = false

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

                // Offered before she has typed anything, because that is the
                // point: on a phone she has already set this up on, the
                // password field is something she should never have to touch.
                if let biometry, BiometricVault.isEnabled {
                    Button {
                        Task { await signInWithBiometrics(biometry) }
                    } label: {
                        HStack(spacing: 7) {
                            Image(systemName: biometry == "Touch ID" ? "touchid" : "faceid")
                                .font(.system(size: 17))
                            Text(L.biometricSignIn(biometry))
                                .font(Brand.font(14.5, .medium))
                        }
                        .foregroundStyle(Brand.accent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14)
                            .strokeBorder(Brand.petal.opacity(0.7), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .disabled(auth.isWorking)
                    .padding(.top, 4)
                }

                // Only when it is not already on, and only once she has typed
                // something worth remembering. A switch offering to store a
                // credential that does not exist yet is a switch about nothing.
                if biometry != nil, !BiometricVault.isEnabled, canSubmit {
                    Toggle(isOn: $offerToRemember) {
                        Text(L.biometricEnableLabel.t)
                            .font(Brand.font(13)).foregroundStyle(Brand.textMuted)
                    }
                    .tint(Brand.accent)
                    .padding(.top, 2)
                }

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
        .onAppear { biometry = BiometricVault.biometryName }
    }

    /// The face or the finger, then the ordinary sign-in with what came back.
    ///
    /// The credential is re-authenticated against the server every time rather
    /// than trusted locally: this shortens the typing, it does not shorten the
    /// check. A password changed on another device therefore fails here, which
    /// is correct — and the stored one is dropped so she is not asked again
    /// with something that no longer works.
    private func signInWithBiometrics(_ kind: String) async {
        error = nil
        do {
            let saved = try await BiometricVault.unlock(
                reason: L.biometricReason(kind), cancelTitle: L.cancel.t)
            phone = saved.phone
            try await auth.signIn(phone: saved.phone, password: saved.password)
            notice = nil
        } catch BiometricVault.VaultError.cancelled {
            // She dismissed the sheet. Not an error to report back to her.
        } catch let e as AuthService.AuthError {
            BiometricVault.disable()
            biometry = BiometricVault.biometryName
            error = Self.message(for: e)
        } catch {
            // `error` here is the caught one, so the state needs naming.
            self.error = L.errNetwork.t
        }
    }

    private func submit() async {
        error = nil
        do {
            try await auth.signIn(phone: phone, password: password)
            // Stored only after the server has accepted it, so a typo is never
            // what the phone remembers.
            if offerToRemember {
                try? BiometricVault.enable(phone: phone, password: password)
            }
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
        case .credentialsOutOfSync: L.errCredentialsOutOfSync.t
        case .deviceKeychainUnavailable: L.errDeviceKeychain.t
        case .unexpected(let code): L.errUnexpectedAuth(code)
        case .server: L.errNetwork.t
        }
    }
}
