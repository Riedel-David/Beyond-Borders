package com.riedel.beyondborders.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotRestoreRowTest {

    @Test
    fun `the row is offered over an empty history whether or not the snapshot is owned`() {
        assertTrue(shouldOfferSnapshotRestore(present = true, owned = true, historyEmpty = true))
        assertTrue(shouldOfferSnapshotRestore(present = true, owned = false, historyEmpty = true))
    }

    @Test
    fun `an unowned snapshot stays offered over existing history so it can still be claimed`() {
        assertTrue(shouldOfferSnapshotRestore(present = true, owned = false, historyEmpty = false))
    }

    @Test
    fun `an owned snapshot over existing history hides the row`() {
        assertFalse(shouldOfferSnapshotRestore(present = true, owned = true, historyEmpty = false))
    }

    @Test
    fun `no snapshot file means no row`() {
        assertFalse(shouldOfferSnapshotRestore(present = false, owned = false, historyEmpty = true))
    }
}
