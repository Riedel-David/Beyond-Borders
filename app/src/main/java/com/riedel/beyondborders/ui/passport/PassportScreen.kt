package com.riedel.beyondborders.ui.passport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.ui.BounceFilterChip
import com.riedel.beyondborders.ui.places.Grouping
import com.riedel.beyondborders.ui.places.PlaceRow
import com.riedel.beyondborders.ui.places.PassportMode
import com.riedel.beyondborders.ui.places.PlacesViewModel
import com.riedel.beyondborders.ui.places.StatusFilter
import com.riedel.beyondborders.ui.places.TravelStats
import com.riedel.beyondborders.ui.places.sectionHeader
import com.riedel.beyondborders.ui.places.visaFilters

/**
 * The passport: every place, searchable, with the counts that make the tracking
 * feel like it is going somewhere.
 *
 * Each row carries two independent toggles rather than one cycling control. Bulk
 * entry (fifty countries on first run) is the dominant use, and "tap Been on the
 * row" is one unambiguous gesture where a cycling control would need two taps and
 * a moment of thought.
 *
 * Two modes share the screen — where I have been, and where this passport lets me go.
 * They are the same 252 places under two questions, and the search box is shared on
 * purpose: looking up Greenland and then asking what it wants should not mean typing
 * it twice.
 */
@Composable
fun PassportScreen(viewModel: PlacesViewModel, modifier: Modifier = Modifier) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    // The stats detail popup needs a name and a status per code in a bucket, and
    // neither lives on [ContinentStat]/[RegionStat]/[NamedGroupStat] — just the
    // code set. Looked up against the full registry rather than [rows], which is
    // already filtered by query/status and would drop codes the popup still needs.
    val places by viewModel.places.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val grouping by viewModel.grouping.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val visa by viewModel.visa.collectAsStateWithLifecycle()
    val visaRows by viewModel.visaRows.collectAsStateWithLifecycle()
    val visaFilter by viewModel.visaFilter.collectAsStateWithLifecycle()
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()
    val wishlistEasyLine by viewModel.wishlistEasyLine.collectAsStateWithLifecycle()
    // Derived from an asset that is read once, so a flow would be a subscription to
    // something that never changes twice.
    val filters = remember(visa) { visaFilters(visa) }
    // Persisted, and prefixed with the grouping's name, so a header collapsed by
    // continent survives closing the app but does not carry over, by coincidence
    // of the same string, into region or A-Z grouping.
    val collapsedHeaders by viewModel.collapsedHeaders.collectAsStateWithLifecycle()

    // Transient: which continent/region a place belongs to never needs to survive
    // a process death the way a preference would, so this is rememberSaveable
    // rather than anything in SettingsStore.
    var showStatsDetail by rememberSaveable { mutableStateOf(false) }
    if (showStatsDetail) {
        // Not a real back-stack entry — just this boolean — so without this the
        // system back gesture has nothing here to intercept and falls through to
        // exiting the app instead of closing the detail screen.
        BackHandler { showStatsDetail = false }
        StatsDetailScreen(
            stats = stats,
            places = places,
            statuses = statuses,
            onBack = { showStatsDetail = false },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item("mode") {
            ModeToggle(
                mode = mode,
                // Offered only where it can be honoured: with no visa asset the
                // second mode has nothing to say, and a toggle that lands on an
                // apology is worse than one tab's worth of function.
                visaAvailable = visa != null,
                onMode = viewModel::setMode,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }

        if (mode == PassportMode.VISA) {
            visaSection(
                visa = visa,
                rows = visaRows,
                filter = visaFilter,
                filters = filters,
                query = query,
                // The same selection the map's card uses. Sharing it is what makes a
                // row you opened here the country still selected when you switch to
                // the map, rather than two ideas of "the place I am looking at".
                selectedCode = selectedCode,
                onQuery = viewModel::setQuery,
                onFilter = viewModel::setVisaFilter,
                onSelect = viewModel::select,
                nameOf = { code -> viewModel.placeOf(code)?.name },
            )
            return@LazyColumn
        }

        item("stats") {
            StatsCard(
                stats = stats,
                wishlistEasyLine = wishlistEasyLine,
                onClick = { showStatsDetail = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        item("controls") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SearchField(
                    query = query,
                    onQuery = viewModel::setQuery,
                    label = "Search places",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                // Five filters do not fit across a 360 dp phone, so this row
                // scrolls, as the visa mode's does: the chips are a single flat set
                // where a second wrapped line would imply a grouping that isn't
                // there, and "All" staying put on the left is what makes the
                // overflow discoverable.
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filter == statusFilter,
                            onClick = { viewModel.setStatusFilter(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = grouping == Grouping.CONTINENT,
                        onClick = { viewModel.setGrouping(Grouping.CONTINENT) },
                        label = { Text("By continent") },
                    )
                    FilterChip(
                        selected = grouping == Grouping.REGION,
                        onClick = { viewModel.setGrouping(Grouping.REGION) },
                        label = { Text("By region") },
                    )
                    FilterChip(
                        selected = grouping == Grouping.ALPHABETICAL,
                        onClick = { viewModel.setGrouping(Grouping.ALPHABETICAL) },
                        label = { Text("A-Z") },
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            item("empty") {
                Text(
                    "Nothing matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        // Headers are emitted inline rather than with stickyHeader: the list is
        // 252 rows at most, so there is nothing to gain and stickyHeader is still
        // an opt-in experimental API.
        //
        // A search that matches something inside a collapsed section would hide
        // its own result, so a query in progress ignores the collapse state
        // entirely rather than making the user remember to expand first.
        val effectiveCollapsed = if (query.isBlank()) collapsedHeaders else emptySet()
        var lastHeader: String? = null
        rows.forEach { row ->
            // Derived next to the sort rather than here: the header letter and the
            // ordering have to be the same answer, and the key has to stay unique.
            val header = sectionHeader(row.place, grouping)
            val collapseKey = "${grouping.name}:$header"
            if (header != lastHeader) {
                lastHeader = header
                item("header-$header") {
                    SectionHeader(
                        text = header,
                        collapsed = collapseKey in effectiveCollapsed,
                        onClick = { viewModel.toggleHeaderCollapsed(grouping, header) },
                    )
                }
            }
            if (collapseKey in effectiveCollapsed) return@forEach
            item(row.place.code) {
                PlaceListRow(
                    row = row,
                    onToggle = { target ->
                        viewModel.vibrate()
                        viewModel.toggle(row.place.code, target)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * The one search box both modes use.
 *
 * Shared state, not just a shared look: typing "greenland" and then asking what it
 * wants should not mean typing it again. Only the label changes, because the two modes
 * are searching the same 252 places for different reasons.
 */
@Composable
internal fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The two questions the screen answers, as one control at the top.
 *
 * A segmented button rather than the filter chips the rest of the screen uses: the
 * chips narrow a list, this changes what the list is *about*, and using the same
 * component for both would make the more consequential control the quieter one.
 */
@Composable
private fun ModeToggle(
    mode: PassportMode,
    visaAvailable: Boolean,
    onMode: (PassportMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visaAvailable) return
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        PassportMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { onMode(option) },
                shape = SegmentedButtonDefaults.itemShape(index, PassportMode.entries.size),
                // No check mark. Its 26 dp is the difference between "Where I've been"
                // fitting and eliding on a 360 dp phone, and with two segments the
                // selected one is already obvious from its fill.
                icon = {},
                label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, collapsed: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        // A glyph rather than an icon: this row already carries no icon
        // dependency, and ▸/▾ read as a disclosure triangle either way.
        Text(
            text = if (collapsed) "▸" else "▾",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PlaceListRow(row: PlaceRow, onToggle: (PlaceStatus) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.place.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                // Sovereign is shown only where it adds information: "Greenland
                // (Denmark)" is useful, "France (France)" is noise.
                if (row.place.sovereign != row.place.name) {
                    "${row.place.code} · ${row.place.sovereign}"
                } else {
                    row.place.code
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Three chips take enough width that a long name now wraps to a second line.
        // Accepted deliberately: bulk entry happens here, so every status has to be
        // one tap away on this screen, and a hidden third option would push the
        // rarest answer onto the map only.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BounceFilterChip(
                selected = row.status == PlaceStatus.VISITED,
                label = "Been",
                onClick = { onToggle(PlaceStatus.VISITED) },
            )
            BounceFilterChip(
                selected = row.status == PlaceStatus.LIVED,
                label = "Lived",
                onClick = { onToggle(PlaceStatus.LIVED) },
            )
            BounceFilterChip(
                selected = row.status == PlaceStatus.WISHLIST,
                label = "Want",
                onClick = { onToggle(PlaceStatus.WISHLIST) },
            )
        }
    }
}

@Composable
private fun StatsCard(
    stats: TravelStats,
    wishlistEasyLine: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    // been, not visited: a place you have lived in is a place you
                    // have been, so promoting one must never shrink this number.
                    "${stats.been}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "  of ${stats.total} places · ${stats.percent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                buildString {
                    append("${stats.continentsTouched} of ${stats.continents.size} continents")
                    // Each clause appears only when it has something to say, so the
                    // line stays short for someone who only ever taps "Been".
                    if (stats.lived > 0) append(" · ${stats.lived} lived in")
                    if (stats.wishlist > 0) append(" · ${stats.wishlist} on the wishlist")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Two lines that only appear once they have something worth saying:
            // the nudge needs a continent partway done, the join needs a wishlist.
            if (stats.coverage != null) {
                Text(
                    stats.coverage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (stats.nudge != null) {
                Text(
                    stats.nudge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (wishlistEasyLine != null) {
                Text(
                    wishlistEasyLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // The continent/region grids live one tap away in StatsDetailScreen —
            // gamification hidden behind the headline number, not in front of it.
            Text(
                "Tap for continents, regions, and more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** One bar's worth of a completion grid, [codes] carried along so a long press
 * can look up which places it covers without re-deriving membership from [Place]. */
private data class Bucket(val label: String, val been: Int, val total: Int, val codes: Set<String>)

/**
 * One tap away from the passport list's headline number: every completion grid
 * that would otherwise crowd the front page — continents, then the formal UN M49
 * regions, then the informal named groupings people actually say out loud.
 *
 * A plain scrolling [Column] rather than [LazyColumn]: even all three sections
 * together are around 45 short rows, nowhere near where laziness would matter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsDetailScreen(
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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

    detail?.let { bucket ->
        BucketDetailDialog(bucket, places, statuses, onDismiss = { detail = null })
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
