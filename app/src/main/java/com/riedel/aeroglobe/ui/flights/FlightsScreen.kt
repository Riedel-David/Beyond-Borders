package com.riedel.aeroglobe.ui.flights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.R
import com.riedel.aeroglobe.data.flights.AircraftPosition
import com.riedel.aeroglobe.data.flights.FetchResult
import com.riedel.aeroglobe.data.flights.FlightPhase
import com.riedel.aeroglobe.data.flights.FlightStatus
import com.riedel.aeroglobe.data.flights.StatusChange
import com.riedel.aeroglobe.data.flights.delayMinutes
import com.riedel.aeroglobe.ui.ComingSoon
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun FlightsScreen(viewModel: FlightsViewModel, modifier: Modifier = Modifier) {
    val flights by viewModel.flights.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(false) }
    var selectedFlightId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = flights.find { it.flight.id == selectedFlightId }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Without this, the system back gesture falls straight through to closing the
    // app: this screen has never had a NavHost to give it a route to pop.
    BackHandler(enabled = selected != null) { selectedFlightId = null }

    Scaffold(
        modifier = modifier,
        // AeroGlobeRoot's outer Scaffold already reserves the status-bar inset; without
        // this override this Scaffold reserves it again, pushing FlightHeroScreen's own
        // TopAppBar down by a second status-bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    // A refresh that fails while a cached status already exists otherwise shows
                    // nothing: only the "no cached status yet" branch on the hero screen surfaces
                    // a FetchResult reason. This is the one place a failure against existing data
                    // needs to say why.
                    val target = selected
                    if (target != null) {
                        viewModel.refreshNow(target.flight.id)
                        val result = viewModel.lastFetchResults.value[target.flight.id]
                        if (target.status != null && result != null && result !is FetchResult.Success) {
                            snackbarHostState.showSnackbar(fetchResultMessage(result, target.flight.flightNumber, target.flight.date))
                        }
                    } else {
                        viewModel.refreshAll()
                        val message = flights.firstNotNullOfOrNull { entry ->
                            val result = viewModel.lastFetchResults.value[entry.flight.id]
                            if (entry.status != null && result != null && result !is FetchResult.Success) {
                                fetchResultMessage(result, entry.flight.flightNumber, entry.flight.date)
                            } else {
                                null
                            }
                        }
                        message?.let { snackbarHostState.showSnackbar(it) }
                    }
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
                        onBack = { selectedFlightId = null },
                        onDelete = {
                            viewModel.removeFlight(selected.flight.id)
                            selectedFlightId = null
                        },
                        onSeatChange = { seat -> viewModel.updateSeat(selected.flight.id, seat) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                flights.isEmpty() -> ComingSoon(
                    icon = R.drawable.ic_tab_flights,
                    title = "No flights yet",
                    detail = "Add a flight number and date. Route, gate, aircraft position and " +
                        "delay estimate fill in from a live lookup — or enter route and " +
                        "schedule by hand on the flight's own screen if there's no key yet.",
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
private fun FlightRow(
    entry: FlightWithStatus,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(entry.flight.flightNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            IconButton(onClick = { confirmingDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${entry.flight.flightNumber}")
            }
        }
    }

    if (confirmingDelete) {
        DeleteFlightConfirmDialog(
            flightNumber = entry.flight.flightNumber,
            onDismiss = { confirmingDelete = false },
            onConfirm = { onRemove(); confirmingDelete = false },
        )
    }
}

@Composable
private fun DeleteFlightConfirmDialog(flightNumber: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop tracking $flightNumber?") },
        text = { Text("Its cached status and change history are removed too.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlightHeroScreen(
    entry: FlightWithStatus,
    changes: List<StatusChange>,
    position: AircraftPosition?,
    lastFetchResult: FetchResult?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSeatChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingSeat by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        // The status-bar inset is already reserved by AeroGlobeRoot's outer Scaffold
        // (it has no topBar, but Scaffold still pads content for it). Without this,
        // this inner Scaffold reserves it a second time and the TopAppBar floats in a
        // blank strip below the real status bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(entry.flight.flightNumber) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${entry.flight.flightNumber}")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                entry.flight.date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val status = entry.status
            if (status != null && (status.departureAirportIata != null || status.arrivalAirportIata != null)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            routeLabel(status.departureCity, status.departureAirportIata, status.arrivalCity, status.arrivalAirportIata),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        InfoRow(
                            "Departs",
                            localTimeLabel(status.actualDepartureUtc ?: status.estimatedDepartureUtc ?: status.scheduledDepartureUtc, status.departureTimeZone),
                        )
                        status.scheduledArrivalUtc?.let {
                            InfoRow(
                                "Arrives",
                                localTimeLabel(status.actualArrivalUtc ?: status.estimatedArrivalUtc ?: it, status.arrivalTimeZone),
                            )
                        }
                        listOfNotNull(
                            status.airlineName,
                            status.aircraftModel?.let { model -> status.aircraftRegistration?.let { reg -> "$model ($reg)" } ?: model },
                        ).takeIf { it.isNotEmpty() }?.let {
                            InfoRow("Aircraft", it.joinToString(" · "))
                        }
                        if (status.isCodeshare) {
                            Text(
                                "Codeshare — check with the operating carrier",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val departureLat = status.departureLat
                        val departureLon = status.departureLon
                        val arrivalLat = status.arrivalLat
                        val arrivalLon = status.arrivalLon
                        if (departureLat != null && departureLon != null && arrivalLat != null && arrivalLon != null) {
                            RouteMapView(
                                departureLat = departureLat,
                                departureLon = departureLon,
                                arrivalLat = arrivalLat,
                                arrivalLon = arrivalLon,
                                departureLabel = airportLabel(status.departureCity, status.departureAirportIata),
                                arrivalLabel = airportLabel(status.arrivalCity, status.arrivalAirportIata),
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                            )
                        }
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status == null) {
                        // No cached status: show either "waiting" or the failure reason
                        Text(
                            lastFetchResult?.let { fetchResultMessage(it, entry.flight.flightNumber, entry.flight.date) }
                                ?: "Waiting for the first status fetch…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lastFetchResult == null || lastFetchResult is FetchResult.Success || lastFetchResult is FetchResult.NoKey) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    } else {
                        Text(status.phase.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        InfoRow("Gate", status.departureGate ?: noLiveDataLabel(status.hasLiveTracking))
                        InfoRow("Terminal", status.departureTerminal ?: noLiveDataLabel(status.hasLiveTracking))
                        InfoRow("Delay", delayLabel(status))
                        if (status.phase == FlightPhase.IN_AIR) {
                            InfoRow("Position", positionLabel(position, status.hasLiveTracking))
                        }
                        val arrivalExtras = listOfNotNull(
                            status.arrivalGate?.let { "Gate $it" },
                            status.arrivalTerminal?.let { "Terminal $it" },
                            status.arrivalBaggageBelt?.let { "Belt $it" },
                        )
                        if (arrivalExtras.isNotEmpty()) {
                            InfoRow("On arrival", arrivalExtras.joinToString(" · "))
                        }
                        Text(
                            freshnessLabel(status.fetchedAt, "Updated"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        status.providerUpdatedAt?.let {
                            Text(
                                freshnessLabel(it, "Airline data updated"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            Text(humanizeStatusChange(change), style = MaterialTheme.typography.bodySmall)
                        }
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

    if (confirmingDelete) {
        DeleteFlightConfirmDialog(
            flightNumber = entry.flight.flightNumber,
            onDismiss = { confirmingDelete = false },
            onConfirm = { confirmingDelete = false; onDelete() },
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

private fun fetchResultMessage(result: FetchResult, flightNumber: String, date: String): String = when (result) {
    is FetchResult.Success -> "Waiting for the first status fetch…"
    is FetchResult.NotFound -> "No flight $flightNumber found on $date"
    is FetchResult.HttpError -> "Lookup failed: ${result.code} — ${result.message}"
    is FetchResult.NoKey -> "No API key configured. Add one in settings."
    is FetchResult.Offline -> "Network unavailable. Pull down to retry."
    is FetchResult.ParseError -> "Parse error: ${result.reason}"
    is FetchResult.RateLimited -> if (result.transient) {
        "Too many lookups at once. Pull down to retry."
    } else {
        "Monthly API quota spent — no more lookups until the plan resets."
    }
}

private fun delayLabel(status: FlightStatus): String {
    val minutes = delayMinutes(status) ?: return "No delay estimate yet"
    return if (minutes <= 0) "On time" else "Delayed $minutes min"
}

/** "Not yet assigned" implies it's coming; that's false once there's no live tracking at all. */
private fun noLiveDataLabel(hasLiveTracking: Boolean): String =
    if (hasLiveTracking) "Not yet assigned" else "No live tracking for this flight"

private fun positionLabel(position: AircraftPosition?, hasLiveTracking: Boolean): String {
    if (position == null) return if (hasLiveTracking) "Live position unavailable" else "No live tracking for this flight"
    val altitude = position.altitudeMeters?.let { "%,.0f m".format(it) } ?: "altitude unknown"
    val speed = position.velocityMs?.let { "%.0f km/h".format(it * 3.6) } ?: "speed unknown"
    return "%.4f, %.4f · %s · %s".format(position.latitude, position.longitude, altitude, speed)
}

private fun airportLabel(city: String?, iata: String?): String = when {
    city != null && iata != null -> "$city ($iata)"
    iata != null -> iata
    city != null -> city
    else -> "?"
}

private fun routeLabel(departureCity: String?, departureIata: String?, arrivalCity: String?, arrivalIata: String?): String =
    "${airportLabel(departureCity, departureIata)} → ${airportLabel(arrivalCity, arrivalIata)}"

private val LOCAL_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

/** Formats in [timeZone] (the airport's own zone), not the device's — "local time" means local to the airport. */
private fun localTimeLabel(instant: Instant, timeZone: String?): String {
    val zone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    return LOCAL_TIME_FORMATTER.withZone(zone).format(instant)
}

private fun freshnessLabel(at: Instant, prefix: String): String {
    val minutes = Duration.between(at, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "$prefix just now"
        minutes < 60 -> "$prefix $minutes min ago"
        minutes < 60 * 24 -> "$prefix ${minutes / 60} h ago"
        else -> "$prefix ${minutes / (60 * 24)} d ago"
    }
}

private fun humanizeStatusChange(change: StatusChange): String {
    val fieldLabel = when (change.field) {
        "gate" -> "Gate"
        "terminal" -> "Terminal"
        "phase" -> "Status"
        else -> change.field
    }
    fun humanizeValue(value: String?): String {
        if (change.field != "phase" || value == null) return value ?: "—"
        return runCatching { FlightPhase.valueOf(value).label() }.getOrDefault(value)
    }
    return "$fieldLabel: ${humanizeValue(change.oldValue)} → ${humanizeValue(change.newValue)}"
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
                        trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.padding(end = 4.dp)) },
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
