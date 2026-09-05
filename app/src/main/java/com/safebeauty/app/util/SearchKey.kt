package com.safebeauty.app.util

/**
 * The normalisation a searchable name is stored under.
 *
 * A byte-for-byte mirror of `normalize` in functions/lib/categories.js, which is
 * what the server writes into `nameKey` on salons and users. It has to be a
 * mirror: Firestore matches a prefix on the STORED value, so if the device sends
 * anything else the query is a range over text that was never written.
 *
 * That is not theoretical. The server stores "سالن آرایشی عروس خانم" as
 * "سالن ارایشی عروس خانم" — the madda is folded away — and the app was sending
 * the raw text. A customer typing the salon's name exactly as it appears on her
 * screen got no results, which reads as "this salon is not on SafeBeauty".
 *
 * The folds are the ones Dari and Pashto actually need. A name typed with an
 * Arabic ي or ك, or with a zero-width non-joiner between the parts of a
 * compound, is the same name as one typed with the Persian forms — and both
 * appear constantly, because keyboards differ.
 */
object SearchKey {

    // ZWNJ, ZWJ, the harakat block, and tatweel: invisible or decorative, and
    // never something a person means to type as part of a name.
    private val INVISIBLE = Regex("[‌‍ً-ْـ]")
    private val ALEF      = Regex("[أإآ]")
    private val YEH       = Regex("[يى]")
    private val SPACES    = Regex("\\s+")

    /** The stored form of [input] — pass this to a nameKey prefix query. */
    fun normalize(input: String?): String =
        (input ?: "")
            .lowercase()
            .replace(INVISIBLE, "")
            .replace(ALEF, "ا")
            .replace(YEH, "ی")
            .replace("ك", "ک")
            .replace("ة", "ه")
            .replace(SPACES, " ")
            .trim()
}
