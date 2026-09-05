package com.safebeauty.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone number decides who you are, so this is the table three
 * implementations have to agree on: PhoneUtils.kt here, PhoneUtils.swift on
 * iOS, and cleanPhone/normalizeAfghanPhone in functions/shared.js.
 *
 * The case that made this file necessary is [persianDigitsFoldToAscii]. A Dari
 * or Pashto keyboard produces ۰۱۲۳۴۵۶۷۸۹, `Char.isDigit()` is Unicode-aware and
 * happily accepted them, and `isValidAfghan` counted nine of them and said yes.
 * The server then cleaned the same number with `/\D/`, which is ASCII-only, and
 * stored the account as the bare country code "+93". The first woman to
 * register from her own keyboard took "+93" as her phone number, and everyone
 * after her was told the number was already taken.
 */
class PhoneUtilsTest {

    // MARK: the ordinary shapes, which all collapse to one

    @Test
    fun `every ascii spelling of one number normalizes the same`() {
        val expected = "+93700123456"
        listOf(
            "0700123456",
            "700123456",
            "+93700123456",
            "0093700123456",
            "93700123456",
            "0700 123 456",
            "0700-123-456",
            "(0700) 123456",
        ).forEach { assertEquals(it, expected, PhoneUtils.normalizeAfghan(it)) }
    }

    // MARK: the regression

    @Test
    fun persianDigitsFoldToAscii() {
        // Eastern Arabic-Indic (U+06F0..), what a Dari/Pashto keyboard emits.
        assertEquals("+93700123456", PhoneUtils.normalizeAfghan("۰۷۰۰۱۲۳۴۵۶"))
        assertEquals("+93700123456", PhoneUtils.normalizeAfghan("۷۰۰۱۲۳۴۵۶"))
        // Arabic-Indic (U+0660..), which some keyboards emit instead.
        assertEquals("+93700123456", PhoneUtils.normalizeAfghan("٠٧٠٠١٢٣٤٥٦"))
    }

    @Test
    fun `a persian-digit number is never left as the bare country code`() {
        // The exact defect: the old clean() kept the Persian digits, and the
        // server's ASCII-only strip reduced what it received to "+93".
        val normalized = PhoneUtils.normalizeAfghan("۷۰۰۱۲۳۴۵۶")
        assertFalse("stored as the bare country code", normalized == "+93")
        assertTrue("must survive an ASCII-only strip, which is what the server does",
            normalized.replace(Regex("[^0-9]"), "").length >= 7)
    }

    @Test
    fun `persian digits are accepted as a valid afghan number`() {
        assertTrue(PhoneUtils.isValidAfghan("۰۷۰۰۱۲۳۴۵۶"))
        assertTrue(PhoneUtils.isValidAfghan("۷۰۰۱۲۳۴۵۶"))
    }

    @Test
    fun `a persian-digit number and its ascii twin are the same account`() {
        // If these ever diverge, one woman has two accounts and can log into
        // neither reliably.
        assertEquals(
            PhoneUtils.normalizeAfghan("0700123456"),
            PhoneUtils.normalizeAfghan("۰۷۰۰۱۲۳۴۵۶")
        )
    }

    // MARK: validity

    @Test
    fun `nine local digits is valid, anything else is not`() {
        assertTrue(PhoneUtils.isValidAfghan("0700123456"))
        assertTrue(PhoneUtils.isValidAfghan("+93700123456"))
        assertFalse("eight digits", PhoneUtils.isValidAfghan("070012345"))
        assertFalse("ten digits", PhoneUtils.isValidAfghan("07001234567"))
        assertFalse("empty", PhoneUtils.isValidAfghan(""))
        assertFalse("letters are not digits", PhoneUtils.isValidAfghan("07001234ab"))
    }

    // MARK: login form

    @Test
    fun `an international number is kept, an afghan one is canonicalised`() {
        // Admins may be in any country, so a leading '+' is taken at its word.
        assertEquals("+14155550123", PhoneUtils.normalizeForLogin("+1 415 555 0123"))
        assertEquals("+93700123456", PhoneUtils.normalizeForLogin("0700123456"))
    }

    @Test
    fun `normalizeAfghan output is a fixed point of normalizeForLogin`() {
        // The property registration and sign-in depend on: what registration
        // stores must be what sign-in resolves. iOS broke this once by handing
        // the sign-in screen a raw typed number instead of the stored form.
        listOf("0700123456", "۰۷۰۰۱۲۳۴۵۶", "0093700123456", "+93700123456")
            .forEach {
                val stored = PhoneUtils.normalizeAfghan(it)
                assertEquals(it, stored, PhoneUtils.normalizeForLogin(stored))
            }
    }
}
