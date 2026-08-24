package com.riedel.beyondborders.data.visa

import com.riedel.beyondborders.data.place.PlaceRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The visa asset, checked against the file that actually ships.
 *
 * Same arrangement as [com.riedel.beyondborders.data.place.TerritorySplitTest] and
 * for the same reason: tools/visa/build_visa.py asserts these invariants when it
 * writes the asset, and this asserts them again afterwards, so a stale or hand-edited
 * visa.json fails the build rather than shipping a wrong answer quietly.
 *
 * The claims here are about *shape*, not about immigration law. Nothing in a unit
 * test can know whether Suriname still grants 90 days; what it can know is that every
 * place has an answer, that every hand-written answer says what it rests on, and that
 * the answer for Greenland is Greenland's own.
 */
class VisaAssetTest {

    private val visa: VisaRegistry by lazy {
        // Unit tests run with the module directory as the working directory.
        val asset = File("src/main/assets/${VisaRegistry.ASSET_NAME}")
        assertTrue(
            "missing ${asset.path} - run tools/visa/build_visa.py",
            asset.exists(),
        )
        VisaRegistry.parse(asset.readText())
    }

    private val places: PlaceRegistry by lazy {
        PlaceRegistry.parse(File("src/main/assets/${PlaceRegistry.ASSET_NAME}").readText())
    }

    // --- coverage -------------------------------------------------------------

    /**
     * The invariant the whole feature rests on. A destination with no row would show
     * as a hole in the map's lens and as a missing row in the list, and the user would
     * have no way to tell that hole from "no visa needed".
     */
    @Test
    fun `every place has exactly one visa answer`() {
        val placeCodes = places.places.map { it.code }.toSet()
        val visaCodes = visa.requirements.map { it.code }
        assertEquals(
            "duplicate codes in ${VisaRegistry.ASSET_NAME}",
            visaCodes.size,
            visaCodes.toSet().size,
        )
        assertEquals(
            "places with no visa answer: ${(placeCodes - visaCodes.toSet()).sorted()}",
            emptySet<String>(),
            placeCodes - visaCodes.toSet(),
        )
        assertEquals(
            "visa answers for places that do not exist: ${(visaCodes.toSet() - placeCodes).sorted()}",
            emptySet<String>(),
            visaCodes.toSet() - placeCodes,
        )
    }

    // --- the answers phase 2 exists for ---------------------------------------

    /**
     * The headline case, at the visa layer. Every other tracker answers Greenland with
     * Denmark's Schengen rules, which are the wrong rules — Greenland is outside both
     * the EU and Schengen.
     */
    @Test
    fun `Greenland is answered on its own terms`() {
        val greenland = visa["GL"]!!
        assertEquals(VisaCategory.VISA_FREE, greenland.category)
        assertEquals(VisaOrigin.CURATED, greenland.origin)
        assertNotNull("Greenland's answer needs its note", greenland.note)
        assertNotNull("a curated answer needs a source", greenland.sourceUrl)
        // The one thing about the answer that is not Denmark's: the note has to say so.
        assertTrue(
            "Greenland's note should say it is outside Schengen: ${greenland.note}",
            greenland.note!!.contains("Schengen", ignoreCase = true),
        )
        // The travel-advice page *is* Denmark's, which is why the UI labels it with the
        // parent's name instead of implying the page is about Greenland.
        assertEquals("DK", greenland.officialFor)
        assertEquals(visa["DK"]!!.officialUrl, greenland.officialUrl)
    }

    /**
     * The other half of the pair, and the opposite trap: French Guiana really does
     * have France's freedom of movement, so the answer is the same category as France
     * for a reason — but it is outside Schengen, so it is not the same *answer*.
     */
    @Test
    fun `French Guiana is answered on its own terms`() {
        val guiana = visa["GF"]!!
        assertEquals(VisaCategory.VISA_FREE, guiana.category)
        assertEquals(VisaOrigin.CURATED, guiana.origin)
        assertEquals("FR", guiana.officialFor)
        assertTrue(
            "French Guiana's note should mention Schengen: ${guiana.note}",
            guiana.note!!.contains("Schengen", ignoreCase = true),
        )
        // No day limit: freedom of movement is not a 90-day grant, and printing one
        // would be the same class of error as inheriting Denmark's rules.
        assertNull("EU freedom of movement has no day count", guiana.days)
    }

    /**
     * The seven places nobody lives in need permission from a research programme or a
     * nature-reserve authority, not a visa. Filing them under [VisaCategory.VISA_REQUIRED]
     * would send someone looking for an embassy counter that does not exist.
     */
    @Test
    fun `the uninhabited places ask for a permit rather than a visa`() {
        val uninhabited = places.places.filter { it.uninhabited }.map { it.code }.toSet()
        val permit = visa.requirements
            .filter { it.category == VisaCategory.PERMIT_REQUIRED }
            .map { it.code }
            .toSet()
        assertEquals(uninhabited, permit)
    }

    // --- honesty about where an answer came from ------------------------------

    @Test
    fun `every hand-written answer carries a source and a confidence`() {
        visa.requirements
            .filter { it.origin != VisaOrigin.DATASET }
            .forEach { requirement ->
                assertNotNull("${requirement.code} has no sourceUrl", requirement.sourceUrl)
                assertNotNull("${requirement.code} has no confidence", requirement.confidence)
                assertNotNull("${requirement.code} has no note", requirement.note)
                assertTrue(
                    "${requirement.code}'s source is not a link: ${requirement.sourceUrl}",
                    requirement.sourceUrl!!.startsWith("https://"),
                )
            }
    }

    /**
     * The converse, which matters just as much: a row straight out of the dataset must
     * not carry a note or a confidence, because both would read as research that
     * nobody did.
     */
    @Test
    fun `a dataset row does not dress itself up as researched`() {
        visa.requirements
            .filter { it.origin == VisaOrigin.DATASET }
            .forEach { requirement ->
                assertNull("${requirement.code} has a note it did not earn", requirement.note)
                assertNull("${requirement.code} claims a confidence", requirement.confidence)
                assertNull("${requirement.code} claims a source", requirement.sourceUrl)
            }
    }

    /**
     * Overruling upstream is allowed; doing it silently is not. The UI shows the raw
     * cell next to our answer, so a reader who thinks we got it wrong can see there
     * was a second opinion.
     */
    @Test
    fun `a correction always says what it overruled`() {
        // No corrections in the current asset — the dataset and the curated territory
        // list happen not to overlap. Written as a loop rather than skipped so the
        // first one added is checked instead of being trusted.
        visa.requirements
            .filter { it.origin == VisaOrigin.CORRECTION }
            .forEach {
                assertNotNull("${it.code} overrules the dataset without saying so", it.datasetSaid)
            }
        visa.requirements
            .filter { it.origin != VisaOrigin.CORRECTION }
            .forEach {
                assertNull("${it.code} quotes the dataset without overruling it", it.datasetSaid)
            }
    }

    /**
     * Every screen has to be able to point at the authority. The two exceptions are
     * real: Germany is the passport itself, and Antarctica has no Auswärtiges Amt
     * country page because it is nobody's country.
     */
    @Test
    fun `every answer links to the Foreign Office except the two with no page`() {
        val without = visa.requirements.filter { it.officialUrl == null }.map { it.code }.toSet()
        assertEquals(setOf("DE", "AQ"), without)
        visa.requirements.mapNotNull { it.officialUrl }.forEach { url ->
            assertTrue(
                "not an Auswärtiges Amt link: $url",
                url.startsWith("https://www.auswaertiges-amt.de/"),
            )
        }
    }

    @Test
    fun `a parent link names a real place that is not itself`() {
        val codes = places.places.map { it.code }.toSet()
        visa.requirements.forEach { requirement ->
            val parent = requirement.officialFor ?: return@forEach
            assertTrue("${requirement.code} points at unknown parent $parent", parent in codes)
            assertFalse("${requirement.code} is its own parent", parent == requirement.code)
            assertNotNull(
                "${requirement.code} names a parent but has no link to it",
                requirement.officialUrl,
            )
            assertEquals(
                "${requirement.code} claims $parent's page but links elsewhere",
                visa[parent]?.officialUrl,
                requirement.officialUrl,
            )
        }
    }

    // --- the header the UI quotes ---------------------------------------------

    @Test
    fun `the dataset is attributed, licensed and dated`() {
        assertEquals("DE", visa.passport)
        assertTrue(
            "asOf is not an ISO date: ${visa.asOf}",
            Regex("""\d{4}-\d{2}-\d{2}""").matches(visa.asOf),
        )
        assertTrue(visa.datasetName.isNotBlank())
        assertTrue(visa.datasetUrl.startsWith("https://"))
        assertTrue("the licence has to be nameable: ${visa.datasetLicence}", visa.datasetLicence.isNotBlank())
        assertTrue(visa.iataUrl.startsWith("https://"))
    }

    /**
     * Loose bounds on purpose. The point is not to pin the number — it moves with the
     * world — but to catch a build that silently produced an asset where nearly
     * everything, or nearly nothing, is open.
     */
    @Test
    fun `the headline numbers are plausible for a German passport`() {
        assertTrue("only ${visa.visaFreeCount} visa-free", visa.visaFreeCount in 120..220)
        assertTrue(
            "easyCount ${visa.easyCount} is below visaFreeCount ${visa.visaFreeCount}",
            visa.easyCount >= visa.visaFreeCount,
        )
        // Every band the lens can paint has something in it, or a quarter of the ramp
        // is spent saying nothing.
        VisaBand.entries.forEach { band ->
            assertTrue("no places in band $band", (visa.countByBand[band] ?: 0) > 0)
        }
        assertEquals(visa.requirements.size, visa.countByBand.values.sum())
        assertEquals(visa.requirements.size, visa.countByCategory.values.sum())
    }

    @Test
    fun `a stated day count is a number of days someone could act on`() {
        visa.requirements.forEach { requirement ->
            val days = requirement.days ?: return@forEach
            assertTrue("${requirement.code} grants $days days", days in 1..366)
        }
    }

    /**
     * The uncertain answers are meant to be a handful of hard territories, not the
     * bulk of the file. If this ever trips, the honest fix is more research rather
     * than a wider bound.
     */
    @Test
    fun `only a few answers are flagged uncertain`() {
        assertTrue(
            "${visa.uncertain.size} uncertain answers: ${visa.uncertain.map { it.code }}",
            visa.uncertain.size <= 12,
        )
        visa.uncertain.forEach {
            assertEquals(VisaConfidence.LOW, it.confidence)
            assertNotNull("an uncertain answer without a note is just a shrug", it.note)
        }
    }

    // --- parsing ---------------------------------------------------------------

    /**
     * An asset written by a newer build can contain a category this one has never
     * heard of. Losing that one place is survivable; losing the screen is not.
     */
    @Test
    fun `an answer from a newer build is skipped rather than fatal`() {
        val registry = VisaRegistry.parse(
            """
            {
              "passport": "DE",
              "asOf": "2026-02-17",
              "dataset": {"name": "n", "url": "https://example.org", "licence": "MIT"},
              "iataUrl": "https://example.org/iata",
              "requirements": [
                {"code": "FR", "category": "VISA_FREE", "source": "dataset"},
                {"code": "ZZ", "category": "TRANSIT_ONLY", "source": "dataset"},
                {"code": "GL", "category": "VISA_FREE", "source": "quantum_oracle"}
              ]
            }
            """.trimIndent()
        )
        assertEquals(listOf("FR"), registry.requirements.map { it.code })
        assertNull("an unreadable answer must not become a blank one", registry["ZZ"])
        assertNull(registry["GL"])
    }

    @Test
    fun `an asset with nothing readable is refused`() {
        val thrown = runCatching {
            VisaRegistry.parse(
                """
                {
                  "passport": "DE",
                  "asOf": "2026-02-17",
                  "dataset": {"name": "n", "url": "u", "licence": "MIT"},
                  "iataUrl": "i",
                  "requirements": [{"code": "FR", "category": "NONSENSE", "source": "dataset"}]
                }
                """.trimIndent()
            )
        }
        assertTrue("an empty registry should not be constructible", thrown.isFailure)
    }

    /** Blank strings in the asset must reach the UI as nothing, not as empty labels. */
    @Test
    fun `a blank optional field reads as absent`() {
        val registry = VisaRegistry.parse(
            """
            {
              "passport": "DE",
              "asOf": "2026-02-17",
              "dataset": {"name": "n", "url": "u", "licence": "MIT"},
              "iataUrl": "i",
              "requirements": [
                {"code": "FR", "category": "VISA_FREE", "source": "curated",
                 "note": "  ", "sourceUrl": "", "confidence": "high"}
              ]
            }
            """.trimIndent()
        )
        val only = registry["FR"]!!
        assertNull(only.note)
        assertNull(only.sourceUrl)
        assertEquals(VisaConfidence.HIGH, only.confidence)
    }
}
