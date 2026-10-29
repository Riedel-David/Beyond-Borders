package com.riedel.beyondborders.ui.passport

import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.asString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One distinct shape per group: the shape says what the badge is about, the hue only adds mood. */
@DrawableRes
internal fun iconFor(group: AchievementGroup): Int = when (group) {
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
    nextUp: List<Achievement> = emptyList(),
    recent: List<Achievement> = emptyList(),
    placesVisited: Int = 0,
) {
    // By id, so the sheet follows the live list rather than a stale copy.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val onOpen: (Achievement) -> Unit = { openId = it.id }
    achievements.firstOrNull { it.id == openId }?.let { badge ->
        BadgeSheet(badge, placesVisited, onDismiss = { openId = null })
    }
    if (recent.isNotEmpty()) {
        HighlightLabel(stringResource(R.string.badge_recent))
        BadgeRows(recent, onOpen, newIds = recent.mapTo(HashSet()) { it.id })
    }
    if (nextUp.isNotEmpty()) {
        HighlightLabel(stringResource(R.string.badge_closest))
        BadgeRows(nextUp, onOpen)
    }
    Text(
        stringResource(R.string.badge_section_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        stringResource(R.string.badge_section_hint),
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
        val header = stringResource(R.string.badge_group_header, stringResource(group.label), done, total)
        // Nothing locked means nothing to reveal, so no dead chevron.
        if (group in Collapsible && all.any { !it.unlocked }) {
            val expanded = group in expandedGroups
            CollapsibleHeader(header, expanded) { onToggleGroup(group) }
            // Earned badges stay visible under a collapsed header; opening it adds the
            // locked ones after them.
            val earned = all.filter { it.unlocked }
            Column(Modifier.animateContentSize()) {
                BadgeRows(if (expanded) earned + all.filter { !it.unlocked } else earned, onOpen)
            }
        } else {
            Text(
                header,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            BadgeRows(all, onOpen)
        }
    }
}

@Composable
private fun HighlightLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun CollapsibleHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f)
    val expandedText = stringResource(R.string.passport_state_expanded)
    val collapsedText = stringResource(R.string.passport_state_collapsed)
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = if (expanded) expandedText else collapsedText }
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
private fun BadgeRows(badges: List<Achievement>, onOpen: (Achievement) -> Unit, newIds: Set<String> = emptySet()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { AchievementBadge(it, { onOpen(it) }, Modifier.weight(1f).fillMaxHeight(), isNew = it.id in newIds) }
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
private fun AchievementBadge(achievement: Achievement, onClick: () -> Unit, modifier: Modifier = Modifier, isNew: Boolean = false) {
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
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = content,
                modifier = Modifier.weight(1f),
            )
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = content)
        if (unlocked) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.badge_unlocked), style = MaterialTheme.typography.labelMedium, color = content)
                if (isNew) {
                    Text(
                        stringResource(R.string.badge_new),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = glyph,
                        modifier = Modifier
                            .background(accent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.badge_progress_slash, achievement.progress, achievement.target),
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(scheme.surface, RoundedCornerShape(3.dp))
            ) {
                val fraction = (achievement.progress.toFloat() / achievement.target.coerceAtLeast(1)).coerceIn(0f, 1f)
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

/**
 * The detail view behind a tap: the badge large, its progress, and, only once it is
 * earned, a Share button. A locked badge has nothing to brag about yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BadgeSheet(badge: Achievement, placesVisited: Int, onDismiss: () -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tone = badgeTone(badge.group)
    val accent = Color(if (dark) tone.accentDark else tone.accentLight)
    val glyph = Color(if (dark) GlyphOnAccentDark else GlyphOnAccentLight)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(88.dp).background(if (badge.unlocked) accent else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(iconFor(badge.group)),
                    contentDescription = null,
                    tint = if (badge.unlocked) glyph else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(badge.title.asString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(badge.description.asString(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(
                badgeProgressText(badge).asString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (badge.unlocked) {
                Button(
                    onClick = { scope.launch {
                        try {
                            shareBadge(context, badge, placesVisited)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.badge_share_failed), Toast.LENGTH_SHORT).show()
                        }
                    } },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.badge_share)) }
            }
        }
    }
}
