package com.riedel.beyondborders.ui.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelAdviceTest {
    @Test
    fun `a known country links to its FCDO page`() {
        assertEquals("https://www.gov.uk/foreign-travel-advice/thailand", travelAdviceUrl("TH"))
    }

    @Test
    fun `the code is case-insensitive`() {
        assertEquals(travelAdviceUrl("TH"), travelAdviceUrl("th"))
    }

    @Test
    fun `countries whose slug is not the plain name use the verified slug`() {
        assertEquals("https://www.gov.uk/foreign-travel-advice/usa", travelAdviceUrl("US"))
        assertEquals("https://www.gov.uk/foreign-travel-advice/the-gambia", travelAdviceUrl("GM"))
        assertEquals("https://www.gov.uk/foreign-travel-advice/cape-verde", travelAdviceUrl("CV"))
    }

    @Test
    fun `places with no page return null so no row is shown`() {
        assertNull(travelAdviceUrl("GB"))
        assertNull(travelAdviceUrl("VA"))
        assertNull(travelAdviceUrl("XN"))
        assertNull(travelAdviceUrl(""))
        assertNull(travelAdviceUrl("ZZ"))
    }

    @Test
    fun `every url is https on the FCDO host and building one needs no network`() {
        val urls = listOf("FR", "JP", "BR", "ZA", "NZ").map { travelAdviceUrl(it)!! }
        urls.forEach { assertTrue(it.startsWith("https://www.gov.uk/foreign-travel-advice/")) }
    }
}
