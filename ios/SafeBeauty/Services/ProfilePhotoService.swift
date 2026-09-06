import Foundation
import FirebaseStorage
import FirebaseFirestore
import UIKit
import SafeBeautyCore

/// Her own photograph on her own account, which iOS had no way to set.
///
/// Android puts a pencil on the avatar; iPhone showed the first letter of her
/// name and that was all it could ever show. It is also the last thing standing
/// between her and `claimProfileReward`, which requires a name, a phone and a
/// photo and was therefore unclaimable on this platform.
///
/// `profile_photos/{uid}.jpg` is the path storage.rules admits, and it checks
/// the filename against the caller's own app uid — so there is exactly one
/// photo per account and it can only be written by her.
@MainActor
@Observable
final class ProfilePhotoService {
    enum PhotoError: Error { case tooLarge, uploadFailed }

    private(set) var isUploading = false

    /// Uploads, writes the URL onto her user document, and returns how many
    /// loyalty points the reward awarded — zero when there was nothing to
    /// award, which is the normal case on every photo after the first.
    func upload(_ image: UIImage, uid: String) async throws -> Int {
        // The same compressor KYC uses: Storage enforces 2 MB, and a photo from
        // a modern phone camera is routinely three times that. Failing the
        // upload instead reads as a permissions bug.
        guard let data = KycService.jpegUnder2MB(image) else { throw PhotoError.tooLarge }

        isUploading = true
        defer { isUploading = false }

        let ref = Storage.storage().reference(withPath: "profile_photos/\(uid).jpg")
        let meta = StorageMetadata()
        meta.contentType = "image/jpeg"

        let url: URL
        do {
            _ = try await ref.putDataAsync(data, metadata: meta)
            url = try await ref.downloadURL()
        } catch {
            throw PhotoError.uploadFailed
        }

        // profilePhotoUrl is not among the fields the rules freeze on a
        // self-update, so this is hers to write. The reward callable reads it
        // back from the document rather than trusting anything sent to it.
        try await Firestore.firestore().document("users/\(uid)")
            .updateData(["profilePhotoUrl": url.absoluteString])

        // Claimed here rather than on some later screen, because this is the
        // moment the profile becomes complete. It is idempotent server-side —
        // profileRewardClaimed gates it — so calling it after every photo
        // change costs one read and awards nothing the second time.
        let response = try? await Callables.call("claimProfileReward")
        guard response?["awarded"]?.boolValue == true else { return 0 }
        return response?["points"]?.intValue ?? 0
    }
}
