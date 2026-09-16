package com.riedel.beyondborders.ui.map

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.riedel.beyondborders.ui.places.TravelStats
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How long a shared snapshot stays readable in the cache.
 *
 * Long enough for a mail sitting in drafts overnight to still find its attachment,
 * short enough that the cache does not accumulate — and these are cache files, so the
 * system is free to reclaim them sooner under pressure regardless.
 */
private const val SHARE_RETENTION_MS = 24L * 60 * 60 * 1000

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
 * Named uniquely per call rather than reusing one fixed file: two taps of Share in
 * quick succession, before the first chooser resolves, would otherwise let one
 * write interleave with the other's read.
 *
 * Older files are swept by age rather than all at once. Deleting the whole directory
 * on every share assumed the receiving app had already finished with the previous
 * URI, and Gmail in particular has not: it keeps the content URI in a draft and only
 * reads it when the mail is sent, so the earlier attachment quietly became a
 * zero-byte failure the moment a second share started.
 */
suspend fun shareMapSnapshot(context: Context, bitmap: Bitmap, stats: TravelStats) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - SHARE_RETENTION_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "beyondborders-map-${System.nanoTime()}.png")
        val captioned = withCaption(bitmap, mapShareCaption(stats))
        try {
            FileOutputStream(file).use { out ->
                captioned.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            // withCaption copies the snapshot to draw on, so this is a second
            // full-screen ARGB_8888 bitmap — several megabytes on a tall phone, and
            // worth handing back rather than waiting for a GC to notice it.
            captioned.recycle()
        }
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
