package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.INFORMAL_REGIONS
import com.riedel.beyondborders.ui.UiText
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoNamesTest {

    private val bundled: JSONArray =
        JSONArray(File("src/main/assets/places.json").readText(Charsets.UTF_8))

    private fun distinct(field: String): Set<String> =
        (0 until bundled.length()).map { bundled.getJSONObject(it).getString(field) }.toSet()

    @Test
    fun `every continent and region in the bundled places has a string`() {
        val missing = (distinct("continent") + distinct("region")).filter { geoNameRes(it) == null }
        assertTrue("no string for: $missing", missing.isEmpty())
    }

    @Test
    fun `every informal group name has a string`() {
        val missing = INFORMAL_REGIONS.map { it.name }.filter { geoNameRes(it) == null }
        assertTrue("no string for: $missing", missing.isEmpty())
    }

    @Test
    fun `an unknown value falls back to the raw string`() {
        assertEquals(UiText.Plain("Atlantis"), geoNameText("Atlantis"))
    }

    @Test
    fun `a known value resolves to a resource`() {
        assertEquals(geoNameRes("Europe"), (geoNameText("Europe") as UiText.Res).id)
    }

    @Test
    fun `the lookup covers nothing that is not data`() {
        val data = distinct("continent") + distinct("region") + INFORMAL_REGIONS.map { it.name }
        assertEquals(emptySet<String>(), allGeoNameKeys() - data)
    }

    @Test
    fun `a localized name wins over the english one, which wins over the code`() {
        val localized = mapOf("DE" to "Deutschland")
        val english = mapOf("DE" to "Germany", "EG" to "Egypt")
        assertEquals("Deutschland", localizedName("DE", localized, english))
        assertEquals("Egypt", localizedName("EG", localized, english))
        assertEquals("XX", localizedName("XX", localized, english))
        assertEquals(mapOf("DE" to "Deutschland", "EG" to "Egypt"), mergeNames(english.keys, localized, english))
    }
}
