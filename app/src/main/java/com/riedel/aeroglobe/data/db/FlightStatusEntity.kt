package com.riedel.aeroglobe.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The latest known status for one tracked flight — one row per [FlightEntity.id]. */
@Entity(tableName = "flight_status")
data class FlightStatusEntity(
    @PrimaryKey val flightId: String,
    val scheduledDepartureUtc: Long,
    val estimatedDepartureUtc: Long?,
    val departureGate: String?,
    val departureTerminal: String?,
    val phase: String,
    val fetchedAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val deviceId: String,
    val aircraftIcao24: String? = null,
    val departureAirportIata: String? = null,
    val actualDepartureUtc: Long? = null,
    val arrivalAirportIata: String? = null,
    val scheduledArrivalUtc: Long? = null,
    val estimatedArrivalUtc: Long? = null,
    val actualArrivalUtc: Long? = null,
    val airlineName: String? = null,
    val airlineIata: String? = null,
    val airlineIcao: String? = null,
    val aircraftModel: String? = null,
)
