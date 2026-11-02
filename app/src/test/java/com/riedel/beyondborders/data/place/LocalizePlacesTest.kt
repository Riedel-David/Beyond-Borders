package com.riedel.beyondborders.data.place

import com.riedel.beyondborders.ui.settings.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizePlacesTest {

    private fun place(code: String, name: String, sovereign: String = name) = Place(
        code = code,
        name = name,
        continent = "Europe",
        region = "Europe",
        sovereign = sovereign,
        uninhabited = false,
        lat = 0.0,
        lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
    )

    private val german = Locale.GERMAN
    private val fake = mapOf("DE" to "Deutschland", "AT" to "Österreich", "EG" to "Ägypten", "DK" to "Dänemark", "GL" to "Grönland", "XK" to "Kosovo-de")
    private val lookup: (String, Locale) -> String? = { code, _ -> fake[code] }

    private val places = listOf(
        place("DE", "Germany"),
        place("AT", "Austria"),
        place("EG", "Egypt"),
        place("DK", "Denmark"),
        place("GL", "Greenland", sovereign = "Denmark"),
        place("XK", "Kosovo"),
        place("FR", "France"),
        place("ZZ", "Nowhere"),
    )

    @Test
    fun `english keeps the bundled names untouched`() {
        assertEquals(places, localizePlaces(places, Locale.ENGLISH, lookup))
    }

    @Test
    fun `german names replace the english ones for real ISO codes`() {
        val byCode = localizePlaces(places, german, lookup).associateBy { it.code }
        assertEquals("Deutschland", byCode.getValue("DE").name)
        assertEquals("Germany", byCode.getValue("DE").englishName)
    }

    @Test
    fun `private XA to XZ codes keep the english name even if the lookup answers`() {
        assertEquals("Kosovo", localizePlaces(places, german, lookup).first { it.code == "XK" }.name)
    }

    @Test
    fun `unknown codes and codes the lookup cannot name keep the english name`() {
        val byCode = localizePlaces(places, german, lookup).associateBy { it.code }
        assertEquals("Nowhere", byCode.getValue("ZZ").name)
        assertEquals("France", byCode.getValue("FR").name)
        val echo = localizePlaces(places, german) { code, _ -> code }
        assertEquals("Germany", echo.first { it.code == "DE" }.name)
        val blank = localizePlaces(places, german) { _, _ -> "" }
        assertEquals("Germany", blank.first { it.code == "DE" }.name)
    }

    @Test
    fun `the sovereign is localized through the place of the same english name, else left english`() {
        val byCode = localizePlaces(places, german, lookup).associateBy { it.code }
        assertEquals("Dänemark", byCode.getValue("GL").sovereign)
        val orphan = listOf(place("GL", "Greenland", sovereign = "Denmark"))
        assertEquals("Denmark", localizePlaces(orphan, german, lookup).single().sovereign)
    }

    @Test
    fun `a place that is its own sovereign still compares equal to its name after localizing`() {
        val de = localizePlaces(places, german, lookup).first { it.code == "DK" }
        assertEquals(de.name, de.sovereign)
    }

    @Test
    fun `searchKey matches both the english and the german name`() {
        val de = localizePlaces(places, german, lookup).first { it.code == "DE" }
        assertTrue(de.searchKey.contains("germany"))
        assertTrue(de.searchKey.contains("deutschland"))
        assertTrue(localizePlaces(places, german, lookup).first { it.code == "GL" }.searchKey.contains("denmark"))
    }

    @Test
    fun `german names with umlauts sort under their base letter`() {
        val sorted = localizePlaces(places, german, lookup).sortedBy { it.sortKey }.map { it.name }
        assertTrue(sorted.indexOf("Ägypten") < sorted.indexOf("Dänemark"))
        assertTrue(sorted.indexOf("Nowhere") < sorted.indexOf("Österreich"))
        assertEquals("A", localizePlaces(places, german, lookup).first { it.code == "EG" }.sortKey.take(1).uppercase())
        assertEquals("O", localizePlaces(places, german, lookup).first { it.code == "AT" }.sortKey.take(1).uppercase())
    }

    @Test
    fun `the real JDK names Germany and France in german`() {
        val real = localizePlaces(places, german)
        assertEquals("Deutschland", real.first { it.code == "DE" }.name)
        assertEquals("Frankreich", real.first { it.code == "FR" }.name)
    }

    @Test
    fun `the language picker maps application locale tags to options and back`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTags("de-DE"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTags("de,en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("fr"))
        assertEquals("de", AppLanguage.GERMAN.tag)
    }

    @Test
    fun `languages the map glyphs cannot draw keep the english names`() {
        for (tag in listOf("ja", "ru", "ar", "fr")) {
            assertEquals(places, localizePlaces(places, Locale.forLanguageTag(tag), lookup))
        }
    }

    @Test
    fun `a territory whose sovereign has a long form still gets a localized sovereign`() {
        val ps = listOf(
            place("US", "United States"),
            place("PR", "Puerto Rico", sovereign = "United States of America"),
        )
        val out = localizePlaces(ps, german) { code, _ -> mapOf("US" to "Vereinigte Staaten", "PR" to "Puerto Rico")[code] }
        assertEquals("Vereinigte Staaten", out.first { it.code == "PR" }.sovereign)
    }

    @Test
    fun `every sovereign in the bundled data matches a place name or is in the explicit map`() {
        val arr = org.json.JSONArray(java.io.File("src/main/assets/places.json").readText())
        val rows = (0 until arr.length()).map { arr.getJSONObject(it) }
        val names = rows.map { it.getString("name") }.toSet()
        val codes = rows.map { it.getString("code") }.toSet()
        val missing = rows.map { it.getString("sovereign") }.toSet()
            .filter { it !in names && it !in SOVEREIGN_CODES }
        assertEquals(emptyList<String>(), missing)
        assertTrue(SOVEREIGN_CODES.values.all { it in codes })
    }
}
