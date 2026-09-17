package com.riedel.beyondborders.ui.map

import com.riedel.beyondborders.ui.places.ContinentStat
import com.riedel.beyondborders.ui.places.TravelStats
import org.junit.Assert.assertEquals
import org.junit.Test

class MapShareTest {

    @Test
    fun `the caption leads with been, not visited, because a lived-in place still counts`() {
        val stats = TravelStats(
            visited = 1,
            lived = 1,
            wishlist = 0,
            total = 252,
            continents = listOf(
                ContinentStat("Europe", been = 2, total = 50),
                ContinentStat("Africa", been = 0, total = 54),
            ),
        )
        assertEquals("2 of 252 · 0% of the world · 1 continent", mapShareCaption(stats))
    }

    @Test
    fun `continents is pluralized correctly`() {
        val oneContinent = TravelStats(
            visited = 1,
            lived = 0,
            wishlist = 0,
            total = 252,
            continents = listOf(
                ContinentStat("Europe", been = 1, total = 50),
                ContinentStat("Africa", been = 0, total = 54),
            ),
        )
        assertEquals("1 of 252 · 0% of the world · 1 continent", mapShareCaption(oneContinent))

        val twoContinents = TravelStats(
            visited = 2,
            lived = 0,
            wishlist = 0,
            total = 252,
            continents = listOf(
                ContinentStat("Europe", been = 1, total = 50),
                ContinentStat("Africa", been = 1, total = 54),
            ),
        )
        assertEquals("2 of 252 · 0% of the world · 2 continents", mapShareCaption(twoContinents))
    }

    @Test
    fun `stamp text is the short form with percentage and fraction`() {
        val stats = TravelStats(
            visited = 35,
            lived = 5,
            wishlist = 10,
            total = 252,
            continents = listOf(
                ContinentStat("Europe", been = 20, total = 50),
                ContinentStat("Asia", been = 10, total = 50),
                ContinentStat("Africa", been = 10, total = 54),
            ),
        )
        assertEquals("15% · 40/252", mapShareStamp(stats))
    }

    // mapShareStatsCard draws onto a real android.graphics.Bitmap/Canvas, which
    // this module's host-JVM tests have no mock for (no Robolectric, no
    // returnDefaultValues) — same reason withCaption and shareMapSnapshot have
    // no test above. Only the pure caption/stamp string builders are covered.
}
