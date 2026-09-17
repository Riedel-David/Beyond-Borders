package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.VisaOrigin
import com.riedel.beyondborders.data.visa.VisaRequirement
import com.riedel.beyondborders.ui.places.VisaFilter
import com.riedel.beyondborders.ui.places.VisaRow
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

    private fun row(code: String, name: String, category: VisaCategory) = VisaRow(
        place = Place(
            code = code,
            name = name,
            continent = "Europe",
            region = "Europe",
            sovereign = name,
            uninhabited = false,
            unMember = true,
            lat = 0.0,
            lon = 0.0,
            bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
            areaKm2 = 0,
            popEst = 0,
        ),
        requirement = VisaRequirement(
            code = code,
            category = category,
            days = null,
            origin = VisaOrigin.DATASET,
            note = null,
            confidence = null,
            sourceUrl = null,
            datasetSaid = null,
            officialUrl = null,
            officialFor = null,
        ),
        status = PlaceStatus.NONE,
    )

    /**
     * [visaHeaderSlots] mirrors [visaSection]'s own header dedup for the fast-scroll
     * overlay, and unlike the travel list there is no collapse to skip.
     */
    @Test
    fun `each row's slot carries its category, once per contiguous run`() {
        val rows = listOf(
            row("FR", "France", VisaCategory.VISA_FREE),
            row("DE", "Germany", VisaCategory.VISA_FREE),
            row("US", "United States", VisaCategory.E_VISA),
        )
        assertEquals(
            listOf("Visa-free", "Visa-free", "Visa-free", "e-Visa", "e-Visa"),
            visaHeaderSlots(rows),
        )
    }
}
