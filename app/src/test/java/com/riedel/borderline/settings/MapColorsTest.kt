package com.riedel.borderline.settings

import androidx.compose.ui.graphics.toArgb
import com.riedel.borderline.ui.theme.ColorMath.contrast
import com.riedel.borderline.ui.theme.ColorMath.hueGap
import com.riedel.borderline.ui.theme.ColorMath.rgb
import com.riedel.borderline.ui.theme.mapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Letting the user repaint the map risks giving back the one guarantee the fixed
 * palette bought: that the four map states stay tellable apart. These tests are
 * where that guarantee is kept — half of them on the model, half on the actual
 * colour values.
 */
class MapColorsTest {

    // --- the distinctness invariant ------------------------------------------

    @Test
    fun `picking an unused colour moves only that status`() {
        val next = MapColors.DEFAULT.with(MapSlot.LIVED, MapHue.VIOLET)
        assertEquals(MapHue.VIOLET, next.lived)
        assertEquals(MapColors.DEFAULT.visited, next.visited)
        assertEquals(MapColors.DEFAULT.wishlist, next.wishlist)
    }

    /**
     * The design decision, asserted: a taken colour is not refused, it is traded.
     * Refusing would leave the user to work out unprompted that they have to move
     * the other status first.
     */
    @Test
    fun `picking a colour another status holds swaps the two`() {
        // DEFAULT is visited=AZURE, lived=ROSE, wishlist=TEAL.
        val next = MapColors.DEFAULT.with(MapSlot.VISITED, MapHue.TEAL)
        assertEquals(MapHue.TEAL, next.visited)
        assertEquals(MapHue.AZURE, next.wishlist)
        assertEquals(MapHue.ROSE, next.lived)
    }

    @Test
    fun `a swap is its own undo`() {
        val swapped = MapColors.DEFAULT.with(MapSlot.VISITED, MapHue.TEAL)
        assertEquals(MapColors.DEFAULT, swapped.with(MapSlot.VISITED, MapHue.AZURE))
    }

    @Test
    fun `re-picking the colour a status already has changes nothing`() {
        assertSame(MapColors.DEFAULT, MapColors.DEFAULT.with(MapSlot.LIVED, MapHue.ROSE))
    }

    /**
     * The invariant, brute-forced rather than argued: from every reachable
     * arrangement, every possible tap still leaves three different colours. This is
     * the test that would catch a future [MapColors.with] rewritten into a plain
     * assignment.
     */
    @Test
    fun `no sequence of picks can make two statuses share a colour`() {
        var reachable = setOf(MapColors.DEFAULT)
        // Three rounds is enough to reach any permutation of three hues drawn from
        // the list, and cheap at six hues.
        repeat(3) {
            reachable = reachable.flatMap { colors ->
                MapSlot.entries.flatMap { slot ->
                    MapHue.entries.map { hue -> colors.with(slot, hue) }
                }
            }.toSet() + reachable
        }
        assertTrue("the walk should have found more than the default", reachable.size > 1)
        reachable.forEach { colors ->
            val hues = listOf(colors.visited, colors.lived, colors.wishlist)
            assertEquals("two statuses share a colour in $colors", 3, hues.distinct().size)
        }
    }

    // --- persistence ---------------------------------------------------------

    @Test
    fun `a stored choice is read back`() {
        val stored = MapColors.DEFAULT.with(MapSlot.LIVED, MapHue.AMBER)
        assertEquals(
            stored,
            MapColors.parse(stored.visited.name, stored.lived.name, stored.wishlist.name),
        )
    }

    @Test
    fun `nothing stored means the shipped palette`() {
        assertEquals(MapColors.DEFAULT, MapColors.parse(null, null, null))
    }

    /**
     * A name this build does not know means a preference written by a newer one. The
     * fallback is whole rather than per field: keeping two of three would risk
     * landing on a pair of statuses sharing a colour, which is the one state the
     * model exists to rule out.
     */
    @Test
    fun `an unreadable colour name falls back to the whole default palette`() {
        val parsed = MapColors.parse("AZURE", "MAGENTA", "TEAL")
        assertEquals(MapColors.DEFAULT, parsed)
    }

    @Test
    fun `a stored pair that shares a colour is refused rather than half-honoured`() {
        // Only reachable by hand-editing the preference file, or by a build that
        // dropped a hue — but the map would be unreadable, so it is worth catching.
        assertEquals(MapColors.DEFAULT, MapColors.parse("GREEN", "GREEN", "TEAL"))
    }

    // --- the colours themselves ----------------------------------------------

    /**
     * WCAG 2.1 SC 1.4.11 asks 3:1 of anything meaningful and non-textual against
     * what sits next to it. For a map fill that neighbour is unmarked land and open
     * water, in whichever theme is on — so every preset owes four passes, and the
     * light half of a pair is checked against light backgrounds only.
     */
    @Test
    fun `every preset clears 3 to 1 against unmarked land and ocean in both themes`() {
        MapBase.entries.forEach { base ->
            listOf(false, true).forEach { dark ->
                val palette = mapPalette(dark, MapColors.DEFAULT, base)
                val land = palette.unvisited.rgb()
                val ocean = palette.ocean.rgb()
                MapHue.entries.forEach { hue ->
                    val fill = hue.argb(dark) and 0xFFFFFF
                    val theme = if (dark) "dark" else "light"
                    assertTrue(
                        "$hue vs $base land in $theme: ${"%.2f".format(contrast(fill, land))}:1",
                        contrast(fill, land) >= 3.0,
                    )
                    assertTrue(
                        "$hue vs $base ocean in $theme: ${"%.2f".format(contrast(fill, ocean))}:1",
                        contrast(fill, ocean) >= 3.0,
                    )
                }
            }
        }
    }

    /**
     * A base pair is a pair for the same reason a hue is: the same hex in both
     * themes would mean one of the two was chosen for the other theme's background.
     */
    @Test
    fun `every base's ocean and land are different in light and dark`() {
        MapBase.entries.forEach { base ->
            assertNotEquals(base.oceanLightArgb, base.oceanDarkArgb)
            assertNotEquals(base.landLightArgb, base.landDarkArgb)
        }
    }

    /**
     * The real point of a curated list. Luminance contrast says a fill is visible;
     * it does not say two fills are *distinguishable*, and two colours can both pass
     * the check above while looking like one colour in two brightnesses. Hue angle
     * is what rules that out, and 40° is roughly where a side-by-side pair stops
     * being arguable.
     */
    @Test
    fun `no two presets sit within 40 degrees of each other`() {
        val hues = MapHue.entries
        listOf(false, true).forEach { dark ->
            hues.forEachIndexed { i, a ->
                hues.drop(i + 1).forEach { b ->
                    val gap = hueGap(a.argb(dark), b.argb(dark))
                    assertTrue(
                        "$a and $b are ${"%.0f".format(gap)}° apart in ${if (dark) "dark" else "light"}",
                        gap >= 40.0,
                    )
                }
            }
        }
    }

    /**
     * A pair is a pair for a reason: the same hex in both themes would mean one of
     * the two was chosen for the other theme's background.
     */
    @Test
    fun `every preset is a different value in light and dark`() {
        MapHue.entries.forEach { assertNotEquals(it.lightArgb, it.darkArgb) }
    }

    @Test
    fun `the default palette is the one the map screen draws with`() {
        val light = mapPalette(dark = false, colors = MapColors.DEFAULT)
        assertEquals(MapHue.AZURE.lightArgb.toInt(), light.visited.toArgb())
        assertEquals(MapHue.ROSE.lightArgb.toInt(), light.lived.toArgb())
        assertEquals(MapHue.TEAL.lightArgb.toInt(), light.wishlist.toArgb())
    }
}
