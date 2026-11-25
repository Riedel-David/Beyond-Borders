package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.ui.places.Grouping
import com.riedel.beyondborders.ui.places.PassportMode
import com.riedel.beyondborders.ui.places.PlaceRow
import com.riedel.beyondborders.ui.places.StampedRows
import com.riedel.beyondborders.ui.places.StatusFilter
import com.riedel.beyondborders.ui.places.VisaFilter
import com.riedel.beyondborders.ui.places.VisaRow
import com.riedel.beyondborders.ui.places.sectionHeader

/**
 * A one-shot "show this country in the list" from the map's selected-country card.
 *
 * Two taps on the same flag are equal values, which a `mutableStateOf` would ignore, but
 * the root nulls the request on every consumption and the Passport tab is a different
 * screen from the map, so each tap is a null-to-value change and re-fires the effect.
 * Deliberately not `rememberSaveable`, so a stale request cannot replay after rotation
 * or process death.
 */
data class ShowCountryRequest(val code: String, val mode: PassportMode)

/**
 * Whether the list the screen is showing has had the "show country" reset applied, i.e.
 * nothing the reset clears is still hiding rows. Until then a row index would refer to
 * the previous, filtered list.
 */
internal fun listSettledForShow(
    mode: PassportMode,
    query: String,
    statusFilter: StatusFilter,
    visaFilter: VisaFilter,
    wishlistOnly: Boolean,
): Boolean = query.isBlank() && when (mode) {
    PassportMode.VISA -> visaFilter == VisaFilter.All && !wishlistOnly
    PassportMode.TRAVEL -> statusFilter == StatusFilter.ALL
}

/**
 * [listSettledForShow] judged on the filters this list was built under, not on the live
 * filter flows: those flip first, so for a frame they read as reset while the list is still
 * the old filtered one, and an index read from it points at the wrong row.
 */
internal fun <T> StampedRows<T>.settledForShow(mode: PassportMode): Boolean =
    listSettledForShow(mode, query, statusFilter, visaFilter, wishlistOnly)

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
 * The item index of the requested country's row, or null while the list is not ready (wrong
 * mode, or the stamped list still carries filters the show-country reset clears) or the row is
 * not emitted. The rows are taken from the stamped lists passed in, and the leading item count
 * from [headerSlotsFor] called with those same rows, so a verdict of "settled" and the index
 * always describe the same list; nothing here is a value captured at an earlier composition.
 * [sortTravel] puts the stamped travel rows in display order (the screen's label sort).
 */
internal fun showCountryIndex(
    request: ShowCountryRequest,
    mode: PassportMode,
    travel: StampedRows<PlaceRow>,
    visa: StampedRows<VisaRow>,
    grouping: Grouping,
    collapsedHeaders: Set<String>,
    query: String,
    sortTravel: (List<PlaceRow>) -> List<PlaceRow>,
    headerSlotsFor: (travelRows: List<PlaceRow>, visaRows: List<VisaRow>) -> List<String?>,
): Int? {
    val settled = if (request.mode == PassportMode.VISA) {
        visa.settledForShow(PassportMode.VISA)
    } else {
        travel.settledForShow(PassportMode.TRAVEL)
    }
    if (mode != request.mode || !settled) return null
    val travelRows = sortTravel(travel.rows)
    val slots = headerSlotsFor(travelRows, visa.rows)
    return if (request.mode == PassportMode.VISA) {
        visaRowItemIndex(visa.rows, request.code, slots.size - visaHeaderCategorySlots(visa.rows).size)
    } else {
        travelRowItemIndex(
            travelRows, grouping, collapsedHeaders, query, request.code,
            slots.size - travelHeaderSlots(travelRows, grouping, collapsedHeaders, query).size,
        )
    }
}

/**
 * Where to scroll so the row at [rowIndex] is visible: one item earlier, which is the
 * section's pinned header for a first row (so the row is not hidden under it) and the
 * neighbouring row otherwise.
 */
internal fun scrollTargetFor(rowIndex: Int): Int = (rowIndex - 1).coerceAtLeast(0)

/**
 * Whether a list of [totalItems] items already holds the row at [rowIndex] (an absolute item
 * index, as [showCountryIndex] returns it). Judged on the row, not on [scrollTargetFor]'s
 * target one item earlier: a list of exactly [rowIndex] items has the target but not the row.
 */
internal fun listHoldsRow(totalItems: Int, rowIndex: Int): Boolean = totalItems > rowIndex
