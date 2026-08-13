package com.riedel.aeroglobe.data.flights

import com.riedel.aeroglobe.core.Uuid
import com.riedel.aeroglobe.data.db.FlightDao
import com.riedel.aeroglobe.data.db.FlightEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Tracked flights, local only — nothing here has ever made a network request. */
class FlightRepository(
    private val dao: FlightDao,
    private val deviceId: String,
    private val scheduler: FlightPollScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [FlightEntity.flightNumber] is already normalized by the caller. */
    val flights: Flow<List<FlightEntity>> = dao.observeActive()

    suspend fun add(flightNumber: String, isoDate: String) = withContext(Dispatchers.IO) {
        val now = clock()
        val id = Uuid.v7(now)
        dao.insert(
            FlightEntity(
                id = id,
                flightNumber = flightNumber,
                date = isoDate,
                updatedAt = now,
                deviceId = deviceId,
            )
        )
        scheduler.scheduleNow(id)
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        dao.tombstone(id, clock())
        scheduler.cancel(id)
    }
}
