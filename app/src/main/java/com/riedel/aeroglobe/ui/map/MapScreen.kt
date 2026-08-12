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
import com.riedel.aeroglobe.ui.places.PlacesViewModel
import com.riedel.aeroglobe.ui.theme.LocalMapPalette
import com.riedel.aeroglobe.ui.theme.MapPalette
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
                if (stats.been == 0 && stats.wishlist == 0) {
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

        MapPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            onClick = controller::showWorld,
        ) {
            Text(
                "World",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (selected != null) {
            SelectedPlaceCard(
                place = selected,
                status = statuses[selected.code] ?: PlaceStatus.NONE,
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
    private var appliedSelection: String? = null

    fun attach(map: MapLibreMap) {
        this.map = map
    }

    fun onStyleLoaded(style: Style) {
        this.style = style
        // Force a re-apply: this style object has fresh layers with empty filters.
        appliedVisited = emptySet()
        appliedLived = emptySet()
        appliedWishlist = emptySet()
        appliedSelection = null
    }

    fun detach() {
        map = null
        style = null
    }

    fun applyStatuses(statuses: Map<String, PlaceStatus>) {
        val style = style ?: return
        val visited = statuses.filterValues { it == PlaceStatus.VISITED }.keys
        val lived = statuses.filterValues { it == PlaceStatus.LIVED }.keys
        val wishlist = statuses.filterValues { it == PlaceStatus.WISHLIST }.keys
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

    fun applySelection(code: String?) {
        val style = style ?: return
        if (code == appliedSelection) return
        appliedSelection = code
        style.getLayerAs<LineLayer>(PlacesMapStyle.LAYER_SELECTED)?.setFilter(
            if (code == null) PlacesMapStyle.noPlaces() else PlacesMapStyle.onlyPlace(code)
        )
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
                controller.applyStatuses(statuses)
                controller.applySelection(selectedCode)
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
            controller.applyStatuses(statuses)
            controller.applySelection(selectedCode)
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
