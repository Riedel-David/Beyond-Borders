package com.riedel.borderline.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One user decision about one place.
 *
 * Only places the user has touched get a row; "not visited" is the absence of one.
 * With 252 places that keeps the table in the low hundreds of rows forever.
 *
 * The last three columns are there for phase 5 (optional cross-device sync) and
 * are maintained from day one, because retrofitting change tracking onto rows
 * that were written without it means guessing at history. Concretely:
 *  - [updatedAt] gives last-write-wins conflict resolution.
 *  - [deletedAt] makes un-marking a place a tombstone instead of a vanished row,
 *    so a delete propagates rather than being resurrected by the other device.
 *  - [deviceId] lets a device skip echoing back its own writes.
 */
@Entity(
    tableName = "place_status",
    indices = [
        // Unique: one decision per place. This is also the invariant that stops
        // a bug from ever double-counting a country in the stats.
        Index(value = ["code"], unique = true),
        // Sync pulls "everything changed since T".
        Index(value = ["updatedAt"]),
    ],
)
data class PlaceStatusEntity(
    /** UUID v7 — see core/Uuid.kt for why not an autoincrement int. */
    @PrimaryKey val id: String,
    /** Matches Place.code in the bundled registry. */
    val code: String,
    /** PlaceStatus.storageValue. Never "none": that state is a tombstone. */
    val status: String,
    /** Unix millis of the last write, from the writing device's clock. */
    val updatedAt: Long,
    /** Unix millis when the user un-marked the place; null while it is active. */
    val deletedAt: Long? = null,
    val deviceId: String,
)
