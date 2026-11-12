package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.place.Visit
import com.riedel.beyondborders.data.place.groupVisits
import com.riedel.beyondborders.data.place.isValidVisitYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitStatsTest {

    private fun v(id: String, code: String, year: Int?) = Visit(id, code, year)
    private val been = PlaceStatus.VISITED

    @Test
    fun `years from 1900 to the current year are valid and null means undated`() {
        assertTrue(isValidVisitYear(1900, 2026))
        assertTrue(isValidVisitYear(2026, 2026))
        assertTrue(isValidVisitYear(null, 2026))
        assertFalse(isValidVisitYear(1899, 2026))
        assertFalse(isValidVisitYear(2027, 2026))
    }

    @Test
    fun `visits are ordered newest first with undated last`() {
        val grouped = groupVisits(listOf(v("a", "FR", null), v("b", "FR", 2019), v("c", "FR", 2023)))
        assertEquals(listOf(2023, 2019, null), grouped.getValue("FR").map { it.year })
    }

    @Test
    fun `visits of a place that is not Been are hidden from stats`() {
        val stats = computeVisitStats(
            mapOf("FR" to listOf(v("a", "FR", 2019)), "DK" to listOf(v("b", "DK", 2020))),
            mapOf("FR" to been, "DK" to PlaceStatus.WISHLIST),
        )
        assertEquals(listOf(2019 to 1), stats.byYear)
    }

    @Test
    fun `visits of a cleared place are hidden but come back when it is marked again`() {
        val visits = mapOf("FR" to listOf(v("a", "FR", 2019)))
        assertFalse(computeVisitStats(visits, emptyMap()).hasVisits)
        assertTrue(computeVisitStats(visits, mapOf("FR" to PlaceStatus.LIVED)).hasVisits)
    }

    @Test
    fun `a Been place with no visits is not counted anywhere`() {
        val stats = computeVisitStats(mapOf("FR" to listOf(v("a", "FR", 2019))), mapOf("FR" to been, "DK" to been))
        assertEquals(listOf(2019 to 1), stats.byYear)
    }

    @Test
    fun `an undated visit is not counted`() {
        val stats = computeVisitStats(
            mapOf("FR" to listOf(v("a", "FR", 2019), v("b", "FR", null))),
            mapOf("FR" to been),
        )
        assertEquals(listOf(2019 to 1), stats.byYear)
    }

    @Test
    fun `a place with two dated visits counts both years`() {
        val stats = computeVisitStats(
            mapOf("FR" to listOf(v("a", "FR", 2019), v("b", "FR", 2023))),
            mapOf("FR" to been),
        )
        assertEquals(listOf(2019 to 1, 2023 to 1), stats.byYear)
    }

    @Test
    fun `first and most recent come from the dated visits only`() {
        val stats = computeVisitStats(
            mapOf("FR" to listOf(v("a", "FR", 2023), v("b", "FR", null)), "DK" to listOf(v("c", "DK", 2015))),
            mapOf("FR" to been, "DK" to been),
        )
        assertEquals(2015, stats.first)
        assertEquals(2023, stats.mostRecent)
    }

    @Test
    fun `the section is hidden when only undated visits exist`() {
        val stats = computeVisitStats(mapOf("FR" to listOf(v("a", "FR", null))), mapOf("FR" to been))
        assertFalse(stats.hasVisits)
        assertNull(stats.first)
    }

    @Test
    fun `no visits at all means nothing to show and no first year`() {
        val stats = computeVisitStats(emptyMap(), mapOf("FR" to been))
        assertFalse(stats.hasVisits)
        assertNull(stats.first)
    }
}
