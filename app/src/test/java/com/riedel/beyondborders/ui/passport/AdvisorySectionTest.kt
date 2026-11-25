package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.advisory.Agreement
import java.util.Locale
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AdvisorySectionTest {
    @Test
    fun `each stance maps to its own tone and stance 0 is neutral`() {
        assertEquals(AdvisoryTone.NEUTRAL, advisoryTone(0))
        assertEquals(AdvisoryTone.CAUTION, advisoryTone(1))
        assertEquals(AdvisoryTone.WARNING, advisoryTone(2))
        assertEquals(AdvisoryTone.DANGER, advisoryTone(3))
    }

    @Test
    fun `a stance outside the range is neutral rather than alarming`() {
        assertEquals(AdvisoryTone.NEUTRAL, advisoryTone(-1))
        assertEquals(AdvisoryTone.NEUTRAL, advisoryTone(9))
    }

    @Test
    fun `the updated date follows the locale and an unreadable one is shown as given`() {
        // The exact German pattern varies with the JDK's locale data; that it differs from US is the claim.
        assertNotEquals(formatAdvisoryDate("2026-10-02", Locale.US), formatAdvisoryDate("2026-10-02", Locale.GERMANY))
        assertEquals("Oct 2, 2026", formatAdvisoryDate("2026-10-02", Locale.US))
        assertEquals("yesterday", formatAdvisoryDate("yesterday", Locale.US))
    }

    // --- headline ----------------------------------------------------------------

    @Test
    fun `the agreement line is hidden when no source advises against travel`() {
        // Bahrain: nothing at stance 3, yet the German note advises against travel; "0 of 3" would contradict it.
        assertNull(advisoryHeadline(Agreement(0, 3)))
        assertNull(advisoryHeadline(Agreement(0, 1)))
    }

    @Test
    fun `the agreement line shows its count from one source up`() {
        val (res, args) = advisoryHeadline(Agreement(1, 3))!!
        assertEquals(R.string.advisory_agreement, res)
        assertEquals(listOf<Any>(1, 3), args.toList())
        assertEquals(listOf<Any>(2, 2), advisoryHeadline(Agreement(2, 2))!!.second.toList())
    }

    @Test
    fun `a place with no data from any source still says so`() {
        assertEquals(R.string.advisory_none, advisoryHeadline(Agreement(0, 0))!!.first)
    }

    // --- notes -------------------------------------------------------------------

    @Test
    fun `German notes are quoted with German marks and English notes with English ones`() {
        assertEquals("„Von Reisen wird abgeraten.“", quoteAdvisoryNote("DE", "Von Reisen wird abgeraten."))
        assertEquals("“Avoid the border area.”", quoteAdvisoryNote("UK", "Avoid the border area."))
        assertEquals("“Do not travel to X.”", quoteAdvisoryNote("US", "Do not travel to X."))
    }

    // --- colours -----------------------------------------------------------------

    private fun luminance(argb: Int): Double {
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(argb shr 16 and 255) + 0.7152 * lin(argb shr 8 and 255) + 0.0722 * lin(argb and 255)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = luminance(a).let { x -> luminance(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `badge text has at least 4_5 to 1 contrast on its fill in both themes`() {
        for (dark in listOf(false, true)) for (tone in AdvisoryTone.values()) {
            val (fill, text) = advisoryPalette(tone, dark)
            assertTrue("$tone dark=$dark ${contrast(fill, text)}", contrast(fill, text) >= 4.5)
        }
    }

    @Test
    fun `in dark theme the fills never get paler as the stance rises and danger is the strongest`() {
        val l = AdvisoryTone.values().map { luminance(advisoryPalette(it, true).first) }
        assertTrue(l.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(l.max(), l.last(), 0.0)
    }

    @Test
    fun `in light theme stance 0 is blue-grey and stance 1 is clearly amber`() {
        val neutral = advisoryPalette(AdvisoryTone.NEUTRAL, false).first
        val caution = advisoryPalette(AdvisoryTone.CAUTION, false).first
        assertTrue((neutral and 255) > (neutral shr 16 and 255))
        assertTrue((caution shr 16 and 255) - (caution and 255) >= 0x40)
    }

    @Test
    fun `every pipeline label key maps to its own string and unknown or missing keys fall back to the label`() {
        val keys = listOf(
            "uk_none", "uk_all_whole", "uk_essential_whole", "uk_all_parts", "uk_essential_parts",
            "uk_all_parts_essential_other", "de_none", "de_advised_against", "de_partial", "de_warning",
        )
        val res = keys.map { advisoryLabelRes(it) }
        assertTrue(res.all { it != null })
        assertEquals(keys.size, res.toSet().size)
        assertNull(advisoryLabelRes(null))
        assertNull(advisoryLabelRes("uk_from_the_future"))
        assertNull(advisoryLabelRes(""))
    }
}
