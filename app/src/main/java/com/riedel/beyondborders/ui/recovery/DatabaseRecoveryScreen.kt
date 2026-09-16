package com.riedel.beyondborders.ui.recovery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.data.db.DatabaseRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Shown in place of the app when the database cannot be opened — see
 * [DatabaseRecovery] for when that happens and why it is not just fixed by wiping.
 *
 * The order of the two buttons is the whole point of the screen: a copy of the bytes
 * is the primary action, and deleting them is a text button behind a confirmation.
 * Nothing here runs on its own.
 */
@Composable
fun DatabaseRecoveryScreen(
    detail: String,
    onWipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<Boolean?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)
                        ?.use { DatabaseRecovery.exportTo(context, it) }
                        ?: return@withContext "Couldn't open the file you chose."
                    null
                } catch (t: Throwable) {
                    t.message ?: t.javaClass.simpleName
                }
            }
            saved = error == null
            saveError = error
        }
    }

    Surface(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Your travel data can't be opened",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "This copy of Beyond Borders doesn't know how to read the data already " +
                    "on your phone. That usually means an older version of the app is " +
                    "installed over a newer one's data.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Nothing has been deleted, and the app won't delete anything by itself. " +
                    "Installing the newer version again should open it as normal. If you " +
                    "can't, save a copy of the data first — it can be read back later, and " +
                    "it's the only copy that exists on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                // Verbatim, because it names the two versions involved, and that is the
                // one thing that makes this diagnosable once the phone is out of reach.
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Button(
                onClick = { saveLauncher.launch("beyondborders-data-${LocalDate.now()}.zip") },
                modifier = Modifier.fillMaxWidth(),
                enabled = DatabaseRecovery.hasData(context),
            ) {
                Text("Save a copy")
            }

            when {
                saved == true -> Text(
                    "Saved.",
                    style = MaterialTheme.typography.bodySmall,
                )
                saveError != null -> Text(
                    "Couldn't save: $saveError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = { confirming = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete the data and start fresh")
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete everything you've marked?") },
            text = {
                Text(
                    if (saved == true) {
                        "You saved a copy, so this can be read back later. Every place " +
                            "you've marked will be gone from the app itself."
                    } else {
                        "You haven't saved a copy. Every place you've ever marked will " +
                            "be gone for good, and there is no undo."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onWipe()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Keep it") }
            },
        )
    }
}
