package com.riedel.beyondborders.ui.map

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.places.TravelStats
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The style in which the snapshot's caption is drawn.
 *
 * Three discrete variants rather than one parameterised layout. They share the
 * brand furniture — [drawLogoTile], [wordmarkPaint], [drawProgressBar] — but not a
 * layout: the bar is a two-column footer under a gradient scrim, the stamp
 * measures its own text to size a badge, and stats-only skips the map bitmap
 * entirely and composes a full card. Parameterising the rest would mean a layout
 * engine for three fixed cases.
 */
enum class ShareCardStyle(val label: String, val blurb: String) {
    BAR("Caption bar", "Logo and stats along the bottom"),
    STAMP("Corner badge", "Compact top-left badge"),
    STATS_ONLY("Stats card", "Just the numbers, no map"),
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
 * The app's own palette, repeated here as plain ints.
 *
 * A shared image is not themed: it must look the same whichever palette or
 * light/dark mode the phone is in, so it does not read `MaterialTheme`. These are
 * the launcher icon's gradient (`ic_launcher_background.xml`) and `Teal40`, so the
 * card and the icon on it are the same brand rather than two.
 *
 * Packed literals rather than `Color.rgb`: these are top-level, so they initialise
 * this file's class, and `android.graphics` is a throwing stub in host-JVM tests —
 * one `Color.rgb` here takes the pure text builders' tests down with it.
 */
private val BrandAzure = 0xFF1E6FE3.toInt()
private val BrandDeep = 0xFF0A2E67.toInt()
private val BrandTeal = 0xFF0F8A7E.toInt()
private val BrandInk = 0xFF070B14.toInt()

/** Letterspaced, so it reads as a wordmark rather than as a sentence. */
private const val WORDMARK = "BEYOND BORDERS"

/**
 * The detail line: what was counted, without repeating the percentage that every
 * style already shows in a bigger size. "been", not "visited": a place lived in
 * must count here too, matching every other count this app shows.
 */
internal fun mapShareDetail(stats: TravelStats): String =
    "${stats.been} of ${stats.total} · " +
        "${stats.continentsTouched} ${pluralize(stats.continentsTouched, "continent")}"

/**
 * Short form for the stamp: percentage and fraction only.
 */
internal fun mapShareStamp(stats: TravelStats): String =
    "${stats.percent}% · ${stats.been}/${stats.total}"

/**
 * The three figures the stats card puts in tiles, as value-and-label pairs.
 *
 * The third tile earns its place: somewhere lived is the rarer and more
 * interesting fact, so it displaces the wish list whenever there is one.
 */
internal fun shareStatTiles(stats: TravelStats): List<Pair<String, String>> = listOf(
    "${stats.been}" to "PLACES BEEN",
    "${stats.continentsTouched}" to "CONTINENTS",
    if (stats.lived > 0) "${stats.lived}" to "LIVED IN" else "${stats.wishlist}" to "WISH LIST",
)

private fun pluralize(count: Int, singular: String): String =
    if (count == 1) singular else "${singular}s"

// --- brand furniture --------------------------------------------------------

/**
 * Draws the launcher icon as a rounded tile of [size] at [left], [top].
 *
 * The real icon rather than a hand-drawn copy, so the globe on a shared card can
 * never drift from the one on the home screen. An adaptive icon is 108 units wide
 * but only its middle 72 are guaranteed visible, so it is drawn 1.5× the tile and
 * centre-cropped — the same framing every launcher mask applies.
 */
private fun drawLogoTile(canvas: Canvas, logo: Drawable?, left: Float, top: Float, size: Float) {
    val radius = size * 0.28f
    val save = canvas.save()
    canvas.clipPath(
        Path().apply {
            addRoundRect(left, top, left + size, top + size, radius, radius, Path.Direction.CW)
        }
    )
    if (logo != null) {
        val bleed = size * 0.25f
        logo.setBounds(
            (left - bleed).toInt(),
            (top - bleed).toInt(),
            (left + size + bleed).toInt(),
            (top + size + bleed).toInt(),
        )
        logo.draw(canvas)
    } else {
        // The icon is in this APK, so null means a resource lookup failed rather
        // than an icon-less install; a brand-coloured tile keeps the layout intact.
        canvas.drawColor(BrandAzure)
    }
    canvas.restoreToCount(save)
}

private fun wordmarkPaint(textSize: Float, alpha: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(alpha, 255, 255, 255)
        this.textSize = textSize
        letterSpacing = 0.16f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

/** Heavier than [Typeface.DEFAULT_BOLD]; for the numbers that carry a card. */
private fun numberPaint(textSize: Float, alpha: Int = 255): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(alpha, 255, 255, 255)
        this.textSize = textSize
        typeface = Typeface.create(Typeface.SANS_SERIF, 800, false)
    }

private fun bodyPaint(textSize: Float, alpha: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(alpha, 255, 255, 255)
        this.textSize = textSize
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

/** Height of one line of [this] paint, ascent to descent. */
private fun Paint.lineHeight(): Float = fontMetrics.let { it.descent - it.ascent }

/**
 * Draws two lines of text stacked at [x], with the pair's centre at [centerY].
 *
 * Every style here is a small label over or under a big number, and each needs the
 * pair centred against something else — a logo tile, a stat tile — rather than
 * against the bitmap. Doing that per style got the second baseline wrong (a
 * baseline already includes its own ascent, so advancing by a whole line height
 * counts one ascent twice), which is why this is one function and not three.
 */
private fun drawTwoLines(
    canvas: Canvas,
    x: Float,
    centerY: Float,
    first: Pair<String, Paint>,
    second: Pair<String, Paint>,
    gap: Float = 0f,
) {
    val (topText, topPaint) = first
    val (bottomText, bottomPaint) = second
    val blockHeight = topPaint.lineHeight() + gap + bottomPaint.lineHeight()
    val topBaseline = centerY - blockHeight / 2f - topPaint.fontMetrics.ascent
    canvas.drawText(topText, x, topBaseline, topPaint)
    canvas.drawText(
        bottomText,
        x,
        topBaseline + topPaint.fontMetrics.descent + gap - bottomPaint.fontMetrics.ascent,
        bottomPaint,
    )
}

/**
 * Centre-aligned text is centred on its measured width, and [Paint.letterSpacing]
 * puts a gap after the last glyph too, so a letterspaced label centres visibly left
 * of where it should. Half that trailing gap, to add back.
 */
private fun Paint.letterSpacingNudge(): Float = textSize * letterSpacing / 2f

/**
 * A rounded track with the visited fraction filled in an azure-to-teal gradient —
 * the percentage said twice, once as a number and once as a length, so the image
 * carries its claim even at thumbnail size.
 */
private fun drawProgressBar(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    height: Float,
    percent: Int,
) {
    val radius = height / 2f
    canvas.drawRoundRect(
        left, top, right, top + height, radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(38, 255, 255, 255) },
    )
    if (percent <= 0) return
    // At least one round cap's worth of width, so a 1% mark is a visible dot
    // rather than a sliver the round rect swallows.
    val fillRight = (left + (right - left) * percent / 100f).coerceAtLeast(left + height)
    canvas.drawRoundRect(
        left, top, fillRight, top + height, radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, top, fillRight, top + height,
                BrandAzure, BrandTeal, Shader.TileMode.CLAMP,
            )
        },
    )
}

// --- map overlays -----------------------------------------------------------

/**
 * Burns a caption into [bitmap] using the chosen [style], returning a new bitmap.
 * A drawn-in caption travels with the image through any app that strips text
 * metadata or crops the share sheet's preview; overlaid chrome would not.
 */
private fun withCaption(
    bitmap: Bitmap,
    style: ShareCardStyle,
    stats: TravelStats,
    logo: Drawable?,
): Bitmap {
    val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    when (style) {
        ShareCardStyle.BAR -> drawCaptionBar(canvas, out.width, out.height, stats, logo)
        ShareCardStyle.STAMP -> drawCornerBadge(canvas, out.width, stats, logo)
        ShareCardStyle.STATS_ONLY -> Unit // Never wraps a map bitmap; see mapShareStatsCard.
    }
    return out
}

/**
 * The bottom caption: a gradient scrim, the icon and wordmark on the left, the
 * percentage on the right, and a hairline progress rule along the very edge.
 *
 * A gradient rather than the flat translucent band this used to draw — a hard edge
 * across a photograph looks like a bug, and the scrim has to darken any map colour
 * the snapshot happens to end on, including a pale ocean.
 */
private fun drawCaptionBar(
    canvas: Canvas,
    width: Int,
    height: Int,
    stats: TravelStats,
    logo: Drawable?,
) {
    val w = width.toFloat()
    val h = height.toFloat()
    val pad = w * 0.055f
    val tile = w * 0.115f
    val rule = (h * 0.005f).coerceAtLeast(3f)

    val scrimHeight = (tile + pad * 2 + rule).coerceAtLeast(h * 0.20f)
    canvas.drawRect(
        0f, h - scrimHeight, w, h,
        Paint().apply {
            shader = LinearGradient(
                0f, h - scrimHeight, 0f, h,
                // Near-opaque by the time the text starts (~0.58 of the way down): at the
                // old 0.45-stop of 140 a country label on the snapshot still showed
                // through behind the wordmark and the percentage.
                intArrayOf(
                    Color.TRANSPARENT, Color.argb(170, 4, 7, 14),
                    Color.argb(242, 4, 7, 14), Color.argb(250, 4, 7, 14),
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        },
    )

    val tileTop = h - rule - pad - tile
    drawLogoTile(canvas, logo, pad, tileTop, tile)
    val tileMid = tileTop + tile / 2f

    // The label goes above the number: right-aligned small caps read as a heading
    // for it, and the number then sits on the tile's own centre line.
    drawTwoLines(
        canvas,
        x = w - pad,
        centerY = tileMid,
        first = "OF THE WORLD" to wordmarkPaint(tile * 0.19f, 165)
            .apply { textAlign = Paint.Align.RIGHT },
        second = "${stats.percent}%" to numberPaint(tile * 0.56f)
            .apply { textAlign = Paint.Align.RIGHT },
        gap = tile * 0.04f,
    )

    drawTwoLines(
        canvas,
        x = pad + tile + pad * 0.55f,
        centerY = tileMid,
        first = WORDMARK to wordmarkPaint(tile * 0.30f, 255),
        second = mapShareDetail(stats) to bodyPaint(tile * 0.27f, 205),
        gap = tile * 0.05f,
    )

    drawProgressBar(canvas, 0f, h - rule, w, rule, stats.percent)
}

/**
 * The corner badge: icon, wordmark and the short stat in a rounded card sized to
 * its own text, for when the map itself is the point and the numbers are a note.
 */
private fun drawCornerBadge(canvas: Canvas, width: Int, stats: TravelStats, logo: Drawable?) {
    val w = width.toFloat()
    val inset = w * 0.04f
    val tile = w * 0.095f
    val padIn = tile * 0.32f

    val namePaint = wordmarkPaint(tile * 0.22f, 175)
    val valuePaint = numberPaint(tile * 0.36f)
    val valueText = mapShareStamp(stats)

    val textWidth = maxOf(namePaint.measureText(WORDMARK), valuePaint.measureText(valueText))
    val badgeWidth = padIn + tile + padIn * 0.75f + textWidth + padIn
    val badgeHeight = tile + padIn * 2
    canvas.drawRoundRect(
        inset, inset, inset + badgeWidth, inset + badgeHeight,
        badgeHeight * 0.30f, badgeHeight * 0.30f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 10, 14, 24) },
    )

    val tileTop = inset + padIn
    drawLogoTile(canvas, logo, inset + padIn, tileTop, tile)

    drawTwoLines(
        canvas,
        x = inset + padIn + tile + padIn * 0.75f,
        centerY = tileTop + tile / 2f,
        first = WORDMARK to namePaint,
        second = valueText to valuePaint,
        gap = tile * 0.06f,
    )
}

// --- stats-only card --------------------------------------------------------

/**
 * Fixed size for the stats-only card: there is no source bitmap to size against,
 * since this style carries no map at all, so 1080x1350 (a common share-sheet
 * portrait ratio) stands in for one.
 */
private const val STATS_CARD_WIDTH = 1080
private const val STATS_CARD_HEIGHT = 1350

/**
 * A standalone card of just the travel numbers, with no map behind them — for
 * sharing the claim without the geography.
 *
 * Laid out as a header, a hero percentage, a progress bar, three tiles and a
 * footer, flowing down from the top with the footer pinned to the bottom, so a
 * missing nudge or coverage line only changes the whitespace above it.
 */
internal fun mapShareStatsCard(stats: TravelStats, logo: Drawable? = null): Bitmap {
    val out = Bitmap.createBitmap(STATS_CARD_WIDTH, STATS_CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val w = out.width.toFloat()
    val h = out.height.toFloat()
    val pad = w * 0.085f
    val contentRight = w - pad

    canvas.drawColor(BrandInk)
    canvas.drawRect(
        0f, 0f, w, h,
        Paint().apply {
            shader = LinearGradient(0f, 0f, w, h, BrandDeep, BrandInk, Shader.TileMode.CLAMP)
        },
    )
    // A single off-centre glow: it gives the flat slab a light source, and sits
    // top-right where the hero text below it is not competing for contrast.
    canvas.drawRect(
        0f, 0f, w, h * 0.6f,
        Paint().apply {
            shader = RadialGradient(
                w * 0.82f, h * 0.06f, w * 0.85f,
                Color.argb(80, 0x1E, 0x6F, 0xE3), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
        },
    )

    val tile = w * 0.10f
    drawLogoTile(canvas, logo, pad, pad, tile)
    val headerPaint = wordmarkPaint(w * 0.033f, 220)
    canvas.drawText(
        WORDMARK,
        pad + tile + w * 0.035f,
        pad + tile / 2f - headerPaint.lineHeight() / 2f - headerPaint.fontMetrics.ascent,
        headerPaint,
    )

    val heroPaint = numberPaint(w * 0.26f)
    val heroSubPaint = bodyPaint(w * 0.048f, 185)
    var cursor = pad + tile + w * 0.10f
    canvas.drawText("${stats.percent}%", pad, cursor - heroPaint.fontMetrics.ascent, heroPaint)
    cursor += heroPaint.lineHeight()
    canvas.drawText("of the world", pad, cursor - heroSubPaint.fontMetrics.ascent, heroSubPaint)
    cursor += heroSubPaint.lineHeight() + w * 0.055f

    val barHeight = w * 0.018f
    drawProgressBar(canvas, pad, cursor, contentRight, barHeight, stats.percent)
    cursor += barHeight + w * 0.06f

    val tiles = shareStatTiles(stats)
    val gap = w * 0.025f
    val tileWidth = (contentRight - pad - gap * (tiles.size - 1)) / tiles.size
    val tileHeight = w * 0.185f
    val tileFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(20, 255, 255, 255) }
    val tileStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = w * 0.0018f
    }
    val valuePaint = numberPaint(w * 0.075f).apply { textAlign = Paint.Align.CENTER }
    val labelPaint = wordmarkPaint(w * 0.026f, 155).apply { textAlign = Paint.Align.CENTER }
    tiles.forEachIndexed { index, (value, label) ->
        val left = pad + index * (tileWidth + gap)
        val radius = w * 0.035f
        canvas.drawRoundRect(left, cursor, left + tileWidth, cursor + tileHeight, radius, radius, tileFill)
        canvas.drawRoundRect(left, cursor, left + tileWidth, cursor + tileHeight, radius, radius, tileStroke)
        drawTwoLines(
            canvas,
            x = left + tileWidth / 2f + labelPaint.letterSpacingNudge(),
            centerY = cursor + tileHeight / 2f,
            first = value to valuePaint,
            second = label to labelPaint,
            gap = w * 0.008f,
        )
    }

    val footer = listOfNotNull(stats.nudge, stats.coverage)
    if (footer.isNotEmpty()) {
        val footerPaint = bodyPaint(w * 0.034f, 155)
        val lineGap = footerPaint.lineHeight() * 1.25f
        val footerTop = h - pad - lineGap * footer.size
        canvas.drawLine(
            pad, footerTop - w * 0.045f, contentRight, footerTop - w * 0.045f,
            Paint().apply { color = Color.argb(28, 255, 255, 255) },
        )
        footer.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                pad,
                footerTop + lineGap * index - footerPaint.fontMetrics.ascent,
                footerPaint,
            )
        }
    }

    return out
}

// --- sharing ----------------------------------------------------------------

/**
 * The launcher icon, for the brand mark every style draws. Null only if the
 * lookup fails; [drawLogoTile] copes.
 */
private fun appLogo(context: Context): Drawable? =
    ContextCompat.getDrawable(context, R.mipmap.ic_launcher)

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
    val captioned = withCaption(bitmap, style, stats, appLogo(context))
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
    shareBitmap(context, mapShareStatsCard(stats, appLogo(context)))
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
        putExtra(Intent.EXTRA_TITLE, "Beyond Borders")
        // The Sharesheet's image preview reads clipData, not the EXTRA_STREAM
        // extra alone — without it several targets fall back to a generic file
        // icon instead of the actual thumbnail.
        clipData = ClipData.newUri(context.contentResolver, "Beyond Borders", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
