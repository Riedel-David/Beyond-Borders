package com.riedel.beyondborders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceStatusDao {

    /**
     * Every live decision, as a stream. The map and the passport list both render
     * from this, so a tap on either updates the other with no manual plumbing.
     */
    @Query("SELECT * FROM place_status WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<PlaceStatusEntity>>

    @Query("SELECT * FROM place_status WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): PlaceStatusEntity?

    /** Rows a sync run still has to push, tombstones included. */
    @Query("SELECT * FROM place_status WHERE updatedAt > :since ORDER BY updatedAt")
    suspend fun changedSince(since: Long): List<PlaceStatusEntity>

    /**
     * The whole table, tombstones included — what a backup export writes.
     *
     * Ordered by code rather than by updatedAt so that exporting twice produces the
     * same bytes; see TravelBackup.encode.
     */
    @Query("SELECT * FROM place_status ORDER BY code")
    suspend fun allRows(): List<PlaceStatusEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: PlaceStatusEntity)

    /** The emergency reset in Settings. Every row, tombstones included — there is
     * nothing left to import or undo from once this runs. */
    @Query("DELETE FROM place_status")
    suspend fun clearAll()

    /** Also clears any tombstone, which is what re-marking a place means. */
    @Query(
        """
        UPDATE place_status
           SET status = :status, deletedAt = NULL, updatedAt = :now, deviceId = :deviceId
         WHERE code = :code
        """
    )
    suspend fun updateStatus(code: String, status: String, now: Long, deviceId: String): Int

    @Query(
        """
        UPDATE place_status
           SET deletedAt = :now, updatedAt = :now, deviceId = :deviceId
         WHERE code = :code AND deletedAt IS NULL
        """
    )
    suspend fun tombstone(code: String, now: Long, deviceId: String): Int

    /**
     * Set a status whether or not a row exists yet.
     *
     * Update-then-insert inside one transaction rather than @Upsert, because a
     * row's identity is its [PlaceStatusEntity.code], not its id — an upsert keyed
     * on the primary key would happily insert a second row for the same place and
     * only fail later on the unique index.
     */
    @Transaction
    suspend fun setStatus(code: String, status: String, now: Long, deviceId: String, newId: String) {
        if (updateStatus(code, status, now, deviceId) == 0) {
            insert(
                PlaceStatusEntity(
                    id = newId,
                    code = code,
                    status = status,
                    updatedAt = now,
                    deviceId = deviceId,
                )
            )
        }
    }

    /**
     * Merge one already-decided row in, whether or not the place has a row yet.
     *
     * A place's identity is its [PlaceStatusEntity.code], not its id, so the conflict
     * target is the unique index on `code` — on a hit the existing row keeps its id
     * and only the four value columns move.
     *
     * Two things this does *not* do. It does not stamp "now" over the incoming
     * [updatedAt]: a merged row has to keep the timestamp it was decided by, or an old
     * backup would look like the freshest write on the device and beat every future
     * sync. And it does not write `'none'` — the storage value of
     * [com.riedel.beyondborders.data.place.PlaceStatus.NONE], which
     * [PlaceStatusEntity] documents the column as never holding — over a status that
     * already exists. A hand-written `"status": "none"` arrives here as a
     * tombstone carrying no status worth keeping, and the app's own un-marks leave the
     * previous status in place and set only `deletedAt` (see [tombstone]), so a merge
     * has to as well or an export would not round-trip. A place with no row at all is
     * the one case with nothing to preserve, and there the insert records `'none'` as
     * it came: the tombstone still has to exist, or a later import of an older
     * "visited" would win the merge.
     */
    @Query(
        """
        INSERT INTO place_status (`id`, `code`, `status`, `updatedAt`, `deletedAt`, `deviceId`)
        VALUES (:id, :code, :status, :updatedAt, :deletedAt, :deviceId)
        ON CONFLICT(`code`) DO UPDATE SET
            `status` = CASE WHEN :status = 'none' THEN place_status.`status` ELSE :status END,
            `updatedAt` = :updatedAt,
            `deletedAt` = :deletedAt,
            `deviceId` = :deviceId
        """
    )
    suspend fun merge(
        id: String,
        code: String,
        status: String,
        updatedAt: Long,
        deletedAt: Long?,
        deviceId: String,
    )

    /**
     * Apply an already-decided set of rows in one transaction.
     *
     * Which rows those are is not this method's business — see BackupMerge, which
     * makes the last-write-wins call as a pure function. Each row is one [merge];
     * this was an update, a branch on the status, and a conditional insert per row,
     * which is two round trips per place and ~500 statements for a full-world import.
     */
    @Transaction
    suspend fun applyAll(rows: List<PlaceStatusEntity>) {
        rows.forEach { row ->
            merge(
                id = row.id,
                code = row.code,
                status = row.status,
                updatedAt = row.updatedAt,
                deletedAt = row.deletedAt,
                deviceId = row.deviceId,
            )
        }
    }
}
