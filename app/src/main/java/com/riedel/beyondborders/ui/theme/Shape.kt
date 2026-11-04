package com.riedel.beyondborders.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape scale with larger corner radii than the defaults.
 *
 * extraSmall (6dp): small chips, selection indicators
 * small (10dp): filter chips, compact cards
 * medium (16dp): standard cards, text fields
 * large (24dp): dialogs, bottom sheets, larger cards
 * extraLarge (32dp): full-screen overlays, major surfaces
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
