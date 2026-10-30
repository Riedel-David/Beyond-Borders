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
}
