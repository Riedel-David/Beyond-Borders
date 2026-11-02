package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The passport summary card counts visa-free via [combinedTotals]; the map legend via
 * [combinedBandCounts]. Both are meant to be one number, so this pins them together
 * against the shipped assets.
 */
class VisaFreeCountAgreementTest {

    private fun load(name: String) = VisaRegistry.parse(File("src/main/assets/$name").readText())

    // Deliberately no literal counts: the release branch regenerates the visa data, so
    // the test compares derivations with each other and with the registry's own count.
    private fun assertAgree(visa: VisaRegistry) {
        val expected = visa.visaFreeCount
        assertEquals(expected, combinedTotals(visa, null).visaFree)
        assertEquals(expected, combinedBandCounts(visa, null)[VisaBand.VISA_FREE])
        assertEquals(expected, bandCodes(visa, null, lens = true)[VisaBand.VISA_FREE]?.size)
    }

    @Test
    fun `summary card and map legend agree on the visa-free count for DE`() = assertAgree(load("visa.json"))

    @Test
    fun `summary card and map legend agree on the visa-free count for KR`() = assertAgree(load("visa_kr.json"))

    @Test
    fun `two passports combined agree and never fall below either single passport`() {
        val de = load("visa.json")
        val kr = load("visa_kr.json")
        val card = combinedTotals(de, kr).visaFree
        val legend = combinedBandCounts(de, kr)[VisaBand.VISA_FREE] ?: 0
        val painted = bandCodes(de, kr, lens = true)[VisaBand.VISA_FREE]?.size ?: 0
        assertEquals(card, legend)
        assertEquals(card, painted)
        val floor = maxOf(de.visaFreeCount, kr.visaFreeCount)
        assertTrue("combined $card < single-passport max $floor", card >= floor)
    }
}
