package com.riedel.beyondborders.ui.map

import com.riedel.beyondborders.ui.places.RegionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MapLegendKindTest {

    @Test
    fun `the visa lens owns the legend even while the region heatmap is on`() {
        assertEquals(
            MapLegendKind.VISA_DOTS,
            kind(lens = true, regionMode = RegionMode.INFORMAL),
        )
    }

    @Test
    fun `the region heatmap outranks the travel counter`() {
        assertEquals(
            MapLegendKind.REGION_OFFICIAL,
            kind(regionMode = RegionMode.OFFICIAL, counterLabeled = true, been = 40),
        )
        assertEquals(
            MapLegendKind.REGION_INFORMAL,
            kind(regionMode = RegionMode.INFORMAL, been = 40),
        )
    }

    @Test
    fun `an untouched map nudges instead of counting to zero`() {
        assertEquals(MapLegendKind.NUDGE, kind(been = 0, wishlist = 0))
        // One wanted place is enough to have something to count.
        assertEquals(MapLegendKind.COUNTER_DOTS, kind(been = 0, wishlist = 1))
    }

    @Test
    fun `each labelled toggle only names its own legend`() {
        assertEquals(MapLegendKind.VISA_LABELED, kind(lens = true, visaLabeled = true))
        // The counter's toggle has no say while the lens is on, and the lens' toggle
        // none while it is off — two separate flags, one pill.
        assertEquals(MapLegendKind.COUNTER_DOTS, kind(been = 40, visaLabeled = true))
        assertEquals(MapLegendKind.COUNTER_LABELED, kind(been = 40, counterLabeled = true))
    }

    private fun kind(
        lens: Boolean = false,
        visaLabeled: Boolean = false,
        regionMode: RegionMode = RegionMode.OFF,
        counterLabeled: Boolean = false,
        been: Int = 40,
        wishlist: Int = 0,
    ) = mapLegendKind(lens, visaLabeled, regionMode, counterLabeled, been, wishlist)
}
