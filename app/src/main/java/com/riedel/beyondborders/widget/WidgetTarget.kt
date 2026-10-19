package com.riedel.beyondborders.widget

/**
 * Where a tap on the widget lands. Chosen per widget id in the config screen and
 * stored in [WidgetPrefs]. Every target sends an explicit tab, so a tap always goes
 * somewhere: an intent with no destination just resumes whatever the app was last
 * showing, which is not "open the map".
 */
enum class WidgetTarget(val storageValue: String, val label: String) {
    MAP("map", "Map"),
    PASSPORT("passport", "Passport"),
    STATS("stats", "Stats"),
    ;

    companion object {
        /** What a tap did before it was configurable, so widgets placed earlier don't change. */
        fun defaultFor(metric: WidgetMetric): WidgetTarget = when (metric) {
            WidgetMetric.COUNTRIES -> MAP
            WidgetMetric.PERCENT, WidgetMetric.CONTINENTS -> STATS
        }

        fun fromStorage(value: String?): WidgetTarget? =
            entries.firstOrNull { it.storageValue == value }
    }
}
