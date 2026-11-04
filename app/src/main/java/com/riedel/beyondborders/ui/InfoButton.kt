package com.riedel.beyondborders.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riedel.beyondborders.R

/**
 * An info button that opens an AlertDialog with a title and body text.
 *
 * Used to move explanatory text out of the main UI while keeping it accessible.
 */
@Composable
fun InfoButton(
    contentDescription: String,
    dialogTitle: String,
    dialogBody: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = contentDescription,
            // Muted: the default onSurface tint made this the brightest thing on screen.
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = dialogBody,
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.info_button_ok))
                }
            },
        )
    }
}
