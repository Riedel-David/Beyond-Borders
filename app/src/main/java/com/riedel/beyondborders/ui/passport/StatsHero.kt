package com.riedel.beyondborders.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.ui.map.rememberPlaceFlag
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.asString
import com.riedel.beyondborders.ui.places.TravelStats

/** One-line tweakable: the rings are drawn at these sizes regardless of how the rest
 * of the screen reflows, so a phone-in-hand adjustment is a single number here. */
private val RingDiameter = 168.dp
private val RingStrokeWidth = 14.dp
private val SmallRingDiameter = 56.dp
private val SmallRingStrokeWidth = 7.dp

/**
 * The top of the Stats screen: the share of the world visited as a ring, the
 * traveller rank (every place visited, whatever the Denominator, like the badges), and how many badges are unlocked.
 */
@Composable
internal fun StatsHero(
    stats: TravelStats,
    placesVisited: Int,
    badgesUnlocked: Int,
    badgesTotal: Int,
    modifier: Modifier = Modifier,
    flagCodes: List<String> = emptyList(),
) {
    val rank = rankFor(placesVisited)
    val rankTitle = rank.title.asString()
    val rankProgress = rank.progressLine.asString()
    val badgesLine = pluralStringResource(R.plurals.stats_hero_badges, badgesTotal, badgesUnlocked, badgesTotal)
    val description = stringResource(
        R.string.stats_hero_description,
        stats.percent, stats.been, stats.total, rankTitle, rankProgress, badgesLine,
    )
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant
    // The old track was the page colour, which read as white on the card. A tint of the
    // card's own text colour is visible on any palette, dynamic colour included.
    val track = lerp(container, onContainer, 0.22f)
    val progress = MaterialTheme.colorScheme.primary

    Column(
        modifier
            // One sentence for TalkBack rather than rings that are colour-only:
            // clearAndSetSemantics so the Canvases underneath aren't also announced.
            .clearAndSetSemantics {
                contentDescription = description
            }
            .background(container, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (flagCodes.isNotEmpty()) FlagStrip(flagCodes, Modifier.padding(bottom = 12.dp))
        Box(Modifier.size(RingDiameter), contentAlignment = Alignment.Center) {
            Ring(RingDiameter, RingStrokeWidth, stats.percent / 100f, track, progress)
            // Sized from dp, not sp: the ring interior is fixed, so the number inside it
            // must not grow with the user's font scale. The count sits below instead.
            Text(
                stringResource(R.string.stats_hero_percent, stats.percent),
                style = MaterialTheme.typography.displaySmall,
                fontSize = with(LocalDensity.current) { 36.dp.toSp() },
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
            )
        }
        Text(
            pluralStringResource(R.plurals.stats_hero_places, stats.total, stats.been, stats.total),
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            rankTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            rankProgress,
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
                badgesLine,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}

/** A full track plus a [fraction]-sweep arc starting at twelve o'clock. */
@Composable
private fun Ring(diameter: Dp, strokeWidth: Dp, fraction: Float, track: Color, progress: Color) {
    Canvas(Modifier.size(diameter)) {
        val strokePx = strokeWidth.toPx()
        val d = size.minDimension - strokePx
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val arcSize = Size(d, d)
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        drawArc(track, -90f, 360f, false, topLeft, arcSize, style = stroke)
        if (fraction > 0f) drawArc(progress, -90f, 360f * fraction.coerceAtMost(1f), false, topLeft, arcSize, style = stroke)
    }
}

private const val HeroFlagCount = 6

/**
 * Which flags dress the hero: lived places first, then visited ones, each group by code
 * so the strip does not reshuffle between visits. [hasFlag] says whether a bundled flag
 * file exists for a code (Northern Cyprus has a code but no flag); a place without one
 * would leave a permanent hole.
 */
internal fun heroFlagCodes(
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
    max: Int = HeroFlagCount,
    hasFlag: (String) -> Boolean = { true },
): List<String> =
    places.filter { statuses[it.code]?.isBeen == true && hasFlag(it.code) }
        .sortedWith(compareBy<Place> { statuses[it.code] != PlaceStatus.LIVED }.thenBy { it.code })
        .take(max)
        .map { it.code }

/** A row of small flags above the ring. Fixed-size frames, so a flag that is still decoding leaves no jump. */
@Composable
private fun FlagStrip(codes: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        codes.forEach { FlagChip(it) }
    }
}

@Composable
private fun FlagChip(code: String) {
    val flag = rememberPlaceFlag(code)
    val shape = RoundedCornerShape(4.dp)
    val frame = Modifier.width(40.dp).height(28.dp).clip(shape)
    if (flag == null) {
        Box(frame.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    } else {
        Image(
            bitmap = flag.bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = frame.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        )
    }
}
