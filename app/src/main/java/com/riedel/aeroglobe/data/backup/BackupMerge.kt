package com.riedel.aeroglobe.data.backup

/**
 * Decides what an import should actually change.
 *
 * Import is a **merge**, not a restore: it never wipes the table first. Someone who
 * marked ten places on a new phone and then imports last year's backup should end
 * up with both sets, not with last year's. The rule is last-write-wins on
 * [TravelBackup.Entry.updatedAt] — the same rule phase 5's sync will use, which is
 * why those columns have been maintained since the first commit.
 *
 * Kept as a pure function over plain maps so the interesting cases (a stale file,
 * a duplicate code, a tombstone that must win) are unit tests rather than something
 * to verify by importing files on a phone.
 */
object BackupMerge {

    /**
     * @property apply the entries that should be written, already deduplicated.
     * @property added entries for places with no local row at all.
     * @property updated entries that beat the local row on [TravelBackup.Entry.updatedAt].
     * @property unchanged entries the local row is the same age or newer than.
     * @property unknownCodes codes this build has no place for, sorted; skipped.
     */
    data class Plan(
        val apply: List<TravelBackup.Entry>,
        val added: Int,
        val updated: Int,
        val unchanged: Int,
        val unknownCodes: List<String>,
    ) {
        val skipped: Int get() = unchanged + unknownCodes.size
    }

    /**
     * @param entries what was in the file.
     * @param localUpdatedAt code -> updatedAt of every existing row, tombstones
     *   included. A tombstoned local row still has a timestamp and can still win.
     * @param knownCodes the codes this build's registry has. An entry for anything
     *   else is dropped rather than stored: it would be a row no screen can show
     *   and no tap can reach, and it would quietly inflate the visited count.
     */
    fun plan(
        entries: List<TravelBackup.Entry>,
        localUpdatedAt: Map<String, Long>,
        knownCodes: Set<String>,
    ): Plan {
        val unknown = sortedSetOf<String>()
        // A file can name the same place twice — hand-edited, or concatenated from
        // two exports. Keep the newest and treat the rest as if they were not there.
        val newestPerCode = LinkedHashMap<String, TravelBackup.Entry>()
        for (entry in entries) {
            if (entry.code !in knownCodes) {
                unknown.add(entry.code)
                continue
            }
            val seen = newestPerCode[entry.code]
            if (seen == null || entry.updatedAt > seen.updatedAt) {
                newestPerCode[entry.code] = entry
            }
        }

        val apply = ArrayList<TravelBackup.Entry>(newestPerCode.size)
        var added = 0
        var updated = 0
        var unchanged = 0
        for (entry in newestPerCode.values) {
            val local = localUpdatedAt[entry.code]
            when {
                local == null -> {
                    // An un-marked place we have never marked either: the file and
                    // the database already agree, and writing the tombstone would
                    // only add a row that nothing can ever show.
                    if (entry.deletedAt != null) {
                        unchanged++
                    } else {
                        added++
                        apply.add(entry)
                    }
                }
                // Strictly newer. Equal timestamps lose, which makes importing the
                // same file twice a no-op instead of a pointless rewrite.
                entry.updatedAt > local -> {
                    updated++
                    apply.add(entry)
                }
                else -> unchanged++
            }
        }

        return Plan(
            apply = apply,
            added = added,
            updated = updated,
            unchanged = unchanged,
            unknownCodes = unknown.toList(),
        )
    }
}
