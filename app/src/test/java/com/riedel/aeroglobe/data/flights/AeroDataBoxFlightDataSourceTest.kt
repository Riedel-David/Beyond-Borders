package com.riedel.aeroglobe.data.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AeroDataBoxFlightDataSourceTest {

    private val now = Instant.parse("2026-08-13T10:00:00Z")

    @Test
    fun `a flight with a gate and terminal parses into a status`() {
        val body = """
            [{
                "status": "Boarding",
                "departure": {
                    "scheduledTime": { "utc": "2026-08-13T12:00:00Z" },
                    "revisedTime": { "utc": "2026-08-13T12:20:00Z" },
                    "terminal": "2",
                    "gate": "42"
                }
            }]
        """.trimIndent()

        val status = parseAeroDataBoxResponse("flight-1", body, now)

        assertEquals(Instant.parse("2026-08-13T12:00:00Z"), status?.scheduledDepartureUtc)
        assertEquals(Instant.parse("2026-08-13T12:20:00Z"), status?.estimatedDepartureUtc)
        assertEquals("42", status?.departureGate)
        assertEquals("2", status?.departureTerminal)
        assertEquals(FlightPhase.BOARDING, status?.phase)
    }

    @Test
    fun `no revised time falls back to no delay estimate`() {
        val body = """
            [{
                "status": "Expected",
                "departure": { "scheduledTime": { "utc": "2026-08-13T12:00:00Z" } }
            }]
        """.trimIndent()

        val status = parseAeroDataBoxResponse("flight-1", body, now)

        assertNull(status?.estimatedDepartureUtc)
        assertNull(status?.departureGate)
    }

    @Test
    fun `an empty flight list is no signal`() {
        assertNull(parseAeroDataBoxResponse("flight-1", "[]", now))
    }

    @Test
    fun `a flight with no scheduled time is no signal`() {
        val body = """[{ "status": "Expected", "departure": {} }]"""
        assertNull(parseAeroDataBoxResponse("flight-1", body, now))
    }

    @Test
    fun `unrecognised statuses default to scheduled rather than failing`() {
        assertEquals(FlightPhase.SCHEDULED, mapAeroDataBoxStatus("SomeNewStatus"))
    }

    @Test
    fun `known statuses map onto the existing flight phases`() {
        assertEquals(FlightPhase.DEPARTED, mapAeroDataBoxStatus("Departed"))
        assertEquals(FlightPhase.IN_AIR, mapAeroDataBoxStatus("EnRoute"))
        assertEquals(FlightPhase.LANDED, mapAeroDataBoxStatus("Landed"))
        assertEquals(FlightPhase.CANCELLED, mapAeroDataBoxStatus("Cancelled"))
    }
}
