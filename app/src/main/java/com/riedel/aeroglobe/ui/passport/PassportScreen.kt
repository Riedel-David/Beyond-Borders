package com.riedel.aeroglobe.ui.passport

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.data.place.PlaceStatus
import com.riedel.aeroglobe.ui.places.ContinentStat
import com.riedel.aeroglobe.ui.places.Grouping
import com.riedel.aeroglobe.ui.places.PlaceRow
import com.riedel.aeroglobe.ui.places.PassportMode
import com.riedel.aeroglobe.ui.places.PlacesViewModel
import com.riedel.aeroglobe.ui.places.StatusFilter
import com.riedel.aeroglobe.ui.places.TravelStats
import com.riedel.aeroglobe.ui.places.sectionHeader
import com.riedel.aeroglobe.ui.places.visaFilters

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
    val query by viewModel.query.collectAsStateWithLifecycle()
    val grouping by viewModel.grouping.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val visa by viewModel.visa.collectAsStateWithLifecycle()
    val visaRows by viewModel.visaRows.collectAsStateWithLifecycle()
    val visaFilter by viewModel.visaFilter.collectAsStateWithLifecycle()
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()
    // Derived from an asset that is read once, so a flow would be a subscription to
    // something that never changes twice.
    val filters = remember(visa) { visaFilters(visa) }

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
            StatsCard(stats, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
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
                    Modifier.padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = grouping == Grouping.CONTINENT,
                        onClick = { viewModel.setGrouping(Grouping.CONTINENT) },
                        label = { Text("By continent") },
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
        var lastHeader: String? = null
        rows.forEach { row ->
            // Derived next to the sort rather than here: the header letter and the
            // ordering have to be the same answer, and the key has to stay unique.
            val header = sectionHeader(row.place, grouping)
            if (header != lastHeader) {
                lastHeader = header
                item("header-$header") { SectionHeader(header) }
            }
            item(row.place.code) {
                PlaceListRow(
                    row = row,
                    onToggle = { target -> viewModel.toggle(row.place.code, target) },
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
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
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
            FilterChip(
                selected = row.status == PlaceStatus.VISITED,
                onClick = { onToggle(PlaceStatus.VISITED) },
                label = { Text("Been") },
            )
            FilterChip(
                selected = row.status == PlaceStatus.LIVED,
                onClick = { onToggle(PlaceStatus.LIVED) },
                label = { Text("Lived") },
            )
            FilterChip(
                selected = row.status == PlaceStatus.WISHLIST,
                onClick = { onToggle(PlaceStatus.WISHLIST) },
                label = { Text("Want") },
            )
        }
    }
}

@Composable
private fun StatsCard(stats: TravelStats, modifier: Modifier = Modifier) {
    Card(
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
            // Two columns: seven continents in four rows instead of seven, so the
            // card does not push the search field and the list off the screen.
            Column(
                Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stats.continents.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { ContinentBar(it, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinentBar(stat: ContinentStat, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(
                stat.continent,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                " ${stat.been}/${stat.total}",
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
            val fraction = if (stat.total == 0) 0f else stat.been.toFloat() / stat.total
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
