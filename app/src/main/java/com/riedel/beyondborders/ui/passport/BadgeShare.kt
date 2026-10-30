package com.riedel.beyondborders.ui.passport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.UiText
import com.riedel.beyondborders.ui.map.BrandDeep
import com.riedel.beyondborders.ui.map.BrandInk
import com.riedel.beyondborders.ui.map.WORDMARK
import com.riedel.beyondborders.ui.map.appLogo
import com.riedel.beyondborders.ui.map.bodyPaint
import com.riedel.beyondborders.ui.map.drawLogoTile
import com.riedel.beyondborders.ui.map.fitToWidth
import com.riedel.beyondborders.ui.map.lineHeight
import com.riedel.beyondborders.ui.map.numberPaint
import com.riedel.beyondborders.ui.map.shareBitmap
import com.riedel.beyondborders.ui.map.wordmarkPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BADGE_CARD_SIZE = 1080

/** 9:21, the tallest phone aspect; a freeform window can report anything. */
private const val BADGE_CARD_MAX_HEIGHT = 2520

/**
 * The card's height for a screen of [screenWidthPx] x [screenHeightPx]: the screen's own
 * aspect at the card's width, so it is the same shape as the map snapshot. A square card
 * posted to a full-screen target (a story, a status) gets black bars; never shorter than
 * square nor taller than 9:21, so an odd window cannot make a landscape or a 30 MB card.
 */
internal fun badgeCardHeight(screenWidthPx: Int, screenHeightPx: Int): Int {
    if (screenWidthPx <= 0) return BADGE_CARD_SIZE
    val scaled = BADGE_CARD_SIZE.toLong() * screenHeightPx / screenWidthPx
    return scaled.coerceIn(BADGE_CARD_SIZE.toLong(), BADGE_CARD_MAX_HEIGHT.toLong()).toInt()
}

// --- text -------------------------------------------------------------------

/** "Explorer · 42 places": the rank and the count it was earned with. */
internal fun badgeRankLine(placesVisited: Int): UiText = UiText.Res(
    R.string.badge_rank_line,
    rankFor(placesVisited).title,
    UiText.Plural(R.plurals.badge_places_count, placesVisited, placesVisited),
)

/** "3 of 10" while locked; a finished badge just says so. */
internal fun badgeProgressText(achievement: Achievement): UiText =
    if (achievement.unlocked) {
        UiText.Res(R.string.badge_unlocked)
    } else {
        UiText.Res(R.string.badge_progress_of, achievement.progress, achievement.target)
    }

/** The text that travels with the image, for targets that show a message beside it. */
internal fun badgeShareCaption(achievement: Achievement): UiText =
    UiText.Res(R.string.badge_share_caption, achievement.title, achievement.description)

// --- card -------------------------------------------------------------------

/**
 * Greedy word wrap of [text] to [maxWidth], at most [maxLines] lines; whatever does
 * not fit is cut with an ellipsis on the last line, so nothing is ever drawn past the
 * edge. A single word wider than the line is broken by [Paint.breakText].
 */
private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
    val lines = ArrayList<String>()
    var rest = text.trim()
    while (rest.isNotEmpty() && lines.size < maxLines) {
        val fits = paint.breakText(rest, true, maxWidth, null)
        if (fits >= rest.length) {
            lines += rest
            rest = ""
            break
        }
        val lastLine = lines.size == maxLines - 1
        if (lastLine) {
            val ellipsis = "…"
            val room = (maxWidth - paint.measureText(ellipsis)).coerceAtLeast(0f)
            val count = paint.breakText(rest, true, room, null)
            lines += rest.take(count).trimEnd() + ellipsis
            rest = ""
            break
        }
        // Break at the last space that fits; a word longer than the line is cut where it overflows.
        val space = rest.lastIndexOf(' ', fits)
        val cut = if (space > 0) space else fits.coerceAtLeast(1)
        lines += rest.take(cut).trimEnd()
        rest = rest.drop(cut).trimStart()
    }
    return lines
}

/**
 * A card for one unlocked badge, [BADGE_CARD_SIZE] wide and [heightPx] tall: brand header,
 * the group's icon on a circle in its colour, title, description, and the traveller rank
 * along the bottom.
 *
 * Like the stats card it is a fixed palette and not themed, so the circle uses the
 * group's dark-theme accent (the bright one) on the brand's dark ground. The block
 * between header and footer is centred in the space it has, so a short title does not
 * leave the card top-heavy; long text wraps, then ellipsizes, never clips.
 */
internal fun badgeShareCard(
    context: Context,
    achievement: Achievement,
    placesVisited: Int,
    icon: Drawable?,
    logo: Drawable? = null,
    heightPx: Int = BADGE_CARD_SIZE,
): Bitmap {
    val out = Bitmap.createBitmap(BADGE_CARD_SIZE, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val w = out.width.toFloat()
    val h = out.height.toFloat()
    val pad = w * 0.085f
    val textWidth = w - 2 * pad
    val tone = badgeTone(achievement.group)

    canvas.drawRect(
        0f, 0f, w, h,
        Paint().apply {
            shader = LinearGradient(0f, 0f, w, h, BrandDeep, BrandInk, Shader.TileMode.CLAMP)
        },
    )
    val tile = w * 0.10f
    val rankPaint = bodyPaint(w * 0.040f, 200).apply { textAlign = Paint.Align.CENTER }
    val footerTop = h - pad - rankPaint.lineHeight() - w * 0.04f

    // Centre block: circle, title, description, centred between header and footer.
    val circle = w * 0.27f
    val titlePaint = numberPaint(w * 0.068f).apply { textAlign = Paint.Align.CENTER }
    val descPaint = bodyPaint(w * 0.037f, 205).apply { textAlign = Paint.Align.CENTER }
    val titleLines = wrapText(achievement.title.asString(context), titlePaint, textWidth, 2)
    val descLines = wrapText(achievement.description.asString(context), descPaint, textWidth, 3)
    val gapCircle = w * 0.05f
    val gapTitle = w * 0.025f
    val titleLH = titlePaint.lineHeight() * 1.0f
    val descLH = descPaint.lineHeight() * 1.1f
    val blockHeight = circle + gapCircle + titleLH * titleLines.size + gapTitle + descLH * descLines.size
    val headerBottom = pad + tile
    val available = footerTop - headerBottom
    var cursor = headerBottom + ((available - blockHeight) / 2f).coerceAtLeast(w * 0.02f)
    val cx = w / 2f
    val cy = cursor + circle / 2f

    // The group's own colour as the light source, so each badge's card has its mood.
    // Centred on the circle: the card's height follows the screen, so a fixed fraction
    // of it drifts off the badge on a tall card.
    canvas.drawRect(
        0f, 0f, w, h,
        Paint().apply {
            shader = RadialGradient(
                cx, cy, w * 0.7f,
                (tone.accentDark and 0x00FFFFFF) or (70 shl 24), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
        },
    )

    drawLogoTile(canvas, logo, pad, pad, tile)
    val headerPaint = wordmarkPaint(w * 0.033f, 220)
    canvas.drawText(
        WORDMARK,
        pad + tile + w * 0.035f,
        pad + tile / 2f - headerPaint.lineHeight() / 2f - headerPaint.fontMetrics.ascent,
        headerPaint,
    )

    // Footer: a hairline and the rank line, pinned to the bottom.
    val rankText = fitToWidth(badgeRankLine(placesVisited).asString(context), rankPaint, textWidth)
    canvas.drawLine(pad, footerTop, w - pad, footerTop, Paint().apply { color = Color.argb(28, 255, 255, 255) })
    canvas.drawText(
        rankText, w / 2f, h - pad - rankPaint.fontMetrics.descent, rankPaint,
    )

    canvas.drawCircle(
        cx, cy, circle / 2f + w * 0.018f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (tone.accentDark and 0x00FFFFFF) or (90 shl 24)
            style = Paint.Style.STROKE
            strokeWidth = w * 0.006f
        },
    )
    canvas.drawCircle(cx, cy, circle / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone.accentDark })
    if (icon != null) {
        val glyph = circle * 0.52f
        icon.mutate().setTint(GlyphOnAccentDark)
        icon.setBounds(
            (cx - glyph / 2f).toInt(), (cy - glyph / 2f).toInt(),
            (cx + glyph / 2f).toInt(), (cy + glyph / 2f).toInt(),
        )
        icon.draw(canvas)
    }
    cursor += circle + gapCircle

    titleLines.forEach { line ->
        canvas.drawText(line, cx, cursor - titlePaint.fontMetrics.ascent, titlePaint)
        cursor += titleLH
    }
    cursor += gapTitle
    descLines.forEach { line ->
        canvas.drawText(line, cx, cursor - descPaint.fontMetrics.ascent, descPaint)
        cursor += descLH
    }
    return out
}

/** Renders the card for [achievement] and opens the share sheet on it. */
internal suspend fun shareBadge(context: Context, achievement: Achievement, placesVisited: Int) {
    val card = withContext(Dispatchers.Default) {
        val icon = ContextCompat.getDrawable(context, iconFor(achievement.group))
        val screen = context.resources.displayMetrics
        badgeShareCard(
            context, achievement, placesVisited, icon, appLogo(context),
            badgeCardHeight(screen.widthPixels, screen.heightPixels),
        )
    }
    shareBitmap(context, card, badgeShareCaption(achievement).asString(context))
}
