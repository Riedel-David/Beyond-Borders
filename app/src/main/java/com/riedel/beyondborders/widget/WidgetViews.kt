package com.riedel.beyondborders.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.map.shareStatTiles
import com.riedel.beyondborders.ui.places.TravelStats

/**
 * Builds the widget's RemoteViews for the chosen tier, metric and background.
 *
 * Shared by [TravelWidgetProvider.updateOne] and the config activity's preview so
 * the preview is always the real widget, not a Compose mock that could drift.
 */
internal fun travelWidgetViews(
    context: Context,
    tier: WidgetTier,
    metric: WidgetMetric,
    filled: Boolean,
    stats: TravelStats,
): RemoteViews {
    val layoutId = when (tier) {
        WidgetTier.COMPACT -> R.layout.travel_widget_compact
        WidgetTier.MEDIUM -> R.layout.travel_widget_medium
        WidgetTier.LARGE -> R.layout.travel_widget_large
    }

    val views = RemoteViews(context.packageName, layoutId)

    // Background: brand card vs quiet card
    if (filled) {
        views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_brand)
    } else {
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            R.drawable.widget_background,
        )
    }

    // Hero number and label
    val headline = widgetHeadline(metric, stats)
    val label = widgetLabel(metric, stats)
    views.setTextViewText(R.id.widget_headline, headline)
    views.setTextViewText(R.id.widget_label, label)

    // Text colors: white on brand, theme colors on quiet
    if (filled) {
        views.setTextColor(R.id.widget_headline, 0xFFFFFFFF.toInt())
        views.setTextColor(R.id.widget_label, 0xDDFFFFFF.toInt())
    } else {
        views.setTextColor(R.id.widget_headline, context.getColor(R.color.widget_on_surface))
        views.setTextColor(R.id.widget_label, context.getColor(R.color.widget_accent))
    }

    // MEDIUM and LARGE add logo, wordmark and progress bar
    if (tier == WidgetTier.MEDIUM || tier == WidgetTier.LARGE) {
        // Logo: the launcher icon as a bitmap. AdaptiveIconDrawable applies its own
        // mask, so the ImageView shows the masked icon the home screen shows.
        val logo = context.packageManager.getApplicationIcon(context.packageName)
        val logoBitmap = logo.toBitmap(
            width = (48 * context.resources.displayMetrics.density).toInt(),
            height = (48 * context.resources.displayMetrics.density).toInt(),
        )
        views.setImageViewBitmap(R.id.widget_logo, logoBitmap)

        // Progress bar: enforce a minimum fill so 1% is a visible dot, matching
        // MapShare.drawProgressBar's reason for doing the same.
        val clampedPercent = stats.percent.coerceAtLeast(2)
        views.setProgressBar(R.id.widget_progress, 100, clampedPercent, false)
    }

    // LARGE adds the three stat tiles
    if (tier == WidgetTier.LARGE) {
        val tiles = shareStatTiles(stats)
        views.setTextViewText(R.id.widget_tile_1_value, tiles[0].first)
        views.setTextViewText(R.id.widget_tile_1_label, tiles[0].second)
        views.setTextViewText(R.id.widget_tile_2_value, tiles[1].first)
        views.setTextViewText(R.id.widget_tile_2_label, tiles[1].second)
        views.setTextViewText(R.id.widget_tile_3_value, tiles[2].first)
        views.setTextViewText(R.id.widget_tile_3_label, tiles[2].second)
    }

    return views
}
