package com.riedel.aeroglobe.ui.flights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riedel.aeroglobe.data.db.FlightEntity
import com.riedel.aeroglobe.data.flights.FlightRepository
import com.riedel.aeroglobe.data.flights.FlightStatus
import com.riedel.aeroglobe.data.flights.FlightStatusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A tracked flight paired with its latest cached status — null while nothing has been fetched yet. */
data class FlightWithStatus(val flight: FlightEntity, val status: FlightStatus?)

class FlightsViewModel(
    private val repo: FlightRepository,
    private val statuses: FlightStatusRepository,
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
    fun addFlight(rawNumber: String, isoDate: String): Boolean {
        val parsed = parseFlightNumber(rawNumber) ?: return false
        viewModelScope.launch { repo.add(parsed, isoDate) }
        return true
    }

    fun removeFlight(id: String) {
        viewModelScope.launch { repo.remove(id) }
    }

    companion object {
        fun factory(repo: FlightRepository, statuses: FlightStatusRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return FlightsViewModel(repo, statuses) as T
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
