package com.riedel.aeroglobe.ui.flights

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.riedel.aeroglobe.ui.theme.LocalMapPalette
import java.net.URI
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val SOURCE_ROUTE = "flight-route"
private const val SOURCE_ENDPOINTS = "flight-endpoints"
private const val SOURCE_LAND = "flight-map-land"
private const val LAYER_BACKGROUND = "flight-map-background"
private const val LAYER_LAND = "flight-map-land-fill"
private const val LAYER_LAND_BORDER = "flight-map-land-border"
private const val LAYER_ROUTE = "flight-route-line"
private const val LAYER_ENDPOINTS = "flight-endpoints-circle"

/** The same bundled asset the places map draws with; see PlacesMapStyle.GEOJSON_ASSET. */
private const val LAND_ASSET = "asset://places.geojson"

private const val LABEL_OFFSET_PX = -36

/**
 * No tile server, same reasoning as the places map: this reuses that same bundled
 * places.geojson for land shapes rather than a network basemap, so the hero screen
 * still works with the phone in airplane mode, and it matches the places map's own
 * ocean/land colours instead of inventing a second visual language.
 *
 * Place names are drawn as plain Compose text over the map rather than a MapLibre
 * SymbolLayer, because text labels need a glyph source and every glyph source is a
 * network call - the one thing this map can't spend.
 */
@Composable
fun RouteMapView(
    departureLat: Double,
    departureLon: Double,
    arrivalLat: Double,
    arrivalLon: Double,
    departureLabel: String,
    arrivalLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalMapPalette.current
    val backgroundColor = palette.ocean.toArgb()
    val landColor = palette.unvisited.toArgb()
    val borderColor = palette.border.toArgb()
    val routeColor = palette.selectedOutline.toArgb()
    val endpointColor = palette.lived.toArgb()

    var departurePoint by remember { mutableStateOf<PointF?>(null) }
    var arrivalPoint by remember { mutableStateOf<PointF?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(
            context,
            MapLibreMapOptions.createFromAttributes(context)
                .scrollGesturesEnabled(false)
                .zoomGesturesEnabled(false)
                .rotateGesturesEnabled(false)
                .tiltGesturesEnabled(false)
                .compassEnabled(false)
                .logoEnabled(false)
                .attributionEnabled(false),
        ).apply { onCreate(null) }
    }

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
            if (!mapView.isDestroyed) {
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    DisposableEffect(
        mapView, departureLat, departureLon, arrivalLat, arrivalLon,
        backgroundColor, landColor, borderColor, routeColor, endpointColor,
    ) {
        val departure = LatLng(departureLat, departureLon)
        val arrival = LatLng(arrivalLat, arrivalLon)
        mapView.getMapAsync { map ->
            val style = Style.Builder()
                .fromJson("""{"version":8,"name":"AeroGlobe route","sources":{},"layers":[]}""")
                .withSource(GeoJsonSource(SOURCE_LAND, URI.create(LAND_ASSET)))
                .withSource(GeoJsonSource(SOURCE_ROUTE, routeLineJson(departure, arrival)))
                .withSource(GeoJsonSource(SOURCE_ENDPOINTS, endpointsJson(departure, arrival)))
                .withLayer(
                    BackgroundLayer(LAYER_BACKGROUND).withProperties(
                        PropertyFactory.backgroundColor(backgroundColor),
                    )
                )
                .withLayer(
                    FillLayer(LAYER_LAND, SOURCE_LAND).withProperties(
                        PropertyFactory.fillColor(landColor),
                        PropertyFactory.fillAntialias(true),
                    )
                )
                .withLayer(
                    LineLayer(LAYER_LAND_BORDER, SOURCE_LAND).withProperties(
                        PropertyFactory.lineColor(borderColor),
                        PropertyFactory.lineWidth(0.8f),
                    )
                )
                .withLayer(
                    LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                        PropertyFactory.lineColor(routeColor),
                        PropertyFactory.lineWidth(2f),
                    )
                )
                .withLayer(
                    CircleLayer(LAYER_ENDPOINTS, SOURCE_ENDPOINTS).withProperties(
                        PropertyFactory.circleColor(endpointColor),
                        PropertyFactory.circleRadius(5f),
                    )
                )

            fun updateProjectedPoints() {
                departurePoint = map.projection.toScreenLocation(departure)
                arrivalPoint = map.projection.toScreenLocation(arrival)
            }

            map.setStyle(style) {
                val bounds = LatLngBounds.Builder().include(departure).include(arrival).build()
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 40))
                // moveCamera's transform hasn't necessarily applied yet when this callback
                // runs, so the labels track the settled camera via the idle listener below
                // rather than a single projection call made right here.
                map.addOnCameraIdleListener(::updateProjectedPoints)
                updateProjectedPoints()
            }
        }
        onDispose { }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        EndpointLabel(departureLabel, departurePoint)
        EndpointLabel(arrivalLabel, arrivalPoint)
    }
}

@Composable
private fun EndpointLabel(text: String, point: PointF?) {
    if (point == null) return
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .offset { IntOffset(point.x.toInt(), point.y.toInt() + LABEL_OFFSET_PX) }
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun routeLineJson(departure: LatLng, arrival: LatLng): String =
    """{"type":"Feature","geometry":{"type":"LineString","coordinates":[[${departure.longitude},${departure.latitude}],[${arrival.longitude},${arrival.latitude}]]}}"""

private fun endpointsJson(departure: LatLng, arrival: LatLng): String =
    """{"type":"FeatureCollection","features":[""" +
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${departure.longitude},${departure.latitude}]}},""" +
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${arrival.longitude},${arrival.latitude}]}}""" +
        """]}"""
