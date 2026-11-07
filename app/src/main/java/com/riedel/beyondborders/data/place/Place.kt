package com.riedel.beyondborders.data.place

import java.util.Locale

/**
 * A place is the unit of "I have been there" — one polygon on the map, one row in
 * the passport list, one tap to toggle.
 *
 * Note what is NOT here: there is no `parentCode`. That absence is deliberate and
 * is the reason this app exists. Every other tracker models Greenland as a child
 * of Denmark and then cascades selection down the tree, so tapping Denmark also
 * marks Greenland. With no parent link there is nothing to cascade along, so the
 * bug is not merely fixed, it is inexpressible.
 *
 * Populated from the bundled `places.json`, which tools/geo/build_geo.py
 * generates from Natural Earth map units.
 */
data class Place(
    /** ISO 3166-1 alpha-2 where one exists, otherwise a private XA-XZ code. */
    val code: String,
    val name: String,
    val continent: String,
    val region: String,
    /** Sovereign state per Natural Earth. Display only — never used to group taps. */
    val sovereign: String,
    /** True for Antarctica, Bouvet Island and friends. Sorted last in lists. */
    val uninhabited: Boolean,
    /** One of the 193 UN member states, or one of the two permanent observers
     * (Holy See, Palestine) — the "195" a traveller means, as opposed to the
     * app's full 252. See `tools/geo/rules.py`'s `UN_MEMBER`. */
    val unMember: Boolean = true,
    /** Label anchor: centroid of the largest polygon, not of the whole extent. */
    val lat: Double,
    val lon: Double,
    /** Where the camera should go when this place is picked. */
    val bounds: Bounds,
    /** Land area in square km, holes (lakes, enclaves) subtracted. From Natural
     * Earth's own polygon, not a separate dataset — see `tools/geo/build_geo.py`. */
    val areaKm2: Long = 0,
    /** Natural Earth's own population estimate, ~2019. */
    val popEst: Long = 0,
    /**
     * The bundled English wording, kept when [name] has been localized so search can
     * still match it ("Germany" finds Deutschland). Equal to [name] until
     * [localizePlaces] swaps the display name.
     */
    val englishName: String = name,
    /** Same idea for [sovereign]. */
    val englishSovereign: String = sovereign,
) {
    /** Case- and diacritic-insensitive haystack for the passport search field. */
    val searchKey: String = listOf(name, englishName, code, sovereign, englishSovereign)
        .map { it.foldForSearch() }
        .distinct()
        .joinToString(" ")

    /**
     * How the passport list orders and files this place.
     *
     * Sorting on [name] directly is UTF-16 code-unit order, which puts every accented
     * initial past Z — Åland Islands landed after Zimbabwe, Türkiye after Tuvalu, São
     * Tomé and Príncipe after Syria. Folding first fixes the order and the section
     * letter together, since both now come off the same string.
     */
    val sortKey: String = name.foldForSearch()
}

data class Bounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

/**
 * Normalises text for searching: lowercase, and accents stripped so that typing
 * "reunion" finds "Réunion" and "turkiye" finds "Türkiye".
 */
fun String.foldForSearch(): String =
    java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace(DIACRITICS, "")

private val DIACRITICS = "\\p{Mn}+".toRegex()

/**
 * Swaps [Place.name] and [Place.sovereign] for the platform's wording in [locale].
 *
 * English keeps the bundled Natural Earth names: those are the app's chosen wording
 * and the platform's would differ ("Czechia", "Türkiye" and so on). Only real ISO
 * 3166-1 alpha-2 codes are looked up; the private XA-XZ codes, and any answer that is
 * blank or just the code echoed back, keep the English name. [displayCountry] is a
 * parameter so host tests do not depend on the JVM's CLDR data.
 */
internal fun localizePlaces(
    places: List<Place>,
    locale: Locale,
    displayCountry: (String, Locale) -> String? = ::platformCountryName,
): List<Place> {
    if (locale.language !in LOCALIZED_LANGUAGES) return places
    val byEnglish = HashMap<String, String>()
    val byCode = HashMap<String, String>()
    for (p in places) {
        if (p.code in ISO_COUNTRIES) {
            displayCountry(p.code, locale)
                ?.replace('\u2019', '\'')
                ?.takeIf { it.isNotBlank() && !it.equals(p.code, ignoreCase = true) }
                ?.let { byEnglish[p.englishName] = it; byCode[p.code] = it }
        }
    }
    return places.map { p ->
        // A sovereign with no matching place stays English.
        val sovereign = byEnglish[p.englishSovereign]
            ?: SOVEREIGN_CODES[p.englishSovereign]?.let(byCode::get)
            ?: p.englishSovereign
        p.copy(name = byCode[p.code] ?: p.englishName, sovereign = sovereign)
    }
}

/**
 * Languages the app localizes place names for. Not every platform language: the
 * bundled map glyphs cover Latin only (code points 0-511; German and Spanish letters,
 * incl. ä ö ü ß á é í ó ú ñ, all sit inside it), so Japanese, Russian or
 * Arabic names would render as blanks. Grows together with the glyph set.
 */
internal val LOCALIZED_LANGUAGES = setOf("de", "es", "fr", "pt")

/**
 * Sovereign strings in the bundled data that are not any place's English name
 * (Natural Earth's long forms), mapped to the place that is meant. A test checks
 * the asset, so new data cannot leave one out silently.
 */
internal val SOVEREIGN_CODES: Map<String, String> = mapOf(
    "United States of America" to "US",
    "Ivory Coast" to "CI",
    "Democratic Republic of the Congo" to "CD",
    "eSwatini" to "SZ",
    "Federated States of Micronesia" to "FM",
    "Republic of Serbia" to "RS",
    "São Tomé and Principe" to "ST",
    "United Republic of Tanzania" to "TZ",
    "East Timor" to "TL",
    "Turkey" to "TR",
    "Vatican" to "VA",
)

internal fun platformCountryName(code: String, locale: Locale): String? =
    Locale.Builder().setRegion(code).build().getDisplayCountry(locale)

private val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toSet()
