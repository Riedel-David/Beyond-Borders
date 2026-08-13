package com.riedel.aeroglobe.ui.flights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.R
import com.riedel.aeroglobe.data.flights.FlightPhase
import com.riedel.aeroglobe.ui.ComingSoon
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun FlightsScreen(viewModel: FlightsViewModel, modifier: Modifier = Modifier) {
    val flights by viewModel.flights.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        if (flights.isEmpty()) {
            ComingSoon(
                icon = R.drawable.ic_tab_flights,
                title = "No flights yet",
                detail = "Add a flight number and date. Gate, aircraft position and " +
                    "delay risk come in a later step of phase 3.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(flights, key = { it.flight.id }) { entry ->
                    FlightRow(entry, onRemove = { viewModel.removeFlight(entry.flight.id) })
                }
            }
        }
    }

    if (showForm) {
        AddFlightDialog(
            onDismiss = { showForm = false },
            onAdd = { number, isoDate -> viewModel.addFlight(number, isoDate) },
        )
    }
}

@Composable
private fun FlightRow(entry: FlightWithStatus, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
private fun AddFlightDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Boolean) {
    var number by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = Instant.now().toEpochMilli())

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis ?: return@TextButton
                val isoDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                invalid = !onAdd(number, isoDate)
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
            DatePicker(state = datePickerState)
        }
    }
}
