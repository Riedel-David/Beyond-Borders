package com.riedel.aeroglobe.data.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FakeFlightDataSourceLogicTest {

    private val departure = fakeStatusFor("id", "LH 400", "2026-08-13", Instant.EPOCH).scheduledDepartureUtc

    @Test
    fun `the same flight, date and instant always produce the same status`() {
        val a = fakeStatusFor("id", "LH 400", "2026-08-13", Instant.EPOCH)
        val b = fakeStatusFor("id", "LH 400", "2026-08-13", Instant.EPOCH)
        assertEquals(a, b)
    }

    @Test
    fun `more than three hours out no gate is assigned yet`() {
        val status = fakeStatusFor("id", "LH 400", "2026-08-13", departure.minus(Duration.ofHours(4)))
        assertNull(status.departureGate)
        assertEquals(FlightPhase.SCHEDULED, status.phase)
    }

    @Test
    fun `inside three hours a gate is assigned before boarding`() {
        val status = fakeStatusFor("id", "LH 400", "2026-08-13", departure.minus(Duration.ofHours(1)))
        assertNotNull(status.departureGate)
        assertEquals(FlightPhase.SCHEDULED, status.phase)
    }

    @Test
    fun `the gate changes once boarding starts`() {
        val beforeBoarding = fakeStatusFor("id", "LH 400", "2026-08-13", departure.minus(Duration.ofHours(1)))
        val boarding = fakeStatusFor("id", "LH 400", "2026-08-13", departure.minus(Duration.ofMinutes(10)))
        assertEquals(FlightPhase.BOARDING, boarding.phase)
        assertNotEquals(beforeBoarding.departureGate, boarding.departureGate)
    }

    @Test
    fun `once departed the phase is in the air`() {
        val status = fakeStatusFor("id", "LH 400", "2026-08-13", departure.plus(Duration.ofMinutes(30)))
        assertEquals(FlightPhase.IN_AIR, status.phase)
    }

    @Test
    fun `after the fake two hour flight duration the phase is landed`() {
        val status = fakeStatusFor("id", "LH 400", "2026-08-13", departure.plus(Duration.ofHours(3)))
        assertEquals(FlightPhase.LANDED, status.phase)
    }

    @Test
    fun `the same aircraft and airport always produce the same inbound leg`() {
        val a = fakeInboundLegFor("47a05e", "OSL", Instant.EPOCH)
        val b = fakeInboundLegFor("47a05e", "OSL", Instant.EPOCH)
        assertEquals(a, b)
    }

    @Test
    fun `the fake inbound leg never originates from its own departure airport`() {
        val leg = fakeInboundLegFor("47a05e", "OSL", Instant.EPOCH)
        assertNotEquals("OSL", leg.originAirportIata)
    }
}
