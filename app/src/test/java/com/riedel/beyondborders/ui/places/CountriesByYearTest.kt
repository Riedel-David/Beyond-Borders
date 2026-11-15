package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.place.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.riedel.beyondborders.ui.passport.labelStep
import com.riedel.beyondborders.ui.passport.niceAxis
import org.junit.Test

class CountriesByYearTest {

    private var n = 0
    private fun v(code: String, year: Int?) = Visit("v${n++}", code, year)
    private val been = PlaceStatus.VISITED

    private fun chart(vararg visits: Visit, statuses: Map<String, PlaceStatus>? = null): List<YearPoint> {
        val grouped = visits.groupBy { it.code }
        return computeCountriesByYear(grouped, statuses ?: grouped.keys.associateWith { been })
    }

    @Test
    fun `a place visited twice in one year counts once for that year`() {
        val p = chart(v("FR", 2020), v("FR", 2020)).single()
        assertEquals(1, p.countries)
    }

    @Test
    fun `years with no visits appear with zero and the cumulative total carried forward`() {
        val pts = chart(v("FR", 2018), v("DE", 2021))
        assertEquals(listOf(2018, 2019, 2020, 2021), pts.map { it.year })
        assertEquals(listOf(1, 0, 0, 1), pts.map { it.countries })
        assertEquals(listOf(1, 1, 1, 2), pts.map { it.cumulative })
    }

    @Test
    fun `a place's first dated year is the one it is counted as new in`() {
        val pts = chart(v("FR", 2019), v("FR", 2021), v("DE", 2021))
        assertEquals(listOf(1, 0, 1), pts.map { it.newCountries })
        assertEquals(listOf(1, 0, 2), pts.map { it.countries })
        assertEquals(listOf(1, 1, 2), pts.map { it.cumulative })
    }

    @Test
    fun `places with only undated visits do not appear in any year`() {
        val pts = chart(v("FR", 2020), v("DE", null))
        assertEquals(1, pts.size)
        assertEquals(1, pts.single().cumulative)
    }

    @Test
    fun `visits of places no longer marked been are ignored`() {
        val pts = chart(v("FR", 2020), v("DE", 2020), statuses = mapOf("FR" to been, "DE" to PlaceStatus.WISHLIST))
        assertEquals(1, pts.single().countries)
    }

    @Test
    fun `a single year gives one point whose cumulative equals its country count`() {
        val p = chart(v("FR", 2020), v("DE", 2020), v("IT", 2020)).single()
        assertEquals(3, p.countries)
        assertEquals(3, p.cumulative)
    }

    @Test
    fun `no dated visits gives an empty list`() {
        assertTrue(chart(v("FR", null)).isEmpty())
        assertTrue(computeCountriesByYear(emptyMap(), emptyMap()).isEmpty())
    }

    @Test
    fun `the chart is capped at the most recent 100 years and the cumulative keeps earlier places`() {
        val pts = chart(v("FR", 1900), v("DE", 2020))
        assertEquals(100, pts.size)
        assertEquals(1921, pts.first().year)
        assertEquals(1, pts.first().cumulative)
        assertEquals(2, pts.last().cumulative)
    }

    @Test
    fun `been places without a dated visit are counted for the footnote`() {
        val visits = mapOf("FR" to listOf(v("FR", 2020)), "DE" to listOf(v("DE", null)))
        val statuses = mapOf("FR" to been, "DE" to been, "IT" to been, "ES" to PlaceStatus.WISHLIST)
        assertEquals(2, countUndatedBeenPlaces(visits, statuses))
    }

    @Test
    fun `the chart axis starts at zero and tops out on a round number with at most four gridlines`() {
        assertEquals(listOf(0, 1), niceAxis(1).ticks())
        assertEquals(listOf(0, 1, 2, 3, 4), niceAxis(4).ticks())
        assertEquals(listOf(0, 5, 10), niceAxis(9).ticks())
        assertEquals(60, niceAxis(57).top)
        assertTrue(niceAxis(1234).ticks().size <= 5)
    }

    @Test
    fun `x labels are thinned out until they no longer overlap`() {
        assertEquals(1, labelStep(100f, 40f))
        assertEquals(3, labelStep(20f, 50f))
    }
}
