package com.riedel.beyondborders.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a merge is allowed to write, at the column level.
 *
 * [PlaceStatusEntity] documents that `status` is never `none` — a place that is not
 * marked is a tombstone with the old status left in place. A hand-written
 * `"status": "none"` in a backup file is the one input that used to be able to break
 * that, because the decoded entry was passed straight through to the update.
 */
@RunWith(AndroidJUnit4::class)
class PlaceStatusDaoApplyAllTest {

    private lateinit var db: BeyondBordersDatabase
    private lateinit var dao: PlaceStatusDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, BeyondBordersDatabase::class.java).build()
        dao = db.placeStatusDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `an imported un-marking tombstones the row without erasing what it was`() = runBlocking {
        dao.setStatus("DE", "visited", now = 1_000L, deviceId = "a", newId = "id-1")

        dao.applyAll(
            listOf(
                PlaceStatusEntity(
                    id = "id-2",
                    code = "DE",
                    status = "none",
                    updatedAt = 2_000L,
                    deletedAt = 2_000L,
                    deviceId = "b",
                )
            )
        )

        val row = dao.findByCode("DE")!!
        assertEquals("visited", row.status)
        assertEquals(2_000L, row.deletedAt)
        assertEquals(2_000L, row.updatedAt)
        // Gone from the live list, which is what "not marked" means to every screen.
        assertEquals(emptyList<PlaceStatusEntity>(), dao.allRows().filter { it.deletedAt == null })
    }

    @Test
    fun `an imported un-marking of a place with no row records the tombstone as it came`() =
        runBlocking {
            dao.applyAll(
                listOf(
                    PlaceStatusEntity(
                        id = "id-1",
                        code = "FR",
                        status = "none",
                        updatedAt = 2_000L,
                        deletedAt = 2_000L,
                        deviceId = "b",
                    )
                )
            )

            // Nothing to preserve here, so "none" is stored: the tombstone still has to
            // exist, or a later import of an older "visited" would win the merge.
            val row = dao.findByCode("FR")!!
            assertEquals("none", row.status)
            assertEquals(2_000L, row.deletedAt)
        }

    @Test
    fun `an imported real status still overwrites the previous one`() = runBlocking {
        dao.setStatus("DE", "wishlist", now = 1_000L, deviceId = "a", newId = "id-1")

        dao.applyAll(
            listOf(
                PlaceStatusEntity(
                    id = "id-2",
                    code = "DE",
                    status = "visited",
                    updatedAt = 2_000L,
                    deletedAt = null,
                    deviceId = "b",
                )
            )
        )

        val row = dao.findByCode("DE")!!
        assertEquals("visited", row.status)
        assertNull(row.deletedAt)
    }
}
