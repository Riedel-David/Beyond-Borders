package com.riedel.aeroglobe.ui.settings

import com.riedel.aeroglobe.data.backup.BackupMerge
import com.riedel.aeroglobe.data.backup.TravelBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence after an import is the only report the user gets, and an import that
 * quietly did less than it looked like it did is exactly the case it has to describe
 * honestly. Cheap to assert here, tedious to check by importing files on a phone.
 */
class SettingsMessageTest {

    private fun plan(
        added: Int = 0,
        updated: Int = 0,
        unchanged: Int = 0,
        unknown: List<String> = emptyList(),
        unknownStatuses: List<String> = emptyList(),
    ) = BackupMerge.Plan(
        apply = emptyList<TravelBackup.Entry>(),
        added = added,
        updated = updated,
        unchanged = unchanged,
        unknownCodes = unknown,
        unknownStatuses = unknownStatuses,
    )

    @Test
    fun `a normal import reports both numbers`() {
        assertEquals(
            "Imported 3 new and 2 updated places. 7 already current.",
            importMessage(plan(added = 3, updated = 2, unchanged = 7)),
        )
    }

    @Test
    fun `only one kind of change reads as one clause`() {
        assertEquals("Imported 4 new places.", importMessage(plan(added = 4)))
        assertEquals("Imported 1 updated place.", importMessage(plan(updated = 1)))
    }

    @Test
    fun `an import that changed nothing says so instead of claiming success`() {
        val message = importMessage(plan(unchanged = 12))
        assertTrue(message, message.startsWith("Nothing to change"))
        // Not "Imported 0 places", which reads like a failure, and not silence,
        // which reads like nothing happened.
        assertTrue(message, !message.contains("Imported"))
    }

    @Test
    fun `skipped codes are named, and truncated when there are many`() {
        val few = importMessage(plan(added = 1, unknown = listOf("XX", "YY")))
        assertTrue(few, few.endsWith("Skipped 2 unknown codes: XX, YY."))

        val many = importMessage(plan(added = 1, unknown = ('A'..'H').map { "$it$it" }))
        assertTrue(many, many.contains("Skipped 8 unknown codes: AA, BB, CC, DD, EE, …"))
    }

    /**
     * A row dropped for an unrecognised status is data this phone refused to take, so
     * the report has to say so out loud — otherwise "Imported 1 new place." is the
     * only thing the user sees and the missing ones look like they were never there.
     */
    @Test
    fun `entries skipped for an unrecognised status are counted and the words named`() {
        val one = importMessage(plan(added = 1, unknownStatuses = listOf("worked")))
        assertTrue(one, one.endsWith("Skipped 1 entry with an unrecognised status: worked."))

        // Two rows, one word: the count is rows, so it does not have to match the list.
        val same = importMessage(plan(added = 1, unknownStatuses = listOf("worked", "worked")))
        assertTrue(same, same.endsWith("Skipped 2 entries with an unrecognised status: worked."))

        val mixed = importMessage(plan(added = 1, unknownStatuses = listOf("dreamt", "worked", "worked")))
        assertTrue(mixed, mixed.endsWith("Skipped 3 entries with unrecognised statuses: dreamt, worked."))
    }

    @Test
    fun `an import can report an unknown code and an unknown status at once`() {
        val message = importMessage(plan(added = 1, unknown = listOf("XX"), unknownStatuses = listOf("worked")))
        assertEquals(
            "Imported 1 new place. Skipped 1 unknown code: XX." +
                " Skipped 1 entry with an unrecognised status: worked.",
            message,
        )
    }

    @Test
    fun `export counts places and removals separately`() {
        assertEquals("Saved 12 places.", exportMessage(places = 12, removals = 0))
        assertEquals("Saved 1 place.", exportMessage(places = 1, removals = 0))
        // Tombstones are in the file but they are not places anyone has been to, so
        // they never inflate the headline number.
        assertEquals("Saved 12 places and 2 removals.", exportMessage(places = 12, removals = 2))
        assertEquals("Saved 0 places and 1 removal.", exportMessage(places = 0, removals = 1))
    }
}
