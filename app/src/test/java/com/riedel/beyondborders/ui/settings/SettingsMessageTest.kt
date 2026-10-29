package com.riedel.beyondborders.ui.settings

import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.backup.BackupMerge
import com.riedel.beyondborders.data.backup.TravelBackup
import com.riedel.beyondborders.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sentence after an import is the only report the user gets, and an import that
 * quietly did less than it looked like it did is exactly the case it has to describe
 * honestly. Cheap to assert here, tedious to check by importing files on a phone.
 *
 * The messages are [UiText] values, so these tests pin which resource and which
 * arguments each case picks; the wording itself lives in strings_settings.xml.
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

    private fun join(a: UiText, b: UiText) = UiText.Res(R.string.backup_sentence_join, a, b)

    private fun newPlaces(n: Int) = UiText.Plural(R.plurals.backup_imported_new, n, n)

    private fun skippedCodes(n: Int, list: String) = UiText.Plural(R.plurals.backup_skipped_codes, n, n, list)

    @Test
    fun `a normal import reports both numbers`() {
        assertEquals(
            join(UiText.Res(R.string.backup_imported_both, 3, 2), UiText.Res(R.string.backup_already_current, 7)),
            importMessage(plan(added = 3, updated = 2, unchanged = 7)),
        )
    }

    @Test
    fun `only one kind of change reads as one clause`() {
        assertEquals(newPlaces(4), importMessage(plan(added = 4)))
        assertEquals(
            UiText.Plural(R.plurals.backup_imported_updated, 1, 1),
            importMessage(plan(updated = 1)),
        )
    }

    @Test
    fun `an import that changed nothing says so instead of claiming success`() {
        // Not "Imported 0 places", which reads like a failure, and not silence,
        // which reads like nothing happened.
        assertEquals(UiText.Res(R.string.backup_import_nothing), importMessage(plan(unchanged = 12)))
    }

    @Test
    fun `skipped codes are named, and truncated when there are many`() {
        assertEquals(
            join(newPlaces(1), skippedCodes(2, "XX, YY")),
            importMessage(plan(added = 1, unknown = listOf("XX", "YY"))),
        )

        assertEquals(
            join(newPlaces(1), skippedCodes(8, "AA, BB, CC, DD, EE, …")),
            importMessage(plan(added = 1, unknown = ('A'..'H').map { "$it$it" })),
        )
    }

    /**
     * A row dropped for an unrecognised status is data this phone refused to take, so
     * the report has to say so out loud — otherwise "Imported 1 new place." is the
     * only thing the user sees and the missing ones look like they were never there.
     */
    @Test
    fun `entries skipped for an unrecognised status are counted and the words named`() {
        fun one(n: Int, words: String) =
            UiText.Plural(R.plurals.backup_skipped_status_one_word, n, n, words)
        fun many(n: Int, words: String) =
            UiText.Plural(R.plurals.backup_skipped_status_many_words, n, n, words)

        assertEquals(
            join(newPlaces(1), one(1, "worked")),
            importMessage(plan(added = 1, unknownStatuses = listOf("worked"))),
        )

        // Two rows, one word: the count is rows, so it does not have to match the list.
        assertEquals(
            join(newPlaces(1), one(2, "worked")),
            importMessage(plan(added = 1, unknownStatuses = listOf("worked", "worked"))),
        )

        assertEquals(
            join(newPlaces(1), many(3, "dreamt, worked")),
            importMessage(plan(added = 1, unknownStatuses = listOf("dreamt", "worked", "worked"))),
        )
    }

    /**
     * A blank code or a missing timestamp has nothing worth naming, unlike an
     * unknown code or an unrecognised status, so this stays a bare count.
     */
    @Test
    fun `rows the decoder could not read are reported as a bare count`() {
        assertEquals(
            join(newPlaces(1), UiText.Plural(R.plurals.backup_unreadable_rows, 1, 1)),
            importMessage(plan(added = 1, unreadableRows = 1)),
        )
        assertEquals(
            join(newPlaces(1), UiText.Plural(R.plurals.backup_unreadable_rows, 3, 3)),
            importMessage(plan(added = 1, unreadableRows = 3)),
        )
    }

    @Test
    fun `an import can report an unknown code and an unknown status at once`() {
        assertEquals(
            join(
                join(newPlaces(1), skippedCodes(1, "XX")),
                UiText.Plural(R.plurals.backup_skipped_status_one_word, 1, 1, "worked"),
            ),
            importMessage(plan(added = 1, unknown = listOf("XX"), unknownStatuses = listOf("worked"))),
        )
    }

    @Test
    fun `export counts places and removals separately`() {
        fun places(n: Int) = UiText.Plural(R.plurals.backup_places, n, n)
        fun removals(n: Int) = UiText.Plural(R.plurals.backup_removals, n, n)

        assertEquals(UiText.Res(R.string.backup_saved, places(12)), exportMessage(places = 12, removals = 0))
        assertEquals(UiText.Res(R.string.backup_saved, places(1)), exportMessage(places = 1, removals = 0))
        // Tombstones are in the file but they are not places anyone has been to, so
        // they never inflate the headline number.
        assertEquals(
            UiText.Res(R.string.backup_saved_with_removals, places(12), removals(2)),
            exportMessage(places = 12, removals = 2),
        )
        assertEquals(
            UiText.Res(R.string.backup_saved_with_removals, places(0), removals(1)),
            exportMessage(places = 0, removals = 1),
        )
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
            UiText.Res(
                R.string.settings_preview_sample_more,
                "AA land, BB land, CC land, DD land, EE land, FF land, GG land, HH land, II land, JJ land",
                2,
            ),
            line,
        )
    }

    @Test
    fun `the sample line has no and-N-more tail when it names every affected place`() {
        val line = importPreviewSampleLine(plan(added = 2), sampleNames = listOf("Denmark", "Greenland"))
        assertEquals(UiText.Res(R.string.settings_preview_sample, "Denmark, Greenland"), line)
    }

    @Test
    fun `the sample line singles out one changed place`() {
        val expected = UiText.Res(R.string.settings_preview_sample, "Andorra")
        assertEquals(expected, importPreviewSampleLine(plan(added = 1), sampleNames = listOf("Andorra")))
        assertEquals(expected, importPreviewSampleLine(plan(updated = 1), sampleNames = listOf("Andorra")))
    }

    @Test
    fun `a preview with nothing unusual has no caveat line`() {
        assertEquals(emptyList<UiText>(), importPreviewCaveat(plan(added = 1, updated = 2, unchanged = 3)))
    }

    @Test
    fun `the caveat line names an unknown code`() {
        assertEquals(
            listOf(UiText.Plural(R.plurals.settings_caveat_codes, 1, 1)),
            importPreviewCaveat(plan(unknown = listOf("XX"))),
        )
        assertEquals(
            listOf(UiText.Plural(R.plurals.settings_caveat_codes, 2, 2)),
            importPreviewCaveat(plan(unknown = listOf("XX", "YY"))),
        )
    }

    @Test
    fun `the caveat line names an unrecognised status`() {
        assertEquals(
            listOf(UiText.Plural(R.plurals.settings_caveat_statuses, 1, 1)),
            importPreviewCaveat(plan(unknownStatuses = listOf("worked"))),
        )
        assertEquals(
            listOf(UiText.Plural(R.plurals.settings_caveat_statuses, 2, 2)),
            importPreviewCaveat(plan(unknownStatuses = listOf("worked", "dreamt"))),
        )
    }

    @Test
    fun `the caveat line counts unreadable rows`() {
        assertEquals(
            listOf(UiText.Plural(R.plurals.settings_caveat_unreadable, 1, 1)),
            importPreviewCaveat(plan(unreadableRows = 1)),
        )
        assertEquals(
            listOf(UiText.Plural(R.plurals.settings_caveat_unreadable, 2, 2)),
            importPreviewCaveat(plan(unreadableRows = 2)),
        )
    }

    @Test
    fun `the caveat line can report every kind of exception at once`() {
        assertEquals(
            listOf(
                UiText.Plural(R.plurals.settings_caveat_codes, 1, 1),
                UiText.Plural(R.plurals.settings_caveat_statuses, 1, 1),
                UiText.Plural(R.plurals.settings_caveat_unreadable, 1, 1),
            ),
            importPreviewCaveat(
                plan(unknown = listOf("XX"), unknownStatuses = listOf("worked"), unreadableRows = 1),
            ),
        )
    }
}
