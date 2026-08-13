package com.riedel.aeroglobe.data.flights

import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real flight status from AeroDataBox (RapidAPI, "Flights by number" endpoint).
 *
 * Scaffolded ahead of a key per the 3d decision: with no key configured, [fetchStatus]
 * returns null exactly like a dead connection, so the cache-first repository behaves
 * against this the same way it does against [FakeFlightDataSource] with no signal —
 * nothing else has to know a key is missing.
 *
 * The JSON field names in [parseAeroDataBoxResponse] match AeroDataBox's documented
 * shape at integration time, but have never been checked against a live response —
 * there is no key yet to call with. Verify field names against a real response before
 * relying on this against actual flights.
 */
class AeroDataBoxFlightDataSource(
    private val apiKey: () -> String?,
    private val onRequestMade: () -> Unit = {},
) : FlightDataSource {

    override suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FlightStatus? =
        withContext(Dispatchers.IO) {
            val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
            runCatching { fetch(key, flightId, flightNumber, date) }.getOrNull()
        }

    private fun fetch(key: String, flightId: String, flightNumber: String, date: String): FlightStatus? {
        val encodedNumber = flightNumber.replace(" ", "")
        val url = URL("https://$HOST/flights/number/$encodedNumber/$date")
        val connection = url.openConnection() as HttpURLConnection
        onRequestMade()
        connection.setRequestProperty("X-RapidAPI-Key", key)
        connection.setRequestProperty("X-RapidAPI-Host", HOST)
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().readText()
            parseAeroDataBoxResponse(flightId, body, Instant.now())
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HOST = "aerodatabox.p.rapidapi.com"
        const val TIMEOUT_MILLIS = 10_000
    }
}

// --- pure functions, unit-testable without Android or a network ---------------

internal fun parseAeroDataBoxResponse(flightId: String, body: String, now: Instant): FlightStatus? {
    val flights = JSONArray(body)
    if (flights.length() == 0) return null
    val flight = flights.getJSONObject(0)
    val departure = flight.optJSONObject("departure") ?: return null
    val scheduled = departure.utcInstant("scheduledTime") ?: return null
    return FlightStatus(
        flightId = flightId,
        scheduledDepartureUtc = scheduled,
        estimatedDepartureUtc = departure.utcInstant("revisedTime") ?: departure.utcInstant("predictedTime"),
        departureGate = departure.optNonBlankString("gate"),
        departureTerminal = departure.optNonBlankString("terminal"),
        phase = mapAeroDataBoxStatus(flight.optString("status")),
        fetchedAt = now,
    )
}

private fun JSONObject.utcInstant(key: String): Instant? =
    optJSONObject(key)?.optNonBlankString("utc")?.let(Instant::parse)

private fun JSONObject.optNonBlankString(key: String): String? = optString(key).takeIf { it.isNotBlank() }

internal fun mapAeroDataBoxStatus(status: String): FlightPhase = when (status.trim().lowercase()) {
    "expected", "checkin" -> FlightPhase.SCHEDULED
    "boarding", "gateclosed" -> FlightPhase.BOARDING
    "departed" -> FlightPhase.DEPARTED
    "enroute", "diverted" -> FlightPhase.IN_AIR
    "arrived", "landed" -> FlightPhase.LANDED
    "canceled", "cancelled" -> FlightPhase.CANCELLED
    else -> FlightPhase.SCHEDULED
}
