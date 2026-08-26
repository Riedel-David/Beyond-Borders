package com.riedel.beyondborders.ui.map

import android.graphics.PointF
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riedel.beyondborders.ui.BounceFilterChip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.place.foldForSearch
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaConfidence
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.VisaRequirement
import com.riedel.beyondborders.ui.passport.SearchField
import com.riedel.beyondborders.ui.places.PlacesViewModel
import com.riedel.beyondborders.ui.places.RegionBand
import com.riedel.beyondborders.ui.places.RegionMode
import com.riedel.beyondborders.ui.places.TravelStats
import com.riedel.beyondborders.ui.places.nextRegionMode
import com.riedel.beyondborders.ui.theme.LocalMapPalette
import com.riedel.beyondborders.ui.theme.MapPalette
import com.riedel.beyondborders.ui.theme.RegionRamp
import com.riedel.beyondborders.ui.theme.VisaRamp
import com.riedel.beyondborders.ui.visa.asOfNotice
import com.riedel.beyondborders.ui.visa.requirementSummary
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer

/**
 * Where the camera sits on a cold start.
 *
 * Zoom 0 is MapLibre's floor and puts the world at 512 dp across, which is wider
 * than a phone — so the far edges of the Pacific are still off-screen and the
 * empty strips above Greenland and below Antarctica are painted by the ocean
 * background layer, which is why they look like sea rather than like a bug.
 */
private val WORLD = CameraPosition.Builder()
    .target(LatLng(18.0, 10.0))
    .zoom(0.0)
    .build()

/**
 * How forgiving a tap is, in dp. Tapping Monaco or Singapore at world zoom means
 * hitting a two-pixel shape, so a miss falls back to a square of this radius
 * around the finger.
 */
private const val TAP_SLOP_DP = 16f

/**
 * How big a place's on-screen footprint has to be, in dp, before its name is
 * drawn. There is no glyph server to paint text into the map tiles themselves
 * (see [PlacesMapStyle]), so labels are a Compose overlay instead, and a fixed
 * zoom cutoff would either hide Luxembourg forever or clutter the world view with
 * every microstate at once. Sizing off the place's own bounding box scales the
 * two together: Russia labels at world zoom, San Marino only once you have
 * zoomed in far enough that it would otherwise go unnamed.
 */
private const val MIN_LABEL_FOOTPRINT_DP = 56f

/** One place name and where to draw it, in screen pixels. */
private data class PlaceLabel(val code: String, val name: String, val x: Float, val y: Float)

/**
 * The world map: one tap marks exactly one place.
 *
 * That sentence is the whole product. Other trackers key their map off a
 * country's parent, so tapping Denmark paints Greenland too. Here the map is
 * driven by [PlacesMapStyle]'s per-code filters over a registry that has no parent
 * links at all (see [Place]), so painting a neighbour is not something the code
 * could do even by accident.
 */
@Composable
fun MapScreen(viewModel: PlacesViewModel, modifier: Modifier = Modifier) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val visa by viewModel.visa.collectAsStateWithLifecycle()
    val visaBands by viewModel.visaBands.collectAsStateWithLifecycle()
    val lens by viewModel.visaLens.collectAsStateWithLifecycle()
    val regionMode by viewModel.regionMode.collectAsStateWithLifecycle()
    val regionBands by viewModel.regionBands.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val palette = LocalMapPalette.current

    val controller = remember { PlacesMapController() }
    val selected = selectedCode?.let(viewModel::placeOf)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf<List<PlaceLabel>>(emptyList()) }
    var counterLabeled by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Collapsed by default: Search/World/Share/Visa stacking up every time made
    // the top-right corner read as clutter rather than controls. One icon expands
    // the stack on demand instead of a fourth-and-growing always-visible pill.
    var controlsExpanded by remember { mutableStateOf(false) }
    val searchMatches = remember(places, searchQuery) { searchMapMatches(places, searchQuery) }
    val regionActive = regionMode != RegionMode.OFF
    // A tap in region mode is asking "which region is this", not "mark this
    // place" — the two questions don't share a card, so this stays local state
    // rather than [PlacesViewModel.selectedCode], which the passport also reads.
    var regionInfo by remember { mutableStateOf<RegionTapInfo?>(null) }

    // Reads the ViewModel's StateFlow.value rather than the regionMode/stats
    // collected above: this runs from a MapLibre click callback, which can fire
    // from a closure captured several recompositions ago, and .value is always
    // the live answer regardless of which composition captured this closure.
    fun tap(code: String?) {
        val liveMode = viewModel.regionMode.value
        if (liveMode != RegionMode.OFF) {
            regionInfo = code?.let { regionInfoFor(it, liveMode, viewModel.stats.value, viewModel::placeOf) }
        } else {
            viewModel.select(code)
        }
    }

    fun pickFromSearch(place: Place) {
        viewModel.select(place.code)
        controller.flyTo(place.bounds)
        searchOpen = false
        searchQuery = ""
    }

    // Leaving the map drops the selection, so switching to the passport and back
    // does not present a card about a country the user has stopped thinking about.
    DisposableEffect(Unit) {
        onDispose { viewModel.select(null) }
    }

    // Switching the heatmap on mid-selection would otherwise leave a marking card
    // showing over a map that no longer means "tap to mark".
    LaunchedEffect(regionActive) {
        if (regionActive) viewModel.select(null) else regionInfo = null
    }

    Box(modifier.fillMaxSize()) {
        PlacesMapView(
            controller = controller,
            palette = palette,
            statuses = statuses,
            visaBands = visaBands,
            lens = lens,
            regionBands = regionBands,
            regionActive = regionActive,
            selectedCode = selectedCode,
            places = places,
            onLabels = { labels = it },
            onTap = ::tap,
            modifier = Modifier.fillMaxSize(),
        )

        // Drawn as plain Compose text, not a map layer: there is no glyph server to
        // paint labels into the style itself (see PlacesMapStyle), so this overlay
        // is the whole feature, repositioned every time the camera moves.
        labels.forEach { label ->
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = MaterialTheme.shapes.extraSmall,
                // A tap on the label is a tap on the country it names — without this
                // the label sits over the polygon and swallows the tap that would
                // otherwise have reached the map underneath.
                modifier = Modifier
                    .offset { IntOffset(label.x.toInt(), label.y.toInt()) }
                    // A small fixed nudge rather than measuring the text to centre
                    // it exactly: the anchor is a country's centroid, which is
                    // already an approximation, so a pixel-perfect centre on top of
                    // it would be precision this feature does not need.
                    .offset(x = (-24).dp, y = (-8).dp)
                    .clickable { tap(label.code) },
            ) {
                Text(
                    label.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        // Overlays sit on top of the map surface, deliberately compact: the map is
        // the point, and chrome that covers the Mediterranean is chrome too much.
        MapPill(
            // Only worth a toggle once there is something to spell out; the empty
            // state's nudge and the visa lens' and region heatmap's own legends
            // both stay static.
            onClick = if (!lens && !regionActive && (stats.been > 0 || stats.wishlist > 0)) {
                { counterLabeled = !counterLabeled }
            } else {
                null
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            if (lens) {
                VisaLegend(palette.visa, visa)
            } else if (regionActive) {
                RegionLegend(palette.region, regionMode, stats)
            } else if (stats.been == 0 && stats.wishlist == 0) {
                // Nothing marked yet, so the counter would say "0" and teach
                // nothing. Say what to do instead.
                Text("Tap a place to mark it", style = MaterialTheme.typography.labelLarge)
            } else if (counterLabeled) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CounterLegendRow(palette.visited, stats.visited, "visited")
                    if (stats.lived > 0) CounterLegendRow(palette.lived, stats.lived, "lived")
                    if (stats.wishlist > 0) CounterLegendRow(palette.wishlist, stats.wishlist, "wanted")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // One dot per map colour, and only for colours actually on the
                    // map: this is a legend, so it counts each status strictly rather
                    // than showing the combined "been" total the passport leads with.
                    LegendDot(palette.visited)
                    Text("${stats.visited}", style = MaterialTheme.typography.labelLarge)
                    if (stats.lived > 0) {
                        LegendDot(palette.lived)
                        Text("${stats.lived}", style = MaterialTheme.typography.labelLarge)
                    }
                    if (stats.wishlist > 0) {
                        LegendDot(palette.wishlist)
                        Text("${stats.wishlist}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapPill(
                onClick = {
                    controlsExpanded = !controlsExpanded
                    // Collapsing hides the search field along with everything else,
                    // so leaving it open underneath would reopen it silently the
                    // next time the stack expands.
                    if (!controlsExpanded) searchOpen = false
                },
            ) {
                Icon(
                    if (controlsExpanded) Icons.Filled.Close else Icons.Filled.MoreVert,
                    contentDescription = if (controlsExpanded) "Hide map controls" else "Show map controls",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (!controlsExpanded) return@Column
            // Search is the passport's own search, on the map — the same reason
            // both screens share the [PlacesViewModel] query, this just is not
            // routed through it, since the map wants to fly the camera on a pick
            // rather than filter a list.
            MapPill(onClick = { searchOpen = !searchOpen }) {
                Text(
                    if (searchOpen) "Close" else "Search",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (searchOpen) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        SearchField(
                            query = searchQuery,
                            onQuery = { searchQuery = it },
                            label = "Find a place",
                        )
                        searchMatches.forEach { place ->
                            Text(
                                place.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pickFromSearch(place) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
            MapPill(onClick = controller::showWorld) {
                Text(
                    "World",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            MapPill(
                onClick = {
                    // The map's snapshot callback never fires while the style is still
                    // loading, which otherwise left a tap doing nothing at all with no
                    // sign anything was wrong.
                    val started = controller.snapshot { bitmap ->
                        scope.launch {
                            runCatching { shareMapSnapshot(context, bitmap, stats) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        "Couldn't share the map",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        }
                    }
                    if (!started) {
                        Toast.makeText(context, "Map isn't ready yet", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(
                    "Share",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Offered only once the asset is known to have loaded: a toggle that
            // silently paints nothing would be worse than no toggle.
            if (visa != null) {
                MapPill(onClick = { viewModel.setVisaLens(!lens) }) {
                    Text(
                        if (lens) "My travels" else "Visa",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // One pill cycles all three states rather than three pills for one
            // choice — Off/Official/Informal is a single question, not three.
            MapPill(onClick = { viewModel.setRegionMode(nextRegionMode(regionMode)) }) {
                Text(
                    when (regionMode) {
                        RegionMode.OFF -> "Regions"
                        RegionMode.OFFICIAL -> "Regions: UN"
                        RegionMode.INFORMAL -> "Regions: named"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (regionActive) {
            if (regionInfo != null) {
                RegionInfoCard(
                    info = regionInfo!!,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                )
            }
        } else if (selected != null) {
            SelectedPlaceCard(
                place = selected,
                status = statuses[selected.code] ?: PlaceStatus.NONE,
                // Carried into the card whenever the lens is on, so a tap answers the
                // question the map is currently asking rather than the other one.
                requirement = if (lens) viewModel.requirementOf(selected.code) else null,
                onToggle = { target ->
                    viewModel.vibrate()
                    viewModel.toggle(selected.code, target)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }
}

/** Which region/grouping a tapped place belongs to, in the mode that was active. */
private data class RegionTapInfo(val place: Place, val name: String, val been: Int, val total: Int)

private fun regionInfoFor(
    code: String,
    mode: RegionMode,
    stats: TravelStats,
    placeOf: (String) -> Place?,
): RegionTapInfo? {
    val place = placeOf(code) ?: return null
    return when (mode) {
        RegionMode.OFF -> null
        RegionMode.OFFICIAL -> stats.regions.firstOrNull { code in it.codes }
            ?.let { RegionTapInfo(place, it.region, it.been, it.total) }
        RegionMode.INFORMAL -> stats.informalRegions.firstOrNull { code in it.codes }
            ?.let { RegionTapInfo(place, it.name, it.been, it.total) }
    }
}

/**
 * Holds the live [MapLibreMap] and [Style] so Compose can push state into a view
 * that finishes loading long after the first composition.
 *
 * Every mutation is a no-op until the style is ready, and everything is re-applied
 * in [onStyleLoaded]; that keeps the ordering problem out of the composables.
 */
private class PlacesMapController {
    private var map: MapLibreMap? = null
    private var style: Style? = null

    private var appliedVisited: Set<String> = emptySet()
    private var appliedLived: Set<String> = emptySet()
    private var appliedWishlist: Set<String> = emptySet()
    private var appliedBands: Map<VisaBand, Set<String>> = emptyMap()
    private var appliedRegionBands: Map<RegionBand, Set<String>> = emptyMap()
    private var appliedSelection: String? = null
    private var appliedLens = false
    private var appliedRegionActive = false

    fun attach(map: MapLibreMap) {
        this.map = map
    }

    fun onStyleLoaded(style: Style) {
        this.style = style
        // Force a re-apply: this style object has fresh layers with empty filters.
        appliedVisited = emptySet()
        appliedLived = emptySet()
        appliedWishlist = emptySet()
        appliedBands = emptyMap()
        appliedRegionBands = emptyMap()
        appliedSelection = null
        appliedLens = false
        appliedRegionActive = false
    }

    fun detach() {
        map = null
        style = null
    }

    /**
     * While [lens] or [regionActive] is on the travel colours are cleared rather
     * than merely covered. The visa fills do cover all 252 places today, so
     * covering would look the same — but a place the asset had no answer for
     * would then show its travel colour under a visa legend, and "I have been
     * here" reading as a visa band is the one thing this map must never say.
     * The region heatmap has the same property for any place outside every
     * named grouping.
     */
    fun applyStatuses(statuses: Map<String, PlaceStatus>, lens: Boolean, regionActive: Boolean) {
        val style = style ?: return
        val effective = if (lens || regionActive) emptyMap() else statuses
        val visited = effective.filterValues { it == PlaceStatus.VISITED }.keys
        val lived = effective.filterValues { it == PlaceStatus.LIVED }.keys
        val wishlist = effective.filterValues { it == PlaceStatus.WISHLIST }.keys
        if (visited != appliedVisited) {
            style.getLayerAs<FillLayer>(PlacesMapStyle.LAYER_VISITED)
                ?.setFilter(PlacesMapStyle.onlyPlaces(visited))
            appliedVisited = visited.toSet()
        }
        if (lived != appliedLived) {
            style.getLayerAs<FillLayer>(PlacesMapStyle.LAYER_LIVED)
                ?.setFilter(PlacesMapStyle.onlyPlaces(lived))
            appliedLived = lived.toSet()
        }
        if (wishlist != appliedWishlist) {
            style.getLayerAs<FillLayer>(PlacesMapStyle.LAYER_WISHLIST)
                ?.setFilter(PlacesMapStyle.onlyPlaces(wishlist))
            appliedWishlist = wishlist.toSet()
        }
    }

    /** Which codes each visa band paints. Empty map means the lens is off. */
    fun applyVisa(bands: Map<VisaBand, Set<String>>) {
        val style = style ?: return
        if (bands == appliedBands) return
        appliedBands = bands
        VisaBand.entries.forEach { band ->
            style.getLayerAs<FillLayer>(PlacesMapStyle.visaLayerId(band))
                ?.setFilter(PlacesMapStyle.onlyPlaces(bands[band].orEmpty()))
        }
    }

    /** Which codes each region band paints. Empty map means the heatmap is off. */
    fun applyRegion(bands: Map<RegionBand, Set<String>>) {
        val style = style ?: return
        if (bands == appliedRegionBands) return
        appliedRegionBands = bands
        RegionBand.entries.forEach { band ->
            style.getLayerAs<FillLayer>(PlacesMapStyle.regionLayerId(band))
                ?.setFilter(PlacesMapStyle.onlyPlaces(bands[band].orEmpty()))
        }
    }

    fun applySelection(code: String?, lens: Boolean, regionActive: Boolean) {
        val style = style ?: return
        if (code == appliedSelection && lens == appliedLens && regionActive == appliedRegionActive) return
        appliedSelection = code
        appliedLens = lens
        appliedRegionActive = regionActive
        val outline =
            if (code == null) PlacesMapStyle.noPlaces() else PlacesMapStyle.onlyPlace(code)
        // Exactly one of the three outlines is armed, so no two of the travel,
        // visa and region views can ever draw over each other.
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_SELECTED)
            ?.setFilter(if (lens || regionActive) PlacesMapStyle.noPlaces() else outline)
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_VISA_SELECTED)
            ?.setFilter(if (lens) outline else PlacesMapStyle.noPlaces())
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_REGION_SELECTED)
            ?.setFilter(if (regionActive) outline else PlacesMapStyle.noPlaces())
    }

    fun showWorld() {
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(WORLD), 500)
    }

    /** Where the camera goes when search picks a place — its own bounding box,
     * padded so the outline is not flush against the screen edge. */
    fun flyTo(bounds: Bounds) {
        val latLngBounds = LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west)
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 80), 500)
    }

    /** Hit-test a tap, precise first and forgiving second. */
    fun placeCodeAt(point: PointF, slopPx: Float): String? {
        val map = map ?: return null
        val exact = map.queryRenderedFeatures(point, PlacesMapStyle.LAYER_FILL)
        val hit = exact.firstOrNull()
            ?: map.queryRenderedFeatures(
                RectF(
                    point.x - slopPx,
                    point.y - slopPx,
                    point.x + slopPx,
                    point.y + slopPx,
                ),
                PlacesMapStyle.LAYER_FILL,
            ).firstOrNull()
        return hit?.getStringProperty("code")
    }

    fun screenLocationOf(latLng: LatLng): PointF? = map?.projection?.toScreenLocation(latLng)

    /**
     * Which places are big enough on screen right now to name, and where. Uses
     * [MapLibreMap.getProjection] directly rather than the style, so this works
     * the instant the map is attached — it does not need to wait for the (async)
     * style load the fill layers do.
     */
    fun labelsFor(
        places: List<Place>,
        minFootprintPx: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): List<PlaceLabel> {
        val projection = map?.projection ?: return emptyList()
        val labels = ArrayList<PlaceLabel>(places.size)
        for (place in places) {
            val sw = projection.toScreenLocation(LatLng(place.bounds.south, place.bounds.west))
            val ne = projection.toScreenLocation(LatLng(place.bounds.north, place.bounds.east))
            val footprint = maxOf(kotlin.math.abs(ne.x - sw.x), kotlin.math.abs(sw.y - ne.y))
            if (footprint < minFootprintPx) continue
            val center = projection.toScreenLocation(LatLng(place.lat, place.lon))
            // Compose does not clip an offset child to its parent's bounds, so a
            // centroid just above the visible map (which does not extend behind the
            // status bar) would otherwise be drawn floating in that gap instead of
            // disappearing with the rest of the map content up there.
            if (center.x < 0f || center.x > viewportWidth || center.y < 0f || center.y > viewportHeight) {
                continue
            }
            labels.add(PlaceLabel(place.code, place.name, center.x, center.y))
        }
        return labels
    }

    /**
     * The rendered frame, exactly as the user is currently looking at it.
     *
     * Returns false without calling [onReady] if the map hasn't attached yet — a
     * snapshot taken then would never call back at all, rather than fail visibly.
     * A single null-check here (instead of a separate isReady() the caller checks
     * first) closes the gap where the map could detach between the check and the
     * call.
     */
    fun snapshot(onReady: (android.graphics.Bitmap) -> Unit): Boolean {
        val map = map ?: return false
        map.snapshot { bitmap -> onReady(bitmap) }
        return true
    }
}

@Composable
private fun PlacesMapView(
    controller: PlacesMapController,
    palette: MapPalette,
    statuses: Map<String, PlaceStatus>,
    visaBands: Map<VisaBand, Set<String>>,
    lens: Boolean,
    regionBands: Map<RegionBand, Set<String>>,
    regionActive: Boolean,
    selectedCode: String?,
    places: List<Place>,
    onLabels: (List<PlaceLabel>) -> Unit,
    onTap: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val slopPx = with(LocalDensity.current) { TAP_SLOP_DP.dp.toPx() }
    val minLabelFootprintPx = with(LocalDensity.current) { MIN_LABEL_FOOTPRINT_DP.dp.toPx() }

    // Style loading is asynchronous and can finish well after this composition —
    // long enough, on a cold start, for the real statuses to have already arrived
    // from Room. Read through rememberUpdatedState so the style-loaded callback
    // below paints whatever is current *when it fires*, not a stale snapshot from
    // whenever the effect was scheduled, which was painting the map blank until
    // some unrelated recomposition came along to correct it.
    val currentStatuses by rememberUpdatedState(statuses)
    val currentVisaBands by rememberUpdatedState(visaBands)
    val currentLens by rememberUpdatedState(lens)
    val currentRegionBands by rememberUpdatedState(regionBands)
    val currentRegionActive by rememberUpdatedState(regionActive)
    val currentSelectedCode by rememberUpdatedState(selectedCode)
    val currentPlaces by rememberUpdatedState(places)
    val currentOnLabels by rememberUpdatedState(onLabels)
    val currentOnTap by rememberUpdatedState(onTap)

    val mapView = remember {
        // Must run before any MapView is constructed. No style URL and no
        // WellKnownTileServer: there is no tile server in this app.
        MapLibre.getInstance(context)
        MapView(
            context,
            MapLibreMapOptions.createFromAttributes(context)
                .camera(WORLD)
                .minZoomPreference(0.0)
                .maxZoomPreference(9.0)
                // A flat political map gains nothing from pitch or bearing, and
                // both make it easy to lose your place with a stray two-finger
                // gesture.
                .rotateGesturesEnabled(false)
                .tiltGesturesEnabled(false)
                .compassEnabled(false)
                // Natural Earth is public domain, so there is nothing to attribute
                // and no reason to spend screen space on a logo.
                .logoEnabled(false)
                .attributionEnabled(false),
        ).apply { onCreate(null) }
    }

    // MapView is a plain Android view with its own lifecycle contract; Compose
    // does not know about it, so the events are forwarded by hand.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.detach()
            if (!mapView.isDestroyed) {
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    // The style is rebuilt when the palette changes so that switching to dark mode
    // recolours the map instead of leaving a light ocean under a dark UI.
    DisposableEffect(mapView, palette) {
        mapView.getMapAsync { map ->
            controller.attach(map)
            map.setStyle(PlacesMapStyle.builder(palette)) { style ->
                controller.onStyleLoaded(style)
                controller.applyStatuses(currentStatuses, currentLens, currentRegionActive)
                controller.applyVisa(currentVisaBands)
                controller.applyRegion(currentRegionBands)
                controller.applySelection(currentSelectedCode, currentLens, currentRegionActive)
            }
        }
        onDispose { }
    }

    // Labels track the camera: recomputed on every move so a pan feels live rather
    // than jumping into place once the gesture ends, and again on idle to settle
    // the count once zoom is done changing which places clear the footprint bar.
    DisposableEffect(mapView, minLabelFootprintPx) {
        val moveListener = MapLibreMap.OnCameraMoveListener {
            currentOnLabels(
                controller.labelsFor(
                    currentPlaces,
                    minLabelFootprintPx,
                    mapView.width.toFloat(),
                    mapView.height.toFloat(),
                ),
            )
        }
        val idleListener = MapLibreMap.OnCameraIdleListener {
            currentOnLabels(
                controller.labelsFor(
                    currentPlaces,
                    minLabelFootprintPx,
                    mapView.width.toFloat(),
                    mapView.height.toFloat(),
                ),
            )
        }
        mapView.getMapAsync { map ->
            map.addOnCameraMoveListener(moveListener)
            map.addOnCameraIdleListener(idleListener)
        }
        onDispose {
            if (!mapView.isDestroyed) {
                mapView.getMapAsync { map ->
                    map.removeOnCameraMoveListener(moveListener)
                    map.removeOnCameraIdleListener(idleListener)
                }
            }
        }
    }

    // The registry loads asynchronously and can arrive well after the map itself,
    // so labels need their own trigger independent of camera movement.
    DisposableEffect(mapView, places, minLabelFootprintPx) {
        mapView.getMapAsync {
            onLabels(
                controller.labelsFor(
                    places,
                    minLabelFootprintPx,
                    mapView.width.toFloat(),
                    mapView.height.toFloat(),
                ),
            )
        }
        onDispose { }
    }

    // Keyed on mapView alone, through rememberUpdatedState like every other
    // callback above: the listener is native (MapLibre), long-lived, and can
    // fire minutes after it was registered, so it must always read whatever
    // onTap is current *now* rather than whatever closure it happened to
    // capture at registration time.
    DisposableEffect(mapView) {
        val listener = MapLibreMap.OnMapClickListener { latLng ->
            val point = controller.screenLocationOf(latLng)
            val code = point?.let { controller.placeCodeAt(it, slopPx) }
            // A tap on open ocean clears the selection rather than doing nothing,
            // which is the cheapest possible way to dismiss the detail card.
            currentOnTap(code)
            true
        }
        mapView.getMapAsync { it.addOnMapClickListener(listener) }
        onDispose {
            if (!mapView.isDestroyed) {
                mapView.getMapAsync { it.removeOnMapClickListener(listener) }
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = {
            controller.applyStatuses(statuses, lens, regionActive)
            controller.applyVisa(visaBands)
            controller.applyRegion(regionBands)
            controller.applySelection(selectedCode, lens, regionActive)
        },
    )
}

/**
 * A floating chip over the map. Semi-transparent so it reads as an overlay rather
 * than as part of the geography underneath it.
 */
@Composable
private fun MapPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
    }
}

/**
 * The lens' legend: the ramp itself, the headline count, and the dataset date.
 *
 * The date is not optional decoration. Visa rules change without notice and this asset
 * is a snapshot, so PLAN.md requires every visa surface to say how old it is and to
 * point at an official source — the map's share of that is this line.
 */
@Composable
private fun VisaLegend(ramp: VisaRamp, visa: VisaRegistry?) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Touching segments rather than the separated dots the travel legend uses:
            // these four are an ordered scale, and a scale reads as one when its steps
            // are adjacent.
            Row(Modifier.clip(MaterialTheme.shapes.extraSmall)) {
                VisaBand.entries.forEach { band ->
                    Box(
                        Modifier
                            .size(width = 14.dp, height = 10.dp)
                            .background(ramp.colorFor(band))
                    )
                }
            }
            Text(
                if (visa == null) "No visa data" else "${visa.visaFreeCount} visa-free",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (visa != null) {
            Text(
                asOfNotice(visa),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The heatmap's legend: the ramp itself and how many of the groupings it is
 * currently painting are fully done.
 *
 * No date line, unlike [VisaLegend] — this is derived entirely from [Place.region]
 * and the hand-curated informal sets already bundled with the app, not from a
 * dated external asset, so there is nothing here that goes stale.
 */
@Composable
private fun RegionLegend(ramp: RegionRamp, mode: RegionMode, stats: TravelStats) {
    val totals = if (mode == RegionMode.OFFICIAL) {
        stats.regions.map { it.been to it.total }
    } else {
        stats.informalRegions.map { it.been to it.total }
    }
    val done = totals.count { (been, total) -> been == total && total > 0 }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.clip(MaterialTheme.shapes.extraSmall)) {
            RegionBand.entries.forEach { band ->
                Box(
                    Modifier
                        .size(width = 14.dp, height = 10.dp)
                        .background(ramp.colorFor(band))
                )
            }
        }
        Text(
            "$done of ${totals.size} " +
                if (mode == RegionMode.OFFICIAL) "regions done" else "groupings done",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CounterLegendRow(color: Color, count: Int, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(color)
        Text("$count $label", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * What a tap opens in region mode, in place of [SelectedPlaceCard].
 *
 * The heatmap is answering "how done is this region", not "mark this place" —
 * so a tap here has nothing to toggle, only the region's name and completion
 * for the place that was tapped.
 */
@Composable
private fun RegionInfoCard(info: RegionTapInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                info.place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                info.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${info.been} / ${info.total} done",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * What a tap opens. Deliberately a card and not a modal sheet: the map stays
 * visible and interactive, so tapping a second country just swaps the card
 * instead of forcing a dismiss first.
 *
 * It carries no Close button either, for the same reason: a tap on open ocean
 * already clears the selection, so a button would only occupy the one thing this
 * screen cannot spare — space over the map — to duplicate a gesture the card
 * itself is proof of.
 */
@Composable
private fun SelectedPlaceCard(
    place: Place,
    status: PlaceStatus,
    requirement: VisaRequirement?,
    onToggle: (PlaceStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildString {
                    append(place.code)
                    append(" · ")
                    append(place.continent)
                    // Shown only where it says something new: "Greenland (Denmark)"
                    // is the point of this app, "France (France)" is noise.
                    if (place.sovereign != place.name) {
                        append(" · ")
                        append(place.sovereign)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (requirement != null) {
                Text(
                    requirementSummary(requirement),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // The curated note is the answer for exactly the places this app was
                // built for — Greenland, French Guiana, Sint Maarten — so it is shown
                // here in full rather than being reserved for a detail screen.
                requirement.note?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (requirement.confidence == VisaConfidence.LOW) {
                    Text(
                        VisaConfidence.LOW.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Short labels, matching the passport rows. Three chips reading
                // "Been here / Lived here / Want to go" overflow a 360 dp phone once
                // the card's padding is taken off, and a wrapped or scrolling row of
                // three would hide one of the three answers behind a gesture.
                BounceFilterChip(
                    selected = status == PlaceStatus.VISITED,
                    label = "Been",
                    onClick = { onToggle(PlaceStatus.VISITED) },
                )
                BounceFilterChip(
                    selected = status == PlaceStatus.LIVED,
                    label = "Lived",
                    onClick = { onToggle(PlaceStatus.LIVED) },
                )
                BounceFilterChip(
                    selected = status == PlaceStatus.WISHLIST,
                    label = "Want",
                    onClick = { onToggle(PlaceStatus.WISHLIST) },
                )
            }
        }
    }
}

/**
 * The passport's own search, on the map: the same [Place.searchKey] match, capped
 * at a handful of results because this is a picker, not a second passport list.
 */
private const val MAP_SEARCH_LIMIT = 5

internal fun searchMapMatches(places: List<Place>, query: String): List<Place> {
    val needle = query.trim().foldForSearch()
    if (needle.isEmpty()) return emptyList()
    return places.filter { it.searchKey.contains(needle) }
        .sortedBy { it.sortKey }
        .take(MAP_SEARCH_LIMIT)
}
