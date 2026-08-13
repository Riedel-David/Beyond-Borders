package com.riedel.aeroglobe.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An append-only log of one field changing on a flight's status, e.g. gate A15 -> B42. */
@Entity(
    tableName = "flight_status_change",
    indices = [Index(value = ["flightId", "changedAt"])],
)
data class FlightStatusChangeEntity(
    @PrimaryKey val id: String,
    val flightId: String,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val deviceId: String,
)
