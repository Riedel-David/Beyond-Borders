package com.riedel.aeroglobe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightStatusChangeDao {

    @Query("SELECT * FROM flight_status_change WHERE flightId = :flightId AND deletedAt IS NULL ORDER BY changedAt DESC")
    fun observe(flightId: String): Flow<List<FlightStatusChangeEntity>>

    @Insert
    suspend fun insert(row: FlightStatusChangeEntity)
}
