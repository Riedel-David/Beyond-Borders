package com.riedel.beyondborders.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.asString

internal const val BadgeColumns = 3

// Fixed dp, not measured: the emblem is a drawing, only the title under it reflows.
private val EmblemSize = 68.dp
private val RingStroke = 4.dp
private val GapToDisc = 3.dp

/** The badges split into rows of [BadgeColumns]; the last row may be short. */
internal fun medalRows(badges: List<Achievement>): List<List<Achievement>> = badges.chunked(BadgeColumns)

/** Share of the ring that is filled, clamped to 0..1 whatever the progress overshoots to. */
internal fun ringFraction(badge: Achievement): Float =
    (badge.progress.toFloat() / badge.target.coerceAtLeast(1)).coerceIn(0f, 1f)

/** The number the badge asks for ("25" for 25 places, "5" for 5 continents), shown on the emblem. */
internal fun tierMark(badge: Achievement): String = badge.target.coerceAtLeast(0).toString()

/**
 * A medal: the group's icon in a disc, a ring around it that is the progress, the
 * target as a small tier mark and the title below. Earned discs take the group accent;
 * locked ones are grey and the ring fills in the accent as progress grows. The
 * description and the exact count live in the detail sheet behind the tap.
 *
 * The ring is drawn on a Canvas: it has to be looked at on a device.
 */
@Composable
internal fun MedalBadge(achievement: Achievement, onClick: () -> Unit, modifier: Modifier = Modifier, isNew: Boolean = false) {
    val unlocked = achievement.unlocked
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val tone = badgeTone(achievement.group)
    val accent = Color(if (dark) tone.accentDark else tone.accentLight)
    val tierFill = if (unlocked) Color(if (dark) tone.containerDark else tone.containerLight) else scheme.surfaceVariant
    val tierInk = if (unlocked) Color(if (dark) InkDark else InkLight) else scheme.onSurfaceVariant
    val glyph = Color(if (dark) GlyphOnAccentDark else GlyphOnAccentLight)
    val track = scheme.outlineVariant
    val fraction = ringFraction(achievement)

    val title = achievement.title.asString()
    val description = achievement.description.asString()
    val spoken = when {
        !unlocked -> stringResource(R.string.badge_spoken_locked, title, achievement.progress, achievement.target, description)
        isNew -> stringResource(R.string.badge_spoken_new, title, description)
        else -> stringResource(R.string.badge_spoken_unlocked, title, description)
    }
    Column(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.badge_show_details), onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(EmblemSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(EmblemSize)) {
                val stroke = RingStroke.toPx()
                val d = size.minDimension - stroke
                val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                val arc = Size(d, d)
                drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(stroke))
                if (fraction > 0f) {
                    drawArc(accent, -90f, 360f * fraction, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Box(
                Modifier
                    .size(EmblemSize - (RingStroke + GapToDisc) * 2)
                    .background(if (unlocked) accent else scheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconFor(achievement.group)),
                    contentDescription = null,
                    tint = if (unlocked) glyph else scheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                tierMark(achievement),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tierInk,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 2.dp)
                    .background(tierFill, RoundedCornerShape(50))
                    .border(1.dp, accent, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            if (isNew) {
                Text(
                    stringResource(R.string.badge_new),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = glyph,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .background(accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            // Two lines reserved so a row of medals stays level whatever the language.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
