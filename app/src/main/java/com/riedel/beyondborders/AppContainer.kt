package com.riedel.beyondborders

import android.content.Context
import com.riedel.beyondborders.core.Haptics
import com.riedel.beyondborders.data.backup.CloudSnapshot
import com.riedel.beyondborders.data.backup.shouldStageSnapshot
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

    // Held as the Lazy rather than only as a delegated property so [checkpointDatabase]
    // can ask whether it was ever opened without opening it.
    private val databaseLazy = lazy { BeyondBordersDatabase.open(appContext) }

    private val database: BeyondBordersDatabase get() = databaseLazy.value

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

    /**
     * Fold the write-ahead log back into `beyondborders.db`.
     *
     * The database is excluded from Auto Backup now (see `backup_rules.xml`), so this
     * is no longer about backup consistency — it is crash recovery: folding `-wal` back
     * in as the app leaves the foreground shrinks how much a hard kill or a wiped app
     * process could lose to whatever was still sitting unflushed.
     *
     * Blocking — the caller puts it on IO. Silent on failure: this runs on the way out
     * of the app, and the WAL staying put for another round is not worth a crash.
     */
    fun checkpointDatabase() {
        // Not worth opening the file for, and on the recovery path it cannot be opened
        // at all — the probe having failed is exactly why nothing has touched it.
        if (!databaseLazy.isInitialized()) return
        runCatching {
            databaseLazy.value.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                // rawQuery is lazy: the pragma only runs once the cursor is filled.
                .use { it.moveToFirst() }
        }
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

    val cloudSnapshot: CloudSnapshot by lazy { CloudSnapshot(appContext) }

    /**
     * Refresh the snapshot Auto Backup carries.
     *
     * An empty export does nothing at all — not write, not delete. The empty case is
     * exactly the state a fresh install sits in right after a restore: the database
     * is empty because nothing has been imported yet, but the snapshot next to it is
     * the whole reason a Restore row has anything to offer. Deleting it here on the
     * very first `onPause` would erase it before it was ever read.
     * [SettingsViewModel.deleteEverything] deletes the snapshot explicitly instead —
     * that is the one place "no history" is a real action rather than "not yet".
     */
    suspend fun stageCloudSnapshot() {
        val export = places.exportBackup()
        if (shouldStageSnapshot(export)) cloudSnapshot.write(export.json)
    }
}
