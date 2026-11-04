package com.riedel.beyondborders.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.riedel.beyondborders.R

private const val TNUM = "tnum"

private val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.restyle(
    weight: FontWeight,
    tracking: TextUnit = letterSpacing,
    tnum: Boolean = false,
): TextStyle = copy(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    letterSpacing = tracking,
    fontFeatureSettings = if (tnum) TNUM else fontFeatureSettings,
)

/**
 * Inter with the Material 3 sizes, re-weighted: titles SemiBold/Medium with slightly
 * tighter tracking, labels Medium, body Normal.
 *
 * Starts from M3's own [Typography] and copies only what changes, so its
 * lineHeightStyle and line-break defaults survive.
 *
 * Display and headline styles carry tabular figures because those show the big counts
 * (stats hero, visa headline): equal-width digits keep a number from jittering as it
 * changes. Labels (buttons, chips, nav text), body and titles stay proportional.
 */
internal val AppTypography: Typography = Base.copy(
    displayLarge = Base.displayLarge.restyle(FontWeight.SemiBold, (-0.5).sp, tnum = true),
    displayMedium = Base.displayMedium.restyle(FontWeight.SemiBold, (-0.25).sp, tnum = true),
    displaySmall = Base.displaySmall.restyle(FontWeight.SemiBold, (-0.25).sp, tnum = true),
    headlineLarge = Base.headlineLarge.restyle(FontWeight.SemiBold, (-0.25).sp, tnum = true),
    headlineMedium = Base.headlineMedium.restyle(FontWeight.SemiBold, (-0.1).sp, tnum = true),
    headlineSmall = Base.headlineSmall.restyle(FontWeight.SemiBold, (-0.1).sp, tnum = true),
    titleLarge = Base.titleLarge.restyle(FontWeight.SemiBold, (-0.1).sp),
    titleMedium = Base.titleMedium.restyle(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.restyle(FontWeight.Medium),
    bodyLarge = Base.bodyLarge.restyle(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.restyle(FontWeight.Normal),
    bodySmall = Base.bodySmall.restyle(FontWeight.Normal),
    labelLarge = Base.labelLarge.restyle(FontWeight.Medium),
    labelMedium = Base.labelMedium.restyle(FontWeight.Medium),
    labelSmall = Base.labelSmall.restyle(FontWeight.Medium),
)
