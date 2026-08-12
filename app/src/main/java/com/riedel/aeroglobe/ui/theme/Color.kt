package com.riedel.aeroglobe.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A hand-picked palette rather than Material You dynamic colour.
 *
 * The map is the app's centrepiece and it only works if "visited", "wishlist"
 * and "not yet" stay clearly distinguishable from each other and from the ocean.
 * Dynamic colour would re-tint those from the user's wallpaper and could easily
 * land on three shades of the same hue. So: fixed palette, deep azure for the
 * app, teal for wishlist, warm amber reserved for flight alerts.
 */

// --- light ------------------------------------------------------------------
private val Azure40 = Color(0xFF135CC9)
private val Azure90 = Color(0xFFDCE7FF)
private val Azure10 = Color(0xFF001B45)
private val Slate40 = Color(0xFF4A6072)
private val Slate90 = Color(0xFFD6E4F0)
private val Slate10 = Color(0xFF071E2E)
private val Teal40 = Color(0xFF0F8A7E)
private val Teal90 = Color(0xFFA7F2E6)
private val Teal10 = Color(0xFF00201C)

val AeroGlobeLight = lightColorScheme(
    primary = Azure40,
    onPrimary = Color.White,
    primaryContainer = Azure90,
    onPrimaryContainer = Azure10,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF131A22),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF131A22),
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = Color(0xFF434A54),
    surfaceContainer = Color(0xFFECF0F6),
    surfaceContainerHigh = Color(0xFFE5EAF2),
    outline = Color(0xFF74797F),
    outlineVariant = Color(0xFFC5CBD4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

// --- dark -------------------------------------------------------------------
private val Azure80 = Color(0xFFAEC6FF)
private val Azure30 = Color(0xFF00458F)
private val Azure20 = Color(0xFF002E6B)
private val Slate80 = Color(0xFFB5C8DA)
private val Slate30 = Color(0xFF32485A)
private val Slate20 = Color(0xFF20323F)
private val Teal80 = Color(0xFF6ED6C6)
private val Teal30 = Color(0xFF006B60)
private val Teal20 = Color(0xFF00382F)

val AeroGlobeDark = darkColorScheme(
    primary = Azure80,
    onPrimary = Azure20,
    primaryContainer = Azure30,
    onPrimaryContainer = Azure90,
    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Teal80,
    onTertiary = Teal20,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    background = Color(0xFF0E1319),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF0E1319),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF3F4650),
    onSurfaceVariant = Color(0xFFC0C6D0),
    surfaceContainer = Color(0xFF1A2028),
    surfaceContainerHigh = Color(0xFF242B34),
    outline = Color(0xFF8A9099),
    outlineVariant = Color(0xFF3F4650),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * Colours the map draws with. Kept out of [androidx.compose.material3.ColorScheme]
 * because MapLibre needs them as plain ints at style-build time, not as theme
 * lookups inside a composition.
 */
data class MapPalette(
    val ocean: Color,
    val unvisited: Color,
    val visited: Color,
    val wishlist: Color,
    val border: Color,
    val selectedOutline: Color,
)

val MapPaletteLight = MapPalette(
    ocean = Color(0xFFDCE6F2),
    unvisited = Color(0xFFFBFCFE),
    visited = Azure40,
    wishlist = Teal40,
    border = Color(0xFFA9B4C2),
    selectedOutline = Color(0xFF0B2C63),
)

val MapPaletteDark = MapPalette(
    ocean = Color(0xFF121A24),
    unvisited = Color(0xFF2A323C),
    visited = Color(0xFF3D7DE0),
    wishlist = Color(0xFF14A192),
    border = Color(0xFF4C5663),
    selectedOutline = Color(0xFFDCE7FF),
)
