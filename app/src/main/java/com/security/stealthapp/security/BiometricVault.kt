package com.security.stealthapp.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric fast-unlock that re-supplies the user's PIN after a Face/fingerprint
 * match, so the normal PIN auth flow can run without the user typing it.
 *
 * Security design:
 *  - A 256-bit AES/GCM key lives in the Android Keystore with
 *    setUserAuthenticationRequired(true): the key is unusable until the OS has
 *    just verified a biometric. We bind the BiometricPrompt to a CryptoObject
 *    holding a Cipher from this key, so a biometric success is cryptographically
 *    tied to the decryption — not just a boolean we could be tricked into.
 *  - setInvalidatedByBiometricEnrollment(true): enrolling a new fingerprint/face
 *    permanently invalidates the key, so a coerced new enrollment can't unlock.
 *    We detect that, wipe the stored PIN, and fall back to PIN entry.
 *  - The encrypted PIN + IV are kept in plain prefs; their confidentiality rests
 *    entirely on the hardware-backed, biometric-gated key.
 *
 * The PIN itself never changes anything here — it's just the existing secret the
 * auth flow already needs, stored at rest only in encrypted form.
 */
object BiometricVault {

    private const val PREFS          = "biometric_prefs"
    private const val KEY_CIPHERTEXT = "enc_pin"
    private const val KEY_IV         = "enc_iv"
    private const val KEYSTORE       = "AndroidKeyStore"
    private const val KEY_ALIAS      = "safebeauty_biometric_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS   = 128

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** True if the device has enrolled strong biometrics we can use. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** True if the user has turned on biometric unlock (an encrypted PIN exists). */
    fun isEnabled(context: Context): Boolean =
        prefs(context).contains(KEY_CIPHERTEXT)

    /** Clears the stored PIN and Keystore key (e.g. on logout or key invalidation). */
    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        }
    }

    /**
     * Prompts for biometric, then encrypts [pin] under the Keystore key and stores
     * it. [onSuccess] runs after the PIN is safely stored; [onError] on any
     * cancellation/failure.
     */
    fun enable(
        activity: FragmentActivity,
        pin: String,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        val cipher = runCatching {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
        }.getOrElse { onError(); return }

        authenticate(activity, cipher, title, subtitle, negativeText,
            onAuth = { c ->
                val ct = c.doFinal(pin.toByteArray(Charsets.UTF_8))
                prefs(activity).edit()
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(ct, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
                    .apply()
                onSuccess()
            },
            onError = onError
        )
    }

    /**
     * Prompts for biometric, then decrypts and returns the stored PIN via [onPin].
     * [onError] runs on cancellation, failure, or if the key was invalidated.
     */
    fun unlock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        onPin: (String) -> Unit,
        onError: () -> Unit
    ) {
        val p = prefs(activity)
        val ctB64 = p.getString(KEY_CIPHERTEXT, null)
        val ivB64 = p.getString(KEY_IV, null)
        if (ctB64 == null || ivB64 == null) { onError(); return }

        val cipher = runCatching {
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, requireKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        }.getOrElse {
            // Key gone or invalidated by a new biometric enrollment — reset to PIN.
            disable(activity)
            onError(); return
        }

        authenticate(activity, cipher, title, subtitle, negativeText,
            onAuth = { c ->
                val ct = Base64.decode(ctB64, Base64.NO_WRAP)
                onPin(String(c.doFinal(ct), Charsets.UTF_8))
            },
            onError = onError
        )
    }

    // ── internals ───────────────────────────────────────────────────────────────

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeText: String,
        onAuth: (Cipher) -> Unit,
        onError: () -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher
                    if (c == null) { onError(); return }
                    runCatching { onAuth(c) }.onFailure { onError() }
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) = onError()
                // onAuthenticationFailed(): a single mismatch; the prompt stays open,
                // so we deliberately don't treat it as a terminal error.
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun getOrCreateKey(): SecretKey {
        getKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun requireKey(): SecretKey =
        getKey() ?: throw IllegalStateException("Biometric key missing")

    private fun getKey(): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
