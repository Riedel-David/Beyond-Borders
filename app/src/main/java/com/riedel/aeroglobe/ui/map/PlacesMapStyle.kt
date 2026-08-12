package com.riedel.aeroglobe.ui.map

import androidx.compose.ui.graphics.toArgb
import com.riedel.aeroglobe.ui.theme.MapPalette
import java.net.URI
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The map style, built in code from the bundled GeoJSON.
 *
 * There is no basemap and no tile server: the only geometry is our own
 * places.geojson, drawn over a flat ocean-coloured background. That is what makes
 * the travel map work with the phone in flight mode, and it is why the app needs
 * no map API key and costs nothing to run.
 *
 * It also means there are no text labels. Labels would need a glyph endpoint, and
 * every glyph endpoint is a network call — so place names live in the card that
 * appears when you tap, not on the map itself.
 */
internal object PlacesMapStyle {

    const val SOURCE_ID = "places"
    const val LAYER_BACKGROUND = "ocean"
    const val LAYER_FILL = "places-fill"
    const val LAYER_VISITED = "places-visited"
    const val LAYER_WISHLIST = "places-wishlist"
    const val LAYER_BORDER = "places-border"
    const val LAYER_SELECTED = "places-selected"

    /** The asset the geo pipeline writes; see tools/geo/build_geo.py. */
    private const val GEOJSON_ASSET = "asset://places.geojson"

    /**
     * A valid but empty style-spec document to hang our layers off. `sources` and
     * `layers` stay empty here and are added below so that the colours can come
     * from the Compose theme rather than being baked into a string.
     */
    private const val EMPTY_STYLE = """{"version":8,"name":"AeroGlobe","sources":{},"layers":[]}"""

    fun builder(palette: MapPalette): Style.Builder {
        // java.net.URI, not android.net.Uri: GeoJsonSource's String overload means
        // "raw JSON" and throws on anything that looks like a URL.
        val source = GeoJsonSource(SOURCE_ID, URI.create(GEOJSON_ASSET))

        return Style.Builder()
            .fromJson(EMPTY_STYLE)
            .withSource(source)
            .withLayer(
                BackgroundLayer(LAYER_BACKGROUND).withProperties(
                    PropertyFactory.backgroundColor(palette.ocean.toArgb()),
                )
            )
            // Base coat: every place in the "not been there" colour.
            .withLayer(
                FillLayer(LAYER_FILL, SOURCE_ID).withProperties(
                    PropertyFactory.fillColor(palette.unvisited.toArgb()),
                    PropertyFactory.fillAntialias(true),
                )
            )
            // Two overlays, each showing only the codes in its filter. Recolouring
            // is then one setFilter call instead of rebuilding a 252-branch match
            // expression, and a place can only ever be in one of them.
            .withLayer(
                FillLayer(LAYER_VISITED, SOURCE_ID)
                    .withProperties(PropertyFactory.fillColor(palette.visited.toArgb()))
                    .withFilter(noPlaces())
            )
            .withLayer(
                FillLayer(LAYER_WISHLIST, SOURCE_ID)
                    .withProperties(PropertyFactory.fillColor(palette.wishlist.toArgb()))
                    .withFilter(noPlaces())
            )
            .withLayer(
                LineLayer(LAYER_BORDER, SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(palette.border.toArgb()),
                    // Borders thicken with zoom so the world view stays readable
                    // without turning Europe into a mesh of lines.
                    PropertyFactory.lineWidth(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(1, 0.4f),
                            Expression.stop(4, 0.8f),
                            Expression.stop(8, 1.4f),
                        )
                    ),
                )
            )
            .withLayer(
                LineLayer(LAYER_SELECTED, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.lineColor(palette.selectedOutline.toArgb()),
                        // Also zoom-dependent: a fixed 2 dp outline turns
                        // Greenland's fjords into a solid navy blob at world zoom.
                        PropertyFactory.lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(1, 1.4f),
                                Expression.stop(5, 2.6f),
                            )
                        ),
                    )
                    .withFilter(noPlaces())
            )
    }

    /** A filter that matches nothing — the starting state of every overlay. */
    fun noPlaces(): Expression = Expression.literal(false)

    /** A filter matching exactly the given place codes. */
    fun onlyPlaces(codes: Collection<String>): Expression {
        if (codes.isEmpty()) return noPlaces()
        // Expression.literal takes a real Object[]; Kotlin's Array<String> is not
        // an Array<Any>, so the array is built element by element.
        val haystack: Array<Any> = Array(codes.size) { index -> codes.elementAt(index) }
        return Expression.`in`(Expression.get("code"), Expression.literal(haystack))
    }

    /** A filter matching one place, for the selection outline. */
    fun onlyPlace(code: String): Expression =
        Expression.eq(Expression.get("code"), Expression.literal(code))
}
