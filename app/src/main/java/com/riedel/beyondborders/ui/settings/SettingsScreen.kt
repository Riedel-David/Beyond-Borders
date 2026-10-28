package com.riedel.beyondborders.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.riedel.beyondborders.AppContainer
import com.riedel.beyondborders.BuildConfig
import com.riedel.beyondborders.data.backup.BackupMerge
import com.riedel.beyondborders.data.backup.TravelBackup
import com.riedel.beyondborders.settings.Denominator
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.settings.MapHue
import com.riedel.beyondborders.settings.MapSlot
import com.riedel.beyondborders.settings.ThemeMode
import com.riedel.beyondborders.ui.theme.LocalIsDarkTheme
import com.riedel.beyondborders.ui.visa.PassportPickerButton

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
            visa = container.visa,
            // The application context's resolver: the ViewModel outlives the
            // activity across a rotation, and holding the activity's would leak it.
            resolver = context.applicationContext.contentResolver,
            snapshot = container.cloudSnapshot,
        ),
    )

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val mapColors by viewModel.mapColors.collectAsStateWithLifecycle()
    val mapBase by viewModel.mapBase.collectAsStateWithLifecycle()
    val denominator by viewModel.denominator.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val nationality by viewModel.nationality.collectAsStateWithLifecycle()
    val secondNationality by viewModel.secondNationality.collectAsStateWithLifecycle()
    val passportNames by viewModel.passportNames.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val snapshotAvailable by viewModel.snapshotAvailable.collectAsStateWithLifecycle()
    val reset by viewModel.reset.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TravelBackup.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::previewImport) }

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
            "Passport",
            "Which passport's visa answers the map and passport tab show.",
        ) {
            PassportPickerButton(
                selected = nationality,
                names = passportNames,
                onSelect = viewModel::setNationality,
            )
            Spacer(Modifier.height(8.dp))
            if (secondNationality == null) {
                PassportPickerButton(
                    selected = null,
                    names = passportNames,
                    onSelect = viewModel::setSecondNationality,
                    dialogTitle = "Second passport",
                    placeholder = "Add second passport",
                    exclude = setOf(nationality),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Dual nationals see whichever of the two passports gives the easier answer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                secondNationality?.let { secondPassport ->
                    Text(
                        "Second passport",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    PassportPickerButton(
                        selected = secondPassport,
                        names = passportNames,
                        onSelect = viewModel::setSecondNationality,
                        dialogTitle = "Second passport",
                        exclude = setOf(nationality),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.setSecondNationality(null) }) {
                        Text("Remove second passport")
                    }
                }
            }
        }

        Section("Haptics", "A short vibration when you mark or clear a place.") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vibrate on marking", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = hapticsEnabled, onCheckedChange = viewModel::setHapticsEnabled)
            }
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
            "Counting places",
            "\"How many countries have you been to\" has no one answer. Pick which " +
                "one the passport tab counts against.",
        ) {
            ChoiceRow(
                options = Denominator.entries,
                selected = denominator,
                label = { d ->
                    when (d) {
                        Denominator.ALL -> "All 252"
                        Denominator.UN_MEMBER -> "195 UN"
                        Denominator.INHABITED -> "Inhabited"
                    }
                },
                onSelect = viewModel::setDenominator,
            )
        }

        Section(
            "Map background",
            "Ocean and unmarked land. A small, checked list rather than a colour " +
                "wheel, so it stays readable against every status colour above.",
        ) {
            BaseRow(mapBase, viewModel::setMapBase)
        }

        Section("Your travel data", null) {
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
            Text(
                "As a JSON file you can keep anywhere. Importing merges it in: an " +
                    "entry only wins if it is newer than what's already here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (snapshotAvailable) {
                OutlinedButton(
                    onClick = viewModel::previewSnapshotRestore,
                    enabled = transfer !is TransferState.Busy,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Restore from Android's backup") }
                Text(
                    "Android's backup left a snapshot of your history on this " +
                        "phone. Restoring merges it in, exactly like importing a file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            when (val state = transfer) {
                TransferState.Idle -> Unit
                TransferState.Busy -> LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                is TransferState.Done -> TransferResult(state.message, isError = false, viewModel::dismissTransfer)
                is TransferState.Failed -> TransferResult(state.message, isError = true, viewModel::dismissTransfer)
            }

            // Picking a file only plans; nothing is written until this is confirmed.
            // Dismissing — including the back gesture, which onDismissRequest also
            // catches — leaves the database exactly as it was.
            importPreview?.let { preview ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissImportPreview,
                    title = { Text("Import this file?") },
                    text = { ImportPreviewSummary(preview.plan, preview.sampleNames) },
                    confirmButton = {
                        TextButton(onClick = viewModel::confirmImportPreview) { Text("Import") }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissImportPreview) { Text("Cancel") }
                    },
                )
            }
        }

        Section(
            "Danger zone",
            "Clears every place you've marked as been, lived in or want to visit. " +
                "Cannot be undone — export a backup first if there's any doubt.",
            // Restrained rather than a full errorContainer fill, which would shout on
            // a screen visited for ordinary reasons too: a container tone distinct
            // from the neutral sections, plus a border/title/button that are
            // unambiguously error-coloured.
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleColor = MaterialTheme.colorScheme.error,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            var showConfirm by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showConfirm = true },
                enabled = reset !is TransferState.Busy,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete everything") }

            when (val state = reset) {
                TransferState.Idle -> Unit
                TransferState.Busy -> LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                is TransferState.Done -> TransferResult(state.message, isError = false, viewModel::dismissReset)
                is TransferState.Failed -> TransferResult(state.message, isError = true, viewModel::dismissReset)
            }

            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = { Text("Delete everything?") },
                    text = {
                        Text(
                            "Every place you've marked, your passports and your display settings " +
                                "are cleared from this phone, along with the snapshot " +
                                "Android's backup keeps. " +
                                "Google may still hold an earlier backup until " +
                                "Android next runs one — Android's backup settings " +
                                "can delete that now."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirm = false
                            viewModel.deleteEverything()
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                    },
                )
            }
        }

        Section("About", null) {
            Text(
                "BeyondBorders ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Open source (GPL-3.0)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            RepoLink()
            ReportProblemLink()
            Text(
                "An independent app, not affiliated with or representing any government. " +
                    "Visa data is a snapshot and can be out of date: confirm entry rules " +
                    "with the official government source before you travel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Borders from Natural Earth (public domain), drawn with MapLibre. " +
                    "The whole map is bundled in the app, so it works with no network " +
                    "and no account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            var showLicences by remember { mutableStateOf(false) }
            TextButton(onClick = { showLicences = true }) { Text("Open-source licences") }
            if (showLicences) LicencesDialog(onDismiss = { showLicences = false })
            Text(
                "Android's own Auto Backup carries a snapshot of your history to " +
                    "your Google account — the system makes that copy, the app " +
                    "never uploads anything itself. A reinstalled or new phone " +
                    "starts empty: nothing is put back until you tap Restore. The " +
                    "database, your settings and a per-install id stay out of the " +
                    "backup entirely. Switch it off in Android's backup settings " +
                    "if you would rather it did not.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    detail: String?,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
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

/**
 * The three background choices, each shown as its own ocean/land pair rather than
 * one dot, since a single colour could not stand in for two.
 */
@Composable
private fun BaseRow(selected: MapBase, onSelect: (MapBase) -> Unit) {
    val dark = LocalIsDarkTheme.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MapBase.entries.forEach { base ->
            BaseSwatch(
                ocean = Color(base.oceanArgb(dark)),
                land = Color(base.landArgb(dark)),
                isSelected = base == selected,
                name = base.displayName,
                onClick = { onSelect(base) },
            )
        }
    }
}

@Composable
private fun BaseSwatch(ocean: Color, land: Color, isSelected: Boolean, name: String, onClick: () -> Unit) {
    Column(
        Modifier
            .selectable(selected = isSelected, onClick = onClick)
            .semantics { contentDescription = name }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(
                    Modifier
                        .size(40.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                )
            }
            Row(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Box(Modifier.weight(1f).fillMaxSize().background(ocean))
                Box(Modifier.weight(1f).fillMaxSize().background(land))
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Display names, kept here with the other user-facing strings rather than on the
 * enum, for the same reason as [MapHue.displayName].
 */
private val MapBase.displayName: String
    get() = when (this) {
        MapBase.DEFAULT -> "Default"
        MapBase.SLATE -> "Slate"
        MapBase.SAND -> "Sand"
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

/**
 * The import confirmation's counts, as three label/value rows rather than one
 * sentence: same numbers, same source ([plan]), but a row scans where a sentence
 * has to be read start to end.
 */
@Composable
private fun ImportPreviewSummary(plan: BackupMerge.Plan, sampleNames: List<String>) {
    Column {
        PreviewCountRow("Added", plan.added)
        PreviewCountRow("Updated", plan.updated)
        // "Skipped" here is [plan.unchanged]: entries the file also has, but not
        // newer than what is already on this phone — the plan is built against
        // the rows as they stand right now, so this is also why a "nothing to
        // change" import can still happen.
        PreviewCountRow("Skipped (already up to date)", plan.unchanged)
        importPreviewSampleLine(plan, sampleNames)?.let { sample ->
            Text(
                sample,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        importPreviewCaveat(plan)?.let { caveat ->
            Text(
                caveat,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun PreviewCountRow(label: String, value: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
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

/**
 * Which places the added/updated counts actually mean, since a count alone does
 * not say *which* rows would be overwritten — the one thing worth knowing before
 * confirming an import. [sampleNames] is already capped and resolved to names by
 * the view model; this only decides whether there is a "and N more" tail.
 */
internal fun importPreviewSampleLine(plan: BackupMerge.Plan, sampleNames: List<String>): String? {
    val changed = plan.added + plan.updated
    if (changed == 0 || sampleNames.isEmpty()) return null
    val more = changed - sampleNames.size
    return buildString {
        append("e.g. ")
        append(sampleNames.joinToString(", "))
        if (more > 0) {
            append(", and ")
            append(more)
            append(" more")
        }
        append(".")
    }
}

/**
 * The nuances [ImportPreviewSummary]'s three rows cannot carry: unrecognised place
 * codes, statuses this build does not know, or rows that could not be read at all.
 * Rare in practice — a file from this app's own export never has any of them — but
 * dropping them silently would hide the reason a file did not import in full.
 */
internal fun importPreviewCaveat(plan: BackupMerge.Plan): String? {
    val issues = buildList {
        if (plan.unknownCodes.isNotEmpty()) {
            add(
                "${plan.unknownCodes.size} place " +
                    (if (plan.unknownCodes.size == 1) "code" else "codes") +
                    " not recognised by this build",
            )
        }
        if (plan.unknownStatuses.isNotEmpty()) {
            add(
                "${plan.unknownStatuses.size} " +
                    (if (plan.unknownStatuses.size == 1) "entry" else "entries") +
                    " with a status this build doesn't understand",
            )
        }
        if (plan.unreadableRows > 0) {
            add(
                "${plan.unreadableRows} " +
                    (if (plan.unreadableRows == 1) "row" else "rows") +
                    " could not be read",
            )
        }
    }
    if (issues.isEmpty()) return null
    return issues.joinToString(separator = "; ") { it }
        .replaceFirstChar { it.uppercase() } + "."
}
