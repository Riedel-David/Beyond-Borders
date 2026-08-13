package com.riedel.aeroglobe.data.flights

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Deterministic per-flight schedule and gate, seeded from the flight number and date so
 * the same flight looks the same across polls. The gate changes once at T-45m so the
 * status-change history (3c) has something to show without waiting on a real provider.
 */
class FakeFlightDataSource : FlightDataSource {
    override suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FlightStatus =
        fakeStatusFor(flightId, flightNumber, date, Instant.now())
}

// --- pure functions, unit-testable without Android ---------------------------

internal fun fakeStatusFor(flightId: String, flightNumber: String, date: String, now: Instant): FlightStatus {
    val rng = Random((flightNumber + date).hashCode())
    val scheduledDeparture = LocalDate.parse(date)
        .atTime(rng.nextInt(24), listOf(0, 15, 30, 45)[rng.nextInt(4)])
        .toInstant(ZoneOffset.UTC)
    val arrival = scheduledDeparture.plus(Duration.ofHours(2))
    val boarding = scheduledDeparture.minus(Duration.ofMinutes(45))
    val gateAssigned = scheduledDeparture.minus(Duration.ofHours(3))
    val terminal = ('A' + rng.nextInt(5)).toString()
    val gateBeforeBoarding = terminal + rng.nextInt(10, 30)
    val gateFromBoarding = terminal + rng.nextInt(30, 60)

    val (phase, gate) = when {
        !now.isBefore(arrival) -> FlightPhase.LANDED to gateFromBoarding
        !now.isBefore(scheduledDeparture) -> FlightPhase.IN_AIR to gateFromBoarding
        !now.isBefore(boarding) -> FlightPhase.BOARDING to gateFromBoarding
        !now.isBefore(gateAssigned) -> FlightPhase.SCHEDULED to gateBeforeBoarding
        else -> FlightPhase.SCHEDULED to null
    }

    return FlightStatus(
        flightId = flightId,
        scheduledDepartureUtc = scheduledDeparture,
        estimatedDepartureUtc = scheduledDeparture,
        departureGate = gate,
        departureTerminal = gate?.let { terminal },
        phase = phase,
        fetchedAt = now,
        aircraftIcao24 = rng.nextInt(0x1000000).toString(16).padStart(6, '0'),
    )
}
