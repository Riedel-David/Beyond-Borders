package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCameraTest {
    private val germany = Bounds(5.87, 47.27, 15.04, 55.06)

    @Test
    fun `Germany gets a slight zoom around its box`() {
        assertEquals(2.6, homeCamera(germany).zoom, 0.3)
    }

    @Test
    fun `the camera is centred on the middle of the box`() {
        val c = homeCamera(germany)
        val mid = (latitudeToMercator(47.27) + latitudeToMercator(55.06)) / 2
        assertEquals(mercatorToLatitude(mid), c.latitude, 1e-6)
        assertEquals(10.455, c.longitude, 0.001)
    }

    @Test
    fun `Netherlands is clamped to the maximum zoom`() {
        assertEquals(3.0, homeCamera(Bounds(3.3, 50.75, 7.2, 53.55)).zoom, 0.0)
    }

    @Test
    fun `Russia is clamped to the minimum zoom`() {
        assertEquals(1.0, homeCamera(Bounds(19.6, 41.2, 180.0, 77.7)).zoom, 0.0)
    }

    @Test
    fun `a box across the antimeridian wraps its longitude span`() {
        val c = homeCamera(Bounds(170.0, -20.0, -170.0, -10.0))
        assertEquals(180.0, Math.abs(c.longitude), 0.001)
        assertTrue(c.zoom >= 1.0 && c.zoom <= 3.0)
    }

    @Test
    fun `a zero span does not produce Infinity or NaN`() {
        val c = homeCamera(Bounds(10.0, 50.0, 10.0, 50.0))
        assertTrue(c.zoom.isFinite())
        assertEquals(3.0, c.zoom, 0.0)
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

    private val norway = Bounds(4.6, 58.0, 31.1, 71.2)

    @Test
    fun `latitude survives a Mercator round trip`() {
        for (lat in listOf(-80.0, -33.5, 0.0, 51.2, 71.2, 84.0)) {
            assertEquals(lat, mercatorToLatitude(latitudeToMercator(lat)), 1e-9)
        }
    }

    @Test
    fun `a high-latitude country centres north of its mean latitude`() {
        val mean = (norway.north + norway.south) / 2
        assertTrue(homeCamera(norway).latitude > mean + 0.5)
    }

    @Test
    fun `an equatorial box keeps its mean latitude`() {
        assertEquals(0.0, homeCamera(Bounds(10.0, -5.0, 20.0, 5.0)).latitude, 1e-9)
    }

    @Test
    fun `zoom grows with the map width`() {
        val narrow = homeCamera(germany, 360.0).zoom
        val wide = homeCamera(germany, 800.0).zoom
        assertTrue(wide > narrow)
    }

    @Test
    fun `zoom stays within 1 to 3 at any width`() {
        for (w in listOf(1.0, 200.0, 400.0, 1200.0, 5000.0)) {
            for (b in listOf(germany, norway, Bounds(19.6, 41.2, 180.0, 77.7))) {
                assertTrue(homeCamera(b, w).zoom in 1.0..3.0)
            }
        }
    }

    @Test
    fun `400 dp reproduces the zoom the fixed phone width gave`() {
        val old = Math.log(100.0 * 360.0 / (512.0 * ((55.06 - 47.27) * 1.5))) / Math.log(2.0)
        assertEquals(old, homeCamera(germany, 400.0).zoom, 1e-9)
        assertEquals(old, homeCamera(germany).zoom, 1e-9)
    }
}
