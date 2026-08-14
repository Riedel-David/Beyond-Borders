package com.riedel.aeroglobe.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SettingsStoreLogicTest {

    @Test
    fun `the month key is a calendar month in UTC`() {
        assertEquals("2026-08", monthKey(Instant.parse("2026-08-13T23:59:59Z")))
        assertEquals("2026-01", monthKey(Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `a stored count carries over within the same month`() {
        assertEquals(5, currentMonthRequestCount(storedMonth = "2026-08", storedCount = 5, nowMonth = "2026-08"))
    }

    @Test
    fun `a stored count resets once the month has changed`() {
        assertEquals(0, currentMonthRequestCount(storedMonth = "2026-07", storedCount = 5, nowMonth = "2026-08"))
    }

    @Test
    fun `no stored month at all also starts the count at zero`() {
        assertEquals(0, currentMonthRequestCount(storedMonth = null, storedCount = 0, nowMonth = "2026-08"))
    }
}
