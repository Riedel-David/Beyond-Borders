package com.riedel.beyondborders.ui.settings

import com.riedel.beyondborders.data.backup.BackupMerge
import com.riedel.beyondborders.data.backup.TravelBackup
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
        unreadableRows: Int = 0,
    ) = BackupMerge.Plan(
        apply = emptyList<TravelBackup.Entry>(),
        added = added,
        updated = updated,
        unchanged = unchanged,
        unknownCodes = unknown,
        unknownStatuses = unknownStatuses,
        unreadableRows = unreadableRows,
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

    /**
     * A blank code or a missing timestamp has nothing worth naming, unlike an
     * unknown code or an unrecognised status, so this stays a bare count.
     */
    @Test
    fun `rows the decoder could not read are reported as a bare count`() {
        val one = importMessage(plan(added = 1, unreadableRows = 1))
        assertTrue(one, one.endsWith("1 row could not be read."))

        val many = importMessage(plan(added = 1, unreadableRows = 3))
        assertTrue(many, many.endsWith("3 rows could not be read."))
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

    @Test
    fun `a preview with nothing to change has no sample line`() {
        assertEquals(null, importPreviewSampleLine(plan(unchanged = 12), sampleNames = emptyList()))
    }

    @Test
    fun `the sample line names the places and counts the rest`() {
        val names = ('A'..'L').map { "$it$it land" } // twelve names, sample size ten
        val line = importPreviewSampleLine(plan(added = 8, updated = 4), sampleNames = names.take(10))
        assertEquals(
            "e.g. AA land, BB land, CC land, DD land, EE land, FF land, GG land, HH land, " +
                "II land, JJ land, and 2 more.",
            line,
        )
    }

    @Test
    fun `the sample line has no and-N-more tail when it names every affected place`() {
        val line = importPreviewSampleLine(plan(added = 2), sampleNames = listOf("Denmark", "Greenland"))
        assertEquals("e.g. Denmark, Greenland.", line)
    }

    @Test
    fun `the sample line singles out one changed place`() {
        assertEquals("e.g. Andorra.", importPreviewSampleLine(plan(added = 1), sampleNames = listOf("Andorra")))
        assertEquals("e.g. Andorra.", importPreviewSampleLine(plan(updated = 1), sampleNames = listOf("Andorra")))
    }

    @Test
    fun `a preview with nothing unusual has no caveat line`() {
        assertEquals(null, importPreviewCaveat(plan(added = 1, updated = 2, unchanged = 3)))
    }

    @Test
    fun `the caveat line names an unknown code`() {
        assertEquals(
            "1 place code not recognised by this build.",
            importPreviewCaveat(plan(unknown = listOf("XX"))),
        )
        assertEquals(
            "2 place codes not recognised by this build.",
            importPreviewCaveat(plan(unknown = listOf("XX", "YY"))),
        )
    }

    @Test
    fun `the caveat line names an unrecognised status`() {
        assertEquals(
            "1 entry with a status this build doesn't understand.",
            importPreviewCaveat(plan(unknownStatuses = listOf("worked"))),
        )
        assertEquals(
            "2 entries with a status this build doesn't understand.",
            importPreviewCaveat(plan(unknownStatuses = listOf("worked", "dreamt"))),
        )
    }

    @Test
    fun `the caveat line counts unreadable rows`() {
        assertEquals("1 row could not be read.", importPreviewCaveat(plan(unreadableRows = 1)))
        assertEquals("2 rows could not be read.", importPreviewCaveat(plan(unreadableRows = 2)))
    }

    @Test
    fun `the caveat line can report every kind of exception at once`() {
        assertEquals(
            "1 place code not recognised by this build; " +
                "1 entry with a status this build doesn't understand; " +
                "1 row could not be read.",
            importPreviewCaveat(
                plan(unknown = listOf("XX"), unknownStatuses = listOf("worked"), unreadableRows = 1),
            ),
        )
    }
}
