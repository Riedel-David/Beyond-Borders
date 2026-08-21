package com.riedel.aeroglobe.data.backup

import com.riedel.aeroglobe.data.place.PlaceStatus
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
    const val FORMAT = "aeroglobe.travel"

    /**
     * Bumped only for a change that an older reader would misinterpret. Adding an
     * optional field does not qualify — unknown keys are ignored on read and
     * missing ones fall back to their defaults.
     */
    const val VERSION = 1

    const val MIME_TYPE = "application/json"

    /**
     * One place's row as it appears in the file.
     *
     * Deliberately not [com.riedel.aeroglobe.data.db.PlaceStatusEntity]: the
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

    /**
     * A decoded file: the header plus its rows.
     *
     * [unknownStatuses] holds one entry per *row* that was dropped because this build
     * does not know its status, so its size is a row count and its distinct values
     * name the statuses. See [decode] for why those rows are dropped rather than read.
     */
    data class Payload(
        val version: Int,
        val exportedAt: Long,
        val deviceId: String?,
        val entries: List<Entry>,
        val unknownStatuses: List<String> = emptyList(),
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
        return root.toString(2)
    }

    /**
     * Read the file, or explain why it cannot be read.
     *
     * Strict about the header and forgiving about the rows: a wrong `format` means
     * the user picked the wrong file and should be told, whereas one row missing an
     * `updatedAt` should not cost them the other 200.
     *
     * @throws BackupFormatException if this is not an AeroGlobe backup, or is a
     *   newer one than this build understands.
     */
    fun decode(text: String): Payload {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("This file is not JSON.", e)
        }

        val format = root.optString("format")
        if (format != FORMAT) {
            throw BackupFormatException("This is not an AeroGlobe backup file.")
        }
        val version = root.optInt("version", 0)
        if (version > VERSION) {
            throw BackupFormatException(
                "This backup was written by a newer version of AeroGlobe (format $version)."
            )
        }

        val array = root.optJSONArray("places")
            ?: throw BackupFormatException("The backup has no places in it.")

        val entries = ArrayList<Entry>(array.length())
        val unknownStatuses = ArrayList<String>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val code = row.optString("code").takeIf { it.isNotBlank() } ?: continue
            // A row with no timestamp cannot be merged safely: last-write-wins has
            // nothing to compare, and defaulting to "now" would let a stale backup
            // silently overwrite newer local edits.
            val updatedAt = row.optLong("updatedAt", 0L).takeIf { it > 0L } ?: continue
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
                    deletedAt = row.optLong("deletedAt", 0L).takeIf { it > 0L }
                        ?: updatedAt.takeIf { status == PlaceStatus.NONE },
                    deviceId = row.optStringOrNull("deviceId"),
                )
            )
        }

        return Payload(
            version = version,
            exportedAt = root.optLong("exportedAt", 0L),
            deviceId = root.optStringOrNull("deviceId"),
            entries = entries,
            unknownStatuses = unknownStatuses.sorted(),
        )
    }

    /**
     * What the save dialog is pre-filled with, e.g. `aeroglobe-travel-2026-08-12`.
     *
     * The date is passed in rather than read from a clock so this stays pure; the
     * caller already has to format one for the UI.
     */
    fun suggestedFileName(isoDate: String): String = "aeroglobe-travel-$isoDate.json"

    /** org.json turns a JSON null into the string "null", which is never wanted. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

/** The file is not something this app can import, with a reason fit to show a user. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A written backup and enough of a summary to tell the user what went into it.
 *
 * [removals] are the tombstones — places that were marked once and then un-marked.
 * They are in the file on purpose but they are not achievements, so the UI counts
 * them separately rather than claiming a larger number of places than the passport
 * shows.
 */
data class BackupExport(val json: String, val places: Int, val removals: Int)
