package com.riedel.aeroglobe.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riedel.aeroglobe.data.place.Place
import com.riedel.aeroglobe.data.place.PlaceRepository
import com.riedel.aeroglobe.data.place.PlaceStatus
import com.riedel.aeroglobe.data.place.foldForSearch
import com.riedel.aeroglobe.data.visa.VisaBand
import com.riedel.aeroglobe.data.visa.VisaCategory
import com.riedel.aeroglobe.data.visa.VisaRegistry
import com.riedel.aeroglobe.data.visa.band
import com.riedel.aeroglobe.data.visa.VisaRepository
import com.riedel.aeroglobe.data.visa.VisaRequirement
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

/**
 * Which question the passport tab is answering.
 *
 * One screen with two modes rather than a fifth tab: both are the same 252 rows
 * with the same search field, and the interesting move — "I have been to 40
 * places; where else can I go without paperwork?" — is a mode switch away rather
 * than a navigation.
 */
enum class PassportMode(val label: String) {
    TRAVEL("Where I've been"),
    VISA("Where I can go"),
}

/**
 * Which requirement the visa list shows.
 *
 * [EASY] is the useful default view of a passport: everything that needs no
 * embassy appointment, which is [VisaCategory.VISA_FREE], [VisaCategory.ETA] and
 * [VisaCategory.VISA_ON_ARRIVAL] together. [Category] narrows to exactly one.
 */
sealed interface VisaFilter {
    val label: String

    data object All : VisaFilter {
        override val label = "All"
    }

    data object Easy : VisaFilter {
        override val label = "No embassy"
    }

    data class Category(val category: VisaCategory) : VisaFilter {
        override val label get() = category.label
    }
}

data class PlaceRow(val place: Place, val status: PlaceStatus)

/**
 * One row of the visa list: the place, what it wants, and whether you have been —
 * because "visa-free and I've never been" is the row worth acting on.
 */
data class VisaRow(
    val place: Place,
    val requirement: VisaRequirement,
    val status: PlaceStatus,
)

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
class PlacesViewModel(
    private val repo: PlaceRepository,
    private val visaRepo: VisaRepository,
) : ViewModel() {

    private val allPlaces = MutableStateFlow<List<Place>>(emptyList())

    /**
     * Null until the asset is parsed, and null forever if it cannot be. Every
     * visa surface treats null as "no visa data" and simply does not appear,
     * because a travel map that still works beats a crash on a bad asset.
     */
    private val _visa = MutableStateFlow<VisaRegistry?>(null)
    val visa: StateFlow<VisaRegistry?> = _visa

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _grouping = MutableStateFlow(Grouping.CONTINENT)
    val grouping: StateFlow<Grouping> = _grouping

    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    val statusFilter: StateFlow<StatusFilter> = _statusFilter

    private val _mode = MutableStateFlow(PassportMode.TRAVEL)
    val mode: StateFlow<PassportMode> = _mode

    private val _visaFilter = MutableStateFlow<VisaFilter>(VisaFilter.All)
    val visaFilter: StateFlow<VisaFilter> = _visaFilter

    /** Whether the map paints visa requirements instead of your own history. */
    private val _visaLens = MutableStateFlow(false)
    val visaLens: StateFlow<Boolean> = _visaLens

    /**
     * The place being looked at, if any: the map's card and the passport's expanded
     * visa row are two views of this one value rather than two selections. Opening
     * Suriname in the list and switching to the map should land on Suriname.
     */
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

    /**
     * Which place codes the map's visa lens should paint in each band, empty while
     * the lens is off. Computed here rather than in the composable so that panning
     * the map does not re-bucket 252 places on every frame.
     */
    val visaBands: StateFlow<Map<VisaBand, Set<String>>> =
        combine(_visa, _visaLens) { visa, lens -> bandCodes(visa, lens) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The rows the visa list should render, already filtered and ordered. */
    val visaRows: StateFlow<List<VisaRow>> =
        combine(allPlaces, _visa, statuses, _query, _visaFilter) { places, visa, st, q, filter ->
            buildVisaRows(places, visa, st, q, filter)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { allPlaces.value = repo.places() }
        viewModelScope.launch {
            // A malformed or missing asset must not take the travel map down with
            // it, so this is caught rather than allowed to kill the scope. The
            // build script is what stops it happening; this is the seatbelt.
            _visa.value = runCatching { visaRepo.registry() }.getOrNull()
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setGrouping(value: Grouping) { _grouping.value = value }
    fun setStatusFilter(value: StatusFilter) { _statusFilter.value = value }
    fun select(code: String?) { _selectedCode.value = code }
    fun setMode(value: PassportMode) { _mode.value = value }
    fun setVisaFilter(value: VisaFilter) { _visaFilter.value = value }
    fun setVisaLens(value: Boolean) { _visaLens.value = value }

    fun placeOf(code: String): Place? = allPlaces.value.firstOrNull { it.code == code }

    fun statusOf(code: String): PlaceStatus = statuses.value[code] ?: PlaceStatus.NONE

    fun requirementOf(code: String): VisaRequirement? = _visa.value?.get(code)

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
        fun factory(repo: PlaceRepository, visaRepo: VisaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return PlacesViewModel(repo, visaRepo) as T
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

/**
 * Bucket every requirement into its map band.
 *
 * Returns an empty map when the lens is off or the asset failed to load, which the
 * map reads as "paint nothing" — so a missing visa.json costs the lens and nothing
 * else.
 */
internal fun bandCodes(
    visa: VisaRegistry?,
    lens: Boolean,
): Map<VisaBand, Set<String>> {
    if (visa == null || !lens) return emptyMap()
    return visa.requirements
        .groupBy { it.category.band }
        .mapValues { (_, group) -> group.mapTo(mutableSetOf()) { it.code } }
}

/**
 * The chips the visa list offers, left to right.
 *
 * [VisaFilter.All] and [VisaFilter.Easy] always, then one chip per category the
 * bundled asset actually uses. Offering a category with nothing in it would be a
 * filter that can only ever empty the list, and the count of empty ones changes with
 * the dataset rather than with the build.
 *
 * [VisaCategory.HOME] is left out on purpose: it is one place, it is the passport's
 * own country, and the user knows they can go home.
 */
internal fun visaFilters(visa: VisaRegistry?): List<VisaFilter> {
    if (visa == null) return emptyList()
    return buildList {
        add(VisaFilter.All)
        add(VisaFilter.Easy)
        VisaCategory.entries.forEach { category ->
            if (category == VisaCategory.HOME) return@forEach
            if ((visa.countByCategory[category] ?: 0) > 0) add(VisaFilter.Category(category))
        }
    }
}

/**
 * The visa list, filtered and ordered.
 *
 * Ordered by requirement first and name second, so the answer to "where can I go"
 * is the top of the list rather than something to scroll for. [VisaCategory]'s own
 * declaration order is the ramp — easiest first — so `ordinal` is the sort key on
 * purpose rather than by accident.
 *
 * Uninhabited places sort last within their category for the same reason they do
 * in the travel list: Bouvet Island should never be above Spain.
 */
internal fun buildVisaRows(
    places: List<Place>,
    visa: VisaRegistry?,
    statuses: Map<String, PlaceStatus>,
    query: String,
    filter: VisaFilter,
): List<VisaRow> {
    if (visa == null) return emptyList()
    val needle = query.trim().foldForSearch()
    return places.asSequence()
        .filter { needle.isEmpty() || it.searchKey.contains(needle) }
        // A place with no requirement is dropped rather than shown blank. The
        // build script guarantees there are none; this keeps the list honest if a
        // future asset disagrees with a future registry.
        .mapNotNull { place ->
            visa[place.code]?.let { req ->
                VisaRow(place, req, statuses[place.code] ?: PlaceStatus.NONE)
            }
        }
        .filter {
            when (filter) {
                VisaFilter.All -> true
                VisaFilter.Easy -> it.requirement.isEasy
                is VisaFilter.Category -> it.requirement.category == filter.category
            }
        }
        .sortedWith(
            compareBy(
                { it.requirement.category.ordinal },
                { it.place.uninhabited },
                { it.place.name },
            )
        )
        .toList()
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
