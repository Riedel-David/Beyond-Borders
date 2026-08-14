package com.riedel.aeroglobe.data.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PollOutcomeTest {

    private val now = Instant.parse("2026-08-13T00:00:00Z")

    @Test
    fun `a spent monthly quota ends the chain instead of backing off`() {
        val outcome = determineOutcome(
            null,
            now,
            noStatusAttemptCount = 0,
            lastResult = FetchResult.RateLimited(transient = false, message = "MONTHLY quota exceeded"),
        )
        assertEquals(PollOutcome.Done, outcome)
    }

    @Test
    fun `a per-second rate limit still backs off rather than giving up`() {
        val outcome = determineOutcome(
            null,
            now,
            noStatusAttemptCount = 0,
            lastResult = FetchResult.RateLimited(transient = true, message = "rate limit per second"),
        )
        assertTrue(outcome is PollOutcome.Reschedule)
        assertEquals(Duration.ofMinutes(5).toMillis(), (outcome as PollOutcome.Reschedule).delayMillis)
    }

    @Test
    fun `null status on first attempt produces five minute reschedule and increments count to 1`() {
        val outcome = determineOutcome(null, now, noStatusAttemptCount = 0)
        assertTrue(outcome is PollOutcome.Reschedule)
        val reschedule = outcome as PollOutcome.Reschedule
        assertEquals(Duration.ofMinutes(5).toMillis(), reschedule.delayMillis)
        assertEquals(1, reschedule.nextAttemptCount)
    }

    @Test
    fun `null status on second attempt produces fifteen minute reschedule and increments count to 2`() {
        val outcome = determineOutcome(null, now, noStatusAttemptCount = 1)
        assertTrue(outcome is PollOutcome.Reschedule)
        val reschedule = outcome as PollOutcome.Reschedule
        assertEquals(Duration.ofMinutes(15).toMillis(), reschedule.delayMillis)
        assertEquals(2, reschedule.nextAttemptCount)
    }

    @Test
    fun `null status on third attempt produces one hour reschedule and increments count to 3`() {
        val outcome = determineOutcome(null, now, noStatusAttemptCount = 2)
        assertTrue(outcome is PollOutcome.Reschedule)
        val reschedule = outcome as PollOutcome.Reschedule
        assertEquals(Duration.ofHours(1).toMillis(), reschedule.delayMillis)
        assertEquals(3, reschedule.nextAttemptCount)
    }

    @Test
    fun `null status past third attempt gives up and produces done outcome`() {
        val outcome = determineOutcome(null, now, noStatusAttemptCount = 3)
        assertTrue(outcome is PollOutcome.Done)
    }

    @Test
    fun `no-status progression chains correctly by feeding nextAttemptCount forward`() {
        // First attempt: 5 min, count 1
        val outcome1 = determineOutcome(null, now, noStatusAttemptCount = 0)
        assertTrue(outcome1 is PollOutcome.Reschedule)
        assertEquals(Duration.ofMinutes(5).toMillis(), (outcome1 as PollOutcome.Reschedule).delayMillis)

        // Second attempt: 15 min, count 2
        val outcome2 = determineOutcome(null, now, noStatusAttemptCount = outcome1.nextAttemptCount)
        assertTrue(outcome2 is PollOutcome.Reschedule)
        assertEquals(Duration.ofMinutes(15).toMillis(), (outcome2 as PollOutcome.Reschedule).delayMillis)

        // Third attempt: 1 hour, count 3
        val outcome3 = determineOutcome(null, now, noStatusAttemptCount = outcome2.nextAttemptCount)
        assertTrue(outcome3 is PollOutcome.Reschedule)
        assertEquals(Duration.ofHours(1).toMillis(), (outcome3 as PollOutcome.Reschedule).delayMillis)

        // Fourth attempt: give up
        val outcome4 = determineOutcome(null, now, noStatusAttemptCount = outcome3.nextAttemptCount)
        assertTrue(outcome4 is PollOutcome.Done)
    }

    @Test
    fun `landed flight produces done outcome`() {
        val status = FlightStatus(
            flightId = "test",
            scheduledDepartureUtc = now.minus(Duration.ofHours(3)),
            estimatedDepartureUtc = null,
            departureGate = null,
            departureTerminal = null,
            phase = FlightPhase.LANDED,
            fetchedAt = now
        )
        val outcome = determineOutcome(status, now, noStatusAttemptCount = 0)
        assertTrue(outcome is PollOutcome.Done)
    }

    @Test
    fun `cancelled flight produces done outcome`() {
        val status = FlightStatus(
            flightId = "test",
            scheduledDepartureUtc = now.plus(Duration.ofHours(5)),
            estimatedDepartureUtc = null,
            departureGate = null,
            departureTerminal = null,
            phase = FlightPhase.CANCELLED,
            fetchedAt = now
        )
        val outcome = determineOutcome(status, now, noStatusAttemptCount = 0)
        assertTrue(outcome is PollOutcome.Done)
    }

    @Test
    fun `scheduled flight more than a day out produces reschedule with one day delay and resets count to 0`() {
        val status = FlightStatus(
            flightId = "test",
            scheduledDepartureUtc = now.plus(Duration.ofHours(30)),
            estimatedDepartureUtc = null,
            departureGate = null,
            departureTerminal = null,
            phase = FlightPhase.SCHEDULED,
            fetchedAt = now
        )
        val outcome = determineOutcome(status, now, noStatusAttemptCount = 2)
        assertTrue(outcome is PollOutcome.Reschedule)
        val reschedule = outcome as PollOutcome.Reschedule
        assertEquals(Duration.ofHours(24).toMillis(), reschedule.delayMillis)
        assertEquals(0, reschedule.nextAttemptCount)
    }

    @Test
    fun `boarding flight inside three hours produces reschedule with five minute delay and resets count to 0`() {
        val status = FlightStatus(
            flightId = "test",
            scheduledDepartureUtc = now.plus(Duration.ofHours(2)),
            estimatedDepartureUtc = null,
            departureGate = null,
            departureTerminal = null,
            phase = FlightPhase.BOARDING,
            fetchedAt = now
        )
        val outcome = determineOutcome(status, now, noStatusAttemptCount = 2)
        assertTrue(outcome is PollOutcome.Reschedule)
        val reschedule = outcome as PollOutcome.Reschedule
        assertEquals(Duration.ofMinutes(5).toMillis(), reschedule.delayMillis)
        assertEquals(0, reschedule.nextAttemptCount)
    }

    @Test
    fun `in-air flight produces reschedule with thirty minute delay and resets count to 0`() {
        val status = FlightStatus(
            flightId = "test",
            scheduledDepartureUtc = now.minus(Duration.ofHours(1)),
            estimatedDepartureUtc = null,
            departureGate = null,
            departureTerminal = null,
            phase = FlightPhase.IN_AIR,
            fetchedAt = now
        )
        val outcome = determineOutcome(status, now, noStatusAttemptCount = 2)
        assertTrue(outcome is PollOutcome.Reschedule)
        val reschedule = outcome as PollOutcome.Reschedule
        assertEquals(Duration.ofMinutes(30).toMillis(), reschedule.delayMillis)
        assertEquals(0, reschedule.nextAttemptCount)
    }

    @Test
    fun `successful fetch after previous no-status attempts resets count to 0`() {
        val status = FlightStatus(
            flightId = "test",
            scheduledDepartureUtc = now.plus(Duration.ofHours(10)),
            estimatedDepartureUtc = null,
            departureGate = null,
            departureTerminal = null,
            phase = FlightPhase.SCHEDULED,
            fetchedAt = now
        )
        // Previous attempts were at count 2, but successful fetch resets to 0
        val outcome = determineOutcome(status, now, noStatusAttemptCount = 2)
        assertTrue(outcome is PollOutcome.Reschedule)
        assertEquals(0, (outcome as PollOutcome.Reschedule).nextAttemptCount)
    }
}
