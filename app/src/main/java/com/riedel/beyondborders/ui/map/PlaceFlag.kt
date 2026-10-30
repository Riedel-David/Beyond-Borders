package com.riedel.beyondborders.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

// Decoded flags are ~320x240 ARGB (~300 KB); a handful covers back-and-forth tapping.
private val flagCache = object : LruCache<String, ImageBitmap>(8) {}

/** Decodes the flag off the main thread. Null for no flag, a missing file or a bad decode. */
private suspend fun loadFlag(context: Context, code: String): ImageBitmap? {
    val path = flagAssetPath(code) ?: return null
    flagCache.get(path)?.let { return it }
    val bmp = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) flagCache.put(path, bmp)
    return bmp
}

/** The flag for [code], or null while loading and for places without one. */
@Composable
internal fun rememberPlaceFlag(code: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val flag by produceState<ImageBitmap?>(null, code) {
        value = if (code == null) null else loadFlag(context, code)
    }
    return flag
}
