package com.riedel.beyondborders.ui.theme

import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.band
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.ui.theme.ColorMath.Cvd
import com.riedel.beyondborders.ui.theme.ColorMath.contrast
import com.riedel.beyondborders.ui.theme.ColorMath.rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The visa lens paints a *categorical* set now: six named hues, one per band, with
 * no order between them for a colour to carry.
 *
 * That makes the accessibility bar pairwise rather than neighbour-to-neighbour — every
 * one of the fifteen pairs has to stay visible and stay apart, not just each band and the
 * one next to it in a sequence. It is also the harder bar to clear on purpose: this
 * palette includes green and red together, which the single-hue ramp this replaced
 * ruled out for the exact reason `no two bands collapse under dichromacy simulation`
 * below exists — a green and a red at the *same* lightness collapse under simulated
 * deuteranopia. The fix kept here is that green and red sit at opposite ends of the
 * lightness range that still clears 3:1 against land, not at the same mid-tone, and
 * this class is the record of that measurement rather than a promise about it.
 */
class VisaRampTest {

    /** WCAG 2.1 SC 1.4.11: 3:1 for meaningful non-text content. */
    private val floorAgainstBackground = 3.0

    /**
     * Between *any* two bands after dichromacy simulation. Lower than the sighted eye
     * would need, because simulation compresses everything — demanding more here would
     * reject palettes that are perfectly readable to someone without the condition.
     */
    private val floorUnderCvd = 1.15

    private fun ramp(dark: Boolean) = mapPalette(dark, MapColors.DEFAULT).visa

    private fun bands(dark: Boolean): List<Pair<VisaBand, Long>> =
        VisaBand.entries.map { it to ramp(dark).colorFor(it).rgb() }

    private fun themes() = listOf(false, true)

    private fun name(dark: Boolean) = if (dark) "dark" else "light"

    private fun <T> Iterable<T>.pairs(): List<Pair<T, T>> {
        val list = toList()
        return list.indices.flatMap { i -> list.drop(i + 1).map { list[i] to it } }
    }

    // --- visible against the map ---------------------------------------------

    @Test
    fun `every band clears 3 to 1 against unmarked land and ocean in both themes`() {
        MapBase.entries.forEach { base ->
            themes().forEach { dark ->
                val palette = mapPalette(dark, MapColors.DEFAULT, base)
                val land = palette.unvisited.rgb()
                val ocean = palette.ocean.rgb()
                bands(dark).forEach { (band, fill) ->
                    assertTrue(
                        "$band vs $base land in ${name(dark)}: ${"%.2f".format(contrast(fill, land))}:1",
                        contrast(fill, land) >= floorAgainstBackground,
                    )
                    assertTrue(
                        "$band vs $base ocean in ${name(dark)}: ${"%.2f".format(contrast(fill, ocean))}:1",
                        contrast(fill, ocean) >= floorAgainstBackground,
                    )
                }
            }
        }
    }

    /**
     * [VisaRamp.noData] is not a [VisaBand] — it paints places absent from the
     * dataset entirely, not a sixth category — so [bands] never exercises it. Same
     * bar as every band gets, checked separately.
     */
    @Test
    fun `no-data grey clears 3 to 1 against unmarked land and ocean in both themes`() {
        MapBase.entries.forEach { base ->
            themes().forEach { dark ->
                val palette = mapPalette(dark, MapColors.DEFAULT, base)
                val land = palette.unvisited.rgb()
                val ocean = palette.ocean.rgb()
                val noData = palette.visa.noData.rgb()
                assertTrue(
                    "no-data vs $base land in ${name(dark)}: ${"%.2f".format(contrast(noData, land))}:1",
                    contrast(noData, land) >= floorAgainstBackground,
                )
                assertTrue(
                    "no-data vs $base ocean in ${name(dark)}: ${"%.2f".format(contrast(noData, ocean))}:1",
                    contrast(noData, ocean) >= floorAgainstBackground,
                )
            }
        }
    }

    /**
     * Both greys mean something different — "no answer" versus "closed" — so a
     * traveller squinting at the legend needs to be able to tell them apart, not
     * just tell either one apart from the land underneath it.
     */
    @Test
    fun `no-data grey stays distinguishable from the closed band under dichromacy simulation`() {
        themes().forEach { dark ->
            val noData = ramp(dark).noData.rgb()
            val closed = ramp(dark).closed.rgb()
            Cvd.entries.forEach { cvd ->
                val seen = contrast(cvd.simulate(noData), cvd.simulate(closed))
                assertTrue(
                    "no-data and closed are ${"%.2f".format(seen)}:1 apart under " +
                        "${cvd.label} in ${name(dark)}",
                    seen >= floorUnderCvd,
                )
            }
        }
    }

    @Test
    fun `the no-data grey is a different value in light and dark`() {
        assertNotEquals(ramp(dark = false).noData, ramp(dark = true).noData)
    }

    // --- legible without colour vision ---------------------------------------

    /**
     * The test that a single-hue ramp never had to pass. A green and a red at the
     * same lightness would collapse under simulated deuteranopia, so this is what
     * proves the lightness split between them (and every other pair) is enough.
     */
    @Test
    fun `no two bands collapse under dichromacy simulation`() {
        themes().forEach { dark ->
            val fills = bands(dark)
            Cvd.entries.forEach { cvd ->
                fills.pairs().forEach { (a, b) ->
                    val (aBand, aFill) = a
                    val (bBand, bFill) = b
                    val seen = contrast(cvd.simulate(aFill), cvd.simulate(bFill))
                    assertTrue(
                        "$aBand and $bBand are ${"%.2f".format(seen)}:1 apart under " +
                            "${cvd.label} in ${name(dark)}",
                        seen >= floorUnderCvd,
                    )
                }
            }
        }
    }

    // --- the lens' own selection outline -------------------------------------

    /**
     * Why the ramp carries an outline colour at all: [MapPalette.selectedOutline] was
     * chosen against the status fills, not this set, so tapping a place under any band
     * has to still show an outline rather than reading as a broken tap.
     */
    @Test
    fun `the lens outline is visible on every band`() {
        themes().forEach { dark ->
            val outline = ramp(dark).selectedOutline.rgb()
            val casing = ramp(dark).selectedCasing.rgb()
            bands(dark).forEach { (band, fill) ->
                // The core line alone may vanish on an extreme band; the casing carries it.
                val seen = maxOf(contrast(outline, fill), contrast(casing, fill))
                assertTrue(
                    "lens outline on $band in ${name(dark)}: ${"%.2f".format(seen)}:1",
                    seen >= floorAgainstBackground,
                )
            }
        }
    }

    @Test
    fun `the ramp is a different set of values in light and dark`() {
        VisaBand.entries.forEach { band ->
            assertNotEquals(
                "$band is the same colour in both themes",
                ramp(dark = false).colorFor(band),
                ramp(dark = true).colorFor(band),
            )
        }
    }

    // --- the mapping the ramp paints -----------------------------------------

    @Test
    fun `every band is something a category actually maps to`() {
        // A band with no categories would spend a sixth of the palette saying
        // nothing, and there are only six bands to give.
        assertEquals(
            VisaBand.entries.toSet(),
            VisaCategory.entries.map { it.band }.toSet(),
        )
    }

    @Test
    fun `every category maps to exactly one band`() {
        VisaCategory.entries.forEach { category ->
            // .band is a plain val getter, so this is really asserting it never
            // throws for any category — but calling it out here means a future
            // VisaCategory addition without a matching branch fails a test instead
            // of a `when` at runtime.
            assertTrue(VisaBand.entries.contains(category.band))
        }
    }
}
