package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsHeroTest {
    private fun place(code: String) =
        Place(code, code, "Europe", "R", code, false, true, 10.0, 10.0, Bounds(0.0, 0.0, 1.0, 1.0), 1L)

    private val places = listOf("FR", "DE", "IT", "ES", "XA", "AT").map(::place)

    @Test
    fun `hero flags are lived places first then visited, each by code`() {
        val statuses = mapOf("FR" to PlaceStatus.VISITED, "DE" to PlaceStatus.LIVED, "AT" to PlaceStatus.VISITED, "IT" to PlaceStatus.WISHLIST)
        assertEquals(listOf("DE", "AT", "FR"), heroFlagCodes(places, statuses))
    }

    @Test
    fun `hero flags are capped`() {
        val statuses = places.associate { it.code to PlaceStatus.VISITED }
        assertEquals(2, heroFlagCodes(places, statuses, max = 2).size)
    }

    @Test
    fun `hero flags skip places whose flag file is missing`() {
        val statuses = mapOf("XA" to PlaceStatus.VISITED, "FR" to PlaceStatus.VISITED)
        assertEquals(listOf("FR"), heroFlagCodes(places, statuses, hasFlag = { it != "XA" }))
    }

    @Test
    fun `hero flags are empty when nothing is visited`() {
        assertEquals(emptyList<String>(), heroFlagCodes(places, emptyMap()))
    }
}
