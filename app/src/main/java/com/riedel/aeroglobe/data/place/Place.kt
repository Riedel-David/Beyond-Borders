package com.riedel.aeroglobe.data.place

/**
 * A place is the unit of "I have been there" — one polygon on the map, one row in
 * the passport list, one tap to toggle.
 *
 * Note what is NOT here: there is no `parentCode`. That absence is deliberate and
 * is the reason this app exists. Every other tracker models Greenland as a child
 * of Denmark and then cascades selection down the tree, so tapping Denmark also
 * marks Greenland. With no parent link there is nothing to cascade along, so the
 * bug is not merely fixed, it is inexpressible. See PLAN.md section 3.
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
    /** Label anchor: centroid of the largest polygon, not of the whole extent. */
    val lat: Double,
    val lon: Double,
    /** Where the camera should go when this place is picked. */
    val bounds: Bounds,
) {
    /** Case- and diacritic-insensitive haystack for the passport search field. */
    val searchKey: String = "${name.foldForSearch()} ${code.lowercase()} ${sovereign.foldForSearch()}"

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
