package com.riedel.beyondborders.ui.passport

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
}
