package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.ui.places.VisaFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The visa list's empty state. Unlike the travel list, there is no loading window
 * to distinguish here: [visaSection] only reaches this branch once the registry
 * itself is non-null, so a query or a filter are the only two causes left.
 */
class VisaSectionTest {

    @Test
    fun `an active query names itself in the visa empty state`() {
        assertEquals(
            "No destinations match “atlantis”.",
            visaEmptyMessage(query = "atlantis", filter = VisaFilter.All),
        )
    }

    @Test
    fun `an active visa filter names itself when there is no query`() {
        assertEquals(
            "No destinations match the “No embassy” filter.",
            visaEmptyMessage(query = "", filter = VisaFilter.Easy),
        )
    }

    @Test
    fun `nothing matches is the fallback with no query or filter active`() {
        assertEquals("Nothing matches.", visaEmptyMessage(query = "", filter = VisaFilter.All))
    }

    @Test
    fun `clear is offered only when a visa query or filter is active`() {
        assertTrue(canClearVisaFilter(query = "atlantis", filter = VisaFilter.All))
        assertTrue(canClearVisaFilter(query = "", filter = VisaFilter.Easy))
        assertFalse(canClearVisaFilter(query = "", filter = VisaFilter.All))
    }
}
