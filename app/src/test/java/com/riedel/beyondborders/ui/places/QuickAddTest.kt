package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.junit.Test
import java.io.File

class QuickAddTest {

    private fun place(
        code: String,
        name: String,
        continent: String,
        unMember: Boolean = true,
        sovereign: String = name,
    ) = Place(
        code = code,
        name = name,
        continent = continent,
        region = continent,
        sovereign = sovereign,
        uninhabited = false,
        unMember = unMember,
        lat = 0.0,
        lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
    )

    private val places = listOf(
        place("DK", "Denmark", "Europe"),
        place("GL", "Greenland", "North America", unMember = false, sovereign = "Denmark"),
        place("TW", "Taiwan", "Asia", unMember = false),
        place("FR", "France", "Europe"),
        place("JP", "Japan", "Asia"),
        place("DE", "Germany", "Europe"),
    )

    @Test
    fun `quick add opens on the most visited states only`() {
        val codes = quickAddRows(places, "").map { it.place.code }
        assertEquals(setOf("FR", "JP", "DE"), codes.toSet())
    }

    @Test
    fun `the most visited list holds 15 states`() {
        assertEquals(15, QUICK_ADD_TOP_CODES.size)
    }

    @Test
    fun `every most visited code is a bundled UN member state`() {
        val bundled = JSONArray(File("src/main/assets/places.json").readText())
        val unMembers = (0 until bundled.length())
            .map { bundled.getJSONObject(it) }
            .filter { it.optBoolean("unMember") }
            .map { it.getString("code") }
            .toSet()
        assertEquals(emptySet<String>(), QUICK_ADD_TOP_CODES - unMembers)
    }

    @Test
    fun `a whitespace-only query shows the most visited list, not an empty one`() {
        assertEquals(setOf("FR", "JP", "DE"), quickAddRows(places, "   ").map { it.place.code }.toSet())
    }

    @Test
    fun `places marked at open stay in the opening list, dependencies still do not`() {
        val codes = quickAddRows(places, "", alsoShow = setOf("DK", "GL")).map { it.place.code }
        assertEquals(setOf("FR", "JP", "DE", "DK"), codes.toSet())
    }

    @Test
    fun `searching widens to every sovereign state including Taiwan but not Greenland`() {
        assertEquals(listOf("DK"), quickAddRows(places, "denm").map { it.place.code })
        assertEquals(listOf("TW"), quickAddRows(places, "taiw").map { it.place.code })
        assertTrue(quickAddRows(places, "green").isEmpty())
    }

    @Test
    fun `search skips Northern Cyprus and Somaliland unless they were already marked`() {
        val withUnrecognised = places + listOf(
            place("XN", "Northern Cyprus", "Asia", unMember = false),
            place("XS", "Somaliland", "Africa", unMember = false),
        )
        assertTrue(quickAddRows(withUnrecognised, "cyprus").isEmpty())
        assertTrue(quickAddRows(withUnrecognised, "somali").isEmpty())
        val opening = quickAddRows(withUnrecognised, "", alsoShow = setOf("XS")).map { it.place.code }
        assertTrue("XS" in opening && "XN" !in opening)
        assertEquals(listOf("XS"), quickAddRows(withUnrecognised, "somali", alsoShow = setOf("XS")).map { it.place.code })
        assertEquals(listOf("TW"), quickAddRows(withUnrecognised, "taiw").map { it.place.code })
    }

    @Test
    fun `a query that folds to nothing does not widen the list`() {
        assertEquals(setOf("FR", "JP", "DE"), quickAddRows(places, "́").map { it.place.code }.toSet())
    }

    @Test
    fun `quick add search matches on the folded name`() {
        assertEquals(listOf("FR"), quickAddRows(places, " fran ").map { it.place.code })
    }

    @Test
    fun `quick add groups rows into one section per continent`() {
        val sections = quickAddSections(quickAddRows(places, ""))
        assertEquals(listOf("Asia", "Europe"), sections.map { it.first })
        assertEquals(listOf("France", "Germany"), sections.first { it.first == "Europe" }.second.map { it.place.name })
    }

    @Test
    fun `ticking an unmarked place writes Been and unticking clears it`() {
        assertEquals(PlaceStatus.VISITED, quickAddTarget(PlaceStatus.NONE))
        assertEquals(PlaceStatus.NONE, quickAddTarget(PlaceStatus.VISITED))
    }

    @Test
    fun `lived places are locked so a stray tap cannot downgrade them`() {
        assertNull(quickAddTarget(PlaceStatus.LIVED))
        assertTrue(isQuickAddLocked(PlaceStatus.LIVED))
        assertFalse(isQuickAddLocked(PlaceStatus.VISITED))
    }

    @Test
    fun `ticking a want place writes Been`() {
        assertFalse(isQuickAddLocked(PlaceStatus.WISHLIST))
        assertEquals(PlaceStatus.VISITED, quickAddTarget(PlaceStatus.WISHLIST))
    }

    @Test
    fun `the checkbox counts Lived as been but not Want`() {
        assertTrue(quickAddChecked(PlaceStatus.VISITED))
        assertTrue(quickAddChecked(PlaceStatus.LIVED))
        assertFalse(quickAddChecked(PlaceStatus.WISHLIST))
        assertFalse(quickAddChecked(PlaceStatus.NONE))
    }
}
