package com.riedel.beyondborders.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Whether the database on this phone can be opened at all. */
sealed interface DatabaseHealth {

    /** The probe has not finished. Only ever the state for the first moments of a launch. */
    data object Unknown : DatabaseHealth

    data object Usable : DatabaseHealth

    /**
     * Room refused to open it. [detail] is the exception's own message, shown verbatim
     * on the recovery screen: it names the versions involved, which is the one piece of
     * information that makes the situation diagnosable after the fact.
     */
    data class Unusable(val detail: String) : DatabaseHealth
}

/**
 * The escape hatch for a database Room will not open — a version this build has no
 * migration for, or one written by a *newer* build than this one.
 *
 * [BeyondBordersDatabase.open] deliberately has no `fallbackToDestructiveMigration`,
 * because the travel history is the one thing in the app that cannot be re-fetched
 * from anywhere. That policy is right and stays. What was missing is what happens
 * next: without this the app threw on its first query, on every launch, and the only
 * way back in was Clear Data — which destroys exactly what refusing to wipe was
 * protecting. So the failure gets a screen, the screen offers a copy of the bytes
 * first, and only then a wipe the user asks for by name.
 */
object DatabaseRecovery {

    /**
     * Open the database and let go of it again, reporting whether it worked.
     *
     * Uses its own instance rather than [AppContainer][com.riedel.beyondborders.AppContainer]'s
     * lazy one, so that a failed open never leaves the container holding a database
     * nothing can query — which is what lets [wipe] be followed by another probe
     * instead of by a process restart.
     *
     * Blocking: call it off the main thread. On a healthy database this is also where
     * any pending migration runs, which is a better place for it than the first frame.
     */
    fun probe(context: Context, name: String = BeyondBordersDatabase.NAME): DatabaseHealth {
        // A fresh install has nothing to fail on, and opening the database here would
        // create the file a moment earlier than the app otherwise would.
        if (!databaseFile(context, name).exists()) return DatabaseHealth.Usable

        // One retry: SQLITE_BUSY or a momentarily full disk fails an open exactly like a
        // version mismatch does, and reporting that as "can't read your data" would
        // send someone to the wipe button over a hiccup.
        var last: Throwable? = null
        repeat(2) {
            val db = BeyondBordersDatabase.open(context, name)
            try {
                db.openHelper.writableDatabase
                return DatabaseHealth.Usable
            } catch (t: Throwable) {
                last = t
            } finally {
                runCatching { db.close() }
            }
        }
        val t = last!!
        return DatabaseHealth.Unusable(t.message ?: t.javaClass.simpleName)
    }

    /**
     * Write every file the database is made of into [out] as a zip.
     *
     * Not just the `.db`. Room runs in WAL mode on every API level this app supports,
     * so recent commits can still be sitting in the `-wal` file, and a probe that
     * failed means nothing has checkpointed them. Handing back the main file alone
     * would produce a copy that silently drops the newest marks — the exact loss this
     * screen exists to prevent.
     */
    fun exportTo(context: Context, out: OutputStream, name: String = BeyondBordersDatabase.NAME) {
        ZipOutputStream(out.buffered()).use { zip ->
            parts(context, name).forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** True if there is anything to export. False on a fresh install. */
    fun hasData(context: Context, name: String = BeyondBordersDatabase.NAME): Boolean =
        databaseFile(context, name).exists()

    /**
     * Delete the database outright — main file, WAL, shared-memory and journal.
     *
     * Only ever reached from an explicit, named tap on the recovery screen. The next
     * [probe] then finds nothing and reports [DatabaseHealth.Usable], and the app
     * opens a new empty database on its way past.
     */
    fun wipe(context: Context, name: String = BeyondBordersDatabase.NAME): Boolean =
        SQLiteDatabase.deleteDatabase(databaseFile(context, name))

    private fun databaseFile(context: Context, name: String): File = context.getDatabasePath(name)

    private fun parts(context: Context, name: String): List<File> {
        val main = databaseFile(context, name)
        return listOf(main, File("${main.path}-wal"), File("${main.path}-shm"), File("${main.path}-journal"))
            .filter { it.exists() }
    }
}
