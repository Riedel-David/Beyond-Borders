package com.riedel.beyondborders.ui.visa

import org.junit.Assert.assertEquals
import org.junit.Test

class PassportPickerSortTest {
    @Test
    fun `passports sort by the folded displayed name so Aegypten comes before Zypern`() {
        val names = mapOf("CY" to "Zypern", "EG" to "Ägypten", "DE" to "Deutschland")
        assertEquals(listOf("EG", "DE", "CY"), sortedPassportCodes(names))
        assertEquals(listOf("EG", "CY"), sortedPassportCodes(names, exclude = setOf("DE")))
    }
}
