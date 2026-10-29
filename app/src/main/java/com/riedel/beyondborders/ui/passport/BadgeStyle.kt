package com.riedel.beyondborders.ui.passport

import kotlin.math.pow

/**
 * One hue per badge group, as a pair of tones per theme: a pale (light theme) or deep
 * (dark theme) [container] for the card and a stronger [accent] for the icon circle.
 * Plain packed ints, not `Color`, so nothing here touches android.graphics and the
 * contrast test can run on the host. The icon shape carries the meaning; colour is mood.
 */
internal data class BadgeTone(
    val containerLight: Int, val accentLight: Int,
    val containerDark: Int, val accentDark: Int,
)

/** Text on a tinted container, and the icon glyph on the accent circle, per theme. */
internal const val InkLight = 0xFF1B1B1F.toInt()
internal const val InkDark = 0xFFE6E1E5.toInt()
internal const val GlyphOnAccentLight = 0xFFFFFFFF.toInt()
internal const val GlyphOnAccentDark = 0xFF1B1B1F.toInt()

private fun tone(cl: Long, al: Long, cd: Long, ad: Long) =
    BadgeTone(cl.toInt(), al.toInt(), cd.toInt(), ad.toInt())

internal fun badgeTone(group: AchievementGroup): BadgeTone = when (group) {
    AchievementGroup.COUNTS -> tone(0xFFE3F0FF, 0xFF1565C0, 0xFF12283D, 0xFF90CAF9)
    AchievementGroup.CONTINENTS -> tone(0xFFE0F5F1, 0xFF00695C, 0xFF0F2E2A, 0xFF80CBC4)
    AchievementGroup.REGIONS -> tone(0xFFE6F4DD, 0xFF2E7D32, 0xFF1B2E19, 0xFFA5D6A7)
    AchievementGroup.GROUPS -> tone(0xFFECE7FA, 0xFF5E35B1, 0xFF241B3D, 0xFFB39DDB)
    AchievementGroup.DEPENDENCIES -> tone(0xFFFFEEDB, 0xFFA84A00, 0xFF3A2410, 0xFFFFB74D)
    AchievementGroup.NON_UN -> tone(0xFFFDE6E6, 0xFFC62828, 0xFF3D1A1A, 0xFFEF9A9A)
    AchievementGroup.TINY -> tone(0xFFFCE4F0, 0xFFAD1457, 0xFF3B1A2B, 0xFFF48FB1)
    AchievementGroup.GIANTS -> tone(0xFFEFE6DF, 0xFF6D4C41, 0xFF2E231E, 0xFFBCAAA4)
    AchievementGroup.SPECIAL -> tone(0xFFFFF3CC, 0xFF7A5F00, 0xFF332B0D, 0xFFFFD54F)
    AchievementGroup.LIVED -> tone(0xFFDDF3FA, 0xFF00677C, 0xFF0F2C35, 0xFF80DEEA)
    AchievementGroup.WISHLIST -> tone(0xFFF3E5F5, 0xFF8E24AA, 0xFF2F1A33, 0xFFCE93D8)
}

/** WCAG 2.x contrast ratio between two opaque packed ARGB colours. */
internal fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
