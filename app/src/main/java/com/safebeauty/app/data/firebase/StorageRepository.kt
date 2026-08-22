package com.safebeauty.app.data.firebase

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.safebeauty.app.util.CrashReporter
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// All uploads are compressed JPEGs written to *.jpg paths. Firebase Storage
// leaves the object's contentType as application/octet-stream when putBytes is
// called without metadata, which storage.rules' isValidImage() (contentType
// must match image/(jpeg|png|webp)) then REJECTS — silently breaking every
// KYC/profile/review upload. Setting it explicitly keeps uploads rule-valid.
private val JPEG_METADATA: StorageMetadata =
    StorageMetadata.Builder().setContentType("image/jpeg").build()

/**
 * Handles all Firebase Storage uploads and deletes for the app.
 *
 * Storage layout:
 *   profile_photos/{uid}.jpg            — customer profile photos (public-read)
 *   salon_gallery/{salonId}/{docId}.jpg — salon portfolio images (public-read)
 *   kyc/{uid}/tazkira.jpg               — national-ID photo (private: owner+admin)
 *   kyc/{uid}/selfie.jpg                — verification selfie (private: owner+admin)
 *
 * Each upload returns a permanent HTTPS download URL stored in Firestore
 * so the app can display images via URL without reading from Storage directly.
 */
@Singleton
class StorageRepository @Inject constructor() {

    private val storage = FirebaseStorage.getInstance()

    /**
     * Uploads [bytes] (compressed JPEG) as the profile photo for [uid].
     * Returns the HTTPS download URL, or throws on failure.
     */
    suspend fun uploadUserPhoto(uid: String, bytes: ByteArray): String {
        val ref = storage.reference.child("profile_photos/$uid.jpg")
        ref.putBytes(bytes, JPEG_METADATA).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Uploads [bytes] (compressed JPEG) as a gallery image for [salonId].
     * [docId] is the Firestore document ID so the Storage path can be reconstructed
     * for deletion without a separate lookup.
     * Returns the HTTPS download URL, or throws on failure.
     */
    suspend fun uploadGalleryImage(salonId: String, docId: String, bytes: ByteArray): String {
        val ref = storage.reference.child("salon_gallery/$salonId/$docId.jpg")
        ref.putBytes(bytes, JPEG_METADATA).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Uploads one photo attached to a review. [reviewId] is the reserved review
     * document ID and [index] disambiguates multiple photos on the same review.
     * Returns the HTTPS download URL, or throws on failure.
     */
    suspend fun uploadReviewImage(uid: String, reviewId: String, index: Int, bytes: ByteArray): String {
        // Path scoped to the uploader's app uid — storage.rules only lets a user
        // write under reviews/{their own uid}/… (prevents cross-user upload spam).
        val ref = storage.reference.child("reviews/$uid/$reviewId/$index.jpg")
        ref.putBytes(bytes, JPEG_METADATA).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Uploads one portfolio photo for a staff member. [staffId] and [index] keep
     * the Storage path unique. Returns the HTTPS download URL, or throws.
     */
    suspend fun uploadStaffPhoto(salonId: String, staffId: String, index: Int, bytes: ByteArray): String {
        val ref = storage.reference.child("staff_portfolio/$salonId/${staffId}_$index.jpg")
        ref.putBytes(bytes, JPEG_METADATA).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Uploads the user's national-ID (tazkira) photo to the private KYC path.
     * Returns the HTTPS download URL (readable only by the owner + admins per
     * storage.rules), or throws on failure.
     */
    suspend fun uploadKycTazkira(uid: String, bytes: ByteArray): String {
        val ref = storage.reference.child("kyc/$uid/tazkira.jpg")
        ref.putBytes(bytes, JPEG_METADATA).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Uploads the user's verification selfie to the private KYC path.
     * Returns the HTTPS download URL, or throws on failure.
     */
    suspend fun uploadKycSelfie(uid: String, bytes: ByteArray): String {
        val ref = storage.reference.child("kyc/$uid/selfie.jpg")
        ref.putBytes(bytes, JPEG_METADATA).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Deletes the file at [storagePath] (e.g. "salon_gallery/abc/xyz.jpg").
     * Silently swallows "object not found" errors since the Firestore doc may have
     * already been deleted before the Storage cleanup runs.
     */
    suspend fun deleteFile(storagePath: String) {
        runCatching {
            storage.reference.child(storagePath).delete().await()
        }.onFailure { CrashReporter.recordNonFatal(it, "storage:deleteFile") }
    }
}
