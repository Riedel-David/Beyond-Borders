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
     *
     * Latin diacritics are folded, but Cyrillic letters keep their identity (see
     * [foldForSort]) so Ukrainian І/Ї/Є/Ґ and Й sort and file as letters of their own.
     */
    val sortKey: String = name.foldForSort()
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
 * The Cyrillic letters we order, in one merged Russian + Ukrainian alphabet. The two
 * languages never disagree on relative order: the letters only one of them has (ё ъ ы э
 * for Russian, ґ є і ї for Ukrainian) never compete for the same slot, and shared й sits
 * after и/ї and before к in both.
 */
private const val CYRILLIC_ORDER = "абвгґдеёєжзиіїйклмнопрстуфхцчшщъыьэюя"
private const val CYRILLIC_BASE = 0xE000

/**
 * Like [foldForSearch] for Latin, but each letter in [CYRILLIC_ORDER] becomes a private-use
 * code point in alphabet order, so plain string comparison orders it correctly and
 * [sortInitial] can recover the letter. Folding would turn Й into И and Ї into І, and
 * code-unit order puts Є/І/Ї/Ґ after Я.
 */
internal fun String.foldForSort(): String {
    val nfc = java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFC)
    val out = StringBuilder(nfc.length)
    val latin = StringBuilder()
    fun flush() {
        if (latin.isNotEmpty()) { out.append(latin.toString().foldForSearch()); latin.setLength(0) }
    }
    for (c in nfc) {
        val i = CYRILLIC_ORDER.indexOf(c)
        if (i >= 0) { flush(); out.append((CYRILLIC_BASE + i).toChar()) } else latin.append(c)
    }
    flush()
    return out.toString()
}

/** The section letter for a [Place.sortKey]; ё files under Е, other Cyrillic letters keep their own. */
internal fun sortInitial(sortKey: String): String {
    val c = sortKey.firstOrNull() ?: return ""
    val i = c.code - CYRILLIC_BASE
    if (i in CYRILLIC_ORDER.indices) {
        val letter = CYRILLIC_ORDER[i]
        return (if (letter == '\u0451') '\u0435' else letter).toString().uppercase()
    }
    return sortKey.take(1).uppercase()
}

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
 * bundled map glyphs cover Latin, Cyrillic and general punctuation (U+0000-U+02FF,
 * U+0400-U+04FF, U+2000-U+20FF; German and Spanish letters, incl. ä ö ü ß á é í ó ú ñ,
 * the Ukrainian and Russian alphabets, the ’ ʼ – — in country names all sit inside), so Japanese or Arabic names would render as blanks. Grows
 * together with the glyph set.
 */
internal val LOCALIZED_LANGUAGES = setOf("de", "es", "fr", "pt", "uk", "ru")

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
