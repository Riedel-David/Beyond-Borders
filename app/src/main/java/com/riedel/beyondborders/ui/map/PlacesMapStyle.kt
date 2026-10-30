package com.riedel.beyondborders.ui.map

import androidx.compose.ui.graphics.toArgb
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.ui.places.RegionBand
import com.riedel.beyondborders.ui.theme.MapPalette
import com.riedel.beyondborders.ui.theme.RegionSlots
import java.net.URI
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The map style, built in code from the bundled GeoJSON.
 *
 * There is no basemap and no tile server: the only geometry is our own
 * places.geojson, drawn over a flat ocean-coloured background. That is what makes
 * the travel map work with the phone in flight mode, and it is why the app needs
 * no map API key and costs nothing to run.
 *
 * Labels used to be a Compose overlay for the same reason: a glyph endpoint is
 * usually a network call. MapLibre resolves the `glyphs` URL through the same
 * `asset://` scheme [GEOJSON_ASSET] already uses, though, so [LAYER_LABELS] reads
 * SDF glyphs bundled at assets/glyphs instead — no server, no Compose
 * recomposition on every camera move, just a real layer like the rest of these.
 */
internal object PlacesMapStyle {

    const val SOURCE_ID = "places"
    const val LABELS_SOURCE_ID = "places-labels-points"
    const val LAYER_BACKGROUND = "ocean"
    const val LAYER_FILL = "places-fill"
    const val LAYER_VISITED = "places-visited"
    const val LAYER_LIVED = "places-lived"
    const val LAYER_WISHLIST = "places-wishlist"
    const val LAYER_BORDER = "places-border"
    const val LAYER_SELECTED = "places-selected"
    const val LAYER_SELECTED_CASING = "places-selected-casing"
    const val LAYER_VISA_SELECTED = "places-visa-selected"
    const val LAYER_VISA_SELECTED_CASING = "places-visa-selected-casing"
    const val LAYER_VISA_NO_DATA = "places-visa-no-data"
    const val LAYER_REGION_SELECTED = "places-region-selected"
    const val LAYER_REGION_SELECTED_CASING = "places-region-selected-casing"
    const val LAYER_LABELS = "places-labels"

    /** Latin-only SDF ranges bundled at assets/glyphs; see [GLYPHS_ASSET]. */
    private const val LABEL_FONT = "Noto Sans Regular"

    /**
     * The region heatmap's fills, one per (hue slot, completion band).
     *
     * Same trick as [visaLayerId]: a fixed, small layer set regardless of how many
     * regions or named groupings exist, because MapLibre bakes a layer's colour in
     * at style-build time. Toggling the heatmap on, or switching between official
     * and informal groupings, is then only ever a setFilter call.
     */
    fun regionLayerId(slot: Int, band: RegionBand): String =
        "places-region-$slot-${band.name.lowercase()}"

    /**
     * The visa lens' fills, one per band.
     *
     * Added above the status fills so switching the lens on is a filter change rather
     * than a style rebuild. DE covers all 252 places; every other passport has real
     * "honest gap" places these six bands don't cover at all — see
     * [LAYER_VISA_NO_DATA], the seventh layer that paints those grey instead of letting
     * [LAYER_FILL]'s unmarked land show through unexplained.
     */
    fun visaLayerId(band: VisaBand): String = "places-visa-${band.name.lowercase()}"

    /** The asset the geo pipeline writes; see tools/geo/build_geo.py. */
    private const val GEOJSON_ASSET = "asset://places.geojson"

    /**
     * One Point per place, not the polygons [GEOJSON_ASSET] carries.
     *
     * A symbol layer anchors once per polygon *part*, so pointing [LAYER_LABELS] at
     * the country outlines directly would draw one "Russia" per Siberian island and
     * one "Canada" per Arctic fragment, while single-part countries competed for the
     * same anchor as everything overlapping them. This source carries the one
     * precomputed centroid per place (build_geo.py, the same point places.json uses)
     * instead, so every place gets exactly one label, at its visual centre.
     */
    private const val LABELS_GEOJSON_ASSET = "asset://places_labels.geojson"

    /**
     * Bundled SDF glyph ranges (Noto Sans, Basic Latin + Latin-1 Supplement +
     * Latin Extended-A - place names are English-only but some carry
     * diacritics, e.g. "Côte d'Ivoire"), resolved via the same `asset://`
     * scheme [GEOJSON_ASSET] uses. Confirmed working fully offline on-device
     * (MapLibre 13.4.1): no glyph server needed, which is what let labels
     * become a real SymbolLayer instead of a Compose overlay.
     */
    private const val GLYPHS_ASSET = "asset://glyphs/{fontstack}/{range}.pbf"

    /**
     * A valid but empty style-spec document to hang our layers off. `sources` and
     * `layers` stay empty here and are added below so that the colours can come
     * from the Compose theme rather than being baked into a string.
     */
    private const val EMPTY_STYLE =
        """{"version":8,"name":"BeyondBorders","sources":{},"layers":[],"glyphs":"$GLYPHS_ASSET"}"""

    fun builder(palette: MapPalette): Style.Builder {
        // java.net.URI, not android.net.Uri: GeoJsonSource's String overload means
        // "raw JSON" and throws on anything that looks like a URL.
        val source = GeoJsonSource(SOURCE_ID, URI.create(GEOJSON_ASSET))
        val labelsSource = GeoJsonSource(LABELS_SOURCE_ID, URI.create(LABELS_GEOJSON_ASSET))

        var builder = Style.Builder()
            .fromJson(EMPTY_STYLE)
            .withSource(source)
            .withSource(labelsSource)
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
            // One overlay per status, each showing only the codes in its filter.
            // Recolouring is then one setFilter call instead of rebuilding a
            // 252-branch match expression, and because PlaceStatus is a single value
            // per place, a place can only ever be in one of them.
            .withLayer(
                FillLayer(LAYER_VISITED, SOURCE_ID)
                    .withProperties(PropertyFactory.fillColor(palette.visited.toArgb()))
                    .withFilter(noPlaces())
            )
            .withLayer(
                FillLayer(LAYER_LIVED, SOURCE_ID)
                    .withProperties(PropertyFactory.fillColor(palette.lived.toArgb()))
                    .withFilter(noPlaces())
            )
            .withLayer(
                FillLayer(LAYER_WISHLIST, SOURCE_ID)
                    .withProperties(PropertyFactory.fillColor(palette.wishlist.toArgb()))
                    .withFilter(noPlaces())
            )

        // The visa lens, one layer per band, above the travel colours and below the
        // borders. Same trick as the statuses: each layer owns one colour and shows
        // whichever codes its filter names, so turning the lens on or off never
        // rebuilds the style.
        VisaBand.entries.forEach { band ->
            builder = builder.withLayer(
                FillLayer(visaLayerId(band), SOURCE_ID)
                    .withProperties(PropertyFactory.fillColor(palette.visa.colorFor(band).toArgb()))
                    .withFilter(noPlaces())
            )
        }
        // The seventh visa layer: places this passport's dataset has no answer for at
        // all, painted grey instead of falling through to LAYER_FILL's unmarked land
        // — which looked identical to "not visited" and said nothing was wrong.
        builder = builder.withLayer(
            FillLayer(LAYER_VISA_NO_DATA, SOURCE_ID)
                .withProperties(PropertyFactory.fillColor(palette.visa.noData.toArgb()))
                .withFilter(noPlaces())
        )

        // The region heatmap, one layer per hue slot and band, above the visa lens
        // and below the borders — the two lenses never carry a filter at once, so
        // layer order between them is cosmetic, but a fixed spot keeps it simple.
        for (slot in 0 until RegionSlots.COUNT) {
            RegionBand.entries.forEach { band ->
                builder = builder.withLayer(
                    FillLayer(regionLayerId(slot, band), SOURCE_ID)
                        .withProperties(PropertyFactory.fillColor(palette.region.colorFor(slot, band).toArgb()))
                        .withFilter(noPlaces())
                )
            }
        }

        return builder
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
            // Each selection outline is a core line over a wider casing in the opposite
            // colour, so it reads on any fill; the casing is added first to draw beneath.
            .withLayer(
                LineLayer(LAYER_SELECTED_CASING, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.lineColor(palette.selectedCasing.toArgb()),
                        PropertyFactory.lineWidth(casingWidth()),
                    )
                    .withFilter(noPlaces())
            )
            .withLayer(
                LineLayer(LAYER_SELECTED, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.lineColor(palette.selectedOutline.toArgb()),
                        PropertyFactory.lineWidth(selectionWidth()),
                    )
                    .withFilter(noPlaces())
            )
            // A second outline in the lens' own colour rather than a recolour of the
            // one above, because a layer's paint properties are fixed when the style
            // is built and the lens toggles far more often than the theme does. Only
            // ever one of the two carries a filter, so they cannot both draw.
            .withLayer(
                LineLayer(LAYER_VISA_SELECTED_CASING, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.lineColor(palette.visa.selectedCasing.toArgb()),
                        PropertyFactory.lineWidth(casingWidth()),
                    )
                    .withFilter(noPlaces())
            )
            .withLayer(
                LineLayer(LAYER_VISA_SELECTED, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.lineColor(palette.visa.selectedOutline.toArgb()),
                        PropertyFactory.lineWidth(selectionWidth()),
                    )
                    .withFilter(noPlaces())
            )
            // The casing goes in first so the core line draws over it.
            .withLayer(
                LineLayer(LAYER_REGION_SELECTED_CASING, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.lineColor(palette.region.selectedCasing.toArgb()),
                        PropertyFactory.lineWidth(casingWidth()),
                    )
                    .withFilter(noPlaces())
            )
            .withLayer(
                LineLayer(LAYER_REGION_SELECTED, SOURCE_ID)
                    .withProperties(
                        PropertyFactory.lineColor(palette.region.selectedOutline.toArgb()),
                        PropertyFactory.lineWidth(selectionWidth()),
                    )
                    .withFilter(noPlaces())
            )
            // One layer for every place's name, not one per lens/status variant:
            // unlike the fills above, label colour never depends on which lens is
            // active, so there is nothing a setFilter would need to swap.
            // minLabelZoom (baked in by tools/geo/build_geo.py from each place's
            // camera-target bbox) stands in for the old per-frame screen-space
            // footprint check - a country only gets a label once it would already
            // read as more than a sliver at the current zoom.
            .withLayer(
                SymbolLayer(LAYER_LABELS, LABELS_SOURCE_ID)
                    .withProperties(
                        PropertyFactory.textField(Expression.get("name")),
                        PropertyFactory.textFont(arrayOf(LABEL_FONT)),
                        PropertyFactory.textSize(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(2, 8f),
                                Expression.stop(8, 15f),
                            )
                        ),
                        PropertyFactory.textColor(palette.labelText.toArgb()),
                        PropertyFactory.textHaloColor(palette.labelHalo.toArgb()),
                        PropertyFactory.textHaloWidth(1.2f),
                        // CENTER with no offset, not the old TOP+downward-offset
                        // nudge: the anchor point is now build_geo.py's actual
                        // visual center, so the text should sit on it, not below
                        // and past it.
                        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
                        // Collision avoidance on: a name that would sit on top of another
                        // is dropped rather than drawn as an unreadable pile (central
                        // Africa, Europe). Labels are placed in symbolSortKey order, lowest
                        // first, and minLabelZoom is smaller for bigger countries - so the
                        // large one keeps its name and the small one gets it once zoomed in.
                        PropertyFactory.textAllowOverlap(false),
                        PropertyFactory.textIgnorePlacement(false),
                        PropertyFactory.textPadding(2f),
                        PropertyFactory.symbolSortKey(Expression.get("minLabelZoom")),
                        PropertyFactory.textMaxWidth(8f),
                    )
                    .withFilter(
                        Expression.gte(Expression.zoom(), Expression.get("minLabelZoom"))
                    )
            )
    }

    /**
     * Zoom-dependent, like the borders: a fixed 2 dp outline turns Greenland's fjords
     * into a solid blob at world zoom.
     */
    private fun selectionWidth(): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(1, 1.4f),
        Expression.stop(5, 2.6f),
    )

    /** [selectionWidth] plus a rim on each side, for every selection outline's casing. */
    private fun casingWidth(): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(1, 3.4f),
        Expression.stop(5, 5.2f),
    )

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
