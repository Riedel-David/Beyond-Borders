package com.riedel.aeroglobe.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riedel.aeroglobe.AppContainer
import com.riedel.aeroglobe.BuildConfig
import com.riedel.aeroglobe.data.backup.TravelBackup
import com.riedel.aeroglobe.settings.MapColors
import com.riedel.aeroglobe.settings.MapHue
import com.riedel.aeroglobe.settings.MapSlot
import com.riedel.aeroglobe.settings.ThemeMode
import com.riedel.aeroglobe.ui.theme.LocalIsDarkTheme

/**
 * Settings: how the app looks, and how to get the data out.
 *
 * The theme preference was already wired up and persisted before this screen
 * existed — MainActivity reads it — so this is a control over state that already
 * works.
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
    val mapColors by viewModel.mapColors.collectAsStateWithLifecycle()
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
            "Map colours",
            // The swap is stated up front because it is the one thing about this
            // control that would otherwise look like a bug.
            "Picking a colour another status already uses swaps the two, so no two " +
                "statuses can end up the same.",
        ) {
            HueRow("Been", mapColors.visited) { viewModel.setMapHue(MapSlot.VISITED, it) }
            HueRow("Lived", mapColors.lived) { viewModel.setMapHue(MapSlot.LIVED, it) }
            HueRow("Want to", mapColors.wishlist) { viewModel.setMapHue(MapSlot.WISHLIST, it) }
            // Hidden at the default, where it would be a button that does nothing.
            if (mapColors != MapColors.DEFAULT) {
                TextButton(onClick = viewModel::resetMapColors) { Text("Reset to default colours") }
            }
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

/**
 * One status and the colours it can take.
 *
 * The label sits above the swatches rather than beside them so that all six get the
 * card's full width and a 44 dp touch target each. Beside the label they would be
 * 28 dp — small enough to mis-tap, which on a swapping control means undoing
 * someone else's colour by accident.
 */
@Composable
private fun HueRow(label: String, selected: MapHue, onSelect: (MapHue) -> Unit) {
    // Read here rather than passed in: the swatch has to show what the map will
    // actually look like right now, and each hue is a light/dark pair.
    val dark = LocalIsDarkTheme.current
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MapHue.entries.forEach { hue ->
                Swatch(
                    color = Color(hue.argb(dark)),
                    isSelected = hue == selected,
                    name = "$label: ${hue.displayName}",
                    onClick = { onSelect(hue) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, isSelected: Boolean, name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            // selectable rather than clickable: it announces the swatch as a radio
            // button and carries the selected state, which a colour alone cannot.
            .selectable(selected = isSelected, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        // A ring around the dot rather than a tick inside it: a tick needs a colour
        // of its own, and there is no single one that stays legible on all six.
        if (isSelected) {
            Box(
                Modifier
                    .size(36.dp)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            )
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                // A hairline so the palest swatch still has an edge on a pale card.
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}

/**
 * Display names, kept here with the other user-facing strings rather than on the
 * enum, which stays a data type the settings layer can persist without knowing
 * anything about words on a screen.
 */
private val MapHue.displayName: String
    get() = when (this) {
        MapHue.AZURE -> "Azure"
        MapHue.TEAL -> "Teal"
        MapHue.GREEN -> "Green"
        MapHue.ROSE -> "Rose"
        MapHue.VIOLET -> "Violet"
        MapHue.AMBER -> "Amber"
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
