package com.riedel.beyondborders.ui.settings

import android.icu.text.ListFormatter
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import com.riedel.beyondborders.ui.SwitchRow
import com.riedel.beyondborders.ui.NavRow
import com.riedel.beyondborders.ui.InfoDialog
import com.riedel.beyondborders.ui.FeedbackText
import com.riedel.beyondborders.ui.ChoiceRow
import com.riedel.beyondborders.ui.ChoiceDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riedel.beyondborders.data.advisory.AdvisoryOutcome
import com.riedel.beyondborders.data.visa.update.VisaCheckOutcome
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riedel.beyondborders.AppContainer
import com.riedel.beyondborders.BuildConfig
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.backup.BackupMerge
import com.riedel.beyondborders.data.backup.TravelBackup
import com.riedel.beyondborders.settings.Denominator
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.settings.MapHue
import com.riedel.beyondborders.settings.MapSlot
import com.riedel.beyondborders.settings.ThemeMode
import com.riedel.beyondborders.ui.UiText
import com.riedel.beyondborders.ui.asString
import com.riedel.beyondborders.ui.openPlayStorePage
import com.riedel.beyondborders.ui.places.localizedName
import com.riedel.beyondborders.ui.theme.LocalIsDarkTheme
import com.riedel.beyondborders.ui.visa.PassportPickerButton
import com.riedel.beyondborders.ui.visa.PassportPickerDialog
import com.riedel.beyondborders.widget.TravelWidgetProvider
import java.time.ZoneId

/**
 * Settings: how the app looks, and how to get the data out.
 *
 * The theme preference was already wired up and persisted before this screen
 * existed — MainActivity reads it — so this is a control over state that already
 * works.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    placeCount: Int,
    unMemberCount: Int,
    placeNames: Map<String, String>,
    passportNames: Map<String, String>,
    englishNames: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settings = container.settings,
            places = container.places,
            // The application context's resolver: the ViewModel outlives the
            // activity across a rotation, and holding the activity's would leak it.
            resolver = context.applicationContext.contentResolver,
            snapshot = container.cloudSnapshot,
            visaUpdates = container.visaUpdates,
            advisoryUpdates = container.advisoryUpdates,
            advisory = container.advisory,
        ),
    )

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val pureBlack by viewModel.pureBlack.collectAsStateWithLifecycle()
    val mapColors by viewModel.mapColors.collectAsStateWithLifecycle()
    val mapBase by viewModel.mapBase.collectAsStateWithLifecycle()
    val denominator by viewModel.denominator.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val nationality by viewModel.nationality.collectAsStateWithLifecycle()
    val secondNationality by viewModel.secondNationality.collectAsStateWithLifecycle()
    val partnerNationality by viewModel.partnerNationality.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val visaCheck by viewModel.visaCheck.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val visaAutoUpdate by viewModel.visaAutoUpdate.collectAsStateWithLifecycle()
    val visaLastCheckAt by viewModel.visaLastCheckAt.collectAsStateWithLifecycle()
    val advisoryAsOf by viewModel.advisoryAsOf.collectAsStateWithLifecycle()
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
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        Section(stringResource(R.string.settings_appearance_title), stringResource(R.string.settings_appearance_detail)) {
            ChoiceRow(
                options = ThemeMode.entries,
                selected = themeMode,
                label = { mode ->
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        },
                    )
                },
                onSelect = viewModel::setThemeMode,
            )

            if (LocalIsDarkTheme.current) {
                Spacer(Modifier.height(16.dp))
                SwitchRow(
                    label = stringResource(R.string.settings_pure_black),
                    detail = stringResource(R.string.settings_pure_black_detail),
                    checked = pureBlack,
                    onChange = viewModel::setPureBlack,
                )
            }
        }

        Section(
            stringResource(R.string.settings_passport_title),
            stringResource(R.string.settings_passport_detail),
        ) {
            var showNationalityPicker by remember { mutableStateOf(false) }
            NavRow(
                title = stringResource(R.string.settings_passport_title),
                value = nationality?.let { passportNames[it] ?: it } ?: stringResource(R.string.settings_passport_unset),
                onClick = { showNationalityPicker = true },
            )
            if (showNationalityPicker) {
                PassportPickerDialog(
                    names = passportNames,
                    englishNames = englishNames,
                    selected = nationality,
                    onSelect = {
                        viewModel.setNationality(it)
                        showNationalityPicker = false
                    },
                    onDismiss = { showNationalityPicker = false },
                    title = stringResource(R.string.picker_choose_passport),
                    exclude = emptySet(),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (secondNationality == null) {
                var showSecondPassportPicker by remember { mutableStateOf(false) }
                NavRow(
                    title = stringResource(R.string.settings_second_passport),
                    value = stringResource(R.string.settings_add_second_passport),
                    onClick = { showSecondPassportPicker = true },
                )
                if (showSecondPassportPicker) {
                    PassportPickerDialog(
                        names = passportNames,
                        englishNames = englishNames,
                        selected = null,
                        onSelect = {
                            viewModel.setSecondNationality(it)
                            showSecondPassportPicker = false
                        },
                        onDismiss = { showSecondPassportPicker = false },
                        title = stringResource(R.string.settings_second_passport),
                        exclude = setOfNotNull(nationality),
                    )
                }
                Text(
                    stringResource(R.string.settings_dual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                secondNationality?.let { secondPassport ->
                    var showSecondPassportPicker by remember { mutableStateOf(false) }
                    Spacer(Modifier.height(4.dp))
                    NavRow(
                        title = stringResource(R.string.settings_second_passport),
                        value = passportNames[secondPassport] ?: secondPassport,
                        onClick = { showSecondPassportPicker = true },
                    )
                    if (showSecondPassportPicker) {
                        PassportPickerDialog(
                            names = passportNames,
                            englishNames = englishNames,
                            selected = secondPassport,
                            onSelect = {
                                viewModel.setSecondNationality(it)
                                showSecondPassportPicker = false
                            },
                            onDismiss = { showSecondPassportPicker = false },
                            title = stringResource(R.string.settings_second_passport),
                            exclude = setOfNotNull(nationality),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.setSecondNationality(null) }) {
                        Text(stringResource(R.string.settings_remove_second_passport))
                    }
                }
            }
            // The travel partner's passport: a different question from the second passport
            // above (that one makes the answers better, this one compares with someone else's).
            Spacer(Modifier.height(12.dp))
            var showPartnerPicker by remember { mutableStateOf(false) }
            NavRow(
                title = stringResource(R.string.settings_partner_passport),
                value = partnerNationality?.let { passportNames[it] ?: it }
                    ?: stringResource(R.string.settings_add_partner_passport),
                onClick = { showPartnerPicker = true },
            )
            if (showPartnerPicker) {
                PassportPickerDialog(
                    names = passportNames,
                    englishNames = englishNames,
                    selected = partnerNationality,
                    onSelect = {
                        viewModel.setPartnerNationality(it)
                        showPartnerPicker = false
                    },
                    onDismiss = { showPartnerPicker = false },
                    title = stringResource(R.string.settings_partner_passport),
                    // Your own passports are not a travel partner's.
                    exclude = setOfNotNull(nationality, secondNationality),
                )
            }
            Text(
                stringResource(R.string.settings_partner_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            partnerNationality?.let {
                TextButton(onClick = { viewModel.setPartnerNationality(null) }) {
                    Text(stringResource(R.string.settings_remove_partner_passport))
                }
            }
        }

        Section(stringResource(R.string.settings_haptics_title), null) {
            SwitchRow(
                label = stringResource(R.string.settings_haptics_toggle),
                checked = hapticsEnabled,
                onChange = viewModel::setHapticsEnabled,
            )
        }

        Section(
            stringResource(R.string.settings_colours_title),
            // The swap is stated up front because it is the one thing about this
            // control that would otherwise look like a bug.
            stringResource(R.string.settings_colours_detail),
        ) {
            HueRow(stringResource(R.string.settings_status_been), mapColors.visited) { viewModel.setMapHue(MapSlot.VISITED, it) }
            HueRow(stringResource(R.string.settings_status_lived), mapColors.lived) { viewModel.setMapHue(MapSlot.LIVED, it) }
            HueRow(stringResource(R.string.settings_status_want), mapColors.wishlist) { viewModel.setMapHue(MapSlot.WISHLIST, it) }
            // Hidden at the default, where it would be a button that does nothing.
            if (mapColors != MapColors.DEFAULT) {
                TextButton(onClick = viewModel::resetMapColors) { Text(stringResource(R.string.settings_colours_reset)) }
            }
        }

        Section(stringResource(R.string.settings_language_title), null) {
            // Read at composition: setApplicationLocales recreates the activity, which
            // recomposes this with the new value.
            // MainActivity handles the locale change itself (no recreate, so no black
            // frame), which means nothing else would recompose this: depend on the
            // configuration so the shown choice follows it.
            LocalConfiguration.current
            val language = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
            val languageLabel = when (language) {
                AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                AppLanguage.ENGLISH -> "English"
                AppLanguage.GERMAN -> "Deutsch"
                AppLanguage.SPANISH -> "Español"
                AppLanguage.FRENCH -> "Français"
                AppLanguage.PORTUGUESE -> "Português"
                AppLanguage.UKRAINIAN -> "Українська"
                AppLanguage.RUSSIAN -> "Русский"
            }
            var showLanguageMenu by remember { mutableStateOf(false) }
            NavRow(
                title = stringResource(R.string.settings_language_title),
                value = languageLabel,
                onClick = { showLanguageMenu = true },
            )
            if (showLanguageMenu) {
                // Endonyms so each language names itself.
                ChoiceDialog(
                    title = stringResource(R.string.settings_language_title),
                    options = AppLanguage.entries,
                    selected = language,
                    label = { option ->
                        when (option) {
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                            AppLanguage.ENGLISH -> "English"
                            AppLanguage.GERMAN -> "Deutsch"
                            AppLanguage.SPANISH -> "Español"
                            AppLanguage.FRENCH -> "Français"
                            AppLanguage.PORTUGUESE -> "Português"
                            AppLanguage.UKRAINIAN -> "Українська"
                            AppLanguage.RUSSIAN -> "Русский"
                        }
                    },
                    onSelect = { option ->
                        showLanguageMenu = false
                        if (option != language || option == AppLanguage.PORTUGUESE) {
                            AppCompatDelegate.setApplicationLocales(
                                if (option == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
                                else LocaleListCompat.forLanguageTags(option.localeTag(
                                    AppLanguage.portugueseRegion(
                                        android.content.res.Resources.getSystem().configuration.locales
                                            .let { l -> (0 until l.size()).map { l[it].language to l[it].country } },
                                    ),
                                )),
                            )
                            TravelWidgetProvider.requestUpdate(context.applicationContext)
                        }
                    },
                    onDismiss = { showLanguageMenu = false },
                )
            }
        }

        Section(
            stringResource(R.string.settings_counting_title),
            stringResource(R.string.settings_counting_detail),
        ) {
            ChoiceRow(
                options = Denominator.entries,
                selected = denominator,
                label = { d ->
                    when (d) {
                        // The counts come from the shared registry, which may not have loaded
                        // yet: a bare label rather than "All 0".
                        Denominator.ALL ->
                            if (placeCount > 0) stringResource(R.string.settings_denominator_all, placeCount)
                            else stringResource(R.string.settings_denominator_all_bare)
                        Denominator.UN_MEMBER ->
                            if (unMemberCount > 0) stringResource(R.string.settings_denominator_un, unMemberCount)
                            else stringResource(R.string.settings_denominator_un_bare)
                        Denominator.INHABITED -> stringResource(R.string.settings_denominator_inhabited)
                    }
                },
                onSelect = viewModel::setDenominator,
            )
        }

        Section(
            stringResource(R.string.settings_background_title),
            stringResource(R.string.settings_background_detail),
        ) {
            BaseRow(mapBase, viewModel::setMapBase)
        }

        Section(stringResource(R.string.settings_visa_title), stringResource(R.string.settings_visa_detail)) {
            SwitchRow(
                label = stringResource(R.string.settings_visa_auto),
                checked = visaAutoUpdate,
                onChange = viewModel::setVisaAutoUpdate,
            )
            val visaLocale = LocalConfiguration.current.locales[0]
            val visaCheckedDate = formatLastBackup(visaLastCheckAt, ZoneId.systemDefault(), visaLocale)
            val advisoryAsOfDate = formatLastBackup(advisoryAsOf, ZoneId.systemDefault(), visaLocale)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (visaCheckedDate == null) stringResource(R.string.settings_visa_last_checked_never)
                    else stringResource(R.string.settings_visa_last_checked, visaCheckedDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // The switch above governs only the weekly job; a tap here always checks.
                TextButton(enabled = visaCheck !is VisaCheckState.Running, onClick = viewModel::checkVisaNow) {
                    if (visaCheck is VisaCheckState.Running) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_visa_checking))
                    } else {
                        Text(stringResource(R.string.settings_visa_check_now))
                    }
                }
            }
            // The date of the data in use, not of the last check: a check that found nothing newer
            // leaves it unchanged, and that is exactly what the user wants to be able to see.
            if (advisoryAsOfDate != null) {
                Text(
                    stringResource(R.string.settings_visa_advisory_as_of, advisoryAsOfDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            (visaCheck as? VisaCheckState.Done)?.let { done ->
                // A good result fades after a few seconds; a failure stays until the next try.
                LaunchedEffect(done) {
                    if (shouldAutoClearCheck(done)) {
                        delay(VISA_RESULT_VISIBLE_MS)
                        viewModel.clearVisaResult()
                    }
                }
                val failed = done.outcome is VisaCheckOutcome.Offline || done.outcome is VisaCheckOutcome.Unavailable
                FeedbackText(
                    message = stringResource(
                        when (done.outcome) {
                            is VisaCheckOutcome.UpToDate -> R.string.settings_visa_result_current
                            is VisaCheckOutcome.Updated -> R.string.settings_visa_result_updated
                            is VisaCheckOutcome.Offline -> R.string.settings_visa_result_offline
                            is VisaCheckOutcome.Unavailable -> R.string.settings_visa_result_unavailable
                        }
                    ),
                    isError = failed,
                    topPadding = 4.dp,
                    style = MaterialTheme.typography.bodySmall,
                )
                FeedbackText(
                    message = stringResource(advisoryResultText(done.advisory)),
                    isError = advisoryCheckFailed(done.advisory),
                    topPadding = 2.dp,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Section(stringResource(R.string.settings_data_title), null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                    enabled = transfer !is TransferState.Busy,
                ) { Text(stringResource(R.string.settings_export)) }
                OutlinedButton(
                    // Not filtered to application/json: providers hand .json files
                    // over as octet-stream often enough that a strict filter hides
                    // the file the user is looking straight at. Anything that is not
                    // a backup is rejected by TravelBackup.decode with a reason.
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = transfer !is TransferState.Busy,
                ) { Text(stringResource(R.string.settings_import)) }
            }
            Text(
                stringResource(R.string.settings_data_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            val locale = LocalConfiguration.current.locales[0]
            val lastBackupDate = formatLastBackup(lastBackupAt, ZoneId.systemDefault(), locale)
            Text(
                if (lastBackupDate == null) stringResource(R.string.settings_last_backup_never)
                else stringResource(R.string.settings_last_backup, lastBackupDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (snapshotAvailable) {
                OutlinedButton(
                    onClick = viewModel::previewSnapshotRestore,
                    enabled = transfer !is TransferState.Busy,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(stringResource(R.string.settings_restore_button)) }
                Text(
                    stringResource(R.string.settings_restore_detail),
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
                is TransferState.Done -> FeedbackText(state.message.asString(), isError = false, onDismiss = viewModel::dismissTransfer)
                is TransferState.Failed -> FeedbackText(state.message.asString(), isError = true, onDismiss = viewModel::dismissTransfer)
            }

            // Picking a file only plans; nothing is written until this is confirmed.
            // Dismissing — including the back gesture, which onDismissRequest also
            // catches — leaves the database exactly as it was.
            importPreview?.let { preview ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissImportPreview,
                    title = { Text(stringResource(R.string.settings_import_title)) },
                    text = { ImportPreviewSummary(preview.plan, preview.sampleCodes.map { localizedName(it, placeNames, passportNames) }) },
                    confirmButton = {
                        TextButton(onClick = viewModel::confirmImportPreview) {
                            Text(stringResource(R.string.settings_import))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissImportPreview) {
                            Text(stringResource(R.string.settings_cancel))
                        }
                    },
                )
            }
        }

        Section(
            stringResource(R.string.settings_danger_title),
            stringResource(R.string.settings_danger_detail),
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
            ) { Text(stringResource(R.string.settings_delete_everything)) }

            when (val state = reset) {
                TransferState.Idle -> Unit
                TransferState.Busy -> LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                is TransferState.Done -> FeedbackText(state.message.asString(), isError = false, onDismiss = viewModel::dismissReset)
                is TransferState.Failed -> FeedbackText(state.message.asString(), isError = true, onDismiss = viewModel::dismissReset)
            }

            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
                    text = {
                        Text(stringResource(R.string.settings_delete_confirm_body))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirm = false
                            viewModel.deleteEverything()
                        }) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
                    },
                )
            }
        }

        Section(stringResource(R.string.settings_about_title), null) {
            Text(
                stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_about_open_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Pulled left by the button's 12dp content padding so the text lines up with the rows below.
            RepoLink(Modifier.offset(x = (-12).dp))
            var showDataInfo by remember { mutableStateOf(false) }
            var showMapInfo by remember { mutableStateOf(false) }
            var showBackupInfo by remember { mutableStateOf(false) }
            var showLicences by remember { mutableStateOf(false) }
            NavRow(
                title = stringResource(R.string.settings_about_data_title),
                onClick = { showDataInfo = true },
            )
            HorizontalDivider()
            NavRow(
                title = stringResource(R.string.settings_about_map_title),
                onClick = { showMapInfo = true },
            )
            HorizontalDivider()
            NavRow(
                title = stringResource(R.string.settings_licences_button),
                onClick = { showLicences = true },
            )
            HorizontalDivider()
            NavRow(
                title = stringResource(R.string.settings_about_backup_title),
                onClick = { showBackupInfo = true },
            )
            HorizontalDivider()
            ReportProblemLink()
            HorizontalDivider()
            val playContext = LocalContext.current
            NavRow(
                title = stringResource(R.string.settings_rate_play),
                onClick = { openPlayStorePage(playContext) },
            )
            if (showDataInfo) {
                InfoDialog(
                    title = stringResource(R.string.settings_about_data_title),
                    body = stringResource(R.string.settings_about_disclaimer),
                    onDismiss = { showDataInfo = false },
                )
            }
            if (showMapInfo) {
                InfoDialog(
                    title = stringResource(R.string.settings_about_map_title),
                    body = stringResource(R.string.settings_about_map),
                    onDismiss = { showMapInfo = false },
                )
            }
            if (showBackupInfo) {
                InfoDialog(
                    title = stringResource(R.string.settings_about_backup_title),
                    body = stringResource(R.string.settings_about_backup),
                    onDismiss = { showBackupInfo = false },
                )
            }
            if (showLicences) LicencesDialog(onDismiss = { showLicences = false })
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
 * A one-line picker: the current choice with a caret, the rest in a menu. For options
 * whose labels can run long (German at a large font scale) where a segmented row
 * would wrap onto two lines.
 */
@Composable
private fun <T> DropdownRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            Text(label(selected), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        if (option != selected) onSelect(option)
                    },
                )
            }
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
                    name = stringResource(R.string.settings_swatch_description, label, stringResource(hue.displayName)),
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
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MapBase.entries.forEach { base ->
            BaseSwatch(
                ocean = Color(base.oceanArgb(dark)),
                land = Color(base.landArgb(dark)),
                isSelected = base == selected,
                name = stringResource(base.displayName),
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
private val MapBase.displayName: Int
    @StringRes get() = when (this) {
        MapBase.DEFAULT -> R.string.settings_base_default
        MapBase.SLATE -> R.string.settings_base_slate
        MapBase.SAND -> R.string.settings_base_sand
    }

@Composable
private fun Swatch(color: Color, isSelected: Boolean, name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
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
private val MapHue.displayName: Int
    @StringRes get() = when (this) {
        MapHue.AZURE -> R.string.settings_hue_azure
        MapHue.TEAL -> R.string.settings_hue_teal
        MapHue.GREEN -> R.string.settings_hue_green
        MapHue.ROSE -> R.string.settings_hue_rose
        MapHue.VIOLET -> R.string.settings_hue_violet
        MapHue.AMBER -> R.string.settings_hue_amber
    }

/**
 * The import confirmation's counts, as three label/value rows rather than one
 * sentence: same numbers, same source ([plan]), but a row scans where a sentence
 * has to be read start to end.
 */
@Composable
private fun ImportPreviewSummary(plan: BackupMerge.Plan, sampleNames: List<String>) {
    Column {
        PreviewCountRow(stringResource(R.string.settings_preview_added), plan.added)
        PreviewCountRow(stringResource(R.string.settings_preview_updated), plan.updated)
        if (plan.visitsAdded + plan.visitsUpdated > 0) {
            PreviewCountRow(
                stringResource(R.string.settings_preview_visits),
                plan.visitsAdded + plan.visitsUpdated,
            )
        }
        // "Skipped" here is [plan.unchanged]: entries the file also has, but not
        // newer than what is already on this phone — the plan is built against
        // the rows as they stand right now, so this is also why a "nothing to
        // change" import can still happen.
        PreviewCountRow(stringResource(R.string.settings_preview_skipped), plan.unchanged)
        importPreviewSampleLine(plan, sampleNames)?.let { sample ->
            Text(
                sample.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        importPreviewCaveat(plan).takeIf { it.isNotEmpty() }?.let { issues ->
            Text(
                caveatSentence(issues),
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

/**
 * Which places the added/updated counts actually mean, since a count alone does
 * not say *which* rows would be overwritten — the one thing worth knowing before
 * confirming an import. [sampleNames] is already capped and resolved to names by
 * the view model; this only decides whether there is a "and N more" tail.
 */
internal fun importPreviewSampleLine(plan: BackupMerge.Plan, sampleNames: List<String>): UiText? {
    val changed = plan.added + plan.updated
    if (changed == 0 || sampleNames.isEmpty()) return null
    val more = changed - sampleNames.size
    val names = sampleNames.joinToString(", ")
    return if (more > 0) {
        UiText.Res(R.string.settings_preview_sample_more, names, more)
    } else {
        UiText.Res(R.string.settings_preview_sample, names)
    }
}

/**
 * The nuances [ImportPreviewSummary]'s three rows cannot carry: unrecognised place
 * codes, statuses this build does not know, or rows that could not be read at all.
 * Rare in practice — a file from this app's own export never has any of them — but
 * dropping them silently would hide the reason a file did not import in full.
 *
 * Returns the individual issues, not one sentence: [caveatSentence] joins them, so
 * the join happens where the language is known.
 */
internal fun importPreviewCaveat(plan: BackupMerge.Plan): List<UiText> = buildList {
    val codes = plan.unknownCodes.size
    if (codes > 0) add(UiText.Plural(R.plurals.settings_caveat_codes, codes, codes))
    val statuses = plan.unknownStatuses.size
    if (statuses > 0) add(UiText.Plural(R.plurals.settings_caveat_statuses, statuses, statuses))
    val rows = plan.unreadableRows
    if (rows > 0) add(UiText.Plural(R.plurals.settings_caveat_unreadable, rows, rows))
}

@Composable
private fun caveatSentence(issues: List<UiText>): String {
    // The locale's own list wording ("A, B and C" / "A, B und C"); every issue starts with a
    // count, so there is no first letter to capitalise.
    val locale = LocalConfiguration.current.locales[0]
    val joined = ListFormatter.getInstance(locale).format(issues.map { it.asString() })
    return stringResource(R.string.settings_preview_caveat, joined)
}

/** How long a reassuring visa-check result stays up before it clears itself. */
internal const val VISA_RESULT_VISIBLE_MS = 5_000L

/**
 * Only "Up to date" fades: it is the answer nobody needs to keep reading. A failure or an
 * update stays until the next check, because the user may not have been looking.
 */
internal fun shouldAutoClearVisaResult(outcome: VisaCheckOutcome): Boolean = outcome is VisaCheckOutcome.UpToDate

/**
 * Check now covers both datasets, so the pair fades only when both reported "nothing to do".
 * "Not published yet" is as calm as "up to date" for advisories: there is nothing to act on, and
 * leaving it up would pin the line for good on an install the weekly job has not reached yet.
 */
internal fun shouldAutoClearCheck(done: VisaCheckState.Done): Boolean =
    shouldAutoClearVisaResult(done.outcome) &&
        (done.advisory == AdvisoryOutcome.UP_TO_DATE || done.advisory == AdvisoryOutcome.NOT_PUBLISHED)

/** Offline and rejected are both "the refresh did not happen" as far as the user can tell. */
internal fun advisoryCheckFailed(outcome: AdvisoryOutcome): Boolean =
    outcome == AdvisoryOutcome.OFFLINE || outcome == AdvisoryOutcome.REJECTED

@StringRes
internal fun advisoryResultText(outcome: AdvisoryOutcome): Int = when (outcome) {
    AdvisoryOutcome.UP_TO_DATE -> R.string.settings_advisory_result_current
    AdvisoryOutcome.NOT_PUBLISHED -> R.string.settings_advisory_result_not_published
    AdvisoryOutcome.UPDATED -> R.string.settings_advisory_result_updated
    AdvisoryOutcome.NEEDS_NEWER_APP -> R.string.settings_advisory_result_newer_app
    AdvisoryOutcome.OFFLINE, AdvisoryOutcome.REJECTED -> R.string.settings_advisory_result_failed
}
