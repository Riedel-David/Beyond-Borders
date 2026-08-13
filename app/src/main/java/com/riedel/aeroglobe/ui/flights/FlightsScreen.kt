package com.riedel.aeroglobe.ui.flights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.R
import com.riedel.aeroglobe.data.flights.AircraftPosition
import com.riedel.aeroglobe.data.flights.FlightPhase
import com.riedel.aeroglobe.data.flights.FlightStatus
import com.riedel.aeroglobe.data.flights.StatusChange
import com.riedel.aeroglobe.data.flights.delayMinutes
import com.riedel.aeroglobe.ui.ComingSoon
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun FlightsScreen(viewModel: FlightsViewModel, modifier: Modifier = Modifier) {
    val flights by viewModel.flights.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(false) }
    var selectedFlightId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = flights.find { it.flight.id == selectedFlightId }

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
        when {
            selected != null -> {
                val changes by viewModel.changesFor(selected.flight.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val icao24 = selected.status?.aircraftIcao24
                val position by (icao24?.let(viewModel::positionFor) ?: emptyFlow())
                    .collectAsStateWithLifecycle(initialValue = null)
                FlightHeroScreen(
                    entry = selected,
                    changes = changes,
                    position = position,
                    onBack = { selectedFlightId = null },
                    onSeatChange = { seat -> viewModel.updateSeat(selected.flight.id, seat) },
                    modifier = Modifier.padding(padding),
                )
            }
            flights.isEmpty() -> ComingSoon(
                icon = R.drawable.ic_tab_flights,
                title = "No flights yet",
                detail = "Add a flight number and date. Gate, aircraft position and " +
                    "delay risk come in a later step of phase 3.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(flights, key = { it.flight.id }) { entry ->
                    FlightRow(
                        entry,
                        onClick = { selectedFlightId = entry.flight.id },
                        onRemove = { viewModel.removeFlight(entry.flight.id) },
                    )
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
private fun FlightRow(entry: FlightWithStatus, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(entry.flight.flightNumber)
                Text(entry.flight.date)
                entry.status?.let { status ->
                    val gate = status.departureGate?.let { "Gate $it" } ?: "Gate not yet assigned"
                    Text("$gate · ${status.phase.label()}", style = MaterialTheme.typography.bodySmall)
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
    onBack: () -> Unit,
    onSeatChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingSeat by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Text("←") }
            Text(entry.flight.flightNumber, style = MaterialTheme.typography.headlineSmall)
        }
        Text(entry.flight.date)

        val status = entry.status
        if (status == null) {
            Text("Waiting for the first status fetch…", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(status.departureGate?.let { "Gate $it" } ?: "Gate not yet assigned")
            Text(status.departureTerminal?.let { "Terminal $it" } ?: "Terminal not yet assigned")
            Text(delayLabel(status))
            Text(status.phase.label())
            if (status.phase == FlightPhase.IN_AIR) {
                Text(positionLabel(position), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            entry.flight.seat?.let { "Seat $it" } ?: "No seat entered — tap to add",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { editingSeat = true },
        )

        if (changes.isNotEmpty()) {
            Text("Change history", style = MaterialTheme.typography.titleMedium)
            changes.forEach { change ->
                Text(
                    "${change.field}: ${change.oldValue ?: "—"} → ${change.newValue ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
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

/** [onAdd] returns false when [number] doesn't parse; the field is marked invalid rather than dismissing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFlightDialog(onDismiss: () -> Unit, onAdd: (String, String, String?) -> Boolean) {
    var number by remember { mutableStateOf("") }
    var seat by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = Instant.now().toEpochMilli())

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis ?: return@TextButton
                val isoDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                invalid = !onAdd(number, isoDate, seat.trim().ifBlank { null })
                if (!invalid) onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it; invalid = false },
                label = { Text("Flight number") },
                placeholder = { Text("LH 400") },
                isError = invalid,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            OutlinedTextField(
                value = seat,
                onValueChange = { seat = it },
                label = { Text("Seat (optional)") },
                placeholder = { Text("14A") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            DatePicker(state = datePickerState)
        }
    }
}
