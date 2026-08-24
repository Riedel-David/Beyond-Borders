package com.riedel.beyondborders.data.place

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regression test for the bug this app exists to fix.
 *
 * Every other travel tracker treats Greenland as part of Denmark and French Guiana
 * as part of France, so one tap marks places the user has never been. The Python
 * build script asserts the same invariants when it generates the asset
 * (tools/geo/rules.py, SPLIT_ASSERTIONS); this test asserts them again against the
 * file that actually ships, so a hand-edited or stale asset fails the build too.
 */
class TerritorySplitTest {

    private val registry: PlaceRegistry by lazy {
        // Unit tests run with the module directory as the working directory.
        val asset = File("src/main/assets/${PlaceRegistry.ASSET_NAME}")
        assertTrue(
            "missing ${asset.path} - run tools/geo/build_geo.py",
            asset.exists(),
        )
        PlaceRegistry.parse(asset.readText())
    }

    /**
     * Groups that must stay independently selectable. Kept in lock-step with
     * SPLIT_ASSERTIONS in tools/geo/rules.py.
     */
    private val mustStaySplit = listOf(
        listOf("DK", "GL", "FO"),
        listOf(
            "FR", "GF", "GP", "MQ", "RE", "YT", "PM", "BL", "MF", "WF", "PF", "NC", "TF",
        ),
        listOf("NL", "AW", "CW", "SX", "BQ"),
        listOf("US", "PR", "VI", "GU", "AS", "MP"),
        listOf(
            "GB", "GI", "JE", "GG", "IM", "FK", "BM", "KY", "TC", "VG", "SH", "AI", "MS", "PN",
        ),
        listOf("CN", "HK", "MO", "TW"),
        listOf("NO", "SJ", "BV"),
        listOf("NZ", "CK", "NU", "TK"),
        listOf("AU", "CX", "CC", "NF", "HM"),
        listOf("FI", "AX"),
        listOf("CY", "XN"),
        listOf("SO", "XS"),
        listOf("RS", "XK"),
        listOf("IL", "PS"),
    )

    @Test
    fun `every territory group stays separately selectable`() {
        mustStaySplit.forEach { group ->
            group.forEach { code ->
                assertNotNull("$code is missing from the place registry", registry[code])
            }
            val names = group.map { registry[it]!!.name }
            assertEquals(
                "places in group ${group.first()} share a name: $names",
                group.size,
                names.toSet().size,
            )
        }
    }

    /**
     * The headline case, stated in the strongest form available at the data layer:
     * Denmark and Greenland are two rows, and Denmark's camera frame is nowhere
     * near Greenland. There is no parent link to cascade along (see [Place]), so
     * there is no code path that could mark one when the user taps the other.
     */
    @Test
    fun `Denmark and Greenland are unrelated places`() {
        val denmark = registry["DK"]!!
        val greenland = registry["GL"]!!

        assertEquals("Denmark", denmark.name)
        assertEquals("Greenland", greenland.name)
        assertFalse(denmark.bounds.contains(greenland.lon, greenland.lat))
        assertFalse(greenland.bounds.contains(denmark.lon, denmark.lat))
    }

    @Test
    fun `France is metropolitan France only`() {
        val france = registry["FR"]!!
        assertEquals("France", france.name)

        // French Guiana is at 53 deg W. If it had been folded into France, the
        // camera frame would reach across the Atlantic.
        assertTrue("France's frame reaches too far west: ${france.bounds}", france.bounds.west > -10.0)
        assertTrue("France's frame reaches too far east: ${france.bounds}", france.bounds.east < 20.0)

        val guiana = registry["GF"]!!
        assertEquals("French Guiana", guiana.name)
        assertTrue(guiana.bounds.east < -50.0)
    }

    /** The other direction: Natural Earth over-splits, and we merge those back. */
    @Test
    fun `sub-national splits are merged away`() {
        val names = registry.places.map { it.name }.toSet()
        listOf(
            "Scotland", "Wales", "Northern Ireland", "England",
            "Azores", "Madeira", "Zanzibar", "Republika Srpska",
            "Brussels Capital Region", "Walloon Region", "Flemish Region",
            "Vojvodina", "Puntland", "Bougainville", "Barbuda", "Ajaria",
        ).forEach {
            assertFalse("$it should have been merged into its parent place", it in names)
        }

        assertEquals("United Kingdom", registry["GB"]!!.name)
        assertEquals("Belgium", registry["BE"]!!.name)
        assertEquals("Bosnia and Herzegovina", registry["BA"]!!.name)
        assertEquals("Portugal", registry["PT"]!!.name)
    }

    @Test
    fun `place codes are unique`() {
        val codes = registry.places.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    /**
     * Natural Earth files nine islands under the pseudo-continent "Seven seas
     * (open ocean)", which would show up in the stats as an eighth continent
     * nobody has heard of. rules.py maps them onto real ones.
     */
    @Test
    fun `every place is on one of the seven continents`() {
        val valid = setOf(
            "Africa", "Antarctica", "Asia", "Europe",
            "North America", "Oceania", "South America",
        )
        registry.places.forEach { place ->
            assertTrue(
                "${place.code} ${place.name} has continent '${place.continent}'",
                place.continent in valid,
            )
        }
        assertEquals("Africa", registry["SC"]!!.continent)
        assertEquals("Asia", registry["MV"]!!.continent)
        assertEquals("Antarctica", registry["GS"]!!.continent)
    }

    @Test
    fun `every place has a usable camera frame`() {
        registry.places.forEach { place ->
            val b = place.bounds
            assertTrue("${place.code}: west >= east ($b)", b.west < b.east)
            assertTrue("${place.code}: south >= north ($b)", b.south < b.north)
            assertTrue("${place.code}: longitude out of range ($b)", b.west >= -180.0 && b.east <= 180.0)
            assertTrue("${place.code}: latitude out of range ($b)", b.south >= -90.0 && b.north <= 90.0)
            assertTrue("${place.code}: centroid outside its own frame", b.contains(place.lon, place.lat))
        }
    }

    /**
     * Vatican City is 0.006 degrees across and was silently quantised out of
     * existence by an earlier version of the build script. If precision handling
     * regresses, this is what catches it.
     */
    @Test
    fun `microstates survive simplification`() {
        listOf("VA", "MC", "SM", "GI", "MT", "LI", "NR", "TV").forEach { code ->
            val place = registry[code]
            assertNotNull("$code is missing", place)
            val b = place!!.bounds
            assertTrue("$code collapsed to a point: $b", b.east - b.west > 0.0)
            assertTrue("$code collapsed to a point: $b", b.north - b.south > 0.0)
        }
    }
}

private fun Bounds.contains(lon: Double, lat: Double): Boolean =
    lon in west..east && lat in south..north
