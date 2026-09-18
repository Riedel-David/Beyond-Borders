package com.riedel.beyondborders.widget

/** How much the widget shows, picked from its size in dp. */
enum class WidgetTier {
    /** Hero number and label only, on the brand background. */
    COMPACT,
    /** Adds logo, wordmark and progress bar. The default. */
    MEDIUM,
    /** Adds three stat tiles beneath the progress bar. */
    LARGE,
}

/** Tier thresholds, named as constants so they are one-line changes. */
private const val LARGE_MIN_WIDTH_DP = 250
private const val LARGE_MIN_HEIGHT_DP = 200
private const val MEDIUM_MIN_WIDTH_DP = 180
private const val MEDIUM_MIN_HEIGHT_DP = 110

/**
 * Picks the widget tier from its size, following the options bundle thresholds.
 *
 * LARGE at ≥ 250 dp wide and ≥ 200 dp tall; MEDIUM at ≥ 180 dp wide and ≥ 110 dp tall;
 * COMPACT otherwise. All three thresholds were picked blind and are flagged in
 * WIDGET_CHECKS.md.
 */
internal fun widgetTier(minWidthDp: Int, minHeightDp: Int): WidgetTier = when {
    minWidthDp >= LARGE_MIN_WIDTH_DP && minHeightDp >= LARGE_MIN_HEIGHT_DP -> WidgetTier.LARGE
    minWidthDp >= MEDIUM_MIN_WIDTH_DP && minHeightDp >= MEDIUM_MIN_HEIGHT_DP -> WidgetTier.MEDIUM
    else -> WidgetTier.COMPACT
}
