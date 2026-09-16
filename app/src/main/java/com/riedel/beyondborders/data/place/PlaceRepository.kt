package com.riedel.beyondborders.data.place

import androidx.room.withTransaction
import com.riedel.beyondborders.core.Uuid
import com.riedel.beyondborders.data.backup.BackupExport
import com.riedel.beyondborders.data.backup.BackupFormatException
import com.riedel.beyondborders.data.backup.BackupMerge
import com.riedel.beyondborders.data.backup.TravelBackup
import com.riedel.beyondborders.data.db.BeyondBordersDatabase
import com.riedel.beyondborders.data.db.PlaceStatusDao
import com.riedel.beyondborders.data.db.PlaceStatusEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The single place the UI goes to ask "where have I been" and to say "I have been
 * here". Joins the immutable bundled registry with the user's mutable decisions.
 */
class PlaceRepository(
    registryLoader: () -> PlaceRegistry,
    private val database: BeyondBordersDatabase,
    private val deviceId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao: PlaceStatusDao = database.placeStatusDao()
    // Thread-safe by default, and only ever touched from inside withContext(IO),
    // so the ~50 kB parse never runs on the main thread.
    private val registry: PlaceRegistry by lazy(registryLoader)

    /** Code -> status, for every place the user has touched. */
    val statuses: Flow<Map<String, PlaceStatus>> =
        dao.observeActive().map { rows ->
            rows.associate { it.code to PlaceStatus.fromStorage(it.status) }
        }

    suspend fun places(): List<Place> = withContext(Dispatchers.IO) { registry.places }

    suspend fun place(code: String): Place? = withContext(Dispatchers.IO) { registry[code] }

    /**
     * Set the status of exactly one place.
     *
     * "Exactly one" is the whole point: [code] is a leaf with no children to
     * cascade to, so marking Denmark can never touch Greenland. See Place's kdoc.
     */
    suspend fun setStatus(code: String, status: PlaceStatus) = withContext(Dispatchers.IO) {
        require(registry[code] != null) { "unknown place code: $code" }
        val now = clock()
        if (status == PlaceStatus.NONE) {
            dao.tombstone(code, now, deviceId)
        } else {
            dao.setStatus(code, status.storageValue, now, deviceId, Uuid.v7(now))
        }
    }

    /**
     * Flip one place between [target] and no status at all, returning what it ended
     * up as.
     *
     * The current value has to be read here rather than by the caller. The UI's copy
     * arrives over [statuses], which lags a write by an emission, so two quick taps
     * on the same chip both saw the old value and both wrote [target] — losing the
     * second tap, the one the user means as an undo. Reading and writing inside one
     * transaction also serializes concurrent taps, which a read followed by a
     * separate write does not.
     */
    suspend fun toggleStatus(code: String, target: PlaceStatus): PlaceStatus =
        withContext(Dispatchers.IO) {
            require(registry[code] != null) { "unknown place code: $code" }
            database.withTransaction {
                val row = dao.findByCode(code)
                val previous =
                    if (row == null || row.deletedAt != null) PlaceStatus.NONE
                    else PlaceStatus.fromStorage(row.status)
                val next = if (previous == target) PlaceStatus.NONE else target
                val now = clock()
                if (next == PlaceStatus.NONE) {
                    dao.tombstone(code, now, deviceId)
                } else {
                    dao.setStatus(code, next.storageValue, now, deviceId, Uuid.v7(now))
                }
                next
            }
        }

    /** Wipe every place status. The emergency reset in Settings — irreversible, and
     * meant to be: there is no undo for the one table this app cannot re-download. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.clearAll() }

    /**
     * Everything the user has decided, as a backup file's contents.
     *
     * Tombstones included — see TravelBackup.encode for why an un-marked place has
     * to appear in the file rather than simply be missing from it.
     */
    suspend fun exportBackup(): BackupExport = withContext(Dispatchers.IO) {
        val rows = dao.allRows()
        val names = rows.mapNotNull { row -> registry[row.code]?.let { row.code to it.name } }.toMap()
        val json = TravelBackup.encode(
            entries = rows.map { row ->
                TravelBackup.Entry(
                    code = row.code,
                    status = PlaceStatus.fromStorage(row.status),
                    updatedAt = row.updatedAt,
                    deletedAt = row.deletedAt,
                    deviceId = row.deviceId,
                )
            },
            exportedAt = clock(),
            deviceId = deviceId,
            names = names,
        )
        BackupExport(
            json = json,
            places = rows.count { it.deletedAt == null },
            removals = rows.count { it.deletedAt != null },
        )
    }

    /**
     * What an import *would* do, without writing anything. Lets the UI show a
     * confirmation before touching the database.
     *
     * @throws BackupFormatException if [text] is not a readable BeyondBorders backup.
     */
    suspend fun previewBackup(text: String): BackupMerge.Plan = withContext(Dispatchers.IO) {
        val payload = TravelBackup.decode(text)
        planAgainstCurrentRows(payload, registry.codes)
    }

    /**
     * Merge a backup file into the database and report what it did.
     *
     * The plan is recomputed here rather than accepting one from an earlier
     * [previewBackup] call: the user could mark a place in the time between seeing
     * the preview and confirming it, and a plan computed before that would then be
     * stale. Recomputing inside the same transaction as the write is what makes
     * that impossible rather than just unlikely.
     *
     * @throws BackupFormatException if [text] is not a readable BeyondBorders backup.
     */
    suspend fun importBackup(text: String): BackupMerge.Plan = withContext(Dispatchers.IO) {
        val payload = TravelBackup.decode(text)
        // Touched before the transaction opens, not inside it: the registry is lazy, so
        // on the first import of a session dereferencing it here would hold the write
        // transaction open across a ~50 kB JSON parse for no reason.
        val knownCodes = registry.codes
        // Read and apply inside one transaction: a concurrent write landing between
        // the two would otherwise be computed against and then overwritten by a
        // stale plan.
        database.withTransaction {
            val plan = planAgainstCurrentRows(payload, knownCodes)
            dao.applyAll(
                plan.apply.map { entry ->
                    PlaceStatusEntity(
                        // Only used if the place has no row yet. Seeding v7 with the
                        // entry's own timestamp keeps ids time-ordered by when the
                        // decision was made rather than by when it was imported.
                        id = Uuid.v7(entry.updatedAt),
                        code = entry.code,
                        // Passed through as decoded. For an un-marking that means
                        // "none", which PlaceStatusDao.applyAll then declines to write
                        // over an existing row's status — the app's own un-marks leave
                        // the previous status in place and only set deletedAt, so a
                        // merge has to as well.
                        status = entry.status.storageValue,
                        updatedAt = entry.updatedAt,
                        deletedAt = entry.deletedAt,
                        // A file written before deviceId existed, or edited by hand, is
                        // attributed to this device rather than to nobody.
                        deviceId = entry.deviceId ?: deviceId,
                    )
                }
            )
            plan
        }
    }

    /** Shared by [previewBackup] and [importBackup]: reads the rows as they stand
     * right now and plans against them. */
    private suspend fun planAgainstCurrentRows(
        payload: TravelBackup.Payload,
        knownCodes: Set<String>,
    ): BackupMerge.Plan {
        val local = dao.allRows().associate { it.code to it.updatedAt }
        return BackupMerge.plan(
            entries = payload.entries,
            localUpdatedAt = local,
            knownCodes = knownCodes,
            unknownStatuses = payload.unknownStatuses,
            unreadableRows = payload.unreadableRows,
        )
    }
}
