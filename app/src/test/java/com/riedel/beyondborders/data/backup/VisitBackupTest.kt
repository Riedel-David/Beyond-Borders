package com.riedel.beyondborders.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitBackupTest {

    private val known = setOf("DK", "FR")
    private val now = 1_800_000_000_000L // 2027, so every year up to 2027 is valid

    private fun visit(id: String, code: String = "FR", year: Int? = 2019, updatedAt: Long = 100, deletedAt: Long? = null) =
        TravelBackup.VisitEntry(id, code, year, updatedAt, deletedAt, "dev")

    @Test
    fun `a version 1 file decodes with no visits`() {
        val json = """{"format":"beyondborders.travel","version":1,"places":[
            {"code":"DK","status":"visited","updatedAt":5}]}"""
        val payload = TravelBackup.decode(json, now)
        assertEquals(1, payload.entries.size)
        assertTrue(payload.visits.isEmpty())
    }

    @Test
    fun `a version 1 file with visits round trips its visits including tombstones and undated`() {
        val visits = listOf(
            visit("b", year = null, updatedAt = 7),
            visit("a", year = 2019, updatedAt = 5),
            visit("c", code = "DK", year = 2020, updatedAt = 9, deletedAt = 9),
        )
        val json = TravelBackup.encode(emptyList(), exportedAt = 1, deviceId = "dev", visits = visits)
        val payload = TravelBackup.decode(json, now)
        assertEquals(1, payload.version)
        assertEquals(visits.sortedWith(compareBy({ it.code }, { it.id })), payload.visits)
        assertNull(payload.visits.first { it.id == "b" }.year)
    }

    @Test
    fun `a file newer than the current version is still refused`() {
        val json = """{"format":"beyondborders.travel","version":2,"places":[]}"""
        val error = runCatching { TravelBackup.decode(json, now) }.exceptionOrNull()
        assertEquals(BackupFormatReason.NEWER_VERSION, (error as BackupFormatException).reason)
    }

    @Test
    fun `visit rows without id or timestamp are counted as unreadable and not fatal`() {
        val json = """{"format":"beyondborders.travel","version":1,"places":[],"visits":[
            {"id":"a","code":"FR","year":2019,"updatedAt":5},
            {"code":"FR","year":2019,"updatedAt":5},
            {"id":"d","code":"FR","year":2019},
            "junk"]}"""
        val payload = TravelBackup.decode(json, now)
        assertEquals(listOf("a"), payload.visits.map { it.id })
        assertEquals(3, payload.unreadableRows)
    }

    @Test
    fun `an out of range year on a live visit keeps the row as undated`() {
        val json = """{"format":"beyondborders.travel","version":1,"places":[],"visits":[
            {"id":"a","code":"FR","year":1850,"updatedAt":5},
            {"id":"b","code":"FR","year":2101,"updatedAt":5},
            {"id":"c","code":"FR","year":2099,"updatedAt":5}]}"""
        val payload = TravelBackup.decode(json, 1_000_000_000_000L) // a clock far in the past changes nothing
        assertEquals(listOf(null, null, 2099), payload.visits.map { it.year })
        assertEquals(0, payload.unreadableRows)
    }

    @Test
    fun `a tombstone with a bad year is never discarded`() {
        val json = """{"format":"beyondborders.travel","version":1,"places":[],"visits":[
            {"id":"a","code":"FR","year":1200,"updatedAt":5,"deletedAt":5}]}"""
        val payload = TravelBackup.decode(json, now)
        assertEquals(5L, payload.visits.single().deletedAt)
        assertNull(payload.visits.single().year)
    }

    @Test
    fun `for the same id the newest visit wins`() {
        val plan = BackupMerge.planVisits(listOf(visit("a", year = 2021, updatedAt = 200)), mapOf("a" to 100L), known)
        assertEquals(listOf(2021), plan.apply.map { it.year })
        assertEquals(1, plan.updated)
    }

    @Test
    fun `an older visit with the same id loses`() {
        val plan = BackupMerge.planVisits(listOf(visit("a", updatedAt = 50)), mapOf("a" to 100L), known)
        assertTrue(plan.apply.isEmpty())
        assertEquals(1, plan.unchanged)
    }

    @Test
    fun `a newer tombstone wins over the local visit`() {
        val plan = BackupMerge.planVisits(listOf(visit("a", updatedAt = 200, deletedAt = 200)), mapOf("a" to 100L), known)
        assertEquals(200L, plan.apply.single().deletedAt)
    }

    @Test
    fun `an older tombstone does not delete a newer local visit`() {
        val plan = BackupMerge.planVisits(listOf(visit("a", updatedAt = 50, deletedAt = 50)), mapOf("a" to 100L), known)
        assertTrue(plan.apply.isEmpty())
    }

    @Test
    fun `visits with different ids are both kept even for one place and year`() {
        val plan = BackupMerge.planVisits(listOf(visit("a"), visit("b")), mapOf("c" to 100L), known)
        assertEquals(listOf("a", "b"), plan.apply.map { it.id })
        assertEquals(2, plan.added)
    }

    @Test
    fun `a visit for an unknown code is dropped`() {
        val plan = BackupMerge.planVisits(listOf(visit("a", code = "XX"), visit("b")), emptyMap(), known)
        assertEquals(listOf("b"), plan.apply.map { it.id })
        assertEquals(1, plan.unknownCodes)
    }

    @Test
    fun `a duplicate id inside one file keeps the newest row`() {
        val plan = BackupMerge.planVisits(listOf(visit("a", year = 2000, updatedAt = 9), visit("a", year = 2001, updatedAt = 3)), emptyMap(), known)
        assertEquals(listOf(2000), plan.apply.map { it.year })
    }

    @Test
    fun `a visit file without month keys decodes with month null`() {
        val json = """{"format":"beyondborders.travel","version":1,"places":[],"visits":[
            {"id":"a","code":"FR","year":2019,"updatedAt":5}]}"""
        assertNull(TravelBackup.decode(json, now).visits.single().month)
    }

    @Test
    fun `month round trips`() {
        val json = TravelBackup.encode(emptyList(), 1, "dev", visits = listOf(visit("a", year = 2023).copy(month = 3)))
        assertEquals(3, TravelBackup.decode(json, now).visits.single().month)
    }

    @Test
    fun `a month without a year or outside 1 to 12 is dropped and the row is kept`() {
        val json = """{"format":"beyondborders.travel","version":1,"places":[],"visits":[
            {"id":"a","code":"FR","month":5,"updatedAt":5},
            {"id":"b","code":"FR","year":2020,"month":13,"updatedAt":5},
            {"id":"c","code":"FR","year":2020,"month":0,"updatedAt":5},
            {"id":"d","code":"FR","year":2020,"month":12,"updatedAt":5}]}"""
        val payload = TravelBackup.decode(json, now)
        assertEquals(listOf("a", "b", "c", "d"), payload.visits.map { it.id })
        assertEquals(listOf(null, null, null, 12), payload.visits.map { it.month })
        assertEquals(0, payload.unreadableRows)
    }
}
