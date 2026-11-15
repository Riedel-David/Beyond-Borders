package com.riedel.beyondborders.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R

/**
 * The one expand/collapse affordance of the app: a right-pointing chevron that turns down
 * when open. Every collapsible header uses this so they cannot drift apart.
 */
@Composable
fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        if (expanded) 90f else 0f,
        animationSpec = tween(Motion.DurationMedium, easing = Motion.Easing),
        label = "chevron",
    )
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.rotate(rotation),
    )
}

/** A full-width, 48 dp collapsible section header: title left, [ExpandChevron] right. */
@Composable
fun ExpandableHeader(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val stateText = stringResource(if (expanded) R.string.passport_state_expanded else R.string.passport_state_collapsed)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = stateText }
            .clickable(role = Role.Button, onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        ExpandChevron(expanded)
    }
}
