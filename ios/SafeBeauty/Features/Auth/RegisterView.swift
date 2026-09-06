import SwiftUI
import SafeBeautyCore

struct RegisterView: View {
    /// Called when the account was created but the device could not sign in.
    /// Carries her phone number back so the sign-in screen she lands on is
    /// already filled in with it — leaving her inside this sheet would put the
    /// message "please sign in" directly above a button that says Register.
    ///
    /// Deliberately has no default. A no-op default would make "handled" and
    /// "silently swallowed" identical at the call site, on the one path where
    /// saying nothing strands a real account.
    let onNeedsSignIn: (String) -> Void

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
    @State private var services: [String] = []
    @State private var serviceInput = ""
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

                        // Asked for, not invented. This used to send a
                        // hard-coded literal purely to satisfy the server's
                        // at-least-one-service check, so every salon created on
                        // iOS had exactly one service with a name its owner had
                        // never typed — and that name is what customers browse
                        // and what the category matcher reads.
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                BrandField(label: .serviceName, text: $serviceInput)
                                Button(L.add.t) { addService() }
                                    .font(Brand.font(14, .medium))
                                    .foregroundStyle(Brand.accent)
                                    .disabled(serviceInput.trimmingCharacters(in: .whitespaces).isEmpty)
                            }
                            if !services.isEmpty {
                                FlowLayout(spacing: 8) {
                                    ForEach(services, id: \.self) { name in
                                        Button {
                                            services.removeAll { $0 == name }
                                        } label: {
                                            HStack(spacing: 5) {
                                                Text(name).font(Brand.font(13))
                                                Image(systemName: "xmark").font(.system(size: 9))
                                            }
                                            .foregroundStyle(Brand.deep)
                                            .padding(.horizontal, 11).padding(.vertical, 6)
                                            .background(Brand.petal.opacity(0.45), in: Capsule())
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }

                    ErrorBanner(message: error)

                    BrandButton(title: .register, isLoading: auth.isWorking) {
                        Task { await submit() }
                    }
                    .padding(.top, 4)

                    Button(L.haveAccount.t) { dismiss() }
                        .font(Brand.font(14, .medium))
                        .foregroundStyle(Brand.accent)
                        .disabled(auth.isWorking)

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
                    // Disabled while the call is in flight. registerAccount is
                    // not cancellable once it has reached the server — it will
                    // create the account regardless — so letting her dismiss
                    // mid-call means the handoff callback lands on a sign-in
                    // screen she has already started typing into, overwriting
                    // the phone and blanking the password under her hands.
                    Button(L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                        .disabled(auth.isWorking)
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
        // registerAccount requires a district for a provider and refuses with
        // invalid-argument, which this screen renders as "check your internet"
        // — pointing a salon owner at her connection while the real problem is
        // an empty field on the form in front of her.
        if isProvider && district.trimmingCharacters(in: .whitespaces).isEmpty {
            return .errDistrictRequired
        }
        if isProvider && services.isEmpty { return .errServicesRequired }
        return nil
    }

    private func addService() {
        let name = serviceInput.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty, !services.contains(name) else { return }
        services.append(name)
        serviceInput = ""
    }

    private func submit() async {
        if let problem = validate() { error = problem.t; return }
        error = nil
        do {
            try await auth.register(
                name: name, phone: phone, email: email, password: password,
                isProvider: isProvider, salonName: salonName, district: district,
                services: isProvider ? services : []
            )
            dismiss()
        } catch AuthService.AuthError.registeredButNotSignedIn {
            // Her account exists. Everything on this screen is now the wrong
            // thing to offer her, so hand her to sign-in rather than leaving
            // her to work that out from an error message.
            //
            // Normalised, not raw. register() stored the number under
            // normalizeAfghan, while sign-in resolves it with normalizeForLogin
            // — and those two disagree on anything typed with a leading "+"
            // that isn't "+93": normalizeForLogin returns it untouched as an
            // international number. Handing back the raw text would prefill a
            // number that resolves to no account, and re-registering would come
            // back phoneTaken. Both exits closed, on the account she just made.
            onNeedsSignIn(PhoneUtils.normalizeAfghan(phone))
            dismiss()
        } catch let e as AuthService.AuthError {
            error = SignInView.message(for: e)
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
