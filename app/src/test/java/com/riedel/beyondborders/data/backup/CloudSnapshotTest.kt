package com.riedel.beyondborders.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a fresh export is worth staging for Auto Backup to pick up.
 *
 * The trap this guards against: after "Delete everything", the next `onPause`
 * exports an empty history, and staging that anyway would leave a useless
 * "Restore" button on screen for nothing worth restoring.
 */
class CloudSnapshotTest {

    private fun export(places: Int, removals: Int) = BackupExport(json = "{}", places = places, removals = removals)

    @Test
    fun `an export with no places and no removals is not staged`() {
        assertFalse(shouldStageSnapshot(export(places = 0, removals = 0)))
    }

    @Test
    fun `an export with places is staged`() {
        assertTrue(shouldStageSnapshot(export(places = 3, removals = 0)))
    }

    @Test
    fun `an export with only removals is still staged`() {
        assertTrue(shouldStageSnapshot(export(places = 0, removals = 2)))
    }
}
