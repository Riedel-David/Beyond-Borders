package com.riedel.beyondborders.ui.visa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A button showing the current passport, opening a searchable list of every
 * built passport on tap. A [ChoiceRow]-style segmented row does not scale past a
 * handful of options; this is the picker for the other ~194.
 */
@Composable
fun PassportPickerButton(
    selected: String,
    names: Map<String, String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(names[selected] ?: passportAdjective(selected))
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
        )
    }
}

@Composable
private fun PassportPickerDialog(
    names: Map<String, String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val options = remember(names) { names.keys.sortedBy { names[it] ?: it } }
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
                Text("Choose a passport", style = MaterialTheme.typography.titleMedium)
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
                                RadioButton(selected = isSelected, onClick = { onSelect(code) })
                            },
                            modifier = Modifier.clickable { onSelect(code) },
                        )
                    }
                }
            }
        }
    }
}
