package com.riedel.beyondborders.widget

import com.riedel.beyondborders.ui.places.TravelStats

/** What the text widget's headline counts. Stored per widget id in [WidgetPrefs]. */
enum class WidgetMetric(val storageValue: String) {
    COUNTRIES("countries"),
    PERCENT("percent"),
    CONTINENTS("continents"),
    ;

    companion object {
        val DEFAULT = COUNTRIES

        fun fromStorage(value: String?): WidgetMetric =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** (headline, subline) for the text widget, split out for host-JVM testing. */
internal fun widgetText(metric: WidgetMetric, stats: TravelStats): Pair<String, String> =
    when (metric) {
        WidgetMetric.COUNTRIES -> "${stats.been} of ${stats.total}" to "${stats.percent}% of the world"
        WidgetMetric.PERCENT -> "${stats.percent}%" to "of the world visited"
        WidgetMetric.CONTINENTS ->
            "${stats.continentsTouched} of ${stats.continents.size}" to "continents touched"
    }

/** Just the hero number for the new widget layouts. */
internal fun widgetHeadline(metric: WidgetMetric, stats: TravelStats): String =
    when (metric) {
        WidgetMetric.COUNTRIES -> "${stats.been}"
        WidgetMetric.PERCENT -> "${stats.percent}%"
        WidgetMetric.CONTINENTS -> "${stats.continentsTouched}"
    }

/** The label beneath the hero number in the new widget layouts. */
internal fun widgetLabel(metric: WidgetMetric, stats: TravelStats): String =
    when (metric) {
        WidgetMetric.COUNTRIES -> "PLACES BEEN"
        WidgetMetric.PERCENT -> "OF THE WORLD"
        WidgetMetric.CONTINENTS -> "CONTINENTS"
    }
