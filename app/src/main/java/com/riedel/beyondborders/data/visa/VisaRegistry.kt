package com.riedel.beyondborders.data.visa

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * The bundled visa answers for one passport, read from `visa.json`.
 *
 * Same shape and same reasoning as
 * [com.riedel.beyondborders.data.place.PlaceRegistry]: reference data, not user
 * data, so it is not in Room. 252 entries and ~44 kB, parsed once with the
 * platform's org.json.
 *
 * One passport at a time, not the whole matrix in one asset: `visa.json` for DE
 * (the curated one, with its 53 territory overrides) and one `visa_<code>.json`
 * per other dataset passport (generic programme-based territory answers where a
 * real rule applies, no answer where it doesn't — see tools/visa/rules.py).
 */
class VisaRegistry private constructor(
    /** ISO 3166-1 alpha-2 of the passport these answers are for. */
    val passport: String,
    /** ISO date the dataset was last updated. Shown on every visa screen. */
    val asOf: String,
    val datasetName: String,
    val datasetUrl: String,
    val datasetLicence: String,
    /**
     * The IATA Travel Centre. One hub for every destination rather than a deep
     * link, because it is a form — the UI names it honestly.
     */
    val iataUrl: String,
    val requirements: List<VisaRequirement>,
) {
    private val byCode: Map<String, VisaRequirement> = requirements.associateBy { it.code }

    operator fun get(code: String): VisaRequirement? = byCode[code]

    /** How many places fall in each category, for the passport-home summary. */
    val countByCategory: Map<VisaCategory, Int> =
        VisaCategory.entries.associateWith { category ->
            requirements.count { it.category == category }
        }

    /** How many places fall in each map band, for the lens' legend. */
    val countByBand: Map<VisaBand, Int> =
        VisaBand.entries.associateWith { band ->
            requirements.count { it.category.band == band }
        }

    /** The headline number: places you can walk into with just the passport. */
    val visaFreeCount: Int get() = requirements.count { it.isVisaFree }

    /** Places reachable without an embassy appointment. */
    val easyCount: Int get() = requirements.count { it.isEasy }

    /** Curated entries the app should not present too confidently. */
    val uncertain: List<VisaRequirement>
        get() = requirements.filter { it.confidence == VisaConfidence.LOW }

    companion object {
        const val ASSET_NAME = "visa.json"
        const val DEFAULT_PASSPORT = "DE"

        /** DE keeps the historical bare filename; see build_visa.py's own naming rule. */
        fun assetNameFor(passport: String): String =
            if (passport == DEFAULT_PASSPORT) ASSET_NAME else "visa_${passport.lowercase()}.json"

        fun load(assets: AssetManager, passport: String = DEFAULT_PASSPORT): VisaRegistry {
            val name = assetNameFor(passport)
            val json = assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
            return parse(json, name)
        }

        /**
         * Every passport code the build actually shipped an asset for, sorted. Read
         * from the assets directory itself rather than a separate manifest: the
         * asset filenames are already the single source of truth for what exists.
         */
        fun availablePassports(assets: AssetManager): List<String> {
            val names = assets.list("") ?: emptyArray()
            val codes = sortedSetOf(DEFAULT_PASSPORT)
            for (name in names) {
                if (name.startsWith("visa_") && name.endsWith(".json")) {
                    codes += name.removePrefix("visa_").removeSuffix(".json").uppercase()
                }
            }
            return codes.toList()
        }

        /** Split out from [load] so unit tests can feed it a literal. */
        fun parse(json: String, assetName: String = ASSET_NAME): VisaRegistry {
            val root = JSONObject(json)
            val dataset = root.getJSONObject("dataset")
            val array = root.getJSONArray("requirements")
            val out = ArrayList<VisaRequirement>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                // An unreadable category means an asset from a newer build. Skip
                // that one place rather than fail: the other 251 answers are still
                // worth having, and a missing place reads as "no data" in the UI,
                // which is true.
                val category = VisaCategory.parseOrNull(o.getString("category")) ?: continue
                val origin = VisaOrigin.parseOrNull(o.getString("source")) ?: continue
                out += VisaRequirement(
                    code = o.getString("code"),
                    category = category,
                    days = if (o.has("days") && !o.isNull("days")) o.getInt("days") else null,
                    origin = origin,
                    note = o.optStringOrNull("note"),
                    confidence = o.optStringOrNull("confidence")
                        ?.let(VisaConfidence::parseOrNull),
                    sourceUrl = o.optStringOrNull("sourceUrl"),
                    datasetSaid = o.optStringOrNull("datasetSaid"),
                    officialUrl = o.optStringOrNull("officialUrl"),
                    officialFor = o.optStringOrNull("officialFor"),
                )
            }
            require(out.isNotEmpty()) { "$assetName has no readable requirements" }
            return VisaRegistry(
                passport = root.getString("passport"),
                asOf = root.getString("asOf"),
                datasetName = dataset.getString("name"),
                datasetUrl = dataset.getString("url"),
                datasetLicence = dataset.getString("licence"),
                iataUrl = root.getString("iataUrl"),
                requirements = out,
            )
        }

        /**
         * org.json's optString returns "" for a missing key, which would put empty
         * notes into the UI instead of nothing at all.
         */
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null
    }
}
