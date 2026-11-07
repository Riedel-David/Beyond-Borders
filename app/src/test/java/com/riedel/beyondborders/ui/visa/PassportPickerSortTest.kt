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

    @Test
    fun `ukrainian and russian passports keep their own letters in alphabet order`() {
        val uk = mapOf("JM" to "Ямайка", "IT" to "Італія", "EG" to "Єгипет", "DE" to "Німеччина", "YE" to "Ємен")
        // Є, І, Н, Я: the Ukrainian letters keep their place instead of sorting after Я.
        assertEquals(listOf("EG", "YE", "IT", "DE", "JM"), sortedPassportCodes(uk))
        val ru = mapOf("YE" to "Йемен", "JO" to "Иордания", "KE" to "Кения")
        assertEquals(listOf("JO", "YE", "KE"), sortedPassportCodes(ru))
    }
}
