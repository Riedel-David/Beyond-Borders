package com.riedel.beyondborders.ui.map

import com.riedel.beyondborders.ui.places.RegionBand
import com.riedel.beyondborders.ui.places.RegionStyleKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangedRegionKeysTest {
    private val a = RegionStyleKey(0, RegionBand.entries.first())
    private val b = RegionStyleKey(1, RegionBand.entries.first())

    @Test
    fun `only keys whose code set differs are reported`() {
        val old = mapOf(a to setOf("FR"), b to setOf("DE"))
        val new = mapOf(a to setOf("FR"), b to setOf("DE", "AT"))
        assertEquals(setOf(b), changedRegionKeys(old, new))
    }

    @Test
    fun `a key that disappears is reported so it can be cleared`() {
        assertEquals(setOf(a), changedRegionKeys(mapOf(a to setOf("FR")), emptyMap()))
    }

    @Test
    fun `an absent key and an empty set are the same thing`() {
        assertEquals(emptySet<RegionStyleKey>(), changedRegionKeys(mapOf(a to emptySet()), emptyMap()))
    }
}
