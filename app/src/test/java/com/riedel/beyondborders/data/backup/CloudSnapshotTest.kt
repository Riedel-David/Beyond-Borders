package com.riedel.beyondborders.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a fresh export is worth staging for Auto Backup to pick up.
 *
 * Places, not tombstones, are what makes an export worth restoring — a history
 * that is nothing but removals has nothing a Restore row could usefully bring
 * back, and places-only is also the "is there history" test `SettingsViewModel`
 * uses for the row's own visibility, so the two have to agree.
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
    fun `an export with only removals is not staged, because there is nothing to restore`() {
        assertFalse(shouldStageSnapshot(export(places = 0, removals = 2)))
    }
}
