package com.riedel.beyondborders.data.visa

import com.riedel.beyondborders.data.place.PlaceRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every shipped passport, not just DE and GB. Upstream files some whole states under
 * an area ("Australia and territories"), and the first version of the build read only
 * the null-area record, so the Canadian asset silently had no Australia, Denmark or
 * France. A holes-in-the-map bug like that is invisible in any one passport's tests,
 * so this walks all of them.
 */
class VisaAssetCoverageTest {

    private val assets = File("src/main/assets")

    private val places = PlaceRegistry.parse(File(assets, PlaceRegistry.ASSET_NAME).readText())

    private val sovereign = places.places.filter { it.sovereign == it.name }.map { it.code }.toSet()

    /**
     * The accepted holes, written by tools/visa/build_visa.py from SOVEREIGN_GAP_* in
     * rules.py (docs/visa-gap-allowlist.txt, `destination XX` and `pair PP XX` lines),
     * so this test cannot keep a stale copy of the list. A new entry in it is a data
     * hole being accepted.
     */
    private val allowlist = File("../docs/visa-gap-allowlist.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(" ") }

    private val gapDestinations = allowlist.filter { it[0] == "destination" }.map { it[1] }.toSet()
    private val gapPairs = allowlist.filter { it[0] == "pair" }.map { it[1] to it[2] }.toSet()

    private fun shippedPassports(): List<VisaRegistry> =
        assets.listFiles { f -> Regex("""visa(_[a-z]{2})?\.json""").matches(f.name) }!!
            .map { VisaRegistry.parse(it.readText()) }

    @Test
    fun `the generated gap list is not empty`() {
        assertTrue(gapDestinations.containsAll(listOf("EH", "AQ")))
        assertTrue(gapPairs.isNotEmpty())
    }

    @Test
    fun `every sovereign place resolves for every passport except its own country and the named gaps`() {
        val passports = shippedPassports()
        assertTrue("only ${passports.size} passports shipped", passports.size >= 190)
        val missing = passports.flatMap { visa ->
            val have = visa.requirements.map { it.code }.toSet()
            (sovereign - have - gapDestinations - visa.passport)
                .filter { (visa.passport to it) !in gapPairs }
                .map { "${visa.passport}->$it" }
        }
        assertEquals("sovereign places with no answer: $missing", emptyList<String>(), missing)
    }

    /** The case that started it: Canada's article files Australia under "and territories". */
    @Test
    fun `a state filed as 'and territories' upstream is still answered`() {
        val ca = shippedPassports().first { it.passport == "CA" }
        listOf("AU", "DK", "FR").forEach {
            assertTrue("CA has no answer for $it", ca[it] != null)
        }
    }

    /**
     * The ESTA rule reads free text. Canada's US note begins "ESTA exempt. Canadian
     * passport holders do not have to get an ESTA.", which a bare substring match
     * shipped as ETA for the US, Puerto Rico, Guam and the Northern Marianas.
     */
    @Test
    fun `an ESTA exemption is not an ESTA requirement`() {
        val ca = shippedPassports().first { it.passport == "CA" }
        listOf("US", "PR", "GU", "MP").forEach {
            assertEquals("CA -> $it", VisaCategory.VISA_FREE, ca[it]!!.category)
        }
        val de = shippedPassports().first { it.passport == "DE" }
        assertEquals(VisaCategory.ETA, de["US"]!!.category)
    }

    @Test
    fun `a CHECK note is cut short`() {
        shippedPassports().flatMap { v -> v.requirements.filter { it.category == VisaCategory.CHECK } }
            .forEach { assertTrue("${it.code}: ${it.note!!.length} chars", it.note!!.length <= 300) }
    }

    @Test
    fun `no shipped passport is a stub`() {
        shippedPassports().forEach { visa ->
            val fromDataset = visa.requirements.count { it.origin == VisaOrigin.DATASET }
            assertTrue("${visa.passport} has only $fromDataset dataset answers", fromDataset >= 150)
        }
    }
}
