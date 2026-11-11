package com.riedel.beyondborders.ui.settings

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastBackupTest {

    // 2026-11-11T23:30:00Z
    private val millis = 1_794_439_800_000L

    @Test
    fun `never backed up shows no date`() {
        assertNull(formatLastBackup(null, ZoneId.of("UTC"), Locale.US))
    }

    @Test
    fun `the date is in the user's zone`() {
        assertEquals("Nov 11, 2026", formatLastBackup(millis, ZoneId.of("UTC"), Locale.US))
        assertEquals("Nov 12, 2026", formatLastBackup(millis, ZoneId.of("Pacific/Auckland"), Locale.US))
    }

    @Test
    fun `the date follows the locale`() {
        assertEquals("11.11.2026", formatLastBackup(millis, ZoneId.of("UTC"), Locale.GERMANY))
    }

    @Test
    fun `an empty export does not count as a backup`() {
        assertFalse(isWorthStamping(places = 0, removals = 0))
        assertTrue(isWorthStamping(places = 1, removals = 0))
        assertTrue(isWorthStamping(places = 0, removals = 2))
    }
}
