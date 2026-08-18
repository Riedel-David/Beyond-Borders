package com.riedel.aeroglobe.data.flights

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.riedel.aeroglobe.settings.SettingsStore
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
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Auto-refresh was switched off since this chain was scheduled: end it quietly
        // rather than fight the setting. The settings screen's own toggle handles the
        // opposite case (re-arming) when it's switched back on.
        if (!settings.autoRefreshEnabled.value) return Result.success()

        val flightId = inputData.getString(KEY_FLIGHT_ID) ?: return Result.failure()
        val noStatusAttemptCount = inputData.getInt(KEY_NO_STATUS_ATTEMPTS, 0)
        val flight = flights.flights.first().find { it.id == flightId }
            ?: return Result.success() // Flight was removed, stop polling

        return try {
            val fetched = statuses.refresh(flightId, flight.flightNumber, flight.date)
            val current = fetched ?: statuses.observeStatus(flightId).first()

            val outcome = determineOutcome(
                current,
                Instant.now(),
                noStatusAttemptCount,
                statuses.lastFetchResults.value[flightId],
                settings.refreshCadence.value.multiplier,
            )
            when (outcome) {
                is PollOutcome.Reschedule -> {
                    scheduler.scheduleAfter(flightId, outcome.delayMillis, outcome.nextAttemptCount)
                    Result.success()
                }
                is PollOutcome.Done -> {
                    // Flight landed/cancelled, or gave up after max no-status attempts.
                    // Bug 3's launch re-arm is the recovery point, so giving up is safe.
                    Result.success()
                }
            }
        } catch (e: Exception) {
            // Transient failure: network issue, API timeout, etc. Cap retries to avoid
            // draining the API quota indefinitely. Bug 3's launch re-arm will pick it up
            // next time the app starts.
            if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                android.util.Log.e("FlightPollWorker", "Poll failed for $flightId after $runAttemptCount attempts, giving up", e)
                return Result.failure()
            }
            android.util.Log.w("FlightPollWorker", "Poll failed for $flightId (attempt $runAttemptCount), will retry", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_FLIGHT_ID = "flightId"
        const val KEY_NO_STATUS_ATTEMPTS = "noStatusAttempts"

        /** Cap on WorkManager retries after transient failures to avoid infinite quota drain. */
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}

/**
 * The outcome of a poll: either reschedule for another poll, or stop because the flight
 * is finished (landed/cancelled). [Reschedule] carries the delay for the next poll and
 * the attempt count to pass to the next request — reset to 0 on success, incremented on
 * no-status, so the backoff progression is testable and survives across the chain.
 */
internal sealed class PollOutcome {
    data class Reschedule(val delayMillis: Long, val nextAttemptCount: Int) : PollOutcome()
    data object Done : PollOutcome()
}

/**
 * Decides whether to keep polling based on what [FlightPollWorker.doWork] just saw and
 * how many no-status attempts this chain has made. A null status (nothing ever cached)
 * backs off over successive attempts to avoid draining the API quota: 5 min → 15 min → 1 h,
 * then gives up. Bug 3's app-launch re-arm is the natural recovery point, so giving up
 * here is safe — a user opening the app gets a fresh attempt. A successful fetch resets
 * the count to 0. A landed or cancelled flight stops immediately. Everything else computes
 * the next delay from [nextPollDelayMillis].
 *
 * [lastResult] exists for one case: a spent monthly quota, where continuing to poll cannot
 * succeed for days. Backing off is not enough there, so the chain ends and app launch becomes
 * the only retry.
 */
internal fun determineOutcome(
    status: FlightStatus?,
    now: Instant,
    noStatusAttemptCount: Int,
    lastResult: FetchResult? = null,
    cadenceMultiplier: Double = 1.0,
): PollOutcome {
    if (lastResult is FetchResult.RateLimited && !lastResult.transient) return PollOutcome.Done
    return if (status == null) {
        // Nothing cached yet. Back off over successive attempts: the API costs 2 units
        // per call against a 600/month budget, so a flat 5-minute retry burns the entire
        // month in ~25 hours. Give up after three attempts; launch re-arm recovers.
        when (noStatusAttemptCount) {
            0 -> PollOutcome.Reschedule(java.time.Duration.ofMinutes(5).toMillis(), nextAttemptCount = 1)
            1 -> PollOutcome.Reschedule(java.time.Duration.ofMinutes(15).toMillis(), nextAttemptCount = 2)
            2 -> PollOutcome.Reschedule(java.time.Duration.ofHours(1).toMillis(), nextAttemptCount = 3)
            else -> PollOutcome.Done
        }
    } else {
        // Successful fetch: reset the no-status count to 0
        val delay = nextPollDelayMillis(now, status.scheduledDepartureUtc, status.phase, cadenceMultiplier)
        if (delay == null) PollOutcome.Done else PollOutcome.Reschedule(delay, nextAttemptCount = 0)
    }
}
