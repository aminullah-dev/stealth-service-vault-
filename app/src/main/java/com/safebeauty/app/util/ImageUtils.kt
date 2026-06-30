package com.safebeauty.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Helpers for turning a picked image Uri into a compact Base64 string that is
 * safe to store directly inside a Firestore document (one image per document).
 *
 * Images are downscaled to [MAX_DIMENSION] px on the longest edge and
 * JPEG-compressed so a typical photo lands around 60–200 KB — comfortably under
 * the 1 MB Firestore document limit even after Base64's ~33% overhead.
 */
object ImageUtils {

    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY  = 70

    /** Hard cap so a pathological image can never blow past the Firestore limit. */
    const val MAX_BASE64_BYTES = 900_000

    sealed interface Result {
        data class Success(val base64: String) : Result
        data object TooLarge : Result
        data object Failed   : Result
    }

    sealed interface BytesResult {
        data class Success(val bytes: ByteArray) : BytesResult
        data object TooLarge : BytesResult
        data object Failed   : BytesResult
    }

    /**
     * Compresses the image at [uri] and returns the raw JPEG bytes for upload
     * to Firebase Storage. Reuses the same downscaling + EXIF rotation as
     * [uriToCompressedBase64].
     */
    suspend fun uriToCompressedBytes(context: Context, uri: Uri): BytesResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeSampledBitmap(context, uri)
                    ?: return@withContext BytesResult.Failed
                val rotated = applyExifRotation(context, uri, bitmap)
                val scaled  = downscale(rotated)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                val bytes = out.toByteArray()
                if (bytes.size > MAX_BASE64_BYTES) BytesResult.TooLarge
                else BytesResult.Success(bytes)
            }.getOrElse { BytesResult.Failed }
        }

    /** @suppress Legacy path — only used to display photos already stored as Base64 in Firestore. */
    suspend fun uriToCompressedBase64(context: Context, uri: Uri): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeSampledBitmap(context, uri)
                    ?: return@withContext Result.Failed
                val rotated = applyExifRotation(context, uri, bitmap)
                val scaled  = downscale(rotated)

                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                val bytes = out.toByteArray()

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (base64.length > MAX_BASE64_BYTES) Result.TooLarge
                else Result.Success(base64)
            }.getOrElse { Result.Failed }
        }

    /** @suppress Legacy path — decodes Base64 images stored before Firebase Storage migration. */
    fun base64ToBitmap(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    // ── internals ────────────────────────────────────────────────────────────

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        // First pass: read bounds only to compute an efficient sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / sample > MAX_DIMENSION * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
