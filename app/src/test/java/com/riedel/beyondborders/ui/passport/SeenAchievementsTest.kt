package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeenAchievementsTest {

    private fun badge(id: String, progress: Int, target: Int, title: String = id) =
        Achievement(id, UiText.Plain(title), UiText.Plain(""), progress, target, AchievementGroup.COUNTS)

    @Test
    fun `next up takes the three highest ratios among locked badges with progress`() {
        val all = listOf(
            badge("a", 1, 10), badge("b", 9, 10), badge("c", 5, 10), badge("d", 7, 10),
            badge("done", 10, 10), badge("zero", 0, 5),
        )
        assertEquals(listOf("b", "d", "c"), nextUp(all).map { it.id })
    }

    @Test
    fun `next up breaks equal ratios by id`() {
        val all = listOf(badge("z", 1, 2), badge("b", 2, 4), badge("m", 3, 6), badge("a", 1, 3))
        assertEquals(listOf("b", "m", "z"), nextUp(all).map { it.id })
    }

    @Test
    fun `next up is empty when nothing is in progress`() {
        assertEquals(emptyList<Achievement>(), nextUp(listOf(badge("a", 0, 3), badge("b", 3, 3))))
    }

    @Test
    fun `a never seeded set seeds with the unlocked ids and celebrates nothing`() {
        val update = newlyUnlocked(null, listOf(badge("a", 1, 1), badge("b", 0, 1)))
        assertEquals(emptyList<Achievement>(), update.fresh)
        assertEquals(setOf("a"), update.seen)
    }

    @Test
    fun `a seeded empty set treats every unlocked badge as new`() {
        val update = newlyUnlocked(emptySet(), listOf(badge("a", 1, 1), badge("b", 0, 1)))
        assertEquals(listOf("a"), update.fresh.map { it.id })
        assertEquals(setOf("a"), update.seen)
    }

    @Test
    fun `only unlocked badges missing from the seen set are new`() {
        val update = newlyUnlocked(setOf("a"), listOf(badge("a", 1, 1), badge("b", 1, 1), badge("c", 0, 1)))
        assertEquals(listOf("b"), update.fresh.map { it.id })
        assertEquals(setOf("a", "b"), update.seen)
    }

    @Test
    fun `a seen badge that locks again keeps its id and is not new on return`() {
        val locked = newlyUnlocked(setOf("a"), listOf(badge("a", 0, 1)))
        assertEquals(setOf("a"), locked.seen)
        assertEquals(emptyList<Achievement>(), newlyUnlocked(locked.seen, listOf(badge("a", 1, 1))).fresh)
    }

    @Test
    fun `celebration message names one badge or counts the rest`() {
        assertNull(celebrationMessage(emptyList()))
        assertEquals(UiText.Res(R.string.badge_celebrate_one, UiText.Plain("X")), celebrationMessage(listOf(badge("x", 1, 1, "X"))))
        assertEquals(
            UiText.Res(R.string.badge_celebrate_more, UiText.Plain("X"), 2),
            celebrationMessage(listOf(badge("x", 1, 1, "X"), badge("y", 1, 1), badge("z", 1, 1))),
        )
    }
}
