package com.riedel.beyondborders.ui.passport

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AdvisorySectionTest {
    @Test
    fun `each stance maps to its own tone and stance 0 is neutral`() {
        assertEquals(AdvisoryTone.NEUTRAL, advisoryTone(0))
        assertEquals(AdvisoryTone.CAUTION, advisoryTone(1))
        assertEquals(AdvisoryTone.WARNING, advisoryTone(2))
        assertEquals(AdvisoryTone.DANGER, advisoryTone(3))
    }

    @Test
    fun `a stance outside the range is neutral rather than alarming`() {
        assertEquals(AdvisoryTone.NEUTRAL, advisoryTone(-1))
        assertEquals(AdvisoryTone.NEUTRAL, advisoryTone(9))
    }

    @Test
    fun `the updated date follows the locale and an unreadable one is shown as given`() {
        // The exact German pattern varies with the JDK's locale data; that it differs from US is the claim.
        assertNotEquals(formatAdvisoryDate("2026-10-02", Locale.US), formatAdvisoryDate("2026-10-02", Locale.GERMANY))
        assertEquals("Oct 2, 2026", formatAdvisoryDate("2026-10-02", Locale.US))
        assertEquals("yesterday", formatAdvisoryDate("yesterday", Locale.US))
    }
}
