package com.riedel.beyondborders.ui.passport

import androidx.compose.ui.graphics.toArgb
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.ui.theme.ColorMath.contrast
import com.riedel.beyondborders.ui.theme.ColorMath.rgb
import com.riedel.beyondborders.ui.theme.mapPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text drawn over a status fill is held to WCAG AA text contrast (4.5:1), in all three
 * looks the app has: light, dark and pure black. Pure black swaps the ocean, land and
 * border but not the hues, so it is listed on its own to catch the day that changes.
 */
class FillTextContrastTest {

    private val textFloor = 4.5

    private val themes = listOf(
        Triple("light", false, false),
        Triple("dark", true, false),
        Triple("pure black", true, true),
    )

    private fun textOver(fillArgb: Int): Long = textColorForFill(fillArgb).toLong() and 0xFFFFFF

    @Test
    fun `chip text clears 4_5 to 1 over every status fill in every theme`() {
        themes.forEach { (name, dark, pureBlack) ->
            val palette = mapPalette(dark, MapColors.DEFAULT, pureBlack = pureBlack)
            mapOf("visited" to palette.visited, "lived" to palette.lived, "wishlist" to palette.wishlist)
                .forEach { (slot, fill) ->
                    val ratio = contrast(textOver(fill.toArgb()), fill.rgb())
                    assertTrue("$slot in $name: ${"%.2f".format(ratio)}:1", ratio >= textFloor)
                }
        }
    }

    @Test
    fun `chip text clears 4_5 to 1 over every visa band fill if text is ever drawn on one`() {
        // No text sits on a band today (the legend and list put the label beside the
        // swatch); this holds the bar for the day one does.
        themes.forEach { (name, dark, pureBlack) ->
            val ramp = mapPalette(dark, MapColors.DEFAULT, pureBlack = pureBlack).visa
            VisaBand.entries.map { it.name to ramp.colorFor(it) }
                .plus("NO_DATA" to ramp.noData)
                .forEach { (label, fill) ->
                    val ratio = contrast(textOver(fill.toArgb()), fill.rgb())
                    assertTrue("$label in $name: ${"%.2f".format(ratio)}:1", ratio >= textFloor)
                }
        }
    }
}
