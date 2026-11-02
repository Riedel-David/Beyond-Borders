package com.riedel.beyondborders.ui.map

import com.riedel.beyondborders.ui.places.RegionMode
import com.riedel.beyondborders.ui.places.TravelStats
import org.junit.Assert.assertEquals
import org.junit.Test

class MapChromeTest {

    @Test
    fun `no mode control is selected when nothing is on`() {
        assertEquals(emptySet<MapControl>(), selectedMapControls(false, false, true, RegionMode.OFF))
    }

    @Test
    fun `each mode control is selected by its own state`() {
        assertEquals(setOf(MapControl.SEARCH), selectedMapControls(true, false, true, RegionMode.OFF))
        assertEquals(setOf(MapControl.VISA), selectedMapControls(false, true, true, RegionMode.OFF))
        assertEquals(setOf(MapControl.REGIONS), selectedMapControls(false, false, true, RegionMode.OFFICIAL))
        assertEquals(setOf(MapControl.REGIONS), selectedMapControls(false, false, true, RegionMode.INFORMAL))
    }

    @Test
    fun `the visa lens is not highlighted when the visa item is not offered`() {
        assertEquals(emptySet<MapControl>(), selectedMapControls(false, true, false, RegionMode.OFF))
    }

    @Test
    fun `controls can be selected together`() {
        assertEquals(MapControl.entries.toSet(), selectedMapControls(true, true, true, RegionMode.INFORMAL))
    }

    private fun stats(visited: Int, lived: Int = 0, wishlist: Int = 0) = TravelStats(
        visited = visited, lived = lived, wishlist = wishlist, total = 252, continents = emptyList(),
    )

    @Test
    fun `the legend always lists visited and hides lived and wanted at zero`() {
        assertEquals(listOf(LegendEntry(LegendStatus.VISITED, 0)), legendEntries(stats(0)))
    }

    @Test
    fun `the legend lists lived and wanted with their own counts once present`() {
        assertEquals(
            listOf(
                LegendEntry(LegendStatus.VISITED, 40),
                LegendEntry(LegendStatus.LIVED, 2),
                LegendEntry(LegendStatus.WANTED, 7),
            ),
            legendEntries(stats(40, lived = 2, wishlist = 7)),
        )
    }
}
