package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.VisaOrigin
import com.riedel.beyondborders.data.visa.VisaRequirement
import com.riedel.beyondborders.ui.places.Grouping
import com.riedel.beyondborders.ui.places.PassportMode
import com.riedel.beyondborders.ui.places.PlaceRow
import com.riedel.beyondborders.ui.places.VisaRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowCountryTest {
    private fun place(code: String, continent: String) = Place(
        code = code, name = code, continent = continent, region = continent, sovereign = code,
        uninhabited = false, unMember = true, lat = 0.0, lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0), areaKm2 = 0, popEst = 0,
    )

    private fun row(code: String, continent: String) = PlaceRow(place(code, continent), PlaceStatus.NONE)

    private fun vrow(code: String, category: VisaCategory) = VisaRow(
        place = place(code, "Europe"),
        requirement = VisaRequirement(
            code = code, category = category, days = null, origin = VisaOrigin.DATASET, note = null,
            confidence = null, sourceUrl = null, datasetSaid = null, officialUrl = null, officialFor = null,
        ),
        status = PlaceStatus.NONE,
    )

    private val travel = listOf(row("FR", "Europe"), row("DE", "Europe"), row("EG", "Africa"))
    private val europeKey = "${Grouping.CONTINENT.name}:Europe"

    @Test
    fun `the first row of a section sits after its header item`() {
        assertEquals(4, travelRowItemIndex(travel, Grouping.CONTINENT, emptySet(), "", "FR", leading = 3))
    }

    @Test
    fun `a later section counts the earlier rows and both headers`() {
        // leading 3, header, FR, DE, header, EG
        assertEquals(7, travelRowItemIndex(travel, Grouping.CONTINENT, emptySet(), "", "EG", leading = 3))
    }

    @Test
    fun `a collapsed section keeps its header item but drops its rows`() {
        val collapsed = setOf(europeKey)
        assertNull(travelRowItemIndex(travel, Grouping.CONTINENT, collapsed, "", "DE", leading = 3))
        // leading 3, header, (FR DE skipped), header, EG
        assertEquals(5, travelRowItemIndex(travel, Grouping.CONTINENT, collapsed, "", "EG", leading = 3))
    }

    @Test
    fun `a query in progress ignores the collapse state`() {
        assertEquals(5, travelRowItemIndex(travel, Grouping.CONTINENT, setOf(europeKey), "fr", "DE", leading = 3))
    }

    @Test
    fun `a country filtered out of the travel rows has no index`() {
        assertNull(travelRowItemIndex(listOf(row("FR", "Europe")), Grouping.CONTINENT, emptySet(), "", "DE", leading = 3))
        assertNull(travelRowItemIndex(emptyList(), Grouping.CONTINENT, emptySet(), "", "DE", leading = 3))
    }

    @Test
    fun `visa rows count one header per category run`() {
        val rows = listOf(
            vrow("FR", VisaCategory.VISA_FREE),
            vrow("DE", VisaCategory.VISA_FREE),
            vrow("EG", VisaCategory.VISA_ON_ARRIVAL),
        )
        assertEquals(6, visaRowItemIndex(rows, "DE", leading = 4))
        assertEquals(8, visaRowItemIndex(rows, "EG", leading = 4))
    }

    @Test
    fun `a country with no visa requirement has no visa index`() {
        assertNull(visaRowItemIndex(listOf(vrow("FR", VisaCategory.VISA_FREE)), "KP", leading = 4))
    }

    @Test
    fun `scrolling targets the item before the row and never goes below zero`() {
        assertEquals(3, scrollTargetFor(4))
        assertEquals(0, scrollTargetFor(0))
    }

    @Test
    fun `two requests for the same country are distinct values`() {
        val a = ShowCountryRequest("FR", PassportMode.TRAVEL, 1)
        assertNotEquals(a, ShowCountryRequest("FR", PassportMode.TRAVEL, 2))
    }
}
