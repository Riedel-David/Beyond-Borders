package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The passport summary card counts visa-free via [combinedTotals]; the map legend via
 * [combinedBandCounts]. Both are meant to be one number, so this pins them together
 * against the shipped assets.
 */
class VisaFreeCountAgreementTest {

    private fun load(name: String) = VisaRegistry.parse(File("src/main/assets/$name").readText())

    private fun assertAgree(visa: VisaRegistry, expected: Int) {
        assertEquals(expected, combinedTotals(visa, null).visaFree)
        assertEquals(expected, combinedBandCounts(visa, null)[VisaBand.VISA_FREE])
        assertEquals(expected, visa.visaFreeCount)
    }

    @Test
    fun `summary card and map legend agree on the visa-free count for DE`() = assertAgree(load("visa.json"), DE_VISA_FREE)

    @Test
    fun `summary card and map legend agree on the visa-free count for KR`() = assertAgree(load("visa_kr.json"), KR_VISA_FREE)

    @Test
    fun `summary card and map legend agree with two passports combined`() {
        val de = load("visa.json")
        val kr = load("visa_kr.json")
        assertEquals(combinedTotals(de, kr).visaFree, combinedBandCounts(de, kr)[VisaBand.VISA_FREE])
    }

    private companion object {
        const val DE_VISA_FREE = 157
        const val KR_VISA_FREE = 143
    }
}
