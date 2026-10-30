package com.riedel.beyondborders.widget

/** How much the widget shows, picked from its size in dp. */
enum class WidgetTier {
    /** Hero number and label on the brand background, with a small logo in the corner. */
    COMPACT,
    /** Adds logo, wordmark and progress bar. The default. */
    MEDIUM,
    /** COMPACT with the wordmark beside the logo, for a narrow widget that is tall enough. */
    NAMED,
    /** Hero beside three one-line stat tiles, for a box too short for MEDIUM and WIDE. */
    STRIP,
    /** MEDIUM's content with the three stat tiles in a column beside it. */
    WIDE,
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
private const val WIDE_MIN_WIDTH_DP = 205
private const val NAMED_MIN_HEIGHT_DP = 130
private const val NAMED_MIN_WIDTH_DP = 100
private const val MEDIUM_MIN_WIDTH_DP = 180
private const val MEDIUM_MIN_HEIGHT_DP = 186
private const val STRIP_MIN_WIDTH_DP = 190
private const val STRIP_MIN_HEIGHT_DP = 120

/**
 * Picks the widget tier from its size, following the options bundle thresholds.
 *
 * LARGE at ≥ 290 dp wide and ≥ 261 dp tall; WIDE at ≥ 205 dp wide and ≥ 186 dp tall (the
 * tiles sit beside the hero, so it needs only MEDIUM's height; a One UI 3x2 reports
 * 220x202); MEDIUM for the 180-204 dp widths between; STRIP below MEDIUM's height but
 * ≥ 190x120 dp (HyperOS places a 3x2 at 193x132 and 209x148); NAMED when narrower but
 * ≥ 130 dp tall; COMPACT otherwise. WIDE's width and NAMED's height are estimates from the
 * layouts' dimensions,
 * not yet measured. LARGE's width is set by its narrowest stat tile label rather than by
 * its height: below ~290 dp the three tiles are too narrow for "CONTINENTS", which is a
 * single word and so breaks mid-word rather than wrapping.
 */
internal fun widgetTier(minWidthDp: Int, minHeightDp: Int): WidgetTier = when {
    minWidthDp >= LARGE_MIN_WIDTH_DP && minHeightDp >= LARGE_MIN_HEIGHT_DP -> WidgetTier.LARGE
    minWidthDp >= WIDE_MIN_WIDTH_DP && minHeightDp >= MEDIUM_MIN_HEIGHT_DP -> WidgetTier.WIDE
    minWidthDp >= MEDIUM_MIN_WIDTH_DP && minHeightDp >= MEDIUM_MIN_HEIGHT_DP -> WidgetTier.MEDIUM
    minWidthDp >= STRIP_MIN_WIDTH_DP && minHeightDp >= STRIP_MIN_HEIGHT_DP -> WidgetTier.STRIP
    minWidthDp >= NAMED_MIN_WIDTH_DP && minHeightDp >= NAMED_MIN_HEIGHT_DP -> WidgetTier.NAMED
    else -> WidgetTier.COMPACT
}
