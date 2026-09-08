package com.riedel.beyondborders.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riedel.beyondborders.data.backup.BackupMerge
import com.riedel.beyondborders.data.backup.TravelBackup
import com.riedel.beyondborders.data.place.PlaceRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val mapColors: StateFlow<MapColors> = settings.mapColors
    val mapBase: StateFlow<MapBase> = settings.mapBase
    val denominator: StateFlow<Denominator> = settings.denominator
    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
    val nationality: StateFlow<String> = settings.nationality

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

    // Separate from [transfer] even though it is the same shape: the two live under
    // different sections, and sharing one flow would surface a delete's result (or an
    // export's) under the wrong heading.
    private val _reset = MutableStateFlow<TransferState>(TransferState.Idle)
    val reset: StateFlow<TransferState> = _reset.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun setMapHue(slot: MapSlot, hue: MapHue) = settings.setMapHue(slot, hue)

    fun resetMapColors() = settings.resetMapColors()

    fun setMapBase(base: MapBase) = settings.setMapBase(base)

    fun setDenominator(value: Denominator) = settings.setDenominator(value)

    fun setHapticsEnabled(enabled: Boolean) = settings.setHapticsEnabled(enabled)

    fun setNationality(value: String) = settings.setNationality(value)

    /** What the save dialog opens with. The user can rename it. */
    fun suggestedFileName(): String = TravelBackup.suggestedFileName(today().toString())

    fun dismissTransfer() {
        _transfer.value = TransferState.Idle
    }

    fun dismissReset() {
        _reset.value = TransferState.Idle
    }

    /** The emergency reset. The screen is responsible for confirming first — this
     * assumes that already happened and just does it. */
    fun deleteEverything() {
        if (_reset.value == TransferState.Busy) return
        _reset.value = TransferState.Busy
        viewModelScope.launch {
            _reset.value = try {
                places.deleteAll()
                settings.resetHasSeenWelcome()
                TransferState.Done("Everything deleted.")
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

    /** Merge a picked file into the database. Never destructive; see [BackupMerge]. */
    fun import(uri: Uri) {
        if (_transfer.value == TransferState.Busy) return
        _transfer.value = TransferState.Busy
        viewModelScope.launch {
            _transfer.value = try {
                val text = withContext(Dispatchers.IO) { readText(uri) }
                TransferState.Done(importMessage(places.importBackup(text)))
            } catch (e: Exception) {
                TransferState.Failed(e.message ?: "The import failed.")
            }
        }
    }

    /**
     * Read a picked file, refusing anything absurdly large.
     *
     * A full export of all 252 places is around 50 kB, so the cap is three orders of
     * magnitude of headroom. It exists because the picker can hand us any file at
     * all, and reading a 2 GB video into a String would take the app down with an
     * OutOfMemoryError that no catch block can turn into a friendly message.
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
        private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

        fun factory(
            settings: SettingsStore,
            places: PlaceRepository,
            visa: VisaRepository,
            resolver: ContentResolver,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(settings, places, visa, resolver) as T
            }
        }
    }
}

// --- pure functions, unit-testable without Android ---------------------------

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
}
