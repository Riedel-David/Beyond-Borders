package com.riedel.aeroglobe.data.flights

import java.time.Duration
import java.time.Instant

/**
 * How long to wait before the next poll, so a free API quota survives real use: hourly
 * from a day out, every 5 minutes inside the last 3 hours, every 60 seconds once the
 * flight has left the gate. Returns null once there is nothing left to poll for.
 */
internal fun nextPollDelayMillis(now: Instant, scheduledDepartureUtc: Instant, phase: FlightPhase): Long? {
    if (phase == FlightPhase.LANDED || phase == FlightPhase.CANCELLED) return null
    if (phase == FlightPhase.DEPARTED || phase == FlightPhase.IN_AIR) return Duration.ofSeconds(60).toMillis()

    val untilDeparture = Duration.between(now, scheduledDepartureUtc)
    return when {
        untilDeparture > Duration.ofHours(24) -> Duration.ofHours(24).toMillis()
        untilDeparture > Duration.ofHours(3) -> Duration.ofHours(1).toMillis()
        else -> Duration.ofMinutes(5).toMillis()
    }
}
