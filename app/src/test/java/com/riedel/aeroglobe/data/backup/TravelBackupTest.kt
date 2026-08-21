package com.riedel.aeroglobe.data.backup

import com.riedel.aeroglobe.data.place.PlaceStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file is the only exit from this app.
 *
 * If it ever writes something it cannot read back, the travel history — the one
 * thing here that cannot be re-downloaded — is stuck inside a phone. So the round
 * trip is asserted field by field rather than sampled.
 */
class TravelBackupTest {

    private val entries = listOf(
        TravelBackup.Entry(
            code = "GL",
            status = PlaceStatus.VISITED,
            updatedAt = 1_700_000_000_000,
            deletedAt = null,
            deviceId = "device-a",
        ),
        TravelBackup.Entry(
            code = "DK",
            status = PlaceStatus.WISHLIST,
            updatedAt = 1_700_000_001_000,
            deviceId = "device-a",
        ),
        // A tombstone: marked once, un-marked later.
        TravelBackup.Entry(
            code = "FR",
            status = PlaceStatus.VISITED,
            updatedAt = 1_700_000_002_000,
            deletedAt = 1_700_000_002_000,
            deviceId = "device-b",
        ),
        TravelBackup.Entry(
            code = "NO",
            status = PlaceStatus.LIVED,
            updatedAt = 1_700_000_003_000,
            deviceId = "device-b",
        ),
    )

    private fun roundTrip(): List<TravelBackup.Entry> =
        TravelBackup.decode(
            TravelBackup.encode(entries, exportedAt = 1_700_000_009_000, deviceId = "device-a")
        ).entries

    @Test
    fun `every field survives the round trip`() {
        assertEquals(entries.sortedBy { it.code }, roundTrip())
    }

    @Test
    fun `the header carries the format, version and export time`() {
        val payload = TravelBackup.decode(
            TravelBackup.encode(entries, exportedAt = 1_700_000_009_000, deviceId = "device-a")
        )
        assertEquals(TravelBackup.VERSION, payload.version)
        assertEquals(1_700_000_009_000, payload.exportedAt)
        assertEquals("device-a", payload.deviceId)
    }

    /**
     * Two exports of the same data must be identical bytes, so that keeping backups
     * in a synced folder or a git repo shows a diff only when something changed.
     */
    @Test
    fun `output is ordered by code and stable`() {
        val once = TravelBackup.encode(entries, 1_700_000_009_000, "device-a")
        val again = TravelBackup.encode(entries.reversed(), 1_700_000_009_000, "device-a")
        assertEquals(once, again)
        assertEquals(listOf("DK", "FR", "GL", "NO"), TravelBackup.decode(once).entries.map { it.code })
    }

    @Test
    fun `names are written for humans and ignored on the way back in`() {
        val json = TravelBackup.encode(
            entries,
            exportedAt = 1L,
            deviceId = "device-a",
            names = mapOf("GL" to "Greenland"),
        )
        assertTrue("the name should be in the file", json.contains("Greenland"))
        // Nothing in Entry holds it: the registry is the authority on names, and a
        // file that could rename a place would let a typo outrank the dataset.
        assertEquals(entries.size, TravelBackup.decode(json).entries.size)
    }

    @Test
    fun `null fields are left out of the file rather than written as null`() {
        val json = TravelBackup.encode(
            listOf(entries[1]),
            exportedAt = 1L,
            deviceId = "device-a",
        )
        val row = JSONObject(json).getJSONArray("places").getJSONObject(0)
        assertTrue(row.has("code"))
        assertTrue("an active row should carry no deletedAt", !row.has("deletedAt"))
    }

    @Test
    fun `a hand-written status of none is read as an un-marking`() {
        val json = """
            {"format":"aeroglobe.travel","version":1,"exportedAt":1,"places":[
              {"code":"DK","status":"none","updatedAt":1700000000000}
            ]}
        """.trimIndent()
        val entry = TravelBackup.decode(json).entries.single()
        assertEquals(PlaceStatus.NONE, entry.status)
        // Otherwise the merge would have no way to tell "un-mark this" from "mark
        // this as something", and importing the file would resurrect the place.
        assertEquals(1_700_000_000_000, entry.deletedAt)
    }

    /**
     * The trap this guards against: [PlaceStatus.fromStorage] falls back to NONE, and
     * NONE decodes as a tombstone. Reading an unknown word that way would turn "I
     * lived here", written by a newer build, into "un-mark this place" — a silent
     * deletion of real history. Skipping and naming the row is the honest failure.
     */
    @Test
    fun `a status this build does not know is skipped and named, not read as an un-marking`() {
        val json = """
            {"format":"aeroglobe.travel","version":1,"places":[
              {"code":"DK","status":"worked","updatedAt":1700000000000},
              {"code":"NO","status":"worked","updatedAt":1700000001000},
              {"code":"SE","status":"lived","updatedAt":1700000002000}
            ]}
        """.trimIndent()
        val payload = TravelBackup.decode(json)

        assertEquals(listOf("SE"), payload.entries.map { it.code })
        assertEquals(PlaceStatus.LIVED, payload.entries.single().status)
        // One per dropped row, so the count is a number of places and not a number
        // of distinct words. The report distincts them itself.
        assertEquals(listOf("worked", "worked"), payload.unknownStatuses)
    }

    @Test
    fun `a readable file reports no unknown statuses`() {
        val json = TravelBackup.encode(entries, exportedAt = 1L, deviceId = "device-a")
        assertEquals(emptyList<String>(), TravelBackup.decode(json).unknownStatuses)
    }

    @Test
    fun `one unusable row does not cost the others`() {
        val json = """
            {"format":"aeroglobe.travel","version":1,"places":[
              {"status":"visited","updatedAt":1700000000000},
              {"code":"DK","status":"visited"},
              {"code":"NO","status":"visited","updatedAt":0},
              {"code":"SE","status":"visited","updatedAt":1700000000000}
            ]}
        """.trimIndent()
        val codes = TravelBackup.decode(json).entries.map { it.code }
        // Dropped in order: no code, no timestamp, a timestamp that cannot be
        // compared against anything.
        assertEquals(listOf("SE"), codes)
    }

    @Test
    fun `a foreign json file is refused with a reason`() {
        val error = runCatching { TravelBackup.decode("""{"places":[]}""") }.exceptionOrNull()
        assertTrue(error is BackupFormatException)
        assertTrue(error!!.message!!.contains("AeroGlobe"))
    }

    @Test
    fun `a file that is not json at all is refused`() {
        val error = runCatching { TravelBackup.decode("not json") }.exceptionOrNull()
        assertTrue(error is BackupFormatException)
    }

    /**
     * Forward compatibility has to fail loudly. A version 2 file might use the same
     * field names for something else, and silently importing half of it would be
     * worse than refusing all of it.
     */
    @Test
    fun `a newer format version is refused rather than guessed at`() {
        val json = """{"format":"aeroglobe.travel","version":99,"places":[]}"""
        val error = runCatching { TravelBackup.decode(json) }.exceptionOrNull()
        assertTrue(error is BackupFormatException)
        assertTrue(error!!.message!!.contains("newer version"))
    }

    @Test
    fun `a backup with no places array is refused`() {
        val json = """{"format":"aeroglobe.travel","version":1}"""
        assertTrue(
            runCatching { TravelBackup.decode(json) }.exceptionOrNull() is BackupFormatException
        )
    }

    @Test
    fun `an empty history still produces a readable file`() {
        val payload = TravelBackup.decode(TravelBackup.encode(emptyList(), 1L, "device-a"))
        assertEquals(emptyList<TravelBackup.Entry>(), payload.entries)
    }

    @Test
    fun `the suggested file name is dated and ends in json`() {
        assertEquals("aeroglobe-travel-2026-08-12.json", TravelBackup.suggestedFileName("2026-08-12"))
    }

    @Test
    fun `a device id written by an older build is optional`() {
        val json = """
            {"format":"aeroglobe.travel","version":1,"places":[
              {"code":"DK","status":"visited","updatedAt":1700000000000}
            ]}
        """.trimIndent()
        val payload = TravelBackup.decode(json)
        assertNull(payload.deviceId)
        assertNull(payload.entries.single().deviceId)
        assertNotNull(payload.entries.single().status)
    }
}
