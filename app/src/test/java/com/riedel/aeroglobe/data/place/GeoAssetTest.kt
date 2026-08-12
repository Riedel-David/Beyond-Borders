package com.riedel.aeroglobe.data.place

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the contract between the two shipped assets.
 *
 * places.json is the registry the UI reads; places.geojson is what the map draws.
 * The map's whole interaction model is "tap a polygon, look its code up in the
 * registry", so the two code sets have to be identical. A code only in the
 * registry is a place nobody can ever tap; a code only in the GeoJSON is a
 * polygon that opens an empty card. Neither failure is visible in a screenshot of
 * Europe, which is exactly why it is asserted here.
 */
class GeoAssetTest {

    private val registry: PlaceRegistry by lazy {
        PlaceRegistry.parse(File("src/main/assets/${PlaceRegistry.ASSET_NAME}").readText())
    }

    private val geojson: JSONObject by lazy {
        val asset = File("src/main/assets/places.geojson")
        assertTrue("missing ${asset.path} - run tools/geo/build_geo.py", asset.exists())
        JSONObject(asset.readText())
    }

    private fun features(): List<JSONObject> {
        val array = geojson.getJSONArray("features")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Test
    fun `map polygons and registry cover exactly the same places`() {
        val mapCodes = features().map { it.getJSONObject("properties").getString("code") }.toSet()
        val registryCodes = registry.places.map { it.code }.toSet()

        assertEquals(
            "on the map but not in the registry: ${(mapCodes - registryCodes).sorted()}",
            emptySet<String>(),
            mapCodes - registryCodes,
        )
        assertEquals(
            "in the registry but not on the map: ${(registryCodes - mapCodes).sorted()}",
            emptySet<String>(),
            registryCodes - mapCodes,
        )
    }

    /**
     * MapLibre keys feature state and `queryRenderedFeatures` results off the
     * feature-level `id`, which is a different field from `properties.code`. They
     * are written from the same value in build_geo.py and must stay in step.
     */
    @Test
    fun `every feature carries a top-level id matching its code`() {
        features().forEach { feature ->
            val code = feature.getJSONObject("properties").getString("code")
            assertEquals("feature $code has a mismatched top-level id", code, feature.optString("id"))
        }
    }

    @Test
    fun `every feature has drawable geometry`() {
        features().forEach { feature ->
            val code = feature.getJSONObject("properties").getString("code")
            val geometry = feature.getJSONObject("geometry")
            val type = geometry.getString("type")
            assertTrue("$code has geometry type $type", type == "Polygon" || type == "MultiPolygon")

            // A polygon's outer ring needs four positions (three corners plus the
            // repeated closing one) before MapLibre will draw anything at all.
            val polygons = if (type == "Polygon") {
                listOf(geometry.getJSONArray("coordinates"))
            } else {
                val multi = geometry.getJSONArray("coordinates")
                (0 until multi.length()).map { multi.getJSONArray(it) }
            }
            assertTrue("$code has no polygons", polygons.isNotEmpty())
            polygons.forEach { rings ->
                assertTrue("$code has a polygon with no rings", rings.length() > 0)
                assertTrue(
                    "$code has an outer ring of fewer than 4 positions",
                    rings.getJSONArray(0).length() >= 4,
                )
            }
        }
    }
}
