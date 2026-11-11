package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.visa.VisaRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Wishlist, visa-free for me" chip: which rows it keeps and when it is offered. */
class WishlistVisaFreeTest {

    private fun place(code: String, name: String) = Place(
        code = code, name = name, continent = "Europe", region = "Europe", sovereign = name,
        uninhabited = false, lat = 0.0, lon = 0.0, bounds = Bounds(-1.0, -1.0, 1.0, 1.0),
    )

    private val places = listOf(
        place("DK", "Denmark"), place("GL", "Greenland"), place("US", "United States"),
        place("IN", "India"), place("DE", "Germany"),
    )

    private fun registry(passport: String, rows: String) = VisaRegistry.parse(
        """
        {"passport": "$passport", "asOf": "2026-02-17",
         "dataset": {"name": "x", "url": "https://example.org/d", "licence": "CC BY-SA 4.0",
           "licenceUrl": "https://example.org/l", "sourceUrl": "https://example.org/s",
           "sourceRevision": 1},
         "iataUrl": "https://example.org/iata",
         "requirements": [$rows]}
        """.trimIndent()
    )

    // Denmark needs a visa and Greenland does not: the two must never share an answer.
    private val visa = registry(
        "AA",
        """
        {"code": "DK", "category": "VISA_REQUIRED", "source": "dataset"},
        {"code": "GL", "category": "VISA_FREE", "source": "dataset"},
        {"code": "US", "category": "ETA", "source": "dataset"},
        {"code": "IN", "category": "VISA_FREE", "source": "dataset"},
        {"code": "DE", "category": "HOME", "source": "dataset"}
        """,
    )

    private fun codes(
        statuses: Map<String, PlaceStatus>,
        visa: VisaRegistry? = this.visa,
        second: VisaRegistry? = null,
    ) = buildRows(places, statuses, "", StatusFilter.WISHLIST_VISA_FREE, Grouping.ALPHABETICAL, visa, second)
        .map { it.place.code }

    @Test
    fun `keeps only wishlist places that are visa-free`() {
        val st = mapOf(
            "GL" to PlaceStatus.WISHLIST, "DK" to PlaceStatus.WISHLIST, "US" to PlaceStatus.WISHLIST,
            "IN" to PlaceStatus.VISITED,
        )
        assertEquals(listOf("GL"), codes(st))
    }

    @Test
    fun `Denmark and Greenland stay separate`() {
        assertEquals(emptyList<String>(), codes(mapOf("DK" to PlaceStatus.WISHLIST)))
        assertEquals(listOf("GL"), codes(mapOf("GL" to PlaceStatus.WISHLIST)))
    }

    @Test
    fun `no passport means no row matches`() {
        assertEquals(emptyList<String>(), codes(mapOf("GL" to PlaceStatus.WISHLIST), visa = null))
    }

    @Test
    fun `no wishlist means no row matches`() {
        assertEquals(emptyList<String>(), codes(mapOf("GL" to PlaceStatus.VISITED)))
        assertEquals(emptyList<String>(), codes(emptyMap()))
    }

    @Test
    fun `a second passport can make a place visa-free`() {
        val second = registry("BB", """{"code": "DK", "category": "VISA_FREE", "source": "dataset"}""")
        assertEquals(listOf("DK"), codes(mapOf("DK" to PlaceStatus.WISHLIST), second = second))
    }

    @Test
    fun `the chip needs a passport and at least one wishlist place`() {
        val wish = mapOf("DK" to PlaceStatus.WISHLIST)
        assertTrue(offerWishlistVisaFree(visa, wish))
        assertFalse(offerWishlistVisaFree(null, wish))
        assertFalse(offerWishlistVisaFree(visa, mapOf("DK" to PlaceStatus.VISITED)))
        assertFalse(offerWishlistVisaFree(visa, emptyMap()))
    }
}
