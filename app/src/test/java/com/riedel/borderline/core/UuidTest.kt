package com.riedel.borderline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidTest {

    @Test
    fun `has the canonical shape and version`() {
        val id = Uuid.v7(1_700_000_000_000L)
        assertTrue(id, Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id))
    }

    @Test
    fun `encodes the timestamp so ids sort by creation time`() {
        val early = Uuid.v7(1_700_000_000_000L)
        val late = Uuid.v7(1_700_000_001_000L)
        // Lexicographic order must match chronological order - that is the whole
        // reason for v7 over v4 (see the class kdoc).
        assertTrue("$early should sort before $late", early < late)
    }

    @Test
    fun `timestamp round-trips`() {
        val millis = 1_723_456_789_012L
        val id = Uuid.v7(millis)
        val hex = id.replace("-", "").take(12)
        assertEquals(millis, hex.toLong(16))
    }

    @Test
    fun `ids from the same millisecond still differ`() {
        val millis = 1_700_000_000_000L
        assertNotEquals(Uuid.v7(millis), Uuid.v7(millis))
    }
}
