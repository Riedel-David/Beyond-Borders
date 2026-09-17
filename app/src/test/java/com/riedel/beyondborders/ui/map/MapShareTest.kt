package com.riedel.beyondborders.ui.map

import com.riedel.beyondborders.ui.places.ContinentStat
import com.riedel.beyondborders.ui.places.TravelStats
import org.junit.Assert.assertEquals
import org.junit.Test

class MapShareTest {

    @Test
    fun `the detail line leads with been, not visited, because a lived-in place still counts`() {
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
        assertEquals("2 of 252 · 1 continent", mapShareDetail(stats))
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
        assertEquals("1 of 252 · 1 continent", mapShareDetail(oneContinent))

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
        assertEquals("2 of 252 · 2 continents", mapShareDetail(twoContinents))
    }

    @Test
    fun `stamp text is the short form with percentage and fraction`() {
        assertEquals("15% · 40/252", mapShareStamp(statsWith(visited = 35, lived = 5, wishlist = 10)))
    }

    @Test
    fun `the third stat tile prefers somewhere lived over the wish list`() {
        val lived = statsWith(visited = 35, lived = 5, wishlist = 10)
        assertEquals(listOf("40" to "PLACES BEEN", "3" to "CONTINENTS", "5" to "LIVED IN"), shareStatTiles(lived))

        val neverLived = statsWith(visited = 40, lived = 0, wishlist = 10)
        assertEquals(
            listOf("40" to "PLACES BEEN", "3" to "CONTINENTS", "10" to "WISH LIST"),
            shareStatTiles(neverLived),
        )
    }

    private fun statsWith(visited: Int, lived: Int, wishlist: Int) = TravelStats(
        visited = visited,
        lived = lived,
        wishlist = wishlist,
        total = 252,
        continents = listOf(
            ContinentStat("Europe", been = 20, total = 50),
            ContinentStat("Asia", been = 10, total = 50),
            ContinentStat("Africa", been = 10, total = 54),
        ),
    )

    // mapShareStatsCard and the two map overlays draw onto a real
    // android.graphics.Bitmap/Canvas, which this module's host-JVM tests have no
    // mock for (no Robolectric, no returnDefaultValues). Only the pure text and
    // tile builders are covered.
}
