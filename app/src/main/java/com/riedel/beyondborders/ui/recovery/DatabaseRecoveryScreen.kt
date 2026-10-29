package com.riedel.beyondborders.ui.recovery

import com.riedel.beyondborders.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
    onWipe: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Saveable, not plain remember: this screen is the only route to the bytes, and
    // a theme or font-scale change recreates the Activity. Losing "Saved." would tell
    // someone their copy had not been written when it had.
    var saved by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    // Plain remember, not saveable: a save in flight cannot survive process death
    // anyway, since the launcher callback would be gone with it. All this guards
    // against is a second tap while the coroutine above is still running.
    var saving by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    var wipeFailed by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val error = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)
                        ?.use { DatabaseRecovery.exportTo(context, it) }
                        ?: return@withContext context.getString(R.string.recovery_open_failed)
                    null
                }
            } catch (t: Throwable) {
                t.message ?: t.javaClass.simpleName
            } finally {
                saving = false
            }
            saved = error == null
            saveError = error
        }
    }

    val hasData = DatabaseRecovery.hasData(context)

    Scaffold(modifier.fillMaxSize()) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.recovery_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.recovery_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.recovery_reassurance),
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
                enabled = hasData && !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.recovery_save_copy))
                }
            }
            if (!hasData) {
                Text(
                    stringResource(R.string.recovery_no_database),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                saved == true -> Text(
                    stringResource(R.string.recovery_saved),
                    style = MaterialTheme.typography.bodySmall,
                )
                saveError != null -> Text(
                    stringResource(R.string.recovery_save_failed, saveError.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (wipeFailed) {
                Text(
                    stringResource(R.string.recovery_wipe_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = { confirming = true },
                enabled = !saving && !wiping,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.recovery_delete_start_fresh))
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.recovery_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (saved == true) R.string.recovery_confirm_saved else R.string.recovery_confirm_unsaved,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        wiping = true
                        wipeFailed = false
                        scope.launch {
                            val ok = try {
                                onWipe()
                            } finally {
                                wiping = false
                            }
                            wipeFailed = !ok
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.recovery_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.recovery_keep)) }
            },
        )
    }
}
