import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// Changing her password, which iOS could not do at all.
///
/// The callable existed and was deployed with no caller on this platform, so
/// the only way an iPhone user could change her password was to ask an admin
/// to reset it — which means telling somebody else a password she then has to
/// change again.
struct ChangePasswordSheet: View {
    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var current = ""
    @State private var next = ""
    @State private var confirm = ""
    @State private var error: String?
    @State private var done = false

    private var canSubmit: Bool {
        !current.isEmpty && next.count >= 6 && next == confirm
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    BrandField(label: .currentPassword, text: $current, isSecure: true)
                    BrandField(label: .newPassword, text: $next, isSecure: true)
                    BrandField(label: .confirmPassword, text: $confirm, isSecure: true)

                    // Said before she submits, not after it is refused. The
                    // rule is the server's own minimum.
                    Text(L.passwordRule.t)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.accent)

                    if done { ErrorBanner(message: L.passwordChanged.t, tone: .notice) }
                    ErrorBanner(message: error)

                    if !done {
                        BrandButton(title: .save, isLoading: auth.isWorking, isEnabled: canSubmit) {
                            Task { await submit() }
                        }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 26).padding(.top, 18)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.changePassword.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(done ? L.close.t : L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                }
            }
        }
    }

    private func submit() async {
        error = nil
        do {
            try await auth.changePassword(current: current, new: next)
            done = true
            current = ""; next = ""; confirm = ""
        } catch let e as AuthService.AuthError {
            error = e == .wrongPhoneOrPassword ? L.wrongCurrentPassword.t
                                               : SignInView.message(for: e)
        } catch {
            self.error = L.errNetwork.t
        }
    }
}

/// Her name, which she could see and not change.
struct EditNameSheet: View {
    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    BrandField(label: .fullName, text: $name)
                    ErrorBanner(message: error)
                    BrandButton(title: .save,
                                isEnabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                        Task {
                            do { try await auth.updateName(name); dismiss() }
                            catch { self.error = L.errNetwork.t }
                        }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 26).padding(.top, 18)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.editName.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.cancel.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
            }
        }
        .onAppear { if name.isEmpty { name = auth.session?.name ?? "" } }
    }
}

/// Turning loyalty points into wallet credit.
///
/// The points were shown as a number and could not be spent, which makes them
/// decoration. redeemLoyaltyPoints converts at 100 points to 100 AFN and
/// refuses anything under that, so the minimum is said here rather than
/// discovered by being turned down.
struct RedeemPointsSheet: View {
    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    let available: Int
    let onRedeemed: () -> Void

    @State private var points = ""
    @State private var error: String?
    @State private var isWorking = false

    private var amount: Int { Int(points) ?? 0 }
    private var canSubmit: Bool {
        amount >= 100 && amount <= available && amount % 100 == 0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L.pointsAvailable(available))
                        .font(Brand.font(14, .medium)).foregroundStyle(Brand.ink)
                    BrandField(label: .pointsToRedeem, text: $points, isPhone: true)
                    Text(L.redeemRule.t)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.accent)
                    ErrorBanner(message: error)
                    BrandButton(title: .redeem, isLoading: isWorking, isEnabled: canSubmit) {
                        Task { await submit() }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 26).padding(.top, 18)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.redeem.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.cancel.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
            }
        }
    }

    private func submit() async {
        isWorking = true; defer { isWorking = false }
        error = nil
        do {
            _ = try await Callables.call("redeemLoyaltyPoints", ["points": .int(amount)])
            onRedeemed()
            dismiss()
        } catch let e as Callables.CallableError {
            if case .failedPrecondition(let m, _) = e { error = m }
            else { error = L.errNetwork.t }
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
