package com.riedel.beyondborders.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceContrastTest {
    private fun rgb(c: Color) = (c.toArgb().toLong() and 0xFFFFFF)

    private fun contrast(a: Color, b: Color) = ColorMath.contrast(rgb(a), rgb(b))

    /** Lowest to Highest, in that order. */
    private fun ladder(s: ColorScheme) = listOf(
        s.surfaceContainerLowest,
        s.surfaceContainerLow,
        s.surfaceContainer,
        s.surfaceContainerHigh,
        s.surfaceContainerHighest,
    )

    private fun checkText(name: String, s: ColorScheme) {
        for ((i, bg) in ladder(s).withIndex()) {
            assertTrue("$name onSurface on rung $i", contrast(s.onSurface, bg) >= 4.5)
            assertTrue("$name onSurfaceVariant on rung $i", contrast(s.onSurfaceVariant, bg) >= 4.5)
        }
    }

    private fun checkInverse(name: String, s: ColorScheme) {
        assertTrue("$name inverseOnSurface", contrast(s.inverseOnSurface, s.inverseSurface) >= 4.5)
        assertTrue("$name inversePrimary", contrast(s.inversePrimary, s.inverseSurface) >= 4.5)
    }

    private fun setRoles(s: ColorScheme) = mapOf(
        "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
        "surfaceBright" to s.surfaceBright,
        "surfaceDim" to s.surfaceDim,
        "inverseSurface" to s.inverseSurface,
        "inverseOnSurface" to s.inverseOnSurface,
        "inversePrimary" to s.inversePrimary,
    )

    private fun checkOverridden(name: String, ours: ColorScheme, baseline: ColorScheme, same: Set<String> = emptySet()) {
        val base = setRoles(baseline)
        for ((role, c) in setRoles(ours)) if (role !in same) assertNotEquals("$name $role is still the M3 baseline", base[role], c)
    }

    @Test
    fun `text on every light surface container reaches 4_5 to 1`() = checkText("light", BeyondBordersLight)

    @Test
    fun `text on every dark surface container reaches 4_5 to 1`() = checkText("dark", BeyondBordersDark)

    @Test
    fun `inverse text and accent on the inverse surface reach 4_5 to 1 in light`() =
        checkInverse("light", BeyondBordersLight)

    @Test
    fun `inverse text and accent on the inverse surface reach 4_5 to 1 in dark`() =
        checkInverse("dark", BeyondBordersDark)

    @Test
    fun `light containers darken strictly from lowest to highest`() {
        val l = ladder(BeyondBordersLight).map { ColorMath.luminance(rgb(it)) }
        assertTrue(l.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun `dark containers lighten strictly from lowest to highest`() {
        val l = ladder(BeyondBordersDark).map { ColorMath.luminance(rgb(it)) }
        assertTrue(l.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `every role set in the light scheme differs from the M3 baseline`() =
        // The M3 light baseline for Lowest is already pure white, which is what we want too.
        checkOverridden("light", BeyondBordersLight, lightColorScheme(), same = setOf("surfaceContainerLowest"))

    @Test
    fun `every role set in the dark scheme differs from the M3 baseline`() =
        checkOverridden("dark", BeyondBordersDark, darkColorScheme())
}
