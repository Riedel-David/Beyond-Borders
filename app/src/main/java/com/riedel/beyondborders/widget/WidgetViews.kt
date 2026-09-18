package com.riedel.beyondborders.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.map.shareStatTiles
import com.riedel.beyondborders.ui.places.TravelStats

/**
 * Logo bitmap size in dp. Must be at least the largest ImageView size any tier
 * declares in its layout (currently 48dp in both medium and large).
 */
private const val LOGO_SIZE_DP = 48

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

    // Text colors: white on brand, theme colors on quiet. Every colour the layouts
    // hardcode is white-alpha, which is right only on the brand card — the brand card is
    // dark in both light and dark mode, the quiet card is not. So anything the layouts
    // set has to be re-set here for the quiet case, including the wordmark and the tiles
    // below; leaving the wordmark out left it white on near-white in light mode.
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
            width = (LOGO_SIZE_DP * context.resources.displayMetrics.density).toInt(),
            height = (LOGO_SIZE_DP * context.resources.displayMetrics.density).toInt(),
        )
        views.setImageViewBitmap(R.id.widget_logo, logoBitmap)

        views.setTextColor(
            R.id.widget_wordmark,
            if (filled) 0xDBFFFFFF.toInt() else context.getColor(R.color.widget_quiet_muted),
        )

        views.setProgressBar(R.id.widget_progress, 100, widgetProgressValue(stats.percent), false)
    }

    // LARGE adds the three stat tiles. shareStatTiles returns exactly three pairs;
    // indexing [0][1][2] is safe today but if that ever changes the widget silently
    // keeps a stale count (updateOne is wrapped in catch (_: Exception)).
    if (tier == WidgetTier.LARGE) {
        val tiles = shareStatTiles(stats)
        val tileIds = listOf(
            Triple(R.id.widget_tile_1, R.id.widget_tile_1_value, R.id.widget_tile_1_label),
            Triple(R.id.widget_tile_2, R.id.widget_tile_2_value, R.id.widget_tile_2_label),
            Triple(R.id.widget_tile_3, R.id.widget_tile_3_value, R.id.widget_tile_3_label),
        )
        val tileBackground =
            if (filled) R.drawable.widget_stat_tile else R.drawable.widget_stat_tile_quiet
        val valueColor =
            if (filled) 0xFFFFFFFF.toInt() else context.getColor(R.color.widget_on_surface)
        val labelColor =
            if (filled) 0x9DFFFFFF.toInt() else context.getColor(R.color.widget_quiet_muted)

        tileIds.forEachIndexed { index, (tile, value, label) ->
            views.setInt(tile, "setBackgroundResource", tileBackground)
            views.setTextViewText(value, tiles[index].first)
            views.setTextViewText(label, tiles[index].second)
            views.setTextColor(value, valueColor)
            views.setTextColor(label, labelColor)
        }
    }

    return views
}

/**
 * The value to hand the widget's progress bar for a travelled [percent], out of 100.
 *
 * A minimum fill of 2 so 1% is a visible dot rather than a sliver the round cap swallows,
 * matching the share card's own bar — but only above zero. `MapShare.drawProgressBar`
 * returns before drawing any fill at `percent <= 0`, so an unconditional floor shows a dot
 * for nothing travelled, which is the one case the bar has to read as empty. Pure and
 * host-tested because the difference between an empty bar and a 2% dot is otherwise only
 * visible on a device, and it was wrong here for as long as the widget existed.
 */
internal fun widgetProgressValue(percent: Int): Int =
    if (percent <= 0) 0 else percent.coerceAtLeast(2)
