package com.riedel.aeroglobe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.riedel.aeroglobe.AeroGlobeApp
import com.riedel.aeroglobe.MainActivity
import com.riedel.aeroglobe.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "X of 252 countries" on the home screen, read straight from the same
 * [com.riedel.aeroglobe.data.place.PlaceRepository] the app itself uses — there is
 * no separate widget data store to fall out of sync with it.
 */
class TravelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val places = (context.applicationContext as AeroGlobeApp).container.places
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
                val total = places.places().size
                val been = places.statuses.first().values.count { it.isBeen }
                appWidgetIds.forEach { id -> updateOne(context, manager, id, been, total) }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun updateOne(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        been: Int,
        total: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.travel_widget)
        views.setTextViewText(R.id.widget_headline, "$been of $total")
        val percent = if (total == 0) 0 else (been * 100) / total
        views.setTextViewText(R.id.widget_subline, "$percent% of the world")
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        /** Called after any change that could move the count, so the widget never
         * waits out the full [android.appwidget.AppWidgetProviderInfo.updatePeriodMillis]
         * to reflect it. */
        fun requestUpdate(context: Context) {
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
        }
    }
}
