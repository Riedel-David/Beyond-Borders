package com.riedel.beyondborders.widget

/**
 * A widget tap intent delivered to [MainActivity], carrying which tab to open and
 * whether to open the stats screen.
 *
 * [timestamp] gives each tap a distinct identity so `mutableStateOf` with structural
 * equality recognizes the state change and re-runs the `LaunchedEffect` that applies
 * the intent. Without it, a second tap on the same widget (same tab and openStats
 * values) is a no-op write: the state never changes and the effect never re-runs.
 */
data class WidgetIntent(
    val tabName: String,
    val openStats: Boolean,
    val timestamp: Long,
)
