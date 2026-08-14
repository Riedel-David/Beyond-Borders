package com.riedel.aeroglobe.data.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AeroDataBoxFlightDataSourceTest {

    private val now = Instant.parse("2026-08-13T10:00:00Z")

    @Test
    fun `a flight with a gate and terminal parses into a status`() {
        // AeroDataBox's real "utc" shape: a space separator and no seconds, not ISO-8601.
        val body = """
            [{
                "status": "Boarding",
                "departure": {
                    "scheduledTime": { "utc": "2026-08-13 12:00Z" },
                    "revisedTime": { "utc": "2026-08-13 12:20Z" },
                    "terminal": "2",
                    "gate": "42"
                },
                "aircraft": { "modeS": "47A05E" }
            }]
        """.trimIndent()

        val result = parseAeroDataBoxResponse("flight-1", body, now)

        assert(result is FetchResult.Success)
        val status = (result as FetchResult.Success).status
        assertEquals(Instant.parse("2026-08-13T12:00:00Z"), status.scheduledDepartureUtc)
        assertEquals(Instant.parse("2026-08-13T12:20:00Z"), status.estimatedDepartureUtc)
        assertEquals("42", status.departureGate)
        assertEquals("2", status.departureTerminal)
        assertEquals(FlightPhase.BOARDING, status.phase)
        assertEquals("47a05e", status.aircraftIcao24)
    }

    @Test
    fun `no revised time falls back to no delay estimate`() {
        val body = """
            [{
                "status": "Expected",
                "departure": { "scheduledTime": { "utc": "2026-08-13 12:00Z" } }
            }]
        """.trimIndent()

        val result = parseAeroDataBoxResponse("flight-1", body, now)

        assert(result is FetchResult.Success)
        val status = (result as FetchResult.Success).status
        assertNull(status.estimatedDepartureUtc)
        assertNull(status.departureGate)
        assertNull(status.aircraftIcao24)
    }

    @Test
    fun `an empty flight list is not found`() {
        val result = parseAeroDataBoxResponse("flight-1", "[]", now)
        assert(result is FetchResult.NotFound)
    }

    @Test
    fun `a flight with no scheduled time is a parse error`() {
        val body = """[{ "status": "Expected", "departure": {} }]"""
        val result = parseAeroDataBoxResponse("flight-1", body, now)
        assert(result is FetchResult.ParseError)
    }

    @Test
    fun `a flight with no aircraft assigned yet has no icao24`() {
        val body = """
            [{
                "status": "Expected",
                "departure": { "scheduledTime": { "utc": "2026-08-13 12:00Z" } }
            }]
        """.trimIndent()
        val result = parseAeroDataBoxResponse("flight-1", body, now)
        assert(result is FetchResult.Success)
        assertNull((result as FetchResult.Success).status.aircraftIcao24)
    }

    @Test
    fun `arrival, airline and aircraft model parse when present`() {
        val body = """
            [{
                "status": "EnRoute",
                "departure": {
                    "airport": { "iata": "AMS" },
                    "scheduledTime": { "utc": "2026-08-13 06:00Z" },
                    "runwayTime": { "utc": "2026-08-13 06:05Z" }
                },
                "arrival": {
                    "airport": { "iata": "OSL" },
                    "scheduledTime": { "utc": "2026-08-13 08:00Z" },
                    "revisedTime": { "utc": "2026-08-13 08:10Z" },
                    "runwayTime": { "utc": "2026-08-13 08:12Z" }
                },
                "airline": { "name": "SAS", "iata": "SK", "icao": "SAS" },
                "aircraft": { "modeS": "47A05E", "model": "A320neo" }
            }]
        """.trimIndent()

        val result = parseAeroDataBoxResponse("flight-1", body, now)

        assert(result is FetchResult.Success)
        val status = (result as FetchResult.Success).status
        assertEquals("AMS", status.departureAirportIata)
        assertEquals(Instant.parse("2026-08-13T06:05:00Z"), status.actualDepartureUtc)
        assertEquals("OSL", status.arrivalAirportIata)
        assertEquals(Instant.parse("2026-08-13T08:00:00Z"), status.scheduledArrivalUtc)
        assertEquals(Instant.parse("2026-08-13T08:10:00Z"), status.estimatedArrivalUtc)
        assertEquals(Instant.parse("2026-08-13T08:12:00Z"), status.actualArrivalUtc)
        assertEquals("SAS", status.airlineName)
        assertEquals("SK", status.airlineIata)
        assertEquals("SAS", status.airlineIcao)
        assertEquals("A320neo", status.aircraftModel)
    }

    @Test
    fun `missing arrival, airline and aircraft model leave those fields null`() {
        val body = """
            [{
                "status": "Expected",
                "departure": { "scheduledTime": { "utc": "2026-08-13 12:00Z" } }
            }]
        """.trimIndent()

        val result = parseAeroDataBoxResponse("flight-1", body, now)

        assert(result is FetchResult.Success)
        val status = (result as FetchResult.Success).status
        assertNull(status.arrivalAirportIata)
        assertNull(status.scheduledArrivalUtc)
        assertNull(status.airlineName)
        assertNull(status.aircraftModel)
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

    @Test
    fun `all documented status values map to a phase`() {
        assertEquals(FlightPhase.SCHEDULED, mapAeroDataBoxStatus("Unknown"))
        assertEquals(FlightPhase.SCHEDULED, mapAeroDataBoxStatus("Expected"))
        assertEquals(FlightPhase.SCHEDULED, mapAeroDataBoxStatus("CheckIn"))
        assertEquals(FlightPhase.BOARDING, mapAeroDataBoxStatus("Boarding"))
        assertEquals(FlightPhase.BOARDING, mapAeroDataBoxStatus("GateClosed"))
        assertEquals(FlightPhase.DEPARTED, mapAeroDataBoxStatus("Departed"))
        assertEquals(FlightPhase.SCHEDULED, mapAeroDataBoxStatus("Delayed"))
        assertEquals(FlightPhase.IN_AIR, mapAeroDataBoxStatus("Approaching"))
        assertEquals(FlightPhase.LANDED, mapAeroDataBoxStatus("Arrived"))
        assertEquals(FlightPhase.CANCELLED, mapAeroDataBoxStatus("Canceled"))
        assertEquals(FlightPhase.IN_AIR, mapAeroDataBoxStatus("Diverted"))
        assertEquals(FlightPhase.CANCELLED, mapAeroDataBoxStatus("CanceledUncertain"))
    }

    @Test
    fun `date parsing accepts the documented space-separated format`() {
        assertEquals(Instant.parse("2026-08-20T14:30:00Z"), parseAeroDataBoxUtc("2026-08-20 14:30Z"))
        assertEquals(Instant.parse("2026-08-20T14:30:00Z"), parseAeroDataBoxUtc("2026-08-20 14:30"))
    }

    @Test
    fun `date parsing accepts space-separated format with seconds`() {
        assertEquals(Instant.parse("2026-08-20T14:30:00Z"), parseAeroDataBoxUtc("2026-08-20 14:30:00Z"))
        assertEquals(Instant.parse("2026-08-20T14:30:00Z"), parseAeroDataBoxUtc("2026-08-20 14:30:00"))
    }

    @Test
    fun `date parsing accepts ISO-8601 with offset`() {
        assertEquals(Instant.parse("2026-08-20T14:30:00Z"), parseAeroDataBoxUtc("2026-08-20T14:30:00Z"))
        assertEquals(Instant.parse("2026-08-20T12:30:00Z"), parseAeroDataBoxUtc("2026-08-20T14:30:00+02:00"))
    }

    @Test
    fun `date parsing returns null for garbage`() {
        assertNull(parseAeroDataBoxUtc("not a date"))
        assertNull(parseAeroDataBoxUtc(""))
        assertNull(parseAeroDataBoxUtc("2026-13-45 99:99"))
    }

    @Test
    fun `the first request of all waits for nothing`() {
        assertEquals(0L, millisUntilNextRequest(lastRequestAtMillis = 0L, nowMillis = 5_000L, minIntervalMillis = 1_100L))
    }

    @Test
    fun `a request arriving inside the interval waits out the remainder`() {
        assertEquals(1_100L, millisUntilNextRequest(1_000L, 1_000L, 1_100L))
        assertEquals(100L, millisUntilNextRequest(1_000L, 2_000L, 1_100L))
    }

    @Test
    fun `a request arriving after the interval goes straight out`() {
        assertEquals(0L, millisUntilNextRequest(1_000L, 2_100L, 1_100L))
        assertEquals(0L, millisUntilNextRequest(1_000L, 60_000L, 1_100L))
    }

    @Test
    fun `a clock that went backwards does not stall the queue`() {
        assertEquals(0L, millisUntilNextRequest(5_000L, 1_000L, 1_100L))
    }

    @Test
    fun `the per-second rate limit is recognised as transient`() {
        assertEquals(
            true,
            isPerSecondRateLimit(
                "You have exceeded the rate limit per second for your plan, BASIC, by the API provider"
            ),
        )
    }

    @Test
    fun `a monthly quota rate limit is not transient`() {
        assertEquals(false, isPerSecondRateLimit("You have exceeded the MONTHLY quota for Requests on your current plan"))
        // An unrecognised 429 is treated as the expensive kind on purpose: guessing
        // "per-second" and being wrong retries against a spent plan for the rest of the month.
        assertEquals(false, isPerSecondRateLimit("Rate limit exceeded"))
    }

    @Test
    fun `a plain integer header parses as the units remaining`() {
        assertEquals(598, parseQuotaRemaining("598"))
    }

    @Test
    fun `a missing or non-numeric header has no known remaining count`() {
        assertNull(parseQuotaRemaining(null))
        assertNull(parseQuotaRemaining(""))
        assertNull(parseQuotaRemaining("unlimited"))
    }
}
