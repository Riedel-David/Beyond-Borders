package com.riedel.aeroglobe.ui.map

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.data.place.Place
import com.riedel.aeroglobe.data.place.PlaceStatus
import com.riedel.aeroglobe.data.visa.VisaBand
import com.riedel.aeroglobe.data.visa.VisaConfidence
import com.riedel.aeroglobe.data.visa.VisaRegistry
import com.riedel.aeroglobe.data.visa.VisaRequirement
import com.riedel.aeroglobe.ui.places.PlacesViewModel
import com.riedel.aeroglobe.ui.theme.LocalMapPalette
import com.riedel.aeroglobe.ui.theme.MapPalette
import com.riedel.aeroglobe.ui.theme.VisaRamp
import com.riedel.aeroglobe.ui.visa.asOfNotice
import com.riedel.aeroglobe.ui.visa.requirementSummary
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
    val palette = LocalMapPalette.current

    val controller = remember { PlacesMapController() }
    val selected = selectedCode?.let(viewModel::placeOf)

    // Leaving the map drops the selection, so switching to the passport and back
    // does not present a card about a country the user has stopped thinking about.
    DisposableEffect(Unit) {
        onDispose { viewModel.select(null) }
    }

    Box(modifier.fillMaxSize()) {
        PlacesMapView(
            controller = controller,
            palette = palette,
            statuses = statuses,
            visaBands = visaBands,
            lens = lens,
            selectedCode = selectedCode,
            onTap = viewModel::select,
            modifier = Modifier.fillMaxSize(),
        )

        // Overlays sit on top of the map surface, deliberately compact: the map is
        // the point, and chrome that covers the Mediterranean is chrome too much.
        MapPill(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (lens) {
                    VisaLegend(palette.visa, visa)
                } else if (stats.been == 0 && stats.wishlist == 0) {
                    // Nothing marked yet, so the counter would say "0" and teach
                    // nothing. Say what to do instead.
                    Text("Tap a place to mark it", style = MaterialTheme.typography.labelLarge)
                } else {
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
            MapPill(onClick = controller::showWorld) {
                Text(
                    "World",
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
        }

        if (selected != null) {
            SelectedPlaceCard(
                place = selected,
                status = statuses[selected.code] ?: PlaceStatus.NONE,
                // Carried into the card whenever the lens is on, so a tap answers the
                // question the map is currently asking rather than the other one.
                requirement = if (lens) viewModel.requirementOf(selected.code) else null,
                onToggle = { target -> viewModel.toggle(selected.code, target) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
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
    private var appliedSelection: String? = null
    private var appliedLens = false

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
        appliedSelection = null
        appliedLens = false
    }

    fun detach() {
        map = null
        style = null
    }

    /**
     * While [lens] is on the travel colours are cleared rather than merely covered.
     * The visa fills do cover all 252 places today, so covering would look the same —
     * but a place the asset had no answer for would then show its travel colour under
     * a visa legend, and "I have been here" reading as a visa band is the one thing
     * this map must never say.
     */
    fun applyStatuses(statuses: Map<String, PlaceStatus>, lens: Boolean) {
        val style = style ?: return
        val effective = if (lens) emptyMap() else statuses
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

    fun applySelection(code: String?, lens: Boolean) {
        val style = style ?: return
        if (code == appliedSelection && lens == appliedLens) return
        appliedSelection = code
        appliedLens = lens
        val outline =
            if (code == null) PlacesMapStyle.noPlaces() else PlacesMapStyle.onlyPlace(code)
        // Exactly one of the two outlines is armed, so the lens' colour and the travel
        // view's can never draw over each other.
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_SELECTED)
            ?.setFilter(if (lens) PlacesMapStyle.noPlaces() else outline)
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_VISA_SELECTED)
            ?.setFilter(if (lens) outline else PlacesMapStyle.noPlaces())
    }

    fun showWorld() {
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(WORLD), 500)
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
}

@Composable
private fun PlacesMapView(
    controller: PlacesMapController,
    palette: MapPalette,
    statuses: Map<String, PlaceStatus>,
    visaBands: Map<VisaBand, Set<String>>,
    lens: Boolean,
    selectedCode: String?,
    onTap: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val slopPx = with(LocalDensity.current) { TAP_SLOP_DP.dp.toPx() }

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
                controller.applyStatuses(statuses, lens)
                controller.applyVisa(visaBands)
                controller.applySelection(selectedCode, lens)
            }
        }
        onDispose { }
    }

    DisposableEffect(mapView, onTap) {
        val listener = MapLibreMap.OnMapClickListener { latLng ->
            val point = controller.screenLocationOf(latLng)
            // A tap on open ocean clears the selection rather than doing nothing,
            // which is the cheapest possible way to dismiss the detail card.
            onTap(point?.let { controller.placeCodeAt(it, slopPx) })
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
            controller.applyStatuses(statuses, lens)
            controller.applyVisa(visaBands)
            controller.applySelection(selectedCode, lens)
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
                FilterChip(
                    selected = status == PlaceStatus.VISITED,
                    onClick = { onToggle(PlaceStatus.VISITED) },
                    label = { Text("Been") },
                )
                FilterChip(
                    selected = status == PlaceStatus.LIVED,
                    onClick = { onToggle(PlaceStatus.LIVED) },
                    label = { Text("Lived") },
                )
                FilterChip(
                    selected = status == PlaceStatus.WISHLIST,
                    onClick = { onToggle(PlaceStatus.WISHLIST) },
                    label = { Text("Want") },
                )
            }
        }
    }
}
