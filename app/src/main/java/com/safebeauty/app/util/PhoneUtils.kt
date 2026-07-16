package com.safebeauty.app.util

/**
 * Normalizes phone numbers to a single canonical E.164-ish form so the same
 * number always produces the same string at registration and at login (the
 * phone is now the login identifier).
 *
 * Rules:
 *   - Afghan customers/providers enter a local number; it becomes +93XXXXXXXXX.
 *   - Admins may use any country: a number the user typed WITH a leading '+'
 *     is kept as an international number (only its digits are cleaned).
 */
object PhoneUtils {

    const val AFGHANISTAN_CODE = "+93"

    /** Strips spaces, dashes and parentheses, keeping digits and a leading '+'. */
    private fun clean(raw: String): String {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }

    /**
     * Canonical form for an Afghan (customer/provider) number. Accepts input
     * like "0700123456", "700123456", "+93700123456", "0093700123456" and
     * returns "+93700123456".
     */
    fun normalizeAfghan(raw: String): String {
        var c = clean(raw)
        if (c.startsWith("+93")) return c
        if (c.startsWith("0093")) return "+93" + c.removePrefix("0093")
        if (c.startsWith("93") && c.length >= 11) return "+$c"
        c = c.removePrefix("+")            // any other '+' prefix isn't Afghan input
        if (c.startsWith("0")) c = c.drop(1)
        return "$AFGHANISTAN_CODE$c"
    }

    /** True when [raw] is a plausible Afghan mobile number (9 local digits). */
    fun isValidAfghan(raw: String): Boolean {
        val local = normalizeAfghan(raw).removePrefix(AFGHANISTAN_CODE)
        return local.length == 9 && local.all { it.isDigit() }
    }

    /**
     * Login-time normalization that works for BOTH audiences: a number typed
     * with a leading '+' is treated as international (admins), otherwise it's
     * treated as an Afghan local number.
     */
    fun normalizeForLogin(raw: String): String {
        val c = clean(raw)
        return if (c.startsWith("+")) c else normalizeAfghan(raw)
    }
}
