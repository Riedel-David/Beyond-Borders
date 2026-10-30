package com.riedel.beyondborders.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riedel.beyondborders.R
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
import com.riedel.beyondborders.ui.UiText
import com.riedel.beyondborders.ui.asString
import com.riedel.beyondborders.ui.passport.SearchField
import com.riedel.beyondborders.ui.places.MapCamera
import com.riedel.beyondborders.ui.places.PlacesViewModel
import com.riedel.beyondborders.ui.places.RegionBand
import com.riedel.beyondborders.ui.places.RegionMode
import com.riedel.beyondborders.ui.places.RegionStyleKey
import com.riedel.beyondborders.ui.places.TravelStats
import com.riedel.beyondborders.ui.places.combinedBandCounts
import com.riedel.beyondborders.ui.places.nextRegionMode
import com.riedel.beyondborders.ui.theme.LocalMapPalette
import com.riedel.beyondborders.ui.theme.MapPalette
import com.riedel.beyondborders.ui.theme.RegionRamp
import com.riedel.beyondborders.ui.theme.RegionSlots
import com.riedel.beyondborders.ui.theme.VisaRamp
import com.riedel.beyondborders.ui.visa.asOfNoticeText
import com.riedel.beyondborders.ui.visa.requirementSummaryText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

private fun MapCamera.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(LatLng(latitude, longitude))
    .zoom(zoom)
    .build()

/**
 * How forgiving a tap is, in dp. Tapping Monaco or Singapore at world zoom means
 * hitting a two-pixel shape, so a miss falls back to a square of this radius
 * around the finger.
 */
private const val TAP_SLOP_DP = 16f

/** Outlives the Map tab; see the share functions in [MapScreen]. */
// lazy: Dispatchers.Main throws on the host JVM, and a top-level initialiser would
// take every host test of this file's pure functions down with it.
private val shareScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

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
fun MapScreen(
    viewModel: PlacesViewModel,
    // The root passes the Scaffold's own PaddingValues through here rather than
    // relying on WindowInsets.safeDrawing: the Scaffold is what actually knows the
    // navigation bar's height, and safeDrawing alone would not account for it once
    // the map opts out of the Scaffold's content padding to draw full-bleed.
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val visa by viewModel.visa.collectAsStateWithLifecycle()
    val secondVisa by viewModel.secondVisa.collectAsStateWithLifecycle()
    val visaBands by viewModel.visaBands.collectAsStateWithLifecycle()
    val visaNoData by viewModel.visaNoData.collectAsStateWithLifecycle()
    val lens by viewModel.visaLens.collectAsStateWithLifecycle()
    val regionMode by viewModel.regionMode.collectAsStateWithLifecycle()
    val regionBands by viewModel.regionBands.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val palette = LocalMapPalette.current

    val controller = remember { PlacesMapController() }
    val selected = selectedCode?.let(viewModel::placeOf)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // rememberSaveable because the root disposes this screen on a tab switch (the
    // SaveableStateProvider above it restores these on return).
    var counterLabeled by rememberSaveable { mutableStateOf(false) }
    var visaLegendLabeled by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Collapsed by default: Search/Visa/Regions stacking up every time made the
    // top-right corner read as clutter rather than controls. One icon expands the
    // stack on demand instead of a third-and-growing always-visible pill. A "World"
    // pill used to live here too and was dropped: pinching back out is the gesture
    // everyone already knows, so the button only cost space.
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    var showShareStyleDialog by remember { mutableStateOf(false) }
    // Local to this screen rather than hoisted: a failed share is a map-only
    // concern, and nothing else on this tab needs to post to it.
    val snackbarHostState = remember { SnackbarHostState() }
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

    // Share work runs in a scope that outlives this screen: the snapshot callback
    // and the caption/PNG work finish after the user may have left the Map tab, and
    // a scope cancelled with the composition dropped the share without a word.
    // [onScreen] says whether the snackbar still has a host to show on.
    var onScreen by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { onScreen = false } }

    fun reportShareFailure(retry: () -> Unit) {
        if (!onScreen) {
            Toast.makeText(context.applicationContext, resources.getString(R.string.map_share_failed), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.map_share_failed),
                actionLabel = resources.getString(R.string.map_share_retry),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) retry()
        }
    }

    fun shareStats() {
        shareScope.launch {
            runCatching { shareStatsCard(context, stats) }
                .onFailure { reportShareFailure(::shareStats) }
        }
    }

    fun shareSnapshot(style: ShareCardStyle) {
        // The map's snapshot callback never fires while the style is still
        // loading, which otherwise left a tap doing nothing at all with no sign
        // anything was wrong.
        val started = controller.snapshot { bitmap ->
            shareScope.launch {
                runCatching { shareMapSnapshot(context, bitmap, stats, style) }
                    .onFailure { reportShareFailure { shareSnapshot(style) } }
            }
        }
        if (!started) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.map_not_ready),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    // Switching the heatmap on mid-selection would otherwise leave a marking card
    // showing over a map that no longer means "tap to mark".
    // Keyed on the mode itself, not just on/off: OFFICIAL <-> INFORMAL keeps
    // regionActive true, and the old card and outline described the other mode.
    LaunchedEffect(regionMode) {
        regionInfo = null
        if (regionActive) viewModel.select(null)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The legend pill (top-left) and the control stack (top-right) share one
        // row; at large font scales or on narrow screens their natural widths
        // overlap, so each gets a fixed share of what is left after the padding.
        val direction = LocalLayoutDirection.current
        val rowWidth = maxWidth - 24.dp -
            contentPadding.calculateStartPadding(direction) - contentPadding.calculateEndPadding(direction)
        val pillMaxWidth = minOf(240.dp, rowWidth * 0.55f)
        val controlsMaxWidth = rowWidth * 0.45f - 8.dp
        PlacesMapView(
            controller = controller,
            palette = palette,
            statuses = statuses,
            visaBands = visaBands,
            visaNoData = visaNoData,
            lens = lens,
            regionBands = regionBands,
            regionActive = regionActive,
            selectedCode = selectedCode,
            regionOutline = regionInfo?.codes.orEmpty(),
            places = places,
            onTap = ::tap,
            initialCamera = viewModel.mapCamera,
            onCameraIdle = { viewModel.mapCamera = it },
            modifier = Modifier.fillMaxSize(),
        )

        // Overlays sit on top of the map surface, deliberately compact: the map is
        // the point, and chrome that covers the Mediterranean is chrome too much.
        MapPill(
            // Only worth a toggle once there is something to spell out; the empty
            // state's nudge and the region heatmap's own legend both stay static.
            onClick = when {
                lens -> {
                    {
                        viewModel.vibrate()
                        visaLegendLabeled = !visaLegendLabeled
                    }
                }
                !regionActive && (stats.been > 0 || stats.wishlist > 0) -> {
                    {
                        viewModel.vibrate()
                        counterLabeled = !counterLabeled
                    }
                }
                else -> null
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(contentPadding)
                .padding(12.dp)
                // The asOf notice is a full sentence with no line break of its own,
                // and this pill sits in the same unbounded Box as the ⋮ stack on
                // the opposite corner — without a cap, a long enough sentence (two
                // passports named instead of one) renders on one line right across
                // the screen and under the ⋮ button instead of wrapping under itself.
                .widthIn(max = pillMaxWidth),
        ) {
            // AnimatedContent, not animateContentSize on the pill: the pill's size
            // animated while its content swapped in a single frame, so expanding read
            // as smooth growth and collapsing read as the labels vanishing instantly.
            // Crossfading the content and animating the size in one transition makes
            // the two directions symmetric. The kind is what the transition keys on,
            // so the outgoing content keeps drawing its own labelled or compact form
            // while it fades out instead of jumping to the incoming one.
            AnimatedContent(
                targetState = mapLegendKind(
                    lens = lens,
                    visaLabeled = visaLegendLabeled,
                    regionMode = regionMode,
                    counterLabeled = counterLabeled,
                    been = stats.been,
                    wishlist = stats.wishlist,
                ),
                label = "legend",
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(180)),
                        initialContentExit = fadeOut(tween(120)),
                        sizeTransform = SizeTransform(clip = false) { _, _ -> tween(180) },
                    )
                },
            ) { kind ->
                when (kind) {
                    MapLegendKind.VISA_DOTS, MapLegendKind.VISA_LABELED -> VisaLegend(
                        palette.visa,
                        visa,
                        secondVisa,
                        noDataCount = visaNoData.size,
                        labeled = kind == MapLegendKind.VISA_LABELED,
                    )
                    MapLegendKind.REGION_OFFICIAL ->
                        RegionLegend(palette.region, palette.unvisited, RegionMode.OFFICIAL, stats)
                    MapLegendKind.REGION_INFORMAL ->
                        RegionLegend(palette.region, palette.unvisited, RegionMode.INFORMAL, stats)
                    // Nothing marked yet, so the counter would say "0" and teach
                    // nothing. Say what to do instead.
                    MapLegendKind.NUDGE -> Text(
                        stringResource(R.string.map_legend_nudge),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    MapLegendKind.COUNTER_LABELED -> Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CounterLegendRow(palette.visited, stringResource(R.string.map_legend_visited, stats.visited))
                        if (stats.lived > 0) CounterLegendRow(palette.lived, stringResource(R.string.map_legend_lived, stats.lived))
                        if (stats.wishlist > 0) {
                            CounterLegendRow(palette.wishlist, stringResource(R.string.map_legend_wanted, stats.wishlist))
                        }
                    }
                    MapLegendKind.COUNTER_DOTS -> {
                        // Resolved here because the semantics block below is not composable.
                        val legendDescription = travelLegendDescription(stats).asString()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            // TalkBack would otherwise read the dots and bare numbers as
                            // separate nodes ("40 · 3 · 7") with nothing to say what each
                            // one counts; clearAndSetSemantics replaces that with one
                            // sentence and stops the numbers being announced twice.
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = legendDescription
                            },
                        ) {
                            // One dot per map colour, and only for colours actually on the
                            // map: this is a legend, so it counts each status strictly
                            // rather than showing the combined "been" total the passport
                            // leads with.
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
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(contentPadding)
                .padding(12.dp)
                // The stack grows by a pill's height each time one more control
                // (search, visa, regions) is revealed on expand. It also changes width
                // whenever a pill's label does ("Search"/"Close", "Regions"/"Regions:
                // named"), and the stack hangs off the right edge: the default TopStart
                // alignment anchored the content to the left of the animating box, so
                // every right-aligned pill, the ⋮ included, slid sideways for 180 ms.
                .widthIn(max = controlsMaxWidth)
                .animateContentSize(animationSpec = tween(180), alignment = Alignment.TopEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapPill(
                onClick = {
                    viewModel.vibrate()
                    controlsExpanded = !controlsExpanded
                    // Collapsing hides the search field along with everything else,
                    // so leaving it open underneath would reopen it silently the
                    // next time the stack expands.
                    if (!controlsExpanded) searchOpen = false
                },
            ) {
                Icon(
                    if (controlsExpanded) Icons.Filled.Close else Icons.Filled.MoreVert,
                    contentDescription = stringResource(
                        if (controlsExpanded) R.string.map_controls_hide else R.string.map_controls_show,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (!controlsExpanded) return@Column
            // Search is the passport's own search, on the map — the same reason
            // both screens share the [PlacesViewModel] query, this just is not
            // routed through it, since the map wants to fly the camera on a pick
            // rather than filter a list.
            MapPill(onClick = { viewModel.vibrate(); searchOpen = !searchOpen }) {
                Text(
                    stringResource(if (searchOpen) R.string.map_search_close else R.string.map_search_open),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (searchOpen) {
                Card(
                    // Same reason as the visa legend's cap: nothing between this
                    // Card and the screen-filling Box constrains its width, and
                    // SearchField's own fillMaxWidth() would otherwise stretch it
                    // to the full screen instead of staying a corner control.
                    modifier = Modifier.widthIn(max = 280.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        SearchField(
                            query = searchQuery,
                            onQuery = { searchQuery = it },
                            label = stringResource(R.string.map_search_label),
                        )
                        if (searchQuery.isNotBlank() && searchMatches.isEmpty()) {
                            Text(
                                stringResource(R.string.map_search_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                        } else {
                            // Capped and scrollable rather than trusting
                            // MAP_SEARCH_LIMIT alone: five rows plus the field
                            // above can still outgrow a short phone's screen.
                            Column(
                                Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                searchMatches.forEach { place ->
                                    Text(
                                        place.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { pickFromSearch(place) }
                                            // The row's own padding only gets it to ~36 dp;
                                            // this raises the touch target to 48 dp without
                                            // adding visible space between rows.
                                            .minimumInteractiveComponentSize()
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Offered only once the asset is known to have loaded: a toggle that
            // silently paints nothing would be worse than no toggle.
            if (visa != null) {
                MapPill(onClick = { viewModel.vibrate(); viewModel.setVisaLens(!lens) }) {
                    Text(
                        stringResource(if (lens) R.string.map_lens_travels else R.string.map_lens_visa),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // One pill cycles all three states rather than three pills for one
            // choice — Off/Official/Informal is a single question, not three.
            MapPill(onClick = { viewModel.vibrate(); viewModel.setRegionMode(nextRegionMode(regionMode)) }) {
                Text(
                    stringResource(
                        when (regionMode) {
                            RegionMode.OFF -> R.string.map_regions_off
                            RegionMode.OFFICIAL -> R.string.map_regions_official
                            RegionMode.INFORMAL -> R.string.map_regions_informal
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // AnimatedVisibility keeps rendering its content through the exit fade, so
        // the last non-null place/region is held here a beat after the live state
        // clears — otherwise the card's content would go blank on the same frame
        // the fade starts, and the fade would have nothing to show.
        //
        // Both cards read the live value *first* and fall back to the held one. Held
        // alone was wrong: it is written during composition, so on the frame a place
        // is first selected the card could compose with nothing in it — and an empty
        // surfaceContainerHigh card is a light grey box, which is exactly what showed
        // up on the device for a frame before the name appeared.
        var lastRegionInfo by remember { mutableStateOf(regionInfo) }
        if (regionInfo != null) lastRegionInfo = regionInfo
        var lastSelected by remember { mutableStateOf(selected) }
        if (selected != null) lastSelected = selected

        AnimatedVisibility(
            visible = regionActive && regionInfo != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                // The share FAB sits at BottomEnd over this same row; without a
                // matching end inset this card's fillMaxWidth() runs full-width
                // straight under it.
                .padding(start = 12.dp, top = 12.dp, end = 88.dp, bottom = 12.dp),
        ) {
            (regionInfo ?: lastRegionInfo)?.let { info -> RegionInfoCard(info = info) }
        }
        AnimatedVisibility(
            visible = !regionActive && selected != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding),
        ) {
            (selected ?: lastSelected)?.let { place ->
                // Carried into the card whenever the lens is on, so a tap answers
                // the question the map is currently asking rather than the other
                // one. Goes through the same combined lookup the map's own paint
                // uses (bandCodes unions both passports), so the card can never
                // show an answer that contradicts the colour the place is drawn in.
                val combined = if (lens) viewModel.combinedRequirementOf(place.code) else null
                val attributeTo = if (combined != null && !combined.fromPrimary && secondVisa != null) {
                    secondVisa!!.passport
                } else {
                    null
                }
                SelectedPlaceCard(
                    place = place,
                    status = statuses[place.code] ?: PlaceStatus.NONE,
                    requirement = combined?.winner,
                    attributeTo = attributeTo,
                    noVisaData = lens && place.code in visaNoData,
                    hasSecondPassport = secondVisa != null,
                    // The visa lens is answering "can I go", not "have I been" — the
                    // travel-status chips are the other lens's question, and showing
                    // both on the same tap reads as if they're related.
                    showStatusToggle = !lens,
                    onToggle = { target ->
                        viewModel.vibrate()
                        viewModel.toggle(place.code, target)
                    },
                )
            }
        }

        // The region/selection cards above anchor to BottomCenter and reserve an
        // end inset (see their modifiers) so this FAB's corner stays clear of them.
        // Hidden while a place is selected: the flag banner owns the bottom edge then.
        if (selected == null) FloatingActionButton(
            onClick = {
                viewModel.vibrate()
                showShareStyleDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(contentPadding)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.map_share_action))
        }

        if (showShareStyleDialog) {
            ShareStyleDialog(
                onDismiss = { showShareStyleDialog = false },
                onStylePicked = { style ->
                    showShareStyleDialog = false
                    // No map bitmap involved for the stats-only style, so there is
                    // nothing to wait on the map controller for.
                    if (style == ShareCardStyle.STATS_ONLY) shareStats() else shareSnapshot(style)
                },
            )
        }

        // Hosted inside this Box, not at the Scaffold level, because the map opts
        // out of the Scaffold's padding — a host placed there would sit under the
        // navigation bar on the map tab. contentPadding keeps it clear the same
        // way the overlay cards above are kept clear.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding),
        )
    }
}

/**
 * Lets the user choose one of three share styles. Each row is a single clickable
 * target with label and blurb, meeting the 48 dp minimum without nested clicks.
 */
@Composable
private fun ShareStyleDialog(
    onDismiss: () -> Unit,
    onStylePicked: (ShareCardStyle) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_share_style_title)) },
        text = {
            Column {
                ShareCardStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            // Role, so TalkBack announces a button rather than an
                            // unlabelled clickable — the same reason the passport
                            // picker's rows carry one.
                            .clickable(role = Role.Button) { onStylePicked(style) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(style.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(style.blurbRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_share_cancel)) } },
    )
}

/** Which region/grouping a tapped place belongs to, in the mode that was active. */
private data class RegionTapInfo(
    val place: Place,
    val name: String,
    val been: Int,
    val total: Int,
    val codes: Set<String>,
)

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
            ?.let { RegionTapInfo(place, it.region, it.been, it.total, it.codes) }
        RegionMode.INFORMAL -> stats.informalRegions.firstOrNull { code in it.codes }
            ?.let { RegionTapInfo(place, it.name, it.been, it.total, it.codes) }
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
    private var appliedNoData: Set<String> = emptySet()
    private var appliedRegionBands: Map<RegionStyleKey, Set<String>> = emptyMap()
    private var appliedSelection: String? = null
    private var appliedLens = false
    private var appliedRegionActive = false
    private var appliedRegionOutline: Set<String> = emptySet()

    fun attach(map: MapLibreMap) {
        this.map = map
        // A new style is about to load (first load or a palette change); until it
        // lands, the old one belongs to layers the map is discarding.
        style = null
    }

    fun onStyleLoaded(style: Style) {
        this.style = style
        // Force a re-apply: this style object has fresh layers with empty filters.
        appliedVisited = emptySet()
        appliedLived = emptySet()
        appliedWishlist = emptySet()
        appliedBands = emptyMap()
        appliedNoData = emptySet()
        appliedRegionBands = emptyMap()
        appliedSelection = null
        appliedLens = false
        appliedRegionOutline = emptySet()
        appliedRegionActive = false
    }

    fun detach() {
        map = null
        style = null
    }

    /**
     * While [lens] or [regionActive] is on the travel colours are cleared rather
     * than merely covered — a place the asset has no answer for would otherwise
     * show its travel colour under a visa legend, and "I have been here" reading
     * as a visa band is the one thing this map must never say. [applyVisa]'s own
     * grey no-data layer is what actually covers those places once cleared. The
     * region heatmap has the same property for any place outside every named
     * grouping.
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

    /**
     * Which codes each visa band paints, plus [noData]: places this passport's
     * dataset has no answer for, painted grey instead of falling through to
     * unmarked land. Empty means the lens is off.
     */
    fun applyVisa(bands: Map<VisaBand, Set<String>>, noData: Set<String>) {
        val style = style ?: return
        if (bands != appliedBands) {
            appliedBands = bands
            VisaBand.entries.forEach { band ->
                style.getLayerAs<FillLayer>(PlacesMapStyle.visaLayerId(band))
                    ?.setFilter(PlacesMapStyle.onlyPlaces(bands[band].orEmpty()))
            }
        }
        if (noData != appliedNoData) {
            appliedNoData = noData
            style.getLayerAs<FillLayer>(PlacesMapStyle.LAYER_VISA_NO_DATA)
                ?.setFilter(PlacesMapStyle.onlyPlaces(noData))
        }
    }

    /** Which codes each (slot, band) layer paints. Empty map means the heatmap is off. */
    fun applyRegion(bands: Map<RegionStyleKey, Set<String>>) {
        val style = style ?: return
        if (bands == appliedRegionBands) return
        // A key that vanished from [bands] is in the changed set and gets an empty filter.
        val changed = changedRegionKeys(appliedRegionBands, bands)
        appliedRegionBands = bands
        for (key in changed) {
            style.getLayerAs<FillLayer>(PlacesMapStyle.regionLayerId(key.slot, key.band))
                ?.setFilter(PlacesMapStyle.onlyPlaces(bands[key].orEmpty()))
        }
    }

    fun applySelection(code: String?, lens: Boolean, regionActive: Boolean, regionOutline: Set<String>) {
        val style = style ?: return
        if (code == appliedSelection && lens == appliedLens && regionActive == appliedRegionActive &&
            regionOutline == appliedRegionOutline
        ) return
        appliedSelection = code
        appliedLens = lens
        appliedRegionActive = regionActive
        appliedRegionOutline = regionOutline
        val outline =
            if (code == null) PlacesMapStyle.noPlaces() else PlacesMapStyle.onlyPlace(code)
        // Exactly one of the three outlines is armed, so no two of the travel,
        // visa and region views can ever draw over each other.
        val travelFilter = if (lens || regionActive) PlacesMapStyle.noPlaces() else outline
        val visaFilter = if (lens) outline else PlacesMapStyle.noPlaces()
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_SELECTED_CASING)?.setFilter(travelFilter)
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_SELECTED)?.setFilter(travelFilter)
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_VISA_SELECTED_CASING)?.setFilter(visaFilter)
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_VISA_SELECTED)?.setFilter(visaFilter)
        val regionFilter =
            if (regionActive) PlacesMapStyle.onlyPlaces(regionOutline) else PlacesMapStyle.noPlaces()
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_REGION_SELECTED_CASING)?.setFilter(regionFilter)
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_REGION_SELECTED)?.setFilter(regionFilter)
    }

    /** Where the camera goes when search picks a place — its own bounding box,
     * padded so the outline is not flush against the screen edge. */
    fun flyTo(bounds: Bounds) {
        val latLngBounds = LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west)
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 80), 500)
    }

    /**
     * Hit-test a tap, precise first and forgiving second.
     *
     * Queries the label layer alongside the fill: a long country name can
     * overflow past its own polygon (small country, long name), so a tap that
     * lands on the label glyphs rather than the shape must still resolve.
     * Both layers key on the same `code` property, so it doesn't matter which
     * one supplied the hit.
     */
    fun placeCodeAt(point: PointF, slopPx: Float): String? {
        val map = map ?: return null
        val exact = map.queryRenderedFeatures(point, PlacesMapStyle.LAYER_FILL, PlacesMapStyle.LAYER_LABELS)
        val hit = exact.firstOrNull()
            ?: map.queryRenderedFeatures(
                RectF(
                    point.x - slopPx,
                    point.y - slopPx,
                    point.x + slopPx,
                    point.y + slopPx,
                ),
                PlacesMapStyle.LAYER_FILL,
                PlacesMapStyle.LAYER_LABELS,
            ).firstOrNull()
        return hit?.getStringProperty("code")
    }

    fun screenLocationOf(latLng: LatLng): PointF? = map?.projection?.toScreenLocation(latLng)

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
    visaNoData: Set<String>,
    lens: Boolean,
    regionBands: Map<RegionStyleKey, Set<String>>,
    regionActive: Boolean,
    selectedCode: String?,
    regionOutline: Set<String>,
    places: List<Place>,
    onTap: (String?) -> Unit,
    initialCamera: MapCamera?,
    onCameraIdle: (MapCamera) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val slopPx = with(LocalDensity.current) { TAP_SLOP_DP.dp.toPx() }

    // Style loading is asynchronous and can finish well after this composition —
    // long enough, on a cold start, for the real statuses to have already arrived
    // from Room. Read through rememberUpdatedState so the style-loaded callback
    // below paints whatever is current *when it fires*, not a stale snapshot from
    // whenever the effect was scheduled, which was painting the map blank until
    // some unrelated recomposition came along to correct it.
    val currentStatuses by rememberUpdatedState(statuses)
    val currentVisaBands by rememberUpdatedState(visaBands)
    val currentVisaNoData by rememberUpdatedState(visaNoData)
    val currentLens by rememberUpdatedState(lens)
    val currentRegionBands by rememberUpdatedState(regionBands)
    val currentRegionActive by rememberUpdatedState(regionActive)
    val currentSelectedCode by rememberUpdatedState(selectedCode)
    val currentRegionOutline by rememberUpdatedState(regionOutline)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)

    val mapView = remember {
        // Must run before any MapView is constructed. No style URL and no
        // WellKnownTileServer: there is no tile server in this app.
        MapLibre.getInstance(context)
        MapView(
            context,
            MapLibreMapOptions.createFromAttributes(context)
                .camera(initialCamera?.toCameraPosition() ?: WORLD)
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
                // The observer may never have seen ON_PAUSE for this view (the
                // owner is still RESUMED when the tab is left), so pause first to
                // keep the order MapView expects: pause, stop, destroy.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    // onLowMemory is the one call MapLibre needs that no lifecycle event carries — it
    // drops cached glyph atlases and rasterised tiles. Without it the app held all of
    // that under memory pressure and made itself a fatter target for the low-memory
    // killer than it needed to be. Registered only while the map is composed, since
    // that is the only time there is anything to drop.
    DisposableEffect(mapView) {
        val app = context.applicationContext
        val callbacks = object : ComponentCallbacks2 {
            // The RUNNING_* levels are deprecated because newer releases stop sending
            // them, but minSdk 30 phones still do, and this is the one that matters there.
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && !mapView.isDestroyed) {
                    mapView.onLowMemory()
                }
            }

            override fun onLowMemory() {
                if (!mapView.isDestroyed) mapView.onLowMemory()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }
        app.registerComponentCallbacks(callbacks)
        onDispose { app.unregisterComponentCallbacks(callbacks) }
    }

    // The style is rebuilt when the palette changes so that switching to dark mode
    // recolours the map instead of leaving a light ocean under a dark UI.
    DisposableEffect(mapView, palette) {
        // Style loading is asynchronous and nothing cancels it, so a tab switch or a
        // dark-mode toggle mid-load used to let this callback land after detach() had
        // already nulled the controller's style — repopulating it, and then running
        // setFilter against a style belonging to a map that no longer exists.
        var disposed = false
        mapView.getMapAsync { map ->
            if (disposed) return@getMapAsync
            controller.attach(map)
            map.setStyle(PlacesMapStyle.builder(palette)) { style ->
                if (disposed) return@setStyle
                controller.onStyleLoaded(style)
                controller.applyStatuses(currentStatuses, currentLens, currentRegionActive)
                controller.applyVisa(currentVisaBands, currentVisaNoData)
                controller.applyRegion(currentRegionBands)
                controller.applySelection(currentSelectedCode, currentLens, currentRegionActive, currentRegionOutline)
            }
        }
        onDispose { disposed = true }
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

    // Saved as the camera settles rather than on dispose: by the time this composable
    // is disposed the MapView may already have been destroyed by the lifecycle
    // observer above, and reading cameraPosition off a destroyed map is not something
    // to rely on the two effects' teardown order to avoid.
    DisposableEffect(mapView) {
        var registered: MapLibreMap.OnCameraIdleListener? = null
        mapView.getMapAsync { map ->
            val listener = MapLibreMap.OnCameraIdleListener {
                val position = map.cameraPosition
                val target = position.target ?: return@OnCameraIdleListener
                currentOnCameraIdle(MapCamera(target.latitude, target.longitude, position.zoom))
            }
            registered = listener
            map.addOnCameraIdleListener(listener)
        }
        onDispose {
            val listener = registered
            if (listener != null && !mapView.isDestroyed) {
                mapView.getMapAsync { it.removeOnCameraIdleListener(listener) }
            }
        }
    }

    val mapDescription = stringResource(R.string.map_description)
    AndroidView(
        factory = { mapView },
        modifier = modifier.semantics {
            contentDescription = mapDescription
        },
        update = {
            controller.applyStatuses(statuses, lens, regionActive)
            controller.applyVisa(visaBands, visaNoData)
            controller.applyRegion(regionBands)
            controller.applySelection(selectedCode, lens, regionActive, regionOutline)
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
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = color, shadowElevation = 2.dp) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
        }
    } else {
        // Surface's own onClick, not Modifier.clickable wrapped around it. Clickable
        // sat above the Surface, so its ripple was drawn against the 48 dp touch box
        // rather than inside the pill's rounded shape — a light grey rectangle
        // flashing around the legend and the ⋮ on every tap. Surface clips the ripple
        // to its own shape and keeps the touch target, which is what these pills
        // needed: they float over the map, so the drawn chip stays ~40 dp while the
        // hit box is 48.
        Surface(
            onClick = onClick,
            modifier = modifier.minimumInteractiveComponentSize(),
            shape = shape,
            color = color,
            shadowElevation = 2.dp,
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
        }
    }
}

/**
 * The lens' legend: the ramp itself, the headline count, and the dataset date.
 *
 * The date is not optional decoration. Visa rules change without notice and this asset
 * is a snapshot, so every visa surface is required to say how old it is and to
 * point at an official source — the map's share of that is this line.
 *
 * A tap expands the dot row into one labelled row per band, same trick as the travel
 * legend's own [counterLabeled] toggle — a compact glance is the default, and the
 * words are one tap away rather than always taking the space.
 */
@Composable
private fun VisaLegend(
    ramp: VisaRamp,
    visa: VisaRegistry?,
    secondVisa: VisaRegistry?,
    noDataCount: Int,
    labeled: Boolean,
) {
    // The combined bands, as painted: with two passports the primary's own counts
    // would leave a drawn band without a legend entry.
    val counts = remember(visa, secondVisa) { combinedBandCounts(visa, secondVisa) }
    Column {
        if (labeled && visa != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VisaBand.entries.forEach { band ->
                    val count = counts[band] ?: 0
                    if (count > 0) {
                        CounterLegendRow(
                            ramp.colorFor(band),
                            stringResource(R.string.map_legend_count_labeled, count, stringResource(band.labelRes)),
                        )
                    }
                }
                // Only shown when it paints something: DE's dataset has no gaps, so
                // a row for a colour this passport never uses would be a riddle.
                if (noDataCount > 0) CounterLegendRow(ramp.noData, stringResource(R.string.map_legend_no_data, noDataCount))
            }
        } else {
            val collapsedSummary = visaLegendSummary(visa, noDataCount, secondVisa).asString()
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                // The band dots carry no number here, only colour, so without this
                // TalkBack would skip straight past them to the aggregate count and
                // never say a visa lens is even showing. The expanded, tap-labelled
                // rows already speak for themselves via CounterLegendRow's own text.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = collapsedSummary
                },
            ) {
                // Separated dots, like the travel legend: the bands are a
                // categorical set now, not an ordered scale, so nothing here should
                // read as a sequence of adjacent steps. Empty bands get no dot, as in
                // the expanded legend and visaLegendSummary: a dot matching no place
                // (DE has no CHECK rows) would be a riddle.
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    VisaBand.entries.forEach { band ->
                        if ((counts[band] ?: 0) > 0) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(ramp.colorFor(band))
                            )
                        }
                    }
                    if (noDataCount > 0) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(ramp.noData)
                        )
                    }
                }
                Text(
                    if (visa == null) {
                        stringResource(R.string.map_visa_legend_none)
                    } else {
                        stringResource(R.string.map_visa_legend_free, counts[VisaBand.VISA_FREE] ?: 0)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (visa != null) {
            Text(
                (if (secondVisa != null) asOfNoticeText(visa, secondVisa) else asOfNoticeText(visa)).asString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The heatmap's legend: how to read the encoding, and how many of the groupings it
 * is currently painting are fully done.
 *
 * Each region is its own colour, so the swatches are one neutral hue at the four
 * strengths the map uses (drawn over the land colour, as the map does) rather than
 * any region's colour: what they teach is "stronger = more visited".
 *
 * No date line, unlike [VisaLegend] — this is derived entirely from [Place.region]
 * and the hand-curated informal sets already bundled with the app, not from a
 * dated external asset, so there is nothing here that goes stale.
 */
@Composable
private fun RegionLegend(ramp: RegionRamp, land: Color, mode: RegionMode, stats: TravelStats) {
    val totals = if (mode == RegionMode.OFFICIAL) {
        stats.regions.map { it.been to it.total }
    } else {
        stats.informalRegions.map { it.been to it.total }
    }
    val done = totals.count { (been, total) -> been == total && total > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.clip(MaterialTheme.shapes.extraSmall)) {
                RegionBand.entries.forEach { band ->
                    Box(
                        Modifier
                            .size(width = 14.dp, height = 10.dp)
                            .background(
                                ramp.legendHue
                                    .copy(alpha = RegionRamp.BAND_ALPHA.getValue(band))
                                    .compositeOver(land)
                            )
                    )
                }
            }
            Text(
                stringResource(
                    if (mode == RegionMode.OFFICIAL) R.string.map_region_legend_official
                    else R.string.map_region_legend_informal
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            stringResource(
                if (mode == RegionMode.OFFICIAL) R.string.map_region_done_official
                else R.string.map_region_done_informal,
                done,
                totals.size,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CounterLegendRow(color: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(color)
        Text(text, style = MaterialTheme.typography.labelLarge)
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
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
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
                stringResource(R.string.map_region_card_done, info.been, info.total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * What a tap opens: a full-width banner over the bottom edge. The flag sits beside the
 * name as a crisp thumbnail at its true aspect ratio, and the background is a diagonal
 * gradient of the flag's own dominant colours, darkened until white text reads on it
 * (plain dark for places without a flag). Deliberately not a modal sheet: the map stays
 * visible and interactive, so tapping a second country just swaps the banner
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
    attributeTo: String?,
    noVisaData: Boolean,
    hasSecondPassport: Boolean,
    showStatusToggle: Boolean,
    onToggle: (PlaceStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flag = rememberPlaceFlag(place.code)
    // The colours are always darkened for white text, so the content is themed dark
    // whatever the app theme is: chips and secondary text would otherwise be
    // near-invisible over a flag in light mode.
    MaterialTheme(colorScheme = darkColorScheme(), typography = MaterialTheme.typography) {
    val colors = flag?.colors.orEmpty().map { Color(it) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(
                when (colors.size) {
                    0 -> Brush.linearGradient(listOf(Color(0xFF1B1B1F), Color(0xFF1B1B1F)))
                    1 -> Brush.linearGradient(listOf(colors[0], colors[0]))
                    else -> Brush.linearGradient(colors)
                }
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // No flag (or still decoding): no slot at all. With one, the slot is a
                // fixed width so the text does not shift between a wide and a square flag.
                flag?.let {
                    val (w, h) = flagThumbSize(it.bitmap.width, it.bitmap.height, 64f, 40f)
                    val shape = RoundedCornerShape(4.dp)
                    Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = it.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(w.dp, h.dp)
                                .clip(shape)
                                .border(1.dp, Color.White.copy(alpha = 0.25f), shape),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    // Two lines, fixed: one line ellipsises the sovereign away, and
                    // the sovereign is the fact this app exists to surface — Sint
                    // Maarten being Dutch is the point, not decoration. minLines
                    // alongside maxLines pins the card's height at two lines
                    // regardless of which place is selected.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                }
            }
            if (requirement != null) {
                Text(
                    requirementSummaryText(requirement, attributeTo).asString(),
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
                        stringResource(VisaConfidence.LOW.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (noVisaData) {
                // The generic-tier honest gap: this passport's dataset has no rule
                // covering this place at all, so there is nothing to summarise —
                // only a nudge to look it up, rather than the silent blank this
                // used to leave on both the map and the card.
                Text(
                    stringResource(
                        if (hasSecondPassport) R.string.map_no_data_either else R.string.map_no_data_this
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (showStatusToggle) {
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Short labels, matching the passport rows. Three chips reading
                    // "Been here / Lived here / Want to go" overflow a 360 dp phone
                    // once the card's padding is taken off, and a wrapped or
                    // scrolling row of three would hide one of the three answers
                    // behind a gesture.
                    //
                    // Own colours rather than the theme's: the theme's grey outline
                    // vanishes into a saturated flag red. A dark wash with a light rim
                    // reads on any flag colour, and the selected chip inverts to white.
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        labelColor = Color.White,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color(0xFF1B1B1F),
                    )
                    @Composable
                    fun StatusChip(target: PlaceStatus, label: String) = BounceFilterChip(
                        selected = status == target,
                        label = label,
                        onClick = { onToggle(target) },
                        colors = chipColors,
                        border = if (status == target) null
                            else BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    )
                    StatusChip(PlaceStatus.VISITED, stringResource(R.string.map_chip_been))
                    StatusChip(PlaceStatus.LIVED, stringResource(R.string.map_chip_lived))
                    StatusChip(PlaceStatus.WISHLIST, stringResource(R.string.map_chip_want))
                }
            }
        }
    }
    }
}

/**
 * The passport's own search, on the map: the same [Place.searchKey] match, capped so
 * the floating card stays a picker rather than becoming a second passport list.
 *
 * Twelve, not the five it started at: five results all fit inside the card's height
 * cap, so the cap and its scroll could never engage and a search for something broad
 * like "island" silently hid everything after the fifth match. Twelve overflows the
 * cap, which is the point — the list scrolls and the card's height stays fixed.
 */
private const val MAP_SEARCH_LIMIT = 12

internal fun searchMapMatches(places: List<Place>, query: String): List<Place> {
    val needle = query.trim().foldForSearch()
    if (needle.isEmpty()) return emptyList()
    return places.filter { it.searchKey.contains(needle) }
        .sortedBy { it.sortKey }
        .take(MAP_SEARCH_LIMIT)
}

/**
 * Which legend the map's top-left pill is currently showing.
 *
 * A named kind rather than the `if/else` chain it replaced, because the pill's
 * transition keys on it: the content fading *out* has to keep drawing its own form
 * while it goes, which it cannot do if it reads the same live flags the incoming
 * content reads.
 */
internal enum class MapLegendKind {
    VISA_DOTS,
    VISA_LABELED,
    REGION_OFFICIAL,
    REGION_INFORMAL,
    NUDGE,
    COUNTER_DOTS,
    COUNTER_LABELED,
}

/**
 * The order is the map's own precedence and is not arbitrary: the visa lens owns the
 * legend whenever it is on, the region heatmap owns it next, and only then does the
 * travel counter get a say — which starts as a nudge while there is nothing to count.
 */
internal fun mapLegendKind(
    lens: Boolean,
    visaLabeled: Boolean,
    regionMode: RegionMode,
    counterLabeled: Boolean,
    been: Int,
    wishlist: Int,
): MapLegendKind = when {
    lens -> if (visaLabeled) MapLegendKind.VISA_LABELED else MapLegendKind.VISA_DOTS
    regionMode == RegionMode.OFFICIAL -> MapLegendKind.REGION_OFFICIAL
    regionMode == RegionMode.INFORMAL -> MapLegendKind.REGION_INFORMAL
    been == 0 && wishlist == 0 -> MapLegendKind.NUDGE
    counterLabeled -> MapLegendKind.COUNTER_LABELED
    else -> MapLegendKind.COUNTER_DOTS
}

/**
 * What the collapsed travel legend's dots and bare counts mean, spoken as one
 * sentence rather than a run of numbers with no label. Only counts the bands the
 * dot row actually draws, so the sentence never mentions a colour that is not on
 * screen.
 */
internal fun travelLegendDescription(stats: TravelStats): UiText {
    val parts = mutableListOf<UiText>(UiText.Res(R.string.map_legend_visited, stats.visited))
    if (stats.lived > 0) parts.add(UiText.Res(R.string.map_legend_lived, stats.lived))
    if (stats.wishlist > 0) parts.add(UiText.Res(R.string.map_legend_wishlisted, stats.wishlist))
    return parts.reduce { joined, part -> UiText.Res(R.string.map_join_comma, joined, part) }
}

/**
 * What the collapsed visa legend's band dots mean: since the dots carry no count
 * of their own in that state, a screen reader would otherwise say nothing about
 * the visa lens beyond the trailing aggregate.
 */
internal fun visaLegendSummary(
    visa: VisaRegistry?,
    noDataCount: Int,
    secondVisa: VisaRegistry? = null,
): UiText {
    if (visa == null) {
        return UiText.Res(
            if (secondVisa != null) R.string.map_visa_summary_none_second else R.string.map_visa_summary_none_single
        )
    }
    val counts = combinedBandCounts(visa, secondVisa)
    val bands = VisaBand.entries.count { (counts[it] ?: 0) > 0 }
    val withNoData = if (noDataCount > 0) bands + 1 else bands
    return UiText.Plural(R.plurals.map_visa_summary, withNoData, withNoData, counts[VisaBand.VISA_FREE] ?: 0)
}

/**
 * The (slot, band) layers whose code set differs between two applied states. A key
 * missing on one side counts as empty, so a key that disappears is reported and gets
 * cleared, while an explicit empty set and an absent key are the same thing.
 */
internal fun changedRegionKeys(
    old: Map<RegionStyleKey, Set<String>>,
    new: Map<RegionStyleKey, Set<String>>,
): Set<RegionStyleKey> =
    (old.keys + new.keys).filterTo(mutableSetOf()) { old[it].orEmpty() != new[it].orEmpty() }
