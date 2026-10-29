package com.riedel.beyondborders.ui.passport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankTest {
    @Test
    fun `nobody visited is a Newcomer five away from Wanderer`() {
        assertEquals(RankInfo("Newcomer", "Wanderer", 5), rankFor(0))
    }

    @Test
    fun `a threshold itself already counts for that rank`() {
        assertEquals("Wanderer", rankFor(5).title)
        assertEquals("Explorer", rankFor(15).title)
        assertEquals("Adventurer", rankFor(30).title)
        assertEquals("Globetrotter", rankFor(60).title)
        assertEquals("World traveller", rankFor(100).title)
        assertEquals("Legend", rankFor(150).title)
    }

    @Test
    fun `one below a threshold stays on the lower rank with one to go`() {
        assertEquals(RankInfo("Newcomer", "Wanderer", 1), rankFor(4))
        assertEquals(RankInfo("World traveller", "Legend", 1), rankFor(149))
    }

    @Test
    fun `Legend has no next rank and a closing line`() {
        val r = rankFor(200)
        assertNull(r.next)
        assertNull(r.remaining)
        assertEquals("You have seen it all.", r.progressLine)
    }

    @Test
    fun `progress line names the remaining count and the next rank`() {
        assertEquals("12 more to reach Explorer", rankFor(3).copy(next = "Explorer", remaining = 12).progressLine)
    }

    @Test
    fun `group progress counts unlocked and total per group`() {
        fun a(id: String, p: Int, g: AchievementGroup) = Achievement(id, id, "", p, 1, g)
        val m = groupProgress(
            listOf(
                a("a", 1, AchievementGroup.REGIONS), a("b", 0, AchievementGroup.REGIONS),
                a("c", 1, AchievementGroup.TINY),
            ),
        )
        assertEquals(1 to 2, m[AchievementGroup.REGIONS])
        assertEquals(1 to 1, m[AchievementGroup.TINY])
        assertNull(m[AchievementGroup.COUNTS])
    }
}
