package com.riedel.beyondborders

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BeyondBordersApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Constructing SettingsStore is synchronous disk work — two SharedPreferences
        // files, a dozen scalars read out of them, and on first launch a deviceId
        // written back. Until this existed the first thing to touch the lazy was
        // MainActivity's setContent, which put all of it on the main thread inside the
        // first frame. Resolving it here instead runs it on an IO thread, in parallel
        // with the rest of startup, so the composables that ask for it find it done.
        CoroutineScope(Dispatchers.IO).launch {
            container.settings
            // Opening the database is disk work of the same kind, and doing it here
            // rather than on the first query means a version this build cannot migrate
            // is a screen offering a way out instead of a crash — see DatabaseRecovery.
            // Settings first: the theme reads them, and this is the slower of the two.
            container.refreshDatabaseHealth()
        }
    }
}
