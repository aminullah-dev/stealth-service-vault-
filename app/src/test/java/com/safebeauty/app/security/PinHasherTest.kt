package com.safebeauty.app.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PinHasherTest {

    private lateinit var pinHasher: PinHasher

    @Before
    fun setUp() {
        pinHasher = PinHasher()
    }

    // ── generateSalt ─────────────────────────────────────────────────────────

    @Test
    fun `generateSalt returns non-empty base64 string`() {
        val salt = pinHasher.generateSalt()
        assertTrue(salt.isNotBlank())
    }

    @Test
    fun `generateSalt returns unique values on each call`() {
        val salts = (1..10).map { pinHasher.generateSalt() }.toSet()
        assertEquals(10, salts.size)
    }

    // ── hash ─────────────────────────────────────────────────────────────────

    @Test
    fun `hash is deterministic for same pin and salt`() {
        val salt  = pinHasher.generateSalt()
        val hash1 = pinHasher.hash("123456", salt)
        val hash2 = pinHasher.hash("123456", salt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hash differs when pin changes`() {
        val salt  = pinHasher.generateSalt()
        val hash1 = pinHasher.hash("123456", salt)
        val hash2 = pinHasher.hash("654321", salt)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash differs when salt changes`() {
        val hash1 = pinHasher.hash("123456", pinHasher.generateSalt())
        val hash2 = pinHasher.hash("123456", pinHasher.generateSalt())
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash output is non-empty`() {
        val hash = pinHasher.hash("123456", pinHasher.generateSalt())
        assertTrue(hash.isNotBlank())
    }

    // ── verify ───────────────────────────────────────────────────────────────

    @Test
    fun `verify returns true for correct pin`() {
        val salt = pinHasher.generateSalt()
        val hash = pinHasher.hash("987654", salt)
        assertTrue(pinHasher.verify("987654", salt, hash))
    }

    @Test
    fun `verify returns false for wrong pin`() {
        val salt = pinHasher.generateSalt()
        val hash = pinHasher.hash("987654", salt)
        assertFalse(pinHasher.verify("111111", salt, hash))
    }

    @Test
    fun `verify returns false when salt does not match`() {
        val salt1 = pinHasher.generateSalt()
        val salt2 = pinHasher.generateSalt()
        val hash  = pinHasher.hash("123456", salt1)
        assertFalse(pinHasher.verify("123456", salt2, hash))
    }

    @Test
    fun `verify returns false for tampered hash`() {
        val salt = pinHasher.generateSalt()
        val hash = pinHasher.hash("123456", salt)
        assertFalse(pinHasher.verify("123456", salt, hash + "x"))
    }

    // ── deriveAuthPassword ───────────────────────────────────────────────────

    @Test
    fun `deriveAuthPassword is deterministic`() {
        val salt  = pinHasher.generateSalt()
        val auth1 = pinHasher.deriveAuthPassword("123456", salt)
        val auth2 = pinHasher.deriveAuthPassword("123456", salt)
        assertEquals(auth1, auth2)
    }

    @Test
    fun `deriveAuthPassword differs from verify hash for same pin`() {
        val salt        = pinHasher.generateSalt()
        val verifyHash  = pinHasher.hash("123456", salt)
        val authPassword = pinHasher.deriveAuthPassword("123456", salt)
        assertNotEquals(verifyHash, authPassword)
    }

    @Test
    fun `deriveAuthPassword differs for different pins`() {
        val salt  = pinHasher.generateSalt()
        val auth1 = pinHasher.deriveAuthPassword("123456", salt)
        val auth2 = pinHasher.deriveAuthPassword("654321", salt)
        assertNotEquals(auth1, auth2)
    }
}
