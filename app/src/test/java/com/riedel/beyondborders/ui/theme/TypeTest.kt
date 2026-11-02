package com.riedel.beyondborders.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeTest {
    private val styles: Map<String, TextStyle> = with(AppTypography) {
        mapOf(
            "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
            "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
        )
    }

    @Test
    fun `every Material style is defined with its own size and weight`() {
        assertEquals(15, styles.size)
        styles.forEach { (name, style) ->
            assertTrue("$name has no fontSize", style.fontSize.isSpecified)
            assertTrue("$name has no weight", style.fontWeight != null)
        }
    }

    @Test
    fun `display and headline styles carry tabular figures and labels body and titles do not`() {
        styles.filterKeys { it.startsWith("display") || it.startsWith("headline") }
            .forEach { (name, style) -> assertEquals("$name", "tnum", style.fontFeatureSettings) }
        styles.filterKeys { !it.startsWith("display") && !it.startsWith("headline") }
            .forEach { (name, style) -> assertEquals("$name", null, style.fontFeatureSettings) }
    }
}
