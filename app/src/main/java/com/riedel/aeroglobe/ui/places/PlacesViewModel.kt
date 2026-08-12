package com.riedel.aeroglobe.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riedel.aeroglobe.data.place.Place
import com.riedel.aeroglobe.data.place.PlaceRepository
import com.riedel.aeroglobe.data.place.PlaceStatus
import com.riedel.aeroglobe.data.place.foldForSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the passport list is ordered. */
enum class Grouping { CONTINENT, ALPHABETICAL }

/**
 * Which rows the passport list shows.
 *
 * [BEEN] is the superset — visited *and* lived, matching what the stats card counts
 * — and [LIVED] narrows to the handful inside it. A "Been" filter that hid the
 * places you have lived in would contradict the number at the top of the screen.
 */
enum class StatusFilter(val label: String) {
    ALL("All"),
    BEEN("Been"),
    LIVED("Lived"),
    WISHLIST("Want"),
    NONE("Not yet"),
}

data class PlaceRow(val place: Place, val status: PlaceStatus)

/** [been] counts visited and lived together; see [PlaceStatus.isBeen]. */
data class ContinentStat(val continent: String, val been: Int, val total: Int)

data class TravelStats(
    /** Strictly [PlaceStatus.VISITED]; for the map legend, which has a dot per colour. */
    val visited: Int,
    val lived: Int,
    val wishlist: Int,
    val total: Int,
    val continents: List<ContinentStat>,
) {
    /** Places you have set foot in. The headline number, and never smaller than [visited]. */
    val been: Int = visited + lived

    val percent: Int = if (total == 0) 0 else (been * 100) / total

    /** Continents with at least one visit — the "6 of 8 continents" headline. */
    val continentsTouched: Int = continents.count { it.been > 0 }

    companion object {
        val EMPTY = TravelStats(0, 0, 0, 0, emptyList())
    }
}

/**
 * The single source of truth for both the map and the passport list.
 *
 * Shared rather than one ViewModel per tab, so that marking a country on the map
 * and seeing it appear in the list is not a synchronisation problem — there is
 * only ever one copy of the state.
 */
class PlacesViewModel(private val repo: PlaceRepository) : ViewModel() {

    private val allPlaces = MutableStateFlow<List<Place>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _grouping = MutableStateFlow(Grouping.CONTINENT)
    val grouping: StateFlow<Grouping> = _grouping

    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    val statusFilter: StateFlow<StatusFilter> = _statusFilter

    /** The place whose detail sheet is open, if any. */
    private val _selectedCode = MutableStateFlow<String?>(null)
    val selectedCode: StateFlow<String?> = _selectedCode

    /** Code -> status for every place the user has marked. Drives the map colours. */
    val statuses: StateFlow<Map<String, PlaceStatus>> =
        repo.statuses.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val stats: StateFlow<TravelStats> =
        combine(allPlaces, statuses) { places, statuses -> computeStats(places, statuses) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TravelStats.EMPTY)

    /** The rows the passport list should render, already filtered and ordered. */
    val rows: StateFlow<List<PlaceRow>> =
        combine(allPlaces, statuses, _query, _statusFilter, _grouping) { places, st, q, filter, grouping ->
            buildRows(places, st, q, filter, grouping)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { allPlaces.value = repo.places() }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setGrouping(value: Grouping) { _grouping.value = value }
    fun setStatusFilter(value: StatusFilter) { _statusFilter.value = value }
    fun select(code: String?) { _selectedCode.value = code }

    fun placeOf(code: String): Place? = allPlaces.value.firstOrNull { it.code == code }

    fun statusOf(code: String): PlaceStatus = statuses.value[code] ?: PlaceStatus.NONE

    /**
     * Set one place's status. One place: no cascade, ever. If the user asks for
     * the status it already has, clear it — that makes a second tap on "Been" an
     * undo, which is what people expect from a toggle.
     */
    fun toggle(code: String, target: PlaceStatus) {
        viewModelScope.launch {
            val next = if (statusOf(code) == target) PlaceStatus.NONE else target
            repo.setStatus(code, next)
        }
    }

    companion object {
        fun factory(repo: PlaceRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return PlacesViewModel(repo) as T
                }
            }
    }
}

// --- pure functions, unit-testable without Android ---------------------------

internal fun computeStats(
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
): TravelStats {
    val continents = places
        .groupBy { it.continent }
        .map { (continent, group) ->
            ContinentStat(
                continent = continent,
                been = group.count { statuses[it.code]?.isBeen == true },
                total = group.size,
            )
        }
        // Biggest achievement first, then alphabetically so the order is stable.
        .sortedWith(compareByDescending<ContinentStat> { it.been }.thenBy { it.continent })

    return TravelStats(
        visited = statuses.count { it.value == PlaceStatus.VISITED },
        lived = statuses.count { it.value == PlaceStatus.LIVED },
        wishlist = statuses.count { it.value == PlaceStatus.WISHLIST },
        total = places.size,
        continents = continents,
    )
}

internal fun buildRows(
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
    query: String,
    filter: StatusFilter,
    grouping: Grouping,
): List<PlaceRow> {
    val needle = query.trim().foldForSearch()
    val matching = places.asSequence()
        .filter { needle.isEmpty() || it.searchKey.contains(needle) }
        .map { PlaceRow(it, statuses[it.code] ?: PlaceStatus.NONE) }
        .filter {
            when (filter) {
                StatusFilter.ALL -> true
                StatusFilter.BEEN -> it.status.isBeen
                StatusFilter.LIVED -> it.status == PlaceStatus.LIVED
                StatusFilter.WISHLIST -> it.status == PlaceStatus.WISHLIST
                StatusFilter.NONE -> it.status == PlaceStatus.NONE
            }
        }

    // Uninhabited places (Bouvet Island, Heard Island) always sort last: they are
    // real places and stay selectable, but they should never be in the way of
    // finding Spain.
    val comparator: Comparator<PlaceRow> = when (grouping) {
        Grouping.CONTINENT -> compareBy({ it.place.continent }, { it.place.uninhabited }, { it.place.name })
        Grouping.ALPHABETICAL -> compareBy({ it.place.uninhabited }, { it.place.name })
    }
    return matching.sortedWith(comparator).toList()
}
