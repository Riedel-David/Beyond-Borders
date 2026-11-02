package com.riedel.beyondborders.ui.places

import androidx.annotation.StringRes
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.UiText

// Continent, UN region and informal-group names are data values (places.json,
// InformalRegions.kt) that double as keys: collapse keys, sorting and filters all use the
// English string. Only the display goes through here, so a language change never moves a
// section or orphans a collapsed header.
private val CONTINENTS: Map<String, Int> = mapOf(
    "Africa" to R.string.geo_continent_africa,
    "Antarctica" to R.string.geo_continent_antarctica,
    "Asia" to R.string.geo_continent_asia,
    "Europe" to R.string.geo_continent_europe,
    "North America" to R.string.geo_continent_north_america,
    "Oceania" to R.string.geo_continent_oceania,
    "South America" to R.string.geo_continent_south_america,
)

private val REGIONS: Map<String, Int> = mapOf(
    "Antarctica" to R.string.geo_region_antarctica,
    "Australia and New Zealand" to R.string.geo_region_australia_and_new_zealand,
    "Caribbean" to R.string.geo_region_caribbean,
    "Central America" to R.string.geo_region_central_america,
    "Central Asia" to R.string.geo_region_central_asia,
    "Eastern Africa" to R.string.geo_region_eastern_africa,
    "Eastern Asia" to R.string.geo_region_eastern_asia,
    "Eastern Europe" to R.string.geo_region_eastern_europe,
    "Melanesia" to R.string.geo_region_melanesia,
    "Micronesia" to R.string.geo_region_micronesia,
    "Middle Africa" to R.string.geo_region_middle_africa,
    "Northern Africa" to R.string.geo_region_northern_africa,
    "Northern America" to R.string.geo_region_northern_america,
    "Northern Europe" to R.string.geo_region_northern_europe,
    "Polynesia" to R.string.geo_region_polynesia,
    "Seven seas (open ocean)" to R.string.geo_region_seven_seas_open_ocean,
    "South America" to R.string.geo_region_south_america,
    "South-Eastern Asia" to R.string.geo_region_south_eastern_asia,
    "Southern Africa" to R.string.geo_region_southern_africa,
    "Southern Asia" to R.string.geo_region_southern_asia,
    "Southern Europe" to R.string.geo_region_southern_europe,
    "Western Africa" to R.string.geo_region_western_africa,
    "Western Asia" to R.string.geo_region_western_asia,
    "Western Europe" to R.string.geo_region_western_europe,
)

private val GROUPS: Map<String, Int> = mapOf(
    "Benelux" to R.string.geo_group_benelux,
    "Scandinavia" to R.string.geo_group_scandinavia,
    "Nordics" to R.string.geo_group_nordics,
    "Baltics" to R.string.geo_group_baltics,
    "British Isles" to R.string.geo_group_british_isles,
    "Iberia" to R.string.geo_group_iberia,
    "Alpine states" to R.string.geo_group_alpine_states,
    "Visegrád / Central Europe" to R.string.geo_group_visegr_d_central_europe,
    "Caucasus" to R.string.geo_group_caucasus,
    "Levant" to R.string.geo_group_levant,
    "Arabian Peninsula / Gulf" to R.string.geo_group_arabian_peninsula_gulf,
    "Maghreb" to R.string.geo_group_maghreb,
    "Horn of Africa" to R.string.geo_group_horn_of_africa,
    "Indochina" to R.string.geo_group_indochina,
    "The Stans" to R.string.geo_group_the_stans,
    "Balkans" to R.string.geo_group_balkans,
)

/** Resource for a continent, region or informal-group name, or null for a value with no translation. */
@StringRes
internal fun geoNameRes(key: String): Int? = CONTINENTS[key] ?: REGIONS[key] ?: GROUPS[key]

/** [key] as display text; an unknown value falls back to itself rather than to nothing. */
internal fun geoNameText(key: String): UiText = geoNameRes(key)?.let { UiText.Res(it) } ?: UiText.Plain(key)

internal fun allGeoNameKeys(): Set<String> = CONTINENTS.keys + REGIONS.keys + GROUPS.keys
