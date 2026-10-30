package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.VisaConfidence
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.ui.visa.asOfNotice
import com.riedel.beyondborders.ui.visa.officialLinkLabel
import com.riedel.beyondborders.ui.visa.requirementSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The visa mode's own logic: which rows the list shows, which places the map's lens
 * paints, and the wording both of them share.
 *
 * Built on a small hand-written registry rather than the shipped asset — that one is
 * [com.riedel.beyondborders.data.visa.VisaAssetTest]'s job. Here the answers are
 * chosen to make the ordering and the filters provable, which 252 real ones would
 * only obscure.
 */
class VisaLogicTest {

    private fun place(
        code: String,
        name: String,
        sovereign: String = name,
        uninhabited: Boolean = false,
    ) = Place(
        code = code,
        name = name,
        continent = "Europe",
        region = "Europe",
        sovereign = sovereign,
        uninhabited = uninhabited,
        lat = 0.0,
        lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
    )

    private val places = listOf(
        place("DE", "Germany"),
        place("GL", "Greenland", sovereign = "Denmark"),
        place("US", "United States"),
        place("IN", "India"),
        place("RU", "Russia"),
        place("BV", "Bouvet Island", sovereign = "Norway", uninhabited = true),
        place("AA", "Atlantis"),
    )

    /**
     * One answer per category the app can render, plus Atlantis — a place with no visa
     * row at all, which the shipped asset never has and the list therefore has to
     * survive anyway.
     */
    private val visa = VisaRegistry.parse(
        """
        {
          "passport": "DE",
          "asOf": "2026-02-17",
          "dataset": {
            "name": "Visa requirements (Wikipedia contributors)",
            "url": "https://example.org/dataset",
            "licence": "CC BY-SA 4.0",
            "licenceUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
            "sourceUrl": "https://en.wikipedia.org/wiki/Visa_requirements_for_German_citizens",
            "sourceRevision": 1234567
          },
          "iataUrl": "https://example.org/iata",
          "requirements": [
            {"code": "DE", "category": "HOME", "source": "dataset"},
            {"code": "GL", "category": "VISA_FREE", "source": "curated", "confidence": "high",
             "note": "Outside Schengen.", "sourceUrl": "https://example.org/gl",
             "officialUrl": "https://example.org/dk", "officialFor": "DK"},
            {"code": "US", "category": "ETA", "source": "dataset",
             "officialUrl": "https://example.org/us"},
            {"code": "IN", "category": "E_VISA", "source": "dataset", "days": 30,
             "officialUrl": "https://example.org/in"},
            {"code": "RU", "category": "VISA_REQUIRED", "source": "dataset",
             "officialUrl": "https://example.org/ru"},
            {"code": "BV", "category": "PERMIT_REQUIRED", "source": "curated",
             "confidence": "medium", "note": "Nature reserve.",
             "sourceUrl": "https://example.org/bv", "officialUrl": "https://example.org/no",
             "officialFor": "NO"},
            {"code": "KP", "category": "CHECK", "source": "dataset", "confidence": "low",
             "note": "Travel banned by the passport's own government."}
          ]
        }
        """.trimIndent()
    )

    private fun rows(
        filter: VisaFilter = VisaFilter.All,
        query: String = "",
        statuses: Map<String, PlaceStatus> = emptyMap(),
    ) = buildVisaRows(places, visa, null, statuses, query, filter).map { it.place.code }

    // --- the list --------------------------------------------------------------

    private fun reg(passport: String, vararg rows: Pair<String, String>) = VisaRegistry.parse(
        """{"passport":"$passport","asOf":"2026-01-01",
        "dataset":{"name":"n","url":"https://e.org","licence":"L"},"iataUrl":"https://e.org",
        "requirements":[${rows.joinToString(",") { """{"code":"${it.first}","category":"${it.second}","source":"dataset"}""" }}]}"""
    )

    @Test
    fun `headline totals and chips come from the combined passports`() {
        val primary = reg("DE", "FR" to "VISA_FREE", "KP" to "VISA_REQUIRED", "CN" to "NO_ADMISSION")
        val second = reg("GB", "KP" to "VISA_FREE", "IN" to "ETA", "CN" to "CHECK")
        // KP: the second passport's visa-free wins; CN: CHECK beats the refusal; IN only on the second.
        assertEquals(CombinedTotals(answered = 4, visaFree = 2, easy = 3), combinedTotals(primary, second))
        assertEquals(CombinedTotals(3, 1, 1), combinedTotals(primary, null))
        assertEquals(
            listOf(VisaCategory.VISA_FREE, VisaCategory.ETA, VisaCategory.CHECK),
            visaFilters(primary, second).filterIsInstance<VisaFilter.Category>().map { it.category },
        )
        // No chip for a category the combined list no longer contains (VISA_REQUIRED, NO_ADMISSION).
        assertEquals(
            setOf(VisaCategory.VISA_FREE, VisaCategory.ETA, VisaCategory.CHECK),
            combinedCategoryCounts(primary, second).keys,
        )
    }

    @Test
    fun `the attribution fields and a CHECK row survive parsing`() {
        assertEquals("CC BY-SA 4.0", visa.datasetLicence)
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", visa.licenceUrl)
        assertEquals(
            "https://en.wikipedia.org/wiki/Visa_requirements_for_German_citizens",
            visa.sourceUrl,
        )
        assertEquals("1234567", visa.sourceRevision)
        val kp = visa["KP"]!!
        assertEquals(VisaCategory.CHECK, kp.category)
        assertEquals(VisaConfidence.LOW, kp.confidence)
        assertEquals("Travel banned by the passport's own government.", kp.note)
    }

    /**
     * The ordering *is* the feature: "where can I go" has to be answerable by looking
     * at the top of the screen rather than by scrolling. [VisaCategory]'s declaration
     * order is easiest-first, and this is what holds the list to it.
     */
    @Test
    fun `the easiest answers come first and the uninhabited ones last`() {
        assertEquals(listOf("DE", "GL", "US", "IN", "RU", "BV"), rows())
    }

    @Test
    fun `a place with no visa answer is dropped rather than shown blank`() {
        assertTrue("Atlantis has no requirement and must not appear", "AA" !in rows())
    }

    @Test
    fun `no visa data means no rows at all`() {
        assertEquals(emptyList<String>(), buildVisaRows(places, null, null, emptyMap(), "", VisaFilter.All))
    }

    /**
     * The default useful view of a passport: everything reachable without an embassy
     * appointment, which is home, visa-free, an ETA and a visa on arrival together.
     */
    @Test
    fun `the no-embassy filter keeps the ETA and drops the e-visa`() {
        assertEquals(listOf("DE", "GL", "US"), rows(VisaFilter.Easy))
    }

    @Test
    fun `a category filter narrows to exactly that answer`() {
        assertEquals(listOf("US"), rows(VisaFilter.Category(VisaCategory.ETA)))
        assertEquals(listOf("BV"), rows(VisaFilter.Category(VisaCategory.PERMIT_REQUIRED)))
        assertEquals(
            emptyList<String>(),
            rows(VisaFilter.Category(VisaCategory.NO_ADMISSION)),
        )
    }

    /** The same search behaviour as the travel list, on the same field. */
    @Test
    fun `search matches the sovereign so Greenland is findable under Denmark`() {
        assertEquals(listOf("GL"), rows(query = "denmark"))
    }

    @Test
    fun `search and filter both apply`() {
        assertEquals(emptyList<String>(), rows(VisaFilter.Category(VisaCategory.ETA), query = "india"))
    }

    /** What you have already done travels with the row: the list marks it, not hides it. */
    @Test
    fun `an already visited destination keeps its row and its status`() {
        val visited = buildVisaRows(
            places,
            visa,
            null,
            mapOf("GL" to PlaceStatus.LIVED),
            "",
            VisaFilter.All,
        )
        val greenland = visited.first { it.place.code == "GL" }
        assertEquals(PlaceStatus.LIVED, greenland.status)
        assertTrue(greenland.status.isBeen)
    }

    // --- the join: two datasets the app already owns ---------------------------

    /** GL is visa-free (easy) and on the wishlist; IN is on the wishlist but only an e-visa. */
    @Test
    fun `the wishlist-easy filter needs both a wish and an easy answer`() {
        assertEquals(
            listOf("GL"),
            rows(VisaFilter.WishlistEasy, statuses = mapOf("GL" to PlaceStatus.WISHLIST)),
        )
        assertEquals(
            emptyList<String>(),
            rows(VisaFilter.WishlistEasy, statuses = mapOf("IN" to PlaceStatus.WISHLIST)),
        )
    }

    /** IN is visa-free-adjacent but only an e-visa with 30 days: not long-stay, not visa-free. */
    @Test
    fun `the long-stay filter is visa-free only, and needs 90 days or more`() {
        assertEquals(emptyList<String>(), rows(VisaFilter.LongStay))
    }

    @Test
    fun `the wishlist join line names the easy fraction of the whole wishlist`() {
        val statuses = mapOf("GL" to PlaceStatus.WISHLIST, "RU" to PlaceStatus.WISHLIST)
        assertEquals(
            "1 of your 2 wishlist places need no embassy",
            computeWishlistEasyLine(places, statuses, visa),
        )
    }

    @Test
    fun `the wishlist join line is null with no visa asset, no wishlist, or nothing easy on it`() {
        assertEquals(null, computeWishlistEasyLine(places, mapOf("GL" to PlaceStatus.WISHLIST), null))
        assertEquals(null, computeWishlistEasyLine(places, emptyMap(), visa))
        assertEquals(null, computeWishlistEasyLine(places, mapOf("RU" to PlaceStatus.WISHLIST), visa))
    }

    // --- the chips -------------------------------------------------------------

    @Test
    fun `the chips offer only categories that have places in them`() {
        val labels = visaFilters(visa)
        assertEquals(VisaFilter.All, labels.first())
        assertEquals(VisaFilter.Easy, labels[1])
        assertEquals(
            listOf(
                VisaCategory.VISA_FREE,
                VisaCategory.ETA,
                VisaCategory.E_VISA,
                VisaCategory.VISA_REQUIRED,
                VisaCategory.CHECK,
                VisaCategory.PERMIT_REQUIRED,
            ),
            labels.filterIsInstance<VisaFilter.Category>().map { it.category },
        )
    }

    /** Home is one place and it is the user's own country. A chip for it is a joke. */
    @Test
    fun `there is no chip for going home`() {
        assertTrue(
            visaFilters(visa).none { it is VisaFilter.Category && it.category == VisaCategory.HOME },
        )
    }

    @Test
    fun `no visa data means no chips`() {
        assertEquals(emptyList<VisaFilter>(), visaFilters(null))
    }

    // --- the map's lens --------------------------------------------------------

    @Test
    fun `the lens paints nothing while it is off`() {
        assertEquals(emptyMap<VisaBand, Set<String>>(), bandCodes(visa, null, lens = false))
        assertEquals(emptyMap<VisaBand, Set<String>>(), bandCodes(null, null, lens = true))
    }

    /**
     * Six bands out of nine categories, and the grouping is the map's whole claim:
     * "roughly, where can I go" is a picture, and nine colours is a legend.
     */
    @Test
    fun `each band collects the categories the ramp merges`() {
        val bands = bandCodes(visa, null, lens = true)
        assertEquals(setOf("DE", "GL"), bands[VisaBand.VISA_FREE])
        assertEquals(setOf("US"), bands[VisaBand.ETA])
        assertEquals(setOf("IN", "RU"), bands[VisaBand.VISA_REQUIRED])
        assertEquals(setOf("BV"), bands[VisaBand.CLOSED])
    }

    /**
     * Every answer lands in exactly one band. A place painted twice would depend on
     * layer order for its colour, and a place painted in no band would look like a
     * place with no answer.
     */
    @Test
    fun `every answer is painted once and only once`() {
        val bands = bandCodes(visa, null, lens = true)
        val painted = bands.values.flatten()
        assertEquals(visa.requirements.size, painted.size)
        assertEquals(painted.size, painted.toSet().size)
        assertEquals(visa.requirements.map { it.code }.toSet(), painted.toSet())
    }

    /**
     * Atlantis has no requirement at all — DE's dataset covers all 252 real places,
     * but this hand-written registry deliberately leaves one place uncovered to
     * stand in for a generic-tier passport's honest gaps.
     */
    @Test
    fun `no-data collects exactly the places the registry has no answer for`() {
        assertEquals(setOf("AA"), noDataCodes(places, visa, null, lens = true))
    }

    @Test
    fun `no-data is empty while the lens is off, or with no visa asset`() {
        assertEquals(emptySet<String>(), noDataCodes(places, visa, null, lens = false))
        assertEquals(emptySet<String>(), noDataCodes(places, null, null, lens = true))
    }

    // --- the words -------------------------------------------------------------

    @Test
    fun `a summary states the day count only when there is one`() {
        assertEquals("e-Visa · 30 days", requirementSummary(visa["IN"]!!))
        // EU freedom of movement has no limit, and inventing 90 days would be the
        // exact class of error this feature exists to avoid.
        assertEquals("Visa-free", requirementSummary(visa["GL"]!!))
    }

    @Test
    fun `every visa surface can say how old the data is`() {
        val notice = asOfNotice(visa)
        assertTrue("the date has to be in it: $notice", notice.contains("2026-02-17"))
        assertTrue("and the warning: $notice", notice.contains("verify", ignoreCase = true))
        assertTrue("and which passport: $notice", notice.contains("German"))
    }

    /**
     * The label follows the page, not the place. Greenland's advice lives on the
     * Denmark page, and a link reading "Greenland" that opens a page headed Dänemark
     * would look like a bug in the one place this app claims to be careful.
     */
    @Test
    fun `a parent's advice page is labelled with the parent's name`() {
        val nameOf = { code: String -> places.firstOrNull { it.code == code }?.name }
        assertEquals(
            "Foreign Office advice: Denmark",
            officialLinkLabel(visa["GL"]!!, nameOf = { "Denmark" }),
        )
        assertEquals("Foreign Office advice", officialLinkLabel(visa["US"]!!, nameOf))
        // An unknown parent falls back to its code rather than to "null".
        assertEquals("Foreign Office advice: NO", officialLinkLabel(visa["BV"]!!, nameOf))
    }
}
