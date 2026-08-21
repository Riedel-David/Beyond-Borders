package com.riedel.aeroglobe.settings

import android.content.Context
import androidx.core.content.edit
import com.riedel.aeroglobe.core.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Preferences, on plain SharedPreferences.
 *
 * DataStore would be the modern choice, but the only stable release is behind an
 * alpha and it buys us nothing here: this is a handful of scalars read once at
 * startup, and SharedPreferences already loads them off the main thread and
 * commits asynchronously. Revisit if settings ever grow beyond scalars.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("aeroglobe.settings", Context.MODE_PRIVATE)

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

    /**
     * Stable per-install id, stamped onto every row we write.
     *
     * A future sync feature needs to tell "this device already knows about that
     * edit" from "another device made this edit" without a user account, so the id
     * is generated on first launch and never leaves the device except inside sync
     * payloads. It is not tied to any hardware identifier.
     */
    val deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: Uuid.v7().also {
        prefs.edit { putString(KEY_DEVICE_ID, it) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString(KEY_THEME, mode.name) }
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
        const val KEY_THEME = "theme_mode"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_HUE_VISITED = "map_hue_visited"
        const val KEY_HUE_LIVED = "map_hue_lived"
        const val KEY_HUE_WISHLIST = "map_hue_wishlist"
    }
}
