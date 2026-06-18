package com.security.stealthapp.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PBKDF2-SHA256 PIN hasher. Each user has a unique random salt so identical
 * PINs produce different hashes, preventing a compromised row from revealing
 * whether two accounts share a PIN.
 */
@Singleton
class PinHasher @Inject constructor() {

    companion object {
        private const val ALGORITHM  = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 65_536
        private const val KEY_BITS   = 256
        private const val SALT_BYTES = 16
    }

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, saltBase64: String): String {
        val salt    = Base64.decode(saltBase64, Base64.NO_WRAP)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val spec    = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val result  = Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
        spec.clearPassword()
        return result
    }

    fun verify(pin: String, saltBase64: String, storedHash: String): Boolean =
        hash(pin, saltBase64) == storedHash
}
