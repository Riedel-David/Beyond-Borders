package com.riedel.aeroglobe.data.flights

/**
 * Picks [real] or [fake] per call rather than at construction, so flipping the settings
 * toggle takes effect on the next poll instead of needing an app restart.
 */
class SwitchingFlightDataSource(
    private val useReal: () -> Boolean,
    private val real: FlightDataSource,
    private val fake: FlightDataSource,
) : FlightDataSource {
    override suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FlightStatus? =
        if (useReal()) real.fetchStatus(flightId, flightNumber, date) else fake.fetchStatus(flightId, flightNumber, date)
}
