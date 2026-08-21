package com.riedel.aeroglobe.data.db

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
     * Overwrite one place's row wholesale, including its sync columns.
     *
     * Unlike [updateStatus] this takes the incoming [updatedAt] instead of stamping
     * "now", because a merged row has to keep the timestamp it was decided by —
     * re-stamping it would make an old backup look like the freshest write on the
     * device and beat every future sync.
     */
    @Query(
        """
        UPDATE place_status
           SET status = :status,
               updatedAt = :updatedAt,
               deletedAt = :deletedAt,
               deviceId = :deviceId
         WHERE code = :code
        """
    )
    suspend fun replaceByCode(
        code: String,
        status: String,
        updatedAt: Long,
        deletedAt: Long?,
        deviceId: String,
    ): Int

    /**
     * Apply an already-decided set of rows in one transaction.
     *
     * Which rows those are is not this method's business — see BackupMerge, which
     * makes the last-write-wins call as a pure function. Here the only question left
     * is whether each place already has a row, answered the same way [setStatus]
     * answers it: try the update, insert if it matched nothing.
     */
    @Transaction
    suspend fun applyAll(rows: List<PlaceStatusEntity>) {
        rows.forEach { row ->
            val matched = replaceByCode(
                code = row.code,
                status = row.status,
                updatedAt = row.updatedAt,
                deletedAt = row.deletedAt,
                deviceId = row.deviceId,
            )
            if (matched == 0) insert(row)
        }
    }
}
