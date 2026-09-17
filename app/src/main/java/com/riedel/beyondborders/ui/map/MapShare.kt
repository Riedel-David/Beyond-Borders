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
 * The style in which the snapshot's caption is drawn.
 *
 * Three discrete variants rather than one parameterised layout. They share only
 * the translucent fill: the bar centres one line on a computed baseline, the
 * stamp measures its own text to size a pill. Stats-only skips the map bitmap
 * entirely and draws its own standalone card, so it does not share that fill at
 * all. Parameterising the rest would mean a layout engine for two fixed cases.
 */
enum class ShareCardStyle(val label: String, val blurb: String) {
    BAR("Caption bar", "Full stats across the bottom"),
    STAMP("Corner stamp", "Compact top-left badge"),
    STATS_ONLY("Stats only", "Just the numbers, no map"),
}

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
        "${stats.continentsTouched} ${pluralize(stats.continentsTouched, "continent")}"

/**
 * Short form for the stamp: percentage and fraction only.
 */
internal fun mapShareStamp(stats: TravelStats): String =
    "${stats.percent}% · ${stats.been}/${stats.total}"

private fun pluralize(count: Int, singular: String): String =
    if (count == 1) singular else "${singular}s"

/**
 * Burns a caption into [bitmap] using the chosen [style], returning a new bitmap.
 * A drawn-in caption travels with the image through any app that strips text
 * metadata or crops the share sheet's preview; overlaid chrome would not.
 */
private fun withCaption(bitmap: Bitmap, style: ShareCardStyle, stats: TravelStats): Bitmap {
    val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val fillPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }

    when (style) {
        ShareCardStyle.BAR -> {
            val textSize = out.width * 0.035f
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val barHeight = textSize * 2.4f
            val barTop = out.height - barHeight
            canvas.drawRect(0f, barTop, out.width.toFloat(), out.height.toFloat(), fillPaint)
            val baseline = out.height - (barHeight - textSize) / 2f
            canvas.drawText(mapShareCaption(stats), out.width / 2f, baseline, textPaint)
        }

        ShareCardStyle.STAMP -> {
            val textSize = out.width * 0.030f
            val inset = out.width * 0.03f
            val paddingH = textSize * 0.6f
            val paddingV = textSize * 0.45f
            val text = mapShareStamp(stats)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.LEFT
            }
            val textWidth = textPaint.measureText(text)
            val pillWidth = textWidth + paddingH * 2
            val pillHeight = textSize + paddingV * 2
            val radius = pillHeight / 2f

            canvas.drawRoundRect(
                inset, inset, inset + pillWidth, inset + pillHeight,
                radius, radius, fillPaint
            )
            val baseline = inset + paddingV - textPaint.fontMetrics.top
            canvas.drawText(text, inset + paddingH, baseline, textPaint)
        }

        ShareCardStyle.STATS_ONLY -> {
            // Handled by mapShareStatsCard: this style never wraps a map bitmap.
        }
    }

    return out
}

/**
 * Fixed size for the stats-only card: there is no source bitmap to size against,
 * since this style carries no map at all, so 1080x1350 (a common share-sheet
 * portrait ratio) stands in for one.
 */
private const val STATS_CARD_WIDTH = 1080
private const val STATS_CARD_HEIGHT = 1350

/**
 * A standalone card of just the travel numbers, with no map image behind them —
 * for sharing the claim without the geography. Built the same way [withCaption]
 * builds its overlays: a [Canvas] over a plain [Bitmap], [Paint] objects sized as
 * a fraction of the card width.
 */
internal fun mapShareStatsCard(stats: TravelStats): Bitmap {
    val out = Bitmap.createBitmap(STATS_CARD_WIDTH, STATS_CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(Color.rgb(24, 26, 32))

    val centerX = out.width / 2f
    val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = out.width * 0.13f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = out.width * 0.05f
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }
    val nudgePaint = Paint(bodyPaint).apply {
        textSize = out.width * 0.04f
        color = Color.argb(220, 255, 255, 255)
    }

    val lines = buildList {
        add("${stats.percent}% of the world" to headlinePaint)
        add("${stats.been} of ${stats.total} visited" to bodyPaint)
        add(
            "${stats.continentsTouched} ${pluralize(stats.continentsTouched, "continent")} touched" to
                bodyPaint
        )
        stats.nudge?.let { add(it to nudgePaint) }
        stats.coverage?.let { add(it to nudgePaint) }
    }

    // Each line's own font metrics decide the gap below it, so a big bold
    // headline gets more breathing room than a small blurb without hand-tuned
    // per-line spacing.
    val gap = out.width * 0.045f
    val totalHeight = lines.sumOf { (_, paint) ->
        val metrics = paint.fontMetrics
        (metrics.descent - metrics.ascent).toDouble()
    }.toFloat() + gap * (lines.size - 1)

    var baseline = (out.height - totalHeight) / 2f - lines.first().second.fontMetrics.ascent
    lines.forEach { (text, paint) ->
        canvas.drawText(text, centerX, baseline, paint)
        val metrics = paint.fontMetrics
        baseline += (metrics.descent - metrics.ascent) + gap
    }

    return out
}

/**
 * Captions the map snapshot with [stats] in the chosen [style], then shares it —
 * see [shareBitmap] for the file-write and share-sheet mechanics.
 */
suspend fun shareMapSnapshot(
    context: Context,
    bitmap: Bitmap,
    stats: TravelStats,
    style: ShareCardStyle,
) {
    val captioned = withCaption(bitmap, style, stats)
    try {
        shareBitmap(context, captioned)
    } finally {
        // withCaption copies the snapshot to draw on, so this is a second
        // full-screen ARGB_8888 bitmap — several megabytes on a tall phone, and
        // worth handing back rather than waiting for a GC to notice it.
        captioned.recycle()
    }
}

/**
 * The stats-only path: no map snapshot to caption, just a standalone card of the
 * numbers — see [mapShareStatsCard].
 */
suspend fun shareStatsCard(context: Context, stats: TravelStats) {
    shareBitmap(context, mapShareStatsCard(stats))
}

/**
 * Writes [bitmap] to cacheDir/shared and opens the system share sheet on it.
 * Shared by both the map-snapshot styles and the stats-only card, since neither
 * cares how the bitmap it was handed was built.
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
private suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - SHARE_RETENTION_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "beyondborders-map-${System.nanoTime()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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
