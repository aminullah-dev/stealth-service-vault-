package com.security.stealthapp.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a 256-bit random passphrase for SQLCipher and stores it inside
 * EncryptedSharedPreferences, which is itself backed by the Android Keystore.
 *
 * The key never leaves the device in plain form. Calling [nukePassphrase] makes
 * the encrypted database permanently unreadable without wiping it first.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "vault_secure_prefs"
        private const val PREF_KEY   = "db_passphrase_b64"
        private const val KEY_BYTES  = 32 // 256-bit
    }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Returns the persisted passphrase, generating a new one on first call. */
    fun getOrCreatePassphrase(): ByteArray {
        val stored = encryptedPrefs.getString(PREF_KEY, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.DEFAULT)
        }

        val fresh = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        encryptedPrefs.edit()
            .putString(PREF_KEY, Base64.encodeToString(fresh, Base64.DEFAULT))
            .apply()
        return fresh
    }

    /**
     * Deletes the stored passphrase.  After this call the encrypted database file
     * cannot be opened — effectively a cryptographic wipe.
     */
    fun nukePassphrase() {
        encryptedPrefs.edit().remove(PREF_KEY).apply()
    }
}
