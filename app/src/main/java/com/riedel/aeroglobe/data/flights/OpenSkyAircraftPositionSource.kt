package com.riedel.aeroglobe.data.flights

import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Live position from OpenSky Network's public state-vector API, used fully anonymously
 * — no key, no settings entry, matching the 3e decision that this stays a hero-screen-
 * only, foreground feature rather than a tracked/quota'd credential like 3d's.
 *
 * The state-vector shape below is verified against a live call (icao24 47a05e,
 * 2026-08-13): a 17-element array per aircraft, index 0 icao24 through 16 position_source.
 */
class OpenSkyAircraftPositionSource : AircraftPositionSource {

    override suspend fun fetchPosition(icao24: String): AircraftPosition? =
        withContext(Dispatchers.IO) {
            runCatching { fetch(icao24) }.getOrNull()
        }

    private fun fetch(icao24: String): AircraftPosition? {
        val url = URL("https://$HOST/api/states/all?icao24=${icao24.lowercase()}")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().readText()
            parseOpenSkyStates(body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HOST = "opensky-network.org"
        const val TIMEOUT_MILLIS = 10_000
    }
}

// --- pure functions, unit-testable without Android or a network ---------------

internal fun parseOpenSkyStates(body: String): AircraftPosition? {
    val states = JSONObject(body).optJSONArray("states") ?: return null
    if (states.length() == 0) return null
    val state = states.getJSONArray(0)
    val latitude = state.optDoubleOrNull(6) ?: return null
    val longitude = state.optDoubleOrNull(5) ?: return null
    val lastContact = state.optLongOrNull(4) ?: return null
    return AircraftPosition(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = state.optDoubleOrNull(7) ?: state.optDoubleOrNull(13),
        velocityMs = state.optDoubleOrNull(9),
        headingDegrees = state.optDoubleOrNull(10),
        onGround = state.optBoolean(8),
        observedAt = Instant.ofEpochSecond(lastContact),
    )
}

private fun JSONArray.optDoubleOrNull(index: Int): Double? = if (isNull(index)) null else optDouble(index)

private fun JSONArray.optLongOrNull(index: Int): Long? = if (isNull(index)) null else optLong(index)
