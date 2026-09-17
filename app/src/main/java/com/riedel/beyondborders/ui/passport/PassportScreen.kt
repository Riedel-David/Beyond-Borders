package com.riedel.beyondborders.ui.passport

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
@OptIn(ExperimentalFoundationApi::class)
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
    val secondVisa by viewModel.secondVisa.collectAsStateWithLifecycle()
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
        StatsScreen(
            stats = stats,
            places = places,
            statuses = statuses,
            onBack = { showStatsDetail = false },
            modifier = modifier,
        )
        return
    }

    // Shared by both modes, since the fast-scroll overlay below reads from whichever
    // one is on screen: a rememberSaveable would restore a scroll offset that no
    // longer matches the rows after a mode switch, so this is deliberately plain.
    val listState = rememberLazyListState()
    // One entry per item this LazyColumn actually emits, in emission order, so a raw
    // firstVisibleItemIndex can be turned back into "which header is this" for the
    // overlay. Built to mirror the two item-emission loops below exactly, including
    // the leading non-header items (which read as null) and, for the travel list,
    // the collapse skip.
    // Remembered, not rebuilt per composition: this walks all 252 rows calling
    // sectionHeader on each, which is not something to redo while a finger is down.
    val headerSlots = remember(mode, visa == null, visaRows, rows, grouping, collapsedHeaders, query) {
        if (mode == PassportMode.VISA) {
            buildList {
                add(null) // "mode"
                if (visa == null) {
                    add(null) // "visa-missing"
                } else {
                    add(null) // "visa-summary"
                    add(null) // "visa-search"
                    add(null) // "visa-filters"
                    if (visaRows.isEmpty()) add(null) // "visa-empty"
                    addAll(visaHeaderSlots(visaRows))
                }
            }
        } else {
            buildList {
                add(null) // "mode"
                add(null) // "stats"
                add(null) // "controls"
                if (rows.isEmpty()) add(null) // "empty"
                addAll(travelHeaderSlots(rows, grouping, collapsedHeaders, query))
            }
        }
    }

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
                secondVisa = secondVisa,
                placesTotal = places.size,
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
                combinedRequirementOf = viewModel::combinedRequirementOf,
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
                // The registry loads once, asynchronously, into an initially-empty
                // flow (see PlacesViewModel.allPlaces): an empty [places] is that
                // startup window, not a real zero, so it reads as "loading" rather
                // than the flat "nothing matches" a query or filter earns.
                val loading = places.isEmpty()
                EmptyPlacesState(
                    message = passportEmptyMessage(loading, query, statusFilter),
                    showClear = !loading && canClearPassportFilter(query, statusFilter),
                    onClear = {
                        viewModel.setQuery("")
                        viewModel.setStatusFilter(StatusFilter.ALL)
                    },
                )
            }
        }

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
                // No animateItem on a pinned header, unlike the rows: a placement
                // animation and the pin both want to own the header's offset, so the
                // two chase each other for the length of every fling. It buys nothing
                // either way — a re-grouping gives the headers new keys, which is a
                // swap rather than the reorder animateItem exists to animate.
                stickyHeader("header-$header", contentType = "header") {
                    SectionHeader(
                        text = header,
                        collapsed = collapseKey in effectiveCollapsed,
                        onClick = { viewModel.toggleHeaderCollapsed(grouping, header) },
                    )
                }
            }
            if (collapseKey in effectiveCollapsed) return@forEach
            item(row.place.code, contentType = "row") {
                Column(Modifier.animateItem()) {
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

    FastScrollOverlay(
        listState = listState,
        headerSlots = headerSlots,
        modifier = Modifier.align(Alignment.TopEnd),
    )

    // Continent and Region sections aren't addressable by a letter, so the rail is
    // absent rather than disabled outside A-Z grouping. Visa mode has no grouping of
    // its own at all — its headers are requirement categories, not letters — so this
    // reads as "grouping is ALPHABETICAL" being a travel-list-only state in practice,
    // even though the check below is the same [grouping] both modes share.
    if (mode != PassportMode.VISA && grouping == Grouping.ALPHABETICAL) {
        AzRail(
            listState = listState,
            headerSlots = headerSlots,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
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
private fun SectionHeader(text: String, collapsed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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

/**
 * The fast-scroll overlay: which section a fling just reached, floating over the
 * top-end corner rather than replacing the (already sticky) header at the top of
 * the list — the two answer different questions when a short section means both
 * are visible at once.
 *
 * Its own composable, and not inlined into [PassportScreen], for the reason the first
 * version of it flung like it was stuck: reading `firstVisibleItemIndex` in the screen's
 * scope invalidates the screen, and that re-runs the `LazyColumn` content lambda over
 * every row on each index change — several times a second mid-fling. Read here instead
 * and only this pill recomposes; `derivedStateOf` narrows that again to the moments the
 * section label actually changes rather than every index.
 */
@Composable
private fun FastScrollOverlay(
    listState: LazyListState,
    headerSlots: List<String?>,
    modifier: Modifier = Modifier,
) {
    val currentHeader by remember(headerSlots) {
        derivedStateOf { headerSlots.getOrNull(listState.firstVisibleItemIndex) }
    }
    // Visible while a fling is in progress, and for a moment after it settles — long
    // enough to read the letter that was just reached, not so long that it lingers
    // over a list the reader has already stopped moving through.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            visible = true
        } else {
            delay(600)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible && currentHeader != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        FastScrollLabel(text = currentHeader.orEmpty())
    }
}

@Composable
private fun FastScrollLabel(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(top = 8.dp, end = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/**
 * The A-Z index rail: a letter per distinct header the alphabetically-grouped list
 * actually emits (see [railLabels]), tap or drag to jump there. Shared between the
 * travel list and the visa list through [headerSlots] the same way [FastScrollOverlay]
 * is — this composable does not know or care which list is on screen.
 *
 * Scrubbing moves [listState] with [LazyListState.scrollToItem], not the animated
 * version: an animation per finger movement would fight the gesture and lag behind it,
 * where a jump is supposed to read as instant. The letter-under-finger pill is
 * [FastScrollOverlay] itself — every `scrollToItem` call lands `firstVisibleItemIndex`
 * exactly on the target header's own slot, so that composable's existing
 * `derivedStateOf` reports the right letter without this one saying so a second time.
 *
 * Every read of [listState] and every bit of scrub state (the finger's row, the
 * touched letter, the target index) lives in `remember`/`mutableStateOf` local to this
 * composable, not in [PassportScreen]'s scope — for the same reason [FastScrollOverlay]
 * moved its own read down here first: a state read up in [PassportScreen] would
 * recompose the whole screen, re-running the `LazyColumn` content lambda over all 252
 * rows, on every frame a finger moves across the rail.
 *
 * Excluded from TalkBack ([clearAndSetSemantics]) on purpose: 26 letters cannot each
 * carry a 48 dp touch target the way the rest of this screen does, and a rail TalkBack
 * can reach only as 26 sub-minimum nodes is worse than one it cannot reach at all. A
 * TalkBack user still has the pinned section headers, which are already announced and
 * already work — don't "fix" this by giving the rail real semantics.
 */
@Composable
private fun AzRail(
    listState: LazyListState,
    headerSlots: List<String?>,
    modifier: Modifier = Modifier,
) {
    val labels = remember(headerSlots) { railLabels(headerSlots) }
    if (labels.isEmpty()) return

    // -1 means "no scrub has requested a jump yet"; the effect below only runs again
    // when this actually changes, so a finger dwelling inside one letter's band does
    // not re-issue the same scrollToItem every frame.
    var targetIndex by remember { mutableIntStateOf(-1) }
    var touchedLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(targetIndex) {
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    Column(
        modifier = modifier
            .width(20.dp)
            .clearAndSetSemantics {}
            .pointerInput(labels, headerSlots) {
                fun letterAt(y: Float): String {
                    val i = (y / size.height * labels.size)
                        .toInt()
                        .coerceIn(0, labels.size - 1)
                    return labels[i]
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pointerId = down.id
                    var pressed = true
                    var label = letterAt(down.position.y)
                    touchedLabel = label
                    railTargetIndex(headerSlots, label)?.let { targetIndex = it }
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null) break
                        pressed = change.pressed
                        if (pressed) {
                            label = letterAt(change.position.y)
                            touchedLabel = label
                            railTargetIndex(headerSlots, label)?.let { targetIndex = it }
                            change.consume()
                        }
                    }
                    touchedLabel = null
                }
            },
        verticalArrangement = Arrangement.Center,
    ) {
        labels.forEach { label ->
            val touched = label == touchedLabel
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (touched) FontWeight.Bold else FontWeight.Normal,
                color = if (touched) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
            )
        }
    }
}

/**
 * One entry per item [PassportScreen]'s travel-mode `rows.forEach` loop emits into
 * the shared `LazyColumn`, in the same order — including the header/collapse skip —
 * so [PassportScreen] can turn a raw `LazyListState.firstVisibleItemIndex` back into
 * "which section is this" for the fast-scroll overlay, without a second source of
 * truth for what that loop already decides.
 */
internal fun travelHeaderSlots(
    rows: List<PlaceRow>,
    grouping: Grouping,
    collapsedHeaders: Set<String>,
    query: String,
): List<String?> {
    val effectiveCollapsed = if (query.isBlank()) collapsedHeaders else emptySet()
    val slots = mutableListOf<String?>()
    var lastHeader: String? = null
    rows.forEach { row ->
        val header = sectionHeader(row.place, grouping)
        val collapseKey = "${grouping.name}:$header"
        if (header != lastHeader) {
            lastHeader = header
            slots += header
        }
        if (collapseKey in effectiveCollapsed) return@forEach
        slots += header
    }
    return slots
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
            // One line, always: the code/sovereign subtitle underneath already
            // carries full identification, so a long name loses only its tail here
            // rather than pushing every row in a uniform list to two different heights.
            Text(
                row.place.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

/** The empty state for the travel list: a loading spinner has nothing to spin
 * against here, so this is just the message plus, when a query or filter is the
 * cause, one tap to get back to the full 252. */
@Composable
private fun EmptyPlacesState(
    message: String,
    showClear: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(24.dp)) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showClear) {
            TextButton(onClick = onClear, contentPadding = PaddingValues(vertical = 4.dp)) {
                Text("Clear")
            }
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
        modifier = modifier
            .fillMaxWidth()
            // A click label rather than a contentDescription override: the card's
            // own lines (the continent count, coverage, nudge...) still need to be
            // heard, this only names what the tap does, in place of the removed
            // "Tap for continents..." caption.
            .semantics {
                onClick(label = "View continents, regions and groupings") { false }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
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
                // An affordance nobody reads is not an affordance — a trailing
                // chevron instead of the old "Tap for continents..." caption.
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
    }
}

/**
 * What the travel list's empty state should say.
 *
 * [loading] stands in for "the registry has not populated [PlacesViewModel.places]
 * yet" — a real zero at this screen only ever happens for a moment on cold start,
 * so an empty backing list is read as loading rather than as "no places exist".
 * A query names itself because "Nothing matches" is the same string whether the
 * cause is a typo or day-one before the asset has loaded, and only one of those
 * is worth a "Clear" button.
 */
internal fun passportEmptyMessage(loading: Boolean, query: String, statusFilter: StatusFilter): String =
    when {
        loading -> "Loading your places…"
        query.isNotBlank() -> "No places match “$query”."
        statusFilter != StatusFilter.ALL -> "No places match the “${statusFilter.label}” filter."
        else -> "Nothing matches."
    }

/** A "Clear" affordance only makes sense once there is something to clear. */
internal fun canClearPassportFilter(query: String, statusFilter: StatusFilter): Boolean =
    query.isNotBlank() || statusFilter != StatusFilter.ALL

/**
 * The [AzRail]'s own letters: every distinct, non-null header in [headerSlots], in
 * the order the list emits them. Read back out of the same slots [FastScrollOverlay]
 * already builds rather than a hardcoded A-Z, so a letter with no places — there are
 * none among the 252, but a filtered-down list could still leave one empty — never
 * appears on the rail with nowhere to jump to.
 */
internal fun railLabels(headerSlots: List<String?>): List<String> =
    headerSlots.filterNotNull().distinct()

/**
 * The row [listState] should land on for a tap or a scrub over [label] — the first
 * slot in [headerSlots] carrying that header, which is the sticky header's own item,
 * not one of its rows. `null` when [label] names a section the list no longer has
 * (a stale label mid-recomposition, or one that was never really there).
 */
internal fun railTargetIndex(headerSlots: List<String?>, label: String): Int? =
    headerSlots.indexOf(label).takeIf { it >= 0 }
