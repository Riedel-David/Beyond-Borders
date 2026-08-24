package com.riedel.borderline.settings

import android.content.Context
import androidx.core.content.edit
import com.riedel.borderline.core.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Which places count towards the passport's denominator — the most-argued number
 * in this hobby, and the one people compare with friends. [Place.unMember] and
 * [Place.uninhabited] already carry everything needed to answer it; this only
 * picks which one, if any, to filter by.
 */
enum class Denominator { ALL, UN_MEMBER, INHABITED }

/**
 * Preferences, on plain SharedPreferences.
 *
 * DataStore would be the modern choice, but the only stable release is behind an
 * alpha and it buys us nothing here: this is a handful of scalars read once at
 * startup, and SharedPreferences already loads them off the main thread and
 * commits asynchronously. Revisit if settings ever grow beyond scalars.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    /**
     * A second file for one value, because Auto Backup excludes *files*, not keys.
     *
     * [deviceId] must not travel to a new phone (see below) while the theme and the
     * map colours should, and one shared file cannot be both — so this one is named
     * in `backup_rules.xml` and `data_extraction_rules.xml` as the only thing a
     * backup leaves behind.
     */
    private val devicePrefs = context.getSharedPreferences(DEVICE_FILE, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _mapColors = MutableStateFlow(
        MapColors.parse(
            visited = prefs.getString(KEY_HUE_VISITED, null),
            lived = prefs.getString(KEY_HUE_LIVED, null),
            wishlist = prefs.getString(KEY_HUE_WISHLIST, null),
        )
    )
    val mapColors: StateFlow<MapColors> = _mapColors.asStateFlow()

    private val _mapBase = MutableStateFlow(
        runCatching { MapBase.valueOf(prefs.getString(KEY_MAP_BASE, null) ?: "") }
            .getOrDefault(MapBase.DEFAULT)
    )
    val mapBase: StateFlow<MapBase> = _mapBase.asStateFlow()

    private val _denominator = MutableStateFlow(
        runCatching { Denominator.valueOf(prefs.getString(KEY_DENOMINATOR, null) ?: "") }
            .getOrDefault(Denominator.ALL)
    )
    val denominator: StateFlow<Denominator> = _denominator.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    /**
     * Stable per-install id, stamped onto every row we write.
     *
     * A future sync feature needs to tell "this device already knows about that
     * edit" from "another device made this edit" without a user account, so the id
     * is generated on first launch and never leaves the device except inside sync
     * payloads. It is not tied to any hardware identifier.
     *
     * *Per install* is the whole point, so a restore onto a second phone has to land
     * on a new one. Two installs sharing an id would each treat the other's writes as
     * its own and skip them — every edit made on the new phone silently ignored by the
     * old one, on the one table in the app that cannot be re-fetched.
     *
     * An upgrade from the build where this lived in the settings file deletes the old
     * key rather than adopting its value, and gets a new id. Adopting it would read
     * the same way on a restored phone as on an upgraded one — every backup taken so
     * far has the old key in it, so the fallback that kept an upgrade's id stable
     * would hand a restore the id it must not have, which is the whole bug. Losing
     * continuity costs nothing today: the id is a stamp on rows we write, and nothing
     * reads it back.
     */
    val deviceId: String = devicePrefs.getString(KEY_DEVICE_ID, null)
        ?: Uuid.v7().also {
            devicePrefs.edit { putString(KEY_DEVICE_ID, it) }
            prefs.edit { remove(KEY_DEVICE_ID) }
        }

    /**
     * Collapsed passport section headers, e.g. "CONTINENT:Europe".
     *
     * Prefixed with the grouping's name so a header collapsed under "By continent"
     * does not, by coincidence of the same string, also collapse under "By region"
     * or "A-Z" — three groupings share one key rather than needing three.
     */
    private val _collapsedHeaders = MutableStateFlow(
        prefs.getStringSet(KEY_COLLAPSED_HEADERS, emptySet()) ?: emptySet()
    )
    val collapsedHeaders: StateFlow<Set<String>> = _collapsedHeaders.asStateFlow()

    fun setCollapsedHeaders(headers: Set<String>) {
        _collapsedHeaders.value = headers
        prefs.edit { putStringSet(KEY_COLLAPSED_HEADERS, headers) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    fun setMapBase(base: MapBase) {
        _mapBase.value = base
        prefs.edit { putString(KEY_MAP_BASE, base.name) }
    }

    fun setDenominator(value: Denominator) {
        _denominator.value = value
        prefs.edit { putString(KEY_DENOMINATOR, value.name) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit { putBoolean(KEY_HAPTICS, enabled) }
    }

    /**
     * Repaint one status. The other two follow, because [MapColors.with] swaps.
     *
     * All three keys are written every time even though one slot was asked for: the
     * swap can move a hue the user did not touch, and a partial write would leave
     * two statuses sharing a colour on the next launch — the one state the model
     * refuses to represent.
     */
    fun setMapHue(slot: MapSlot, hue: MapHue) {
        val next = _mapColors.value.with(slot, hue)
        if (next == _mapColors.value) return
        _mapColors.value = next
        prefs.edit {
            putString(KEY_HUE_VISITED, next.visited.name)
            putString(KEY_HUE_LIVED, next.lived.name)
            putString(KEY_HUE_WISHLIST, next.wishlist.name)
        }
    }

    /**
     * Back to the shipped palette.
     *
     * The keys are removed rather than written with the default names, so a later
     * build that changes what the default is picks the new one up instead of being
     * stuck with today's answer frozen into a preference file.
     */
    fun resetMapColors() {
        _mapColors.value = MapColors.DEFAULT
        prefs.edit {
            remove(KEY_HUE_VISITED)
            remove(KEY_HUE_LIVED)
            remove(KEY_HUE_WISHLIST)
        }
    }

    private companion object {
        const val SETTINGS_FILE = "borderline.settings"

        /** Named in both backup rule files; renaming it here means renaming it there. */
        const val DEVICE_FILE = "borderline.device"

        const val KEY_THEME = "theme_mode"
        const val KEY_MAP_BASE = "map_base"
        const val KEY_DENOMINATOR = "denominator"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_COLLAPSED_HEADERS = "collapsed_headers"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_HUE_VISITED = "map_hue_visited"
        const val KEY_HUE_LIVED = "map_hue_lived"
        const val KEY_HUE_WISHLIST = "map_hue_wishlist"
    }
}
