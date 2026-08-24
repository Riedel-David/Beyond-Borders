package com.riedel.beyondborders.ui.map

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class MapSearchTest {

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

    private val places = listOf(
        place("DK", "Denmark"),
        place("GL", "Greenland", sovereign = "Denmark"),
        place("FR", "France"),
        place("RE", "Réunion", sovereign = "France"),
    )

    @Test
    fun `an empty query finds nothing, so the panel starts blank rather than listing everyone`() {
        assertEquals(emptyList<Place>(), searchMapMatches(places, ""))
        assertEquals(emptyList<Place>(), searchMapMatches(places, "   "))
    }

    @Test
    fun `search ignores case and accents, same as the passport`() {
        assertEquals(listOf("RE"), searchMapMatches(places, "reunion").map { it.code })
    }

    @Test
    fun `search matches the sovereign so Greenland is findable under Denmark`() {
        assertEquals(setOf("DK", "GL"), searchMapMatches(places, "denmark").map { it.code }.toSet())
    }

    @Test
    fun `results are capped so this stays a picker, not a second passport list`() {
        val many = (1..10).map { place("C$it", "Country $it") }
        assertEquals(5, searchMapMatches(many, "country").size)
    }
}
