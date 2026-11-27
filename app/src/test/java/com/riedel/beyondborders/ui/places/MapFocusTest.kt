package com.riedel.beyondborders.ui.places

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFocusTest {
    @Test
    fun `a place asked for from the list is centred once the map is ready`() {
        assertTrue(shouldFlyToSelection("NO", "NO", mapReady = true, regionActive = false))
    }

    @Test
    fun `a selection made on the map itself is never chased`() {
        assertFalse(shouldFlyToSelection(null, "NO", mapReady = true, regionActive = false))
    }

    @Test
    fun `a request for another place than the selected one is ignored`() {
        assertFalse(shouldFlyToSelection("NO", "CL", mapReady = true, regionActive = false))
    }

    @Test
    fun `nothing moves before the map is ready or while region mode hides places`() {
        assertFalse(shouldFlyToSelection("NO", "NO", mapReady = false, regionActive = false))
        assertFalse(shouldFlyToSelection("NO", "NO", mapReady = true, regionActive = true))
    }
}
