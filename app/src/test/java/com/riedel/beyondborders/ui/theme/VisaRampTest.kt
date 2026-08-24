package com.riedel.beyondborders.ui.theme

import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.band
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.ui.theme.ColorMath.Cvd
import com.riedel.beyondborders.ui.theme.ColorMath.contrast
import com.riedel.beyondborders.ui.theme.ColorMath.luminance
import com.riedel.beyondborders.ui.theme.ColorMath.rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The visa lens paints an *ordered* scale, which is a stricter thing to get right than
 * the four unordered map statuses.
 *
 * MapColorsTest holds the status hues 40° apart because those answers have no order and
 * hue is the only thing telling them apart. Here order is the message, so the ramp has
 * to be legible *as a sequence*: every step visible against the map, each step clearly
 * different from its neighbours, in one consistent direction, and still all of that for
 * someone who cannot see one of the three primaries. That last requirement is the one
 * that decided the design — it is why the ramp is one violet at four intensities rather
 * than a path through several hues, and why there are four bands and not eight.
 *
 * These numbers came out of a search, so this class is not decoration: it is the record
 * of what the search was searching for.
 */
class VisaRampTest {

    /** WCAG 2.1 SC 1.4.11: 3:1 for meaningful non-text content. */
    private val floorAgainstBackground = 3.0

    /**
     * Between neighbouring bands. Well above the 1.0 that "distinguishable" would
     * literally require, and just under the 1.46 the dark theme's usable range allows
     * across four steps — the dark background sets that ceiling, because a fill has to
     * stay above 3:1 against dark land and below white.
     */
    private val floorBetweenNeighbours = 1.40

    /**
     * Between *any* two bands after dichromacy simulation. Lower than the sighted floor
     * on purpose: simulation compresses everything, so demanding the full 1.40 here
     * would reject ramps that are perfectly readable.
     */
    private val floorUnderCvd = 1.15

    private fun ramp(dark: Boolean) = mapPalette(dark, MapColors.DEFAULT).visa

    private fun bands(dark: Boolean): List<Pair<VisaBand, Long>> =
        VisaBand.entries.map { it to ramp(dark).colorFor(it).rgb() }

    private fun themes() = listOf(false, true)

    private fun name(dark: Boolean) = if (dark) "dark" else "light"

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

    // --- legible as a sequence -----------------------------------------------

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
     * The design decision, asserted. Intensity encodes *openness*, not difficulty: the
     * places the passport opens are the strongest thing on the map and the closed ones
     * recede into the background. Stated as contrast rather than as lightness so it is
     * the same claim in both themes, whose backgrounds are inverted.
     */
    @Test
    fun `openness is what intensity means, in both themes`() {
        themes().forEach { dark ->
            val palette = mapPalette(dark, MapColors.DEFAULT)
            val land = palette.unvisited.rgb()
            val byBand = bands(dark).associate { (band, fill) -> band to contrast(fill, land) }
            VisaBand.entries.zipWithNext { open, closed ->
                assertTrue(
                    "$open should stand out more than $closed in ${name(dark)}: " +
                        "${"%.2f".format(byBand.getValue(open))} vs " +
                        "${"%.2f".format(byBand.getValue(closed))}",
                    byBand.getValue(open) > byBand.getValue(closed),
                )
            }
        }
    }

    // --- legible without colour vision ---------------------------------------

    /**
     * The test that ruled out the alternatives. A blue-to-red hue path over six bands
     * passed every check above and still collapsed here — two bands rendering as the
     * same colour for a red-green dichromat — which is what a hue-ordered ramp does
     * once the hue is gone. A single-hue ramp has only intensity to lose, and intensity
     * is what dichromacy leaves alone.
     */
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

    // --- the lens' own selection outline -------------------------------------

    /**
     * Why the ramp carries an outline colour at all: [MapPalette.selectedOutline] was
     * chosen against the status fills, and on the lens' deepest violet the light one is
     * a near-invisible navy. Tapping a country you can travel to freely and seeing no
     * outline would read as a broken tap.
     */
    @Test
    fun `the lens outline is visible on every band`() {
        themes().forEach { dark ->
            val outline = ramp(dark).selectedOutline.rgb()
            bands(dark).forEach { (band, fill) ->
                assertTrue(
                    "lens outline on $band in ${name(dark)}: " +
                        "${"%.2f".format(contrast(outline, fill))}:1",
                    contrast(outline, fill) >= floorAgainstBackground,
                )
            }
        }
    }

    @Test
    fun `the travel outline would not have done`() {
        // Not a hypothetical: this is the measurement that added VisaRamp.selectedOutline,
        // kept as a test so a later "simplification" that drops it fails loudly.
        themes().forEach { dark ->
            val travel = mapPalette(dark, MapColors.DEFAULT).selectedOutline.rgb()
            val worst = bands(dark).minOf { (_, fill) -> contrast(travel, fill) }
            assertTrue(
                "the travel outline is unexpectedly fine in ${name(dark)} " +
                    "(${"%.2f".format(worst)}:1) — if that is now true, this test and " +
                    "VisaRamp.selectedOutline can both go",
                worst < floorAgainstBackground,
            )
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

    /**
     * [VisaCategory] is ordered easiest-first and so is [VisaBand], so the mapping
     * between them must never go backwards. If it did, the list and the map would
     * disagree about which of two places is the easier to reach — with the list sorted
     * one way and the map shaded the other.
     */
    @Test
    fun `band order agrees with category order`() {
        VisaCategory.entries.zipWithNext { earlier, later ->
            assertTrue(
                "${earlier.name} maps to ${earlier.band} but ${later.name} maps to " +
                    "${later.band}, which is earlier in the ramp",
                earlier.band.ordinal <= later.band.ordinal,
            )
        }
    }

    @Test
    fun `every band is something a category actually maps to`() {
        // A band with no categories would spend a quarter of the ramp saying nothing,
        // and the ramp only has four steps to give.
        assertEquals(
            VisaBand.entries.toSet(),
            VisaCategory.entries.map { it.band }.toSet(),
        )
    }
}
