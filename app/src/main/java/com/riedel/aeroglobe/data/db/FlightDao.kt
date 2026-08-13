package com.riedel.aeroglobe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {

    /** Tracked flights, soonest first. The list screen renders straight from this. */
    @Query("SELECT * FROM flight WHERE deletedAt IS NULL ORDER BY date ASC")
    fun observeActive(): Flow<List<FlightEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: FlightEntity)

    @Query("UPDATE flight SET deletedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun tombstone(id: String, now: Long): Int
}
