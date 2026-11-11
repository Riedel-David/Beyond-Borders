package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    )

    @Test
    fun `quick add lists sovereign states including Taiwan but not Greenland`() {
        val codes = quickAddRows(places, "").map { it.place.code }
        assertFalse("GL" in codes)
        assertEquals(setOf("DK", "FR", "JP", "TW"), codes.toSet())
    }

    @Test
    fun `quick add search matches on the folded name`() {
        assertEquals(listOf("FR"), quickAddRows(places, " fran ").map { it.place.code })
    }

    @Test
    fun `quick add groups rows into one section per continent`() {
        val sections = quickAddSections(quickAddRows(places, ""))
        assertEquals(listOf("Asia", "Europe"), sections.map { it.first })
        assertEquals(listOf("Denmark", "France"), sections.first { it.first == "Europe" }.second.map { it.place.name })
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
