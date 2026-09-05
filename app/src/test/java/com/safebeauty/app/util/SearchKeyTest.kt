package com.safebeauty.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The same table as functions/test/searchkey.test.js, deliberately.
 *
 * Firestore matches a prefix against the stored value, so the device has to
 * derive exactly the string the server wrote. When it did not, a customer typing
 * a salon's name as it appeared on her screen got nothing back — which reads as
 * "this salon is not on SafeBeauty", not as a bug.
 */
class SearchKeyTest {

    private val cases = listOf(
        "سالن آرایشی عروس خانم" to "سالن ارایشی عروس خانم",
        "آرایشگاه زیبایی"       to "ارایشگاه زیبایی",
        "نوري"                  to "نوری",
        "حکيم الله"             to "حکیم الله",
        "مکياژ"                 to "مکیاژ",
        "زيبايي كابل"           to "زیبایی کابل",
        "Shaghayeq Ha"          to "shaghayeq ha",
        "  spaced   out  "      to "spaced out",
        "فاطمة"                 to "فاطمه",
        ""                      to "",
    )

    @Test
    fun `folds the spellings Dari and Pashto keyboards actually produce`() {
        for ((input, expected) in cases) {
            assertEquals("normalize(\"$input\")", expected, SearchKey.normalize(input))
        }
    }

    @Test
    fun `normalizing twice changes nothing`() {
        for ((input, _) in cases) {
            val once = SearchKey.normalize(input)
            assertEquals(once, SearchKey.normalize(once))
        }
    }

    @Test
    fun `two keyboards, one salon`() {
        assertEquals(SearchKey.normalize("زيبايي"), SearchKey.normalize("زیبایی"))
        assertEquals(SearchKey.normalize("كابل"), SearchKey.normalize("کابل"))
        assertEquals(SearchKey.normalize("آرایش"), SearchKey.normalize("ارایش"))
    }

    @Test
    fun `null is the empty key, not a crash`() {
        assertEquals("", SearchKey.normalize(null))
    }
}
