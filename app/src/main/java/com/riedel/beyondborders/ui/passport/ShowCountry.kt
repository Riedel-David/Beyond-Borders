package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.ui.places.Grouping
import com.riedel.beyondborders.ui.places.PassportMode
import com.riedel.beyondborders.ui.places.PlaceRow
import com.riedel.beyondborders.ui.places.VisaRow
import com.riedel.beyondborders.ui.places.sectionHeader

/**
 * A one-shot "show this country in the list" from the map's selected-country card.
 *
 * [nonce] gives every tap its own identity: a second tap on the same flag is an equal
 * `code` and `mode`, and a data class assigned an equal value is a no-op for
 * `mutableStateOf`, so the effect keyed on it would never run again. Deliberately kept
 * out of `rememberSaveable` so a stale request cannot replay after rotation or process death.
 */
data class ShowCountryRequest(val code: String, val mode: PassportMode, val nonce: Long)

/**
 * The absolute `LazyColumn` item index of [code]'s row in the travel list, or null when
 * the row is not emitted (absent, filtered out, or inside a collapsed section). [leading]
 * is the number of items before the first section header. Mirrors the loop in
 * `PassportScreen` through [travelHeaderSlots]'s own walk, so the two cannot drift.
 */
internal fun travelRowItemIndex(
    rows: List<PlaceRow>,
    grouping: Grouping,
    collapsedHeaders: Set<String>,
    query: String,
    code: String,
    leading: Int,
): Int? {
    val effectiveCollapsed = if (query.isBlank()) collapsedHeaders else emptySet()
    var index = leading
    var lastHeader: String? = null
    rows.forEach { row ->
        val header = sectionHeader(row.place, grouping)
        if (header != lastHeader) {
            lastHeader = header
            index++ // the sticky header's own item
        }
        if ("${grouping.name}:$header" in effectiveCollapsed) return@forEach
        if (row.place.code == code) return index
        index++
    }
    return null
}

/** Same as [travelRowItemIndex] for the visa list, whose headers are never collapsible. */
internal fun visaRowItemIndex(rows: List<VisaRow>, code: String, leading: Int): Int? {
    var index = leading
    var lastHeader: Any? = null
    rows.forEach { row ->
        val header = row.requirement.category
        if (header != lastHeader) {
            lastHeader = header
            index++
        }
        if (row.place.code == code) return index
        index++
    }
    return null
}

/**
 * Where to scroll so the row at [rowIndex] is visible: one item earlier, which is the
 * section's pinned header for a first row (so the row is not hidden under it) and the
 * neighbouring row otherwise.
 */
internal fun scrollTargetFor(rowIndex: Int): Int = (rowIndex - 1).coerceAtLeast(0)
