package com.riedel.aeroglobe.data.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PollScheduleLogicTest {

    private val now = Instant.parse("2026-08-13T00:00:00Z")

    @Test
    fun `more than a day out the next poll is a day away`() {
        val departure = now.plus(Duration.ofHours(30))
        assertEquals(Duration.ofHours(24).toMillis(), nextPollDelayMillis(now, departure, FlightPhase.SCHEDULED))
    }

    @Test
    fun `between three and twenty four hours out the next poll is an hour away`() {
        val departure = now.plus(Duration.ofHours(10))
        assertEquals(Duration.ofHours(1).toMillis(), nextPollDelayMillis(now, departure, FlightPhase.SCHEDULED))
    }

    @Test
    fun `inside three hours the next poll is five minutes away`() {
        val departure = now.plus(Duration.ofHours(2))
        assertEquals(Duration.ofMinutes(5).toMillis(), nextPollDelayMillis(now, departure, FlightPhase.BOARDING))
    }

    @Test
    fun `once departed the next poll is sixty seconds away regardless of schedule`() {
        val departure = now.minus(Duration.ofHours(1))
        assertEquals(Duration.ofSeconds(60).toMillis(), nextPollDelayMillis(now, departure, FlightPhase.IN_AIR))
    }

    @Test
    fun `a landed flight has no next poll`() {
        assertNull(nextPollDelayMillis(now, now.minus(Duration.ofHours(3)), FlightPhase.LANDED))
    }

    @Test
    fun `a cancelled flight has no next poll`() {
        assertNull(nextPollDelayMillis(now, now.plus(Duration.ofHours(5)), FlightPhase.CANCELLED))
    }
}
