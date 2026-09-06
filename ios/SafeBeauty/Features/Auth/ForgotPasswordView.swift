import SwiftUI
import SafeBeautyCore

/// Recovering a forgotten password, which iOS had no path to at all.
///
/// The account is found by phone — the only identifier she knows — and the link
/// is sent to the email on it. The link opens the hosted /reset page, which
/// re-derives the PBKDF2 material and syncs pinHash and salt; nothing here
/// re-implements that.
///
/// Three outcomes, three sentences. "No account with that number", "that
/// account has no email address", and "check your email" lead to completely
/// different next actions, and collapsing them into one leaves her retrying a
/// thing that will never work.
struct ForgotPasswordView: View {
    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    let initialPhone: String

    @State private var phone = ""
    @State private var error: String?
    @State private var sentTo: String?
    @State private var noEmail = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L.forgotPasswordHelp.t)
                        .font(Brand.font(14))
                        .foregroundStyle(Brand.accent)

                    BrandField(label: .phone, text: $phone, isPhone: true)

                    if let sentTo {
                        ErrorBanner(message: L.resetSentTo(sentTo), tone: .notice)
                    }
                    if noEmail {
                        // Not an error she can fix by trying again, so it does
                        // not offer her the button again — it tells her the one
                        // route that exists.
                        ErrorBanner(message: L.resetNoEmail.t)
                    }
                    ErrorBanner(message: error)

                    if sentTo == nil {
                        BrandButton(title: .sendResetLink,
                                    isLoading: auth.isWorking,
                                    isEnabled: !phone.isEmpty) {
                            Task { await submit() }
                        }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 26)
                .padding(.top, 18)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.forgotPassword.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.cancel.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
            }
        }
        .onAppear { if phone.isEmpty { phone = initialPhone } }
    }

    private func submit() async {
        error = nil; noEmail = false
        do {
            switch try await auth.sendPasswordReset(phone: phone) {
            case .sent(let email): sentTo = email
            case .noEmail:         noEmail = true
            case .notFound:
                // Deliberately specific here, unlike sign-in. She is holding
                // her own number and being told "wrong phone or password"
                // would send her to try passwords she does not have.
                error = L.resetNoAccount.t
            }
        } catch let e as AuthService.AuthError {
            error = SignInView.message(for: e)
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
