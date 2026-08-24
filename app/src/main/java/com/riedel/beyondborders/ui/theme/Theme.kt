package com.riedel.beyondborders.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.settings.ThemeMode

/** The map palette for the theme currently in effect. */
val LocalMapPalette = staticCompositionLocalOf { MapPaletteLight }

/** True when the app is rendering dark, regardless of how that was decided. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun BeyondBordersTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    mapColors: MapColors = MapColors.DEFAULT,
    mapBase: MapBase = MapBase.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Status- and navigation-bar icons have to follow the app's choice, not the
    // system's, otherwise forcing light mode on a dark-mode phone leaves white
    // icons on our white background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        // Recomputed on every change to either input, which is all the live
        // recolouring needs: MapScreen keys its style rebuild on this palette, and
        // MapPalette is a data class, so a new hue reaches MapLibre by itself.
        LocalMapPalette provides mapPalette(dark, mapColors, mapBase),
        LocalIsDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) BeyondBordersDark else BeyondBordersLight,
            content = content,
        )
    }
}
