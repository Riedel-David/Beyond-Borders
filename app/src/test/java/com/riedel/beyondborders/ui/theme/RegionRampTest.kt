package com.riedel.beyondborders.ui.theme

import com.riedel.beyondborders.data.place.INFORMAL_REGIONS
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.ui.places.RegionBand
import com.riedel.beyondborders.ui.theme.ColorMath.contrast
import com.riedel.beyondborders.ui.theme.ColorMath.luminance
import com.riedel.beyondborders.ui.theme.ColorMath.rgb
import java.io.File
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The region heatmap gives each region its own hue and shows completion as how strong
 * that hue is, so the checks are: every region has a hue ([RegionSlots]), neighbours
 * never share one, the hues are tellable apart, and the strength steps are visible.
 *
 * Deliberately not asserted: that all eight hues survive dichromacy simulation. Eight
 * categorical hues cannot all be kept apart for protanopes and deuteranopes (the old
 * four-step ramp held 1.15:1 under every simulation; that bar is relaxed here, not
 * forgotten). What carries meaning for those users is strength, which is lightness,
 * and the tap card, which names the region.
 */
class RegionRampTest {

    private val floorAgainstBackground = 3.0
    private val floorBandStep = 1.15
    private val floorFaintAgainstLand = 1.3

    /** Minimum CIE76 distance between any two slot colours, per theme. The measured
     * minimum is 26.6 (light, orange to red) and 30.0 (dark); 25 leaves a little room. */
    private val floorDeltaE = 25.0

    private fun ramp(dark: Boolean) = mapPalette(dark, MapColors.DEFAULT).region
    private fun themes() = listOf(false, true)
    private fun name(dark: Boolean) = if (dark) "dark" else "light"
    private fun slots() = 0 until RegionSlots.COUNT

    // --- colour maths -------------------------------------------------------

    private fun lab(rgb: Long): Triple<Double, Double, Double> {
        fun lin(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = lin(16); val g = lin(8); val b = lin(0)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private fun deltaE(a: Long, b: Long): Double {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
    }

    /** [fill] at [alpha] over [under], as 24-bit rgb. */
    private fun over(fill: Long, alpha: Float, under: Long): Long {
        var out = 0L
        for (shift in intArrayOf(16, 8, 0)) {
            val f = (fill shr shift) and 0xFF
            val u = (under shr shift) and 0xFF
            out = (out shl 8) or Math.round(f * alpha + u * (1 - alpha)).toLong()
        }
        return out
    }

    private fun shown(dark: Boolean, slot: Int, band: RegionBand, land: Long): Long =
        over(ramp(dark).hues[slot].rgb(), RegionRamp.BAND_ALPHA.getValue(band), land)

    // --- the slot table -----------------------------------------------------

    private fun officialRegions(): Set<String> {
        val json = JSONArray(File("src/main/assets/places.json").readText())
        return (0 until json.length()).map { json.getJSONObject(it).getString("region") }.toSet()
    }

    @Test
    fun `every official region in the bundled places has a slot`() {
        val regions = officialRegions()
        assertEquals(24, regions.size)
        regions.forEach { assertTrue("no slot for $it", it in RegionSlots.official) }
        RegionSlots.official.values.forEach { assertTrue(it in slots()) }
    }

    @Test
    fun `every informal grouping has a slot`() {
        INFORMAL_REGIONS.forEach {
            assertTrue("no slot for ${it.name}", it.name in RegionSlots.informal)
        }
        RegionSlots.informal.values.forEach { assertTrue(it in slots()) }
    }

    @Test
    fun `the slot table has no stale names`() {
        assertEquals(officialRegions(), RegionSlots.official.keys)
        assertEquals(INFORMAL_REGIONS.map { it.name }.toSet(), RegionSlots.informal.keys)
    }

    /** Pairs of regions that share a land border or a short sea crossing. */
    private val officialNeighbours = listOf(
        "Northern Africa" to "Western Africa", "Northern Africa" to "Middle Africa",
        "Northern Africa" to "Eastern Africa", "Western Africa" to "Middle Africa",
        "Middle Africa" to "Eastern Africa", "Middle Africa" to "Southern Africa",
        "Eastern Africa" to "Southern Africa", "Northern Africa" to "Western Asia",
        "Northern Africa" to "Southern Europe", "Eastern Africa" to "Western Asia",
        "Western Europe" to "Northern Europe", "Western Europe" to "Southern Europe",
        "Western Europe" to "Eastern Europe", "Northern Europe" to "Eastern Europe",
        "Southern Europe" to "Eastern Europe", "Southern Europe" to "Western Asia",
        "Eastern Europe" to "Western Asia", "Eastern Europe" to "Central Asia",
        "Eastern Europe" to "Eastern Asia", "Western Asia" to "Central Asia",
        "Western Asia" to "Southern Asia", "Central Asia" to "Southern Asia",
        "Central Asia" to "Eastern Asia", "Southern Asia" to "Eastern Asia",
        "Southern Asia" to "South-Eastern Asia", "Eastern Asia" to "South-Eastern Asia",
        "South-Eastern Asia" to "Australia and New Zealand", "South-Eastern Asia" to "Melanesia",
        "South-Eastern Asia" to "Micronesia", "Australia and New Zealand" to "Melanesia",
        "Melanesia" to "Micronesia", "Melanesia" to "Polynesia", "Micronesia" to "Polynesia",
        "Australia and New Zealand" to "Polynesia", "Eastern Asia" to "Micronesia",
        "Northern America" to "Central America", "Northern America" to "Caribbean",
        "Northern America" to "Eastern Europe", "Northern America" to "Northern Europe",
        "Central America" to "Caribbean", "Central America" to "South America",
        "Caribbean" to "South America",
    )

    private val informalNeighbours = listOf(
        "Scandinavia" to "Nordics", "Scandinavia" to "Baltics", "Nordics" to "Baltics",
        "Benelux" to "British Isles", "Benelux" to "Alpine states", "Iberia" to "Maghreb",
        "Iberia" to "British Isles", "Alpine states" to "Visegrád / Central Europe",
        "Alpine states" to "Balkans", "Visegrád / Central Europe" to "Balkans",
        "Visegrád / Central Europe" to "Baltics", "Caucasus" to "The Stans",
        "Caucasus" to "Levant", "Caucasus" to "Balkans", "Levant" to "Arabian Peninsula / Gulf",
        "Levant" to "Balkans", "Levant" to "Maghreb", "Arabian Peninsula / Gulf" to "Horn of Africa",
        "Maghreb" to "Horn of Africa", "Maghreb" to "Balkans", "Indochina" to "The Stans",
    )

    @Test
    fun `neighbouring official regions never share a slot`() {
        officialNeighbours.forEach { (a, b) ->
            assertTrue("unknown region in $a / $b", a in RegionSlots.official && b in RegionSlots.official)
            assertNotEquals("$a and $b are neighbours", RegionSlots.official[a], RegionSlots.official[b])
        }
    }

    @Test
    fun `neighbouring informal groupings never share a slot`() {
        informalNeighbours.forEach { (a, b) ->
            assertTrue("unknown grouping in $a / $b", a in RegionSlots.informal && b in RegionSlots.informal)
            assertNotEquals("$a and $b are neighbours", RegionSlots.informal[a], RegionSlots.informal[b])
        }
    }

    // --- the colours --------------------------------------------------------

    @Test
    fun `the ramp has one hue per slot`() {
        themes().forEach { assertEquals(RegionSlots.COUNT, ramp(it).hues.size) }
    }

    @Test
    fun `every slot at full strength clears 3 to 1 against land and ocean in both themes`() {
        MapBase.entries.forEach { base ->
            themes().forEach { dark ->
                val palette = mapPalette(dark, MapColors.DEFAULT, base)
                slots().forEach { slot ->
                    val fill = shown(dark, slot, RegionBand.DONE, palette.unvisited.rgb())
                    listOf("land" to palette.unvisited.rgb(), "ocean" to palette.ocean.rgb()).forEach { (what, bg) ->
                        assertTrue(
                            "slot $slot vs $base $what in ${name(dark)}: ${"%.2f".format(contrast(fill, bg))}:1",
                            contrast(fill, bg) >= floorAgainstBackground,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `no two slot colours are closer than the Lab distance floor`() {
        themes().forEach { dark ->
            val hues = ramp(dark).hues.map { it.rgb() }
            hues.forEachIndexed { i, a ->
                hues.drop(i + 1).forEachIndexed { k, b ->
                    val d = deltaE(a, b)
                    assertTrue(
                        "slots $i and ${i + 1 + k} are ${"%.1f".format(d)} apart in ${name(dark)}",
                        d >= floorDeltaE,
                    )
                }
            }
        }
    }

    @Test
    fun `stronger bands stand out more, every step visible, in every slot and base`() {
        MapBase.entries.forEach { base ->
            themes().forEach { dark ->
                val land = mapPalette(dark, MapColors.DEFAULT, base).unvisited.rgb()
                slots().forEach { slot ->
                    val faint = shown(dark, slot, RegionBand.NONE, land)
                    assertTrue(
                        "slot $slot NONE vs $base land in ${name(dark)}: ${"%.2f".format(contrast(faint, land))}:1",
                        contrast(faint, land) >= floorFaintAgainstLand,
                    )
                    RegionBand.entries.zipWithNext { less, more ->
                        val a = shown(dark, slot, less, land)
                        val b = shown(dark, slot, more, land)
                        assertTrue(
                            "slot $slot $less to $more on $base in ${name(dark)}: ${"%.2f".format(contrast(a, b))}:1",
                            contrast(a, b) >= floorBandStep &&
                                contrast(b, land) > contrast(a, land),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the legend hue is neutral and its strengths are visible against land`() {
        themes().forEach { dark ->
            val land = mapPalette(dark, MapColors.DEFAULT).unvisited.rgb()
            val hue = ramp(dark).legendHue.rgb()
            val shades = RegionBand.entries.map { over(hue, RegionRamp.BAND_ALPHA.getValue(it), land) }
            shades.zipWithNext { a, b -> assertTrue(contrast(a, b) >= floorBandStep) }
        }
    }

    @Test
    fun `the heatmap outline is visible on every slot and band`() {
        themes().forEach { dark ->
            val land = mapPalette(dark, MapColors.DEFAULT).unvisited.rgb()
            val outline = ramp(dark).selectedOutline.rgb()
            val casing = ramp(dark).selectedCasing.rgb()
            slots().forEach { slot ->
                RegionBand.entries.forEach { band ->
                    val fill = shown(dark, slot, band, land)
                    val seen = maxOf(contrast(outline, fill), contrast(casing, fill))
                    assertTrue(
                        "outline on slot $slot $band in ${name(dark)}: ${"%.2f".format(seen)}:1",
                        seen >= floorAgainstBackground,
                    )
                }
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
        slots().forEach { assertNotEquals(ramp(false).hues[it], ramp(true).hues[it]) }
    }
}
