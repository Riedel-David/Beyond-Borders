package com.riedel.beyondborders.ui.visa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A button showing the current passport, opening a searchable list of every
 * built passport on tap. A [ChoiceRow]-style segmented row does not scale past a
 * handful of options; this is the picker for the other ~194.
 */
@Composable
fun PassportPickerButton(
    selected: String?,
    names: Map<String, String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = "Choose a passport",
    placeholder: String = "Choose a passport",
    exclude: Set<String> = emptySet(),
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(selected?.let { names[it] ?: passportAdjective(it) } ?: placeholder)
    }
    if (open) {
        PassportPickerDialog(
            names = names,
            selected = selected,
            onSelect = {
                onSelect(it)
                open = false
            },
            onDismiss = { open = false },
            title = dialogTitle,
            exclude = exclude,
        )
    }
}

@Composable
private fun PassportPickerDialog(
    names: Map<String, String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Choose a passport",
    exclude: Set<String> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    val options = remember(names, exclude) {
        names.keys.filterNot { it in exclude }.sortedBy { names[it] ?: it }
    }
    val filtered = remember(query, options, names) {
        if (query.isBlank()) {
            options
        } else {
            options.filter { code ->
                (names[code] ?: code).contains(query, ignoreCase = true) ||
                    code.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                )
                HorizontalDivider()
                LazyColumn {
                    items(filtered, key = { it }) { code ->
                        val isSelected = code == selected
                        ListItem(
                            headlineContent = { Text(names[code] ?: code) },
                            supportingContent = { Text(code) },
                            leadingContent = {
                                // Non-interactive: the row below is the single tap
                                // target and carries the radio role, so a second
                                // clickable here would give TalkBack two overlapping
                                // targets for one choice.
                                RadioButton(selected = isSelected, onClick = null)
                            },
                            modifier = Modifier.selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(code) },
                            ),
                        )
                    }
                }
            }
        }
    }
}
