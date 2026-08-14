package com.riedel.aeroglobe.data.flights

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Real flight status from AeroDataBox (RapidAPI, "Flights by number" endpoint).
 *
 * With no key configured, [fetchStatus] returns [FetchResult.NoKey] — nothing else has
 * to know a key is missing.
 *
 * Field names in [parseAeroDataBoxResponse] are verified against a live response
 * (DY 2003, 2026-08-13): "utc" timestamps come back as "yyyy-MM-dd HH:mmZ", not
 * ISO-8601, and the aircraft's Mode-S/ICAO24 address is at `aircraft.modeS`. Arrival,
 * airline and aircraft-model fields are verified against the same OpenAPI spec
 * (`arrival.airport.iata`, `arrival.scheduledTime.utc`/`revisedTime.utc`,
 * `arrival.runwayTime.utc`, `airline.name`/`iata`/`icao`, `aircraft.model`,
 * `departure.runwayTime.utc`).
 *
 * Every request passes through [awaitRequestSlot] first. The Basic plan allows one request
 * per second, and the app has several callers that naturally fire together — pull-to-refresh
 * fans out over every tracked flight, and the launch re-arm enqueues one worker per flight —
 * so without a gate a two-flight app reliably 429s on half its own requests. This class is a
 * singleton in `AppContainer`, which is what makes one mutex enough to cover all of them.
 */
class AeroDataBoxFlightDataSource(
    private val apiKey: () -> String?,
    private val onRequestMade: () -> Unit = {},
    private val onQuotaRemaining: (Int) -> Unit = {},
) : FlightDataSource {

    private val requestGate = Mutex()
    private var lastRequestAtMillis = 0L

    override suspend fun fetchStatus(flightId: String, flightNumber: String, date: String): FetchResult =
        withContext(Dispatchers.IO) {
            val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() }
            if (key == null) {
                Log.i(TAG, "No API key configured")
                return@withContext FetchResult.NoKey
            }
            awaitRequestSlot()
            fetch(key, flightId, flightNumber, date)
        }

    /**
     * Blocks until this caller may make a request. The wait happens *inside* the lock, which
     * is the point: N callers arriving at once leave one per interval rather than all at once.
     * `elapsedRealtime` rather than wall-clock time so a clock correction or timezone change
     * mid-flight can't hand out a slot early or stall one for hours.
     */
    private suspend fun awaitRequestSlot() {
        requestGate.withLock {
            val wait = millisUntilNextRequest(lastRequestAtMillis, SystemClock.elapsedRealtime(), MIN_REQUEST_INTERVAL_MILLIS)
            if (wait > 0) {
                Log.i(TAG, "Throttled ${wait}ms to stay under the plan's 1 req/s limit")
                delay(wait)
            }
            lastRequestAtMillis = SystemClock.elapsedRealtime()
        }
    }

    private fun fetch(key: String, flightId: String, flightNumber: String, date: String): FetchResult {
        val encodedNumber = flightNumber.replace(" ", "")
        val url = "https://$HOST/flights/number/$encodedNumber/$date"
        Log.i(TAG, "Fetching $url (key redacted)")

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Network unavailable: ${e.message}")
            return FetchResult.Offline
        } catch (e: IOException) {
            Log.w(TAG, "Connection failed: ${e.message}")
            return FetchResult.Offline
        }

        connection.setRequestProperty("X-RapidAPI-Key", key)
        connection.setRequestProperty("X-RapidAPI-Host", HOST)
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS

        return try {
            val responseCode = connection.responseCode
            Log.i(TAG, "Response code: $responseCode")

            // RapidAPI stamps this on every response, refused or served, so it is read
            // unconditionally — the provider's own count is always more trustworthy than
            // ours, which only ever counted requests, not the 2-unit-per-call this endpoint
            // actually bills.
            parseQuotaRemaining(connection.getHeaderField(QUOTA_REMAINING_HEADER))?.let(onQuotaRemaining)

            // Counted here rather than before the call: a request the gateway refused outright
            // isn't one the provider served, and counting it makes the settings figure drift
            // further from the truth every time we trip a limit.
            if (responseCode != HTTP_TOO_MANY_REQUESTS) onRequestMade()

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().readText()
                    parseAeroDataBoxResponse(flightId, body, Instant.now())
                }
                HttpURLConnection.HTTP_NO_CONTENT -> {
                    Log.i(TAG, "No flights found (204)")
                    FetchResult.NotFound
                }
                else -> {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText()
                    } catch (e: Exception) {
                        null
                    }
                    val message = extractErrorMessage(errorBody, responseCode)
                    if (responseCode == HTTP_TOO_MANY_REQUESTS) {
                        val transient = isPerSecondRateLimit(message)
                        Log.w(TAG, "HTTP 429 (${if (transient) "per-second" else "plan quota"}): $message")
                        FetchResult.RateLimited(transient, message)
                    } else {
                        Log.w(TAG, "HTTP $responseCode: $message")
                        FetchResult.HttpError(responseCode, message)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Request timed out")
            FetchResult.Offline
        } catch (e: IOException) {
            Log.w(TAG, "I/O error: ${e.message}")
            FetchResult.Offline
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HOST = "aerodatabox.p.rapidapi.com"
        const val TIMEOUT_MILLIS = 10_000
        const val QUOTA_REMAINING_HEADER = "x-ratelimit-requests-remaining"

        /**
         * 1.1s, not 1.0s: the plan's limit is one request per second measured at the provider,
         * and the round-trip means our idea of when the last one left is always a little early.
         * The 100ms is cheap — the tightest poll cadence is five minutes.
         */
        const val MIN_REQUEST_INTERVAL_MILLIS = 1_100L

        /** `HttpURLConnection` predates RFC 6585 and has no constant for it. */
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

/**
 * File-level rather than in the companion so the parsing functions below — which are
 * top-level, to stay unit-testable without Android — log under the same tag as the class.
 * `adb logcat -s AeroDataBox:*` is meant to show the whole request, response and parse
 * story for one lookup in one stream.
 */
private const val TAG = "AeroDataBox"

/**
 * Extracts a human-readable error message from the response body. Tries to parse it as
 * AeroDataBox's ErrorContract ({"message": "...", "details": "..."}), falls back to the
 * raw body (truncated), then to a generic message for the response code.
 */
private fun extractErrorMessage(errorBody: String?, responseCode: Int): String {
    if (errorBody.isNullOrBlank()) {
        return when (responseCode) {
            401 -> "Invalid API key"
            403 -> "You are not subscribed to this endpoint on RapidAPI"
            429 -> "Rate limit exceeded"
            451 -> "Unavailable for legal reasons"
            500 -> "Server error"
            503 -> "Service temporarily unavailable"
            else -> "HTTP $responseCode"
        }
    }

    // Try parsing as AeroDataBox ErrorContract
    return try {
        val json = JSONObject(errorBody)
        val message = json.optString("message", "")
        val details = json.optString("details", "")
        if (message.isNotBlank()) {
            if (details.isNotBlank()) "$message — $details" else message
        } else {
            errorBody.take(200)
        }
    } catch (e: JSONException) {
        // Not JSON, return raw body truncated
        errorBody.take(200)
    }
}

// --- pure functions, unit-testable without Android or a network ---------------

/**
 * How long a caller must wait before its request may leave, given when the last one did.
 * Zero when nothing has been sent yet, and zero if the elapsed-time source appears to have
 * gone backwards — a negative wait would be nonsense, and stalling forever is worse than
 * risking one 429.
 */
internal fun millisUntilNextRequest(lastRequestAtMillis: Long, nowMillis: Long, minIntervalMillis: Long): Long {
    if (lastRequestAtMillis <= 0L) return 0L
    val elapsed = nowMillis - lastRequestAtMillis
    if (elapsed < 0L) return 0L
    return (minIntervalMillis - elapsed).coerceAtLeast(0L)
}

/**
 * Whether a 429 is the per-second cap (wait a moment and it clears) or the monthly plan
 * quota (nothing will work until the plan resets). RapidAPI and AeroDataBox both phrase the
 * first as a rate limit "per second" and the second as a "monthly quota", but neither is a
 * documented contract, so an unrecognised 429 is treated as the expensive kind: assuming
 * quota exhaustion costs a delayed update, assuming per-second costs the rest of the month.
 */
internal fun isPerSecondRateLimit(message: String): Boolean {
    val normalized = message.lowercase()
    return normalized.contains("per second") || normalized.contains("per-second")
}

/** RapidAPI's header value, or null if the header was absent or not a plain integer. */
internal fun parseQuotaRemaining(headerValue: String?): Int? = headerValue?.trim()?.toIntOrNull()

internal fun parseAeroDataBoxResponse(flightId: String, body: String, now: Instant): FetchResult {
    val flights = try {
        JSONArray(body)
    } catch (e: JSONException) {
        return FetchResult.ParseError("Invalid JSON: ${e.message}")
    }

    if (flights.length() == 0) {
        return FetchResult.NotFound
    }

    val flight = try {
        flights.getJSONObject(0)
    } catch (e: JSONException) {
        return FetchResult.ParseError("Malformed flight object: ${e.message}")
    }

    val departure = flight.optJSONObject("departure")
        ?: return FetchResult.ParseError("Missing departure object")

    val scheduled = departure.utcInstant("scheduledTime")
        ?: return FetchResult.ParseError("Missing scheduledTime.utc")

    val statusString = flight.optString("status")
    // A value AeroDataBox has added since this was written still yields a usable flight,
    // but it should not do so silently — an unlogged default is how a new "Delayed"-style
    // state would quietly read as on-time forever.
    val mapped = mapAeroDataBoxStatusOrNull(statusString)
    if (mapped == null && statusString.isNotBlank()) {
        Log.w(TAG, "Unknown status value: '$statusString' — defaulting to SCHEDULED")
    }
    val phase = mapped ?: FlightPhase.SCHEDULED

    val arrival = flight.optJSONObject("arrival")
    val airline = flight.optJSONObject("airline")
    val aircraft = flight.optJSONObject("aircraft")

    val status = FlightStatus(
        flightId = flightId,
        scheduledDepartureUtc = scheduled,
        estimatedDepartureUtc = departure.utcInstant("revisedTime") ?: departure.utcInstant("predictedTime"),
        departureGate = departure.optNonBlankString("gate"),
        departureTerminal = departure.optNonBlankString("terminal"),
        phase = phase,
        fetchedAt = now,
        aircraftIcao24 = aircraft?.optNonBlankString("modeS")?.lowercase(),
        departureAirportIata = departure.optJSONObject("airport")?.optNonBlankString("iata"),
        actualDepartureUtc = departure.utcInstant("runwayTime"),
        arrivalAirportIata = arrival?.optJSONObject("airport")?.optNonBlankString("iata"),
        scheduledArrivalUtc = arrival?.utcInstant("scheduledTime"),
        estimatedArrivalUtc = arrival?.utcInstant("revisedTime") ?: arrival?.utcInstant("predictedTime"),
        actualArrivalUtc = arrival?.utcInstant("runwayTime"),
        airlineName = airline?.optNonBlankString("name"),
        airlineIata = airline?.optNonBlankString("iata"),
        airlineIcao = airline?.optNonBlankString("icao"),
        aircraftModel = aircraft?.optNonBlankString("model"),
    )

    return FetchResult.Success(status)
}

private fun JSONObject.utcInstant(key: String): Instant? =
    optJSONObject(key)?.optNonBlankString("utc")?.let { raw ->
        parseAeroDataBoxUtc(raw)
    }

/**
 * Parses AeroDataBox's "utc" timestamp strings. Verified against live responses as
 * "yyyy-MM-dd HH:mmZ", but accepts both that and full ISO-8601 with offset since the
 * OpenAPI spec declares `format: date-time` which normally means ISO-8601.
 */
internal fun parseAeroDataBoxUtc(raw: String): Instant? {
    // Strip trailing Z if present
    val withoutZ = raw.removeSuffix("Z").trim()

    // Try each format in order of likelihood
    return tryParse(withoutZ, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        ?: tryParse(withoutZ, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ?: tryParse(raw, DateTimeFormatter.ISO_DATE_TIME)
        ?: tryParse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        ?: null
}

private fun tryParse(text: String, formatter: DateTimeFormatter): Instant? =
    try {
        if (formatter == DateTimeFormatter.ISO_DATE_TIME || formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
            // These parse directly to Instant or need ZonedDateTime
            try {
                Instant.from(formatter.parse(text))
            } catch (e: DateTimeParseException) {
                // Might be a local datetime with offset
                java.time.ZonedDateTime.parse(text, formatter).toInstant()
            }
        } else {
            // Custom patterns parse as LocalDateTime and assume UTC
            LocalDateTime.parse(text, formatter).toInstant(ZoneOffset.UTC)
        }
    } catch (e: DateTimeParseException) {
        null
    }

private fun JSONObject.optNonBlankString(key: String): String? = optString(key).takeIf { it.isNotBlank() }

internal fun mapAeroDataBoxStatus(status: String): FlightPhase =
    mapAeroDataBoxStatusOrNull(status) ?: FlightPhase.SCHEDULED

/**
 * The same mapping, but null rather than a default for a value AeroDataBox has not
 * documented. Two functions rather than one so "is this value known?" and "what phase is
 * it?" cannot drift apart: the caller logs the unknown ones, and there is no second list
 * of status strings to keep in sync with this `when`.
 */
internal fun mapAeroDataBoxStatusOrNull(status: String): FlightPhase? {
    val normalized = status.trim().lowercase()
    return when (normalized) {
        "unknown", "expected", "checkin" -> FlightPhase.SCHEDULED
        "boarding", "gateclosed" -> FlightPhase.BOARDING
        "departed" -> FlightPhase.DEPARTED
        // "approaching" is still airborne, near the destination — not on the ground.
        // Calling it LANDED would make nextPollDelayMillis return null and end the poll
        // chain minutes before touchdown, so the one state change worth catching is the
        // one we would miss.
        "enroute", "diverted", "approaching" -> FlightPhase.IN_AIR
        "arrived", "landed" -> FlightPhase.LANDED
        "canceled", "cancelled", "canceleduncertain" -> FlightPhase.CANCELLED
        "delayed" -> FlightPhase.SCHEDULED  // Delay is derived from times, not a distinct phase
        else -> null
    }
}
