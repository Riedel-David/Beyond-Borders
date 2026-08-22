package com.riedel.aeroglobe.ui.map

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.riedel.aeroglobe.ui.places.TravelStats
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the shared image says about the map, so it is a claim rather than just a
 * picture. "been", not "visited": a place lived in must count here too, matching
 * every other count this app shows.
 */
internal fun mapShareCaption(stats: TravelStats): String =
    "${stats.been} of ${stats.total} · ${stats.percent}% of the world · " +
        "${stats.continentsTouched} continents"

/**
 * Burns [caption] into a translucent bar across the bottom of [bitmap], returning
 * a new bitmap. A drawn-in caption travels with the image through any app that
 * strips text metadata or crops the share sheet's preview; overlaid chrome would
 * not.
 */
private fun withCaption(bitmap: Bitmap, caption: String): Bitmap {
    val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val textSize = out.width * 0.035f
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.textSize = textSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val barHeight = textSize * 2.4f
    val barTop = out.height - barHeight
    canvas.drawRect(0f, barTop, out.width.toFloat(), out.height.toFloat(), Paint().apply {
        color = Color.argb(160, 0, 0, 0)
    })
    val baseline = out.height - (barHeight - textSize) / 2f
    canvas.drawText(caption, out.width / 2f, baseline, textPaint)
    return out
}

/**
 * Writes the map snapshot, captioned with [stats], to cacheDir/shared and opens
 * the system share sheet on it.
 *
 * Overwrites the same file every time rather than naming it uniquely: the file only
 * needs to survive long enough for the receiving app to read it out of the Uri, not
 * to persist, so there is nothing to clean up and nothing worth a timestamp.
 */
suspend fun shareMapSnapshot(context: Context, bitmap: Bitmap, stats: TravelStats) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "aeroglobe-map.png")
        val captioned = withCaption(bitmap, mapShareCaption(stats))
        FileOutputStream(file).use { out -> captioned.compress(Bitmap.CompressFormat.PNG, 100, out) }
        file
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
