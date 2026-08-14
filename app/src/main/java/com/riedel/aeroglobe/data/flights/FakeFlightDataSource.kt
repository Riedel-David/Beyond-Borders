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
    override suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FetchResult =
        FetchResult.Success(fakeStatusFor(flightId, flightNumber, date, Instant.now()))

    override suspend fun fetchInboundLeg(icao24: String, departureAirportIata: String, beforeUtc: Instant): InboundLeg =
        fakeInboundLegFor(icao24, departureAirportIata, beforeUtc)
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
    val airport = FAKE_AIRPORTS[rng.nextInt(FAKE_AIRPORTS.size)]

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
        departureAirportIata = airport,
    )
}

private val FAKE_AIRPORTS = listOf("OSL", "CDG", "AMS", "LHR", "FRA")

/** Deterministic per-aircraft/airport so the same flight sees the same inbound leg across polls. */
internal fun fakeInboundLegFor(icao24: String, departureAirportIata: String, beforeUtc: Instant): InboundLeg {
    val rng = Random((icao24 + departureAirportIata).hashCode())
    val origin = FAKE_AIRPORTS.filter { it != departureAirportIata }.let { it[rng.nextInt(it.size)] }
    val scheduledArrival = beforeUtc.minus(Duration.ofMinutes(rng.nextLong(30, 180)))
    val delayMinutes = rng.nextLong(-10, 60)
    return InboundLeg(
        originAirportIata = origin,
        scheduledArrivalUtc = scheduledArrival,
        estimatedArrivalUtc = scheduledArrival.plus(Duration.ofMinutes(delayMinutes)),
        phase = if (scheduledArrival.plus(Duration.ofMinutes(delayMinutes)).isBefore(beforeUtc)) {
            FlightPhase.LANDED
        } else {
            FlightPhase.IN_AIR
        },
    )
}
