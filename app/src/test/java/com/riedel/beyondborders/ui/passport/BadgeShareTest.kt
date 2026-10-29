package com.riedel.beyondborders.ui.passport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeShareTest {

    private fun badge(progress: Int, target: Int = 10) = Achievement(
        "count_10", "10 places", "Be in 10 places.", progress, target, AchievementGroup.COUNTS,
    )

    @Test
    fun `rank line names the rank and the number of places`() {
        assertEquals("Explorer · 20 places", badgeRankLine(20))
    }

    @Test
    fun `rank line uses the singular for one place`() {
        assertEquals("Newcomer · 1 place", badgeRankLine(1))
    }

    @Test
    fun `rank line copes with a large count at the top rank`() {
        assertEquals("Legend · 252 places", badgeRankLine(252))
    }

    @Test
    fun `progress text of a locked badge is x of y`() {
        assertEquals("3 of 10", badgeProgressText(badge(3)))
    }

    @Test
    fun `progress text of an unlocked badge says unlocked`() {
        assertEquals("Unlocked", badgeProgressText(badge(10)))
    }

    @Test
    fun `share caption carries the title and the description`() {
        val caption = badgeShareCaption(badge(10))
        assertTrue(caption.contains("10 places"))
        assertTrue(caption.contains("Be in 10 places."))
        assertTrue(caption.contains("Beyond Borders"))
    }
}
