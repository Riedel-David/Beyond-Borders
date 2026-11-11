package com.riedel.beyondborders.ui.passport

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
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
import com.riedel.beyondborders.ui.InfoButton
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
    onShareFailed: () -> Unit,
) {
    // By id, so the sheet follows the live list rather than a stale copy.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val onOpen: (Achievement) -> Unit = { openId = it.id }
    achievements.firstOrNull { it.id == openId }?.let { badge ->
        BadgeSheet(badge, placesVisited, onDismiss = { openId = null }, onShareFailed = onShareFailed)
    }
    if (recent.isNotEmpty()) {
        HighlightLabel(stringResource(R.string.badge_recent))
        BadgeRows(recent, onOpen, newIds = recent.mapTo(HashSet()) { it.id })
    }
    if (nextUp.isNotEmpty()) {
        HighlightLabel(stringResource(R.string.badge_closest))
        BadgeRows(nextUp, onOpen)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.badge_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        InfoButton(
            contentDescription = stringResource(R.string.badge_section_info_label),
            dialogTitle = stringResource(R.string.badge_section_title),
            dialogBody = { Text(stringResource(R.string.badge_section_hint)) },
        )
    }

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
    // Plain rows rather than a lazy grid: the Stats screen already scrolls vertically,
    // and a lazy grid inside it would need a bounded height.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        medalRows(badges).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { MedalBadge(it, { onOpen(it) }, Modifier.weight(1f), isNew = it.id in newIds) }
                repeat(BadgeColumns - row.size) { Spacer(Modifier.weight(1f)) }
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
private fun BadgeSheet(badge: Achievement, placesVisited: Int, onDismiss: () -> Unit, onShareFailed: () -> Unit) {
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
            val config = LocalConfiguration.current
            val title = remember(badge.title, config) { badge.title.asString(context) }
            val description = remember(badge.description, config) { badge.description.asString(context) }
            val progress = remember(badge.progress, badge.target, badge.unlocked, config) { badgeProgressText(badge).asString(context) }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(
                progress,
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
                            // The sheet is a separate window above the screen's snackbar
                            // host, so close it first or the message sits under the scrim.
                            onShareFailed()
                            onDismiss()
                        }
                    } },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.badge_share)) }
            }
        }
    }
}
