package com.riedel.aeroglobe.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riedel.aeroglobe.AppContainer
import com.riedel.aeroglobe.BuildConfig
import com.riedel.aeroglobe.data.backup.TravelBackup
import com.riedel.aeroglobe.settings.HomeTab
import com.riedel.aeroglobe.settings.ThemeMode

/**
 * Settings: how the app looks, where it starts, and how to get the data out.
 *
 * Both preferences were already wired up and persisted before this screen existed —
 * MainActivity reads the theme and AeroGlobeRoot reads the home tab — so everything
 * here is a control over state that already works.
 */
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settings = container.settings,
            places = container.places,
            // The application context's resolver: the ViewModel outlives the
            // activity across a rotation, and holding the activity's would leak it.
            resolver = context.applicationContext.contentResolver,
        ),
    )

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val homeTab by viewModel.homeTab.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TravelBackup.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        Section("Appearance", "Dark mode follows the system unless you pick one.") {
            ChoiceRow(
                options = ThemeMode.entries,
                selected = themeMode,
                label = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                },
                onSelect = viewModel::setThemeMode,
            )
        }

        Section(
            "Opening screen",
            // Spelling out what Smart does, because it is the default and it is the
            // one option whose behaviour cannot be guessed from its name.
            "Smart shows your flight while one is tracked, and the map when none is.",
        ) {
            ChoiceRow(
                options = HomeTab.entries,
                selected = homeTab,
                label = { tab ->
                    when (tab) {
                        HomeTab.AUTO -> "Smart"
                        HomeTab.MAP -> "Map"
                        HomeTab.FLIGHTS -> "Flight"
                    }
                },
                onSelect = viewModel::setHomeTab,
            )
        }

        Section(
            "Your travel data",
            "Everything you have marked, as a JSON file you can keep anywhere. " +
                "Importing merges: an entry only wins if it is newer than what is " +
                "already on this phone, and nothing gets deleted.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                    enabled = transfer !is TransferState.Busy,
                ) { Text("Export") }
                OutlinedButton(
                    // Not filtered to application/json: providers hand .json files
                    // over as octet-stream often enough that a strict filter hides
                    // the file the user is looking straight at. Anything that is not
                    // a backup is rejected by TravelBackup.decode with a reason.
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = transfer !is TransferState.Busy,
                ) { Text("Import") }
            }

            when (val state = transfer) {
                TransferState.Idle -> Unit
                TransferState.Busy -> LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                is TransferState.Done -> TransferResult(state.message, isError = false, viewModel::dismissTransfer)
                is TransferState.Failed -> TransferResult(state.message, isError = true, viewModel::dismissTransfer)
            }
        }

        Section("About", null) {
            Text(
                "AeroGlobe ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Borders from Natural Earth (public domain), drawn with MapLibre. " +
                    "The whole map is bundled in the app, so it works with no network " +
                    "and no account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    detail: String?,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/**
 * A three-way exclusive choice. Segmented buttons rather than a dropdown or radio
 * list: with three short options every choice and the current answer are visible
 * without a tap.
 */
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
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

@Composable
private fun TransferResult(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("OK") }
    }
}
