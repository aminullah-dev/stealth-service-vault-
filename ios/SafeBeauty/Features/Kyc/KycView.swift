import SwiftUI
import PhotosUI
import SafeBeautyCore

/// Identity verification.
///
/// The server refuses a booking until kycStatus is APPROVED, so for a new
/// customer this screen stands between her and the product. It is also where
/// she is asked for a photograph of her tazkira, which is the most sensitive
/// thing this product ever holds — so it says what happens to it, in her
/// language, before she is asked rather than after.
struct KycView: View {
    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var kyc = KycService()
    @State private var tazkiraItem: PhotosPickerItem?
    @State private var selfieItem: PhotosPickerItem?
    @State private var tazkiraImage: UIImage?
    @State private var selfieImage: UIImage?

    @State private var tazkiraNumber = ""
    @State private var province = ""
    @State private var addressDetail = ""
    @State private var error: String?
    @State private var submitted = false

    private var canSubmit: Bool {
        tazkiraImage != nil && selfieImage != nil
            && !tazkiraNumber.trimmingCharacters(in: .whitespaces).isEmpty
            && !province.trimmingCharacters(in: .whitespaces).isEmpty
            && !addressDetail.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if submitted {
                    submittedState
                } else {
                    form
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.verifyIdentity.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.close.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
            }
        }
        .onChange(of: tazkiraItem) { _, item in Task { tazkiraImage = await load(item) } }
        .onChange(of: selfieItem) { _, item in Task { selfieImage = await load(item) } }
    }

    @ViewBuilder
    private var form: some View {
        VStack(alignment: .leading, spacing: 18) {
            // Said before she is asked, not in a policy she will not open.
            Text(L.kycWhy.t)
                .font(Brand.font(13.5))
                .foregroundStyle(Brand.deep)
                .padding(13)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Brand.petal.opacity(0.22), in: RoundedRectangle(cornerRadius: 12))

            PhotoSlot(title: .tazkiraPhoto, image: tazkiraImage, selection: $tazkiraItem)
            PhotoSlot(title: .selfiePhoto, image: selfieImage, selection: $selfieItem)

            BrandField(label: .tazkiraNumber, text: $tazkiraNumber)
            BrandField(label: .province, text: $province)
            BrandField(label: .addressDetail, text: $addressDetail)

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

            BrandButton(title: .submitVerification,
                        isLoading: kyc.isWorking, isEnabled: canSubmit) {
                Task { await submit() }
            }
            .padding(.bottom, 30)
        }
        .padding(.horizontal, 22)
        .padding(.top, 14)
    }

    private var stepLabel: String {
        switch kyc.step {
        case .compressing: L.kycPreparing.t
        case .uploadingTazkira, .uploadingSelfie: L.kycUploading.t
        case .submitting: L.kycSubmitting.t
        case .idle: ""
        }
    }

    @ViewBuilder
    private var submittedState: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.shield.fill")
                .font(.system(size: 44)).foregroundStyle(Brand.deep)
            Text(L.kycSubmitted.t)
                .font(Brand.font(16, .medium))
                .foregroundStyle(Brand.ink)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 34)
        .padding(.top, 70)
    }

    private func load(_ item: PhotosPickerItem?) async -> UIImage? {
        guard let item,
              let data = try? await item.loadTransferable(type: Data.self)
        else { return nil }
        return UIImage(data: data)
    }

    private func submit() async {
        guard let uid = auth.session?.uid,
              let tazkira = tazkiraImage, let selfie = selfieImage else { return }
        error = nil
        do {
            try await kyc.submit(
                uid: uid, tazkira: tazkira, selfie: selfie,
                tazkiraNumber: tazkiraNumber, addressProvince: province,
                addressDetail: addressDetail)
            submitted = true
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
                        .fill(.white)
                        .overlay(RoundedRectangle(cornerRadius: 13)
                            .strokeBorder(Brand.petal.opacity(0.6), lineWidth: 1))

                    if let image {
                        Image(uiImage: image)
                            .resizable().scaledToFill()
                            .clipShape(RoundedRectangle(cornerRadius: 13))
                    } else {
                        VStack(spacing: 6) {
                            Image(systemName: "camera.fill").font(.system(size: 22))
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
