package com.riedel.beyondborders.data.visa.update

import com.riedel.beyondborders.data.visa.VisaRegistry
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONObject

/** Where the published files live. https only; [isHttps] is checked again at fetch time. */
const val UPDATE_BASE_URL = "https://riedel-david.github.io/Beyond-Borders/"
const val MANIFEST_FILE = "manifest.json"

/** The only manifest and data-file versions this build understands; anything else is ignored. */
const val SUPPORTED_MANIFEST_VERSION = 1
const val SUPPORTED_DATA_FORMAT = 1

const val MAX_MANIFEST_BYTES = 256 * 1024
const val MAX_FILE_BYTES = 256 * 1024
const val NETWORK_TIMEOUT_MS = 15_000

/** One passport's published file, as the manifest describes it. */
data class ManifestEntry(val file: String, val sha256: String, val bytes: Int, val asOf: String)

/**
 * [passports] holds the usable entries, keyed by upper-case ISO code. [listed] is every key
 * present in the manifest, usable or not: only a code missing from [listed] counts as
 * dropped, so a malformed entry means "no fetch", never "delete".
 */
data class Manifest(val passports: Map<String, ManifestEntry>, val listed: Set<String> = passports.keys)

/** What to do about the passports we know of, given a manifest that was fetched and understood. */
data class FetchPlan(val fetch: List<String>, val delete: List<String>)

enum class Rejection { TOO_LARGE, WRONG_LENGTH, WRONG_HASH, UNPARSEABLE, WRONG_PASSPORT, ASOF_MISMATCH, FUTURE_DATED, OLDER_THAN_IN_USE }

/** A file was fetched but must not be used; unlike an [IOException] it is not worth retrying. */
open class VisaFetchRejected(message: String) : Exception(message)

/** The server answered 404: the file is not published (yet). A [VisaFetchRejected] for every existing caller. */
class VisaFetchNotFound(message: String) : VisaFetchRejected(message)

/** Network side, behind an interface so the apply logic runs on the host with a fake. */
interface VisaFetcher {
    /** Throws [IOException] on any network trouble, [VisaFetchRejected] if the body exceeds [maxBytes]. */
    fun fetch(file: String, maxBytes: Int): ByteArray
}

/** Disk side. Codes are upper-case. */
interface VisaStore {
    fun codes(): Set<String>
    fun read(code: String): ByteArray?
    fun write(code: String, bytes: ByteArray)
    fun delete(code: String)
}

data class UpdateResult(
    val applied: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    /** Newest asOf among [applied]; null when nothing was applied. */
    val appliedAsOf: String? = null,
    /** True only if a network error cut the run short; the worker maps this to a retry. */
    val ioFailure: Boolean = false,
    /** The manifest was fetched and recognised. A check only counts as made when it was. */
    val manifestSeen: Boolean = false,
) {
    val changed: Boolean get() = applied.isNotEmpty() || deleted.isNotEmpty()
}

/**
 * One whole update run: manifest, plan, deletions, downloads. Never throws for network
 * trouble. Deletion happens only after the manifest was fetched, signed by a known key AND recognised, so a
 * failed or unrecognised fetch can never remove a stored copy ("not yet" is not "dropped").
 */
internal fun runUpdate(
    fetcher: VisaFetcher,
    store: VisaStore,
    wanted: Set<String>,
    bundledAsOf: (String) -> String?,
    today: () -> String = ::todayUtc,
    publicKeys: List<String> = MANIFEST_PUBLIC_KEYS,
): UpdateResult {
    val now = today()
    val manifest = try {
        val raw = fetcher.fetch(MANIFEST_FILE, MAX_MANIFEST_BYTES)
        // The signature is checked before the manifest is even parsed. Missing, malformed or
        // invalid all mean "not recognised" (no unsigned fallback: that would be a downgrade
        // hole), which keeps what is stored and bundled and reads as a plain rejection.
        val signature = fetcher.fetch(SIGNATURE_FILE, MAX_SIGNATURE_BYTES).toString(Charsets.UTF_8)
        if (!verifyManifestSignature(raw, signature, publicKeys)) return UpdateResult()
        parseManifest(raw.toString(Charsets.UTF_8), now)
    } catch (e: IOException) {
        return UpdateResult(ioFailure = true)
    } catch (e: VisaFetchRejected) {
        null
    } ?: return UpdateResult()

    val storedAsOf = store.codes().associateWith { code ->
        runCatching { VisaRegistry.parse(store.read(code)!!.toString(Charsets.UTF_8), code).asOf }.getOrNull()
    }
    val plan = planFetches(manifest, wanted.map { it.uppercase() }.toSet(), storedAsOf, bundledAsOf, now)

    val deleted = ArrayList<String>()
    for (code in plan.delete) {
        runCatching { store.delete(code); deleted += code }
    }

    val applied = ArrayList<String>()
    var appliedAsOf: String? = null
    var ioFailure = false
    for (code in plan.fetch) {
        val entry = manifest.passports.getValue(code)
        try {
            val bytes = fetcher.fetch(entry.file, MAX_FILE_BYTES)
            val inUse = newestInUse(storedAsOf[code], bundledAsOf(code), now)
            if (verifyDownload(bytes, entry, code, inUse, now) != null) continue
            // A disk error here is ours, not the network's: skip the passport, no retry.
            if (runCatching { store.write(code, bytes) }.isFailure) continue
            applied += code
            if (appliedAsOf == null || entry.asOf > appliedAsOf) appliedAsOf = entry.asOf
        } catch (e: IOException) {
            ioFailure = true
        } catch (e: VisaFetchRejected) {
            // skip this passport
        }
    }
    return UpdateResult(applied, deleted, appliedAsOf, ioFailure, manifestSeen = true)
}

// --- pure functions, unit-testable without Android ---------------------------

/** What a manual check tells the user. */
sealed interface VisaCheckOutcome {
    data object UpToDate : VisaCheckOutcome
    data class Updated(val count: Int) : VisaCheckOutcome
    /** The server could not be reached, or the run was cut short by a network error. */
    data object Offline : VisaCheckOutcome
    /** The server answered, but with something this build cannot use or does not understand. */
    data object Unavailable : VisaCheckOutcome
}

internal fun outcomeOf(result: UpdateResult): VisaCheckOutcome = when {
    // No usable manifest: a network error is "offline", anything else was rejected.
    !result.manifestSeen -> if (result.ioFailure) VisaCheckOutcome.Offline else VisaCheckOutcome.Unavailable
    result.applied.isNotEmpty() -> VisaCheckOutcome.Updated(result.applied.size)
    result.ioFailure -> VisaCheckOutcome.Offline
    else -> VisaCheckOutcome.UpToDate
}

/**
 * Null for anything the build should ignore: malformed JSON, a different manifestVersion
 * or dataFormat. A bad entry is skipped on its own rather than failing the lot. The file
 * name must be exactly `visa_<code>.json`, so a manifest cannot point the fetcher at a path.
 */
internal fun parseManifest(json: String, today: String): Manifest? = try {
    val root = JSONObject(json)
    if (root.optInt("manifestVersion", -1) != SUPPORTED_MANIFEST_VERSION ||
        root.optInt("dataFormat", -1) != SUPPORTED_DATA_FORMAT
    ) {
        null
    } else {
        val passports = root.getJSONObject("passports")
        val out = LinkedHashMap<String, ManifestEntry>()
        val listed = HashSet<String>()
        val latest = latestAllowed(today)
        for (key in passports.keys()) {
            val code = key.uppercase()
            listed += code
            if (!CODE_REGEX.matches(code)) continue
            val o = passports.optJSONObject(key) ?: continue
            val file = o.optString("file")
            if (file != "visa_${code.lowercase()}.json") continue
            val bytes = o.optInt("bytes", -1)
            val sha = o.optString("sha256").lowercase()
            val asOf = o.optString("asOf")
            if (bytes < 0 || sha.isEmpty() || !DATE_REGEX.matches(asOf) || asOf > latest) continue
            out[code] = ManifestEntry(file, sha, bytes, asOf)
        }
        Manifest(out, listed)
    }
} catch (e: Exception) {
    null
}

internal val CODE_REGEX = Regex("[A-Z]{2}")
internal val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

internal fun todayUtc(): String = LocalDate.now(ZoneOffset.UTC).toString()

/** The newest asOf believable on [today]: tomorrow, to allow for a publisher ahead in time zone. */
internal fun latestAllowed(today: String): String = LocalDate.parse(today).plusDays(1).toString()

/** The newest copy already usable for a passport: stored (if not future-dated) or bundled. */
internal fun newestInUse(storedAsOf: String?, bundledAsOf: String?, today: String): String? =
    listOfNotNull(storedAsOf?.takeIf { it <= latestAllowed(today) }, bundledAsOf).maxOrNull()

/**
 * [stored] maps each locally stored code to its asOf (null if the file does not parse).
 * Fetch what is wanted or stored and listed with a usable entry newer than everything
 * already in use (stored and bundled). Delete a stored copy only when the manifest does not
 * list its code at all, or when it can never be used (unparseable, future-dated, or not
 * strictly newer than the bundled asset).
 */
internal fun planFetches(
    manifest: Manifest,
    wanted: Set<String>,
    stored: Map<String, String?>,
    bundledAsOf: (String) -> String?,
    today: String,
): FetchPlan {
    val fetch = (wanted + stored.keys).filter { code ->
        val entry = manifest.passports[code] ?: return@filter false
        // Only passports the app already ships are improved; no bundled asset, no fetch.
        val bundled = bundledAsOf(code) ?: return@filter false
        entry.asOf > newestInUse(stored[code], bundled, today)!!
    }.sorted()
    val latest = latestAllowed(today)
    val delete = stored.filter { (code, asOf) ->
        val bundled = bundledAsOf(code)
        code !in manifest.listed || bundled == null ||
            asOf == null || asOf > latest || (bundled != null && !shouldUse(asOf, bundled))
    }.keys.sorted()
    return FetchPlan(fetch, delete)
}

/** Null means accept. [inUseAsOf] is the newest copy already usable for this passport. */
internal fun verifyDownload(
    bytes: ByteArray,
    entry: ManifestEntry,
    expectedCode: String,
    inUseAsOf: String?,
    today: String,
): Rejection? {
    if (bytes.size > MAX_FILE_BYTES) return Rejection.TOO_LARGE
    if (bytes.size != entry.bytes) return Rejection.WRONG_LENGTH
    if (sha256Hex(bytes) != entry.sha256.lowercase()) return Rejection.WRONG_HASH
    val registry = try {
        VisaRegistry.parse(bytes.toString(Charsets.UTF_8), entry.file)
    } catch (e: Exception) {
        return Rejection.UNPARSEABLE
    }
    if (registry.passport != expectedCode) return Rejection.WRONG_PASSPORT
    if (registry.asOf != entry.asOf) return Rejection.ASOF_MISMATCH
    if (registry.asOf > latestAllowed(today)) return Rejection.FUTURE_DATED
    if (inUseAsOf != null && registry.asOf < inUseAsOf) return Rejection.OLDER_THAN_IN_USE
    return null
}

/** A stored copy is used only when strictly newer than the bundled one (ISO dates compare as strings). */
internal fun shouldUse(storedAsOf: String, bundledAsOf: String): Boolean = storedAsOf > bundledAsOf

/** Bundled unless the stored copy exists, parses, is for this passport and is newer; any failure means bundled. */
internal fun preferStored(bundled: VisaRegistry, today: () -> String = ::todayUtc, stored: () -> VisaRegistry?): VisaRegistry = try {
    stored()?.takeIf {
        it.passport == bundled.passport && shouldUse(it.asOf, bundled.asOf) && it.asOf <= latestAllowed(today())
    } ?: bundled
} catch (e: Exception) {
    bundled
}

/** What a non-200 answer means: 404 is "not published", other non-retryable 4xx a rejection, the rest worth retrying. */
internal fun failureForStatus(status: Int): Exception = when {
    status == 404 -> VisaFetchNotFound("HTTP 404")
    status in 400..499 && !isRetryableStatus(status) -> VisaFetchRejected("HTTP $status")
    else -> IOException("HTTP $status")
}

/** Timeouts, throttling and server errors may pass; any other 4xx is final. */
internal fun isRetryableStatus(status: Int): Boolean = status == 408 || status == 429 || status >= 500

internal fun isHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
