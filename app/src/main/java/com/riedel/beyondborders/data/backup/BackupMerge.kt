package com.riedel.beyondborders.data.backup

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
     * @property unknownStatuses one entry per row [TravelBackup.decode] dropped for
     *   having a status this build does not know. Carried rather than computed here —
     *   those rows never reach [plan] — so that the import report can name them next
     *   to the unknown codes instead of staying silent about them.
     * @property visitsAdded / visitsUpdated what the file's visits would add or replace;
     *   see [planVisits]. Visit rows for unknown codes are folded into [unreadableRows].
     * @property unreadableRows a count of rows [TravelBackup.decode] dropped for
     *   having no code, no positive `updatedAt`, or not being an object at all.
     *   Carried through for the same reason as [unknownStatuses], but as a count: none
     *   of those rows has a name worth showing.
     */
    data class Plan(
        val apply: List<TravelBackup.Entry>,
        val added: Int,
        val updated: Int,
        val unchanged: Int,
        val unknownCodes: List<String>,
        val unknownStatuses: List<String> = emptyList(),
        val unreadableRows: Int = 0,
        val visitsAdded: Int = 0,
        val visitsUpdated: Int = 0,
    ) {
        val skipped: Int get() = unchanged + unknownCodes.size + unknownStatuses.size + unreadableRows
    }

    /**
     * @param entries what was in the file.
     * @param localUpdatedAt code -> updatedAt of every existing row, tombstones
     *   included. A tombstoned local row still has a timestamp and can still win.
     * @param knownCodes the codes this build's registry has. An entry for anything
     *   else is dropped rather than stored: it would be a row no screen can show
     *   and no tap can reach, and it would quietly inflate the visited count.
     * @param unknownStatuses passed straight through to [Plan.unknownStatuses].
     * @param unreadableRows passed straight through to [Plan.unreadableRows].
     */
    fun plan(
        entries: List<TravelBackup.Entry>,
        localUpdatedAt: Map<String, Long>,
        knownCodes: Set<String>,
        unknownStatuses: List<String> = emptyList(),
        unreadableRows: Int = 0,
    ): Plan {
        val unknown = sortedSetOf<String>()
        // A file can name the same place twice — hand-edited, or concatenated from
        // two exports. Keep the newest and treat the rest as if they were not there.
        // On an equal timestamp the later row in the file wins: two rows for one
        // place is usually two exports concatenated, and the newer export is the
        // one that appears later.
        val newestPerCode = LinkedHashMap<String, TravelBackup.Entry>()
        for (entry in entries) {
            if (entry.code !in knownCodes) {
                unknown.add(entry.code)
                continue
            }
            val seen = newestPerCode[entry.code]
            if (seen == null || entry.updatedAt >= seen.updatedAt) {
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
            unknownStatuses = unknownStatuses,
            unreadableRows = unreadableRows,
        )
    }

    /**
     * @property apply the visits to write, deduplicated by id.
     * @property unknownCodes how many file rows were dropped for naming a place this
     *   build does not have.
     */
    data class VisitPlan(
        val apply: List<TravelBackup.VisitEntry>,
        val added: Int,
        val updated: Int,
        val unchanged: Int,
        val unknownCodes: Int,
    )

    /**
     * Visits merge as a union by id, not by place: two phones that each added a visit
     * to France both keep both, and only a shared id is ever a conflict. For a shared
     * id the higher `updatedAt` wins, so a tombstone beats an older edit and loses to a
     * newer one. Equal timestamps lose, making a second import of one file a no-op.
     *
     * A visit of a place that is not currently Been/Lived is kept: it is hidden, not
     * dropped, and comes back if the place is marked again. Only codes missing from the
     * registry are dropped.
     *
     * @param localUpdatedAt id -> updatedAt of every local visit, tombstones included.
     */
    internal fun planVisits(
        visits: List<TravelBackup.VisitEntry>,
        localUpdatedAt: Map<String, Long>,
        knownCodes: Set<String>,
    ): VisitPlan {
        var unknown = 0
        val newestPerId = LinkedHashMap<String, TravelBackup.VisitEntry>()
        for (visit in visits) {
            if (visit.code !in knownCodes) {
                unknown++
                continue
            }
            val seen = newestPerId[visit.id]
            if (seen == null || visit.updatedAt >= seen.updatedAt) newestPerId[visit.id] = visit
        }
        val apply = ArrayList<TravelBackup.VisitEntry>(newestPerId.size)
        var added = 0
        var updated = 0
        var unchanged = 0
        for (visit in newestPerId.values) {
            val local = localUpdatedAt[visit.id]
            when {
                // A tombstone for an unseen id is stored too: unlike a place, a visit has no
                // natural key, so the tombstone is what stops an older copy of the same
                // id from a second file resurrecting it.
                local == null -> { added++; apply.add(visit) }
                visit.updatedAt > local -> { updated++; apply.add(visit) }
                else -> unchanged++
            }
        }
        return VisitPlan(apply, added, updated, unchanged, unknown)
    }
}
