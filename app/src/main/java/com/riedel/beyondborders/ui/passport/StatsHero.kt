package com.riedel.beyondborders.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.ui.places.TravelStats

/** One-line tweakable: the rings are drawn at these sizes regardless of how the rest
 * of the screen reflows, so a phone-in-hand adjustment is a single number here. */
private val RingDiameter = 168.dp
private val RingStrokeWidth = 14.dp
private val SmallRingDiameter = 56.dp
private val SmallRingStrokeWidth = 7.dp

/**
 * The top of the Stats screen: the share of the world visited as a ring, the
 * traveller rank earned by the same count, and how many badges are unlocked.
 */
@Composable
internal fun StatsHero(
    stats: TravelStats,
    badgesUnlocked: Int,
    badgesTotal: Int,
    modifier: Modifier = Modifier,
) {
    val rank = rankFor(stats.been)
    val container = MaterialTheme.colorScheme.surfaceVariant
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surface
    val progress = MaterialTheme.colorScheme.primary

    Column(
        modifier
            // One sentence for TalkBack rather than rings that are colour-only:
            // clearAndSetSemantics so the Canvases underneath aren't also announced.
            .clearAndSetSemantics {
                contentDescription =
                    "${stats.percent} percent of places visited, ${stats.been} of ${stats.total}. " +
                    "Rank ${rank.title}. ${rank.progressLine.trimEnd('.')}. " +
                    "$badgesUnlocked of $badgesTotal badges unlocked."
            }
            .background(container, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(RingDiameter), contentAlignment = Alignment.Center) {
            Ring(RingDiameter, RingStrokeWidth, stats.percent / 100f, track, progress)
            // Sized from dp, not sp: the ring interior is fixed, so the number inside it
            // must not grow with the user's font scale. The count sits below instead.
            Text(
                "${stats.percent}%",
                style = MaterialTheme.typography.displaySmall,
                fontSize = with(LocalDensity.current) { 36.dp.toSp() },
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
            )
        }
        Text(
            "${stats.been} of ${stats.total} places",
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            rank.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            rank.progressLine,
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fraction = if (badgesTotal == 0) 0f else badgesUnlocked.toFloat() / badgesTotal
            Ring(SmallRingDiameter, SmallRingStrokeWidth, fraction, track, progress)
            Text(
                "$badgesUnlocked of $badgesTotal badges unlocked",
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}

/** A full track plus a [fraction]-sweep arc starting at twelve o'clock. */
@Composable
private fun Ring(diameter: Dp, strokeWidth: Dp, fraction: Float, track: Color, progress: Color) {
    Canvas(Modifier.size(diameter).fillMaxSize()) {
        val strokePx = strokeWidth.toPx()
        val d = size.minDimension - strokePx
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val arcSize = Size(d, d)
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        drawArc(track, -90f, 360f, false, topLeft, arcSize, style = stroke)
        if (fraction > 0f) drawArc(progress, -90f, 360f * fraction, false, topLeft, arcSize, style = stroke)
    }
}
