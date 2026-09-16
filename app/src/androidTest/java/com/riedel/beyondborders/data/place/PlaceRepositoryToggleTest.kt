package com.riedel.beyondborders.data.place

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riedel.beyondborders.data.db.BeyondBordersDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [PlaceRepository.toggleStatus] against a real database.
 *
 * Instrumented rather than host-side because the behaviour worth pinning is the
 * ordering of a read against a write, and a fake repository answers both instantly —
 * which is exactly the condition under which the bug this guards did not reproduce.
 */
@RunWith(AndroidJUnit4::class)
class PlaceRepositoryToggleTest {

    private lateinit var db: BeyondBordersDatabase
    private lateinit var repo: PlaceRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, BeyondBordersDatabase::class.java).build()
        repo = PlaceRepository({ PlaceRegistry.parse(ONE_PLACE) }, db, "test-device")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `asking for the status a place already has clears it`() = runBlocking {
        assertEquals(PlaceStatus.VISITED, repo.toggleStatus("DE", PlaceStatus.VISITED))
        assertEquals(PlaceStatus.NONE, repo.toggleStatus("DE", PlaceStatus.VISITED))
    }

    @Test
    fun `asking for a different status replaces it rather than clearing it`() = runBlocking {
        repo.toggleStatus("DE", PlaceStatus.VISITED)
        assertEquals(PlaceStatus.WISHLIST, repo.toggleStatus("DE", PlaceStatus.WISHLIST))
    }

    /**
     * Two taps on the same chip in quick succession are a mark and an undo, even when
     * the second starts before the first has finished writing.
     *
     * This is the regression. The status used to be read from the UI's own copy, which
     * arrives over a Flow and lags the write, so both taps saw "not visited" and both
     * wrote "visited" — the undo silently did nothing. Reading inside the transaction
     * is what serializes them.
     */
    @Test
    fun `two overlapping toggles of the same chip end with the mark cleared`() = runBlocking {
        val first = async { repo.toggleStatus("DE", PlaceStatus.VISITED) }
        val second = async { repo.toggleStatus("DE", PlaceStatus.VISITED) }
        val outcomes = setOf(first.await(), second.await())

        assertEquals(setOf(PlaceStatus.VISITED, PlaceStatus.NONE), outcomes)
        assertEquals(emptyMap<String, PlaceStatus>(), repo.statuses.first())
    }

    private companion object {
        /** One place is enough: [PlaceRepository.toggleStatus] only ever touches one. */
        const val ONE_PLACE = """
            [{"code":"DE","name":"Germany","continent":"Europe","region":"Western Europe",
              "sovereign":"DE","uninhabited":false,"unMember":true,"lat":51.0,"lon":10.0,
              "bbox":[5.9,47.3,15.0,55.1],"areaKm2":357022,"popEst":83000000}]
        """
    }
}
