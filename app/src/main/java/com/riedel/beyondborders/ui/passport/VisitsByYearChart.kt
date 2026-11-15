package com.riedel.beyondborders.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.sp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.ExpandableHeader
import com.riedel.beyondborders.ui.places.VisitStats
import com.riedel.beyondborders.ui.places.YearPoint

private val PLOT_HEIGHT = 108.dp
private val MIN_SLOT = 32.dp
private val AXIS_GAP = 8.dp

/**
 * The "Visits by year" section: bars for the places visited each year, a line for the running
 * total of places first visited, a legend, per-year numbers for screen readers (and anyone who
 * wants exact figures), and a footnote for places with no year.
 *
 * With many years the plot scrolls sideways (opened at the latest year) instead of squeezing
 * bars; the y axis stays put at the left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VisitsByYearChart(stats: VisitStats, modifier: Modifier = Modifier) {
    val points = stats.yearPoints
    var showNumbers by remember { mutableStateOf(false) }
    // Collapsed by default: the chart is a lot of screen for a figure most people look at now and then.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        ExpandableHeader(stringResource(R.string.stats_visits_title), expanded, { expanded = !expanded })
        AnimatedVisibility(visible = expanded) {
        Column {
        if (points.isNotEmpty()) {
            val summary = stringResource(
                R.string.stats_chart_summary, points.first().year, points.last().year, points.last().cumulative,
            )
            Column(Modifier.clearAndSetSemantics { contentDescription = summary }) {
                YearChart(points, Modifier.padding(top = 12.dp))
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LegendEntry(MaterialTheme.colorScheme.primary, stringResource(R.string.stats_chart_countries), line = false)
                    LegendEntry(MaterialTheme.colorScheme.tertiary, stringResource(R.string.stats_chart_cumulative), line = true)
                }
            }
            TextButton(onClick = { showNumbers = !showNumbers }) {
                Text(stringResource(if (showNumbers) R.string.stats_chart_details_hide else R.string.stats_chart_details_show))
            }
            if (showNumbers) {
                Column {
                    // Newest first: the recent years are what one wants to read.
                    points.asReversed().forEach { p ->
                        Text(
                            stringResource(R.string.stats_chart_year_detail, p.year, p.countries, p.newCountries, p.cumulative),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
        val first = stats.first
        val recent = stats.mostRecent
        if (first != null && recent != null) {
            Text(
                stringResource(R.string.stats_visits_first_recent, first, recent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (stats.undatedPlaces > 0) {
            Text(
                pluralStringResource(R.plurals.stats_chart_no_year, stats.undatedPlaces, stats.undatedPlaces),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        }
        }
    }
}

@Composable
private fun LegendEntry(color: Color, label: String, line: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
            if (line) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color, 3.dp.toPx(), Offset(size.width / 2, size.height / 2))
            } else {
                drawRoundRect(color, Offset(size.width * 0.2f, 0f), Size(size.width * 0.6f, size.height), CornerRadius(2.dp.toPx()))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun YearChart(points: List<YearPoint>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val barColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val axis = remember(points) { niceAxis(points.maxOf { maxOf(it.countries, it.cumulative) }) }

    // sp-sized labels grow with the font scale, so the reserved gutters are measured, not guessed.
    val yLabelWidthPx = measurer.measure(axis.top.toString(), labelStyle).size.width
    val yLabelHeightPx = measurer.measure("0", labelStyle).size.height
    val xLabelWidthPx = measurer.measure("0000", labelStyle).size.width
    val axisWidth = with(density) { yLabelWidthPx.toDp() } + AXIS_GAP
    val xLabelBand = with(density) { yLabelHeightPx.toDp() } + 6.dp
    // Half a label of headroom so the top gridline's label is never clipped.
    val topPad = with(density) { (yLabelHeightPx / 2).toDp() } + 1.dp
    val totalHeight = topPad + PLOT_HEIGHT + xLabelBand

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth - axisWidth
        val slot = maxOf(MIN_SLOT, available / points.size)
        val scrolls = slot * points.size > available + 0.5.dp
        val scroll = rememberScrollState()
        LaunchedEffect(points.size, scrolls, scroll.maxValue) { if (scrolls) scroll.scrollTo(scroll.maxValue) }

        Row {
            Canvas(Modifier.width(axisWidth).height(totalHeight)) {
                val top = topPad.toPx()
                val h = PLOT_HEIGHT.toPx()
                axis.ticks().forEach { v ->
                    val y = top + h * (1f - v.toFloat() / axis.top)
                    val text = measurer.measure(v.toString(), labelStyle)
                    drawText(text, topLeft = Offset(size.width - AXIS_GAP.toPx() - text.size.width, y - text.size.height / 2f))
                }
            }
            val plot = if (scrolls) Modifier.horizontalScroll(scroll) else Modifier
            // A scrolling plot lives inside a viewport of the remaining width; its canvas is as wide as all slots.
            Row(Modifier.width(available).then(plot)) {
                Canvas(Modifier.width(if (scrolls) slot * points.size else available).height(totalHeight)) {
                    val top = topPad.toPx()
                    val h = PLOT_HEIGHT.toPx()
                    val slotPx = size.width / points.size
                    axis.ticks().forEach { v ->
                        val y = top + h * (1f - v.toFloat() / axis.top)
                        // Faint dashes for the guides, a solid hairline only at zero: lighter than a full grid.
                        drawLine(
                            gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                            pathEffect = if (v == 0) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        )
                    }
                    val barW = minOf(slotPx * 0.45f, 12.dp.toPx())
                    val r = CornerRadius(barW / 2)
                    points.forEachIndexed { i, p ->
                        if (p.countries > 0) {
                            val bh = h * p.countries / axis.top
                            val x = slotPx * i + (slotPx - barW) / 2
                            val path = Path().apply {
                                addRoundRect(RoundRect(x, top + h - bh, x + barW, top + h, r, r, CornerRadius.Zero, CornerRadius.Zero))
                            }
                            drawPath(path, barColor.copy(alpha = 0.85f))
                        }
                    }
                    val line = Path()
                    val centres = points.mapIndexed { i, p ->
                        Offset(slotPx * i + slotPx / 2, top + h * (1f - p.cumulative.toFloat() / axis.top))
                    }
                    centres.forEachIndexed { i, o -> if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y) }
                    drawPath(line, lineColor, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Only the latest total gets a marker; a dot on every year was noise.
                    centres.lastOrNull()?.let { drawCircle(lineColor, 3.5.dp.toPx(), it) }

                    // Labels at every k-th slot counted back from the latest year, so that one is always shown.
                    val step = labelStep(slotPx, xLabelWidthPx + 4.dp.toPx())
                    points.forEachIndexed { i, p ->
                        if ((points.size - 1 - i) % step == 0) {
                            val text = measurer.measure(p.year.toString(), labelStyle)
                            val x = (slotPx * i + slotPx / 2 - text.size.width / 2f).coerceIn(0f, maxOf(0f, size.width - text.size.width))
                            drawText(text, topLeft = Offset(x, top + h + 6.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}

/** Y axis: [top] is the value at the top gridline, [step] the distance between gridlines. */
internal data class ChartAxis(val top: Int, val step: Int) {
    fun ticks(): List<Int> = (0..top step step).toList()
}

/** A round axis top with at most four gridlines above zero, always at least 1. */
internal fun niceAxis(max: Int): ChartAxis {
    val m = max.coerceAtLeast(1)
    val rough = (m + 3) / 4
    var magnitude = 1
    while (magnitude * 10 <= rough) magnitude *= 10
    val step = listOf(1, 2, 5, 10).map { it * magnitude }.first { it >= rough }
    val top = ((m + step - 1) / step) * step
    return ChartAxis(top, step)
}

/** Every how-manyth slot gets a label so that labels of [labelPx] never overlap in slots of [slotPx]. */
internal fun labelStep(slotPx: Float, labelPx: Float): Int =
    kotlin.math.ceil(labelPx / slotPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
