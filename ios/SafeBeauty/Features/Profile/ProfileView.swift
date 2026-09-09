import SwiftUI
import PhotosUI
import FirebaseFirestore
import SafeBeautyCore

/// Her account: who she is to the product, what she is owed, and how to leave.
///
/// Read straight from her own users document, which the rules permit
/// (`allow get: if isSignedIn() && ownsDoc(uid)`) and nothing else. The wallet
/// and loyalty figures are server-written and displayed as found — a client
/// that recomputed a balance would eventually disagree with the one money is
/// actually settled against.
struct ProfileView: View {
    @Environment(AuthService.self) private var auth

    @State private var referralCredit = 0
    @State private var loyaltyPoints = 0
    @State private var referralCode = ""
    @State private var phone = ""
    @State private var showKyc = false
    @State private var showBlocked = false
    @State private var biometryName = BiometricVault.biometryName
    @Environment(Moderation.self) private var moderation
    @State private var confirmSignOut = false
    @State private var confirmDelete = false
    @State private var deleting = false
    @State private var deleteError: String?
    @State private var photoItem: PhotosPickerItem?
    /// Her own bookings, for the export. Read here rather than passed in: this
    /// screen is the only place that needs them and the list is her own.
    @State private var bookings = BookingsRepository()
    @State private var exportURL: URL?
    @State private var photos = ProfilePhotoService()
    @State private var photoUrl = ""
    @State private var photoNote: String?
    @State private var photoError: String?
    @State private var showSupport = false
    @State private var showChangePassword = false
    @State private var showEditName = false
    @State private var showRedeem = false
    @State private var showTopUp = false
    @State private var showGift = false
    @State private var lang = LanguageStore.shared
    @State private var theme = ThemeStore.shared

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    identityCard
                    walletCard
                    if !referralCode.isEmpty { inviteCard }
                    verificationRow

                    VStack(alignment: .leading, spacing: 8) {
                        Text(L.language.t).font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.ink.opacity(0.75))
                        @Bindable var lang = lang
                        Picker("", selection: $lang.current) {
                            ForEach(AppLanguage.allCases) { Text(verbatim: $0.endonym).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding(15)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))

                    // The account actions, which the profile showed the results
                    // of and gave no way to change: her name was displayed and
                    // not editable, and there was no path to a password change
                    // on this platform at all.
                    themeCard

                    Button { showEditName = true } label: {
                        accountRow("person.text.rectangle", L.editName.t)
                    }
                    .buttonStyle(.plain)

                    Button { showChangePassword = true } label: {
                        accountRow("lock.rotation", L.changePassword.t)
                    }
                    .buttonStyle(.plain)

                    // Buying credit for someone else, which Android offers here
                    // and iOS did not offer anywhere. It is also the one thing
                    // in this app a woman can do FOR another woman.
                    Button { showGift = true } label: {
                        accountRow("gift.fill", L.giftCard.t)
                    }
                    .buttonStyle(.plain)

                    Button { showSupport = true } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "bubble.left.fill").foregroundStyle(Brand.accent)
                            Text(L.support.t).font(Brand.font(14.5, .medium))
                                .foregroundStyle(Brand.ink)
                            Spacer()
                            Image(systemName: "chevron.forward").font(.system(size: 12))
                                .foregroundStyle(Brand.accent)
                        }
                        .padding(15)
                        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)

                    // Only once she has actually blocked someone. An empty
                    // list is a row that teaches her the app has a feature she
                    // does not need.
                    if !moderation.blocked.isEmpty {
                        Button { showBlocked = true } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "hand.raised.fill")
                                    .foregroundStyle(Brand.accent)
                                Text(L.blockedTitle.t).font(Brand.font(14.5, .medium))
                                    .foregroundStyle(Brand.ink)
                                Spacer()
                                Text(verbatim: "\(moderation.blocked.count)")
                                    .font(Brand.font(13)).foregroundStyle(Brand.textMuted)
                                Image(systemName: "chevron.forward").font(.system(size: 12))
                                    .foregroundStyle(Brand.accent)
                            }
                            .padding(15)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
                        }
                        .buttonStyle(.plain)
                    }

                    // Turning it off without signing out. Only shown when
                    // there is something to turn off, so it is not a row
                    // explaining a feature she has not used.
                    if BiometricVault.isEnabled, biometryName != nil {
                        Button {
                            BiometricVault.disable()
                            biometryName = BiometricVault.biometryName
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "faceid").foregroundStyle(Brand.accent)
                                Text(L.biometricTurnOff.t).font(Brand.font(14.5, .medium))
                                    .foregroundStyle(Brand.ink)
                                Spacer()
                            }
                            .padding(15)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
                        }
                        .buttonStyle(.plain)
                    }

                    Button(role: .destructive) { confirmSignOut = true } label: {
                        Text(L.signOut.t)
                            .font(Brand.font(15, .medium))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))
                    }
                    .padding(.top, 6)

                    // Her booking history as a file she can keep, which Android
                    // has had and iOS had not. A ShareLink rather than a button
                    // and a sheet: iOS gives her every destination she already
                    // uses, and the file is written before the sheet opens so
                    // there is nothing to fail once it is up.
                    if let exportURL {
                        ShareLink(item: exportURL) {
                            HStack(spacing: 10) {
                                Image(systemName: "square.and.arrow.up")
                                    .foregroundStyle(Brand.accent)
                                Text(L.exportTitle.t)
                                    .font(Brand.font(14.5, .medium))
                                    .foregroundStyle(Brand.ink)
                                Spacer()
                            }
                            .padding(15)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
                        }
                    }

                    // Terms and privacy, which iOS linked from nowhere.
                    //
                    // Android has had them since it shipped, in Support and on
                    // the registration screen; the pages themselves are live at
                    // safebeauty.web.app and are the app-facing site the deploy
                    // notes say must stay where it is. Opened in Safari rather
                    // than in a web view: a policy shown inside the app it
                    // describes is a policy the app could have rewritten.
                    HStack(spacing: 0) {
                        legalLink(L.legalTermsLabel.t, "https://safebeauty.web.app/terms")
                        Divider().frame(height: 22).background(Brand.petal)
                        legalLink(L.legalPrivacyLabel.t, "https://safebeauty.web.app/privacy")
                    }
                    .background(Brand.surface, in: RoundedRectangle(cornerRadius: 14))
                    .padding(.top, 4)

                    ErrorBanner(message: deleteError)

                    // Below sign-out and quieter than it. requestAccountDeletion
                    // was deployed with no caller on this platform, so an iPhone
                    // customer could not close her own account — which is also
                    // something the App Store requires of any app that lets her
                    // open one.
                    Button { confirmDelete = true } label: {
                        HStack(spacing: 6) {
                            if deleting { ProgressView().tint(Brand.accent) }
                            Text(L.deleteAccount.t)
                                .font(Brand.font(13.5, .medium))
                        }
                        .foregroundStyle(Brand.danger)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                    }
                    .buttonStyle(.plain)
                    .disabled(deleting)
                    .padding(.bottom, 30)
                }
                .padding(.horizontal, 22)
                .padding(.top, 14)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.profile.t)
            .task(id: auth.session?.uid) {
                if let uid = auth.session?.uid { bookings.start(customerId: uid) }
            }
            .onChange(of: bookings.upcoming.count + bookings.past.count) { _, _ in
                refreshExport()
            }
            .sheet(isPresented: $showKyc) { KycView().appDirection() }
            .sheet(isPresented: $showBlocked) {
                BlockedAccountsSheet(moderation: moderation).appDirection()
            }
            .sheet(isPresented: $showSupport) { SupportView().appDirection() }
            .sheet(isPresented: $showChangePassword) { ChangePasswordSheet().appDirection() }
            .sheet(isPresented: $showEditName) { EditNameSheet().appDirection() }
            .sheet(isPresented: $showRedeem) {
                RedeemPointsSheet(available: loyaltyPoints) { Task { await load() } }.appDirection()
            }
            // Both reload the profile on dismiss: the webhook credits her while
            // she is on HesabPay, so the balance she comes back to should be the
            // new one rather than the one she left.
            .sheet(isPresented: $showTopUp, onDismiss: { Task { await load() } }) {
                TopUpSheet().appDirection()
            }
            .sheet(isPresented: $showGift, onDismiss: { Task { await load() } }) {
                GiftCardSheet().appDirection()
            }
            .alert(L.deleteAccount.t, isPresented: $confirmDelete) {
                Button(L.cancel.t, role: .cancel) {}
                Button(L.deleteAccountConfirm.t, role: .destructive) {
                    Task { await deleteAccount() }
                }
            } message: {
                // Says what it costs before she agrees, not after. The callable
                // cancels her upcoming appointments and closes the account, and
                // neither of those comes back.
                Text(L.deleteAccountWarning.t)
            }
            .alert(L.signOut.t, isPresented: $confirmSignOut) {
                Button(L.cancel.t, role: .cancel) {}
                Button(L.signOut.t, role: .destructive) { auth.signOut() }
            } message: {
                Text(L.signOutWarning.t)
            }
        }
        .task(id: auth.session?.uid) { await load() }
    }

    private var identityCard: some View {
        VStack(spacing: 8) {
            // Her photograph, with the pencil Android has had all along. The
            // letter tile stays as the fallback, because most accounts have no
            // photo and an empty grey circle says less than an initial does.
            PhotosPicker(selection: $photoItem, matching: .images) {
                ZStack(alignment: .bottomTrailing) {
                    Group {
                        if let url = URL(string: photoUrl), !photoUrl.isEmpty {
                            AsyncImage(url: url) { phase in
                                if case .success(let image) = phase {
                                    image.resizable().scaledToFill()
                                } else {
                                    letterAvatar
                                }
                            }
                        } else {
                            letterAvatar
                        }
                    }
                    .frame(width: 66, height: 66)
                    .clipShape(Circle())

                    if photos.isUploading {
                        ProgressView().tint(.white)
                            .frame(width: 24, height: 24)
                            .background(Brand.deep, in: Circle())
                    } else {
                        Image(systemName: "pencil")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 24, height: 24)
                            .background(Brand.deep, in: Circle())
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(photos.isUploading)
            .accessibilityLabel(L.changePhoto.t)
            Text(auth.session?.name ?? "")
                .font(Brand.font(19, .bold)).foregroundStyle(Brand.ink)
            if !phone.isEmpty {
                // A phone number reads left-to-right in every language.
                Text(phone)
                    .font(Brand.font(13))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.accent)
            }
            if let photoNote {
                Text(photoNote)
                    .font(Brand.font(12.5)).foregroundStyle(Brand.success)
                    .multilineTextAlignment(.center)
            }
            ErrorBanner(message: photoError)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 18)
        .padding(.horizontal, 14)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
        .onChange(of: photoItem) { _, item in
            Task { await uploadPhoto(item) }
        }
    }

    private var letterAvatar: some View {
        Brand.gradient.overlay(
            Text(String(auth.session?.name.prefix(1) ?? ""))
                .font(Brand.font(26, .bold)).foregroundStyle(.white)
        )
    }

    /// Uploading also completes the profile, so the reward is claimed in the
    /// same breath rather than waiting for a screen she may never open again.
    private func uploadPhoto(_ item: PhotosPickerItem?) async {
        guard let item, let uid = auth.session?.uid else { return }
        photoError = nil; photoNote = nil
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else {
            photoError = L.photoUploadFailed.t
            return
        }
        do {
            let points = try await photos.upload(image, uid: uid)
            await load()
            if points > 0 { photoNote = L.rewardEarned(points) }
        } catch ProfilePhotoService.PhotoError.tooLarge {
            photoError = L.photoTooLarge.t
        } catch {
            photoError = L.photoUploadFailed.t
        }
    }

    private var walletCard: some View {
        HStack(spacing: 0) {
            // The balance and, under it, the way to add to it. It was a figure
            // she could read and not change: createWalletTopUp was deployed
            // with no caller on this platform.
            Button { showTopUp = true } label: {
                VStack(spacing: 3) {
                    statTile(L.walletCredit.t, "\(referralCredit)", suffix: L.afn.t)
                    Text(L.topUp.t)
                        .font(Brand.font(11.5, .medium))
                        .foregroundStyle(Brand.deep)
                }
            }
            .buttonStyle(.plain)
            Divider().frame(height: 40).overlay(Brand.petal.opacity(0.5))
            // Not disabled below the threshold. A greyed-out STAT reads as a
            // number that failed to load; the figure is correct either way, so
            // it stays at full strength and only gains a tappable caption once
            // there is actually something to spend.
            if loyaltyPoints >= 100 {
                Button { showRedeem = true } label: {
                    VStack(spacing: 3) {
                        statTile(L.loyaltyPoints.t, "\(loyaltyPoints)", suffix: nil)
                        Text(L.redeem.t)
                            .font(Brand.font(11.5, .medium))
                            .foregroundStyle(Brand.deep)
                    }
                }
                .buttonStyle(.plain)
            } else {
                statTile(L.loyaltyPoints.t, "\(loyaltyPoints)", suffix: nil)
            }
        }
        .padding(.vertical, 15)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    /// One row shape for every account action, so they read as a set rather
    /// than as three buttons that happen to be near each other.
    /// Colour and light, the way Android offers them. A woman who picked
    /// Lavender on her phone and saw Rose on an iPhone would think it was a
    /// different product.
    private var themeCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L.theme.t).font(Brand.font(13, .medium))
                .foregroundStyle(Brand.textMuted)
            swatchRow
            appearanceRow
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var swatchRow: some View {
        HStack(spacing: 10) {
            ForEach(AppBrandTheme.allCases) { b in
                Button { theme.brand = b } label: {
                    Circle()
                        .fill(swatch(b))
                        .frame(width: 34, height: 34)
                        .overlay(Circle().strokeBorder(
                            theme.brand == b ? Brand.ink : Color.clear, lineWidth: 2.5))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(themeName(b))
                .accessibilityAddTraits(theme.brand == b ? [.isSelected] : [])
            }
        }
    }

    private var appearanceRow: some View {
        @Bindable var theme = theme
        return Picker("", selection: $theme.appearance) {
            Text(L.appearanceSystem.t).tag(ThemeStore.Appearance.system)
            Text(L.appearanceLight.t).tag(ThemeStore.Appearance.light)
            Text(L.appearanceDark.t).tag(ThemeStore.Appearance.dark)
        }
        .pickerStyle(.segmented)
    }

    /// The swatch shows the brand's own rose, taken from its light palette so
    /// the six read as six colours rather than six shades of the current one.
    private func swatch(_ b: AppBrandTheme) -> Color {
        allPalettes[b.rawValue.prefix(1).uppercased() + b.rawValue.dropFirst() + "Light"]?
            .roseGold ?? Brand.accent
    }

    private func themeName(_ b: AppBrandTheme) -> String {
        switch b {
        case .rose: L.themeRose.t
        case .lavender: L.themeLavender.t
        case .sage: L.themeSage.t
        case .ocean: L.themeOcean.t
        case .honey: L.themeHoney.t
        case .maroon: L.themeMaroon.t
        }
    }

    /// Closes the account, then signs out — in that order.
    ///
    /// Signing out first would leave her authenticated as nobody with the
    /// request still in flight, and a failed call would then have closed
    /// nothing while she was already out of the app with no way back in to try
    /// again.
    private func deleteAccount() async {
        deleting = true; defer { deleting = false }
        deleteError = nil
        do {
            _ = try await Callables.call("requestAccountDeletion")
            auth.signOut()
        } catch let e as Callables.CallableError {
            deleteError = e.localized ?? L.errNetwork.t
        } catch {
            deleteError = L.errNetwork.t
        }
    }

    private func accountRow(_ icon: String, _ title: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundStyle(Brand.accent)
            Text(title).font(Brand.font(14.5, .medium)).foregroundStyle(Brand.ink)
            Spacer()
            Image(systemName: "chevron.forward").font(.system(size: 12))
                .foregroundStyle(Brand.accent)
        }
        .padding(15)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private func statTile(_ label: String, _ value: String, suffix: String?) -> some View {
        VStack(spacing: 4) {
            HStack(spacing: 4) {
                Text(value)
                    .font(Brand.font(21, .bold))
                    .environment(\.layoutDirection, .leftToRight)
                if let suffix {
                    Text(suffix).font(Brand.font(12)).foregroundStyle(Brand.accent)
                }
            }
            .foregroundStyle(Brand.ink)
            Text(label).font(Brand.font(12)).foregroundStyle(Brand.accent)
        }
        .frame(maxWidth: .infinity)
    }

    private var inviteCard: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(L.yourInviteCode.t).font(Brand.font(13, .medium))
                .foregroundStyle(Brand.ink.opacity(0.75))
            HStack {
                Text(referralCode)
                    .font(.system(size: 19, weight: .bold, design: .monospaced))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.deep)
                Spacer()
                // Copy, not share. A share sheet posts it somewhere public by
                // default, and the invite code is tied to one account's credit —
                // it is hers to hand to someone, not to broadcast.
                Button {
                    UIPasteboard.general.string = referralCode
                } label: {
                    Image(systemName: "doc.on.doc").foregroundStyle(Brand.accent)
                }
            }
            Text(L.inviteExplain.t)
                .font(Brand.font(11.5)).foregroundStyle(Brand.accent)
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private var verificationRow: some View {
        let status = auth.session?.kycStatus ?? "NONE"
        Button {
            // Only NONE and REJECTED can act. PENDING is waiting on a person
            // and APPROVED is done, and submitKyc refuses both — so the row
            // shows state rather than offering a button that would be refused.
            if status == "NONE" || status == "REJECTED" { showKyc = true }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: status == "APPROVED" ? "checkmark.shield.fill"
                                : status == "PENDING" ? "clock.fill" : "shield")
                    .foregroundStyle(status == "APPROVED" ? Brand.deep : Brand.gold)
                Text(L.verifyIdentity.t).font(Brand.font(14.5, .medium)).foregroundStyle(Brand.ink)
                Spacer()
                Text(statusLabel(status)).font(Brand.font(13)).foregroundStyle(Brand.accent)
                if status == "NONE" || status == "REJECTED" {
                    Image(systemName: "chevron.forward").font(.system(size: 12))
                        .foregroundStyle(Brand.accent)
                }
            }
            .padding(15)
            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private func statusLabel(_ s: String) -> String {
        switch s {
        case "APPROVED": L.kycApproved.t
        case "PENDING": L.kycPending.t
        case "REJECTED": L.kycRejected.t
        default: L.kycNone.t
        }
    }

    @ViewBuilder
    private func legalLink(_ title: String, _ url: String) -> some View {
        Link(destination: URL(string: url)!) {
            Text(title)
                .font(Brand.font(13.5, .medium))
                .foregroundStyle(Brand.accent)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .contentShape(Rectangle())
        }
    }

    /// Writes the CSV to a temp file so ShareLink has something to hand over.
    ///
    /// Rewritten whenever the booking list changes, because a share sheet
    /// offering yesterday's file is worse than no button: she would not know
    /// it was stale.
    private func refreshExport() {
        // Everything, newest first — the export is a record, so a booking
        // being in the past is exactly why she wants it in the file.
        let all = (bookings.upcoming + bookings.past)
            .sorted { $0.appointmentDate > $1.appointmentDate }
        let csv = BookingExport.csv(all)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(BookingExport.filename)
        exportURL = (try? csv.write(to: url, atomically: true, encoding: .utf8)) == nil
            ? nil : url
    }

    private func load() async {
        guard let uid = auth.session?.uid, !uid.isEmpty,
              let snap = try? await Firestore.firestore().document("users/\(uid)").getDocument(),
              let d = snap.data()
        else { return }
        // Displayed as the server wrote them. referralCredit is real money she
        // can spend at checkout, and loyaltyPoints convert into it at 100 —
        // neither is a number this app should be deriving.
        referralCredit = (d["referralCredit"] as? Int) ?? Int((d["referralCredit"] as? Double) ?? 0)
        loyaltyPoints = (d["loyaltyPoints"] as? Int) ?? 0
        referralCode = (d["referralCode"] as? String) ?? ""
        phone = (d["phone"] as? String) ?? ""
        photoUrl = (d["profilePhotoUrl"] as? String) ?? ""
    }
}
