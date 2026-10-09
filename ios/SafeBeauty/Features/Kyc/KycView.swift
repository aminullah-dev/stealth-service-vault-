import SwiftUI
import PhotosUI
import SafeBeautyCore

/// Identity verification, for both people who need it.
///
/// A customer reaches it as a sheet — from Profile, or from a salon page,
/// because the server refuses a booking until kycStatus is APPROVED. A salon
/// owner reaches it as the whole app: `AccountGate` sends a provider whose
/// account is approved but whose identity is not here instead of to her salon,
/// exactly as Android's sign-in gate does. Before that branch existed an iOS
/// salon owner skipped this screen entirely, and the server let her.
///
/// It renders what Android's KycScreen renders, from her live document rather
/// than from the session read at sign-in: the form (NONE / REJECTED, with the
/// admin's reason and Resubmit), "under review" (PENDING), or "verified"
/// (APPROVED). It is also where she is asked for a photograph of her tazkira,
/// the most sensitive thing this product holds, so it says what happens to it
/// before she is asked rather than after.
struct KycView: View {
    enum Presentation { case sheet, gate }
    var presentation: Presentation = .sheet

    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var kyc = KycService()
    @State private var watcher = KycStatusWatcher()
    @State private var tazkiraItem: PhotosPickerItem?
    @State private var selfieItem: PhotosPickerItem?
    @State private var tazkiraImage: UIImage?
    @State private var selfieImage: UIImage?
    @State private var showCamera = false
    @State private var showSupport = false

    @State private var tazkiraNumber = ""
    @State private var birthYear = ""
    @State private var issueDate = ""
    @State private var expiryDate = ""
    @State private var province = ""
    @State private var addressDetail = ""
    @State private var error: String?
    /// Set the moment submitKyc returns, so "under review" shows at once
    /// instead of the form flashing back while the snapshot is on its way.
    @State private var submitted = false

    /// The live value once it has arrived; the session's until then.
    private var status: String {
        if submitted && watcher.status != "APPROVED" { return "PENDING" }
        return watcher.status ?? auth.session?.kycStatus ?? "NONE"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    switch status {
                    case "PENDING":
                        statusCard(icon: "hourglass", tint: Brand.gold,
                                   title: L.kycPendingTitle, body: L.kycPendingText)
                    case "APPROVED":
                        statusCard(icon: "checkmark.shield.fill", tint: Brand.success,
                                   title: L.kycApprovedTitle, body: L.kycApprovedText)
                        BrandButton(title: .kycContinue) { Task { await finish() } }
                    default:
                        form
                    }
                    if presentation == .gate { gateFooter }
                }
                .padding(.horizontal, 22)
                .padding(.top, 14)
                .padding(.bottom, 30)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.verifyIdentity.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if presentation == .sheet {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(L.close.t) { dismiss() }.foregroundStyle(Brand.accent)
                    }
                }
            }
        }
        .task(id: auth.session?.uid) {
            if let uid = auth.session?.uid { watcher.start(uid: uid) }
        }
        .onDisappear { watcher.stop() }
        .onChange(of: watcher.status) { _, new in
            // Keep the session in step with the document, so Profile and the
            // salon page agree with this screen. Except APPROVED at the gate:
            // there a refreshed session would swap this screen for the salon app
            // mid-sentence, and Android shows "verified" with an OK first.
            // The document has caught up with the submission; from here on it
            // speaks for itself, including a later rejection.
            if new == "PENDING" { submitted = false }
            guard let new, new != auth.session?.kycStatus else { return }
            if presentation == .sheet || new != "APPROVED" { Task { await auth.refresh() } }
        }
        .onChange(of: tazkiraItem) { _, item in Task { tazkiraImage = await load(item) } }
        .onChange(of: selfieItem) { _, item in Task { selfieImage = await load(item) } }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { image in
                if let image { selfieImage = image; error = nil }
                showCamera = false
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $showSupport) { SupportView().appDirection() }
    }

    // MARK: Form

    @ViewBuilder
    private var form: some View {
        // Said before she is asked, not in a policy she will not open.
        Text(presentation == .gate ? L.kycWhyProvider.t : L.kycWhy.t)
            .font(Brand.font(13.5))
            .foregroundStyle(Brand.deep)
            .padding(13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.petal.opacity(0.22), in: RoundedRectangle(cornerRadius: 12))

        if status == "REJECTED" {
            // The admin's reason, which reviewKyc requires and iOS never showed:
            // without it a rejected woman resubmits the same blurred photograph.
            VStack(alignment: .leading, spacing: 4) {
                Text(L.kycRejectedTitle.t).font(Brand.font(14, .bold))
                if !watcher.rejectionReason.isEmpty {
                    Text(L.kycRejectedReason(watcher.rejectionReason)).font(Brand.font(13))
                }
            }
            .foregroundStyle(Brand.danger)
            .padding(13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.danger.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
        }

        BrandField(label: .tazkiraNumber, text: $tazkiraNumber)
        BrandField(label: .kycBirthYear, text: $birthYear)
        BrandField(label: .kycIssueDate, text: $issueDate)
        BrandField(label: .kycExpiryDate, text: $expiryDate)
        BrandField(label: .province, text: $province)
        BrandField(label: .addressDetail, text: $addressDetail)

        PhotoSlot(title: .tazkiraPhoto, image: tazkiraImage, selection: $tazkiraItem)
        PhotoSlot(title: .selfiePhoto, image: selfieImage, selection: $selfieItem)
        if CameraPicker.isAvailable {
            // The camera, as Android takes it. The library stays available in
            // the slot above for a phone whose front camera is broken.
            Button { showCamera = true } label: {
                Label(L.kycTakeSelfie.t, systemImage: "camera")
                    .font(Brand.font(14, .medium))
                    .foregroundStyle(Brand.accent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(Brand.surface, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
        }

        ErrorBanner(message: error)

        if kyc.isWorking {
            // Named steps rather than a spinner: uploading two photographs
            // on Afghan mobile data is slow enough that "still working"
            // needs to be distinguishable from "stuck".
            HStack(spacing: 8) {
                ProgressView().tint(Brand.accent)
                Text(stepLabel).font(Brand.font(13)).foregroundStyle(Brand.accent)
            }
        }

        BrandButton(title: status == "REJECTED" ? L.kycResubmit : L.submitVerification,
                    isLoading: kyc.isWorking) {
            Task { await submit() }
        }
    }

    private var stepLabel: String {
        switch kyc.step {
        case .compressing: L.kycPreparing.t
        case .uploadingTazkira, .uploadingSelfie: L.kycUploading.t
        case .submitting: L.kycSubmitting.t
        case .idle: ""
        }
    }

    // MARK: States

    private func statusCard(icon: String, tint: Color, title: L, body: L) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 44)).foregroundStyle(tint)
            Text(title.t).font(Brand.font(18, .bold)).foregroundStyle(Brand.ink)
            Text(body.t)
                .font(Brand.font(14.5))
                .foregroundStyle(Brand.deep)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
        .padding(.top, 30)
    }

    /// At the gate this screen is the whole app, so the two doors that must
    /// stay open are here: support, and signing out on a shared phone.
    private var gateFooter: some View {
        VStack(spacing: 12) {
            Button(L.support.t) { showSupport = true }
                .font(Brand.font(15, .medium)).foregroundStyle(Brand.accent)
            Button(L.signOut.t) { auth.signOut() }
                .font(Brand.font(15, .medium)).foregroundStyle(Brand.accent)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 18)
    }

    // MARK: Actions

    private func finish() async {
        // Re-read her document so the session says APPROVED: at the gate that
        // is what opens the salon app; in a sheet it is what unlocks Book.
        await auth.refresh()
        if presentation == .sheet { dismiss() }
    }

    private func load(_ item: PhotosPickerItem?) async -> UIImage? {
        guard let item else { return nil }
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data)
        else {
            // Said, not swallowed. Android learned this the hard way: a photo
            // the app quietly refused left the row reading "not chosen", and
            // she picked the same one again.
            error = L.kycErrPhotoUnreadable.t
            return nil
        }
        error = nil
        return image
    }

    private func submit() async {
        if let problem = KycForm.firstProblem(
            tazkiraNumber: tazkiraNumber, province: province, address: addressDetail,
            hasTazkiraPhoto: tazkiraImage != nil, hasSelfie: selfieImage != nil) {
            error = switch problem {
            case .tazkiraNumberRequired: L.kycErrTazkiraNumber.t
            case .provinceRequired: L.kycErrProvince.t
            case .addressRequired: L.kycErrAddress.t
            case .tazkiraPhotoRequired: L.kycErrTazkiraPhoto.t
            case .selfieRequired: L.kycErrSelfie.t
            }
            return
        }
        guard let uid = auth.session?.uid,
              let tazkira = tazkiraImage, let selfie = selfieImage else { return }
        error = nil
        do {
            try await kyc.submit(
                uid: uid, tazkira: tazkira, selfie: selfie,
                tazkiraNumber: tazkiraNumber, addressProvince: province,
                addressDetail: addressDetail,
                birthYear: birthYear.trimmingCharacters(in: .whitespaces),
                issueDate: issueDate.trimmingCharacters(in: .whitespaces),
                expiryDate: expiryDate.trimmingCharacters(in: .whitespaces))
            submitted = true
            tazkiraImage = nil; selfieImage = nil
            tazkiraItem = nil; selfieItem = nil
            // The server has just moved her to PENDING. The listener will say
            // so too; this makes the session agree without waiting for it.
            await auth.refresh()
        } catch let e as KycService.KycError {
            error = switch e {
            case .imageTooLarge: L.kycErrTooLarge.t
            case .uploadDenied: L.kycErrUpload.t
            case .alreadyUnderReview: L.kycErrUnderReview.t
            case .alreadyVerified: L.kycErrAlreadyVerified.t
            case .missingFields: L.kycErrMissing.t
            case .network: L.errNetwork.t
            }
        } catch {
            self.error = L.errNetwork.t
        }
    }
}

/// One photograph, with its own preview.
///
/// The preview exists so she can see what she is about to send. A blurred
/// tazkira means a rejected verification and a second trip, and the moment to
/// notice that is before uploading, not days later.
struct PhotoSlot: View {
    let title: L
    let image: UIImage?
    @Binding var selection: PhotosPickerItem?

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title.t).font(Brand.font(13, .medium)).foregroundStyle(Brand.ink.opacity(0.75))

            PhotosPicker(selection: $selection, matching: .images) {
                ZStack {
                    RoundedRectangle(cornerRadius: 13)
                        .fill(Brand.surface)
                        .overlay(RoundedRectangle(cornerRadius: 13)
                            .strokeBorder(Brand.petal.opacity(0.6), lineWidth: 1))

                    if let image {
                        Image(uiImage: image)
                            .resizable().scaledToFill()
                            .clipShape(RoundedRectangle(cornerRadius: 13))
                    } else {
                        VStack(spacing: 6) {
                            Image(systemName: "photo.on.rectangle").font(.system(size: 22))
                            Text(L.choosePhoto.t).font(Brand.font(13))
                        }
                        .foregroundStyle(Brand.accent)
                    }
                }
                .frame(height: 150)
                .clipped()
            }
        }
    }
}
