package com.riedel.aeroglobe.ui

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
import kotlinx.coroutines.launch

/**
 * A [FilterChip] that dips and springs back on every tap.
 *
 * The status change already lands instantly, but with no message on screen after
 * a mark (see [com.riedel.aeroglobe.ui.AeroGlobeRoot]) the tap itself needs
 * to visibly react, or a fast double-tap looks like it did nothing.
 */
@Composable
fun BounceFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
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
        label = { Text(label) },
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}
