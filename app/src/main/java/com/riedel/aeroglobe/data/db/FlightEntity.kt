package com.riedel.aeroglobe.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One flight the user is tracking, entered by hand: number and date, nothing looked
 * up yet. Gate/status/position are phase 3b+; this table just has to survive a
 * restart.
 */
@Entity(
    tableName = "flight",
    indices = [Index(value = ["updatedAt"])],
)
data class FlightEntity(
    /** UUID v7 — see core/Uuid.kt. */
    @PrimaryKey val id: String,
    /** Normalized IATA form, e.g. "LH 400". See FlightsViewModel.parseFlightNumber. */
    val flightNumber: String,
    /** ISO-8601, e.g. "2026-08-13". */
    val date: String,
    /** Unix millis of the last write, from the writing device's clock. */
    val updatedAt: Long,
    /** Unix millis when the flight was removed; null while it is active. */
    val deletedAt: Long? = null,
    val deviceId: String,
    /** Typed by the user, not fetched — no data source has ever known a seat. */
    val seat: String? = null,
)
