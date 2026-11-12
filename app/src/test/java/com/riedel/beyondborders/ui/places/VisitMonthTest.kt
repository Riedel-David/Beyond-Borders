package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Visit
import com.riedel.beyondborders.data.place.cleanVisitMonth
import com.riedel.beyondborders.data.place.groupVisits
import com.riedel.beyondborders.data.place.isFutureVisitMonth
import com.riedel.beyondborders.data.place.isValidVisitMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitMonthTest {

    private fun v(id: String, year: Int?, month: Int? = null) = Visit(id, "FR", year, month)

    @Test
    fun `a month needs a year and must be 1 to 12`() {
        assertTrue(isValidVisitMonth(2023, 3))
        assertTrue(isValidVisitMonth(2023, null))
        assertTrue(isValidVisitMonth(null, null))
        assertFalse(isValidVisitMonth(null, 3))
        assertFalse(isValidVisitMonth(2023, 0))
        assertFalse(isValidVisitMonth(2023, 13))
        assertNull(cleanVisitMonth(null, 3))
        assertEquals(3, cleanVisitMonth(2023, 3))
    }

    @Test
    fun `newest first by year then month with a whole year after its dated months and undated last`() {
        val sorted = groupVisits(
            listOf(v("a", 2023), v("b", 2023, 3), v("c", 2023, 9), v("d", null), v("e", 2024), v("f", 2019, 12))
        ).getValue("FR")
        assertEquals(listOf("e", "c", "b", "a", "f", "d"), sorted.map { it.id })
    }

    @Test
    fun `timeline order is oldest first with undated last`() {
        val ordered = visitsChronological(listOf(v("a", 2023, 3), v("b", null), v("c", 2019), v("d", 2023)))
        assertEquals(listOf("c", "d", "a", "b"), ordered.map { it.id })
    }

    @Test
    fun `labels use the locale's short month names`() {
        assertEquals("Mar 2023", visitLabel(v("a", 2023, 3), "Undated", Locale.ENGLISH))
        assertEquals("2019", visitLabel(v("a", 2019), "Undated", Locale.ENGLISH))
        assertEquals("Undated", visitLabel(v("a", null), "Undated", Locale.ENGLISH))
        assertTrue(visitLabel(v("a", 2023, 3), "x", Locale.GERMAN).endsWith(" 2023"))
        assertEquals("2023, Mar 2019, Undated",
            visitYearsText(listOf(v("a", 2023), v("b", 2019, 3), v("c", null)), "Undated", Locale.ENGLISH))
    }

    @Test
    fun `only a later month of the current year is in the future`() {
        assertTrue(isFutureVisitMonth(2026, 12, 2026, 11))
        assertFalse(isFutureVisitMonth(2026, 11, 2026, 11))
        assertFalse(isFutureVisitMonth(2025, 12, 2026, 11))
        assertFalse(isFutureVisitMonth(2026, null, 2026, 11))
        assertFalse(isFutureVisitMonth(null, 5, 2026, 11))
    }
}
