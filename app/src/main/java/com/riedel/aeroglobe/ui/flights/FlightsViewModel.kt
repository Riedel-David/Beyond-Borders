package com.riedel.aeroglobe.ui.flights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riedel.aeroglobe.data.db.FlightEntity
import com.riedel.aeroglobe.data.flights.AircraftPosition
import com.riedel.aeroglobe.data.flights.AircraftPositionSource
import com.riedel.aeroglobe.data.flights.FetchResult
import com.riedel.aeroglobe.data.flights.FlightRepository
import com.riedel.aeroglobe.data.flights.FlightStatus
import com.riedel.aeroglobe.data.flights.FlightStatusRepository
import com.riedel.aeroglobe.data.flights.StatusChange
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A tracked flight paired with its latest cached status — null while nothing has been fetched yet. */
data class FlightWithStatus(val flight: FlightEntity, val status: FlightStatus?)

class FlightsViewModel(
    private val repo: FlightRepository,
    private val statuses: FlightStatusRepository,
    private val positions: AircraftPositionSource,
) : ViewModel() {

    val flights: StateFlow<List<FlightWithStatus>> =
        repo.flights.flatMapLatest { tracked ->
            if (tracked.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(tracked.map { flight -> statuses.observeStatus(flight.id).map { FlightWithStatus(flight, it) } }) {
                    it.toList()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Returns false, and adds nothing, if [rawNumber] does not look like a flight number. */
    fun addFlight(rawNumber: String, isoDate: String, seat: String? = null): Boolean {
        val parsed = parseFlightNumber(rawNumber) ?: return false
        viewModelScope.launch { repo.add(parsed, isoDate, seat?.trim()?.ifBlank { null }) }
        return true
    }

    fun removeFlight(id: String) {
        viewModelScope.launch { repo.remove(id) }
    }

    fun updateSeat(id: String, seat: String?) {
        viewModelScope.launch { repo.updateSeat(id, seat?.trim()?.ifBlank { null }) }
    }

    fun changesFor(flightId: String): Flow<List<StatusChange>> = statuses.observeChanges(flightId)

    /** The most recent fetch result per flight ID; a flight absent from the map has never been fetched. */
    val lastFetchResults: StateFlow<Map<String, FetchResult>> = statuses.lastFetchResults

    /** Fetches [flightId]'s status right now, outside the adaptive poll schedule — used by pull-to-refresh. */
    suspend fun refreshNow(flightId: String) {
        val flight = repo.flights.first().find { it.id == flightId } ?: return
        statuses.refresh(flight.id, flight.flightNumber, flight.date)
    }

    /**
     * Refreshes every tracked flight — the list screen's pull-to-refresh. The fan-out is
     * deliberate but the requests do not actually leave together: the data source holds them
     * to one per second, so this takes roughly a second per flight and the spinner stays up
     * for all of it. Firing them in parallel got half of them refused with a 429.
     */
    suspend fun refreshAll() = coroutineScope {
        repo.flights.first().forEach { flight ->
            launch { statuses.refresh(flight.id, flight.flightNumber, flight.date) }
        }
    }

    /**
     * Polls OpenSky for [icao24]'s live position while collected — the hero screen is
     * the only collector (3e decision), so closing it cancels the poll. Emits null and
     * keeps polling on any failure, same "no signal, try again" contract as [FlightDataSource].
     */
    fun positionFor(icao24: String): Flow<AircraftPosition?> = flow {
        while (true) {
            emit(positions.fetchPosition(icao24))
            delay(POSITION_POLL_INTERVAL_MILLIS)
        }
    }

    companion object {
        private const val POSITION_POLL_INTERVAL_MILLIS = 15_000L

        fun factory(
            repo: FlightRepository,
            statuses: FlightStatusRepository,
            positions: AircraftPositionSource,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return FlightsViewModel(repo, statuses, positions) as T
                }
            }
    }
}

// --- pure functions, unit-testable without Android ---------------------------

/**
 * Normalizes free-text flight number input ("lh 400", "LH400", "af1234a") into
 * IATA form: a 2-3 character carrier code, a space, 1-4 digits, and an optional
 * single-letter operational suffix. Returns null if [raw] doesn't match that shape.
 */
internal fun parseFlightNumber(raw: String): String? {
    val match = FLIGHT_NUMBER_PATTERN.matchEntire(raw.trim().uppercase()) ?: return null
    val (carrier, number, suffix) = match.destructured
    return "$carrier $number$suffix"
}

// The carrier group is non-greedy: a greedy {2,3} would happily absorb the
// number's first digit into a 3-character carrier ("LH400" -> "LH4" + "00").
private val FLIGHT_NUMBER_PATTERN = Regex("""([A-Z0-9]{2,3}?)\s*(\d{1,4})([A-Z]?)""")

