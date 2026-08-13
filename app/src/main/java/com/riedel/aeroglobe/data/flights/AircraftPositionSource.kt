package com.riedel.aeroglobe.data.flights

import java.time.Instant

/** A live position for one aircraft, as of [observedAt] — OpenSky's own last-contact time, not fetch time. */
data class AircraftPosition(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val velocityMs: Double?,
    val headingDegrees: Double?,
    val onGround: Boolean,
    val observedAt: Instant,
)

/**
 * Live position, keyed by the aircraft's own ICAO24/Mode-S address rather than a
 * flight number — a transponder broadcasts its position with no notion of a flight.
 */
interface AircraftPositionSource {
    /** Returns null on any failure or when the aircraft has no current position (e.g. still on the ground, no signal). */
    suspend fun fetchPosition(icao24: String): AircraftPosition?
}
