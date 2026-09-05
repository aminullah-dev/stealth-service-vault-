import Foundation
import UIKit
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
            if case .failedPrecondition(let message) = e {
                let m = message.lowercased()
                if m.contains("already verified") { throw KycError.alreadyVerified }
                if m.contains("under review") { throw KycError.alreadyUnderReview }
            }
            throw KycError.network
        }
    }
}
