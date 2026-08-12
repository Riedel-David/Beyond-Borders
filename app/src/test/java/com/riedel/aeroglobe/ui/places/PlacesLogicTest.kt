package com.riedel.aeroglobe.ui.places

import com.riedel.aeroglobe.data.place.Bounds
import com.riedel.aeroglobe.data.place.Place
import com.riedel.aeroglobe.data.place.PlaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesLogicTest {

    private fun place(
        code: String,
        name: String,
        continent: String = "Europe",
        sovereign: String = name,
        uninhabited: Boolean = false,
    ) = Place(
        code = code,
        name = name,
        continent = continent,
        region = continent,
        sovereign = sovereign,
        uninhabited = uninhabited,
        lat = 0.0,
        lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
    )

    private val places = listOf(
        place("DK", "Denmark"),
        place("GL", "Greenland", continent = "North America", sovereign = "Denmark"),
        place("FR", "France"),
        place("RE", "Réunion", continent = "Africa", sovereign = "France"),
        place("BV", "Bouvet Island", continent = "Antarctica", sovereign = "Norway", uninhabited = true),
    )

    @Test
    fun `marking Denmark visited leaves Greenland alone`() {
        val statuses = mapOf("DK" to PlaceStatus.VISITED)
        val stats = computeStats(places, statuses)

        assertEquals(1, stats.visited)
        val rows = buildRows(places, statuses, "", StatusFilter.ALL, Grouping.ALPHABETICAL)
        assertEquals(PlaceStatus.NONE, rows.first { it.place.code == "GL" }.status)
    }

    @Test
    fun `stats count continents touched not places`() {
        val stats = computeStats(
            places,
            mapOf("DK" to PlaceStatus.VISITED, "FR" to PlaceStatus.VISITED, "RE" to PlaceStatus.WISHLIST),
        )
        assertEquals(2, stats.visited)
        assertEquals(1, stats.wishlist)
        assertEquals(5, stats.total)
        assertEquals(40, stats.percent)
        // Europe has both visits; Africa's only place is a wish, not a visit.
        assertEquals(1, stats.continentsTouched)
    }

    /**
     * The regression this whole category has to avoid: saying "I lived there" is a
     * stronger claim than "I have been there", so it must never cost you a place.
     */
    @Test
    fun `promoting a visit to lived leaves the been count alone`() {
        val visited = computeStats(places, mapOf("DK" to PlaceStatus.VISITED, "FR" to PlaceStatus.VISITED))
        val lived = computeStats(places, mapOf("DK" to PlaceStatus.LIVED, "FR" to PlaceStatus.VISITED))

        assertEquals(2, visited.been)
        assertEquals(2, lived.been)
        assertEquals(visited.percent, lived.percent)
        // Strict counts still split them, because the map legend needs one per colour.
        assertEquals(1, lived.visited)
        assertEquals(1, lived.lived)
    }

    @Test
    fun `a continent bar counts lived as been`() {
        val stats = computeStats(places, mapOf("DK" to PlaceStatus.LIVED, "FR" to PlaceStatus.WISHLIST))
        assertEquals(1, stats.continents.first { it.continent == "Europe" }.been)
        assertEquals(1, stats.continentsTouched)
    }

    @Test
    fun `wishlist is not a kind of been`() {
        val stats = computeStats(places, mapOf("DK" to PlaceStatus.WISHLIST))
        assertEquals(0, stats.been)
        assertEquals(0, stats.percent)
    }

    @Test
    fun `search ignores case and accents`() {
        val hits = buildRows(places, emptyMap(), "reunion", StatusFilter.ALL, Grouping.ALPHABETICAL)
        assertEquals(listOf("RE"), hits.map { it.place.code })
    }

    @Test
    fun `search matches the sovereign so Greenland is findable under Denmark`() {
        val hits = buildRows(places, emptyMap(), "denmark", StatusFilter.ALL, Grouping.ALPHABETICAL)
        assertEquals(setOf("DK", "GL"), hits.map { it.place.code }.toSet())
    }

    @Test
    fun `uninhabited places sort last`() {
        val rows = buildRows(places, emptyMap(), "", StatusFilter.ALL, Grouping.ALPHABETICAL)
        assertEquals("BV", rows.last().place.code)
    }

    @Test
    fun `status filter narrows to one bucket`() {
        val statuses = mapOf("DK" to PlaceStatus.VISITED, "FR" to PlaceStatus.WISHLIST)
        assertEquals(
            listOf("DK"),
            buildRows(places, statuses, "", StatusFilter.BEEN, Grouping.ALPHABETICAL).map { it.place.code },
        )
        assertEquals(
            listOf("FR"),
            buildRows(places, statuses, "", StatusFilter.WISHLIST, Grouping.ALPHABETICAL).map { it.place.code },
        )
        assertTrue(
            buildRows(places, statuses, "", StatusFilter.NONE, Grouping.ALPHABETICAL)
                .none { it.place.code in setOf("DK", "FR") },
        )
    }

    /**
     * "Been" is the superset and "Lived" narrows inside it. If Been excluded lived
     * places, the filter would disagree with the count at the top of the screen.
     */
    @Test
    fun `the been filter includes lived places and the lived filter narrows`() {
        val statuses = mapOf("DK" to PlaceStatus.VISITED, "FR" to PlaceStatus.LIVED)
        assertEquals(
            setOf("DK", "FR"),
            buildRows(places, statuses, "", StatusFilter.BEEN, Grouping.ALPHABETICAL)
                .map { it.place.code }.toSet(),
        )
        assertEquals(
            listOf("FR"),
            buildRows(places, statuses, "", StatusFilter.LIVED, Grouping.ALPHABETICAL).map { it.place.code },
        )
        // And a lived place is not "not yet".
        assertTrue(
            buildRows(places, statuses, "", StatusFilter.NONE, Grouping.ALPHABETICAL)
                .none { it.place.code == "FR" },
        )
    }
}
