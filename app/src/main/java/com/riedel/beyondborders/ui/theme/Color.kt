package com.riedel.beyondborders.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.settings.MapBase
import com.riedel.beyondborders.settings.MapColors
import com.riedel.beyondborders.ui.places.RegionBand

/**
 * A hand-picked palette rather than Material You dynamic colour.
 *
 * The map is the app's centrepiece and it only works if "visited", "lived",
 * "wishlist" and "not yet" stay clearly distinguishable from each other and from
 * the ocean. Dynamic colour would re-tint those from the user's wallpaper and could
 * easily land on four shades of the same hue. So: fixed palette, deep azure for the
 * app, rose for lived, teal for wishlist.
 *
 * The three map statuses are repaintable from settings, but only from the vetted
 * list in [com.riedel.beyondborders.settings.MapHue] — which keeps the guarantee
 * dynamic colour would have cost, since every option on that list was checked
 * against these backgrounds and against the other options.
 */

// --- light ------------------------------------------------------------------
private val Azure40 = Color(0xFF135CC9)
private val Azure90 = Color(0xFFDCE7FF)
private val Azure10 = Color(0xFF001B45)
private val Slate40 = Color(0xFF4A6072)
private val Slate90 = Color(0xFFD6E4F0)
private val Slate10 = Color(0xFF071E2E)
private val Teal40 = Color(0xFF0F8A7E)
private val Teal90 = Color(0xFFA7F2E6)
private val Teal10 = Color(0xFF00201C)

val BeyondBordersLight = lightColorScheme(
    primary = Azure40,
    onPrimary = Color.White,
    primaryContainer = Azure90,
    onPrimaryContainer = Azure10,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF131A22),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF131A22),
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = Color(0xFF434A54),
    surfaceContainer = Color(0xFFECF0F6),
    surfaceContainerHigh = Color(0xFFE5EAF2),
    outline = Color(0xFF74797F),
    outlineVariant = Color(0xFFC5CBD4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

// --- dark -------------------------------------------------------------------
private val Azure80 = Color(0xFFAEC6FF)
private val Azure30 = Color(0xFF00458F)
private val Azure20 = Color(0xFF002E6B)
private val Slate80 = Color(0xFFB5C8DA)
private val Slate30 = Color(0xFF32485A)
private val Slate20 = Color(0xFF20323F)
private val Teal80 = Color(0xFF6ED6C6)
private val Teal30 = Color(0xFF006B60)
private val Teal20 = Color(0xFF00382F)

val BeyondBordersDark = darkColorScheme(
    primary = Azure80,
    onPrimary = Azure20,
    primaryContainer = Azure30,
    onPrimaryContainer = Azure90,
    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Teal80,
    onTertiary = Teal20,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    background = Color(0xFF0E1319),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF0E1319),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF3F4650),
    onSurfaceVariant = Color(0xFFC0C6D0),
    surfaceContainer = Color(0xFF1A2028),
    surfaceContainerHigh = Color(0xFF242B34),
    outline = Color(0xFF8A9099),
    outlineVariant = Color(0xFF3F4650),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * Colours the map draws with. Kept out of [androidx.compose.material3.ColorScheme]
 * because MapLibre needs them as plain ints at style-build time, not as theme
 * lookups inside a composition.
 */
data class MapPalette(
    val ocean: Color,
    val unvisited: Color,
    val visited: Color,
    /**
     * A hue of its own, and deliberately not a tint of [visited].
     *
     * "Lived there" is a different answer, not a weaker version of visited, so a
     * tint would read as *less* visited. Rose is the default because this is by far
     * the rarest status, so it can afford to be the loudest colour on the map
     * without shouting over everything else.
     */
    val lived: Color,
    val wishlist: Color,
    val border: Color,
    val selectedOutline: Color,
    /** Country name labels; a halo instead of the Compose pill's background,
     * since a SymbolLayer has no way to draw a rounded rect behind text. */
    val labelText: Color,
    val labelHalo: Color,
    /** The colours the visa lens paints with; see [VisaRamp]. */
    val visa: VisaRamp,
    /** The colours the region heatmap paints with; see [RegionRamp]. */
    val region: RegionRamp,
)

/**
 * The visa lens' colour set: five named hues, one per band.
 *
 * A *categorical* scheme now, not a sequential one — [VisaBand] stopped being an
 * openness ramp when [VisaBand.ETA] and [VisaBand.VISA_ON_ARRIVAL] split apart, so
 * there is no longer one "harder" direction for intensity to carry. Green for
 * visa-free, blue for an ETA, orange for on-arrival, red for apply-first and a
 * neutral grey for closed is the nameable, memorable set — deliberately including
 * green and red together, which the ramp this replaced ruled out for exactly the
 * red/green colour-vision-deficiency collision that risks.
 *
 * That risk is why every value here is tuned, not just picked: green and red survive
 * simulated deuteranopia (the commonest dichromacy, and the one that confuses these
 * two hues specifically) by being pushed to opposite ends of the *lightness* range
 * that clears 3:1 against land — a bright, saturated green and a dark, desaturated
 * red, rather than two mid-tone versions of each. Lightness is what dichromacy leaves
 * alone, so two colours far enough apart in it stay tellable apart even when their
 * hues collapse. VisaRampTest asserts the result rather than trusting the theory: 3:1
 * against land and ocean, and no two bands closer than 1.15:1 apart under simulated
 * protanopia, deuteranopia or tritanopia.
 */
data class VisaRamp(
    val visaFree: Color,
    val eta: Color,
    val visaOnArrival: Color,
    val visaRequired: Color,
    val closed: Color,
    /**
     * Places this passport's dataset has no answer for at all — an "honest gap",
     * not a sixth band: it never comes from a [VisaBand], only from a place absent
     * from [com.riedel.beyondborders.data.visa.VisaRegistry] entirely, so it
     * stays a plain field here rather than another [VisaBand.entries] case.
     */
    val noData: Color,
    /**
     * The selection outline while the lens is on.
     *
     * Its own value because [MapPalette.selectedOutline] was picked against the
     * status fills, not this set — the lens repaints every fill on the map, so it
     * repaints this too.
     */
    val selectedOutline: Color,
) {
    fun colorFor(band: VisaBand): Color = when (band) {
        VisaBand.VISA_FREE -> visaFree
        VisaBand.ETA -> eta
        VisaBand.VISA_ON_ARRIVAL -> visaOnArrival
        VisaBand.VISA_REQUIRED -> visaRequired
        VisaBand.CLOSED -> closed
    }
}

/**
 * The palette for one theme.
 *
 * Not user-repaintable, unlike the status fills. A categorical set is only accessible
 * while every pair in it — not just adjacent ones — clears the same contrast and CVD
 * bars, which is a property of the whole set and not of any one colour, so there is
 * nothing here a per-colour picker could safely offer.
 */
private fun visaRamp(dark: Boolean): VisaRamp = if (dark) {
    VisaRamp(
        visaFree = Color(0xFF308E47),
        eta = Color(0xFF50AADF),
        visaOnArrival = Color(0xFFF28B3C),
        visaRequired = Color(0xFFD35E64),
        closed = Color(0xFFCAC7CA),
        noData = Color(0xFF9AA0A8),
        selectedOutline = Color(0xFF05080C),
    )
} else {
    VisaRamp(
        visaFree = Color(0xFF348731),
        eta = Color(0xFF3C6997),
        visaOnArrival = Color(0xFF463113),
        visaRequired = Color(0xFF310B08),
        closed = Color(0xFF4B4B49),
        noData = Color(0xFF6B6D70),
        selectedOutline = Color(0xFFF7FAFF),
    )
}

/**
 * The region heatmap's colour ramp: two hue pairs, not one hue at four intensities.
 *
 * The single-amber version this started as turned out to not read as four steps on
 * an actual phone screen — adjacent intensities of one hue are exactly the kind of
 * difference that gets lost outdoors or on a dim display, even though it clears the
 * same contrast maths [VisaRamp] does. A hue change per step is the fix, but it
 * reopens the risk [VisaRampTest]'s own history warns about: a hue-ordered ramp can
 * collapse under dichromacy in a way a single hue never does. So this is two hues,
 * not four — teal/green for the low half, orange/near-black for the high half — with
 * [RegionRampTest] checking every pair (not just neighbours) survives all three
 * dichromacy simulations, on top of the same 3:1-background and monotonic-luminance
 * bars [VisaRamp] holds.
 */
data class RegionRamp(
    val none: Color,
    val some: Color,
    val most: Color,
    val done: Color,
    /** The heatmap's own selection outline; see [VisaRamp.selectedOutline] for
     * why the fixed [MapPalette.selectedOutline] would not have done here either. */
    val selectedOutline: Color,
) {
    fun colorFor(band: RegionBand): Color = when (band) {
        RegionBand.NONE -> none
        RegionBand.SOME -> some
        RegionBand.MOST -> most
        RegionBand.DONE -> done
    }
}

private fun regionRamp(dark: Boolean): RegionRamp = if (dark) {
    RegionRamp(
        none = Color(0xFF2F8E8E),
        some = Color(0xFF44C16D),
        most = Color(0xFFF1C79D),
        done = Color(0xFFFDF4F2),
        selectedOutline = Color(0xFF05080C),
    )
} else {
    RegionRamp(
        none = Color(0xFF1F7C7C),
        some = Color(0xFF154826),
        most = Color(0xFF3C2207),
        done = Color(0xFF030100),
        selectedOutline = Color(0xFFF7FAFF),
    )
}

/**
 * The palette the map draws with, for one theme, one set of chosen hues and one
 * chosen background.
 *
 * Border and selection stay fixed regardless of [base]: they were picked against
 * the default background and every [MapBase] pair sits close enough in luminance
 * that they hold up unchanged — see [MapBase].
 */
fun mapPalette(dark: Boolean, colors: MapColors, base: MapBase = MapBase.DEFAULT): MapPalette = MapPalette(
    ocean = Color(base.oceanArgb(dark)),
    unvisited = Color(base.landArgb(dark)),
    visited = Color(colors.visited.argb(dark)),
    lived = Color(colors.lived.argb(dark)),
    wishlist = Color(colors.wishlist.argb(dark)),
    border = if (dark) Color(0xFF4C5663) else Color(0xFFA9B4C2),
    selectedOutline = if (dark) Color(0xFFDCE7FF) else Color(0xFF0B2C63),
    labelText = if (dark) Color(0xFFF2F5FA) else Color(0xFF10151C),
    labelHalo = if (dark) Color(0xFF10151C) else Color(0xFFF2F5FA),
    visa = visaRamp(dark),
    region = regionRamp(dark),
)

// Derived from MapColors.DEFAULT rather than spelled out again, so the palette the
// app opens with and the palette "reset" returns to cannot drift apart.
val MapPaletteLight = mapPalette(dark = false, colors = MapColors.DEFAULT)
val MapPaletteDark = mapPalette(dark = true, colors = MapColors.DEFAULT)
