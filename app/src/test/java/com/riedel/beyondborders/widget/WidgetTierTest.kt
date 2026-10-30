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
    fun `WIDE when wide enough for LARGE but too short for it`() {
        assertEquals(WidgetTier.WIDE, widgetTier(290, 260))
        assertEquals(WidgetTier.WIDE, widgetTier(290, 186))
        assertEquals(WidgetTier.WIDE, widgetTier(220, 313))
        assertEquals(WidgetTier.WIDE, widgetTier(205, 186))
        assertEquals(WidgetTier.MEDIUM, widgetTier(204, 186))
    }

    @Test
    fun `MEDIUM when height meets threshold but width is below WIDE`() {
        assertEquals(WidgetTier.MEDIUM, widgetTier(180, 186))
        assertEquals(WidgetTier.WIDE, widgetTier(289, 260))
    }

    @Test
    fun `MEDIUM when height meets threshold but width is below LARGE threshold`() {
        assertEquals(WidgetTier.WIDE, widgetTier(289, 261))
    }

    @Test
    fun `COMPACT when width is below MEDIUM threshold`() {
        assertEquals(WidgetTier.NAMED, widgetTier(179, 200))
        assertEquals(WidgetTier.NAMED, widgetTier(100, 150))
    }

    @Test
    fun `NAMED for a narrow widget tall enough to carry the wordmark`() {
        assertEquals(WidgetTier.NAMED, widgetTier(125, 190))
        assertEquals(WidgetTier.NAMED, widgetTier(179, 130))
        assertEquals(WidgetTier.COMPACT, widgetTier(125, 129))
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
    fun `a tall narrow widget is NAMED, not COMPACT`() {
        assertEquals(WidgetTier.NAMED, widgetTier(100, 400))
    }

    @Test
    fun `MEDIUM boundary cases`() {
        assertEquals(WidgetTier.MEDIUM, widgetTier(180, 186))
        assertEquals(WidgetTier.NAMED, widgetTier(179, 186))
        assertEquals(WidgetTier.NAMED, widgetTier(180, 185))
    }

    @Test
    fun `LARGE boundary cases`() {
        assertEquals(WidgetTier.LARGE, widgetTier(290, 261))
        assertEquals(WidgetTier.WIDE, widgetTier(289, 261))
        assertEquals(WidgetTier.WIDE, widgetTier(290, 260))
    }

    @Test
    fun `a real HyperOS 3x2 is STRIP, which the tile layouts are too tall for`() {
        // Measured from the options bundle on a Xiaomi 22101316UG: the two placed widgets
        // report 193x132 and 209x148 dp. STRIP's layout measures 111 dp tall.
        assertEquals(WidgetTier.STRIP, widgetTier(193, 132))
        assertEquals(WidgetTier.STRIP, widgetTier(209, 148))
    }

    @Test
    fun `STRIP boundary cases`() {
        assertEquals(WidgetTier.STRIP, widgetTier(190, 120))
        assertEquals(WidgetTier.NAMED, widgetTier(189, 130))
        assertEquals(WidgetTier.COMPACT, widgetTier(190, 119))
        assertEquals(WidgetTier.MEDIUM, widgetTier(190, 186))
        assertEquals(WidgetTier.STRIP, widgetTier(290, 185))
        assertEquals(WidgetTier.STRIP, widgetTier(205, 185))
        assertEquals(WidgetTier.STRIP, widgetTier(204, 185))
    }

    @Test
    fun `a real One UI 3x2 and 4x2 are both WIDE rather than LARGE`() {
        // Measured from the options bundle on a physical phone: One UI reports a 3x2 as
        // 220x202 dp and a 4x2 as roughly 300x202. The old thresholds made the 4x2 LARGE,
        // whose content needs 261 dp, so its three stat tiles were squeezed to no height
        // at all — the defect that set every number in this file.
        assertEquals(WidgetTier.WIDE, widgetTier(220, 202))
        assertEquals(WidgetTier.WIDE, widgetTier(300, 202))
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
