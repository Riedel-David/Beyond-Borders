package com.riedel.aeroglobe.data.flights

import com.riedel.aeroglobe.core.Uuid
import com.riedel.aeroglobe.data.db.FlightStatusChangeDao
import com.riedel.aeroglobe.data.db.FlightStatusChangeEntity
import com.riedel.aeroglobe.data.db.FlightStatusDao
import com.riedel.aeroglobe.data.db.FlightStatusEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Cache-first: the UI always reads [observeStatus] from Room, never the data source
 * directly, so a dead connection shows the last known gate instead of a blank screen.
 */
class FlightStatusRepository(
    private val statusDao: FlightStatusDao,
    private val changeDao: FlightStatusChangeDao,
    private val dataSource: FlightDataSource,
    private val deviceId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _lastFetchResults = MutableStateFlow<Map<String, FetchResult>>(emptyMap())

    fun observeStatus(flightId: String): Flow<FlightStatus?> =
        statusDao.observe(flightId).map { it?.toDomain() }

    fun observeChanges(flightId: String): Flow<List<StatusChange>> =
        changeDao.observe(flightId).map { rows -> rows.map { it.toDomain() } }

    /**
     * The most recent fetch result per flight ID, absent until one has been attempted.
     *
     * Exposed as the whole map rather than a per-flight flow because a `lastFetchResult(id)`
     * accessor has to build its flow somewhere, and building it per call handed the UI a fresh
     * single-value flow that could never emit the *next* result. That failed silently in the
     * one case it exists for: a fetch that fails leaves the cached status untouched, so
     * nothing else recomposes, so the reason for the failure never appeared on screen.
     */
    val lastFetchResults: StateFlow<Map<String, FetchResult>> = _lastFetchResults.asStateFlow()

    /** Fetches, diffs against the cache, and writes both the snapshot and any change log rows. */
    suspend fun refresh(flightId: String, flightNumber: String, date: String): FlightStatus? =
        withContext(Dispatchers.IO) {
            val result = dataSource.fetchStatus(flightId, flightNumber, date)

            // Always record the result so the UI can show what happened
            _lastFetchResults.value = _lastFetchResults.value + (flightId to result)

            when (result) {
                is FetchResult.Success -> {
                    val fetched = result.status
                    val previous = statusDao.get(flightId)?.toDomain()
                    val now = clock()
                    diffStatus(previous, fetched).forEach { change ->
                        changeDao.insert(
                            FlightStatusChangeEntity(
                                id = Uuid.v7(now),
                                flightId = flightId,
                                field = change.field,
                                oldValue = change.oldValue,
                                newValue = change.newValue,
                                changedAt = now,
                                updatedAt = now,
                                deviceId = deviceId,
                            )
                        )
                    }
                    statusDao.upsert(fetched.toEntity(deviceId, now))
                    fetched
                }
                else -> {
                    // Failure: keep the cached status and return null
                    null
                }
            }
        }

    private fun FlightStatusEntity.toDomain() = FlightStatus(
        flightId = flightId,
        scheduledDepartureUtc = Instant.ofEpochMilli(scheduledDepartureUtc),
        estimatedDepartureUtc = estimatedDepartureUtc?.let(Instant::ofEpochMilli),
        departureGate = departureGate,
        departureTerminal = departureTerminal,
        phase = FlightPhase.valueOf(phase),
        fetchedAt = Instant.ofEpochMilli(fetchedAt),
        aircraftIcao24 = aircraftIcao24,
        departureAirportIata = departureAirportIata,
        actualDepartureUtc = actualDepartureUtc?.let(Instant::ofEpochMilli),
        arrivalAirportIata = arrivalAirportIata,
        scheduledArrivalUtc = scheduledArrivalUtc?.let(Instant::ofEpochMilli),
        estimatedArrivalUtc = estimatedArrivalUtc?.let(Instant::ofEpochMilli),
        actualArrivalUtc = actualArrivalUtc?.let(Instant::ofEpochMilli),
        airlineName = airlineName,
        airlineIata = airlineIata,
        airlineIcao = airlineIcao,
        aircraftModel = aircraftModel,
        departureCity = departureCity,
        arrivalCity = arrivalCity,
        departureLat = departureLat,
        departureLon = departureLon,
        arrivalLat = arrivalLat,
        arrivalLon = arrivalLon,
        departureTimeZone = departureTimeZone,
        arrivalTimeZone = arrivalTimeZone,
        providerUpdatedAt = providerUpdatedAt?.let(Instant::ofEpochMilli),
        aircraftRegistration = aircraftRegistration,
        isCodeshare = isCodeshare,
        arrivalGate = arrivalGate,
        arrivalTerminal = arrivalTerminal,
        arrivalBaggageBelt = arrivalBaggageBelt,
        hasLiveTracking = hasLiveTracking,
    )

    private fun FlightStatusChangeEntity.toDomain() = StatusChange(
        field = field,
        oldValue = oldValue,
        newValue = newValue,
        changedAt = Instant.ofEpochMilli(changedAt),
    )

    private fun FlightStatus.toEntity(deviceId: String, updatedAt: Long) = FlightStatusEntity(
        flightId = flightId,
        scheduledDepartureUtc = scheduledDepartureUtc.toEpochMilli(),
        estimatedDepartureUtc = estimatedDepartureUtc?.toEpochMilli(),
        departureGate = departureGate,
        departureTerminal = departureTerminal,
        phase = phase.name,
        fetchedAt = fetchedAt.toEpochMilli(),
        updatedAt = updatedAt,
        deviceId = deviceId,
        aircraftIcao24 = aircraftIcao24,
        departureAirportIata = departureAirportIata,
        actualDepartureUtc = actualDepartureUtc?.toEpochMilli(),
        arrivalAirportIata = arrivalAirportIata,
        scheduledArrivalUtc = scheduledArrivalUtc?.toEpochMilli(),
        estimatedArrivalUtc = estimatedArrivalUtc?.toEpochMilli(),
        actualArrivalUtc = actualArrivalUtc?.toEpochMilli(),
        airlineName = airlineName,
        airlineIata = airlineIata,
        airlineIcao = airlineIcao,
        aircraftModel = aircraftModel,
        departureCity = departureCity,
        arrivalCity = arrivalCity,
        departureLat = departureLat,
        departureLon = departureLon,
        arrivalLat = arrivalLat,
        arrivalLon = arrivalLon,
        departureTimeZone = departureTimeZone,
        arrivalTimeZone = arrivalTimeZone,
        providerUpdatedAt = providerUpdatedAt?.toEpochMilli(),
        aircraftRegistration = aircraftRegistration,
        isCodeshare = isCodeshare,
        arrivalGate = arrivalGate,
        arrivalTerminal = arrivalTerminal,
        arrivalBaggageBelt = arrivalBaggageBelt,
        hasLiveTracking = hasLiveTracking,
    )
}

// --- pure functions, unit-testable without Android ---------------------------

/** A first-ever sighting isn't a "change" — there is nothing to compare it against. */
internal fun diffStatus(previous: FlightStatus?, current: FlightStatus): List<StatusChange> {
    if (previous == null) return emptyList()
    val changes = mutableListOf<StatusChange>()
    if (previous.departureGate != current.departureGate) {
        changes += StatusChange("gate", previous.departureGate, current.departureGate, current.fetchedAt)
    }
    if (previous.departureTerminal != current.departureTerminal) {
        changes += StatusChange("terminal", previous.departureTerminal, current.departureTerminal, current.fetchedAt)
    }
    if (previous.phase != current.phase) {
        changes += StatusChange("phase", previous.phase.name, current.phase.name, current.fetchedAt)
    }
    return changes
}
