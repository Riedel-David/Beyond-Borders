package com.riedel.beyondborders.data.place

/**
 * The names people actually think in — "Balkans", "Scandinavia", "Benelux" — as
 * opposed to `Place.region`'s UN M49 buckets ("Southern Europe", "Central Asia"),
 * which no one has ever said out loud. Hand-curated, same shape and same caveat as
 * the visa territory overrides in `tools/geo/rules.py`: someone will disagree with
 * a boundary, which is fine for a private stats screen and not fine for anything
 * presented as authoritative.
 *
 * Two sets are fuzzy at the edges and were picked deliberately rather than argued
 * over every time this file is touched:
 * - The Stans is the clean five "-stan" states. Afghanistan and Pakistan are
 *   Southern Asia in this dataset and colloquially "the Stans" only sometimes, so
 *   they stay out.
 * - Balkans is the nine-country uncontested core. Romania and Türkiye are argued
 *   in and out depending who you ask, so they stay out too.
 *
 * A place can belong to zero or one group here — this is not a partition of the
 * 252 places the way continent/region is, so [PlacesViewModel.computeInformalRegionStats]
 * counts membership rather than grouping by it.
 */
data class NamedGroup(val name: String, val codes: Set<String>)

val INFORMAL_REGIONS: List<NamedGroup> = listOf(
    NamedGroup("Benelux", setOf("BE", "NL", "LU")),
    NamedGroup("Scandinavia", setOf("NO", "SE", "DK")),
    NamedGroup("Nordics", setOf("NO", "SE", "DK", "FI", "IS")),
    NamedGroup("Baltics", setOf("EE", "LV", "LT")),
    NamedGroup("British Isles", setOf("GB", "IE", "IM", "GG", "JE")),
    NamedGroup("Iberia", setOf("ES", "PT", "AD", "GI")),
    NamedGroup("Alpine states", setOf("CH", "AT", "LI")),
    NamedGroup("Visegrád / Central Europe", setOf("PL", "CZ", "SK", "HU")),
    NamedGroup("Caucasus", setOf("GE", "AM", "AZ")),
    NamedGroup("Levant", setOf("IL", "PS", "LB", "SY", "JO")),
    NamedGroup("Arabian Peninsula / Gulf", setOf("SA", "AE", "QA", "KW", "BH", "OM", "YE")),
    NamedGroup("Maghreb", setOf("MA", "DZ", "TN", "LY")),
    NamedGroup("Horn of Africa", setOf("ET", "SO", "ER", "DJ")),
    NamedGroup("Indochina", setOf("VN", "LA", "KH")),
    NamedGroup("The Stans", setOf("KZ", "KG", "TJ", "TM", "UZ")),
    NamedGroup("Balkans", setOf("AL", "BA", "HR", "GR", "XK", "ME", "MK", "RS", "SI")),
)
