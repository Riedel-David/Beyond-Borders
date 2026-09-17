package com.riedel.beyondborders.ui.passport

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.ui.places.TravelStats
import com.riedel.beyondborders.ui.places.leastCoveredContinent

/** One-line tweakable: the ring is drawn at this size regardless of how the rest
 * of the screen reflows, so a phone-in-hand adjustment is a single number here. */
private val RingDiameter = 168.dp
private val RingStrokeWidth = 14.dp

/** One bar's worth of a completion grid, [codes] carried along so a long press
 * can look up which places it covers without re-deriving membership from [Place]. */
private data class Bucket(val label: String, val been: Int, val total: Int, val codes: Set<String>)

/**
 * One tap away from the passport list's headline number. A hero ring answers the
 * one question the screen is about — how much of the world, of the total — and
 * everything else here is progressively disclosed underneath it: continents stay
 * visible because seven rows fit, the two least/most "worth a line" facts get a
 * line each, and the full regions/named-groupings breakdown is a tap away rather
 * than forty-five thin bars competing for the same glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    stats: TravelStats,
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which bar is long-pressed, if any. Plain remember rather than rememberSaveable:
    // a Set<String> is not Parcelable-friendly, and a popup that closes on rotation
    // is not a loss worth designing around.
    var detail by remember { mutableStateOf<Bucket?>(null) }
    // Collapsed by default: regions and named groupings are the forty-five bars
    // nobody scans on arrival. Plain remember, not rememberSaveable — reopening
    // the screen collapsed again is not worth a process-death survivor.
    var showAllGroupings by remember { mutableStateOf(false) }

    // Zero insets throughout: this screen is drawn inside the root Scaffold's own
    // content padding (BeyondBordersRoot pads the passport tab before it composes),
    // so a Scaffold and a TopAppBar left to their defaults would each add the status
    // bar a second time and push the title a full bar's height down the screen.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            StatsRing(stats, Modifier.fillMaxWidth().padding(vertical = 16.dp))

            Text(
                "Hold a bar to see which places are still missing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompletionGrid(
                "Continents",
                stats.continents.map { Bucket(it.continent, it.been, it.total, it.codes) },
                onHold = { detail = it },
            )

            val leastCovered = remember(stats.continents) { leastCoveredContinent(stats.continents) }
            if (leastCovered != null) {
                LabeledStat(
                    label = "Least covered",
                    value = leastCovered,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            // stats.nudge is already computed on TravelStats — recomputing it here
            // from continents would be the same string built twice.
            if (stats.nudge != null) {
                LabeledStat(
                    label = "Next up",
                    value = stats.nudge!!,
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (leastCovered != null) 12.dp else 16.dp),
                )
            }

            ShowAllGroupingsRow(
                expanded = showAllGroupings,
                onToggle = { showAllGroupings = !showAllGroupings },
                modifier = Modifier.padding(top = 16.dp),
            )
            // Only the size changes here, not the content underneath it — the two
            // grids are always the same composables, just present or absent — so
            // animateContentSize is the right tool. A crossfade would be wrong for
            // a content swap, but nothing here swaps.
            Column(Modifier.animateContentSize()) {
                if (showAllGroupings) {
                    CompletionGrid(
                        "Regions",
                        stats.regions.map { Bucket(it.region, it.been, it.total, it.codes) },
                        onHold = { detail = it },
                    )
                    CompletionGrid(
                        "Named groupings",
                        stats.informalRegions.map { Bucket(it.name, it.been, it.total, it.codes) },
                        onHold = { detail = it },
                    )
                }
            }
        }
    }

    detail?.let { bucket ->
        BucketDetailDialog(bucket, places, statuses, onDismiss = { detail = null })
    }
}

/** The ring: a full track plus a [TravelStats.percent]-sweep arc starting at twelve
 * o'clock, with the percentage centred inside it and the raw count underneath. */
@Composable
private fun StatsRing(stats: TravelStats, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            // One sentence for TalkBack rather than the ring being colour-only:
            // clearAndSetSemantics so the Canvas underneath isn't also announced.
            .clearAndSetSemantics {
                contentDescription =
                    "${stats.percent} percent of places visited, ${stats.been} of ${stats.total}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(RingDiameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = RingStrokeWidth.toPx()
                val diameter = size.minDimension - strokePx
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * stats.percent / 100f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
            Text(
                "${stats.percent}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "${stats.been} of ${stats.total} places",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A quiet section label with its value beneath it in the stronger style — the
 * shape "Least covered" and "Next up" share, so a pair with nothing to show
 * (the null case) never renders a label over a blank line. */
@Composable
private fun LabeledStat(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** The row that reveals regions and named groupings. A chevron rather than words
 * that change, since "Show" vs. "Hide" is the same information the rotation
 * already gives, twice. */
@Composable
private fun ShowAllGroupingsRow(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Show all groupings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun CompletionGrid(title: String, bars: List<Bucket>, onHold: (Bucket) -> Unit) {
    if (bars.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    // Two columns, same reasoning as the old continent grid: half the rows means
    // the section does not dominate the screen it now has entirely to itself.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bars.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { bucket -> CompletionBar(bucket, onHold, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletionBar(bucket: Bucket, onHold: (Bucket) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.combinedClickable(onClick = {}, onLongClick = { onHold(bucket) }),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row {
            Text(
                bucket.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                " ${bucket.been}/${bucket.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Drawn by hand rather than with LinearProgressIndicator so it can be a
        // thin flush bar without fighting the component's built-in padding.
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            val fraction = if (bucket.total == 0) 0f else bucket.been.toFloat() / bucket.total
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/** What a long press on a bar answers: which of its places you have already been
 * to, and which are still missing — the two lists a bar's fraction cannot show. */
@Composable
private fun BucketDetailDialog(
    bucket: Bucket,
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
    onDismiss: () -> Unit,
) {
    val members = remember(bucket, places) {
        bucket.codes.mapNotNull { code -> places.firstOrNull { it.code == code } }
            .sortedBy { it.sortKey }
    }
    val (been, missing) = members.partition { statuses[it.code]?.isBeen == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(bucket.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (been.isNotEmpty()) {
                    Text(
                        "Been (${been.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    been.forEach { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
                }
                if (missing.isNotEmpty()) {
                    Text(
                        "Still to go (${missing.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (been.isNotEmpty()) 12.dp else 0.dp),
                    )
                    missing.forEach { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
