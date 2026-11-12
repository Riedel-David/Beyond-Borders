package com.riedel.beyondborders.data.backup

import androidx.annotation.StringRes
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.place.cleanVisitMonth
import com.riedel.beyondborders.data.place.MAX_IMPORT_VISIT_YEAR
import com.riedel.beyondborders.data.place.MIN_VISIT_YEAR
import com.riedel.beyondborders.ui.UiText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The on-disk backup format, and the only thing that knows how it is spelled.
 *
 * The travel history is the one piece of data in this app that cannot be
 * re-downloaded, so it must be possible to get it out — of this app, of this
 * phone, and of this codebase if it is ever abandoned. Hence a plain JSON file
 * written through the system file picker rather than a proprietary blob or a
 * cloud account: the user can read it, diff it, mail it to themselves and, in the
 * worst case, retype it.
 *
 * Both directions are pure string work with no Room and no Android in sight,
 * which is what lets [TravelBackupTest] cover the round trip on the host JVM.
 */
object TravelBackup {

    /** Marks the file as ours. Checked on import so a random .json fails loudly. */
    const val FORMAT = "beyondborders.travel"

    /**
     * Bumped only for a change that an older reader would misinterpret. Adding an
     * optional field does not qualify — unknown keys are ignored on read and
     * missing ones fall back to their defaults. The optional `visits` array is such a
     * key: older readers ignore it, and a file without it simply has no visits.
     */
    const val VERSION = 1

    const val MIME_TYPE = "application/json"

    /** How far past the importing phone's clock an imported timestamp may reach. */
    const val MAX_FUTURE_SKEW_MS = 24L * 60 * 60 * 1000

    /**
     * One place's row as it appears in the file.
     *
     * Deliberately not [com.riedel.beyondborders.data.db.PlaceStatusEntity]: the
     * file format has to stay stable across schema changes, and a row read from a
     * file has no primary key yet — the importer decides whether it becomes a new
     * row or overwrites an existing one.
     *
     * [updatedAt], [deletedAt] and [deviceId] are carried through verbatim because
     * they are what makes a merge possible at all; see [BackupMerge].
     */
    data class Entry(
        val code: String,
        val status: PlaceStatus,
        val updatedAt: Long,
        val deletedAt: Long? = null,
        val deviceId: String? = null,
    )

    /** One visit's row as it appears in the file; [year] null is an undated visit. */
    data class VisitEntry(
        val id: String,
        val code: String,
        val year: Int?,
        val updatedAt: Long,
        val deletedAt: Long? = null,
        val deviceId: String? = null,
        val month: Int? = null,
    )

    /**
     * A decoded file: the header plus its rows.
     *
     * [unknownStatuses] holds one entry per *row* that was dropped because this build
     * does not know its status, so its size is a row count and its distinct values
     * name the statuses. See [decode] for why those rows are dropped rather than read.
     *
     * [unreadableRows] counts rows dropped for having no code, no positive
     * `updatedAt`, or for not being an object at all. Unlike [unknownStatuses] there
     * is nothing about such a row worth showing a user, so it is a count and not a
     * list.
     */
    data class Payload(
        val version: Int,
        val exportedAt: Long,
        val deviceId: String?,
        val entries: List<Entry>,
        val unknownStatuses: List<String> = emptyList(),
        val unreadableRows: Int = 0,
        val visits: List<VisitEntry> = emptyList(),
    )

    /**
     * Write the file.
     *
     * Tombstones ([Entry.deletedAt] set) are included on purpose. Restoring onto a
     * second phone has to be able to say "this place was un-marked", and a row that
     * is simply absent cannot say that — the merge would leave the stale local
     * value in place.
     *
     * [names] is informational only and ignored on import. It exists so that a
     * human opening the file sees "Greenland" next to "GL" instead of having to
     * look codes up.
     */
    fun encode(
        entries: List<Entry>,
        exportedAt: Long,
        deviceId: String,
        names: Map<String, String> = emptyMap(),
        visits: List<VisitEntry> = emptyList(),
    ): String {
        val places = JSONArray()
        // Sorted by code so two exports of the same data are byte-identical and a
        // diff of two backups shows only what actually changed.
        entries.sortedBy { it.code }.forEach { entry ->
            val row = JSONObject()
            row.put("code", entry.code)
            names[entry.code]?.let { row.put("name", it) }
            row.put("status", entry.status.storageValue)
            row.put("updatedAt", entry.updatedAt)
            entry.deletedAt?.let { row.put("deletedAt", it) }
            entry.deviceId?.let { row.put("deviceId", it) }
            places.put(row)
        }

        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        // Unix millis, like every other timestamp in the app. The UI formats it.
        root.put("exportedAt", exportedAt)
        root.put("deviceId", deviceId)
        root.put("places", places)
        val visitRows = JSONArray()
        // Tombstones included, for the same reason as places; sorted so exports are stable.
        visits.sortedWith(compareBy({ it.code }, { it.id })).forEach { visit ->
            val row = JSONObject()
            row.put("id", visit.id)
            row.put("code", visit.code)
            names[visit.code]?.let { row.put("name", it) }
            visit.year?.let { row.put("year", it) }
            visit.month?.let { row.put("month", it) }
            row.put("updatedAt", visit.updatedAt)
            visit.deletedAt?.let { row.put("deletedAt", it) }
            visit.deviceId?.let { row.put("deviceId", it) }
            visitRows.put(row)
        }
        root.put("visits", visitRows)
        return root.toString(2)
    }

    /**
     * Read the file, or explain why it cannot be read.
     *
     * Strict about the header and forgiving about the rows: a wrong `format` means
     * the user picked the wrong file and should be told, whereas one row missing an
     * `updatedAt` should not cost them the other 200.
     *
     * @throws BackupFormatException if this is not a BeyondBorders backup, or is a
     *   newer one than this build understands.
     */
    fun decode(text: String, now: Long = System.currentTimeMillis()): Payload {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException(BackupFormatReason.NOT_JSON, cause = e)
        }

        val format = root.optString("format")
        if (format != FORMAT) {
            throw BackupFormatException(BackupFormatReason.WRONG_FORMAT)
        }
        val version = root.optInt("version", 0)
        if (version > VERSION) {
            throw BackupFormatException(BackupFormatReason.NEWER_VERSION, version)
        }

        val array = root.optJSONArray("places")
            ?: throw BackupFormatException(BackupFormatReason.NO_PLACES)

        val entries = ArrayList<Entry>(array.length())
        val unknownStatuses = ArrayList<String>()
        var unreadableRows = 0
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: run { unreadableRows++; continue }
            val code = row.optString("code").takeIf { it.isNotBlank() }
                ?: run { unreadableRows++; continue }
            // A row with no timestamp cannot be merged safely: last-write-wins has
            // nothing to compare, and defaulting to "now" would let a stale backup
            // silently overwrite newer local edits.
            // Clamped to a day past now: last-write-wins would otherwise let a row stamped
            // in the far future beat every later local edit of that place, forever.
            val ceiling = now + MAX_FUTURE_SKEW_MS
            val updatedAt = row.optLong("updatedAt", 0L).takeIf { it > 0L }?.coerceAtMost(ceiling)
                ?: run { unreadableRows++; continue }
            val rawStatus = row.optString("status")
            // Strict, not PlaceStatus.fromStorage: falling back to NONE here would
            // read a status from a newer build as an *un-marking* and delete real
            // history on import. A word we do not know is skipped and reported
            // instead, exactly like a place code we do not know.
            val status = PlaceStatus.parseOrNull(rawStatus)
            if (status == null) {
                unknownStatuses.add(rawStatus)
                continue
            }
            entries.add(
                Entry(
                    code = code,
                    status = status,
                    updatedAt = updatedAt,
                    // A hand-written `"status": "none"` is how a person spells "not
                    // marked"; the app itself spells it as a tombstone with the old
                    // status left in place. Both mean the same thing, so they are
                    // normalised to the same thing here and the rest of the import
                    // only has to understand deletedAt.
                    deletedAt = row.optLong("deletedAt", 0L).takeIf { it > 0L }?.coerceAtMost(ceiling)
                        ?: updatedAt.takeIf { status == PlaceStatus.NONE },
                    deviceId = row.optStringOrNull("deviceId"),
                )
            )
        }

        // Absent in a version 1 file: no visits, not an error. A row without id, code or
        // timestamp is counted with the unreadable place rows rather than failing the import.
        val visits = ArrayList<VisitEntry>()
        val visitArray = root.optJSONArray("visits")
        val ceiling = now + MAX_FUTURE_SKEW_MS
        if (visitArray != null) {
            for (i in 0 until visitArray.length()) {
                val row = visitArray.optJSONObject(i) ?: run { unreadableRows++; continue }
                val id = row.optStringOrNull("id")
                val code = row.optStringOrNull("code")
                val updatedAt = row.optLong("updatedAt", 0L).takeIf { it > 0L }?.coerceAtMost(ceiling)
                // Never a reason to lose a row: a tombstone must still propagate, and a
                // live visit with an impossible year is still a visit. The bounds are fixed
                // rather than clock-based so a wrong device clock cannot change what imports;
                // anything outside them becomes undated.
                val rawYear = if (row.isNull("year")) null else row.optInt("year", Int.MIN_VALUE)
                val year = rawYear?.takeIf { it in MIN_VISIT_YEAR..MAX_IMPORT_VISIT_YEAR }
                // A month with no year, or outside 1..12, is dropped; the row stays.
                val month = if (row.isNull("month")) null else cleanVisitMonth(year, row.optInt("month", 0))
                if (id == null || code == null || updatedAt == null) {
                    unreadableRows++
                    continue
                }
                visits.add(
                    VisitEntry(
                        id = id,
                        code = code,
                        year = year,
                        month = month,
                        updatedAt = updatedAt,
                        deletedAt = row.optLong("deletedAt", 0L).takeIf { it > 0L }?.coerceAtMost(ceiling),
                        deviceId = row.optStringOrNull("deviceId"),
                    )
                )
            }
        }

        return Payload(
            version = version,
            exportedAt = root.optLong("exportedAt", 0L),
            deviceId = root.optStringOrNull("deviceId"),
            entries = entries,
            unknownStatuses = unknownStatuses.sorted(),
            unreadableRows = unreadableRows,
            visits = visits,
        )
    }

    /**
     * What the save dialog is pre-filled with, e.g. `beyondborders-travel-2026-08-12`.
     *
     * The date is passed in rather than read from a clock so this stays pure; the
     * caller already has to format one for the UI.
     */
    fun suggestedFileName(isoDate: String): String = "beyondborders-travel-$isoDate.json"

    /** org.json turns a JSON null into the string "null", which is never wanted. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

/** Why a file is not importable; the wording lives in resources so it follows the app's language. */
enum class BackupFormatReason(@StringRes val res: Int) {
    NOT_JSON(R.string.backup_not_json),
    WRONG_FORMAT(R.string.backup_wrong_format),
    NEWER_VERSION(R.string.backup_newer_version),
    NO_PLACES(R.string.backup_no_places),
}

/**
 * The file is not something this app can import. [reason] (and [version] for a newer
 * file) is all it carries; [userText] maps it to the text the settings screen shows.
 */
class BackupFormatException(
    val reason: BackupFormatReason,
    val version: Int = 0,
    cause: Throwable? = null,
) : Exception("${reason.name} (format $version)", cause) {
    val userText: UiText
        get() = if (reason == BackupFormatReason.NEWER_VERSION) UiText.Res(reason.res, version) else UiText.Res(reason.res)
}

/**
 * A written backup and enough of a summary to tell the user what went into it.
 *
 * [removals] are the tombstones — places that were marked once and then un-marked.
 * They are in the file on purpose but they are not achievements, so the UI counts
 * them separately rather than claiming a larger number of places than the passport
 * shows.
 */
data class BackupExport(val json: String, val places: Int, val removals: Int)
