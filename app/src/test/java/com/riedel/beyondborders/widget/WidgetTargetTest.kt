package com.riedel.beyondborders.widget

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class WidgetTargetTest {

    @Test
    fun `a widget with no stored target keeps the destination its metric had before the choice existed`() {
        assertEquals(WidgetTarget.MAP, WidgetTarget.defaultFor(WidgetMetric.COUNTRIES))
        assertEquals(WidgetTarget.STATS, WidgetTarget.defaultFor(WidgetMetric.PERCENT))
        assertEquals(WidgetTarget.STATS, WidgetTarget.defaultFor(WidgetMetric.CONTINENTS))
    }

    @Test
    fun `every target survives a round trip through its storage value`() {
        WidgetTarget.entries.forEach {
            assertEquals(it, WidgetTarget.fromStorage(it.storageValue))
        }
    }

    @Test
    fun `an unknown or missing storage value is null so the metric default applies`() {
        assertNull(WidgetTarget.fromStorage(null))
        assertNull(WidgetTarget.fromStorage("globe"))
    }
}
