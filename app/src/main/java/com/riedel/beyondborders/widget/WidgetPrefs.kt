package com.riedel.beyondborders.widget

import android.content.Context

/**
 * Per-instance choices for the travel widget, keyed by widget id so that two
 * copies on the same home screen can show different things. Set once from
 * [TravelWidgetConfigActivity]; read on every update.
 */
object WidgetPrefs {
    private const val PREFS_NAME = "widget_prefs"

    fun metric(context: Context, widgetId: Int): WidgetMetric =
        WidgetMetric.fromStorage(prefs(context).getString(metricKey(widgetId), null))

    fun filledBackground(context: Context, widgetId: Int): Boolean =
        prefs(context).getBoolean(backgroundKey(widgetId), true)

    fun save(context: Context, widgetId: Int, metric: WidgetMetric, filledBackground: Boolean) {
        prefs(context).edit()
            .putString(metricKey(widgetId), metric.storageValue)
            .putBoolean(backgroundKey(widgetId), filledBackground)
            .apply()
    }

    /**
     * Move one widget's choices from [oldId] to [newId], for
     * [TravelWidgetProvider.onRestored].
     *
     * Reads the old values explicitly rather than defaulting, so a widget that was
     * never configured stays unconfigured instead of being written out at the
     * defaults under its new id.
     */
    fun rekey(context: Context, oldId: Int, newId: Int) {
        val prefs = prefs(context)
        val metric = prefs.getString(metricKey(oldId), null) ?: return
        val filled = prefs.getBoolean(backgroundKey(oldId), true)
        prefs.edit()
            .putString(metricKey(newId), metric)
            .putBoolean(backgroundKey(newId), filled)
            .remove(metricKey(oldId))
            .remove(backgroundKey(oldId))
            .apply()
    }

    /** Called from [TravelWidgetProvider.onDeleted] so a removed widget's choices
     * don't linger forever under an id nothing will ever read again. */
    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(metricKey(widgetId))
            .remove(backgroundKey(widgetId))
            .apply()
    }

    private fun metricKey(widgetId: Int) = "metric_$widgetId"
    private fun backgroundKey(widgetId: Int) = "background_$widgetId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
