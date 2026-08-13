package com.riedel.aeroglobe.data.flights

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Fetches one flight's status, then re-schedules itself at the cadence
 * [nextPollDelayMillis] computes for what it just saw. A flight that was removed, or
 * whose fetch fails with nothing ever cached, ends the chain rather than spinning.
 */
class FlightPollWorker(
    context: Context,
    params: WorkerParameters,
    private val flights: FlightRepository,
    private val statuses: FlightStatusRepository,
    private val scheduler: FlightPollScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val flightId = inputData.getString(KEY_FLIGHT_ID) ?: return Result.failure()
        val flight = flights.flights.first().find { it.id == flightId } ?: return Result.success()

        val fetched = statuses.refresh(flightId, flight.flightNumber, flight.date)
        val current = fetched ?: statuses.observeStatus(flightId).first()

        val delay = if (current == null) {
            RETRY_WITH_NO_STATUS_MILLIS
        } else {
            nextPollDelayMillis(Instant.now(), current.scheduledDepartureUtc, current.phase)
        }
        if (delay != null) scheduler.scheduleAfter(flightId, delay)
        return Result.success()
    }

    companion object {
        const val KEY_FLIGHT_ID = "flightId"

        /** Nothing has ever been cached for this flight yet — try again soon rather than give up. */
        private val RETRY_WITH_NO_STATUS_MILLIS = java.time.Duration.ofMinutes(5).toMillis()
    }
}
