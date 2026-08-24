package com.riedel.beyondborders.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG colour maths, shared by the palette tests.
 *
 * In the test source set rather than in the app: nothing the app draws needs to
 * compute a contrast ratio at runtime, because the whole point of asserting these
 * numbers at build time is that the shipped values are already known to be good.
 *
 * Colours are plain 24-bit `0xRRGGBB` longs here. Alpha is deliberately absent — every
 * fill on the map is opaque, and a contrast ratio against a translucent colour is not
 * a defined thing.
 */
internal object ColorMath {

    /** The relative luminance of a colour, per WCAG 2.1. */
    fun luminance(rgb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** The shorter way round the colour wheel between two colours, in degrees. */
    fun hueGap(a: Long, b: Long): Double {
        val gap = abs(hueDegrees(a) - hueDegrees(b))
        return min(gap, 360.0 - gap)
    }

    fun hueDegrees(rgb: Long): Double {
        val r = ((rgb shr 16) and 0xFF).toDouble() / 255.0
        val g = ((rgb shr 8) and 0xFF).toDouble() / 255.0
        val b = (rgb and 0xFF).toDouble() / 255.0
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        val span = high - low
        if (span == 0.0) return 0.0
        val sextant = when (high) {
            r -> (g - b) / span
            g -> (b - r) / span + 2.0
            else -> (r - g) / span + 4.0
        }
        return (sextant * 60.0 + 360.0) % 360.0
    }

    /** Drops the alpha byte a Compose [Color] carries. */
    fun Color.rgb(): Long = toArgb().toLong() and 0xFFFFFF

    /**
     * The three dichromacies, as linear-RGB projections.
     *
     * These are the standard Vienot–Brettel–Mollon matrices: they collapse one of the
     * three cone responses and reproject, which is what makes two colours that differ
     * only in the missing dimension come out identical. Simulation is an approximation
     * of a spectrum of real conditions, so it is used here to *reject* a palette rather
     * than to certify one — a pair that survives all three has not been shown to be
     * safe, only to have no obvious way of failing.
     */
    enum class Cvd(val label: String, private val m: Array<DoubleArray>) {
        /** No red cones: roughly 1% of men. */
        PROTANOPIA(
            "protanopia",
            arrayOf(
                doubleArrayOf(0.1121, 0.8853, -0.0005),
                doubleArrayOf(0.1127, 0.8897, -0.0001),
                doubleArrayOf(0.0045, 0.0000, 1.0019),
            ),
        ),

        /** No green cones: roughly 1% of men, and the commonest of the three. */
        DEUTERANOPIA(
            "deuteranopia",
            arrayOf(
                doubleArrayOf(0.2920, 0.7054, -0.0003),
                doubleArrayOf(0.2934, 0.7089, 0.0000),
                doubleArrayOf(-0.0209, 0.4054, 0.6165),
            ),
        ),

        /** No blue cones: rare, and the worst case for a violet ramp. */
        TRITANOPIA(
            "tritanopia",
            arrayOf(
                doubleArrayOf(1.0, 0.1483, -0.1447),
                doubleArrayOf(0.0, 0.5766, 0.4185),
                doubleArrayOf(0.0, 0.0, 1.0),
            ),
        ),
        ;

        fun simulate(rgb: Long): Long {
            val linear = DoubleArray(3) { i -> toLinear(((rgb shr (16 - 8 * i)) and 0xFF).toInt()) }
            var out = 0L
            for (row in 0..2) {
                val v = m[row][0] * linear[0] + m[row][1] * linear[1] + m[row][2] * linear[2]
                out = (out shl 8) or fromLinear(v).toLong()
            }
            return out
        }

        private fun toLinear(byte: Int): Double {
            val c = byte / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        private fun fromLinear(value: Double): Int {
            val c = value.coerceIn(0.0, 1.0)
            val srgb = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
            return (srgb * 255.0).toInt().coerceIn(0, 255)
        }
    }
}
