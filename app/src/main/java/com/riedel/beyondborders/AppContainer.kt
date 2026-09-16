package com.riedel.beyondborders

import android.content.Context
import com.riedel.beyondborders.core.Haptics
import com.riedel.beyondborders.data.db.BeyondBordersDatabase
import com.riedel.beyondborders.data.db.DatabaseHealth
import com.riedel.beyondborders.data.db.DatabaseRecovery
import com.riedel.beyondborders.data.place.PlaceRegistry
import com.riedel.beyondborders.data.place.PlaceRepository
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.VisaRepository
import com.riedel.beyondborders.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual dependency wiring.
 *
 * Hilt would add an annotation processor and a build-time graph for what is
 * currently a handful of objects with no branching. Everything here is `by lazy`, so
 * nothing is constructed on the launch path unless a screen actually asks for it.
 * If the object count triples, swap this for Hilt then.
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val haptics: Haptics by lazy { Haptics(appContext) }

    private val database: BeyondBordersDatabase by lazy { BeyondBordersDatabase.open(appContext) }

    private val _databaseHealth = MutableStateFlow<DatabaseHealth>(DatabaseHealth.Unknown)

    /**
     * Whether [database] can be opened at all, so the UI can offer a way out instead
     * of crashing on its first query. See [DatabaseRecovery].
     */
    val databaseHealth: StateFlow<DatabaseHealth> = _databaseHealth.asStateFlow()

    /**
     * Run the probe. Called once from `Application.onCreate`, and again after a wipe.
     *
     * Blocking, like [DatabaseRecovery.probe] itself — the callers put it on IO.
     */
    fun refreshDatabaseHealth() {
        _databaseHealth.value = DatabaseRecovery.probe(appContext)
    }

    val places: PlaceRepository by lazy {
        PlaceRepository(
            registryLoader = { PlaceRegistry.load(appContext.assets) },
            database = database,
            deviceId = settings.deviceId,
        )
    }

    val visa: VisaRepository by lazy {
        VisaRepository(
            registryLoader = { passport -> VisaRegistry.load(appContext.assets, passport) },
            passportsLoader = { VisaRegistry.availablePassports(appContext.assets) },
        )
    }
}
