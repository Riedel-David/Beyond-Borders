package com.riedel.aeroglobe.data.flights

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.riedel.aeroglobe.settings.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * One chained `OneTimeWorkRequest` per tracked flight, not `PeriodicWorkRequest`: the
 * adaptive cadence in [nextPollDelayMillis] goes down to 5 minutes, well under
 * WorkManager's 15-minute periodic floor. [FlightPollWorker] re-schedules itself on
 * every run with the next delay it computes.
 */
class FlightPollScheduler(private val context: Context, private val settings: SettingsStore) {

    /**
     * Schedules an immediate poll, replacing any existing work. Called when the user adds
     * a new flight — if something is already running for this flight ID (stale chain, or
     * user deleted and re-added), we want the new request to take over. Starts with a
     * fresh no-status attempt count of 0.
     */
    fun scheduleNow(flightId: String) = enqueue(flightId, 0L, noStatusAttemptCount = 0, ExistingWorkPolicy.REPLACE)

    /**
     * Schedules the next poll in the chain. Called from inside [FlightPollWorker.doWork],
     * which is the currently-executing work under `flight-poll-$flightId`. REPLACE is
     * required here: it cancels the running worker (harmless, since it has already enqueued
     * its successor) and keeps the new request. KEEP would silently drop the new request
     * because work with that name is RUNNING, killing the chain after exactly one poll.
     * Carries the no-status attempt count forward through the chain.
     */
    fun scheduleAfter(flightId: String, delayMillis: Long, noStatusAttemptCount: Int) =
        enqueue(flightId, delayMillis, noStatusAttemptCount, ExistingWorkPolicy.REPLACE)

    /**
     * Re-arms polling for a flight if no work is already scheduled. Used on app start to
     * repair broken chains (process death, upgrade, force-stop, a previous failure that
     * killed the worker). KEEP semantics: if a healthy chain is already running, leave it
     * alone. Computes the correct delay from cached status to avoid burning API quota on
     * repeated app launches. Starts with a fresh no-status attempt count of 0.
     */
    fun rearmIfNeeded(flightId: String, delayMillis: Long) =
        enqueue(flightId, delayMillis, noStatusAttemptCount = 0, ExistingWorkPolicy.KEEP)

    fun cancel(flightId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(flightId))
    }

    private fun enqueue(flightId: String, delayMillis: Long, noStatusAttemptCount: Int, policy: ExistingWorkPolicy) {
        // Covers all three callers in one place, rather than each of scheduleNow/
        // scheduleAfter/rearmIfNeeded checking the setting itself.
        if (!settings.autoRefreshEnabled.value) {
            cancel(flightId)
            return
        }
        val request = OneTimeWorkRequestBuilder<FlightPollWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                FlightPollWorker.KEY_FLIGHT_ID to flightId,
                FlightPollWorker.KEY_NO_STATUS_ATTEMPTS to noStatusAttemptCount
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueWorkName(flightId), policy, request)
    }

    private fun uniqueWorkName(flightId: String) = "flight-poll-$flightId"
}
