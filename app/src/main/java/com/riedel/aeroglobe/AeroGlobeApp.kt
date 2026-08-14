package com.riedel.aeroglobe

import android.app.Application
import androidx.work.Configuration
import com.riedel.aeroglobe.data.flights.FlightPhase
import com.riedel.aeroglobe.data.flights.nextPollDelayMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

class AeroGlobeApp : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }

    // Application-scope coroutine for background initialization that shouldn't block
    // the main thread or tie to any particular screen's lifecycle.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Manual DI reaches the flight poll worker too: WorkManager needs its own factory
    // instead of the default one, which can only build zero-argument Workers.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.flightWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Re-arm polling for any tracked flights that don't have a worker running. If the
        // poll chain was broken (process death, upgrade, force-stop, a previous failure),
        // this repairs it. KEEP semantics in rearmIfNeeded ensure we never disturb a
        // healthy chain. Computes the correct delay from cached status to avoid burning
        // API quota on repeated app launches. Runs in the background so it doesn't block
        // app startup on Room or the network.
        appScope.launch {
            val flights = container.flights.flights.first()
            val now = Instant.now()

            flights.forEach { flight ->
                val status = container.flightStatuses.observeStatus(flight.id).first()
                val isFinished = status?.phase?.let { phase ->
                    phase == FlightPhase.LANDED || phase == FlightPhase.CANCELLED
                } ?: false

                if (!isFinished) {
                    val delay = if (status == null) {
                        // No cached status yet, start with the first attempt delay
                        java.time.Duration.ofMinutes(5).toMillis()
                    } else {
                        nextPollDelayMillis(now, status.scheduledDepartureUtc, status.phase) ?: 0L
                    }
                    container.flightPollScheduler.rearmIfNeeded(flight.id, delay)
                }
            }
        }
    }
}
