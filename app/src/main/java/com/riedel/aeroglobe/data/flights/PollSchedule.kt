package com.riedel.aeroglobe.data.flights

import java.time.Duration
import java.time.Instant

/**
 * A user-facing speed for the adaptive schedule below. The multiplier scales the
 * returned delay only — which tier applies still depends on the real, unscaled time
 * to departure, so SLOW never means "check less often near departure than far out."
 */
enum class RefreshCadence(val multiplier: Double) {
    FAST(1.0),
    NORMAL(2.0),
    SLOW(4.0),
}

/**
 * How long to wait before the next poll, so a free API quota survives real use: hourly
 * from a day out, every 5 minutes inside the last 3 hours. Once departed, every 30
 * minutes rather than every few seconds — there is nothing to act on mid-flight, and
 * in-flight wifi is not a given, so this leg just waits to catch the flip to landed
 * without burning quota on updates nobody can use. Returns null once there is nothing
 * left to poll for. [multiplier] scales the delay for the settings cadence picker.
 */
internal fun nextPollDelayMillis(
    now: Instant,
    scheduledDepartureUtc: Instant,
    phase: FlightPhase,
    multiplier: Double = 1.0,
): Long? {
    if (phase == FlightPhase.LANDED || phase == FlightPhase.CANCELLED) return null
    if (phase == FlightPhase.DEPARTED || phase == FlightPhase.IN_AIR) {
        return (Duration.ofMinutes(30).toMillis() * multiplier).toLong()
    }

    val untilDeparture = Duration.between(now, scheduledDepartureUtc)
    val base = when {
        untilDeparture > Duration.ofHours(24) -> Duration.ofHours(24).toMillis()
        untilDeparture > Duration.ofHours(3) -> Duration.ofHours(1).toMillis()
        else -> Duration.ofMinutes(5).toMillis()
    }
    return (base * multiplier).toLong()
}
