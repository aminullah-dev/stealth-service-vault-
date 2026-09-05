import SwiftUI
import SafeBeautyCore

struct RegisterView: View {
    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var isProvider = false
    @State private var salonName = ""
    @State private var district = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    BrandField(label: .fullName, text: $name)
                    BrandField(label: .phone, text: $phone, isPhone: true)
                    BrandField(label: .emailOptional, text: $email, isEmail: true)
                    BrandField(label: .password, text: $password, isSecure: true)
                    BrandField(label: .confirmPassword, text: $confirm, isSecure: true)

                    Toggle(L.iAmASalon.t, isOn: $isProvider)
                        .font(Brand.font(15, .medium))
                        .foregroundStyle(Brand.ink)
                        .tint(Brand.accent)
                        .padding(.vertical, 4)

                    if isProvider {
                        BrandField(label: .salonName, text: $salonName)
                        BrandField(label: .district, text: $district)
                    }

                    ErrorBanner(message: error)

                    BrandButton(title: .register, isLoading: auth.isWorking) {
                        Task { await submit() }
                    }
                    .padding(.top, 4)

                    Button(L.haveAccount.t) { dismiss() }
                        .font(Brand.font(14, .medium))
                        .foregroundStyle(Brand.accent)

                    Spacer(minLength: 30)
                }
                .padding(.horizontal, 26)
                .padding(.top, 20)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.register.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.cancel.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
            }
        }
    }

    /// Validated here before the network, in the same order Android validates,
    /// so the same form produces the same first complaint on both platforms.
    private func validate() -> L? {
        if name.trimmingCharacters(in: .whitespaces).isEmpty { return .errNameRequired }
        if !PhoneUtils.isValidAfghan(phone) { return .errPhoneInvalid }
        if password.count < 6 { return .errPasswordShort }
        if password != confirm { return .errPasswordMismatch }
        if isProvider && salonName.trimmingCharacters(in: .whitespaces).isEmpty {
            return .errSalonNameRequired
        }
        return nil
    }

    private func submit() async {
        if let problem = validate() { error = problem.t; return }
        error = nil
        do {
            try await auth.register(
                name: name, phone: phone, email: email, password: password,
                isProvider: isProvider, salonName: salonName, district: district,
                services: isProvider ? ["خدمات"] : []
            )
            dismiss()
        } catch let e as AuthService.AuthError {
            error = SignInView.message(for: e)
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
