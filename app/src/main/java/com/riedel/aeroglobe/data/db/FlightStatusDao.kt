package com.riedel.aeroglobe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightStatusDao {

    @Query("SELECT * FROM flight_status WHERE flightId = :flightId AND deletedAt IS NULL")
    fun observe(flightId: String): Flow<FlightStatusEntity?>

    @Query("SELECT * FROM flight_status WHERE flightId = :flightId AND deletedAt IS NULL")
    suspend fun get(flightId: String): FlightStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: FlightStatusEntity)
}
