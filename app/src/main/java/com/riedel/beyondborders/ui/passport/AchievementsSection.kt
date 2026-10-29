package com.riedel.beyondborders.ui.passport

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R

/** One distinct shape per group: the shape says what the badge is about, the hue only adds mood. */
@DrawableRes
private fun iconFor(group: AchievementGroup): Int = when (group) {
    AchievementGroup.COUNTS -> R.drawable.ic_badge_globe
    AchievementGroup.CONTINENTS -> R.drawable.ic_badge_layers
    AchievementGroup.REGIONS -> R.drawable.ic_badge_compass
    AchievementGroup.GROUPS -> R.drawable.ic_badge_grid
    AchievementGroup.DEPENDENCIES -> R.drawable.ic_badge_island
    AchievementGroup.NON_UN -> R.drawable.ic_badge_flag
    AchievementGroup.TINY -> R.drawable.ic_badge_pin
    AchievementGroup.GIANTS -> R.drawable.ic_badge_mountain
    AchievementGroup.SPECIAL -> R.drawable.ic_badge_star
    AchievementGroup.LIVED -> R.drawable.ic_badge_home
    AchievementGroup.WISHLIST -> R.drawable.ic_badge_heart
}

private val CircleSize = 40.dp
private val LockSize = 16.dp
private val Collapsible = setOf(AchievementGroup.REGIONS, AchievementGroup.GROUPS)

/**
 * Badges are derived from the current statuses, so unticking a place can take one
 * away again. Regions and named groupings are many and mostly locked, so each of
 * those two sits behind its own header and opens on demand.
 */
@Composable
internal fun AchievementsSection(
    achievements: List<Achievement>,
    expandedGroups: Set<AchievementGroup>,
    onToggleGroup: (AchievementGroup) -> Unit,
) {
    Text(
        "Achievements",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        "Badges follow your map as it is now: untick a place and a badge can go away again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )

    val byGroup = remember(achievements) { achievements.groupBy { it.group } }
    val progress = remember(achievements) { groupProgress(achievements) }
    AchievementGroup.entries.forEach { group ->
        val all = byGroup[group].orEmpty()
        if (all.isEmpty()) return@forEach
        val (done, total) = progress.getValue(group)
        val header = "${group.label} · $done of $total"
        if (group in Collapsible) {
            val expanded = group in expandedGroups
            CollapsibleHeader(header, expanded) { onToggleGroup(group) }
            // Earned badges stay visible under a collapsed header; opening it adds the
            // locked ones after them.
            val earned = all.filter { it.unlocked }
            Column(Modifier.animateContentSize()) {
                BadgeRows(if (expanded) earned + all.filter { !it.unlocked } else earned)
            }
        } else {
            Text(
                header,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            BadgeRows(all)
        }
    }
}

@Composable
private fun CollapsibleHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f)
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button, onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun BadgeRows(badges: List<Achievement>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { AchievementBadge(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Unlocked: the group's tint with its icon in a filled circle. Locked: the neutral
 * container with the same icon muted, a lock mark and a progress bar. Text keeps full
 * strength either way: dimming it is what made the locked cards unreadable before.
 */
@Composable
private fun AchievementBadge(achievement: Achievement, modifier: Modifier = Modifier) {
    val unlocked = achievement.unlocked
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val tone = badgeTone(achievement.group)
    val accent = Color(if (dark) tone.accentDark else tone.accentLight)
    val container = if (unlocked) Color(if (dark) tone.containerDark else tone.containerLight) else scheme.surfaceVariant
    val content = if (unlocked) Color(if (dark) InkDark else InkLight) else scheme.onSurfaceVariant
    val glyph = Color(if (dark) GlyphOnAccentDark else GlyphOnAccentLight)
    // One spoken line per badge: the visual state (colour, "x / y", progress bar) is
    // otherwise read as loose fragments, and the bar says nothing at all.
    val spoken = buildString {
        append(achievement.title).append(", ")
        append(if (unlocked) "unlocked" else "locked, ${achievement.progress} of ${achievement.target}")
        append(". ").append(achievement.description)
    }
    Column(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .background(container, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(CircleSize + LockSize / 4)) {
                Box(
                    Modifier
                        .size(CircleSize)
                        .background(if (unlocked) accent else scheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(iconFor(achievement.group)),
                        contentDescription = null,
                        tint = if (unlocked) glyph else content,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (!unlocked) {
                    Icon(
                        painterResource(R.drawable.ic_badge_lock),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(LockSize)
                            .background(container, CircleShape)
                            .padding(1.dp),
                    )
                }
            }
            Text(
                achievement.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = content,
                modifier = Modifier.weight(1f),
            )
        }
        Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = content)
        if (unlocked) {
            Text("Unlocked", style = MaterialTheme.typography.labelMedium, color = content)
        } else {
            Text(
                "${achievement.progress} / ${achievement.target}",
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(scheme.surface, RoundedCornerShape(3.dp))
            ) {
                val fraction = achievement.progress.toFloat() / achievement.target
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .background(accent, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}
