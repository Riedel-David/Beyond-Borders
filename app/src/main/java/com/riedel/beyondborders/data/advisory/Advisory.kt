package com.riedel.beyondborders.data.advisory

import com.riedel.beyondborders.data.visa.update.DATE_REGEX
import com.riedel.beyondborders.data.visa.update.isHttps
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject

/** The only `advisory.json` dataFormat this build understands; any other file is ignored whole. */
const val SUPPORTED_ADVISORY_FORMAT = 1
const val ADVISORY_FILE = "advisory.json"

// Three sources, one line each, a country a few hundred bytes a source: well under this, and
// a bound so a wrong file on the host cannot fill memory. Larger than the visa files' limit.
const val MAX_ADVISORY_BYTES = 1024 * 1024

/** The pipeline's own floor: a file with fewer countries than this is truncated, whatever else is valid. */
const val MIN_ADVISORY_COUNTRIES = 150

/** A download must keep at least this share of the countries in the copy already in use. */
const val MIN_ADVISORY_RETAINED = 0.8

/** A source with at least this many entries in the copy in use is guarded against collapsing. */
const val MIN_GUARDED_SOURCE_ENTRIES = 100

/** ...and a download must keep at least this share of that source's entries. */
const val MIN_SOURCE_RETAINED = 0.5

/** The sources in the order the rows are shown. */
val ADVISORY_SOURCES = listOf("US", "UK", "DE")

/**
 * One government's rating for one country. [stance] is the pipeline's normalisation to 0..3
 * (0 normal precautions, 1 increased caution, 2 reconsider or parts of the country, 3 do not
 * travel) and exists only so the count and the row accent have something to key on;
 * [label] is what is shown. It is usually the source's own wording, but two are written by the
 * pipeline: the UK's stance-0 "no travel warning" line and the German "no formal warning, but
 * advised against" line, whose reasons are then carried in [notes].
 */
data class AdvisoryEntry(
    val stance: Int,
    val label: String,
    val updated: String,
    val url: String,
    val tags: List<String> = emptyList(),
    /**
     * The source's own sentences, verbatim, about parts of the country or (German) an advice
     * against travel: "Von Reisen nach X wird abgeraten". Any source may carry them.
     */
    val notes: List<String> = emptyList(),
    /**
     * Names which of the pipeline's own UK/German label texts [label] is, so the screen can show
     * its translation. Null for a US entry, an older file or a blank value; [label] is then shown.
     */
    val labelKey: String? = null,
)

// Defensive caps on the optional `notes` field. A note is shown whole or not at all (the Auswärtiges
// Amt's terms forbid showing its text incomplete), so one over the length cap is dropped, never cut.
// MAX_ADVISORY_NOTE_CHARS must stay equal to DE_NOTE_MAX_CHARS in tools/advisory/build_advisory.py,
// which fails the build on a longer note, so a file from the pipeline never loses one here.
const val MAX_ADVISORY_NOTES = 3
const val MAX_ADVISORY_NOTE_CHARS = 1000

/**
 * Hosts an advisory `url` may point at; an entry on any other host is dropped. The pipeline's
 * URL_HOSTS in tools/advisory/build_advisory.py must list the same hosts.
 */
val ADVISORY_URL_HOSTS = setOf("travel.state.gov", "www.gov.uk", "www.auswaertiges-amt.de")

/**
 * [countries] is keyed by upper-case ISO code, then by source key. A source that has nothing
 * for a country is an absent key, which is why "no data" and "no warning" stay different.
 */
data class AdvisoryData(
    val generatedAt: Instant,
    val sourceLabels: Map<String, String>,
    val countries: Map<String, Map<String, AdvisoryEntry>>,
) {
    /** Exact-code lookup only: no sovereign fallback, so Denmark's rating never shows on Greenland. */
    fun forPlace(code: String): Map<String, AdvisoryEntry> = countries[code.uppercase()] ?: emptyMap()
}

/**
 * "[against] of [withData]" sources advise against travel (stance 3). The screen shows this line
 * only when [against] is at least 1: at 0 the rows speak for themselves, and a "0 of 3" would sit
 * above a quoted German sentence advising against travel (stance 2 there) and read as a contradiction. [withData] counts only
 * the sources that have an entry for the country, so a missing source is not a "no".
 */
data class Agreement(val against: Int, val withData: Int)

/** The screen's one agreement line is a function of the entries alone, with no weighting. */
fun agreementOf(entries: Map<String, AdvisoryEntry>): Agreement {
    val present = ADVISORY_SOURCES.mapNotNull { entries[it] }
    return Agreement(present.count { it.stance == 3 }, present.size)
}

/** NEWER_FORMAT: well-formed, but written for a newer app; not a fault, just nothing this build can use. */
enum class AdvisoryRejection { UNPARSEABLE, IMPLAUSIBLE, FUTURE_DATED, NOT_NEWER, NEWER_FORMAT }

/** Entries per source, for the collapse guard. */
internal fun advisorySourceCounts(data: AdvisoryData): Map<String, Int> =
    ADVISORY_SOURCES.associateWith { s -> data.countries.values.count { s in it } }

/** True when [bytes] is a JSON object whose dataFormat is an integer above what this build reads. */
internal fun isNewerAdvisoryFormat(bytes: ByteArray): Boolean = try {
    JSONObject(bytes.toString(Charsets.UTF_8)).optInt("dataFormat", -1) > SUPPORTED_ADVISORY_FORMAT
} catch (e: Exception) {
    false
}

// --- pure functions, unit-testable without Android ---------------------------

/**
 * Null for anything this build should ignore: malformed JSON, another dataFormat, a missing or
 * unreadable generatedAt. A bad entry (stance outside 0..3, no label, a date that is not
 * ISO, a link that is not https) is skipped on its own, leaving that source "no data" for the
 * country rather than inventing a rating or failing the lot.
 */
internal fun parseAdvisory(json: String): AdvisoryData? = try {
    val root = JSONObject(json)
    if (root.optInt("dataFormat", -1) != SUPPORTED_ADVISORY_FORMAT) {
        null
    } else {
        val generatedAt = Instant.parse(root.getString("generatedAt"))
        val labels = LinkedHashMap<String, String>()
        root.optJSONObject("sources")?.let { s ->
            for (key in ADVISORY_SOURCES) {
                (s.optJSONObject(key)?.opt("label") as? String)?.takeIf { it.isNotBlank() }?.let { labels[key] = it }
            }
        }
        val out = LinkedHashMap<String, Map<String, AdvisoryEntry>>()
        val countries = root.getJSONObject("countries")
        for (code in countries.keys()) {
            if (!ISO_REGEX.matches(code)) continue
            val c = countries.optJSONObject(code) ?: continue
            val entries = LinkedHashMap<String, AdvisoryEntry>()
            for (source in ADVISORY_SOURCES) {
                parseEntry(c.optJSONObject(source))?.let { entries[source] = it }
            }
            if (entries.isNotEmpty()) out[code] = entries
        }
        AdvisoryData(generatedAt, labels, out)
    }
} catch (e: Exception) {
    null
}

private val ISO_REGEX = Regex("[A-Z]{2}")

private fun parseEntry(o: JSONObject?): AdvisoryEntry? {
    o ?: return null
    // optInt coerces "3" and 2.9 to an Int; the file's own type is the contract, so only a real Int counts.
    val stance = o.opt("stance") as? Int ?: return null
    // optString turns a JSON null into the text "null"; only a real string counts.
    val label = o.opt("label") as? String ?: return null
    val updated = o.opt("updated") as? String ?: return null
    val url = o.opt("url") as? String ?: return null
    if (stance !in 0..3 || label.isBlank() || !DATE_REGEX.matches(updated) || !isHttps(url)) return null
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()
    if (host !in ADVISORY_URL_HOSTS) return null
    runCatching { LocalDate.parse(updated) }.getOrNull() ?: return null
    val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }
    // Optional and additive: absent, not an array, or non-string members all mean "no notes" for that
    // part, so an older or odd file can never cost the entry itself.
    val notes = o.optJSONArray("notes")?.let { a ->
        (0 until a.length()).mapNotNull { (a.opt(it) as? String)?.trim()?.takeIf(String::isNotEmpty) }
            .filter { it.length <= MAX_ADVISORY_NOTE_CHARS }
            .take(MAX_ADVISORY_NOTES)
    }
    // Optional and additive, like notes: only a non-blank string counts, anything else means "no key".
    val labelKey = (o.opt("labelKey") as? String)?.trim()?.takeIf(String::isNotEmpty)
    return AdvisoryEntry(stance, label, updated, url, tags ?: emptyList(), notes ?: emptyList(), labelKey)
}

/** Tomorrow's instant, to allow for a publisher a time zone ahead. */
internal fun advisoryLatestAllowed(now: Instant): Instant = now.plusSeconds(24 * 3600)

/**
 * Null means accept. [inUse] is the newest copy already usable (stored or bundled). A copy that
 * is not strictly newer is not an error but changes nothing, so it is rejected to save the write.
 */
internal fun verifyAdvisory(
    bytes: ByteArray,
    inUse: Instant?,
    now: Instant,
    inUseCountries: Int = 0,
    minCountries: Int = MIN_ADVISORY_COUNTRIES,
    inUseSources: Map<String, Int> = emptyMap(),
): AdvisoryRejection? {
    if (isNewerAdvisoryFormat(bytes)) return AdvisoryRejection.NEWER_FORMAT
    val data = parseAdvisory(bytes.toString(Charsets.UTF_8)) ?: return AdvisoryRejection.UNPARSEABLE
    // A file with no usable country at all would blank the section for everyone; never accept it.
    if (data.countries.isEmpty()) return AdvisoryRejection.UNPARSEABLE
    // Valid but truncated would replace a full dataset with a partial one, and "no data" would
    // then be shown for places the old copy covered.
    if (data.countries.size < minCountries || data.countries.size < inUseCountries * MIN_ADVISORY_RETAINED) {
        return AdvisoryRejection.IMPLAUSIBLE
    }
    // One whole source vanishing leaves enough countries to pass the check above, yet turns that
    // source's row into "No data" everywhere.
    val counts = advisorySourceCounts(data)
    for ((source, before) in inUseSources) {
        if (before >= MIN_GUARDED_SOURCE_ENTRIES && (counts[source] ?: 0) < before * MIN_SOURCE_RETAINED) {
            return AdvisoryRejection.IMPLAUSIBLE
        }
    }
    if (data.generatedAt > advisoryLatestAllowed(now)) return AdvisoryRejection.FUTURE_DATED
    if (inUse != null && data.generatedAt <= inUse) return AdvisoryRejection.NOT_NEWER
    return null
}

/** The stored copy only when it parses and is strictly newer than the bundled one and not future-dated. */
internal fun chooseAdvisory(bundled: AdvisoryData?, stored: AdvisoryData?, now: Instant): AdvisoryData? {
    val usable = stored?.takeIf {
        it.generatedAt <= advisoryLatestAllowed(now) && (bundled == null || it.generatedAt > bundled.generatedAt)
    }
    return usable ?: bundled
}

internal fun nowInstant(): Instant = Instant.now()
