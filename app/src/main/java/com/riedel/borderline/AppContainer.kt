package com.riedel.borderline

import android.content.Context
import com.riedel.borderline.core.Haptics
import com.riedel.borderline.data.db.BorderlineDatabase
import com.riedel.borderline.data.place.PlaceRegistry
import com.riedel.borderline.data.place.PlaceRepository
import com.riedel.borderline.data.visa.VisaRegistry
import com.riedel.borderline.data.visa.VisaRepository
import com.riedel.borderline.settings.SettingsStore

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

    private val database: BorderlineDatabase by lazy { BorderlineDatabase.open(appContext) }

    val places: PlaceRepository by lazy {
        PlaceRepository(
            registryLoader = { PlaceRegistry.load(appContext.assets) },
            dao = database.placeStatusDao(),
            deviceId = settings.deviceId,
        )
    }

    val visa: VisaRepository by lazy {
        VisaRepository(registryLoader = { VisaRegistry.load(appContext.assets) })
    }
}
