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
import com.riedel.beyondborders.ui.places.ShowInListPlan
import com.riedel.beyondborders.ui.places.StatusFilter
import com.riedel.beyondborders.ui.places.VisaFilter
import com.riedel.beyondborders.ui.places.StampedRows
import com.riedel.beyondborders.ui.places.showInListPlan
import com.riedel.beyondborders.ui.places.VisaRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `the list is settled only once the query and the mode's filters are cleared`() {
        val all = StatusFilter.ALL
        val none = VisaFilter.All
        assertTrue(listSettledForShow(PassportMode.TRAVEL, "", all, none, wishlistOnly = false))
        assertFalse(listSettledForShow(PassportMode.TRAVEL, "fra", all, none, wishlistOnly = false))
        assertFalse(listSettledForShow(PassportMode.TRAVEL, "", StatusFilter.BEEN, none, wishlistOnly = false))
        assertTrue(listSettledForShow(PassportMode.TRAVEL, "", all, VisaFilter.Easy, wishlistOnly = true))
        assertFalse(listSettledForShow(PassportMode.VISA, "", all, VisaFilter.Easy, wishlistOnly = false))
        assertFalse(listSettledForShow(PassportMode.VISA, "", all, none, wishlistOnly = true))
        assertTrue(listSettledForShow(PassportMode.VISA, "", StatusFilter.BEEN, none, wishlistOnly = false))
    }

    @Test
    fun `a list still stamped with the old filters is not settled even once the live filters read as reset`() {
        val filteredTravel = StampedRows(listOf(row("FR", "Europe")), query = "fra", statusFilter = StatusFilter.BEEN)
        val freshTravel = StampedRows(travel)
        assertFalse(filteredTravel.settledForShow(PassportMode.TRAVEL))
        assertTrue(freshTravel.settledForShow(PassportMode.TRAVEL))
        val filteredVisa = StampedRows(listOf(vrow("FR", VisaCategory.VISA_FREE)), visaFilter = VisaFilter.Easy, wishlistOnly = true)
        assertFalse(filteredVisa.settledForShow(PassportMode.VISA))
        assertTrue(StampedRows<VisaRow>(emptyList()).settledForShow(PassportMode.VISA))
        // The stale list does contain the tapped country, which is exactly why it must be refused.
        assertEquals(4, travelRowItemIndex(filteredTravel.rows, Grouping.CONTINENT, emptySet(), "", "FR", leading = 3))
    }

    @Test
    fun `a country with no row resets nothing`() {
        assertEquals(
            ShowInListPlan(false, false, false, null),
            showInListPlan(PassportMode.VISA, present = false, collapseKey = "x", collapsed = setOf("x")),
        )
    }

    @Test
    fun `the travel plan resets the status filter and drops only a collapsed key`() {
        assertEquals(
            ShowInListPlan(true, true, false, europeKey),
            showInListPlan(PassportMode.TRAVEL, present = true, collapseKey = europeKey, collapsed = setOf(europeKey)),
        )
        assertEquals(
            ShowInListPlan(true, true, false, null),
            showInListPlan(PassportMode.TRAVEL, present = true, collapseKey = europeKey, collapsed = emptySet()),
        )
    }

    @Test
    fun `the visa plan resets the visa filters and leaves collapse alone`() {
        assertEquals(
            ShowInListPlan(true, false, true, null),
            showInListPlan(PassportMode.VISA, present = true, collapseKey = europeKey, collapsed = setOf(europeKey)),
        )
    }

    private val full = listOf(
        vrow("FR", VisaCategory.VISA_FREE),
        vrow("DE", VisaCategory.VISA_FREE),
        vrow("EG", VisaCategory.VISA_ON_ARRIVAL),
    )

    // Leading items as the screen builds them: mode + visa-summary/search/filters = 4.
    private fun slots(visaRows: List<VisaRow>) =
        passportHeaderSlots(PassportMode.VISA, false, false, false, false, visaRows, emptyList(), Grouping.CONTINENT, emptySet(), "")

    @Test
    fun `the visa index is counted on the stamped rows even when the screen last composed a filtered list`() {
        // The screen's captured list was the Easy-filtered one (EG only); the stamp has since
        // flipped to the full, settled list. The index must describe the stamped list.
        val seen = mutableListOf<List<VisaRow>>()
        val index = showCountryIndex(
            ShowCountryRequest("EG", PassportMode.VISA), PassportMode.VISA,
            StampedRows(emptyList()), StampedRows(full), Grouping.CONTINENT, emptySet(), "",
            sortTravel = { it },
            headerSlotsFor = { _, v -> seen += v; slots(v) },
        )
        assertEquals(8, index)
        assertEquals(listOf(full), seen)
    }

    @Test
    fun `a stamp that still carries the filters yields no index however the live filters read`() {
        val filtered = StampedRows(listOf(vrow("EG", VisaCategory.VISA_ON_ARRIVAL)), visaFilter = VisaFilter.Easy)
        assertNull(
            showCountryIndex(
                ShowCountryRequest("EG", PassportMode.VISA), PassportMode.VISA,
                StampedRows(emptyList()), filtered, Grouping.CONTINENT, emptySet(), "",
                sortTravel = { it }, headerSlotsFor = { _, v -> slots(v) },
            ),
        )
    }

    @Test
    fun `the travel index uses slots built from the same collapse set as the walk`() {
        fun slotsFor(collapsed: Set<String>) = { t: List<PlaceRow>, v: List<VisaRow> ->
            passportHeaderSlots(PassportMode.TRAVEL, false, false, false, false, v, t, Grouping.CONTINENT, collapsed, "")
        }
        // Europe collapsed: FR and DE are not emitted, EG follows Europe's header and Africa's header.
        val collapsed = setOf(europeKey)
        val index = showCountryIndex(
            ShowCountryRequest("EG", PassportMode.TRAVEL), PassportMode.TRAVEL,
            StampedRows(travel), StampedRows(emptyList()), Grouping.CONTINENT, collapsed, "",
            sortTravel = { it }, headerSlotsFor = slotsFor(collapsed),
        )
        // 3 leading (mode, stats, controls) + Europe header + Africa header = 5.
        assertEquals(5, index)
    }

    @Test
    fun `a request for the other mode than the screen shows has no index`() {
        assertNull(
            showCountryIndex(
                ShowCountryRequest("FR", PassportMode.VISA), PassportMode.TRAVEL,
                StampedRows(travel), StampedRows(full), Grouping.CONTINENT, emptySet(), "",
                sortTravel = { it }, headerSlotsFor = { _, v -> slots(v) },
            ),
        )
    }
}
