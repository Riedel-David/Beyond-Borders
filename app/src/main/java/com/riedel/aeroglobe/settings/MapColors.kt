package com.riedel.aeroglobe.settings

/**
 * The three map fills a user is allowed to repaint.
 *
 * Only the statuses: ocean, unmarked land, borders and the selection outline stay
 * fixed. Those four carry no meaning that a colour choice could express, and they
 * are the backgrounds every preset below was contrast-checked against — letting
 * them move would invalidate the check.
 */
enum class MapSlot { VISITED, LIVED, WISHLIST }

/**
 * A named hue with a light-mode and a dark-mode value.
 *
 * A preset is a *pair*, not a colour, which is the whole reason this is a short
 * list and not a colour wheel. The map is drawn over a near-white land fill in
 * light mode and a dark slate one in dark mode, so a single hex that reads well
 * against one is often invisible against the other. Picking from a wheel, the user
 * would be choosing for whichever mode they happened to be in.
 *
 * Every pair here clears 3:1 against unmarked land in *both* modes — WCAG 2.1's
 * non-text contrast bar — and no two hues sit closer than 42° apart on the colour
 * wheel, which is what stops the map turning into shades of one colour. Both
 * properties are asserted in MapColorsTest rather than trusted, so adding a hue
 * here fails the build until it earns its place.
 */
enum class MapHue(val lightArgb: Long, val darkArgb: Long) {
    AZURE(0xFF135CC9, 0xFF3D7DE0),
    TEAL(0xFF0F8A7E, 0xFF14A192),
    GREEN(0xFF2E7D32, 0xFF5BB55F),
    ROSE(0xFFB0245C, 0xFFD9527F),
    VIOLET(0xFF7B2FBE, 0xFFB07BE8),

    /** Offered, though the default palette deliberately avoids it as a status fill. */
    AMBER(0xFFB26B00, 0xFFE0A33A),
    ;

    fun argb(dark: Boolean): Long = if (dark) darkArgb else lightArgb
}

/**
 * Which hue each status is painted in.
 *
 * The invariant that matters is that the three are always *different*. A fixed
 * palette earned the app the guarantee that the four map states stay tellable
 * apart; handing out a free choice per status would hand that guarantee straight
 * back, since nothing stops someone painting "been" and "want to" the same green
 * and then wondering which is which. So [with] permutes rather than assigns, and
 * the invariant holds by construction instead of by validation.
 */
data class MapColors(
    val visited: MapHue,
    val lived: MapHue,
    val wishlist: MapHue,
) {
    fun hueFor(slot: MapSlot): MapHue = when (slot) {
        MapSlot.VISITED -> visited
        MapSlot.LIVED -> lived
        MapSlot.WISHLIST -> wishlist
    }

    /**
     * Paint [slot] in [hue], swapping with whichever slot already held it.
     *
     * Swapping rather than refusing, because a refusal would leave the user to work
     * out for themselves that they must first move the *other* status — a puzzle
     * with no clue on screen. A swap always succeeds, is its own undo, and keeps
     * three distinct hues without ever needing to say no.
     */
    fun with(slot: MapSlot, hue: MapHue): MapColors {
        val previous = hueFor(slot)
        if (previous == hue) return this
        val displaced = MapSlot.entries.firstOrNull { hueFor(it) == hue }

        fun resolve(target: MapSlot) = when (target) {
            slot -> hue
            displaced -> previous
            else -> hueFor(target)
        }

        return MapColors(
            visited = resolve(MapSlot.VISITED),
            lived = resolve(MapSlot.LIVED),
            wishlist = resolve(MapSlot.WISHLIST),
        )
    }

    companion object {
        /** The palette the app shipped with, and what "reset" restores. */
        val DEFAULT = MapColors(
            visited = MapHue.AZURE,
            lived = MapHue.ROSE,
            wishlist = MapHue.TEAL,
        )

        /**
         * Rebuild from stored names, falling back whole rather than per field.
         *
         * All-or-nothing on purpose: a half-read choice could pair two statuses on
         * one hue, which is exactly the state [with] exists to make unreachable. An
         * unreadable preference is a cosmetic loss, so the safe answer is the
         * default palette and not a best effort at the user's intent.
         */
        fun parse(visited: String?, lived: String?, wishlist: String?): MapColors {
            val hues = listOf(visited, lived, wishlist).map { name ->
                MapHue.entries.firstOrNull { it.name == name } ?: return DEFAULT
            }
            return if (hues.distinct().size == hues.size) {
                MapColors(visited = hues[0], lived = hues[1], wishlist = hues[2])
            } else {
                DEFAULT
            }
        }
    }
}
