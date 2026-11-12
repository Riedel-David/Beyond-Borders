package com.riedel.beyondborders.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One trip to one place. A place can have many; [year] is the only date kept, and
 * null means "been there, no idea when".
 *
 * Not unique on [code]: two visits in the same year are two visits. The sync columns
 * follow [PlaceStatusEntity], and a deleted visit is a tombstone for the same reason
 * an un-marked place is — a delete has to survive a merge with an older backup.
 *
 * Visits are deliberately independent of the place's status row: clearing a status
 * keeps them (hidden), re-marking brings them back.
 */
@Entity(
    tableName = "visit",
    indices = [
        Index(value = ["code"]),
        Index(value = ["updatedAt"]),
    ],
)
data class VisitEntity(
    /** UUID v7 — see core/Uuid.kt. */
    @PrimaryKey val id: String,
    /** Matches Place.code in the bundled registry. */
    val code: String,
    val year: Int? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val deviceId: String,
    /** 1..12, only with a [year]; null means the whole year. Added in schema 12, hence last. */
    val month: Int? = null,
)
