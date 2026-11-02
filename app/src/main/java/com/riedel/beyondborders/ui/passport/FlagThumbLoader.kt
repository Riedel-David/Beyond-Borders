package com.riedel.beyondborders.ui.passport

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.riedel.beyondborders.ui.map.flagAssetPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The list's own flag path, apart from the banner's: the rows need a small bitmap and
 * nothing else, so there is no colour extraction here, and the cache is bounded by
 * bytes (about 64 thumbnails) rather than by a handful of full-size flags.
 */
private const val CACHE_BYTES = 64 * 120 * 84 * 4

private val thumbCache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

// Codes whose file is missing or undecodable, so scrolling past XA-XZ does not reopen
// the asset every time. At most one entry per place code.
private val missing = java.util.Collections.synchronizedSet(HashSet<String>())

/** Largest power-of-two sample size that keeps the decoded image at least the target size. */
internal fun thumbSampleSize(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
    if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) return 1
    var size = 1
    while (srcW / (size * 2) >= targetW && srcH / (size * 2) >= targetH) size *= 2
    return size
}

private suspend fun loadThumb(context: Context, path: String, targetW: Int, targetH: Int): ImageBitmap? {
    thumbCache.get(path)?.let { return it }
    if (path in missing) return null
    val bmp = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = thumbSampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
            }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) thumbCache.put(path, bmp) else missing += path
    return bmp
}

/** A flag thumbnail sized for a [widthDp] x [heightDp] tile, or null while loading and for no flag. */
@Composable
internal fun rememberFlagThumb(code: String, widthDp: Int, heightDp: Int): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val path = flagAssetPath(code)
    var thumb by remember(path) { mutableStateOf(path?.let { thumbCache.get(it) }) }
    LaunchedEffect(path) {
        if (thumb == null && path != null) {
            thumb = loadThumb(context, path, (widthDp * density).toInt(), (heightDp * density).toInt())
        }
    }
    return thumb
}
