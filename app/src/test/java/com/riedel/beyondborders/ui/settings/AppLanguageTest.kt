package com.riedel.beyondborders.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `the language picker maps application locale tags to options and back`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTags("de-DE"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTags("de,en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("ja"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTags("es-MX"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTags("es,en"))
        assertEquals("de", AppLanguage.GERMAN.tag)
        assertEquals("es", AppLanguage.SPANISH.tag)
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTags("fr-CA"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTags("pt-BR"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTags("pt-PT"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTags("pt-PT,en"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromTags("uk"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTags("ru-RU"))
    }

    @Test
    fun `Portuguese asks for pt-PT on a phone set to Portugal and plain pt elsewhere`() {
        assertEquals("pt-PT", AppLanguage.PORTUGUESE.localeTag("PT"))
        assertEquals("pt", AppLanguage.PORTUGUESE.localeTag("BR"))
        assertEquals("pt", AppLanguage.PORTUGUESE.localeTag(""))
        assertEquals("de", AppLanguage.GERMAN.localeTag("PT"))
    }

    @Test
    fun `European Portuguese is chosen when any system locale is Portuguese in Portugal`() {
        assertEquals("PT", AppLanguage.portugueseRegion(listOf("en" to "GB", "pt" to "PT")))
        assertEquals("PT", AppLanguage.portugueseRegion(listOf("pt" to "PT")))
    }

    @Test
    fun `a Portuguese locale from Brazil or an empty list does not select pt-PT`() {
        assertEquals("BR", AppLanguage.portugueseRegion(listOf("pt" to "BR", "en" to "GB")))
        assertEquals("", AppLanguage.portugueseRegion(emptyList()))
    }

    @Test
    fun `the first locale region is kept when no Portuguese Portugal entry exists`() {
        assertEquals("PT", AppLanguage.portugueseRegion(listOf("en" to "PT")))
    }

    @Test
    fun `the first Portuguese locale decides between Brazil and Portugal`() {
        assertEquals("BR", AppLanguage.portugueseRegion(listOf("pt" to "BR", "en" to "GB", "pt" to "PT")))
        assertEquals("PT", AppLanguage.portugueseRegion(listOf("pt" to "PT", "pt" to "BR")))
        assertEquals("", AppLanguage.portugueseRegion(listOf("en" to "PT", "pt" to "")))
    }
}
