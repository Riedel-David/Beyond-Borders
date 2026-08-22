package com.riedel.aeroglobe.ui.map

import com.riedel.aeroglobe.ui.places.ContinentStat
import com.riedel.aeroglobe.ui.places.TravelStats
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
        assertEquals("2 of 252 · 0% of the world · 1 continents", mapShareCaption(stats))
    }
}
