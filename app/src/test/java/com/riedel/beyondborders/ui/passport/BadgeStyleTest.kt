package com.riedel.beyondborders.ui.passport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeStyleTest {
    @Test
    fun `black on white has the textbook contrast of 21`() {
        assertEquals(21.0, contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun `body text on every group container meets AA in both themes`() {
        for (g in AchievementGroup.entries) {
            val t = badgeTone(g)
            assertTrue("$g light", contrastRatio(InkLight, t.containerLight) >= 4.5)
            assertTrue("$g dark", contrastRatio(InkDark, t.containerDark) >= 4.5)
        }
    }

    @Test
    fun `icon glyph on every accent circle has at least 3 to 1 in both themes`() {
        for (g in AchievementGroup.entries) {
            val t = badgeTone(g)
            assertTrue("$g light", contrastRatio(GlyphOnAccentLight, t.accentLight) >= 3.0)
            assertTrue("$g dark", contrastRatio(GlyphOnAccentDark, t.accentDark) >= 3.0)
        }
    }

    @Test
    fun `accent circle stands out from its container by at least 3 to 1 in both themes`() {
        for (g in AchievementGroup.entries) {
            val t = badgeTone(g)
            assertTrue("$g light", contrastRatio(t.accentLight, t.containerLight) >= 3.0)
            assertTrue("$g dark", contrastRatio(t.accentDark, t.containerDark) >= 3.0)
        }
    }

    @Test
    fun `every group has its own light accent`() {
        val accents = AchievementGroup.entries.map { badgeTone(it).accentLight }
        assertEquals(accents.size, accents.toSet().size)
    }
}
