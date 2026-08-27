package com.riedel.beyondborders.data.visa

import com.riedel.beyondborders.data.place.PlaceRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GB visa asset, checked the same way [VisaAssetTest] checks DE's — shape, not
 * immigration law — plus the handful of facts that are the whole reason GB has its
 * own rules.py programs rather than sharing DE's tuples: the Common Travel Area,
 * and the total absence of a Foreign Office link-out, which is DE-only by design.
 */
class VisaAssetGbTest {

    private val visa: VisaRegistry by lazy {
        val asset = File("src/main/assets/${VisaRegistry.assetNameFor(Nationality.GB)}")
        assertTrue(
            "missing ${asset.path} - run tools/visa/build_visa.py --passport GB",
            asset.exists(),
        )
        VisaRegistry.parse(asset.readText())
    }

    private val places: PlaceRegistry by lazy {
        PlaceRegistry.parse(File("src/main/assets/${PlaceRegistry.ASSET_NAME}").readText())
    }

    @Test
    fun `every place has exactly one GB visa answer`() {
        val placeCodes = places.places.map { it.code }.toSet()
        val visaCodes = visa.requirements.map { it.code }.toSet()
        assertEquals(emptySet<String>(), placeCodes - visaCodes)
        assertEquals(emptySet<String>(), visaCodes - placeCodes)
    }

    @Test
    fun `the dataset is attributed for GB`() {
        assertEquals("GB", visa.passport)
        assertTrue(
            "asOf is not an ISO date: ${visa.asOf}",
            Regex("""\d{4}-\d{2}-\d{2}""").matches(visa.asOf),
        )
    }

    /** The design decision the Common Travel Area rules.py program exists for. */
    @Test
    fun `the Crown Dependencies are home for a British passport`() {
        assertEquals(VisaCategory.HOME, visa["JE"]!!.category)
        assertEquals(VisaCategory.HOME, visa["GG"]!!.category)
        assertEquals(VisaCategory.HOME, visa["IM"]!!.category)
    }

    /** The Foreign Office link-out is DE-only; a GB build has no equivalent feed. */
    @Test
    fun `no GB answer links to a Foreign Office page`() {
        visa.requirements.forEach {
            assertNull("${it.code} has an officialUrl in a GB build", it.officialUrl)
        }
    }

    @Test
    fun `the GB headline numbers are plausible`() {
        assertTrue("only ${visa.visaFreeCount} visa-free", visa.visaFreeCount in 100..220)
        assertTrue(visa.uncertain.size <= 25)
    }
}
