package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.settings.Denominator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesLogicTest {

    private fun place(
        code: String,
        name: String,
        continent: String = "Europe",
        region: String = continent,
        sovereign: String = name,
        uninhabited: Boolean = false,
        unMember: Boolean = true,
        areaKm2: Long = 0,
        popEst: Long = 0,
    ) = Place(
        code = code,
        name = name,
        continent = continent,
        region = region,
        sovereign = sovereign,
        uninhabited = uninhabited,
        unMember = unMember,
        lat = 0.0,
        lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
        areaKm2 = areaKm2,
        popEst = popEst,
    )

    private val places = listOf(
        place("DK", "Denmark", region = "Northern Europe"),
        place("GL", "Greenland", continent = "North America", sovereign = "Denmark", unMember = false),
        place("FR", "France", region = "Western Europe"),
        place("RE", "Réunion", continent = "Africa", sovereign = "France", unMember = false),
        place("BV", "Bouvet Island", continent = "Antarctica", sovereign = "Norway", uninhabited = true, unMember = false),
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

    /**
     * The setting this whole model exists for: "of 195" should mean 195, and a mark
     * on a place outside that count (Greenland, a dependency) must not inflate
     * "been" past a denominator that no longer includes it.
     */
    @Test
    fun `the UN member denominator excludes dependencies and observers from both sides`() {
        val stats = computeStats(
            places,
            mapOf("DK" to PlaceStatus.VISITED, "GL" to PlaceStatus.VISITED),
            Denominator.UN_MEMBER,
        )
        // Only DK and FR are UN members here; GL, RE and BV are not.
        assertEquals(2, stats.total)
        assertEquals(1, stats.been)
        assertEquals(50, stats.percent)
    }

    @Test
    fun `the inhabited denominator drops only the uninhabited place`() {
        val stats = computeStats(places, mapOf("DK" to PlaceStatus.VISITED), Denominator.INHABITED)
        assertEquals(4, stats.total)
    }

    @Test
    fun `the all denominator is the full 252-style count`() {
        val stats = computeStats(places, mapOf("DK" to PlaceStatus.VISITED), Denominator.ALL)
        assertEquals(places.size, stats.total)
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
    fun `an accented initial sorts by its unaccented letter rather than past Z`() {
        val withAland = places + place("AX", "Åland Islands", sovereign = "Finland")
        assertEquals(
            listOf("AX", "DK", "FR", "GL", "RE", "BV"),
            buildRows(withAland, emptyMap(), "", StatusFilter.ALL, Grouping.ALPHABETICAL)
                .map { it.place.code },
        )
    }

    @Test
    fun `a section letter follows the sort, so Åland is filed under A`() {
        assertEquals("A", sectionHeader(place("AX", "Åland Islands"), Grouping.ALPHABETICAL))
        assertEquals("D", sectionHeader(place("DK", "Denmark"), Grouping.ALPHABETICAL))
    }

    /**
     * The uninhabited block is one section rather than a second run of letters. Bouvet
     * Island under its own "B" would collide with Belgium's, and two list items with
     * one key is a crash rather than a cosmetic repeat.
     */
    @Test
    fun `uninhabited places get their own header instead of a second alphabet`() {
        val bouvet = places.first { it.code == "BV" }
        assertEquals(UNINHABITED_HEADER, sectionHeader(bouvet, Grouping.ALPHABETICAL))
        // Continent grouping keeps it where it belongs: there it sorts last inside
        // Antarctica, so the header never repeats and never needs splitting.
        assertEquals("Antarctica", sectionHeader(bouvet, Grouping.CONTINENT))
        // Region grouping has the same property, one header per subregion.
        assertEquals("Antarctica", sectionHeader(bouvet, Grouping.REGION))
    }

    @Test
    fun `region grouping splits a continent that grouping by continent lumps together`() {
        val rows = buildRows(places, emptyMap(), "", StatusFilter.ALL, Grouping.REGION)
        val headerFor = { code: String -> sectionHeader(rows.first { it.place.code == code }.place, Grouping.REGION) }
        // Denmark and France are both "Europe" by continent, but different UN
        // subregions - the point of grouping by region instead.
        assertEquals("Northern Europe", headerFor("DK"))
        assertEquals("Western Europe", headerFor("FR"))
    }

    @Test
    fun `the nudge picks the continent closest to done, not the biggest one`() {
        val continents = listOf(
            ContinentStat("Europe", been = 40, total = 44),
            ContinentStat("Africa", been = 1, total = 54),
            ContinentStat("Antarctica", been = 0, total = 5),
        )
        // Europe is 4 away, Africa is 53 away, Antarctica is untouched and skipped.
        assertEquals("4 places from finishing Europe", almostFinishedNudge(continents))
    }

    @Test
    fun `the nudge says place, singular, with one to go`() {
        val continents = listOf(ContinentStat("Oceania", been = 13, total = 14))
        assertEquals("1 place from finishing Oceania", almostFinishedNudge(continents))
    }

    @Test
    fun `there is no nudge once every touched continent is finished`() {
        val continents = listOf(
            ContinentStat("Europe", been = 44, total = 44),
            ContinentStat("Africa", been = 0, total = 54),
        )
        assertEquals(null, almostFinishedNudge(continents))
    }

    @Test
    fun `coverage joins been status against area and population already on the asset`() {
        val withGeo = listOf(
            place("DK", "Denmark", areaKm2 = 43_000, popEst = 6_000_000),
            place("FR", "France", areaKm2 = 550_000, popEst = 65_000_000),
            place("RU", "Russia", areaKm2 = 407_000, popEst = 144_000_000),
        )
        // Total area 1,000,000; total pop 215,000,000. Marking Denmark alone:
        // 43,000 / 1,000,000 = 4%; 6,000,000 / 215,000,000 rounds down to 2%.
        assertEquals(
            "4% of the world's land area, 2% of its population",
            worldCoverage(withGeo, mapOf("DK" to PlaceStatus.VISITED)),
        )
    }

    @Test
    fun `coverage is null with nothing marked, or with an asset carrying no area or population`() {
        val withGeo = listOf(place("DK", "Denmark", areaKm2 = 43_000, popEst = 6_000_000))
        assertEquals(null, worldCoverage(withGeo, emptyMap()))
        assertEquals(null, worldCoverage(places, mapOf("DK" to PlaceStatus.VISITED)))
    }

    @Test
    fun `the wishlist join needs a visa asset and a wishlist`() {
        val statuses = mapOf("DK" to PlaceStatus.WISHLIST)
        assertEquals(null, computeWishlistEasyLine(places, statuses, visa = null))
        assertEquals(null, computeWishlistEasyLine(places, emptyMap(), visa = null))
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
