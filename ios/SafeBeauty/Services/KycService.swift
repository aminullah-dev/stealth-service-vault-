import Foundation
import UIKit
import FirebaseFirestore
import FirebaseStorage
import SafeBeautyCore

/// Identity verification: two photographs and a few fields.
///
/// This handles the most sensitive data in the product — photographs of Afghan
/// women's identity documents — so the shape of it matters more than the size.
///
/// The photo LOCATIONS are never sent. `submitKyc` derives them from the
/// caller's own uid (`kyc/{uid}/tazkira.jpg`) and checks the files exist before
/// recording anything. An earlier design accepted paths from the client, which
/// is a request to be handed someone else's document; the server was changed to
/// derive them, and this client must not reintroduce the parameter by being
/// helpful.
///
/// `storage.rules` allows the write only where `uid == appUid()`, so a session
/// whose uid_map bridge is missing cannot upload at all. That is the correct
/// failure and it is reported rather than retried into silence.
@MainActor
@Observable
final class KycService {

    enum Step: Equatable, Sendable {
        case idle
        case compressing
        case uploadingTazkira
        case uploadingSelfie
        case submitting
    }

    enum KycError: LocalizedError, Equatable {
        case imageTooLarge
        case uploadDenied
        case alreadyUnderReview
        case alreadyVerified
        case missingFields
        case network

        var errorDescription: String? { String(describing: self) }
    }

    private(set) var step: Step = .idle
    var isWorking: Bool { step != .idle }

    /// Firebase Storage enforces 2 MB and raster-only. Compressing to fit here
    /// rather than letting the upload be rejected means a photograph from a
    /// modern phone camera — routinely 4-6 MB — actually goes through, instead
    /// of failing with a permissions error that reads like a bug.
    private static let maxBytes = 2 * 1024 * 1024

    static func jpegUnder2MB(_ image: UIImage) -> Data? {
        // Long edge first: a 4032px photograph carries far more detail than a
        // reviewer needs, and shrinking it saves more than quality alone.
        let maxEdge: CGFloat = 2000
        let scale = min(1, maxEdge / max(image.size.width, image.size.height))
        let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)

        let resized = UIGraphicsImageRenderer(size: target).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }

        // Step the quality down until it fits. A tazkira number has to stay
        // legible, so this stops at 0.4 rather than compressing until it fits
        // at any cost — an unreadable document is a rejected verification and
        // a second trip for her.
        for quality in stride(from: 0.85, through: 0.4, by: -0.15) {
            if let data = resized.jpegData(compressionQuality: quality),
               data.count < maxBytes {
                return data
            }
        }
        return resized.jpegData(compressionQuality: 0.4).flatMap {
            $0.count < maxBytes ? $0 : nil
        }
    }

    func submit(
        uid: String,
        tazkira: UIImage,
        selfie: UIImage,
        tazkiraNumber: String,
        addressProvince: String,
        addressDetail: String,
        birthYear: String = "",
        issueDate: String = "",
        expiryDate: String = ""
    ) async throws {
        guard !tazkiraNumber.trimmingCharacters(in: .whitespaces).isEmpty,
              !addressProvince.trimmingCharacters(in: .whitespaces).isEmpty,
              !addressDetail.trimmingCharacters(in: .whitespaces).isEmpty
        else { throw KycError.missingFields }

        step = .compressing
        defer { step = .idle }

        guard let tazkiraData = Self.jpegUnder2MB(tazkira),
              let selfieData = Self.jpegUnder2MB(selfie)
        else { throw KycError.imageTooLarge }

        // The same paths the server derives. Written here because Storage needs
        // a destination, but the server never takes our word for it — it
        // recomputes them from the caller's uid and checks both files exist.
        let storage = Storage.storage()
        let meta = StorageMetadata()
        meta.contentType = "image/jpeg"

        do {
            step = .uploadingTazkira
            _ = try await storage.reference(withPath: "kyc/\(uid)/tazkira.jpg")
                .putDataAsync(tazkiraData, metadata: meta)

            step = .uploadingSelfie
            _ = try await storage.reference(withPath: "kyc/\(uid)/selfie.jpg")
                .putDataAsync(selfieData, metadata: meta)
        } catch {
            // storage.rules refuses anyone but the owner, so this is either a
            // missing uid_map bridge or a genuine network failure. Both are
            // worth saying rather than retrying quietly.
            throw KycError.uploadDenied
        }

        step = .submitting
        do {
            _ = try await Callables.call("submitKyc", [
                "tazkiraNumber": .string(tazkiraNumber.trimmingCharacters(in: .whitespaces)),
                "addressProvince": .string(addressProvince.trimmingCharacters(in: .whitespaces)),
                "addressDetail": .string(addressDetail.trimmingCharacters(in: .whitespaces)),
                "birthYear": .string(birthYear),
                "tazkiraIssueDate": .string(issueDate),
                "tazkiraExpiryDate": .string(expiryDate),
            ])
        } catch let e as Callables.CallableError {
            if case .failedPrecondition(let message, let reason) = e {
                // The reason code first — it is what submitKyc actually sends —
                // and the English sentence only for a server older than that.
                let m = message.lowercased()
                if reason == "KYC_VERIFIED" || m.contains("already verified") { throw KycError.alreadyVerified }
                if reason == "KYC_UNDER_REVIEW" || m.contains("under review") { throw KycError.alreadyUnderReview }
                if reason == "KYC_PHOTOS_MISSING" { throw KycError.uploadDenied }
            }
            throw KycError.network
        }
    }
}

/// Her verification state, live.
///
/// Android's KycScreen observes the user document (`observeUser`) so that the
/// moment an admin approves or rejects, the screen changes under her. iOS read
/// `kycStatus` once, at sign-in, from the session — so a salon owner sitting on
/// the "under review" screen would never see it lift, and a rejection reason
/// was never shown at all.
///
/// One document, her own: `allow get: if ownsDoc(uid)` already permits it.
@MainActor
@Observable
final class KycStatusWatcher {
    /// nil until the first snapshot arrives. Not "NONE": a read that has not
    /// happened yet must not be shown as "you have never submitted".
    private(set) var status: String?
    private(set) var rejectionReason = ""
    private var listener: ListenerRegistration?
    private var watching = ""

    func start(uid: String) {
        guard !uid.isEmpty, uid != watching else { return }
        stop()
        watching = uid
        listener = Firestore.firestore().document("users/\(uid)")
            .addSnapshotListener { [weak self] snapshot, _ in
                // A failed read keeps whatever was last known rather than
                // flipping her to an empty form — the same reasoning as
                // DashboardViewModel.kycStatus on Android, which treats an
                // unreadable document as "unknown", not as "NONE".
                guard let self, let data = snapshot?.data() else { return }
                self.status = (data["kycStatus"] as? String) ?? "NONE"
                self.rejectionReason = (data["kycRejectionReason"] as? String) ?? ""
            }
    }

    // No deinit, for the reason PaymentWatcher gives. The view calls stop().
    func stop() {
        listener?.remove()
        listener = nil
        watching = ""
    }
}
