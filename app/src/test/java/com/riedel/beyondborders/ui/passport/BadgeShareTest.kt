package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeShareTest {

    private fun badge(progress: Int, target: Int = 10) = Achievement(
        "count_10",
        UiText.Plural(R.plurals.badge_count_n_title, 10, 10), UiText.Plural(R.plurals.badge_count_n_desc, 10, 10),
        progress, target, AchievementGroup.COUNTS,
    )

    @Test
    fun `rank line names the rank and the number of places`() {
        assertEquals(
            UiText.Res(R.string.badge_rank_line, UiText.Res(R.string.rank_explorer), UiText.Plural(R.plurals.badge_places_count, 20, 20)),
            badgeRankLine(20),
        )
    }

    @Test
    fun `rank line uses the singular for one place`() {
        assertEquals(
            UiText.Res(R.string.badge_rank_line, UiText.Res(R.string.rank_newcomer), UiText.Plural(R.plurals.badge_places_count, 1, 1)),
            badgeRankLine(1),
        )
    }

    @Test
    fun `rank line copes with a large count at the top rank`() {
        assertEquals(
            UiText.Res(R.string.badge_rank_line, UiText.Res(R.string.rank_legend), UiText.Plural(R.plurals.badge_places_count, 252, 252)),
            badgeRankLine(252),
        )
    }

    @Test
    fun `progress text of a locked badge is x of y`() {
        assertEquals(UiText.Res(R.string.badge_progress_of, 3, 10), badgeProgressText(badge(3)))
    }

    @Test
    fun `progress text of an unlocked badge says unlocked`() {
        assertEquals(UiText.Res(R.string.badge_unlocked), badgeProgressText(badge(10)))
    }

    @Test
    fun `share caption carries the title and the description`() {
        val b = badge(10)
        assertEquals(UiText.Res(R.string.badge_share_caption, b.title, b.description), badgeShareCaption(b))
    }
}
