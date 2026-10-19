package com.riedel.beyondborders.ui.theme

import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.ui.places.RegionBand
import com.riedel.beyondborders.ui.theme.ColorMath.Cvd
import com.riedel.beyondborders.ui.theme.ColorMath.contrast
import com.riedel.beyondborders.ui.theme.ColorMath.luminance
import com.riedel.beyondborders.ui.theme.ColorMath.rgb
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The region heatmap's ramp is held to the same bars as [VisaRampTest], for the
 * same reason: it paints an ordered scale (a region's own completion), not an
 * unordered category, so the checks that matter are "visible", "legible as a
 * sequence" and "legible without colour vision".
 *
 * Unlike [VisaRamp], this ramp is not one hue — a single-hue version shipped first
 * and turned out illegible band-to-band on an actual screen. Changing hue per step
 * reopens exactly the dichromacy risk [VisaRampTest] rejected hue-ordering for, so
 * `no two bands collapse under dichromacy simulation` below is doing more work here
 * than it does there: it is what proves this particular two-hue-pair split is safe,
 * not a formality inherited from the visa ramp's shape.
 */
class RegionRampTest {

    private val floorAgainstBackground = 3.0
    private val floorBetweenNeighbours = 1.35
    private val floorUnderCvd = 1.15

    private fun ramp(dark: Boolean) = mapPalette(dark, MapColors.DEFAULT).region

    private fun bands(dark: Boolean): List<Pair<RegionBand, Long>> =
        RegionBand.entries.map { it to ramp(dark).colorFor(it).rgb() }

    private fun themes() = listOf(false, true)

    private fun name(dark: Boolean) = if (dark) "dark" else "light"

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

    @Test
    fun `neighbouring bands stay apart`() {
        themes().forEach { dark ->
            bands(dark).zipWithNext { (aBand, a), (bBand, b) ->
                assertTrue(
                    "$aBand to $bBand in ${name(dark)}: ${"%.2f".format(contrast(a, b))}:1",
                    contrast(a, b) >= floorBetweenNeighbours,
                )
            }
        }
    }

    @Test
    fun `the ramp runs in one direction`() {
        themes().forEach { dark ->
            val luminances = bands(dark).map { luminance(it.second) }
            val ascending = luminances.zipWithNext().all { (a, b) -> b > a }
            val descending = luminances.zipWithNext().all { (a, b) -> b < a }
            assertTrue(
                "${name(dark)} ramp is not monotonic: " +
                    luminances.joinToString { "%.4f".format(it) },
                ascending || descending,
            )
        }
    }

    /**
     * The design decision, asserted: completion is what intensity means, so DONE
     * must stand out more against the map than NONE, in both themes.
     */
    @Test
    fun `more complete stands out more, in both themes`() {
        themes().forEach { dark ->
            val palette = mapPalette(dark, MapColors.DEFAULT)
            val land = palette.unvisited.rgb()
            val byBand = bands(dark).associate { (band, fill) -> band to contrast(fill, land) }
            RegionBand.entries.zipWithNext { lessDone, moreDone ->
                assertTrue(
                    "$moreDone should stand out more than $lessDone in ${name(dark)}: " +
                        "${"%.2f".format(byBand.getValue(moreDone))} vs " +
                        "${"%.2f".format(byBand.getValue(lessDone))}",
                    byBand.getValue(moreDone) > byBand.getValue(lessDone),
                )
            }
        }
    }

    @Test
    fun `no two bands collapse under dichromacy simulation`() {
        themes().forEach { dark ->
            val fills = bands(dark)
            Cvd.entries.forEach { cvd ->
                fills.forEachIndexed { i, (aBand, a) ->
                    fills.drop(i + 1).forEach { (bBand, b) ->
                        val seen = contrast(cvd.simulate(a), cvd.simulate(b))
                        assertTrue(
                            "$aBand and $bBand are ${"%.2f".format(seen)}:1 apart under " +
                                "${cvd.label} in ${name(dark)}",
                            seen >= floorUnderCvd,
                        )
                    }
                }
            }
        }
    }

    /**
     * Why the ramp carries an outline colour at all: [MapPalette.selectedOutline]
     * was chosen against the status fills and against [VisaRamp]'s violet, not
     * against amber.
     */
    @Test
    fun `the heatmap outline is visible on every band`() {
        themes().forEach { dark ->
            val outline = ramp(dark).selectedOutline.rgb()
            val casing = ramp(dark).selectedCasing.rgb()
            bands(dark).forEach { (band, fill) ->
                // The core line alone may vanish on the extreme bands; the casing
                // under it must then carry the edge.
                val seen = maxOf(contrast(outline, fill), contrast(casing, fill))
                assertTrue(
                    "heatmap outline on $band in ${name(dark)}: ${"%.2f".format(seen)}:1",
                    seen >= floorAgainstBackground,
                )
            }
        }
    }

    @Test
    fun `the outline is black in light mode and white in dark, with the opposite casing`() {
        val light = ramp(false)
        val dark = ramp(true)
        assertTrue(luminance(light.selectedOutline.rgb()) < 0.05)
        assertTrue(luminance(dark.selectedOutline.rgb()) > 0.9)
        assertTrue(contrast(light.selectedOutline.rgb(), light.selectedCasing.rgb()) >= 10.0)
        assertTrue(contrast(dark.selectedOutline.rgb(), dark.selectedCasing.rgb()) >= 10.0)
    }

    @Test
    fun `the ramp is a different set of values in light and dark`() {
        RegionBand.entries.forEach { band ->
            assertNotEquals(
                "$band is the same colour in both themes",
                ramp(dark = false).colorFor(band),
                ramp(dark = true).colorFor(band),
            )
        }
    }
}
