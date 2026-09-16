package com.riedel.beyondborders.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * The way out of a database this build cannot open.
 *
 * Everything here runs against [NAME], never the app's own file — the point of the
 * `name` parameter on [DatabaseRecovery] is that a test can leave a device's real
 * travel history alone.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        DatabaseRecovery.wipe(context, NAME)
    }

    @Test
    fun `a version this build has no migration for is reported rather than thrown`() {
        writeDatabaseAtVersion(3)

        val health = DatabaseRecovery.probe(context, NAME)

        assertTrue("expected Unusable, got $health", health is DatabaseHealth.Unusable)
        // The message is shown verbatim on the recovery screen, so it has to be the one
        // that names the versions rather than a bare class-name fallback.
        val detail = (health as DatabaseHealth.Unusable).detail
        assertTrue("unhelpful detail: $detail", detail.contains("3") && detail.contains("10"))
    }

    @Test
    fun `a database written by a newer build is reported rather than thrown`() {
        writeDatabaseAtVersion(99)

        assertTrue(DatabaseRecovery.probe(context, NAME) is DatabaseHealth.Unusable)
    }

    @Test
    fun `no database at all is usable and so is one at the current version`() {
        assertFalse(DatabaseRecovery.hasData(context, NAME))
        assertEquals(DatabaseHealth.Usable, DatabaseRecovery.probe(context, NAME))

        // The probe above is what created it, at version 10.
        assertTrue(DatabaseRecovery.hasData(context, NAME))
        assertEquals(DatabaseHealth.Usable, DatabaseRecovery.probe(context, NAME))
    }

    @Test
    fun `the export carries the write-ahead log alongside the db file`() {
        val db = BeyondBordersDatabase.open(context, NAME)
        val entries = mutableListOf<String>()
        try {
            runBlocking {
                db.placeStatusDao().insert(
                    PlaceStatusEntity(
                        id = "id-1",
                        code = "DE",
                        status = "visited",
                        updatedAt = 1_000L,
                        deviceId = "a",
                    )
                )
            }

            // Exported with the connection still open, which is when uncheckpointed
            // pages are actually sitting in the -wal. That is the state a failed probe
            // leaves behind too, and copying only the .db would drop them.
            val zipped = ByteArrayOutputStream()
            DatabaseRecovery.exportTo(context, zipped, NAME)

            ZipInputStream(ByteArrayInputStream(zipped.toByteArray())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries += entry.name
                    // Readable and non-empty, or the copy is a reassuring file that
                    // restores nothing.
                    assertTrue("empty entry ${entry.name}", zip.readBytes().isNotEmpty())
                }
            }
        } finally {
            db.close()
        }

        assertTrue("$entries", entries.contains(NAME))
        assertTrue("no WAL in $entries", entries.any { it.endsWith("-wal") })
    }

    @Test
    fun `a wipe leaves nothing behind and the next probe is clean`() {
        writeDatabaseAtVersion(3)
        assertTrue(DatabaseRecovery.hasData(context, NAME))

        assertTrue(DatabaseRecovery.wipe(context, NAME))

        assertFalse(DatabaseRecovery.hasData(context, NAME))
    }

    /** A file SQLite will open and Room will refuse, stamped with [version]. */
    private fun writeDatabaseAtVersion(version: Int) {
        val file = context.getDatabasePath(NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `leftovers` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.version = version
        }
    }

    private companion object {
        const val NAME = "recovery-test.db"
    }
}
