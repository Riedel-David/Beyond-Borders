package com.riedel.aeroglobe.data.flights

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.riedel.aeroglobe.settings.SettingsStore

/** Manual DI for the one Worker in the app: WorkManager builds it, so it can't take a constructor. */
class FlightWorkerFactory(
    private val flights: FlightRepository,
    private val statuses: FlightStatusRepository,
    private val scheduler: FlightPollScheduler,
    private val settings: SettingsStore,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        FlightPollWorker::class.java.name ->
            FlightPollWorker(appContext, workerParameters, flights, statuses, scheduler, settings)
        else -> null
    }
}
