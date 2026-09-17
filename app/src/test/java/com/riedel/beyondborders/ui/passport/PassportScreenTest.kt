package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.ui.places.Grouping
import com.riedel.beyondborders.ui.places.PlaceRow
import com.riedel.beyondborders.ui.places.StatusFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The travel list's empty state: distinguishing the startup window (the registry
 * has not populated [com.riedel.beyondborders.ui.places.PlacesViewModel.places]
 * yet) from a query or filter that genuinely matched nothing.
 */
class PassportScreenTest {

    @Test
    fun `loading takes priority over a query while the registry has not populated yet`() {
        assertEquals(
            "Loading your places…",
            passportEmptyMessage(loading = true, query = "atlantis", statusFilter = StatusFilter.BEEN),
        )
    }

    @Test
    fun `an active query names itself in the empty state`() {
        assertEquals(
            "No places match “atlantis”.",
            passportEmptyMessage(loading = false, query = "atlantis", statusFilter = StatusFilter.ALL),
        )
    }

    @Test
    fun `an active status filter names itself when there is no query`() {
        assertEquals(
            "No places match the “Been” filter.",
            passportEmptyMessage(loading = false, query = "", statusFilter = StatusFilter.BEEN),
        )
    }

    @Test
    fun `nothing matches is the fallback once loaded with no query or filter active`() {
        assertEquals(
            "Nothing matches.",
            passportEmptyMessage(loading = false, query = "", statusFilter = StatusFilter.ALL),
        )
    }

    @Test
    fun `clear is offered only when a query or filter is active`() {
        assertTrue(canClearPassportFilter(query = "atlantis", statusFilter = StatusFilter.ALL))
        assertTrue(canClearPassportFilter(query = "", statusFilter = StatusFilter.BEEN))
        assertFalse(canClearPassportFilter(query = "", statusFilter = StatusFilter.ALL))
    }

    private fun place(code: String, name: String, continent: String) = Place(
        code = code,
        name = name,
        continent = continent,
        region = continent,
        sovereign = name,
        uninhabited = false,
        unMember = true,
        lat = 0.0,
        lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
        areaKm2 = 0,
        popEst = 0,
    )

    private fun row(code: String, name: String, continent: String) =
        PlaceRow(place(code, name, continent), PlaceStatus.NONE)

    /**
     * The fast-scroll overlay's index-to-header mapping has to mirror the travel
     * loop's own header dedup exactly, or the overlay would name the wrong section
     * mid-scroll.
     */
    @Test
    fun `each row's slot carries the header it sits under, and headers appear once per run`() {
        val rows = listOf(
            row("FR", "France", "Europe"),
            row("DE", "Germany", "Europe"),
            row("EG", "Egypt", "Africa"),
        )
        assertEquals(
            listOf("Europe", "Europe", "Europe", "Africa", "Africa"),
            travelHeaderSlots(rows, Grouping.CONTINENT, collapsedHeaders = emptySet(), query = ""),
        )
    }

    @Test
    fun `a collapsed header keeps its own slot but drops its rows' slots`() {
        val rows = listOf(row("FR", "France", "Europe"), row("DE", "Germany", "Europe"))
        val collapsed = setOf("${Grouping.CONTINENT.name}:Europe")
        assertEquals(
            listOf("Europe"),
            travelHeaderSlots(rows, Grouping.CONTINENT, collapsedHeaders = collapsed, query = ""),
        )
    }

    @Test
    fun `a query in progress ignores the collapse state, same as the row loop does`() {
        val rows = listOf(row("FR", "France", "Europe"), row("DE", "Germany", "Europe"))
        val collapsed = setOf("${Grouping.CONTINENT.name}:Europe")
        assertEquals(
            listOf("Europe", "Europe", "Europe"),
            travelHeaderSlots(rows, Grouping.CONTINENT, collapsedHeaders = collapsed, query = "many"),
        )
    }

    /**
     * The A-Z rail's letters: every distinct header the list actually emits, in the
     * order it emits them, so a letter with nowhere to jump never makes it onto the
     * rail.
     */
    @Test
    fun `an empty header slot list gives an empty rail`() {
        assertEquals(emptyList<String>(), railLabels(emptyList()))
    }

    @Test
    fun `a leading null slot from the mode toggle is dropped from the rail`() {
        assertEquals(listOf("A"), railLabels(listOf(null, "A", "A")))
    }

    @Test
    fun `a filter that leaves one section gives a rail of one letter`() {
        assertEquals(listOf("F"), railLabels(listOf(null, null, "F", "F", "F")))
    }

    @Test
    fun `each header appears once on the rail no matter how many rows sit under it`() {
        assertEquals(
            listOf("A", "B"),
            railLabels(listOf(null, "A", "A", "A", "B", "B")),
        )
    }

    /**
     * The A-Z rail's tap-and-scrub target: the index of a label's own header slot,
     * not one of its rows, so `scrollToItem` lands the sticky header at the top.
     */
    @Test
    fun `a label maps back to its header's own first slot`() {
        val slots = listOf(null, "A", "A", "A", "B", "B")
        assertEquals(1, railTargetIndex(slots, "A"))
        assertEquals(4, railTargetIndex(slots, "B"))
    }

    @Test
    fun `a label absent from the slots has nowhere to jump`() {
        assertEquals(null, railTargetIndex(listOf(null, "A", "A"), "Z"))
    }

    @Test
    fun `a label absent from an empty slot list has nowhere to jump either`() {
        assertEquals(null, railTargetIndex(emptyList(), "A"))
    }
}
