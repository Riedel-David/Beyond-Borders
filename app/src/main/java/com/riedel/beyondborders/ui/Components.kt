package com.riedel.beyondborders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R

// The controls and dialogs the app repeats, in one place so that a row means the same
// thing wherever it appears: one tap target per row carrying the right semantic role,
// 48 dp minimum, the theme's shapes rather than numbers typed at each call site.

/**
 * A labelled on/off row. The whole row is the toggle target (role Switch) and the
 * [Switch] inside is display only, so a screen reader sees one control rather than a
 * label plus a second, separately focusable switch.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A list row that opens something: a dialog, a picker, a link. The optional [value] is
 * the current answer, shown under the title, so the row doubles as a read-out.
 * Dividers come from the caller.
 */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = if (value != null) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The same glyph as ExpandChevron, which rotates; this one points at the next screen.
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * An exclusive choice among a few short options. Segmented buttons rather than a
 * dropdown or radio list: with three options every choice and the current answer are
 * visible without a tap. Past that, use [ChoiceDialog].
 */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label(option)) },
            )
        }
    }
}

/**
 * One radio row. The row is the single tap target and carries the radio role; the
 * [RadioButton] is display only, so TalkBack does not see two overlapping targets for
 * one choice.
 */
@Composable
fun RadioListItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        // Transparent so rows take the surface they sit on (dialogs are not the screen surface).
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * A dialog of exclusive options with the current one marked. Picking closes it via
 * [onSelect]; the caller decides whether picking the current value is a no-op.
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    RadioListItem(
                        label = label(option),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** An explanatory dialog with a title, a body and a single OK. */
@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_button_ok)) } },
    )
}

/**
 * An outcome message under a control. A polite live region, so a screen reader
 * announces it when it appears. With [onDismiss] it carries an OK button; without, it
 * is expected to clear itself.
 */
@Composable
fun FeedbackText(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onDismiss: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            message,
            style = style,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (onDismiss != null) {
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.info_button_ok))
            }
        }
    }
}
