package com.riedel.aeroglobe.settings

import android.content.Context
import androidx.core.content.edit
import com.riedel.aeroglobe.core.Uuid
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Which screen the app opens on.
 *
 * [AUTO] is the behaviour asked for in PLAN.md section 6: the flight screen wins
 * whenever a flight is being tracked, the map wins when none is.
 */
enum class HomeTab { AUTO, MAP, FLIGHTS }

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

    private val _homeTab = MutableStateFlow(
        runCatching { HomeTab.valueOf(prefs.getString(KEY_HOME_TAB, null) ?: "") }
            .getOrDefault(HomeTab.AUTO)
    )
    val homeTab: StateFlow<HomeTab> = _homeTab.asStateFlow()

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
     * Phase 5 sync needs to tell "this device already knows about that edit" from
     * "another device made this edit" without a user account, so the id is
     * generated on first launch and never leaves the device except inside sync
     * payloads. It is not tied to any hardware identifier.
     */
    val deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: Uuid.v7().also {
        prefs.edit { putString(KEY_DEVICE_ID, it) }
    }

    /** Off by default: with no key entered this would just poll AeroDataBox into null responses. */
    private val _useRealFlightData = MutableStateFlow(prefs.getBoolean(KEY_USE_REAL_FLIGHT_DATA, false))
    val useRealFlightData: StateFlow<Boolean> = _useRealFlightData.asStateFlow()

    /** The RapidAPI key for AeroDataBox. Null until the user pastes one in. */
    private val _aeroDataBoxApiKey = MutableStateFlow(prefs.getString(KEY_AERODATABOX_API_KEY, null))
    val aeroDataBoxApiKey: StateFlow<String?> = _aeroDataBoxApiKey.asStateFlow()

    /**
     * Requests made against AeroDataBox so far this calendar month, for the low-quota
     * warning on 3d's free tier. Resets itself on read across a month boundary rather
     * than needing a background job — nothing else here runs while the app is closed.
     */
    private val _flightRequestCount = MutableStateFlow(
        currentMonthRequestCount(
            storedMonth = prefs.getString(KEY_FLIGHT_REQUEST_MONTH, null),
            storedCount = prefs.getInt(KEY_FLIGHT_REQUEST_COUNT, 0),
            nowMonth = monthKey(Instant.now()),
        )
    )
    val flightRequestCount: StateFlow<Int> = _flightRequestCount.asStateFlow()

    fun setUseRealFlightData(enabled: Boolean) {
        _useRealFlightData.value = enabled
        prefs.edit { putBoolean(KEY_USE_REAL_FLIGHT_DATA, enabled) }
    }

    fun setAeroDataBoxApiKey(key: String?) {
        val trimmed = key?.trim()?.ifBlank { null }
        _aeroDataBoxApiKey.value = trimmed
        prefs.edit { putString(KEY_AERODATABOX_API_KEY, trimmed) }
    }

    /** Called once per attempted AeroDataBox request, success or failure — quota is spent either way. */
    fun recordFlightRequest() {
        val month = monthKey(Instant.now())
        val next = if (month == prefs.getString(KEY_FLIGHT_REQUEST_MONTH, null)) {
            _flightRequestCount.value + 1
        } else {
            1
        }
        _flightRequestCount.value = next
        prefs.edit {
            putString(KEY_FLIGHT_REQUEST_MONTH, month)
            putInt(KEY_FLIGHT_REQUEST_COUNT, next)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    fun setHomeTab(tab: HomeTab) {
        _homeTab.value = tab
        prefs.edit { putString(KEY_HOME_TAB, tab.name) }
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
        const val KEY_HOME_TAB = "home_tab"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_HUE_VISITED = "map_hue_visited"
        const val KEY_HUE_LIVED = "map_hue_lived"
        const val KEY_HUE_WISHLIST = "map_hue_wishlist"
        const val KEY_USE_REAL_FLIGHT_DATA = "use_real_flight_data"
        const val KEY_AERODATABOX_API_KEY = "aerodatabox_api_key"
        const val KEY_FLIGHT_REQUEST_MONTH = "flight_request_month"
        const val KEY_FLIGHT_REQUEST_COUNT = "flight_request_count"
    }
}

// --- pure functions, unit-testable without Android ---------------------------

/** "2026-08" — a calendar month, UTC, as the boundary a request count resets on. */
internal fun monthKey(instant: Instant): String {
    val date = instant.atZone(ZoneOffset.UTC)
    return "%04d-%02d".format(date.year, date.monthValue)
}

/** [storedCount] carries over within a month; a month change starts the count over at zero. */
internal fun currentMonthRequestCount(storedMonth: String?, storedCount: Int, nowMonth: String): Int =
    if (storedMonth == nowMonth) storedCount else 0
