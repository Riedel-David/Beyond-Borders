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

/** Just the hero number for the widget layouts. */
internal fun widgetHeadline(metric: WidgetMetric, stats: TravelStats): String =
    when (metric) {
        WidgetMetric.COUNTRIES -> "${stats.been}"
        WidgetMetric.PERCENT -> "${stats.percent}%"
        WidgetMetric.CONTINENTS -> "${stats.continentsTouched}"
    }
