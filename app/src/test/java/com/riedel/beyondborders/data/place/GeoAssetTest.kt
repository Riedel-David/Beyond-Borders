package com.riedel.beyondborders.data.place

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

    /**
     * `areaKm2` and `popEst` back the passport's "% of the world's land area,
     * % of its population" line — a null or negative value there would silently
     * zero out the whole coverage figure instead of failing a build.
     */
    @Test
    fun `every place carries a plausible area and population`() {
        registry.places.forEach { place ->
            assertTrue("${place.code} has a negative area", place.areaKm2 >= 0)
            assertTrue("${place.code} has a negative population", place.popEst >= 0)
        }
        val totalArea = registry.places.sumOf { it.areaKm2 }
        val totalPop = registry.places.sumOf { it.popEst }
        // Earth's land area is ~148.9M km^2 and 2019-era world population is
        // ~7.7B; wide bands, since this only has to catch a broken pipeline,
        // not validate the exact figure.
        assertTrue("world land area is implausible: $totalArea", totalArea in 120_000_000..170_000_000)
        assertTrue("world population is implausible: $totalPop", totalPop in 6_000_000_000..9_000_000_000)
    }

    /**
     * The count behind the "which denominator" setting: 193 UN member states plus
     * the two permanent observers, Holy See and Palestine, is the "195" a traveller
     * means — a different, smaller number than the app's own 252 places.
     */
    @Test
    fun `exactly 195 places are UN members or permanent observers`() {
        assertEquals(195, registry.places.count { it.unMember })
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
