package com.security.stealthapp.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a 256-bit random passphrase for SQLCipher and stores it inside
 * EncryptedSharedPreferences backed by the Android Keystore (AES-256-GCM).
 *
 * Uses the stable security-crypto 1.0.0 API (MasterKeys / EncryptedSharedPreferences).
 * The passphrase never leaves the device in plain form. [nukePassphrase] makes the
 * encrypted database permanently unreadable without wiping it first.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "vault_secure_prefs"
        private const val PREF_KEY   = "db_passphrase_b64"
        private const val KEY_BYTES  = 32 // 256-bit
    }

    private val encryptedPrefs by lazy {
        // MasterKeys.getOrCreate() retrieves or generates an AES-256-GCM key
        // in the Android Keystore under the given alias.
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Returns the persisted SQLCipher passphrase, generating a new one on first call. */
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
     * Deletes the stored passphrase. The encrypted database file becomes permanently
     * unreadable — a cryptographic wipe without touching the file itself.
     */
    fun nukePassphrase() {
        encryptedPrefs.edit().remove(PREF_KEY).apply()
    }
}
