package com.riedel.aeroglobe.data.place

/**
 * What the user has said about a place.
 *
 * Stored as a string rather than an ordinal so that adding a state later (say
 * "lived in") cannot silently renumber existing rows, and so the sync payload
 * stays readable.
 */
enum class PlaceStatus(val storageValue: String) {
    /** No row, or a tombstoned one. */
    NONE("none"),
    VISITED("visited"),
    WISHLIST("wishlist"),
    ;

    /** The cycle a tap on the map walks through. */
    fun next(): PlaceStatus = when (this) {
        NONE -> VISITED
        VISITED -> WISHLIST
        WISHLIST -> NONE
    }

    companion object {
        fun fromStorage(value: String?): PlaceStatus = when (value) {
            VISITED.storageValue -> VISITED
            WISHLIST.storageValue -> WISHLIST
            else -> NONE
        }
    }
}
