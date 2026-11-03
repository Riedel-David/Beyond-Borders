package com.riedel.beyondborders.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {
    @Test
    fun `a phone is locked to portrait`() {
        assertTrue(shouldLockPortrait(360))
        assertTrue(shouldLockPortrait(599))
    }

    @Test
    fun `a window of 600dp or more is left free to rotate`() {
        assertFalse(shouldLockPortrait(600))
        assertFalse(shouldLockPortrait(840))
    }
}
