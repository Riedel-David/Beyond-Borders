package com.riedel.beyondborders.widget

import com.riedel.beyondborders.ui.places.ContinentStat
import com.riedel.beyondborders.ui.places.TravelStats
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetMetricTest {

    private val stats = TravelStats(
        visited = 30,
        lived = 2,
        wishlist = 5,
        total = 252,
        continents = listOf(
            ContinentStat("Europe", been = 20, total = 50),
            ContinentStat("Asia", been = 0, total = 48),
        ),
    )

    @Test
    fun `countries metric reads been of total`() {
        val (headline, subline) = widgetText(WidgetMetric.COUNTRIES, stats)
        assertEquals("32 of 252", headline)
        assertEquals("12% of the world", subline)
    }

    @Test
    fun `percent metric reads the percentage alone`() {
        val (headline, subline) = widgetText(WidgetMetric.PERCENT, stats)
        assertEquals("12%", headline)
        assertEquals("of the world visited", subline)
    }

    @Test
    fun `continents metric counts continents touched`() {
        val (headline, subline) = widgetText(WidgetMetric.CONTINENTS, stats)
        assertEquals("1 of 2", headline)
        assertEquals("continents touched", subline)
    }

    @Test
    fun `unknown storage value falls back to the default metric`() {
        assertEquals(WidgetMetric.DEFAULT, WidgetMetric.fromStorage("nonsense"))
        assertEquals(WidgetMetric.DEFAULT, WidgetMetric.fromStorage(null))
    }
}
