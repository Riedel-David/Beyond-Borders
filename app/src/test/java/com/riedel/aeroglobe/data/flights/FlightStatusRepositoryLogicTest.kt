package com.riedel.aeroglobe.data.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FlightStatusRepositoryLogicTest {

    private val now = Instant.parse("2026-08-13T10:00:00Z")

    private fun status(gate: String?, terminal: String?, phase: FlightPhase) = FlightStatus(
        flightId = "flight-1",
        scheduledDepartureUtc = now,
        estimatedDepartureUtc = now,
        departureGate = gate,
        departureTerminal = terminal,
        phase = phase,
        fetchedAt = now,
    )

    @Test
    fun `a first sighting produces no changes`() {
        val current = status("A12", "A", FlightPhase.SCHEDULED)
        assertTrue(diffStatus(null, current).isEmpty())
    }

    @Test
    fun `an identical status produces no changes`() {
        val previous = status("A12", "A", FlightPhase.SCHEDULED)
        val current = status("A12", "A", FlightPhase.SCHEDULED)
        assertTrue(diffStatus(previous, current).isEmpty())
    }

    @Test
    fun `a gate change is recorded with the old and new value`() {
        val previous = status("A12", "A", FlightPhase.SCHEDULED)
        val current = status("B42", "B", FlightPhase.SCHEDULED)
        val changes = diffStatus(previous, current)
        val gateChange = changes.single { it.field == "gate" }
        assertEquals("A12", gateChange.oldValue)
        assertEquals("B42", gateChange.newValue)
    }

    @Test
    fun `a phase change is recorded alongside a gate change`() {
        val previous = status("A12", "A", FlightPhase.SCHEDULED)
        val current = status("B42", "B", FlightPhase.BOARDING)
        val fields = diffStatus(previous, current).map { it.field }.toSet()
        assertEquals(setOf("gate", "terminal", "phase"), fields)
    }
}
