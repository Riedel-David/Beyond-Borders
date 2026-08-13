package com.riedel.aeroglobe

import android.app.Application
import androidx.work.Configuration

class AeroGlobeApp : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }

    // Manual DI reaches the flight poll worker too: WorkManager needs its own factory
    // instead of the default one, which can only build zero-argument Workers.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.flightWorkerFactory)
            .build()
}
