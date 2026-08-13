package com.riedel.aeroglobe.data.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class OpenSkyAircraftPositionSourceTest {

    // Captured from a real, unauthenticated call to opensky-network.org/api/states/all
    // for icao24 47a05e (Norwegian DY2003, 2026-08-13) — see AeroDataBoxFlightDataSource.
    private val liveSample = """
        {"time":1786620281,"states":[["47a05e","NOZ6PU  ","Norway",1786620281,1786620617,20.8966,56.6652,11277.6,false,249.32,130.23,0,null,11597.64,"6132",false,0]]}
    """.trimIndent()

    @Test
    fun `a live state vector parses into a position`() {
        val position = parseOpenSkyStates(liveSample)!!

        assertEquals(56.6652, position.latitude, 0.0001)
        assertEquals(20.8966, position.longitude, 0.0001)
        assertEquals(11277.6, position.altitudeMeters!!, 0.01)
        assertEquals(249.32, position.velocityMs!!, 0.01)
        assertEquals(130.23, position.headingDegrees!!, 0.01)
        assertEquals(false, position.onGround)
        assertEquals(Instant.ofEpochSecond(1786620617), position.observedAt)
    }

    @Test
    fun `no matching aircraft is no signal`() {
        assertNull(parseOpenSkyStates("""{"time":1786620281,"states":null}"""))
    }

    @Test
    fun `an empty states list is no signal`() {
        assertNull(parseOpenSkyStates("""{"time":1786620281,"states":[]}"""))
    }
}
