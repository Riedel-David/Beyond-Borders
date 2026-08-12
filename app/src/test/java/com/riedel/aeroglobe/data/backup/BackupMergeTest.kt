package com.riedel.aeroglobe.data.backup

import com.riedel.aeroglobe.data.place.PlaceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Import is a merge, and a merge is where travel history gets lost quietly.
 *
 * These cases are the ones that would be tedious and slow to check by importing
 * files on a phone, and invisible when they go wrong: an old backup silently
 * overwriting this year's trips, an un-marking that fails to propagate, a code from
 * a differently-curated dataset landing in the visited count.
 */
class BackupMergeTest {

    private val known = setOf("DK", "GL", "FR", "SE")

    private fun entry(
        code: String,
        updatedAt: Long,
        status: PlaceStatus = PlaceStatus.VISITED,
        deletedAt: Long? = null,
    ) = TravelBackup.Entry(code = code, status = status, updatedAt = updatedAt, deletedAt = deletedAt)

    @Test
    fun `a place with no local row is added`() {
        val plan = BackupMerge.plan(listOf(entry("DK", 100)), emptyMap(), known)
        assertEquals(1, plan.added)
        assertEquals(listOf("DK"), plan.apply.map { it.code })
    }

    @Test
    fun `a newer entry replaces the local row`() {
        val plan = BackupMerge.plan(listOf(entry("DK", 200)), mapOf("DK" to 100L), known)
        assertEquals(1, plan.updated)
        assertEquals(0, plan.added)
        assertEquals(listOf("DK"), plan.apply.map { it.code })
    }

    /** Restoring last year's backup must not undo this year's trips. */
    @Test
    fun `an older entry loses to the local row`() {
        val plan = BackupMerge.plan(listOf(entry("DK", 100)), mapOf("DK" to 200L), known)
        assertEquals(0, plan.added + plan.updated)
        assertEquals(1, plan.unchanged)
        assertEquals(emptyList<String>(), plan.apply.map { it.code })
    }

    /** So that importing the same file twice is a no-op instead of a rewrite. */
    @Test
    fun `an entry with the same timestamp changes nothing`() {
        val plan = BackupMerge.plan(listOf(entry("DK", 100)), mapOf("DK" to 100L), known)
        assertEquals(0, plan.apply.size)
        assertEquals(1, plan.unchanged)
    }

    @Test
    fun `a newer un-marking is applied so the deletion propagates`() {
        val plan = BackupMerge.plan(
            listOf(entry("DK", 200, deletedAt = 200)),
            mapOf("DK" to 100L),
            known,
        )
        assertEquals(1, plan.updated)
        assertEquals(200L, plan.apply.single().deletedAt)
    }

    /**
     * A tombstone for a place this device never marked carries no information: the
     * file and the database already agree that it is unmarked, and storing the row
     * would only leave something behind that no screen can show.
     */
    @Test
    fun `an un-marking for a place with no local row is dropped`() {
        val plan = BackupMerge.plan(listOf(entry("DK", 200, deletedAt = 200)), emptyMap(), known)
        assertEquals(0, plan.added)
        assertEquals(1, plan.unchanged)
        assertEquals(emptyList<String>(), plan.apply.map { it.code })
    }

    @Test
    fun `a code this build has no place for is skipped and named`() {
        val plan = BackupMerge.plan(
            listOf(entry("DK", 100), entry("XX", 100), entry("ZZ", 100)),
            emptyMap(),
            known,
        )
        assertEquals(listOf("DK"), plan.apply.map { it.code })
        assertEquals(listOf("XX", "ZZ"), plan.unknownCodes)
        assertEquals(2, plan.skipped)
    }

    /**
     * Rows dropped for an unrecognised status never reach [BackupMerge.plan] — the
     * decoder already threw them away — so they are carried in rather than counted
     * here. They still belong in [BackupMerge.Plan.skipped], because from the user's
     * side a row the app refused to take is a row the app skipped.
     */
    @Test
    fun `statuses dropped by the decoder are carried through and counted as skipped`() {
        val plan = BackupMerge.plan(
            listOf(entry("DK", 100), entry("XX", 100)),
            emptyMap(),
            known,
            unknownStatuses = listOf("worked", "worked"),
        )
        assertEquals(listOf("worked", "worked"), plan.unknownStatuses)
        // One unknown code plus two dropped rows; the applied DK is not skipped.
        assertEquals(3, plan.skipped)
    }

    /** A hand-edited file, or two exports concatenated. Newest wins, once. */
    @Test
    fun `a code repeated in the file is applied once at its newest`() {
        val plan = BackupMerge.plan(
            listOf(
                entry("DK", 100, PlaceStatus.VISITED),
                entry("DK", 300, PlaceStatus.WISHLIST),
                entry("DK", 200, PlaceStatus.VISITED),
            ),
            emptyMap(),
            known,
        )
        assertEquals(1, plan.apply.size)
        assertEquals(300L, plan.apply.single().updatedAt)
        assertEquals(PlaceStatus.WISHLIST, plan.apply.single().status)
        assertEquals(1, plan.added)
        assertEquals(0, plan.unchanged)
    }

    /**
     * The app's founding bug, in import form: a file that marks Denmark must not
     * bring Greenland with it, and there is nothing in the merge that could relate
     * the two — which is the point.
     */
    @Test
    fun `marking one place imports exactly that place`() {
        val plan = BackupMerge.plan(listOf(entry("DK", 100)), emptyMap(), known)
        assertEquals(setOf("DK"), plan.apply.map { it.code }.toSet())
    }

    @Test
    fun `a tombstoned local row can still be beaten by a newer marking`() {
        // Local row is a tombstone at t=100; the file re-marks the place at t=200.
        val plan = BackupMerge.plan(listOf(entry("GL", 200)), mapOf("GL" to 100L), known)
        assertEquals(1, plan.updated)
        assertEquals(null, plan.apply.single().deletedAt)
    }

    @Test
    fun `an empty file changes nothing`() {
        val plan = BackupMerge.plan(emptyList(), mapOf("DK" to 100L), known)
        assertEquals(0, plan.added + plan.updated + plan.unchanged)
        assertEquals(emptyList<TravelBackup.Entry>(), plan.apply)
    }
}
