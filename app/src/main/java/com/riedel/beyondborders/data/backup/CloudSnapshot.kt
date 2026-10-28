package com.riedel.beyondborders.data.backup

import android.app.backup.BackupManager
import android.content.Context
import java.io.File

/**
 * A copy of the travel history that lives entirely in `files/`, for Android's Auto
 * Backup to carry off without the app's cooperation.
 *
 * The database and the settings prefs are excluded from Auto Backup (see
 * `backup_rules.xml` and `data_extraction_rules.xml`) — a reinstall must start
 * genuinely empty, and "Delete everything" must actually be the end of the data. But
 * a snapshot written here, in the same [TravelBackup] JSON the file-picker export
 * already uses, is inert on arrival: the system restores it before the app ever
 * runs, so it just sits in `files/cloud/` until [SettingsViewModel] reads it through
 * the ordinary import-preview path. Nothing is applied automatically.
 */
class CloudSnapshot(context: Context) {
    private val appContext = context.applicationContext

    private companion object {
        val WRITE_LOCK = Any()
    }

    private val file: File get() = File(File(appContext.filesDir, "cloud"), "snapshot.json")

    fun exists(): Boolean = file.exists()

    /** Never throws — a snapshot is a convenience, not something worth crashing over. */
    fun read(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

    /**
     * Write the snapshot atomically: a `.tmp` sibling first, then [File.renameTo] onto
     * the real name. Auto Backup can run at any time, including mid-write, so a
     * half-written file must never be the one it sees — only a completed rename ever
     * changes what `snapshot.json` itself contains.
     *
     * A failed rename is left as a failed rename rather than turned into an exception
     * to throw and then swallow in the same breath: the `.tmp` sibling stays on disk,
     * unreferenced by anything, and the next successful `write` overwrites it, so
     * nothing worse than a wasted write happens. Same "never crash over this" stance
     * as [read] and [delete].
     */
    fun write(json: String): Boolean = synchronized(WRITE_LOCK) {
        // Serialised: two writers sharing one `.tmp` name would interleave their bytes
        // and could rename a file that is neither's complete content. A false return
        // from renameTo is a failure like any other, and must not be mistaken for a
        // snapshot this install owns.
        runCatching {
            val dir = file.parentFile ?: return@runCatching false
            dir.mkdirs()
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeText(json)
            tmp.renameTo(file)
        }.getOrDefault(false).also { if (it) markOwned() }
    }

    /**
     * Deletes the snapshot and the claim to it together: with no file there is nothing
     * to protect, and a stale marker would wrongly vouch for the next one that arrives.
     */
    fun delete() {
        synchronized(WRITE_LOCK) {
            runCatching { file.delete() }
            clearOwnership()
        }
    }

    // The marker lives in noBackupFilesDir so Auto Backup never carries it: a snapshot
    // that arrives by restore therefore has no marker, which is exactly how this install
    // tells "a file I wrote" from "a file the system handed me that nobody has read yet".
    private val marker: File get() = File(appContext.noBackupFilesDir, "cloud-snapshot-owned")

    /** True if this install wrote the snapshot, or the user has already restored from it. */
    fun isOwned(): Boolean = runCatching { marker.exists() }.getOrDefault(false)

    fun markOwned() {
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText("1")
        }
    }

    fun clearOwnership() {
        runCatching { marker.delete() }
    }

    /**
     * One-time bridge for installs that already had a snapshot before the marker
     * existed. Those were all written by this install, but have no marker; without this
     * their snapshot would be frozen forever. A reinstall that received a restore is a
     * first install ([updatedInPlace] false) and is left unclaimed.
     *
     * [localHasHistory] guards the other lookalike: an in-place update whose database is
     * still empty could be holding a restored snapshot nobody has imported. That case is
     * undecided, so the checked flag stays unset and a later pass (once there is history)
     * decides.
     */
    fun adoptLegacySnapshot(updatedInPlace: Boolean, localHasHistory: Boolean) {
        val done = File(appContext.noBackupFilesDir, "cloud-snapshot-adoption-checked")
        if (runCatching { done.exists() }.getOrDefault(true)) return
        val exists = exists()
        val owned = isOwned()
        if (shouldAdoptLegacySnapshot(exists, owned, updatedInPlace, localHasHistory)) markOwned()
        if (isAdoptionDecisionFinal(exists, owned, updatedInPlace, localHasHistory)) {
            runCatching { done.parentFile?.mkdirs(); done.writeText("1") }
        }
    }

    /**
     * Tell the system this file changed, so Auto Backup uploads the new state instead
     * of waiting for its own schedule. This only enqueues a `KeyValueBackupJob`-style
     * request — it does not upload synchronously and does not confirm anything reached
     * Google, which is why "Delete everything" cannot promise the cloud copy is gone
     * immediately, only that it will stop matching the old history once that job runs.
     */
    fun notifyBackupChanged() {
        runCatching { BackupManager(appContext).dataChanged() }
    }
}

// --- pure functions, unit-testable without Android ---------------------------

/**
 * Whether a fresh export is worth writing for Auto Backup to pick up.
 *
 * Tombstones alone do not count: a history that is nothing but removals has
 * nothing a Restore button could usefully bring back, and this is also what keeps
 * this check and [SettingsViewModel]'s "is there local history" check agreeing on
 * one notion of "there is history" — both ask only about [BackupExport.places].
 */
internal fun shouldStageSnapshot(export: BackupExport): Boolean = export.places > 0

/**
 * Whether a new snapshot may replace what is on disk.
 *
 * A snapshot that arrived by restore and has not been imported or dismissed is the
 * only copy of the history; the first mark on the fresh install must not overwrite it.
 * So writing is allowed when there is no file, or when this install owns the file.
 * Combined with [shouldStageSnapshot], which is about the export, not the file.
 */
internal fun mayReplaceSnapshot(snapshotExists: Boolean, owned: Boolean): Boolean =
    !snapshotExists || owned

/** See [CloudSnapshot.adoptLegacySnapshot]. */
internal fun shouldAdoptLegacySnapshot(
    snapshotExists: Boolean,
    owned: Boolean,
    updatedInPlace: Boolean,
    localHasHistory: Boolean,
): Boolean = snapshotExists && !owned && updatedInPlace && localHasHistory

/**
 * Whether the adoption check may be recorded as done. Only the one undecidable case
 * keeps it open: an unowned snapshot, updated in place, but no local history yet.
 */
internal fun isAdoptionDecisionFinal(
    snapshotExists: Boolean,
    owned: Boolean,
    updatedInPlace: Boolean,
    localHasHistory: Boolean,
): Boolean = !(snapshotExists && !owned && updatedInPlace && !localHasHistory)
