package com.riedel.beyondborders.ui.places

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riedel.beyondborders.core.Haptics
import com.riedel.beyondborders.data.place.INFORMAL_REGIONS
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceRepository
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.place.foldForSearch
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.band
import com.riedel.beyondborders.data.visa.VisaRepository
import com.riedel.beyondborders.data.visa.VisaRequirement
import com.riedel.beyondborders.settings.Denominator
import com.riedel.beyondborders.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the passport list is ordered. */
enum class Grouping { CONTINENT, REGION, ALPHABETICAL }

/** Off, or which grouping the map's region heatmap should paint by. */
enum class RegionMode { OFF, OFFICIAL, INFORMAL }

/**
 * A region's own completion, bucketed for the map heatmap.
 *
 * Four bands rather than a continuous shade for the same reason the visa lens is
 * four bands and not a gradient: MapLibre bakes a layer's colour in at style-build
 * time, so painting has to be "one pre-built layer per discrete value plus a
 * setFilter", and that only stays cheap if the number of values stays small and
 * fixed regardless of how many regions exist.
 */
enum class RegionBand { NONE, SOME, MOST, DONE }

/** Off -> Official -> Informal -> Off, one tap at a time. */
fun nextRegionMode(mode: RegionMode): RegionMode = when (mode) {
    RegionMode.OFF -> RegionMode.OFFICIAL
    RegionMode.OFFICIAL -> RegionMode.INFORMAL
    RegionMode.INFORMAL -> RegionMode.OFF
}

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

    /** Wishlist places the visa asset says need no embassy — the join this app can
     * do offline that nothing else can, because it already owns both datasets. */
    data object WishlistEasy : VisaFilter {
        override val label = "Wishlist, easy"
    }

    /** Visa-free with a stay of 90 days or more — long enough to actually live somewhere. */
    data object LongStay : VisaFilter {
        override val label = "90+ days"
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

/** [been] counts visited and lived together; see [PlaceStatus.isBeen]. [codes] is
 * every member place, for the "where have I been / still need to go" detail popup —
 * default empty so the many existing call sites that only care about the count
 * did not need to change. */
data class ContinentStat(val continent: String, val been: Int, val total: Int, val codes: Set<String> = emptySet())

/** Same shape as [ContinentStat], one per UN M49 region ("Northern Europe",
 * "Western Africa") rather than per continent. */
data class RegionStat(val region: String, val been: Int, val total: Int, val codes: Set<String> = emptySet())

/** One of [INFORMAL_REGIONS] — "Balkans", "Benelux" — with how many of its member
 * codes are been vs. how many exist in this build's 252, after the denominator. */
data class NamedGroupStat(val name: String, val been: Int, val total: Int, val codes: Set<String> = emptySet())

data class TravelStats(
    /** Strictly [PlaceStatus.VISITED]; for the map legend, which has a dot per colour. */
    val visited: Int,
    val lived: Int,
    val wishlist: Int,
    val total: Int,
    val continents: List<ContinentStat>,
    val regions: List<RegionStat> = emptyList(),
    val informalRegions: List<NamedGroupStat> = emptyList(),
    /** "11% of the world's land area, 47% of its population" — null while
     * nothing is marked. See [worldCoverage]. */
    val coverage: String? = null,
) {
    /** Places you have set foot in. The headline number, and never smaller than [visited]. */
    val been: Int = visited + lived

    val percent: Int = if (total == 0) 0 else (been * 100) / total

    /** Continents with at least one visit — the "6 of 8 continents" headline. */
    val continentsTouched: Int = continents.count { it.been > 0 }

    /** "2 places from finishing Europe" — null once every continent is either
     * untouched or already finished. See [almostFinishedNudge]. */
    val nudge: String? = almostFinishedNudge(continents)

    companion object {
        val EMPTY = TravelStats(0, 0, 0, 0, emptyList())
    }
}

/**
 * Where the map was last looking.
 *
 * Plain numbers rather than MapLibre's `CameraPosition`: this file is host-tested,
 * and the ViewModel has no other reason to know which map SDK draws the thing.
 */
data class MapCamera(val latitude: Double, val longitude: Double, val zoom: Double)

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
    private val settings: SettingsStore,
    private val haptics: Haptics,
) : ViewModel() {

    /**
     * Kept here because switching tabs destroys the MapView, and with it the camera:
     * without this, zooming into the Balkans, glancing at the passport list and
     * coming back put the user out over the Atlantic at world zoom.
     *
     * A plain var rather than a StateFlow — it is read once, when the map is built,
     * and never observed. Emitting it would only cause recompositions on every pan.
     */
    var mapCamera: MapCamera? = null

    private val allPlaces = MutableStateFlow<List<Place>>(emptyList())

    /** The full registry, for surfaces that draw every place rather than a filtered list — the map's labels. */
    val places: StateFlow<List<Place>> = allPlaces

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

    /**
     * Prefixed with the grouping's name (e.g. "CONTINENT:Europe") so the same
     * header string in two groupings does not share a collapsed/expanded state.
     */
    val collapsedHeaders: StateFlow<Set<String>> = settings.collapsedHeaders

    /** Called by the map and passport screens on every mark. Goes straight to the
     * platform Vibrator rather than Compose's performHapticFeedback, which many
     * phones gate behind a separate, often-off "touch feedback" setting. */
    fun vibrate() {
        if (settings.hapticsEnabled.value) haptics.tap()
    }

    fun toggleHeaderCollapsed(grouping: Grouping, header: String) {
        val key = "${grouping.name}:$header"
        val current = settings.collapsedHeaders.value
        settings.setCollapsedHeaders(
            if (key in current) current - key else current + key
        )
    }

    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    val statusFilter: StateFlow<StatusFilter> = _statusFilter

    private val _mode = MutableStateFlow(PassportMode.TRAVEL)
    val mode: StateFlow<PassportMode> = _mode

    private val _visaFilter = MutableStateFlow<VisaFilter>(VisaFilter.All)
    val visaFilter: StateFlow<VisaFilter> = _visaFilter

    /** Whether the map paints visa requirements instead of your own history. */
    private val _visaLens = MutableStateFlow(false)
    val visaLens: StateFlow<Boolean> = _visaLens

    /** Whether, and by which grouping, the map paints region completion instead
     * of your own history. Mutually exclusive with [visaLens] — both are ways
     * of temporarily replacing the travel colours, and showing two answers to
     * "what am I looking at" at once would just be confusing. */
    private val _regionMode = MutableStateFlow(RegionMode.OFF)
    val regionMode: StateFlow<RegionMode> = _regionMode

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
        combine(allPlaces, statuses, settings.denominator) { places, statuses, denom -> computeStats(places, statuses, denom) }
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

    /** Which place codes the visa lens should paint grey: this passport's dataset
     * has no answer for them at all, an "honest gap" rather than a sixth band. */
    val visaNoData: StateFlow<Set<String>> =
        combine(allPlaces, _visa, _visaLens) { places, visa, lens -> noDataCodes(places, visa, lens) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Which place codes the map's region heatmap should paint in each band,
     * empty while the mode is off. Computed here for the same reason [visaBands]
     * is: panning the map should not re-bucket every region on every frame. */
    val regionBands: StateFlow<Map<RegionBand, Set<String>>> =
        combine(stats, _regionMode) { stats, mode -> regionBandCodes(mode, stats) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The rows the visa list should render, already filtered and ordered. */
    val visaRows: StateFlow<List<VisaRow>> =
        combine(allPlaces, _visa, statuses, _query, _visaFilter) { places, visa, st, q, filter ->
            buildVisaRows(places, visa, st, q, filter)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** "9 of your 12 wishlist places need no embassy" — the join neither dataset can
     * do alone. Null while there is nothing to say: no visa asset, or no wishlist. */
    val wishlistEasyLine: StateFlow<String?> =
        combine(allPlaces, statuses, _visa) { places, st, visa -> computeWishlistEasyLine(places, st, visa) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { allPlaces.value = repo.places() }
        viewModelScope.launch {
            // A StateFlow re-emits its current value to a new collector, so this
            // both loads the initial registry and reloads it on every nationality
            // switch from Settings — one path, not an init load plus a separate
            // reactor for later changes.
            settings.nationality.collect { nationality ->
                // A malformed or missing asset must not take the travel map down
                // with it, so this is caught rather than allowed to kill the scope.
                // The build script is what stops it happening; this is the seatbelt.
                _visa.value = load(nationality)
                    // A nationality that no longer has an asset is not hypothetical:
                    // the key lives in the backed-up preferences file, so a restore
                    // onto a build with a different asset set brings one along. Before
                    // this fell back, the result was a permanently blank visa tab with
                    // nothing logged to explain it.
                    ?: load(VisaRegistry.DEFAULT_PASSPORT)?.also {
                        settings.setNationality(VisaRegistry.DEFAULT_PASSPORT)
                    }
            }
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setGrouping(value: Grouping) { _grouping.value = value }
    fun setStatusFilter(value: StatusFilter) { _statusFilter.value = value }
    fun select(code: String?) { _selectedCode.value = code }
    fun setMode(value: PassportMode) { _mode.value = value }
    fun setVisaFilter(value: VisaFilter) { _visaFilter.value = value }

    fun setVisaLens(value: Boolean) {
        _visaLens.value = value
        if (value) _regionMode.value = RegionMode.OFF
    }

    fun setRegionMode(value: RegionMode) {
        _regionMode.value = value
        if (value != RegionMode.OFF) _visaLens.value = false
    }

    /**
     * Load one passport's registry, or null if its asset is missing or unreadable.
     *
     * Logged rather than swallowed: this is the failure that presents as "the visa
     * tab is empty and I don't know why", so the reason belongs somewhere a bug
     * report can reach even though the UI has nothing useful to say about it.
     */
    private suspend fun load(passport: String): VisaRegistry? =
        try {
            visaRepo.registry(passport)
        } catch (e: Exception) {
            Log.w(TAG, "No usable visa data for passport $passport", e)
            null
        }

    fun placeOf(code: String): Place? = allPlaces.value.firstOrNull { it.code == code }

    fun statusOf(code: String): PlaceStatus = statuses.value[code] ?: PlaceStatus.NONE

    fun requirementOf(code: String): VisaRequirement? = _visa.value?.get(code)

    /**
     * Set one place's status. One place: no cascade, ever. If the user asks for
     * the status it already has, clear it — that makes a second tap on "Been" an
     * undo, which is what people expect from a toggle.
     *
     * Deciding which of the two it is belongs to [PlaceRepository.toggleStatus], not
     * here: [statuses] is a frame or two behind the database, which is close enough
     * to paint with and not close enough to compare against.
     */
    fun toggle(code: String, target: PlaceStatus) {
        viewModelScope.launch { repo.toggleStatus(code, target) }
    }

    companion object {
        private const val TAG = "PlacesViewModel"

        fun factory(
            repo: PlaceRepository,
            visaRepo: VisaRepository,
            settings: SettingsStore,
            haptics: Haptics,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return PlacesViewModel(repo, visaRepo, settings, haptics) as T
                }
            }
    }
}

// --- pure functions, unit-testable without Android ---------------------------

/**
 * "2 places from finishing Europe" — the continent closest to done that is not
 * already done, ties broken alphabetically so the message is stable. Null if
 * every continent is either untouched or already finished, since there is
 * nothing left to nudge towards.
 */
internal fun almostFinishedNudge(continents: List<ContinentStat>): String? {
    val candidate = continents
        .filter { it.been > 0 && it.been < it.total }
        .minWithOrNull(compareBy({ it.total - it.been }, { it.continent }))
        ?: return null
    val remaining = candidate.total - candidate.been
    val noun = if (remaining == 1) "place" else "places"
    return "$remaining $noun from finishing ${candidate.continent}"
}

/**
 * "9 of your 12 wishlist places need no embassy" — the one line neither the
 * travel history nor the visa asset can say alone. Null with nothing to join:
 * no visa data, no wishlist, or a wishlist with nothing easy in it.
 */
internal fun computeWishlistEasyLine(
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
    visa: VisaRegistry?,
): String? {
    if (visa == null) return null
    val wishlistCodes = statuses.filterValues { it == PlaceStatus.WISHLIST }.keys
    if (wishlistCodes.isEmpty()) return null
    val easy = wishlistCodes.count { code -> visa[code]?.isEasy == true }
    if (easy == 0) return null
    return "$easy of your ${wishlistCodes.size} wishlist places need no embassy"
}

/** Whether [place] counts towards the chosen [Denominator]. */
internal fun Denominator.includes(place: Place): Boolean = when (this) {
    Denominator.ALL -> true
    Denominator.UN_MEMBER -> place.unMember
    Denominator.INHABITED -> !place.uninhabited
}

internal fun computeStats(
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
    denominator: Denominator = Denominator.ALL,
): TravelStats {
    // Filtered once, up front: the denominator changes what counts as "been" too,
    // not only the total, so a place marked outside the chosen set (e.g. a
    // dependency, while counting only UN members) contributes to neither side of
    // the fraction rather than inflating "been" past a shrunken "total".
    val counted = places.filter(denominator::includes)
    val countedCodes = counted.map { it.code }.toSet()

    val continents = counted
        .groupBy { it.continent }
        .map { (continent, group) ->
            ContinentStat(
                continent = continent,
                been = group.count { statuses[it.code]?.isBeen == true },
                total = group.size,
                codes = group.map { it.code }.toSet(),
            )
        }
        // Biggest achievement first, then alphabetically so the order is stable.
        .sortedWith(compareByDescending<ContinentStat> { it.been }.thenBy { it.continent })

    val regions = counted
        .groupBy { it.region }
        .map { (region, group) ->
            RegionStat(
                region = region,
                been = group.count { statuses[it.code]?.isBeen == true },
                total = group.size,
                codes = group.map { it.code }.toSet(),
            )
        }
        .sortedWith(compareByDescending<RegionStat> { it.been }.thenBy { it.region })

    val countedStatuses = statuses.filterKeys { it in countedCodes }

    return TravelStats(
        visited = countedStatuses.count { it.value == PlaceStatus.VISITED },
        lived = countedStatuses.count { it.value == PlaceStatus.LIVED },
        wishlist = countedStatuses.count { it.value == PlaceStatus.WISHLIST },
        total = counted.size,
        continents = continents,
        regions = regions,
        informalRegions = computeInformalRegionStats(countedCodes, statuses),
        coverage = worldCoverage(places, statuses),
    )
}

/**
 * Membership, not a grouping: a place belongs to zero or one of [INFORMAL_REGIONS],
 * unlike continent/region, which partition every place. [countedCodes] is already
 * the denominator-filtered set, so a group's `total` shrinks the same way a
 * continent's does when e.g. only UN members are counted.
 */
internal fun computeInformalRegionStats(
    countedCodes: Set<String>,
    statuses: Map<String, PlaceStatus>,
): List<NamedGroupStat> = INFORMAL_REGIONS
    .map { group ->
        val codes = group.codes intersect countedCodes
        NamedGroupStat(
            name = group.name,
            been = codes.count { statuses[it]?.isBeen == true },
            total = codes.size,
            codes = codes,
        )
    }
    .sortedWith(compareByDescending<NamedGroupStat> { it.been }.thenBy { it.name })

/**
 * "11% of the world's land area, 47% of its population" — a number 40 of 252
 * cannot say, because it distinguishes Russia from Monaco. Built entirely from
 * fields the asset already carries (see `tools/geo/build_geo.py`), so this
 * needs no new asset field or download, only the join. Null until at least one
 * place with area or population behind it has been marked.
 */
internal fun worldCoverage(places: List<Place>, statuses: Map<String, PlaceStatus>): String? {
    val totalArea = places.sumOf { it.areaKm2 }
    val totalPop = places.sumOf { it.popEst }
    if (totalArea == 0L || totalPop == 0L) return null
    val been = places.filter { statuses[it.code]?.isBeen == true }
    val beenArea = been.sumOf { it.areaKm2 }
    val beenPop = been.sumOf { it.popEst }
    if (beenArea == 0L && beenPop == 0L) return null
    val areaPercent = (beenArea * 100 / totalArea).toInt()
    val popPercent = (beenPop * 100 / totalPop).toInt()
    return "$areaPercent% of the world's land area, $popPercent% of its population"
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
 * Every place this passport's [VisaRegistry] has no requirement for at all.
 *
 * DE's dataset covers all 252 places, so this is always empty for it; every other
 * passport is generic-tier and only has real answers where a specific programme
 * rule applies — see STATUS.md's "honest gap" note. Returns empty when the lens is
 * off or the asset failed to load, the same as [bandCodes].
 */
internal fun noDataCodes(
    places: List<Place>,
    visa: VisaRegistry?,
    lens: Boolean,
): Set<String> {
    if (visa == null || !lens) return emptySet()
    val covered = visa.requirements.mapTo(mutableSetOf()) { it.code }
    return places.mapNotNullTo(mutableSetOf()) { it.code.takeUnless { code -> code in covered } }
}

/**
 * Bucket every region (or, in [RegionMode.INFORMAL], every named grouping) into
 * its completion band.
 *
 * Returns an empty map when the mode is off, which the map reads the same way it
 * reads an empty visa-band map: paint nothing.
 */
internal fun regionBandCodes(mode: RegionMode, stats: TravelStats): Map<RegionBand, Set<String>> {
    val buckets = when (mode) {
        RegionMode.OFF -> return emptyMap()
        RegionMode.OFFICIAL -> stats.regions.map { Triple(it.been, it.total, it.codes) }
        RegionMode.INFORMAL -> stats.informalRegions.map { Triple(it.been, it.total, it.codes) }
    }
    val result = mutableMapOf<RegionBand, MutableSet<String>>()
    buckets.forEach { (been, total, codes) ->
        if (total == 0) return@forEach
        val band = when {
            been == 0 -> RegionBand.NONE
            been == total -> RegionBand.DONE
            been * 2 >= total -> RegionBand.MOST
            else -> RegionBand.SOME
        }
        result.getOrPut(band) { mutableSetOf() }.addAll(codes)
    }
    return result
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
        add(VisaFilter.WishlistEasy)
        add(VisaFilter.LongStay)
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
                VisaFilter.WishlistEasy -> it.status == PlaceStatus.WISHLIST && it.requirement.isEasy
                VisaFilter.LongStay ->
                    it.requirement.category == VisaCategory.VISA_FREE && (it.requirement.days ?: 0) >= 90
                is VisaFilter.Category -> it.requirement.category == filter.category
            }
        }
        .sortedWith(
            compareBy(
                { it.requirement.category.ordinal },
                { it.place.uninhabited },
                { it.place.sortKey },
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
        Grouping.CONTINENT -> compareBy({ it.place.continent }, { it.place.uninhabited }, { it.place.sortKey })
        Grouping.REGION -> compareBy({ it.place.region }, { it.place.uninhabited }, { it.place.sortKey })
        Grouping.ALPHABETICAL -> compareBy({ it.place.uninhabited }, { it.place.sortKey })
    }
    return matching.sortedWith(comparator).toList()
}

/** The one header the uninhabited block sits under; see [sectionHeader]. */
internal const val UNINHABITED_HEADER = "Uninhabited"

/**
 * Which section header a row belongs under.
 *
 * Two things here are not obvious. Letters come off [Place.sortKey], so a place is
 * filed under the same letter the sort just ordered it by — Åland Islands under A
 * rather than under its own Å at the bottom of the list.
 *
 * And the uninhabited places get one header of their own instead of a second run of
 * letters. They sort last as a block, and all seven of them start with a letter some
 * inhabited place already used, so per-letter headers there repeated the alphabet at
 * the foot of the list *and* gave the LazyColumn two items with the same key — which
 * throws ("Key header-S was used multiple times") the moment a filter or a search
 * brings both runs on screen together, as searching "south" in A-Z order did.
 *
 * Continent and region grouping need neither: the uninhabited places sort last inside
 * their own continent or region, so every header stays one contiguous run.
 */
internal fun sectionHeader(place: Place, grouping: Grouping): String = when (grouping) {
    Grouping.CONTINENT -> place.continent
    Grouping.REGION -> place.region
    Grouping.ALPHABETICAL ->
        if (place.uninhabited) UNINHABITED_HEADER else place.sortKey.take(1).uppercase()
}
