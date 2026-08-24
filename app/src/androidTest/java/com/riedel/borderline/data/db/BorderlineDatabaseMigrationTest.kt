package com.riedel.borderline.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade path, against the real committed schemas.
 *
 * This table is the one thing in the app that cannot be re-downloaded — a decade of
 * travel history against 252 places — and `open` deliberately has no
 * `fallbackToDestructiveMigration`, so a migration bug is a crash on launch rather
 * than a silent wipe. That makes it the one part of the data layer worth an
 * instrumented test.
 *
 * Every case here migrates by calling the shipped [BorderlineDatabase.open] rather than
 * `MigrationTestHelper.runMigrationsAndValidate`. The helper takes the migrations as
 * arguments, which tests the SQL but assumes the answer to the question that actually
 * goes wrong: whether the migration is *registered* in `addMigrations` at all. Going
 * through `open` tests the SQL, the registration and Room's own post-migration schema
 * validation in one pass — and needs no `private` loosened to `internal` to do it.
 */
@RunWith(AndroidJUnit4::class)
class BorderlineDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BorderlineDatabase::class.java,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Creates the old database under the name the app actually opens.
     *
     * [MigrationTestHelper.createDatabase] writes to `targetContext.getDatabasePath(name)`,
     * which is the same file [BorderlineDatabase.open] opens — that shared file is what
     * lets these tests hand a real upgrade to the production code path. Closed straight
     * away so the upgrade happens on a cold open, as it does on a phone.
     */
    private fun createLegacyDatabase(version: Int, fill: SupportSQLiteDatabase.() -> Unit = {}) {
        helper.createDatabase(BorderlineDatabase.NAME, version).apply {
            fill()
            close()
        }
    }

    /**
     * One row in the shape versions 8 and 9 shared: the two dead columns are still
     * there, and filled, because a migration that drops them has to prove it keeps the
     * rest of the row rather than the rows that happened to leave them null.
     */
    private fun SupportSQLiteDatabase.insertLegacyStatus(
        id: String,
        code: String,
        status: String,
        updatedAt: Long,
        deletedAt: Long? = null,
        deviceId: String = "device-a",
    ) = execSQL(
        """
        INSERT INTO place_status
            (id, code, status, firstVisitedOn, note, updatedAt, deletedAt, deviceId)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        arrayOf(id, code, status, "2019-04-01", "a note no screen ever showed", updatedAt, deletedAt, deviceId),
    )

    private fun BorderlineDatabase.tableNames(): Set<String> = buildSet {
        openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .use { cursor -> while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun BorderlineDatabase.columnNames(table: String): Set<String> = buildSet {
        openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(name))
        }
    }

    private fun BorderlineDatabase.indexNames(table: String): Set<String> = buildSet {
        openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?", arrayOf(table))
            .use { cursor -> while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    /**
     * Version 8 is where a phone that was on the flight-tracking build sits, so this is
     * the full 8 -> 10 path: both registered migrations, one after the other.
     *
     * The tombstone is part of the case and not decoration. Un-marking a place leaves a
     * row with `deletedAt` set rather than deleting it, and `MIGRATION_9_10` copies rows
     * with a plain INSERT ... SELECT — a `NOT NULL` slip on that nullable column would
     * lose exactly the rows that record a decision being undone.
     */
    @Test
    fun `a version 8 database keeps every status and loses the flight tables`() {
        createLegacyDatabase(8) {
            insertLegacyStatus("id-dk", "DK", "visited", updatedAt = 1_700_000_000_000)
            insertLegacyStatus("id-fr", "FR", "lived", updatedAt = 1_700_000_001_000, deviceId = "device-b")
            insertLegacyStatus("id-gl", "GL", "wishlist", updatedAt = 1_700_000_002_000, deletedAt = 1_700_000_003_000)
        }

        val db = BorderlineDatabase.open(context)
        try {
            val rows = runBlocking { db.placeStatusDao().allRows() }
            assertEquals(listOf("DK", "FR", "GL"), rows.map { it.code })
            assertEquals(listOf("visited", "lived", "wishlist"), rows.map { it.status })
            assertEquals(listOf("device-a", "device-b", "device-a"), rows.map { it.deviceId })
            assertEquals(1_700_000_000_000, rows.first { it.code == "DK" }.updatedAt)
            assertNull(rows.first { it.code == "DK" }.deletedAt)
            assertEquals(1_700_000_003_000, rows.first { it.code == "GL" }.deletedAt)

            // The tombstone stays out of the live stream, which is what a tombstone is for.
            assertEquals(
                setOf("DK", "FR"),
                runBlocking { db.placeStatusDao().observeActive().first() }.map { it.code }.toSet(),
            )

            // MIGRATION_8_9's whole job: the three tables flight tracking left behind.
            val tables = db.tableNames()
            assertFalse("flight tables survived: $tables", tables.any { it.startsWith("flight") })
        } finally {
            db.close()
        }
    }

    /**
     * `MIGRATION_9_10` rebuilds the table rather than dropping two columns, because
     * SQLite could not drop a column at the version Room targets here. A rebuild is the
     * migration that can silently lose things — rows, the columns it meant to keep, and
     * the indices, which do not survive a `DROP TABLE` and have to be recreated by hand.
     */
    @Test
    fun `dropping the dead columns keeps the rows and the indices`() {
        createLegacyDatabase(9) {
            insertLegacyStatus("id-dk", "DK", "visited", updatedAt = 1_700_000_000_000)
        }

        val db = BorderlineDatabase.open(context)
        try {
            val row = runBlocking { db.placeStatusDao().findByCode("DK") }
            assertEquals("visited", row?.status)
            assertEquals(
                setOf("id", "code", "status", "updatedAt", "deletedAt", "deviceId"),
                db.columnNames("place_status"),
            )
            assertTrue(
                "indices were not recreated: ${db.indexNames("place_status")}",
                db.indexNames("place_status")
                    .containsAll(listOf("index_place_status_code", "index_place_status_updatedAt")),
            )
        } finally {
            db.close()
        }
    }

    /**
     * The unique index on `code` is not just an index: it is the invariant that stops a
     * bug from double-counting a country in the stats, and `setStatus` relies on it
     * being enforced. A rebuild that recreated the index as non-unique would pass a
     * name check and fail here.
     */
    @Test
    fun `the rebuilt table still refuses a second row for one place`() {
        createLegacyDatabase(9) {
            insertLegacyStatus("id-dk", "DK", "visited", updatedAt = 1_700_000_000_000)
        }

        val db = BorderlineDatabase.open(context)
        try {
            val duplicate = PlaceStatusEntity(
                id = "id-dk-again",
                code = "DK",
                status = "wishlist",
                updatedAt = 1_700_000_009_000,
                deviceId = "device-a",
            )
            val thrown = runCatching { runBlocking { db.placeStatusDao().insert(duplicate) } }
                .exceptionOrNull()
            assertTrue(
                "a second DK row was accepted; the unique index is gone. Got: $thrown",
                thrown is SQLiteConstraintException,
            )
        } finally {
            db.close()
        }
    }

    /**
     * A known gap, pinned rather than fixed: `open` registers 8 -> 9 and 9 -> 10 only,
     * so anything still on 1..7 has no path to 10 and Room refuses to open it.
     *
     * That is the honest behaviour to assert — better a crash than the wipe
     * `fallbackToDestructiveMigration` would do — but it is a gap, and one this project
     * can live with only because every install was upgraded past 8 long ago. If someone
     * adds the missing migrations, this test failing is the reminder to describe the new
     * path here instead.
     */
    @Test
    fun `a database older than version 8 has no migration path`() {
        createLegacyDatabase(7)

        val db = BorderlineDatabase.open(context)
        try {
            // build() is lazy, so the refusal surfaces on the first query rather than here.
            val thrown = runCatching { runBlocking { db.placeStatusDao().allRows() } }.exceptionOrNull()
            assertTrue(
                "a version 7 database opened without a migration path. Got: $thrown",
                thrown is IllegalStateException,
            )
            assertTrue(
                "unexpected message: ${thrown?.message}",
                thrown?.message?.contains("migration") == true,
            )
        } finally {
            db.close()
        }
    }
}
