package com.riedel.beyondborders.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riedel.beyondborders.data.backup.BackupMerge
import com.riedel.beyondborders.data.backup.CloudSnapshot
import com.riedel.beyondborders.data.backup.TravelBackup
import com.riedel.beyondborders.data.place.PlaceRepository
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.VisaRepository
import com.riedel.beyondborders.settings.Denominator
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.settings.MapHue
import com.riedel.beyondborders.settings.MapSlot
import com.riedel.beyondborders.settings.SettingsStore
import com.riedel.beyondborders.settings.ThemeMode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the file-transfer row under the export and import buttons is showing. */
sealed interface TransferState {
    data object Idle : TransferState
    data object Busy : TransferState
    data class Done(val message: String) : TransferState
    data class Failed(val message: String) : TransferState
}

/**
 * The confirmation dialog between picking a file and actually importing it.
 *
 * [sampleNames] are resolved here, not in the screen, because [plan] only carries
 * place codes and "DE" means nothing to a user deciding whether to trust a file.
 */
data class ImportPreview(val plan: BackupMerge.Plan, val sampleNames: List<String>)

/**
 * Preferences, plus getting the travel history in and out of the app.
 *
 * The two live together because they are the same kind of thing from the user's
 * side: rarely touched, and about the app rather than about a place.
 *
 * File contents are read and written here rather than in the composable so the work
 * stays off the main thread and survives a rotation. Everything interesting about
 * the *format* lives in [TravelBackup] and [BackupMerge]; this class only moves
 * bytes and turns the result into a sentence.
 */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val places: PlaceRepository,
    private val visa: VisaRepository,
    private val resolver: ContentResolver,
    private val snapshot: CloudSnapshot,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val mapColors: StateFlow<MapColors> = settings.mapColors
    val mapBase: StateFlow<MapBase> = settings.mapBase
    val denominator: StateFlow<Denominator> = settings.denominator
    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
    // Settings is only reachable once welcome-gating has forced a real choice, so
    // this fallback is structurally unreachable — kept only so the type stays
    // non-null, not as a real default.
    val nationality: StateFlow<String> = settings.nationality
        .map { it ?: VisaRegistry.DEFAULT_PASSPORT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), VisaRegistry.DEFAULT_PASSPORT)
    val secondNationality: StateFlow<String?> = settings.secondNationality

    /** Every passport the picker can offer, with a real country name where one is
     * known. Loaded once: the set of built passports never changes at runtime. */
    private val _passportNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val passportNames: StateFlow<Map<String, String>> = _passportNames.asStateFlow()

    init {
        viewModelScope.launch {
            val codes = visa.availablePassports()
            _passportNames.value = codes.associateWith { code -> places.place(code)?.name ?: code }
        }
    }

    private val _transfer = MutableStateFlow<TransferState>(TransferState.Idle)
    val transfer: StateFlow<TransferState> = _transfer.asStateFlow()

    // Holds the picked file's text between the preview and the user's confirmation
    // so confirming applies exactly what was previewed rather than re-reading the
    // URI — the user could have swapped the file in the meantime. Bounded by
    // MAX_IMPORT_BYTES, so holding it is cheap. Cleared whenever the flow ends,
    // confirmed or not.
    private var pendingImportText: String? = null

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    // Separate from [transfer] even though it is the same shape: the two live under
    // different sections, and sharing one flow would surface a delete's result (or an
    // export's) under the wrong heading.
    private val _reset = MutableStateFlow<TransferState>(TransferState.Idle)
    val reset: StateFlow<TransferState> = _reset.asStateFlow()

    // Whether the snapshot file is on disk. Seeded off the main thread rather than
    // read in the constructor — `snapshot.exists()` is a stat call, and this class
    // is constructed during composition. Otherwise only ever written from inside
    // this class: after a restore lands, a delete, or a snapshot found to have
    // vanished underneath a tap.
    private val _snapshotFilePresent = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            _snapshotFilePresent.value = withContext(Dispatchers.IO) { snapshot.exists() }
        }
    }

    /**
     * Whether the Restore row belongs on screen: the file is there *and* the local
     * history is empty. Both conditions together are what make the row's own copy
     * true — "Android's backup left a snapshot of your history on this phone" would
     * be a lie the moment the phone has its own history again, whether that history
     * arrived by the user marking a place or by the restore itself landing.
     */
    val snapshotAvailable: StateFlow<Boolean> =
        combine(_snapshotFilePresent, places.statuses) { present, statuses -> present && statuses.isEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun setMapHue(slot: MapSlot, hue: MapHue) = settings.setMapHue(slot, hue)

    fun resetMapColors() = settings.resetMapColors()

    fun setMapBase(base: MapBase) = settings.setMapBase(base)

    fun setDenominator(value: Denominator) = settings.setDenominator(value)

    fun setHapticsEnabled(enabled: Boolean) = settings.setHapticsEnabled(enabled)

    fun setNationality(value: String) = settings.setNationality(value)

    fun setSecondNationality(value: String?) = settings.setSecondNationality(value)

    /** What the save dialog opens with. The user can rename it. */
    fun suggestedFileName(): String = TravelBackup.suggestedFileName(today().toString())

    fun dismissTransfer() {
        _transfer.value = TransferState.Idle
    }

    fun dismissReset() {
        _reset.value = TransferState.Idle
    }

    /**
     * The emergency reset. The screen is responsible for confirming first — this
     * assumes that already happened and just does it.
     *
     * No [TransferState.Done] on success: [SettingsStore.resetAll] sends
     * the user straight to the welcome screen, which is confirmation enough. A
     * "deleted" dialog on top of that would need its own dismissal before the
     * welcome screen underneath became visible.
     */
    fun deleteEverything() {
        if (_reset.value == TransferState.Busy) return
        _reset.value = TransferState.Busy
        viewModelScope.launch {
            _reset.value = try {
                places.deleteAll()
                settings.resetAll()
                withContext(Dispatchers.IO) {
                    snapshot.delete()
                    // Only enqueues the upload — it does not run synchronously and does
                    // not confirm anything reached Google, but it is what turns the
                    // emptied state into what Auto Backup carries next, overwriting
                    // whatever pre-existing cloud copy this device had.
                    snapshot.notifyBackupChanged()
                }
                _snapshotFilePresent.value = false
                TransferState.Idle
            } catch (e: Exception) {
                TransferState.Failed(e.message ?: "Couldn't delete everything.")
            }
        }
    }

    /** Write the whole history to a location the user picked in the file picker. */
    fun export(uri: Uri) {
        if (_transfer.value == TransferState.Busy) return
        _transfer.value = TransferState.Busy
        viewModelScope.launch {
            _transfer.value = try {
                val export = places.exportBackup()
                withContext(Dispatchers.IO) {
                    // "wt" truncates. Without the t, overwriting a longer previous
                    // backup leaves its tail behind and produces a file that is
                    // valid JSON followed by garbage.
                    val stream = resolver.openOutputStream(uri, "wt")
                        ?: throw IOException("The file could not be opened for writing.")
                    stream.use { it.write(export.json.toByteArray()) }
                }
                TransferState.Done(exportMessage(export.places, export.removals))
            } catch (e: Exception) {
                // Deliberately broad: this is a user-chosen file on a provider we
                // know nothing about, and every plausible failure — permission
                // revoked, SD card pulled, provider crash — should be a sentence on
                // screen rather than a crash on a screen full of settings.
                TransferState.Failed(e.message ?: "The export failed.")
            }
        }
    }

    /**
     * Read a picked file and plan what importing it would do, without writing
     * anything. [confirmImportPreview] or [dismissImportPreview] end the flow this
     * starts.
     */
    fun previewImport(uri: Uri) {
        if (_transfer.value == TransferState.Busy) return
        _transfer.value = TransferState.Busy
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readText(uri) }
                previewText(text)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // Throwable for the same reason [confirmImportPreview] catches it, and
                // more so: this is where a picked file is first read and parsed, so an
                // OutOfMemoryError or a StackOverflowError from nested JSON surfaces
                // here first and should be a sentence on screen, not a crash.
                _transfer.value = TransferState.Failed(t.message ?: "The import failed.")
            }
        }
    }

    /**
     * Offer the snapshot Auto Backup left behind through the same preview dialog a
     * picked file goes through — [previewText] is the tail [previewImport] shares.
     *
     * A null read means the file vanished between the Restore button appearing and
     * the tap (deleted from under it, or never actually landed): treated as the
     * snapshot no longer being there, not as an import failure with nothing to blame.
     */
    fun previewSnapshotRestore() {
        if (_transfer.value == TransferState.Busy) return
        _transfer.value = TransferState.Busy
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { snapshot.read() }
            if (text == null) {
                _snapshotFilePresent.value = false
                _transfer.value = TransferState.Failed("That snapshot isn't there anymore.")
                return@launch
            }
            try {
                previewText(text)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _transfer.value = TransferState.Failed(t.message ?: "The import failed.")
            }
        }
    }

    /**
     * The shared tail of [previewImport] and [previewSnapshotRestore]: plan what
     * importing [text] would do and hand the result to the same confirmation dialog,
     * regardless of where the text came from.
     */
    private suspend fun previewText(text: String) {
        val plan = places.previewBackup(text)
        // Codes, not names, are what the plan carries — resolved here because
        // "DE" means nothing to a user looking at a confirmation dialog.
        val sampleNames = plan.apply.take(PREVIEW_SAMPLE_SIZE)
            .map { entry -> places.place(entry.code)?.name ?: entry.code }
        pendingImportText = text
        _importPreview.value = ImportPreview(plan, sampleNames)
        _transfer.value = TransferState.Idle
    }

    /** The user confirmed the dialog [previewImport] opened. Applies the file held
     * since the preview, not a fresh read of the URI. */
    fun confirmImportPreview() {
        val text = pendingImportText ?: return
        // Busy means another transfer is still running: keep the dialog and the text so
        // the tap can be repeated, instead of silently discarding what was confirmed.
        if (_transfer.value == TransferState.Busy) return
        pendingImportText = null
        _importPreview.value = null
        _transfer.value = TransferState.Busy
        viewModelScope.launch {
            _transfer.value = try {
                // No snapshot-presence bookkeeping needed here: [snapshotAvailable]
                // already combines the file's presence with `places.statuses`, and a
                // successful import — from a snapshot restore or an ordinary file —
                // makes that flow non-empty on its own, which is what hides the row.
                val plan = places.importBackup(text)
                // The user has acted on the snapshot (or on a file that supersedes it),
                // so staging may refresh it from here on.
                withContext(Dispatchers.IO) { snapshot.markOwned() }
                TransferState.Done(importMessage(plan))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // Throwable rather than Exception on purpose. The two failures a
                // picked file can actually cause here are Errors, not Exceptions:
                // OutOfMemoryError from a large file and StackOverflowError from
                // deeply nested JSON, which org.json parses recursively. [readText]'s
                // cap is what makes both unlikely; this is what keeps either from
                // being a crash instead of a sentence on screen. importBackup decodes
                // the text again rather than trusting the preview's plan, so this
                // arm is reachable here and not only in [previewImport].
                TransferState.Failed(t.message ?: "The import failed.")
            }
        }
    }

    /** The user dismissed the dialog [previewImport] opened. Nothing changes. */
    fun dismissImportPreview() {
        pendingImportText = null
        _importPreview.value = null
    }

    /**
     * Read a picked file, refusing anything absurdly large.
     *
     * A full export of all 252 places is around 50 kB, so the cap is ten times the
     * largest file this app can itself produce. It exists because the picker can hand
     * us any file at all, and reading a 2 GB video into a String would take the app
     * down with an OutOfMemoryError.
     *
     * The cap has to be this tight because the peak is several times the file: the
     * bytes buffered here, then a String of them (UTF-16, so double), then org.json's
     * object tree, which is larger again. An 8 MB cap — the previous value — could
     * still reach a small device's heap ceiling with all three alive at once, which
     * is the very thing the cap is for.
     */
    private fun readText(uri: Uri): String {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("The file could not be opened for reading.")
        stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > MAX_IMPORT_BYTES) {
                    throw IOException("That file is far too large to be an BeyondBorders backup.")
                }
            }
            return buffer.toString(Charsets.UTF_8.name())
        }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 512 * 1024

        fun factory(
            settings: SettingsStore,
            places: PlaceRepository,
            visa: VisaRepository,
            resolver: ContentResolver,
            snapshot: CloudSnapshot,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(settings, places, visa, resolver, snapshot) as T
            }
        }
    }
}

// --- pure functions, unit-testable without Android ---------------------------

/** How many affected place names the preview dialog names before falling back to
 * "and N more". Ten reads as a sample; the full list of up to 252 would not. */
internal const val PREVIEW_SAMPLE_SIZE = 10

internal fun exportMessage(places: Int, removals: Int): String = buildString {
    append("Saved ")
    append(places)
    append(if (places == 1) " place" else " places")
    // Only mentioned when there are any: "and 0 removals" is noise, but silently
    // writing rows the user cannot see would be worse.
    if (removals > 0) {
        append(" and ")
        append(removals)
        append(if (removals == 1) " removal" else " removals")
    }
    append(".")
}

internal fun importMessage(plan: BackupMerge.Plan): String = buildString {
    val changed = plan.added + plan.updated
    if (changed == 0) {
        append("Nothing to change — everything in that file is already here.")
    } else {
        append("Imported ")
        val parts = buildList {
            if (plan.added > 0) add("${plan.added} new")
            if (plan.updated > 0) add("${plan.updated} updated")
        }
        append(parts.joinToString(" and "))
        append(if (changed == 1) " place." else " places.")
        if (plan.unchanged > 0) {
            append(" ")
            append(plan.unchanged)
            append(" already current.")
        }
    }
    if (plan.unknownCodes.isNotEmpty()) {
        // Worth naming rather than counting: an unknown code almost always means
        // the file came from a build with a different place list, and knowing
        // which ones were dropped is the only way to tell whether that matters.
        append(" Skipped ")
        append(plan.unknownCodes.size)
        append(if (plan.unknownCodes.size == 1) " unknown code: " else " unknown codes: ")
        append(plan.unknownCodes.take(5).joinToString(", "))
        if (plan.unknownCodes.size > 5) append(", …")
        append(".")
    }
    if (plan.unknownStatuses.isNotEmpty()) {
        // Named for the same reason, and urgent for a different one: an unrecognised
        // status means the file was written by a newer build, so those places still
        // exist somewhere and the user needs to know this phone did not take them.
        // The count is rows and the list is distinct words, so "2 entries" with one
        // word named is normal rather than a bug.
        append(" Skipped ")
        append(plan.unknownStatuses.size)
        append(if (plan.unknownStatuses.size == 1) " entry" else " entries")
        val names = plan.unknownStatuses.distinct()
        append(if (names.size == 1) " with an unrecognised status: " else " with unrecognised statuses: ")
        append(names.take(5).joinToString(", "))
        if (names.size > 5) append(", …")
        append(".")
    }
    if (plan.unreadableRows > 0) {
        // Nothing to name — a blank code or a missing timestamp carries no identity
        // worth showing — so this stays a bare count, unlike the two clauses above.
        append(" ")
        append(plan.unreadableRows)
        append(if (plan.unreadableRows == 1) " row could not be read." else " rows could not be read.")
    }
}
