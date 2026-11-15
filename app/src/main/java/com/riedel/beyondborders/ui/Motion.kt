package com.riedel.beyondborders.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


/**
 * The app's one motion vocabulary: durations, easing and the shared enter/exit transitions.
 *
 * Do not use `animateContentSize` on a width-wrapping Column as an expand mechanism: it
 * animates width as well as height, starting from 0, so the content wipes in from left to
 * right (the bug the Statistics screen had). Use [ExpandableSection] instead.
 */
object Motion {
    /** Expand/collapse and chevron rotation. */
    const val DurationMedium = 250

    /** Fades and content swaps. */
    const val DurationShort = 180

    val Easing = FastOutSlowInEasing

    val expandEnter: EnterTransition =
        expandVertically(tween(DurationMedium, easing = Easing), expandFrom = Alignment.Top) +
            fadeIn(tween(DurationMedium, easing = Easing))

    val expandExit: ExitTransition =
        shrinkVertically(tween(DurationMedium, easing = Easing), shrinkTowards = Alignment.Top) +
            fadeOut(tween(DurationMedium / 2, easing = Easing))

    /** Lazy-list rows arriving, leaving or moving: the same specs in every list. */
    fun LazyItemScope.animateItemMotion(): Modifier = Modifier.animateItem(
        fadeInSpec = tween(DurationShort, easing = Easing),
        placementSpec = tween(DurationMedium, easing = Easing),
        fadeOutSpec = tween(DurationShort, easing = Easing),
    )

    /** For cards and panels that appear over content; [slidePx] 0 means a plain fade. */
    fun cardEnter(slidePx: Int = 0): EnterTransition =
        fadeIn(tween(120, easing = Easing)) +
            if (slidePx != 0) slideInVertically(tween(DurationShort, easing = Easing)) { slidePx } else EnterTransition.None

    fun cardExit(slidePx: Int = 0): ExitTransition =
        fadeOut(tween(140, easing = Easing)) +
            if (slidePx != 0) slideOutVertically(tween(DurationShort, easing = Easing)) { slidePx } else ExitTransition.None
}

/** A full-width section that expands and collapses vertically from its top edge. */
@Composable
fun ExpandableSection(expanded: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier.fillMaxWidth(),
        enter = Motion.expandEnter,
        exit = Motion.expandExit,
    ) {
        Column(content = content)
    }
}
