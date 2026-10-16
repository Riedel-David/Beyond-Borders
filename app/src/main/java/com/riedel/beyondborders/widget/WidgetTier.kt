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

/**
 * Tier thresholds, named as constants so they are one-line changes.
 *
 * The heights are the heights the layouts *measure*, not preferences: a tier picked for a
 * box shorter than its content needs does not degrade gracefully, it clips — and before
 * the layouts grew their weighted spacer it clipped by squeezing the last children to
 * zero height, so the label and the stat tiles disappeared entirely rather than being cut
 * off visibly. The first set of numbers here was picked blind and admitted MEDIUM from
 * 110 dp against the 186 dp it needs, and LARGE from 200 dp against 274 dp; a perfectly
 * ordinary 4×2 widget therefore drew three empty tiles. Before changing a layout's content,
 * re-measure: a throwaway androidTest that inflates `travelWidgetViews` and measures it with
 * an UNSPECIFIED height spec reports what the layout needs.
 */
private const val LARGE_MIN_WIDTH_DP = 290
private const val LARGE_MIN_HEIGHT_DP = 261
private const val MEDIUM_MIN_WIDTH_DP = 180
private const val MEDIUM_MIN_HEIGHT_DP = 186

/**
 * Picks the widget tier from its size, following the options bundle thresholds.
 *
 * LARGE at ≥ 290 dp wide and ≥ 261 dp tall; MEDIUM at ≥ 180 dp wide and ≥ 186 dp tall;
 * COMPACT otherwise. LARGE's width is set by its narrowest stat tile label rather than by
 * its height: below ~290 dp the three tiles are too narrow for "CONTINENTS", which is a
 * single word and so breaks mid-word rather than wrapping.
 */
internal fun widgetTier(minWidthDp: Int, minHeightDp: Int): WidgetTier = when {
    minWidthDp >= LARGE_MIN_WIDTH_DP && minHeightDp >= LARGE_MIN_HEIGHT_DP -> WidgetTier.LARGE
    minWidthDp >= MEDIUM_MIN_WIDTH_DP && minHeightDp >= MEDIUM_MIN_HEIGHT_DP -> WidgetTier.MEDIUM
    else -> WidgetTier.COMPACT
}
