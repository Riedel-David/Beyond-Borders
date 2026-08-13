package com.riedel.aeroglobe.data.flights

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * One chained `OneTimeWorkRequest` per tracked flight, not `PeriodicWorkRequest`: the
 * adaptive cadence in [nextPollDelayMillis] goes down to 60 seconds, well under
 * WorkManager's 15-minute periodic floor. [FlightPollWorker] re-schedules itself on
 * every run with the next delay it computes.
 */
class FlightPollScheduler(private val context: Context) {

    fun scheduleNow(flightId: String) = enqueue(flightId, 0L)

    fun scheduleAfter(flightId: String, delayMillis: Long) = enqueue(flightId, delayMillis)

    fun cancel(flightId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(flightId))
    }

    private fun enqueue(flightId: String, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<FlightPollWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(FlightPollWorker.KEY_FLIGHT_ID to flightId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueWorkName(flightId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueWorkName(flightId: String) = "flight-poll-$flightId"
}
