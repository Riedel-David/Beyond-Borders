package com.riedel.aeroglobe.data.place

import com.riedel.aeroglobe.core.Uuid
import com.riedel.aeroglobe.data.backup.BackupExport
import com.riedel.aeroglobe.data.backup.BackupFormatException
import com.riedel.aeroglobe.data.backup.BackupMerge
import com.riedel.aeroglobe.data.backup.TravelBackup
import com.riedel.aeroglobe.data.db.PlaceStatusDao
import com.riedel.aeroglobe.data.db.PlaceStatusEntity
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
    private val dao: PlaceStatusDao,
    private val deviceId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
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

    suspend fun currentStatus(code: String): PlaceStatus = withContext(Dispatchers.IO) {
        val row = dao.findByCode(code)
        if (row == null || row.deletedAt != null) PlaceStatus.NONE
        else PlaceStatus.fromStorage(row.status)
    }

    suspend fun setFirstVisitedOn(code: String, isoDate: String?) = withContext(Dispatchers.IO) {
        dao.updateFirstVisitedOn(code, isoDate, clock(), deviceId)
        Unit
    }

    suspend fun setNote(code: String, note: String?) = withContext(Dispatchers.IO) {
        dao.updateNote(code, note?.takeIf { it.isNotBlank() }, clock(), deviceId)
        Unit
    }

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
                    firstVisitedOn = row.firstVisitedOn,
                    note = row.note,
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
     * Merge a backup file into the database and report what it did.
     *
     * @throws BackupFormatException if [text] is not a readable AeroGlobe backup.
     */
    suspend fun importBackup(text: String): BackupMerge.Plan = withContext(Dispatchers.IO) {
        val payload = TravelBackup.decode(text)
        val local = dao.allRows().associate { it.code to it.updatedAt }
        val plan = BackupMerge.plan(
            entries = payload.entries,
            localUpdatedAt = local,
            knownCodes = registry.places.mapTo(HashSet()) { it.code },
            unknownStatuses = payload.unknownStatuses,
        )
        dao.applyAll(
            plan.apply.map { entry ->
                PlaceStatusEntity(
                    // Only used if the place has no row yet. Seeding v7 with the
                    // entry's own timestamp keeps ids time-ordered by when the
                    // decision was made rather than by when it was imported.
                    id = Uuid.v7(entry.updatedAt),
                    code = entry.code,
                    // Kept verbatim even for a tombstone: that is how the app's own
                    // un-mark writes look, and it means re-marking the place later
                    // does not lose what it used to be.
                    status = entry.status.storageValue,
                    firstVisitedOn = entry.firstVisitedOn,
                    note = entry.note,
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
