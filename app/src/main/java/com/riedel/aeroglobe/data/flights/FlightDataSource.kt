package com.riedel.aeroglobe.data.flights

/**
 * A source of live flight status. Real providers arrive in 3d — this interface is the
 * seam that lets 3b-3c build and test against [FakeFlightDataSource] with no API key.
 */
interface FlightDataSource {
    /**
     * Returns null on any failure (no network, provider error, unknown flight). The
     * repository treats null as "no signal" and keeps whatever it last cached — a
     * flight must survive a dead connection, not disappear.
     */
    suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FlightStatus?
}
