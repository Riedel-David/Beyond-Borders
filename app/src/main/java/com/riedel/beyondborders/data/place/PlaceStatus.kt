package com.riedel.beyondborders.data.place

/**
 * What the user has said about a place.
 *
 * Exactly one of these per place, which the schema enforces with a unique index on
 * `code`. There is no "visited *and* lived" combination to represent: [LIVED] is a
 * stronger answer to the same question, and [isBeen] is how the rest of the app
 * asks "have you set foot there" without caring which of the two it was.
 *
 * Stored as a string rather than an ordinal so that adding a state cannot silently
 * renumber existing rows, and so the sync payload stays readable.
 */
enum class PlaceStatus(val storageValue: String) {
    /** No row, or a tombstoned one. */
    NONE("none"),
    VISITED("visited"),
    LIVED("lived"),
    WISHLIST("wishlist"),
    ;

    /**
     * True for the statuses that mean "I have been there".
     *
     * Every total in the app counts on this rather than on `== VISITED`, so that
     * upgrading a country from visited to lived can never make the headline number
     * go down — which would be the one thing a travel tracker must not do.
     */
    val isBeen: Boolean get() = this == VISITED || this == LIVED

    companion object {
        /**
         * Read a status we wrote ourselves, falling back to [NONE].
         *
         * Only for our own database rows, where an unrecognised value means the row
         * is corrupt and [NONE] is the safest reading. Data from outside the app
         * must use [parseOrNull] instead, because there the difference between
         * "not marked" and "written by a build that knows more than this one"
         * matters — see [com.riedel.beyondborders.data.backup.TravelBackup].
         */
        fun fromStorage(value: String?): PlaceStatus = parseOrNull(value) ?: NONE

        /** Read a status strictly: null means "this build does not know that word". */
        fun parseOrNull(value: String?): PlaceStatus? =
            entries.firstOrNull { it.storageValue == value }
    }
}
