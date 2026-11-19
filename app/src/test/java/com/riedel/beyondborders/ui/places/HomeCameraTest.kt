package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCameraTest {
    private val germany = Bounds(5.87, 47.27, 15.04, 55.06)

    @Test
    fun `Germany gets a slight zoom around its box`() {
        assertEquals(3.7, homeCamera(germany).zoom, 0.3)
    }

    @Test
    fun `the camera is centred on the middle of the box`() {
        val c = homeCamera(germany)
        assertEquals(51.165, c.latitude, 0.001)
        assertEquals(10.455, c.longitude, 0.001)
    }

    @Test
    fun `Netherlands is clamped to the maximum zoom`() {
        assertEquals(4.0, homeCamera(Bounds(3.3, 50.75, 7.2, 53.55)).zoom, 0.0)
    }

    @Test
    fun `Russia is clamped to the minimum zoom`() {
        assertEquals(1.0, homeCamera(Bounds(19.6, 41.2, 180.0, 77.7)).zoom, 0.0)
    }

    @Test
    fun `a box across the antimeridian wraps its longitude span`() {
        val c = homeCamera(Bounds(170.0, -20.0, -170.0, -10.0))
        assertEquals(180.0, Math.abs(c.longitude), 0.001)
        assertTrue(c.zoom >= 1.0 && c.zoom <= 4.0)
    }

    @Test
    fun `a zero span does not produce Infinity or NaN`() {
        val c = homeCamera(Bounds(10.0, 50.0, 10.0, 50.0))
        assertTrue(c.zoom.isFinite())
        assertEquals(4.0, c.zoom, 0.0)
    }

    @Test
    fun `first run centres on an untouched or near-world camera but not on a moved one`() {
        assertEquals(true, shouldCentreOnHome(null, "DE", null))
        assertEquals(true, shouldCentreOnHome(null, "DE", MapCamera(18.0001, 10.0, 0.02)))
        assertEquals(false, shouldCentreOnHome(null, "DE", MapCamera(45.0, 20.0, 5.0)))
    }

    @Test
    fun `the same passport never centres twice but a changed one always does`() {
        assertEquals(false, shouldCentreOnHome("DE", "DE", null))
        assertEquals(true, shouldCentreOnHome("DE", "FR", MapCamera(45.0, 20.0, 5.0)))
    }
}
