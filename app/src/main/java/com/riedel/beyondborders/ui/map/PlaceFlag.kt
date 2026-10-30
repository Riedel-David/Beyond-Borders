package com.riedel.beyondborders.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asset path of the bundled flag for a place code, or null when the code cannot name one.
 * Only shape is checked (two letters); whether the file exists is the loader's business,
 * because a few places (Northern Cyprus, Somaliland) have a code but no flag upstream.
 */
internal fun flagAssetPath(code: String?): String? {
    val c = code?.trim()?.lowercase() ?: return null
    if (c.length != 2 || !c.all { it in 'a'..'z' }) return null
    return "flags/$c.webp"
}

/** A decoded flag and the banner colours derived from it, computed together off the main thread. */
internal class PlaceFlag(val bitmap: ImageBitmap, val colors: List<Int>)

// Decoded flags are ~120 px tall, at most ~300x120 ARGB (~140 KB); a handful covers
// back-and-forth tapping.
private val flagCache = object : LruCache<String, PlaceFlag>(8) {}

/** Decodes the flag off the main thread. Null for no flag, a missing file or a bad decode. */
private suspend fun loadFlag(context: Context, code: String): PlaceFlag? {
    val path = flagAssetPath(code) ?: return null
    flagCache.get(path)?.let { return it }
    val flag = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
                ?: return@runCatching null
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            PlaceFlag(bmp.asImageBitmap(), dominantColors(px).map(::darkenForBannerText))
        }.getOrNull()
    }
    if (flag != null) flagCache.put(path, flag)
    return flag
}

/** The flag for [code], or null while loading and for places without one. */
@Composable
internal fun rememberPlaceFlag(code: String?): PlaceFlag? {
    val context = LocalContext.current.applicationContext
    val path = flagAssetPath(code)
    // Seeded from the cache and keyed on the code: a cached flag is there in the very
    // first frame (no slot-less frame that jumps), and a new code never shows the old flag.
    var flag by remember(code) { mutableStateOf(path?.let { flagCache.get(it) }) }
    LaunchedEffect(code) {
        if (flag == null && code != null) flag = loadFlag(context, code)
    }
    return flag
}

/**
 * Largest size with the bitmap's aspect ratio that fits inside [maxW] x [maxH]. Never
 * scales to a degenerate size: an empty bitmap yields the box itself rather than NaN.
 */
internal fun flagThumbSize(widthPx: Int, heightPx: Int, maxW: Float, maxH: Float): Pair<Float, Float> {
    if (widthPx <= 0 || heightPx <= 0) return maxW to maxH
    val scale = minOf(maxW / widthPx, maxH / heightPx)
    return widthPx * scale to heightPx * scale
}

/**
 * The most populous colours of an ARGB pixel array, most common first, at most [max].
 * Pixels are bucketed at 4 bits per channel so anti-aliased edges fold into the flat
 * colour beside them; each bucket reports the mean of its own pixels rather than its
 * corner, and a bucket close to an already chosen one is dropped so a flag's two
 * shades of blue do not take two of the three slots. Transparent pixels (Nepal's
 * corners) are skipped.
 */
internal fun dominantColors(argb: IntArray, max: Int = 3): List<Int> {
    val count = IntArray(4096)
    val sums = LongArray(4096 * 3)
    for (p in argb) {
        if ((p ushr 24) < 128) continue
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val k = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        count[k]++
        sums[k * 3] += r.toLong()
        sums[k * 3 + 1] += g.toLong()
        sums[k * 3 + 2] += b.toLong()
    }
    val chosen = ArrayList<IntArray>()
    for (k in count.indices.filter { count[it] > 0 }.sortedByDescending { count[it] }) {
        if (chosen.size >= max) break
        val rgb = intArrayOf(
            (sums[k * 3] / count[k]).toInt(),
            (sums[k * 3 + 1] / count[k]).toInt(),
            (sums[k * 3 + 2] / count[k]).toInt(),
        )
        val tooClose = chosen.any { c ->
            val dr = c[0] - rgb[0]
            val dg = c[1] - rgb[1]
            val db = c[2] - rgb[2]
            dr * dr + dg * dg + db * db < NEAR_DUPLICATE_DISTANCE_SQ
        }
        if (!tooClose) chosen += rgb
    }
    return chosen.map { (0xFF shl 24) or (it[0] shl 16) or (it[1] shl 8) or it[2] }
}

// RGB distance of 60 (of 441 possible): separates navy from red from white, merges shades.
private const val NEAR_DUPLICATE_DISTANCE_SQ = 60 * 60

// Sized for the banner's dimmest text, not its title: the meta line is the app's dark
// onSurfaceVariant (#C0C6D0, luminance ~0.562), and 4.5:1 for that needs
// (L_text + 0.05) / (L + 0.05) >= 4.5. White then gets ~8:1. Sized for white alone, the
// meta line came out at ~2.6:1.
internal const val BANNER_SECONDARY_TEXT_ARGB = 0xFFC0C6D0.toInt()
private val MAX_LUMINANCE_FOR_BANNER_TEXT =
    (relativeLuminance(BANNER_SECONDARY_TEXT_ARGB) + 0.05) / 4.5 - 0.05

private fun srgbToLinear(c: Int): Double {
    val v = c / 255.0
    return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
}

internal fun relativeLuminance(argb: Int): Double =
    0.2126 * srgbToLinear((argb shr 16) and 0xFF) +
        0.7152 * srgbToLinear((argb shr 8) and 0xFF) +
        0.0722 * srgbToLinear(argb and 0xFF)

/**
 * [argb] blended toward black, in 5% steps, until the banner's grey secondary text (and
 * so, with room to spare, its white title) reaches the WCAG AA ratio of 4.5:1. A colour that already passes comes back unchanged (and opaque).
 */
internal fun darkenForBannerText(argb: Int): Int {
    val opaque = argb or (0xFF shl 24)
    for (step in 0..20) {
        val keep = 1.0 - step * 0.05
        val r = (((opaque shr 16) and 0xFF) * keep).toInt()
        val g = (((opaque shr 8) and 0xFF) * keep).toInt()
        val b = ((opaque and 0xFF) * keep).toInt()
        val c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        if (relativeLuminance(c) <= MAX_LUMINANCE_FOR_BANNER_TEXT) return c
    }
    return 0xFF000000.toInt()
}
