package com.safebeauty.app.data

import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.data.firebase.FirestoreRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who an announcement is for, and when it stops.
 *
 * The admin console has written targetRole, targetLang and targetDistrict since
 * the Announce tab was built. BroadcastDocument did not have the fields, so the
 * app read none of them and every announcement reached everybody — a Pashto
 * message aimed at Pashto readers sat, in Pashto, above a Dari interface. And
 * nothing ended: one sent in August was still on screen days later, retired only
 * by each customer swiping it away.
 */
class BroadcastVisibilityTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun visible(
        all: List<BroadcastDocument>,
        role: String = "CUSTOMER",
        lang: String = "fa",
        districtKey: String = "",
    ) = FirestoreRepository.visibleBroadcastsFor(all, role, lang, districtKey, now)

    private fun b(
        id: String,
        role: String = "", lang: String = "", district: String = "",
        createdAt: Long = now, expiresAt: Long = 0L,
    ) = BroadcastDocument(
        id = id, message = id, createdAt = createdAt,
        targetRole = role, targetLang = lang, targetDistrict = district,
        expiresAt = expiresAt,
    )

    @Test
    fun `an untargeted announcement reaches everyone`() {
        assertEquals(listOf("all"), visible(listOf(b("all"))).map { it.id })
    }

    @Test
    fun `a language-targeted announcement reaches only that language`() {
        val all = listOf(b("ps-only", lang = "ps"), b("fa-only", lang = "fa"))
        assertEquals(listOf("fa-only"), visible(all, lang = "fa").map { it.id })
        assertEquals(listOf("ps-only"), visible(all, lang = "ps").map { it.id })
    }

    @Test
    fun `a role-targeted announcement does not cross roles`() {
        val all = listOf(b("providers", role = "PROVIDER"), b("customers", role = "CUSTOMER"))
        assertEquals(listOf("customers"), visible(all, role = "CUSTOMER").map { it.id })
        assertEquals(listOf("providers"), visible(all, role = "PROVIDER").map { it.id })
    }

    @Test
    fun `a district-targeted announcement is not shown to someone with no district`() {
        // A customer has none. Showing it to her anyway is how a message meant
        // for salons in one ناحیه reaches everybody in Kabul.
        val all = listOf(b("karte-char", district = "KBL_D3_KarteChar"))
        assertTrue(visible(all).isEmpty())
        assertEquals(
            listOf("karte-char"),
            visible(all, role = "PROVIDER", districtKey = "KBL_D3_KarteChar").map { it.id }
        )
    }

    @Test
    fun `an expired announcement stops showing`() {
        val all = listOf(b("gone", expiresAt = now - 1), b("live", expiresAt = now + day))
        assertEquals(listOf("live"), visible(all).map { it.id })
    }

    @Test
    fun `an old announcement with no expiry retires on age`() {
        // The ones sent before expiries existed. Without this they never stop.
        val all = listOf(b("august", createdAt = now - 40 * day), b("today", createdAt = now))
        assertEquals(listOf("today"), visible(all).map { it.id })
    }

    @Test
    fun `an announcement with no timestamp at all is still shown`() {
        // A missing createdAt is a malformed document, not an old one, and
        // hiding it would make a genuine announcement silently invisible.
        assertEquals(listOf("undated"), visible(listOf(b("undated", createdAt = 0L))).map { it.id })
    }
}
