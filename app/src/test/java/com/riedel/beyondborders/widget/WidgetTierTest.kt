package com.riedel.beyondborders.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTierTest {

    @Test
    fun `LARGE when both width and height meet thresholds`() {
        assertEquals(WidgetTier.LARGE, widgetTier(250, 200))
        assertEquals(WidgetTier.LARGE, widgetTier(300, 250))
    }

    @Test
    fun `MEDIUM when width meets threshold but height is below LARGE threshold`() {
        assertEquals(WidgetTier.MEDIUM, widgetTier(250, 199))
        assertEquals(WidgetTier.MEDIUM, widgetTier(180, 110))
    }

    @Test
    fun `MEDIUM when height meets threshold but width is below LARGE threshold`() {
        assertEquals(WidgetTier.MEDIUM, widgetTier(249, 200))
    }

    @Test
    fun `COMPACT when width is below MEDIUM threshold`() {
        assertEquals(WidgetTier.COMPACT, widgetTier(179, 200))
        assertEquals(WidgetTier.COMPACT, widgetTier(100, 150))
    }

    @Test
    fun `COMPACT when height is below MEDIUM threshold`() {
        assertEquals(WidgetTier.COMPACT, widgetTier(250, 109))
        assertEquals(WidgetTier.COMPACT, widgetTier(200, 100))
    }

    @Test
    fun `COMPACT for degenerate sizes`() {
        assertEquals(WidgetTier.COMPACT, widgetTier(0, 0))
        assertEquals(WidgetTier.COMPACT, widgetTier(0, 200))
        assertEquals(WidgetTier.COMPACT, widgetTier(200, 0))
    }

    @Test
    fun `COMPACT for tall narrow widget`() {
        assertEquals(WidgetTier.COMPACT, widgetTier(100, 400))
    }

    @Test
    fun `MEDIUM boundary cases`() {
        assertEquals(WidgetTier.MEDIUM, widgetTier(180, 110))
        assertEquals(WidgetTier.COMPACT, widgetTier(179, 110))
        assertEquals(WidgetTier.COMPACT, widgetTier(180, 109))
    }

    @Test
    fun `LARGE boundary cases`() {
        assertEquals(WidgetTier.LARGE, widgetTier(250, 200))
        assertEquals(WidgetTier.MEDIUM, widgetTier(249, 200))
        assertEquals(WidgetTier.MEDIUM, widgetTier(250, 199))
    }
}
