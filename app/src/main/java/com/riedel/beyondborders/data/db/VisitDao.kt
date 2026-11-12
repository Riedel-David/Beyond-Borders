package com.riedel.beyondborders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {

    /** Every live visit. Whether the place is still Been/Lived is the reader's call. */
    @Query("SELECT * FROM visit WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<VisitEntity>>

    /** The whole table, tombstones included — what a backup export writes. Ordered for stable bytes. */
    @Query("SELECT * FROM visit ORDER BY code, id")
    suspend fun allRows(): List<VisitEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: VisitEntity)

    @Query(
        """
        UPDATE visit
           SET deletedAt = :now, updatedAt = :now, deviceId = :deviceId
         WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun tombstone(id: String, now: Long, deviceId: String): Int

    /** The emergency reset in Settings: every row, tombstones included. */
    @Query("DELETE FROM visit")
    suspend fun clearAll()

    /**
     * Write rows BackupMerge already decided on. REPLACE on the primary key is the
     * right conflict rule here: the incoming row won the updatedAt comparison and keeps
     * its own timestamp, so nothing is stamped "now".
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<VisitEntity>)
}
