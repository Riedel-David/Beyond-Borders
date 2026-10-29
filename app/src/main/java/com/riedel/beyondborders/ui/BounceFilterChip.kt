package com.riedel.beyondborders.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

/**
 * A [FilterChip] that dips and springs back on every tap.
 *
 * The status change already lands instantly, but with no message on screen after
 * a mark (see [com.riedel.beyondborders.ui.BeyondBordersRoot]) the tap itself needs
 * to visibly react, or a fast double-tap looks like it did nothing.
 */
@Composable
fun BounceFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    // For chips repeated down a list, where "Been" alone says nothing about which row.
    semanticsLabel: String? = null,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    FilterChip(
        selected = selected,
        onClick = {
            onClick()
            scope.launch {
                scale.snapTo(0.85f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        },
        // With a semanticsLabel the chip's merged node would read that description and then
        // the text again; clearing only the Text's own semantics leaves the chip's click,
        // selected state and role untouched and lets the label replace the text.
        label = {
            Text(label, modifier = if (semanticsLabel != null) Modifier.clearAndSetSemantics { } else Modifier)
        },
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(
                if (semanticsLabel != null) Modifier.semantics { contentDescription = semanticsLabel }
                else Modifier,
            ),
    )
}
