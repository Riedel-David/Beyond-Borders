package com.riedel.beyondborders.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.MIN_VISIT_YEAR
import com.riedel.beyondborders.data.place.Visit
import com.riedel.beyondborders.data.place.isFutureVisitMonth
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * "Visits  2019, Mar 2023, Undated  +" on the place card of a Been/Lived place. The whole
 * row opens [VisitsDialog]; the plus is there so an empty row still reads as tappable.
 */
@Composable
internal fun VisitsRow(visits: List<Visit>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val undated = stringResource(R.string.visits_undated)
    val locale = currentLocale()
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.visits_row_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            if (visits.isEmpty()) stringResource(R.string.visits_none_yet) else visitYearsText(visits, undated, locale),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.visits_add))
    }
}

/**
 * A thin line with one dot per visit, oldest at the left, a short label under each dot.
 * Scrolls sideways when it overflows; a tap anywhere opens the visits dialog. Plain
 * Compose, and the line is a DrawScope line behind each cell so the cells join up.
 */
@Composable
internal fun VisitTimeline(visits: List<Visit>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val undated = stringResource(R.string.visits_undated)
    val locale = currentLocale()
    val ordered = visitsChronological(visits)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val dotColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .horizontalScroll(rememberScrollState()),
    ) {
        ordered.forEachIndexed { i, visit ->
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .drawBehind {
                            // Half a cell either side of the dot, except the outer ends.
                            val y = size.height / 2
                            val from = if (i == 0) size.width / 2 else 0f
                            val to = if (i == ordered.lastIndex) size.width / 2 else size.width
                            drawLine(lineColor, Offset(from, y), Offset(to, y), strokeWidth = 2.dp.toPx())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                }
                Text(
                    visitLabel(visit, undated, locale),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Existing visits with a delete button each, then a list of years to add one in, and
 * for a chosen year an optional month. Nothing is added until "Add" is pressed.
 */
@Composable
internal fun VisitsDialog(
    placeName: String,
    visits: List<Visit>,
    currentYear: Int,
    currentMonth: Int,
    onAdd: (Int?, Int?) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val undated = stringResource(R.string.visits_undated)
    val locale = currentLocale()
    // Two vars rather than one nullable year, because "No date" (null) is itself a choice.
    var hasPick by remember { mutableStateOf(false) }
    var pickedYear by remember { mutableStateOf<Int?>(null) }
    // null is "Whole year"; only meaningful while a year is picked.
    var pickedMonth by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.visits_dialog_title, placeName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.heightIn(max = 96.dp).verticalScroll(rememberScrollState())) {
                    visits.forEach { visit ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(visitLabel(visit, undated, locale))
                            IconButton(onClick = { onDelete(visit.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.visits_delete))
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.visits_pick_year),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 130.dp)) {
                    // Choices as (year?) so "No date" sits in the same list as the years.
                    items(listOf<Int?>(null) + visitYearChoices(currentYear)) { year ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    hasPick = true
                                    pickedYear = year
                                    // A month picked earlier may be in the future of the new year.
                                    if (year == null || isFutureVisitMonth(year, pickedMonth, currentYear, currentMonth)) {
                                        pickedMonth = null
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = hasPick && pickedYear == year, onClick = null)
                            Text(
                                year?.toString() ?: stringResource(R.string.visits_no_date),
                                Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.visits_pick_month),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                val monthsEnabled = hasPick && pickedYear != null
                FilterChip(
                    selected = pickedMonth == null,
                    onClick = { pickedMonth = null },
                    enabled = monthsEnabled,
                    label = { Text(stringResource(R.string.visits_whole_year)) },
                )
                (1..12).chunked(4).forEach { months ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        months.forEach { m ->
                            FilterChip(
                                selected = pickedMonth == m,
                                onClick = { pickedMonth = m },
                                enabled = monthsEnabled && !isFutureVisitMonth(pickedYear, m, currentYear, currentMonth),
                                label = { Text(monthShortName(m, locale), maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Disabled again right after a tap, so a double tap cannot add two visits.
            TextButton(
                enabled = hasPick,
                onClick = { onAdd(pickedYear, pickedMonth.takeIf { pickedYear != null })
                    hasPick = false
                    pickedYear = null
                    pickedMonth = null
                },
            ) { Text(stringResource(R.string.visits_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.visits_done)) } },
    )
}

/** The locale's short month name, "Mar" in English. */
internal fun monthShortName(month: Int, locale: Locale): String =
    Month.of(month).getDisplayName(TextStyle.SHORT, locale)

/** "Mar 2023", "2019" or [undatedLabel]. */
internal fun visitLabel(visit: Visit, undatedLabel: String, locale: Locale): String {
    val year = visit.year ?: return undatedLabel
    val month = visit.month?.takeIf { it in 1..12 } ?: return year.toString()
    return "${monthShortName(month, locale)} $year"
}

/** "2023, Mar 2019, Undated" — the list is already newest first with undated last. */
internal fun visitYearsText(visits: List<Visit>, undatedLabel: String, locale: Locale): String =
    visits.joinToString(", ") { visitLabel(it, undatedLabel, locale) }

/** Timeline order: oldest first by (year, month), a whole-year visit before the months of its year, undated last. */
internal fun visitsChronological(visits: List<Visit>): List<Visit> =
    visits.sortedWith(
        compareBy<Visit> { it.year == null }.thenBy { it.year ?: 0 }.thenBy { it.month ?: 0 }.thenBy { it.id }
    )

/** Every year the dialog offers, newest first, bounded the same way the repository is. */
internal fun visitYearChoices(currentYear: Int): List<Int> =
    (currentYear downTo MIN_VISIT_YEAR).toList()
