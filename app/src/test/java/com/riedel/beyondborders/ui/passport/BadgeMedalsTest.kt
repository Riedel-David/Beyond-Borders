package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeMedalsTest {
    private fun badge(progress: Int, target: Int, id: String = "b") =
        Achievement(id, UiText.Plain("t"), UiText.Plain("d"), progress, target, AchievementGroup.COUNTS)

    @Test
    fun `tier mark is the target number when the title does not state it`() {
        assertEquals("25", tierMark(badge(3, 25), "Asia complete"))
        assertEquals("5", tierMark(badge(2, 5), "Caribbean"))
    }

    @Test
    fun `tier mark is absent when the title already states the number`() {
        assertNull(tierMark(badge(3, 25), "25 places"))
        assertNull(tierMark(badge(2, 5), "5 continents"))
        assertNull(tierMark(badge(2, 3), "Lived in 3"))
    }

    @Test
    fun `a longer number in the title does not hide a shorter tier`() {
        assertEquals("10", tierMark(badge(3, 10), "100 club"))
    }

    @Test
    fun `tier mark is absent for a single-step badge`() {
        assertNull(tierMark(badge(0, 1), "First stamp"))
        assertNull(tierMark(badge(0, 0), "Empty"))
    }

    @Test
    fun `ring fraction is progress over target`() {
        assertEquals(0.4f, ringFraction(badge(10, 25)), 0.0001f)
        assertEquals(0f, ringFraction(badge(0, 25)), 0f)
    }

    @Test
    fun `ring fraction is clamped to 0 and 1`() {
        assertEquals(1f, ringFraction(badge(30, 25)), 0f)
        assertEquals(0f, ringFraction(badge(-2, 25)), 0f)
    }

    @Test
    fun `ring fraction survives a zero target`() {
        assertEquals(1f, ringFraction(badge(1, 0)), 0f)
        assertEquals(0f, ringFraction(badge(0, 0)), 0f)
    }

    @Test
    fun `medal rows hold three each with a short last row`() {
        val rows = medalRows((1..7).map { badge(0, 1, "b$it") })
        assertEquals(listOf(3, 3, 1), rows.map { it.size })
        assertEquals("b7", rows.last().single().id)
    }

    @Test
    fun `medal rows of nothing are no rows`() {
        assertEquals(emptyList<List<Achievement>>(), medalRows(emptyList()))
    }
}
