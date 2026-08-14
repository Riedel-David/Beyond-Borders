package com.riedel.aeroglobe.data.flights

import java.time.Instant

/**
 * Picks [real] or [fake] per call rather than at construction, so flipping the settings
 * toggle takes effect on the next poll instead of needing an app restart.
 */
class SwitchingFlightDataSource(
    private val useReal: () -> Boolean,
    private val real: FlightDataSource,
    private val fake: FlightDataSource,
) : FlightDataSource {
    override suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FetchResult =
        if (useReal()) real.fetchStatus(flightId, flightNumber, date) else fake.fetchStatus(flightId, flightNumber, date)

    override suspend fun fetchInboundLeg(icao24: String, departureAirportIata: String, beforeUtc: Instant): InboundLeg? =
        if (useReal()) {
            real.fetchInboundLeg(icao24, departureAirportIata, beforeUtc)
        } else {
            fake.fetchInboundLeg(icao24, departureAirportIata, beforeUtc)
        }
}
