package com.riedel.aeroglobe.data.flights

import com.riedel.aeroglobe.core.Uuid
import com.riedel.aeroglobe.data.db.FlightStatusChangeDao
import com.riedel.aeroglobe.data.db.FlightStatusChangeEntity
import com.riedel.aeroglobe.data.db.FlightStatusDao
import com.riedel.aeroglobe.data.db.FlightStatusEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    fun observeStatus(flightId: String): Flow<FlightStatus?> =
        statusDao.observe(flightId).map { it?.toDomain() }

    fun observeChanges(flightId: String): Flow<List<StatusChange>> =
        changeDao.observe(flightId).map { rows -> rows.map { it.toDomain() } }

    /** Fetches, diffs against the cache, and writes both the snapshot and any change log rows. */
    suspend fun refresh(flightId: String, flightNumber: String, date: String): FlightStatus? =
        withContext(Dispatchers.IO) {
            val fetched = dataSource.fetchStatus(flightId, flightNumber, date) ?: return@withContext null
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

    private fun FlightStatusEntity.toDomain() = FlightStatus(
        flightId = flightId,
        scheduledDepartureUtc = Instant.ofEpochMilli(scheduledDepartureUtc),
        estimatedDepartureUtc = estimatedDepartureUtc?.let(Instant::ofEpochMilli),
        departureGate = departureGate,
        departureTerminal = departureTerminal,
        phase = FlightPhase.valueOf(phase),
        fetchedAt = Instant.ofEpochMilli(fetchedAt),
        aircraftIcao24 = aircraftIcao24,
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
