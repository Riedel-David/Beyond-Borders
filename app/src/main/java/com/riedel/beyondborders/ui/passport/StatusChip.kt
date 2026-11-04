package com.riedel.beyondborders.ui.passport

import androidx.annotation.StringRes
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.settings.MapSlot
import com.riedel.beyondborders.ui.theme.LocalMapPalette

/** What a Passport row's status menu (and the row's TalkBack actions) offer: the three statuses. */
internal enum class StatusAction { BEEN, LIVED, WANT }

/** The status an entry sets. */
internal fun StatusAction.status(): PlaceStatus = when (this) {
    StatusAction.BEEN -> PlaceStatus.VISITED
    StatusAction.LIVED -> PlaceStatus.LIVED
    StatusAction.WANT -> PlaceStatus.WISHLIST
}

/**
 * The status to write for [action] on a row currently showing [current]. Absolute, never
 * relative to what is stored: picking the marked entry again writes NONE (that is the only
 * way to clear), anything else writes exactly that entry's status. Only the row's own
 * status changes.
 */
internal fun statusTargetFor(action: StatusAction, current: PlaceStatus): PlaceStatus =
    if (action.status() == current) PlaceStatus.NONE else action.status()

/** Whether [action]'s entry is the row's current status (highlighted). */
internal fun isCurrent(action: StatusAction, current: PlaceStatus): Boolean = action.status() == current

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

/**
 * Label colour for a chip filled with [fillArgb]: black or white, whichever contrasts more.
 * Pure black rather than the theme's near-black because teal at 0xFF0F8A7E only reaches
 * 4.38:1 against near-black, under the 4.5:1 AA line.
 */
internal fun textColorForFill(fillArgb: Int): Int {
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    return if (contrastRatio(fillArgb, black) >= contrastRatio(fillArgb, white)) black else white
}

@StringRes
private fun actionLabelRes(action: StatusAction): Int = when (action) {
    StatusAction.BEEN -> R.string.passport_chip_been
    StatusAction.LIVED -> R.string.passport_chip_lived
    StatusAction.WANT -> R.string.passport_chip_want
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
 * the highlight is frozen at open time so it does not jump
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
    val fillColor: Color? = when (statusSlot(status)) {
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
            // Pill and menu share this box so the menu anchors to the visible pill, not to
            // the taller 48dp touch target around it.
            Box {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 32.dp)
                        .clip(shape)
                        .background(fillColor ?: Color.Transparent)
                        .then(if (fillColor == null) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
                        .padding(PaddingValues(horizontal = 12.dp, vertical = 4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Never ellipsized: the pill grows with the label and the name beside it yields.
                    Text(
                        text = label ?: "+",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (fillColor != null) Color(textColorForFill(fillColor.toArgb()))
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
                    // Three entries, no icon slot: the selected one is tinted and bold, and tapping
                    // it again clears the status, so there is no separate Clear entry.
                    for (action in StatusAction.entries) {
                        val current = isCurrent(action, menuStatus)
                        val hue = when (statusSlot(action.status())) {
                            MapSlot.VISITED -> palette.visited
                            MapSlot.LIVED -> palette.lived
                            MapSlot.WISHLIST -> palette.wishlist
                            null -> Color.Transparent
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(actionLabelRes(action)),
                                    fontWeight = if (current) FontWeight.Bold else null,
                                    color = if (current) Color(textColorForFill(hue.toArgb())) else Color.Unspecified,
                                )
                            },
                            modifier = Modifier
                                .background(if (current) hue else Color.Transparent)
                                .semantics {
                                    selected = current
                                    role = Role.RadioButton
                                },
                            onClick = { onAction(action) },
                        )
                    }
                }
            }
        }
    }
}
