package com.riedel.aeroglobe.data.flights

import java.time.Duration
import java.time.Instant

/** Where a flight is in its journey — drives both the UI and the poll cadence. */
enum class FlightPhase {
    SCHEDULED,
    BOARDING,
    DEPARTED,
    IN_AIR,
    LANDED,
    CANCELLED,
}

/** A schedule/status snapshot for one tracked flight, as of [fetchedAt]. */
data class FlightStatus(
    val flightId: String,
    val scheduledDepartureUtc: Instant,
    val estimatedDepartureUtc: Instant?,
    val departureGate: String?,
    val departureTerminal: String?,
    val phase: FlightPhase,
    val fetchedAt: Instant,
    val aircraftIcao24: String? = null,
    val departureAirportIata: String? = null,
    val actualDepartureUtc: Instant? = null,
    val arrivalAirportIata: String? = null,
    val scheduledArrivalUtc: Instant? = null,
    val estimatedArrivalUtc: Instant? = null,
    val actualArrivalUtc: Instant? = null,
    val airlineName: String? = null,
    val airlineIata: String? = null,
    val airlineIcao: String? = null,
    val aircraftModel: String? = null,
)

/** One field that changed between two [FlightStatus] snapshots, e.g. "gate" A15 -> B42. */
data class StatusChange(
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Instant,
)

/** Minutes late vs. schedule, or null when no estimate has been fetched yet. */
internal fun delayMinutes(status: FlightStatus): Long? =
    status.estimatedDepartureUtc?.let { Duration.between(status.scheduledDepartureUtc, it).toMinutes() }
