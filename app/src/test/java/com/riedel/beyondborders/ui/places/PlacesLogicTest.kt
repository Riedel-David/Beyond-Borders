package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.VisaConfidence
import com.riedel.beyondborders.data.visa.VisaOrigin
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.VisaRequirement
import com.riedel.beyondborders.settings.Denominator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `stats also group by UN M49 region, not just continent`() {
        val stats = computeStats(places, mapOf("DK" to PlaceStatus.VISITED, "FR" to PlaceStatus.VISITED))
        // Denmark and France are both "Europe", but different regions - the point
        // of having both breakdowns rather than only the continent one.
        assertEquals(1, stats.regions.first { it.region == "Northern Europe" }.been)
        assertEquals(1, stats.regions.first { it.region == "Western Europe" }.been)
    }

    @Test
    fun `the Stans stay the clean five, and the Balkans stay the nine-country core`() {
        val stansAndNeighbours = listOf(
            place("KZ", "Kazakhstan", region = "Central Asia"),
            place("KG", "Kyrgyzstan", region = "Central Asia"),
            place("TJ", "Tajikistan", region = "Central Asia"),
            place("TM", "Turkmenistan", region = "Central Asia"),
            place("UZ", "Uzbekistan", region = "Central Asia"),
            place("AF", "Afghanistan", region = "Southern Asia"),
            place("PK", "Pakistan", region = "Southern Asia"),
        )
        val everyoneVisited = stansAndNeighbours.associate { it.code to PlaceStatus.VISITED }
        val stans = computeInformalRegionStats(everyoneVisited.keys, everyoneVisited)
            .first { it.name == "The Stans" }
        // Marking Afghanistan and Pakistan too must not grow the Stans' total past 5.
        assertEquals(5, stans.total)
        assertEquals(5, stans.been)

        val balkansAndNeighbours = listOf(
            place("AL", "Albania"), place("BA", "Bosnia and Herzegovina"), place("HR", "Croatia"),
            place("GR", "Greece"), place("XK", "Kosovo"), place("ME", "Montenegro"),
            place("MK", "North Macedonia"), place("RS", "Serbia"), place("SI", "Slovenia"),
            place("RO", "Romania"), place("TR", "Türkiye"),
        )
        val allVisited = balkansAndNeighbours.associate { it.code to PlaceStatus.VISITED }
        val balkans = computeInformalRegionStats(allVisited.keys, allVisited)
            .first { it.name == "Balkans" }
        assertEquals(9, balkans.total)
        assertEquals(9, balkans.been)
    }

    @Test
    fun `region bands bucket by completion, not by count`() {
        assertEquals(emptyMap<RegionBand, Set<String>>(), regionBandCodes(RegionMode.OFF, TravelStats.EMPTY))

        val stats = computeStats(places, mapOf("DK" to PlaceStatus.VISITED, "FR" to PlaceStatus.VISITED))
        val official = regionBandCodes(RegionMode.OFFICIAL, stats)
        // Northern Europe is DK alone, fully done; Western Europe is FR alone, also
        // done; North America (Greenland), Africa (Réunion) and Antarctica (Bouvet
        // Island) are each their own untouched one-place region.
        assertEquals(setOf("DK", "FR"), official[RegionBand.DONE])
        assertEquals(setOf("GL", "RE", "BV"), official[RegionBand.NONE])
        assertEquals(null, official[RegionBand.SOME])
        assertEquals(null, official[RegionBand.MOST])
    }

    @Test
    fun `region bands split a half-done group into MOST, not SOME`() {
        val benelux = listOf(
            place("BE", "Belgium", region = "Western Europe"),
            place("NL", "Netherlands", region = "Western Europe"),
            place("LU", "Luxembourg", region = "Western Europe"),
        )
        val statuses = mapOf("BE" to PlaceStatus.VISITED)
        val stats = computeStats(benelux, statuses)
        val informal = regionBandCodes(RegionMode.INFORMAL, stats)
        // 1 of 3 Benelux countries: rounds down to SOME, not MOST.
        assertEquals(setOf("BE", "NL", "LU"), informal[RegionBand.SOME])

        val twoOfThree = computeStats(benelux, mapOf("BE" to PlaceStatus.VISITED, "NL" to PlaceStatus.VISITED))
        val mostlyDone = regionBandCodes(RegionMode.INFORMAL, twoOfThree)
        assertEquals(setOf("BE", "NL", "LU"), mostlyDone[RegionBand.MOST])
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
    fun `least covered picks the lowest ratio not the biggest gap`() {
        val continents = listOf(
            ContinentStat("Europe", been = 40, total = 44),
            ContinentStat("Africa", been = 1, total = 54),
        )
        // Africa is 1/54, a far worse ratio than Europe's 40/44.
        assertEquals("Africa — 1 of 54", leastCoveredContinent(continents))
    }

    @Test
    fun `least covered breaks a ratio tie by the smaller been then by name`() {
        val continents = listOf(
            ContinentStat("Europe", been = 2, total = 4),
            ContinentStat("Africa", been = 2, total = 4),
            ContinentStat("Antarctica", been = 1, total = 2),
        )
        // All three are 50% - Antarctica wins on the smaller been count.
        assertEquals("Antarctica — 1 of 2", leastCoveredContinent(continents))
    }

    @Test
    fun `least covered ignores a continent with no places at all`() {
        val continents = listOf(
            ContinentStat("Europe", been = 1, total = 44),
            ContinentStat("Antarctica", been = 0, total = 0),
        )
        assertEquals("Europe — 1 of 44", leastCoveredContinent(continents))
    }

    @Test
    fun `least covered is null with nothing marked on a fresh install`() {
        val continents = listOf(
            ContinentStat("Europe", been = 0, total = 44),
            ContinentStat("Africa", been = 0, total = 54),
        )
        assertEquals(null, leastCoveredContinent(continents))
    }

    @Test
    fun `least covered is null once every continent is finished`() {
        val continents = listOf(
            ContinentStat("Europe", been = 44, total = 44),
            ContinentStat("Oceania", been = 14, total = 14),
        )
        assertEquals(null, leastCoveredContinent(continents))
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

    private fun requirement(
        code: String = "US",
        category: VisaCategory = VisaCategory.VISA_FREE,
        days: Int? = null,
        note: String? = null,
        confidence: VisaConfidence? = null,
    ) = VisaRequirement(
        code = code,
        category = category,
        days = days,
        origin = VisaOrigin.DATASET,
        note = note,
        confidence = confidence,
        sourceUrl = null,
        datasetSaid = null,
        officialUrl = null,
        officialFor = null,
    )

    @Test
    fun `combining requirements picks the less restrictive by ordinal`() {
        val visaFree = requirement(category = VisaCategory.VISA_FREE)
        val eta = requirement(category = VisaCategory.ETA)
        val visaRequired = requirement(category = VisaCategory.VISA_REQUIRED)

        // Visa-free beats ETA
        val combined1 = combineRequirements(visaFree, eta)
        assertEquals(VisaCategory.VISA_FREE, combined1?.winner?.category)

        // Visa-free beats visa required
        val combined2 = combineRequirements(visaRequired, visaFree)
        assertEquals(VisaCategory.VISA_FREE, combined2?.winner?.category)

        // ETA beats visa on arrival
        val voa = requirement(category = VisaCategory.VISA_ON_ARRIVAL)
        val combined3 = combineRequirements(eta, voa)
        assertEquals(VisaCategory.ETA, combined3?.winner?.category)
    }

    @Test
    fun `combining keeps the winning requirement intact - curated fields preserved`() {
        val curated = requirement(
            category = VisaCategory.VISA_FREE,
            days = 90,
            note = "Hand-curated note",
            confidence = VisaConfidence.HIGH,
        )
        val generic = requirement(
            category = VisaCategory.VISA_REQUIRED,
            note = null,
            confidence = null,
        )

        val combined = combineRequirements(curated, generic)
        assertEquals(curated, combined?.winner)
        assertEquals("Hand-curated note", combined?.winner?.note)
        assertEquals(VisaConfidence.HIGH, combined?.winner?.confidence)
    }

    @Test
    fun `ties go to the primary passport`() {
        val primary = requirement(code = "FR", category = VisaCategory.VISA_FREE, days = 90)
        val second = requirement(code = "FR", category = VisaCategory.VISA_FREE, days = 180)

        val combined = combineRequirements(primary, second)
        assertTrue(combined?.fromPrimary == true)
        assertEquals(90, combined?.winner?.days)
    }

    @Test
    fun `one side missing resolves to the other`() {
        val primary = requirement(category = VisaCategory.VISA_FREE)

        val fromPrimaryOnly = combineRequirements(primary, null)
        assertEquals(primary, fromPrimaryOnly?.winner)
        assertTrue(fromPrimaryOnly?.fromPrimary == true)

        val fromSecondOnly = combineRequirements(null, primary)
        assertEquals(primary, fromSecondOnly?.winner)
        assertTrue(fromSecondOnly?.fromPrimary == false)
    }

    @Test
    fun `both missing returns null`() {
        assertNull(combineRequirements(null, null))
    }

    @Test
    fun `noData with two passports is the union complement - a place covered by either is not grey`() {
        // Primary covers all 5 places; second covers only FR and GL
        val primary = VisaRegistry.parse("""
            {
              "passport": "DE",
              "asOf": "2026-02-17",
              "dataset": {"name": "Test", "url": "https://example.org", "licence": "MIT"},
              "iataUrl": "https://example.org/iata",
              "requirements": [
                {"code": "DK", "category": "VISA_FREE", "source": "dataset"},
                {"code": "GL", "category": "VISA_FREE", "source": "dataset"},
                {"code": "FR", "category": "VISA_FREE", "source": "dataset"},
                {"code": "RE", "category": "VISA_FREE", "source": "dataset"},
                {"code": "BV", "category": "VISA_FREE", "source": "dataset"}
              ]
            }
        """.trimIndent())

        val second = VisaRegistry.parse("""
            {
              "passport": "GB",
              "asOf": "2026-02-17",
              "dataset": {"name": "Test", "url": "https://example.org", "licence": "MIT"},
              "iataUrl": "https://example.org/iata",
              "requirements": [
                {"code": "FR", "category": "VISA_FREE", "source": "dataset"},
                {"code": "GL", "category": "VISA_REQUIRED", "source": "dataset"}
              ]
            }
        """.trimIndent())

        val noData = noDataCodes(places, primary, second, lens = true)

        // DK: covered by primary only - NOT in noData
        assertTrue("DK covered by primary only", "DK" !in noData)
        // GL: covered by both - NOT in noData
        assertTrue("GL covered by both", "GL" !in noData)
        // FR: covered by both - NOT in noData
        assertTrue("FR covered by both", "FR" !in noData)
        // RE, BV: covered by primary only - NOT in noData
        assertTrue("RE covered by primary only", "RE" !in noData)
        assertTrue("BV covered by primary only", "BV" !in noData)

        // bandCodes and noDataCodes must agree: every code with a band must be absent from noData
        val bands = bandCodes(primary, second, lens = true)
        val painted = bands.values.flatten().toSet()
        assertTrue(
            "Every painted code must be absent from noData",
            painted.intersect(noData).isEmpty()
        )
    }
}
