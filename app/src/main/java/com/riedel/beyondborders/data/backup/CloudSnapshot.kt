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
    fun write(json: String) {
        runCatching {
            val dir = file.parentFile ?: return
            dir.mkdirs()
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeText(json)
            tmp.renameTo(file)
        }
    }

    fun delete() {
        runCatching { file.delete() }
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
