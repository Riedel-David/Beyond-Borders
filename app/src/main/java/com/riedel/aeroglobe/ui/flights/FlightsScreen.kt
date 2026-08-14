package com.riedel.aeroglobe.ui.flights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.R
import com.riedel.aeroglobe.data.flights.AircraftPosition
import com.riedel.aeroglobe.data.flights.FetchResult
import com.riedel.aeroglobe.data.flights.FlightPhase
import com.riedel.aeroglobe.data.flights.FlightStatus
import com.riedel.aeroglobe.data.flights.InboundLeg
import com.riedel.aeroglobe.data.flights.StatusChange
import com.riedel.aeroglobe.data.flights.delayMinutes
import com.riedel.aeroglobe.ui.ComingSoon
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun FlightsScreen(viewModel: FlightsViewModel, modifier: Modifier = Modifier) {
    val flights by viewModel.flights.collectAsStateWithLifecycle()
    val isUsingRealData by viewModel.isUsingRealData.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(false) }
    var selectedFlightId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = flights.find { it.flight.id == selectedFlightId }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    // Without this, the system back gesture falls straight through to closing the
    // app: this screen has never had a NavHost to give it a route to pop.
    BackHandler(enabled = selected != null) { selectedFlightId = null }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (selected == null) {
                FloatingActionButton(onClick = { showForm = true }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    if (selected != null) viewModel.refreshNow(selected.flight.id) else viewModel.refreshAll()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                selected != null -> {
                    val changes by viewModel.changesFor(selected.flight.id)
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val icao24 = selected.status?.aircraftIcao24
                    val position by (icao24?.let(viewModel::positionFor) ?: emptyFlow())
                        .collectAsStateWithLifecycle(initialValue = null)
                    val fetchResults by viewModel.lastFetchResults.collectAsStateWithLifecycle()
                    val lastFetchResult = fetchResults[selected.flight.id]
                    FlightHeroScreen(
                        entry = selected,
                        changes = changes,
                        position = position,
                        lastFetchResult = lastFetchResult,
                        isUsingRealData = isUsingRealData,
                        onBack = { selectedFlightId = null },
                        onSeatChange = { seat -> viewModel.updateSeat(selected.flight.id, seat) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                flights.isEmpty() -> ComingSoon(
                    icon = R.drawable.ic_tab_flights,
                    title = "No flights yet",
                    detail = "Add a flight number and date. Gate, aircraft position and " +
                        "delay risk come in a later step of phase 3.",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(flights, key = { it.flight.id }) { entry ->
                        FlightRow(
                            entry,
                            isUsingRealData = isUsingRealData,
                            onClick = { selectedFlightId = entry.flight.id },
                            onRemove = { viewModel.removeFlight(entry.flight.id) },
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        AddFlightDialog(
            onDismiss = { showForm = false },
            onAdd = { number, isoDate, seat -> viewModel.addFlight(number, isoDate, seat) },
        )
    }
}

@Composable
private fun DemoDataBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            "Demo data",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun FlightRow(
    entry: FlightWithStatus,
    isUsingRealData: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.flight.flightNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!isUsingRealData) DemoDataBadge()
                }
                Text(
                    entry.flight.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.status?.let { status ->
                    val gate = status.departureGate?.let { "Gate $it" } ?: "Gate not yet assigned"
                    Text(
                        "$gate · ${status.phase.label()}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Text("✕")
            }
        }
    }
}

@Composable
private fun FlightHeroScreen(
    entry: FlightWithStatus,
    changes: List<StatusChange>,
    position: AircraftPosition?,
    lastFetchResult: FetchResult?,
    isUsingRealData: Boolean,
    onBack: () -> Unit,
    onSeatChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingSeat by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Text("←") }
            Text(entry.flight.flightNumber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (!isUsingRealData) DemoDataBadge()
        }
        Text(
            entry.flight.date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp),
        )

        val status = entry.status
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == null) {
                    // No cached status: show either "waiting" or the failure reason
                    when (lastFetchResult) {
                        null -> Text("Waiting for the first status fetch…", style = MaterialTheme.typography.bodyMedium)
                        is FetchResult.Success -> Text("Waiting for the first status fetch…", style = MaterialTheme.typography.bodyMedium)
                        is FetchResult.NotFound -> Text(
                            "No flight ${entry.flight.flightNumber} found on ${entry.flight.date}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        is FetchResult.HttpError -> Text(
                            "Lookup failed: ${lastFetchResult.code} — ${lastFetchResult.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        is FetchResult.NoKey -> Text(
                            "No API key configured. Enable real flight data in settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is FetchResult.Offline -> Text(
                            "Network unavailable. Pull down to retry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        is FetchResult.ParseError -> Text(
                            "Parse error: ${lastFetchResult.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        is FetchResult.RateLimited -> Text(
                            if (lastFetchResult.transient) {
                                "Too many lookups at once. Pull down to retry."
                            } else {
                                "Monthly API quota spent — no more lookups until the plan resets."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(status.phase.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    InfoRow("Gate", status.departureGate ?: "Not yet assigned")
                    InfoRow("Terminal", status.departureTerminal ?: "Not yet assigned")
                    InfoRow("Delay", delayLabel(status))
                    if (status.phase == FlightPhase.IN_AIR) {
                        InfoRow("Position", positionLabel(position))
                    }
                    if (status.phase == FlightPhase.SCHEDULED || status.phase == FlightPhase.BOARDING) {
                        inboundLegLabel(status.inboundLeg, status.inboundLegCheckedAt)?.let {
                            InfoRow("Inbound aircraft", it)
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth().clickable { editingSeat = true }) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.flight.seat?.let { "Seat $it" } ?: "No seat entered",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (changes.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Change history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    changes.forEach { change ->
                        Text(
                            "${change.field}: ${change.oldValue ?: "—"} → ${change.newValue ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (editingSeat) {
        SeatEditDialog(
            initial = entry.flight.seat,
            onDismiss = { editingSeat = false },
            onConfirm = { seat -> onSeatChange(seat); editingSeat = false },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun delayLabel(status: FlightStatus): String {
    val minutes = delayMinutes(status) ?: return "No delay estimate yet"
    return if (minutes <= 0) "On time" else "Delayed $minutes min"
}

private fun positionLabel(position: AircraftPosition?): String {
    if (position == null) return "Live position unavailable"
    val altitude = position.altitudeMeters?.let { "%,.0f m".format(it) } ?: "altitude unknown"
    val speed = position.velocityMs?.let { "%.0f km/h".format(it * 3.6) } ?: "speed unknown"
    return "%.4f, %.4f · %s · %s".format(position.latitude, position.longitude, altitude, speed)
}

/** Null before the one-time inbound-leg check has happened — nothing to show yet, not "no signal". */
private fun inboundLegLabel(leg: InboundLeg?, checkedAt: Instant?): String? {
    if (checkedAt == null) return null
    if (leg == null) return "Inbound aircraft status unavailable"
    val origin = leg.originAirportIata ?: "unknown origin"
    if (leg.phase == FlightPhase.LANDED) return "Aircraft inbound from $origin has landed"
    val eta = (leg.estimatedArrivalUtc ?: leg.scheduledArrivalUtc)
        ?.atZone(ZoneOffset.UTC)?.format(DateTimeFormatter.ofPattern("HH:mm"))
        ?: "unknown time"
    return "Aircraft inbound from $origin, landing ~$eta UTC"
}

@Composable
private fun SeatEditDialog(initial: String?, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var seat by remember { mutableStateOf(initial ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seat") },
        text = {
            OutlinedTextField(
                value = seat,
                onValueChange = { seat = it },
                placeholder = { Text("14A") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(seat.trim().ifBlank { null }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun FlightPhase.label(): String = when (this) {
    FlightPhase.SCHEDULED -> "Scheduled"
    FlightPhase.BOARDING -> "Boarding"
    FlightPhase.DEPARTED -> "Departed"
    FlightPhase.IN_AIR -> "In the air"
    FlightPhase.LANDED -> "Landed"
    FlightPhase.CANCELLED -> "Cancelled"
}

/**
 * [onAdd] returns false when [number] doesn't parse; the field is marked invalid rather
 * than dismissing. The date lives in a plain read-only field with its own trigger, rather
 * than inside [DatePickerDialog]'s content slot: that slot is meant to hold only the
 * [DatePicker] itself, and stacking form fields above it there is what was pushing the
 * day-of-week header and the dialog's own buttons into the calendar grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFlightDialog(onDismiss: () -> Unit, onAdd: (String, String, String?) -> Boolean) {
    var number by remember { mutableStateOf("") }
    var seat by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf(Instant.now().toEpochMilli()) }
    val isoDate = Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add flight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it; invalid = false },
                    label = { Text("Flight number") },
                    placeholder = { Text("LH 400") },
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = isoDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date") },
                        trailingIcon = { Text("📅", modifier = Modifier.padding(end = 4.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // A readOnly field still consumes touches for cursor placement, so a
                    // clickable on the field itself never fires — an invisible clickable
                    // overlay is what actually opens the picker.
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showDatePicker = true },
                    )
                }
                OutlinedTextField(
                    value = seat,
                    onValueChange = { seat = it },
                    label = { Text("Seat (optional)") },
                    placeholder = { Text("14A") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                invalid = !onAdd(number, isoDate, seat.trim().ifBlank { null })
                if (!invalid) onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
