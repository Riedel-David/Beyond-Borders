package com.riedel.beyondborders.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The numbers here are the heights and widths the layouts measure, not preferences — see
 * the comment on the constants in `WidgetTier.kt`. If a layout's content changes, re-measure
 * on a device and move these with the measurement rather than guessing.
 */
class WidgetTierTest {

    @Test
    fun `LARGE when both width and height meet thresholds`() {
        assertEquals(WidgetTier.LARGE, widgetTier(290, 261))
        assertEquals(WidgetTier.LARGE, widgetTier(320, 300))
    }

    @Test
    fun `MEDIUM when width meets threshold but height is below LARGE threshold`() {
        assertEquals(WidgetTier.MEDIUM, widgetTier(290, 260))
        assertEquals(WidgetTier.MEDIUM, widgetTier(180, 186))
    }

    @Test
    fun `MEDIUM when height meets threshold but width is below LARGE threshold`() {
        assertEquals(WidgetTier.MEDIUM, widgetTier(289, 261))
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
        assertEquals(WidgetTier.MEDIUM, widgetTier(180, 186))
        assertEquals(WidgetTier.COMPACT, widgetTier(179, 186))
        assertEquals(WidgetTier.COMPACT, widgetTier(180, 185))
    }

    @Test
    fun `LARGE boundary cases`() {
        assertEquals(WidgetTier.LARGE, widgetTier(290, 261))
        assertEquals(WidgetTier.MEDIUM, widgetTier(289, 261))
        assertEquals(WidgetTier.MEDIUM, widgetTier(290, 260))
    }

    @Test
    fun `a real One UI 3x2 is MEDIUM and a 4x2 is not LARGE`() {
        // Measured from the options bundle on a physical phone: One UI reports a 3x2 as
        // 220x202 dp and a 4x2 as roughly 300x202. The old thresholds made the 4x2 LARGE,
        // whose content needs 261 dp, so its three stat tiles were squeezed to no height
        // at all — the defect that set every number in this file.
        assertEquals(WidgetTier.MEDIUM, widgetTier(220, 202))
        assertEquals(WidgetTier.MEDIUM, widgetTier(300, 202))
        assertEquals(WidgetTier.LARGE, widgetTier(300, 304))
    }

    @Test
    fun `the progress bar is empty at nothing travelled and a dot at one percent`() {
        assertEquals(0, widgetProgressValue(0))
        assertEquals(0, widgetProgressValue(-1))
        assertEquals(2, widgetProgressValue(1))
        assertEquals(2, widgetProgressValue(2))
    }

    @Test
    fun `the progress bar passes through every percentage above the dot floor`() {
        assertEquals(3, widgetProgressValue(3))
        assertEquals(17, widgetProgressValue(17))
        assertEquals(100, widgetProgressValue(100))
    }
}
