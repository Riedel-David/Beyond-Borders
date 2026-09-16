package com.riedel.beyondborders.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.riedel.beyondborders.BeyondBordersApp
import com.riedel.beyondborders.MainActivity
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.PlaceRepository
import com.riedel.beyondborders.ui.places.computeStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A headline number on the home screen, read straight from the same
 * [PlaceRepository] the app itself uses — there is no separate widget data store
 * to fall out of sync with it. Which number, and whether the card carries its own
 * background, is chosen per instance in [TravelWidgetConfigActivity] and read
 * back from [WidgetPrefs].
 */
class TravelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            // Both halves of this matter. finish() in a finally, because a throw
            // between goAsync() and it leaves the broadcast open until the system
            // times it out — and this receiver is also the ACTION_APPWIDGET_UPDATE
            // target, so the leak costs every later update, not just this one. And
            // the catch, because nothing above us handles it: an unhandled throw in
            // a bare CoroutineScope reaches the thread's default handler and takes
            // the app down over a home-screen count. The widget keeps the number it
            // last drew instead, which is a stale count rather than a crash.
            try {
                appWidgetIds.forEach { id -> updateOne(context, id) }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> WidgetPrefs.clear(context, id) }
    }

    /**
     * Restoring a backup onto a new phone gives every widget a new id, and the system
     * reports the mapping here exactly once.
     *
     * Without this the per-instance choices stayed filed under the old ids: every
     * restored widget silently fell back to the defaults while the settings it should
     * have used sat there under ids nothing would ever read again.
     */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) ->
            WidgetPrefs.rekey(context, old, new)
        }
    }

    companion object {
        /** Shared by [onUpdate] and by the config activity's "Add widget", which
         * has to draw the just-chosen state immediately rather than wait out
         * [android.appwidget.AppWidgetProviderInfo.updatePeriodMillis]. */
        suspend fun updateOne(context: Context, widgetId: Int) {
            val container = (context.applicationContext as BeyondBordersApp).container
            val places = container.places
            // The denominator has to come along: it changes what counts as "been" as
            // well as the total, so leaving computeStats on its ALL default made the
            // home screen say "40 of 252" while the passport tab, with "195 UN"
            // chosen, said "40 of 195" about the same data.
            val stats = computeStats(
                places.places(),
                places.statuses.first(),
                container.settings.denominator.value,
            )
            val metric = WidgetPrefs.metric(context, widgetId)
            val filledBackground = WidgetPrefs.filledBackground(context, widgetId)

            val views = RemoteViews(context.packageName, R.layout.travel_widget)
            val (headline, subline) = widgetText(metric, stats)
            views.setTextViewText(R.id.widget_headline, headline)
            views.setTextViewText(R.id.widget_subline, subline)
            if (filledBackground) {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background)
            } else {
                views.setInt(R.id.widget_root, "setBackgroundColor", Color.TRANSPARENT)
            }
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
        }

        /** Called after any change that could move the count, so the widget never
         * waits out the full [android.appwidget.AppWidgetProviderInfo.updatePeriodMillis]
         * to reflect it. */
        fun requestUpdate(context: Context) {
            // getAppWidgetIds is a synchronous binder call and this is invoked from
            // Activity.onPause, on the main thread, whether or not a widget is even
            // installed — so the whole thing goes to an IO thread. Nothing waits on it.
            CoroutineScope(Dispatchers.IO).launch {
                // Caught for the same reason onUpdate catches, and it matters more
                // here: this runs on every backgrounding, so a binder that is dead
                // or busy — the launcher restarting, the widget host gone — would
                // otherwise reach the thread's default handler and kill the process
                // on the way out of the app. A widget left holding a stale count
                // until its next update is the better failure.
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        android.content.ComponentName(context, TravelWidgetProvider::class.java)
                    )
                    if (ids.isNotEmpty()) {
                        context.sendBroadcast(
                            Intent(context, TravelWidgetProvider::class.java).apply {
                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                            }
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}
