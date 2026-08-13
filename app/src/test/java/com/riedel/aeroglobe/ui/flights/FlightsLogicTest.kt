package com.riedel.aeroglobe.ui.flights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightsLogicTest {

    @Test
    fun `lowercase with a space normalizes to upper case with a space`() {
        assertEquals("LH 400", parseFlightNumber("lh 400"))
    }

    @Test
    fun `no space between carrier and number is still parsed`() {
        assertEquals("LH 400", parseFlightNumber("LH400"))
    }

    @Test
    fun `extra surrounding whitespace is trimmed`() {
        assertEquals("LH 400", parseFlightNumber("  lh400  "))
    }

    @Test
    fun `an alphanumeric carrier code is accepted`() {
        assertEquals("5J 1234", parseFlightNumber("5j1234"))
    }

    @Test
    fun `a single letter operational suffix is kept`() {
        assertEquals("AF 1234A", parseFlightNumber("af1234a"))
    }

    @Test
    fun `blank input does not parse`() {
        assertNull(parseFlightNumber(""))
        assertNull(parseFlightNumber("   "))
    }

    @Test
    fun `text with no digits does not parse`() {
        assertNull(parseFlightNumber("LH"))
    }

    @Test
    fun `a carrier code longer than three characters does not parse`() {
        assertNull(parseFlightNumber("LUFT400"))
    }
}
