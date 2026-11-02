package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.settings.MapSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusChipTest {

    @Test
    fun `the menu offers exactly the three statuses`() {
        assertEquals(
            listOf(StatusAction.BEEN, StatusAction.LIVED, StatusAction.WANT),
            StatusAction.entries.toList(),
        )
    }

    @Test
    fun `picking a different status writes exactly that status`() {
        assertEquals(PlaceStatus.LIVED, statusTargetFor(StatusAction.LIVED, PlaceStatus.VISITED))
        assertEquals(PlaceStatus.VISITED, statusTargetFor(StatusAction.BEEN, PlaceStatus.NONE))
    }

    @Test
    fun `picking the marked status again clears it to NONE`() {
        assertEquals(PlaceStatus.NONE, statusTargetFor(StatusAction.WANT, PlaceStatus.WISHLIST))
        assertEquals(PlaceStatus.NONE, statusTargetFor(StatusAction.BEEN, PlaceStatus.VISITED))
        assertEquals(PlaceStatus.NONE, statusTargetFor(StatusAction.LIVED, PlaceStatus.LIVED))
    }

    @Test
    fun `thumbnail sample size halves while the result stays at least the target`() {
        assertEquals(2, thumbSampleSize(300, 200, 120, 84))
        assertEquals(2, thumbSampleSize(300, 200, 100, 70))
        assertEquals(8, thumbSampleSize(1200, 800, 120, 84))
        assertEquals(1, thumbSampleSize(0, 0, 120, 84))
    }

    @Test
    fun `only the current status entry is marked and an unset row marks none`() {
        assertTrue(isCurrent(StatusAction.BEEN, PlaceStatus.VISITED))
        assertFalse(isCurrent(StatusAction.LIVED, PlaceStatus.VISITED))
        assertTrue(StatusAction.entries.none { isCurrent(it, PlaceStatus.NONE) })
    }

    @Test
    fun `chip label and colour follow the status and NONE has neither`() {
        assertEquals(R.string.passport_chip_been, statusLabelRes(PlaceStatus.VISITED))
        assertEquals(R.string.passport_chip_lived, statusLabelRes(PlaceStatus.LIVED))
        assertEquals(R.string.passport_chip_want, statusLabelRes(PlaceStatus.WISHLIST))
        assertEquals(MapSlot.WISHLIST, statusSlot(PlaceStatus.WISHLIST))
        assertNull(statusLabelRes(PlaceStatus.NONE))
        assertNull(statusSlot(PlaceStatus.NONE))
    }
}
