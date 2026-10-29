package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankTest {
    private fun rank(id: Int) = UiText.Res(id)

    @Test
    fun `nobody visited is a Newcomer five away from Wanderer`() {
        assertEquals(RankInfo(rank(R.string.rank_newcomer), rank(R.string.rank_wanderer), 5), rankFor(0))
    }

    @Test
    fun `a threshold itself already counts for that rank`() {
        assertEquals(rank(R.string.rank_wanderer), rankFor(5).title)
        assertEquals(rank(R.string.rank_explorer), rankFor(15).title)
        assertEquals(rank(R.string.rank_adventurer), rankFor(30).title)
        assertEquals(rank(R.string.rank_globetrotter), rankFor(60).title)
        assertEquals(rank(R.string.rank_world_traveller), rankFor(100).title)
        assertEquals(rank(R.string.rank_legend), rankFor(150).title)
    }

    @Test
    fun `one below a threshold stays on the lower rank with one to go`() {
        assertEquals(RankInfo(rank(R.string.rank_newcomer), rank(R.string.rank_wanderer), 1), rankFor(4))
        assertEquals(RankInfo(rank(R.string.rank_world_traveller), rank(R.string.rank_legend), 1), rankFor(149))
    }

    @Test
    fun `Legend has no next rank and a closing line`() {
        val r = rankFor(200)
        assertNull(r.next)
        assertNull(r.remaining)
        assertEquals(UiText.Res(R.string.rank_top_reached), r.progressLine)
    }

    @Test
    fun `progress line names the remaining count and the next rank`() {
        assertEquals(
            UiText.Res(R.string.rank_progress, 12, rank(R.string.rank_explorer)),
            rankFor(3).copy(next = rank(R.string.rank_explorer), remaining = 12).progressLine,
        )
    }

    @Test
    fun `group progress counts unlocked and total per group`() {
        fun a(id: String, p: Int, g: AchievementGroup) = Achievement(id, UiText.Plain(id), UiText.Plain(""), p, 1, g)
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
