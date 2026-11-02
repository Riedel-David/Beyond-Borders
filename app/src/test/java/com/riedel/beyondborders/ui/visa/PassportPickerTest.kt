package com.riedel.beyondborders.ui.visa

import org.junit.Assert.assertEquals
import org.junit.Test

class PassportPickerTest {

    private val names = mapOf("EG" to "Ägypten", "DE" to "Deutschland", "FR" to "Frankreich")
    private val english = mapOf("EG" to "Egypt", "DE" to "Germany", "FR" to "France")
    private val options = listOf("EG", "DE", "FR")

    @Test
    fun `an unaccented query finds an accented name`() {
        assertEquals(listOf("EG"), filterPassportCodes(options, "agypten", names, english))
    }

    @Test
    fun `the english name matches even when the shown name is german`() {
        assertEquals(listOf("DE"), filterPassportCodes(options, "Germany", names, english))
    }

    @Test
    fun `the code matches and a blank query keeps everything`() {
        assertEquals(listOf("FR"), filterPassportCodes(options, "fr", names, emptyMap()).filter { it == "FR" })
        assertEquals(options, filterPassportCodes(options, "  ", names, english))
    }

    @Test
    fun `without english names only the shown name and code are searched`() {
        assertEquals(emptyList<String>(), filterPassportCodes(options, "Germany", names))
    }
}
