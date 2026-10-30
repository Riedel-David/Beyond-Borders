package com.riedel.beyondborders.ui.map

import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceFlagTest {
    @Test
    fun `a code maps to its lowercase webp asset`() {
        assertEquals("flags/de.webp", flagAssetPath("DE"))
        assertEquals("flags/gb.webp", flagAssetPath(" gb "))
    }

    @Test
    fun `codes that cannot name a flag have no path`() {
        assertNull(flagAssetPath(null))
        assertNull(flagAssetPath(""))
        assertNull(flagAssetPath("D"))
        assertNull(flagAssetPath("../x"))
        assertNull(flagAssetPath("GBR"))
    }

    @Test
    fun `every place code except the known gaps has a bundled flag file`() {
        val places = JSONArray(File("src/main/assets/places.json").readText())
        val missing = (0 until places.length())
            .mapNotNull { flagAssetPath(places.getJSONObject(it).optString("code")) }
            .filterNot { File("src/main/assets/$it").isFile }
        assertEquals(listOf("flags/xn.webp", "flags/xs.webp"), missing.sorted())
    }

    private val navy = 0xFF3C3B6E.toInt()
    private val red = 0xFFB22234.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun contrastWithWhite(argb: Int) = 1.05 / (relativeLuminance(argb) + 0.05)

    @Test
    fun `a US-like flag yields navy red and white in order of area`() {
        val px = IntArray(1000) { i ->
            when {
                i < 400 -> red
                i < 750 -> white
                else -> navy
            }
        }
        assertEquals(listOf(red, white, navy), dominantColors(px))
    }

    @Test
    fun `transparent pixels do not count towards any colour`() {
        val px = IntArray(100) { i -> if (i < 90) 0x00FF0000 else navy }
        assertEquals(listOf(navy), dominantColors(px))
    }

    @Test
    fun `a single-colour flag yields one colour`() {
        assertEquals(listOf(red), dominantColors(IntArray(50) { red }))
    }

    @Test
    fun `near-identical shades collapse into one colour`() {
        val px = IntArray(100) { i -> if (i % 2 == 0) 0xFF0000C8.toInt() else 0xFF0000CC.toInt() }
        assertEquals(1, dominantColors(px).size)
    }

    @Test
    fun `darkening gives white text at least 4 point 5 to 1 contrast`() {
        for (c in listOf(white, 0xFFFFD100.toInt(), 0xFFFF0000.toInt(), navy)) {
            assertTrue(contrastWithWhite(darkenForWhiteText(c)) >= 4.5)
        }
    }

    @Test
    fun `an already dark colour is left unchanged`() {
        val dark = 0xFF1B1B1F.toInt()
        assertEquals(dark, darkenForWhiteText(dark))
        assertEquals(navy, darkenForWhiteText(navy))
    }

    @Test
    fun `a wide flag is limited by the width of the box`() {
        val (w, h) = flagThumbSize(228, 120, 64f, 40f)
        assertEquals(64f, w, 0.01f)
        assertEquals(64f * 120 / 228, h, 0.01f)
    }

    @Test
    fun `a square flag is limited by the height of the box`() {
        val (w, h) = flagThumbSize(120, 120, 64f, 40f)
        assertEquals(40f, w, 0.01f)
        assertEquals(40f, h, 0.01f)
    }

    @Test
    fun `a tall flag keeps its proportions at full height`() {
        val (w, h) = flagThumbSize(100, 120, 64f, 40f)
        assertEquals(40f, h, 0.01f)
        assertEquals(100f / 120 * 40f, w, 0.01f)
    }
}
