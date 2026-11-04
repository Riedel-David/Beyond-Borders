package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.settings.MapHue
import com.riedel.beyondborders.settings.MapSlot
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusChipTest {

    @Test
    fun `the menu offers exactly the three statuses`() {
        assertEquals(
            listOf(StatusAction.BEEN, StatusAction.LIVED, StatusAction.WANT),
            StatusAction.entries.toList(),
        )
    }

    @Test
    fun `picking a different status writes exactly that status`() {
        assertEquals(PlaceStatus.LIVED, statusTargetFor(StatusAction.LIVED, PlaceStatus.VISITED))
        assertEquals(PlaceStatus.VISITED, statusTargetFor(StatusAction.BEEN, PlaceStatus.NONE))
    }

    @Test
    fun `picking the marked status again clears it to NONE`() {
        assertEquals(PlaceStatus.NONE, statusTargetFor(StatusAction.WANT, PlaceStatus.WISHLIST))
        assertEquals(PlaceStatus.NONE, statusTargetFor(StatusAction.BEEN, PlaceStatus.VISITED))
        assertEquals(PlaceStatus.NONE, statusTargetFor(StatusAction.LIVED, PlaceStatus.LIVED))
    }

    @Test
    fun `thumbnail sample size halves while the result stays at least the target`() {
        assertEquals(2, thumbSampleSize(300, 200, 120, 84))
        assertEquals(2, thumbSampleSize(300, 200, 100, 70))
        assertEquals(8, thumbSampleSize(1200, 800, 120, 84))
        assertEquals(1, thumbSampleSize(0, 0, 120, 84))
    }

    @Test
    fun `only the current status entry is marked and an unset row marks none`() {
        assertTrue(isCurrent(StatusAction.BEEN, PlaceStatus.VISITED))
        assertFalse(isCurrent(StatusAction.LIVED, PlaceStatus.VISITED))
        assertTrue(StatusAction.entries.none { isCurrent(it, PlaceStatus.NONE) })
    }

    @Test
    fun `chip label and colour follow the status and NONE has neither`() {
        assertEquals(R.string.passport_chip_been, statusLabelRes(PlaceStatus.VISITED))
        assertEquals(R.string.passport_chip_lived, statusLabelRes(PlaceStatus.LIVED))
        assertEquals(R.string.passport_chip_want, statusLabelRes(PlaceStatus.WISHLIST))
        assertEquals(MapSlot.WISHLIST, statusSlot(PlaceStatus.WISHLIST))
        assertNull(statusLabelRes(PlaceStatus.NONE))
        assertNull(statusSlot(PlaceStatus.NONE))
    }

    /**
     * WCAG AA asks 4.5:1 for normal text. Every selectable status color in both light and dark
     * themes must get a text color that meets this bar against its own fill.
     */
    @Test
    fun `text color chosen for every selectable status hue clears WCAG AA contrast`() {
        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (kotlin.math.max(la, lb) + 0.05) / (kotlin.math.min(la, lb) + 0.05)
        }
        MapHue.entries.forEach { hue ->
            listOf(false, true).forEach { dark ->
                val fill = (hue.argb(dark) and 0xFFFFFF or 0xFF000000).toInt()
                val text = textColorForFill(fill)
                val ratio = contrast(fill, text)
                val theme = if (dark) "dark" else "light"
                assertTrue(
                    "$hue in $theme: text contrast ${"%.2f".format(ratio)}:1 falls short of WCAG AA 4.5:1",
                    ratio >= 4.5
                )
            }
        }
    }

    @Test
    fun `text color returns packed ARGB with full alpha`() {
        MapHue.entries.forEach { hue ->
            listOf(false, true).forEach { dark ->
                val fill = (hue.argb(dark) and 0xFFFFFF or 0xFF000000).toInt()
                val text = textColorForFill(fill)
                val alpha = (text shr 24) and 0xFF
                assertEquals("Text color alpha byte must be 0xFF", 0xFF, alpha)
            }
        }
    }
}
