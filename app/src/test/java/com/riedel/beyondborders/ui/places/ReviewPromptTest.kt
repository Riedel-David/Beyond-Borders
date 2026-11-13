package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.settings.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val oldInstall = now - 10 * day

    private fun eligible(
        been: Int = 5,
        installedAt: Long = oldInstall,
        state: ReviewState = ReviewState(),
    ) = reviewPromptEligible(been, installedAt, now, state)

    @Test fun `five places on a week-old install with no history is eligible`() = assertTrue(eligible())

    @Test fun `fewer than five been places is not eligible`() = assertFalse(eligible(been = 4))

    @Test fun `an install under seven days old is not eligible`() = assertFalse(eligible(installedAt = now - 6 * day))

    @Test fun `an install exactly seven days old is eligible`() = assertTrue(eligible(installedAt = now - 7 * day))

    @Test fun `done is never eligible`() = assertFalse(eligible(state = ReviewState(1, now - 90 * day, done = true)))

    @Test fun `two asks used up is not eligible`() = assertFalse(eligible(state = ReviewState(2, now - 90 * day, false)))

    @Test fun `within thirty days of the last ask is not eligible`() =
        assertFalse(eligible(state = ReviewState(1, now - 29 * day, false)))

    @Test fun `thirty days after the first ask is eligible again`() =
        assertTrue(eligible(state = ReviewState(1, now - 30 * day, false)))

    @Test fun `a last ask dated in the future is treated as long enough ago`() =
        assertTrue(eligible(state = ReviewState(1, now + 5 * day, false)))

    @Test fun `rate marks done and counts the ask`() =
        assertEquals(ReviewState(1, now, true), reviewStateAfter(ReviewState(), ReviewChoice.RATE, now))

    @Test fun `dont ask again marks done and counts the ask`() =
        assertEquals(ReviewState(1, now, true), reviewStateAfter(ReviewState(), ReviewChoice.NEVER, now))

    @Test fun `later on the first ask postpones without finishing`() =
        assertEquals(ReviewState(1, now, false), reviewStateAfter(ReviewState(), ReviewChoice.LATER, now))

    @Test fun `only a play store install or a debug build may be asked`() {
        assertEquals(true, reviewChannelAllowed("com.android.vending", false))
        assertEquals(false, reviewChannelAllowed(null, false))
        assertEquals(false, reviewChannelAllowed("org.fdroid.fdroid", false))
        assertEquals(true, reviewChannelAllowed(null, true))
    }

    @Test fun `a dismissed first prompt waits out the retry period without having used an ask`() {
        val dismissedYesterday = ReviewState(0, now - day, false)
        assertEquals(false, reviewPromptEligible(5, now - 10 * day, now, dismissedYesterday))
        assertEquals(true, reviewPromptEligible(5, now - 100 * day, now, dismissedYesterday.copy(lastAskedAtMs = now - 31 * day)))
    }

    @Test fun `later on the second ask counts as never`() =
        assertEquals(ReviewState(2, now, true), reviewStateAfter(ReviewState(1, now - 40 * day, false), ReviewChoice.LATER, now))
}
