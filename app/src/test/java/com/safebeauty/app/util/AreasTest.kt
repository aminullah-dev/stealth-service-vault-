package com.safebeauty.app.util

import com.safebeauty.app.ui.theme.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geography four cities depend on, which had no test on this side at all.
 *
 * functions/test/areas.test.js guards the server's copy and that the two do not
 * drift. What it cannot guard is what the app does with the list: which keys the
 * customer's filter offers, and which field each of them has to be queried on.
 * Send a گذر to districtKey and the query returns nothing — silently, because an
 * equality on the wrong field is not an error.
 */
class AreasTest {

    @Test
    fun `every live city has districts`() {
        // A city offered in the picker with nothing under it is a dead end the
        // customer walks into. Areas.kt states the rule; this checks it holds.
        for (city in Areas.liveCities) {
            assertTrue(
                "${city.key} is live with no districts",
                Areas.districtsIn(city.key).isNotEmpty()
            )
        }
        assertEquals(
            listOf("KABUL", "HERAT", "MAZAR", "JALALABAD"),
            Areas.liveCities.map { it.key }
        )
    }

    @Test
    fun `a key belongs to exactly the city its prefix names`() {
        assertEquals("KABUL", Areas.areas.first { it.key == "KBL_D17" }.cityKey)
        assertEquals("HERAT", Areas.areas.first { it.key == "HRT_D01" }.cityKey)
        assertEquals("MAZAR", Areas.areas.first { it.key == "MZR_GuzarQarghan" }.cityKey)
        assertEquals("JALALABAD", Areas.areas.first { it.key == "JAL_D09" }.cityKey)
    }

    @Test
    fun `isDistrict separates the level districtKey holds from the level areaKey holds`() {
        assertTrue(Areas.isDistrict("KBL_D17"))
        assertTrue(Areas.isDistrict("MZR_D02"))
        assertFalse(Areas.isDistrict("KBL_Khair_Khana"))
        assertFalse(Areas.isDistrict("MZR_GuzarQarghan"))
        // An unknown key is not a district — the filter must not send it to
        // districtKey on the strength of a wrong default.
        assertFalse(Areas.isDistrict("nonsense"))
        assertFalse(Areas.isDistrict(""))
    }

    @Test
    fun `the filter list offers both levels, each district followed by its own areas`() {
        val mazar = Areas.filterableIn("MAZAR")
        // District 2 is followed immediately by the guzar recorded inside it,
        // so the list reads as an address rather than as two stapled lists.
        val d2 = mazar.indexOfFirst { it.key == "MZR_D02" }
        val qarghan = mazar.indexOfFirst { it.key == "MZR_GuzarQarghan" }
        assertTrue("District 2 must appear", d2 >= 0)
        assertEquals("its guzar must follow it", d2 + 1, qarghan)
        // Nothing from another city leaks in.
        assertTrue(mazar.all { it.cityKey == "MAZAR" })
    }

    @Test
    fun `a city with no recorded sub-areas offers its districts and nothing else`() {
        // Jalalabad's guzars are numbered rather than named and the sources give
        // two of an unknown number, so none are listed. The filter must show the
        // nine districts, not an empty list and not a partial guess.
        val jalalabad = Areas.filterableIn("JALALABAD")
        assertEquals(9, jalalabad.size)
        assertTrue(jalalabad.all { Areas.isDistrict(it.key) })
    }

    @Test
    fun `no city means no area list, because two cities' districts are indistinguishable`() {
        // Herat's first district and Jalalabad's first district are both
        // "ناحیه اول". Concatenated they are two identical rows.
        assertTrue(Areas.filterableIn("").isEmpty())
        assertEquals(
            Areas.districtsIn("HERAT").first().fa,
            Areas.districtsIn("JALALABAD").first().fa
        )
    }

    @Test
    fun `the neighbourhood picker offers every area, not only the ones with a parent`() {
        // The picker was built on filterableIn, which reaches a neighbourhood
        // only through its parent district. None of Kabul's 42 has a parent
        // recorded, so it offered the 22 ناحیه and not one محله — «خیرخانه»
        // included, which is where one of the two live salons is. A salon that
        // cannot be filtered to is a salon a customer does not find.
        val kabul = com.safebeauty.app.viewmodel.neighborhoodOptionsFor("KABUL")
        assertTrue(
            "خیرخانه must be pickable",
            kabul.any { it.key == "KBL_Khair_Khana" }
        )
        assertEquals(Areas.areasIn("KABUL"), kabul)
        assertTrue(
            "the picker must offer more than filterableIn reaches",
            kabul.size > Areas.filterableIn("KABUL").size
        )
    }

    @Test
    fun `the picker's rows and the keys they filter by read one list`() {
        // They were two, matched by position: labels from districtsIn, keys
        // from filterableIn. Those agree only where no neighbourhood has a
        // parent — so Kabul and Jalalabad were fine and Herat and Mazar were
        // not. Picking Herat's «ناحیه دوم» queried HRT_BaghMurad; 14 of its 16
        // rows filtered by an area other than the one they named.
        //
        // The label half lives in a Composable and no unit test can reach it.
        // What this can hold is the other half: the options function both sides
        // now read must stay the city's own list, so that a labels list built
        // from it lines up row for row.
        for (city in Areas.liveCities) {
            assertEquals(
                "${city.key}: the picker must read one list",
                Areas.areasIn(city.key),
                com.safebeauty.app.viewmodel.neighborhoodOptionsFor(city.key)
            )
        }
        // Index 0 is the "all" row and belongs to no area, which is why the
        // filter drops it rather than querying for it.
        assertTrue(com.safebeauty.app.viewmodel.neighborhoodOptionsFor("").isEmpty())
    }

    @Test
    fun `every recorded sub-area names a parent in its own city`() {
        for (a in Areas.areas.filter { it.parent.isNotEmpty() }) {
            val parent = Areas.areas.firstOrNull { it.key == a.parent }
            assertTrue("${a.key} names a parent that does not exist", parent != null)
            assertTrue("${a.key} names a parent that is not a district", Areas.isDistrict(a.parent))
            assertEquals("${a.key} names a parent in another city", a.cityKey, parent!!.cityKey)
        }
    }
    @Test
    fun `a pre-prefix key still shows a label, because that is what production holds`() {
        // Every salon stored its district before the keys carried a city, so the
        // document says D9_Makroryan and the list holds KBL_D9_Makroryan. Without
        // the fallback the customer reads the raw key on the salon card, which is
        // exactly what she was reading tonight.
        assertEquals(
            Areas.labelForKey("KBL_D9_Makroryan", AppLanguage.DARI),
            Areas.labelForKey("D9_Makroryan", AppLanguage.DARI)
        )
        assertFalse(Areas.labelForKey("D9_Makroryan", AppLanguage.DARI) == "D9_Makroryan")
    }

    @Test
    fun `free text is shown as the salon wrote it, not replaced or blanked`() {
        // Older salons hold an address rather than a key. Showing it is better
        // than showing nothing, and better than pretending it resolves.
        val typed = "خیرخانه مینه ناحیه 17"
        assertEquals(typed, Areas.labelForKey(typed, AppLanguage.DARI))
    }
}
