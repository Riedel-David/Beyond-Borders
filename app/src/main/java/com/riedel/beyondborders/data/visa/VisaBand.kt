package com.riedel.beyondborders.data.visa

import androidx.annotation.StringRes
import com.riedel.beyondborders.R

/**
 * The five groups the *map* paints. The list keeps all eight [VisaCategory] values.
 *
 * Fewer bands than categories, and not as a shortcut. Eight fills on a world map is
 * a legend to study, not a map to read; the map answers "roughly, where can I go" and
 * the list answers "what exactly does Suriname want". So the two surfaces carry
 * different granularity on purpose rather than the map being a lossy copy of the list.
 *
 * Unlike the ramp this replaced, these are not ordered by openness — they are a
 * *categorical* answer, coloured by hue rather than by intensity, so [ETA] and
 * [VISA_ON_ARRIVAL] get their own bands instead of sharing one the way they used to.
 * VisaRampTest holds the palette to the same 3:1-background and CVD-simulation bars
 * the old ramp did, just checked pairwise rather than as neighbours in a sequence.
 *
 * [label] is the English fallback for callers that cannot resolve [labelRes] yet.
 */
enum class VisaBand(@StringRes val labelRes: Int, val label: String) {
    /** [VisaCategory.HOME] and [VisaCategory.VISA_FREE]: turn up with the passport. */
    VISA_FREE(R.string.visa_band_visa_free, "Visa-free"),

    /** [VisaCategory.ETA]: an online form before you fly, never an embassy visit. */
    ETA(R.string.visa_band_eta, "e-Ticket/ETA"),

    /** [VisaCategory.VISA_ON_ARRIVAL]: a desk at the airport, no advance paperwork. */
    VISA_ON_ARRIVAL(R.string.visa_band_visa_on_arrival, "On arrival"),

    /**
     * [VisaCategory.E_VISA] and [VisaCategory.VISA_REQUIRED]: apply, wait, then
     * book. An e-visa is less of an errand than a consular visa, and the list says
     * which — but on the map both mean "not this weekend".
     */
    VISA_REQUIRED(R.string.visa_band_visa_required, "Apply first"),

    /**
     * [VisaCategory.PERMIT_REQUIRED] and [VisaCategory.NO_ADMISSION]. Merged because
     * for either passport this is a handful of permit regimes and at most one or two
     * refusals, so splitting it further would spend a whole band on almost nothing.
     */
    CLOSED(R.string.visa_band_closed, "Closed"),
}

/** Which map band this category falls in. Total, so the map can never miss a place. */
val VisaCategory.band: VisaBand
    get() = when (this) {
        VisaCategory.HOME, VisaCategory.VISA_FREE -> VisaBand.VISA_FREE
        VisaCategory.ETA -> VisaBand.ETA
        VisaCategory.VISA_ON_ARRIVAL -> VisaBand.VISA_ON_ARRIVAL
        VisaCategory.E_VISA, VisaCategory.VISA_REQUIRED -> VisaBand.VISA_REQUIRED
        VisaCategory.PERMIT_REQUIRED, VisaCategory.NO_ADMISSION -> VisaBand.CLOSED
    }
