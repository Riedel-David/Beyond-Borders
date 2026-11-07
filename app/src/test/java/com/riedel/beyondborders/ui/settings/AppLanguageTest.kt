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
    }
}
