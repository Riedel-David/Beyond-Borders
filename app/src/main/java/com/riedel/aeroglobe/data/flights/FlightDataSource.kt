package com.riedel.aeroglobe.data.flights

import java.time.Instant

/**
 * A source of live flight status. Real providers arrive in 3d — this interface is the
 * seam that lets 3b-3c build and test against [FakeFlightDataSource] with no API key.
 */
interface FlightDataSource {
    /**
     * Fetches status, returning a [FetchResult] that distinguishes success from the
     * various failure modes. The repository treats failures as "no signal" and keeps
     * whatever it last cached — a flight must survive a dead connection, not disappear.
     */
    suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FetchResult

    /**
     * The tracked aircraft's leg into [departureAirportIata] immediately before [beforeUtc].
     * Called once per flight, right when [FlightStatus.aircraftIcao24] first becomes known —
     * not polled, since a completed inbound leg's schedule rarely changes afterwards. Returns
     * null on any failure or when no matching leg is found, same "no signal" convention as
     * before.
     */
    suspend fun fetchInboundLeg(icao24: String, departureAirportIata: String, beforeUtc: Instant): InboundLeg?
}

/**
 * The outcome of a [FlightDataSource.fetchStatus] call. Success carries the status;
 * failures distinguish the reason so the UI and logs can be specific instead of
 * showing "null" for everything.
 */
sealed interface FetchResult {
    data class Success(val status: FlightStatus) : FetchResult
    data object NotFound : FetchResult
    data class HttpError(val code: Int, val message: String) : FetchResult
    data object NoKey : FetchResult
    data object Offline : FetchResult
    data class ParseError(val reason: String) : FetchResult

    /**
     * HTTP 429, split out from [HttpError] because the two kinds need opposite responses.
     * [transient] is the per-second cap — clears on its own, so retrying is right. The other
     * kind is the monthly plan quota being spent, where every further call is refused for
     * days and retrying only wastes battery.
     */
    data class RateLimited(val transient: Boolean, val message: String) : FetchResult
}
