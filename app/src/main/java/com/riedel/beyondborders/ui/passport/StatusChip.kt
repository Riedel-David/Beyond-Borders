package com.riedel.beyondborders.ui.passport

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.settings.MapSlot
import com.riedel.beyondborders.ui.theme.LocalMapPalette

/** What a Passport row's status menu (and the row's TalkBack actions) offer. */
internal enum class StatusAction { BEEN, LIVED, WANT, CLEAR }

/** Menu entries for a row in state [current]: the three statuses, plus Clear only when one is set. */
internal fun statusMenuEntries(current: PlaceStatus): List<StatusAction> =
    if (current == PlaceStatus.NONE) listOf(StatusAction.BEEN, StatusAction.LIVED, StatusAction.WANT)
    else StatusAction.entries

/** The status an entry sets; null for Clear. */
internal fun StatusAction.status(): PlaceStatus? = when (this) {
    StatusAction.BEEN -> PlaceStatus.VISITED
    StatusAction.LIVED -> PlaceStatus.LIVED
    StatusAction.WANT -> PlaceStatus.WISHLIST
    StatusAction.CLEAR -> null
}

/**
 * The status to write for [action] on a row currently showing [current], or null when
 * it would change nothing. Absolute, never relative to what is stored: Clear is NONE.
 * Re-picking the marked entry is a no-op by design. Only the row's own status changes.
 */
internal fun statusTargetFor(action: StatusAction, current: PlaceStatus): PlaceStatus? {
    val target = action.status() ?: PlaceStatus.NONE
    return target.takeIf { it != current }
}

/** Whether [action]'s entry is the row's current status (gets the check mark). */
internal fun isCurrent(action: StatusAction, current: PlaceStatus): Boolean =
    action.status() != null && action.status() == current

/** Chip text for a status; null for NONE, which shows a "+" instead. */
@StringRes
internal fun statusLabelRes(status: PlaceStatus): Int? = when (status) {
    PlaceStatus.VISITED -> R.string.passport_chip_been
    PlaceStatus.LIVED -> R.string.passport_chip_lived
    PlaceStatus.WISHLIST -> R.string.passport_chip_want
    PlaceStatus.NONE -> null
}

/** Which user-chosen map hue colours the chip; null for NONE (muted). */
internal fun statusSlot(status: PlaceStatus): MapSlot? = when (status) {
    PlaceStatus.VISITED -> MapSlot.VISITED
    PlaceStatus.LIVED -> MapSlot.LIVED
    PlaceStatus.WISHLIST -> MapSlot.WISHLIST
    PlaceStatus.NONE -> null
}

@StringRes
private fun actionLabelRes(action: StatusAction): Int = when (action) {
    StatusAction.BEEN -> R.string.passport_chip_been
    StatusAction.LIVED -> R.string.passport_chip_lived
    StatusAction.WANT -> R.string.passport_chip_want
    StatusAction.CLEAR -> R.string.passport_clear
}

/** Fixed-size flag tile; a neutral tile when the code has no flag file or it is still decoding. */
@Composable
internal fun FlagThumb(code: String, modifier: Modifier = Modifier) {
    val thumb = rememberFlagThumb(code, 40, 28)
    Box(
        modifier
            .size(width = 40.dp, height = 28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
            )
        }
    }
}

/**
 * One status chip per row. Touch only: the row's own merged node carries the TalkBack
 * description, actions and "change status" click, so the chip adds no focus stop. The
 * menu is hosted here but its open state and the [menuStatus] it shows are the row's:
 * the entries are frozen at open time so Clear and the check mark do not pop in or out
 * while the dropdown is animating away after a pick.
 */
@Composable
internal fun StatusChip(
    status: PlaceStatus,
    open: Boolean,
    menuStatus: PlaceStatus,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (StatusAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMapPalette.current
    val accent: Color? = when (statusSlot(status)) {
        MapSlot.VISITED -> palette.visited
        MapSlot.LIVED -> palette.lived
        MapSlot.WISHLIST -> palette.wishlist
        null -> null
    }
    val label = statusLabelRes(status)?.let { stringResource(it) }
    Box(modifier) {
        // The 48dp target is the outer box; the visible pill inside is smaller.
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 32.dp)
                    .clip(shape)
                    .background(accent?.copy(alpha = 0.22f) ?: Color.Transparent)
                    .border(BorderStroke(1.dp, accent ?: MaterialTheme.colorScheme.outlineVariant), shape)
                    .padding(PaddingValues(horizontal = 12.dp, vertical = 4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // Never ellipsized: the pill grows with the label and the name beside it yields.
                Text(
                    text = label ?: "+",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (accent != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
            for (action in statusMenuEntries(menuStatus)) {
                val current = isCurrent(action, menuStatus)
                DropdownMenuItem(
                    text = { Text(stringResource(actionLabelRes(action))) },
                    leadingIcon = {
                        if (current) Icon(Icons.Filled.Check, contentDescription = null)
                        else Box(Modifier.size(24.dp))
                    },
                    modifier = Modifier.semantics { selected = current },
                    onClick = { onAction(action) },
                )
            }
        }
    }
}
