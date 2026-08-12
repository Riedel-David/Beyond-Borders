package com.riedel.aeroglobe.data.visa

/**
 * The four groups the *map* paints. The list keeps all eight [VisaCategory] values.
 *
 * Fewer bands than categories, and not as a shortcut. A choropleth encodes order as
 * intensity, and the number of intensity steps a ramp can carry is fixed by the
 * background it has to stay legible against: the dark theme's land fill sets a floor
 * on how dim a fill may be, white sets the ceiling, and four steps is what fits
 * between them while every step stays 3:1 against land and ocean *and* stays apart
 * under colour-vision-deficiency simulation. Six steps do not fit — measured, not
 * guessed, which is what VisaRampTest keeps honest.
 *
 * That constraint happens to agree with what the map is for. Eight fills on a world
 * map is a legend to study, not a map to read; the map answers "roughly, where can I
 * go" and the list answers "what exactly does Suriname want". So the two surfaces
 * carry different granularity on purpose rather than the map being a lossy copy of
 * the list.
 *
 * Ordered by openness, most open first, which is the direction the ramp runs.
 */
enum class VisaBand(val label: String) {
    /** [VisaCategory.HOME] and [VisaCategory.VISA_FREE]: turn up with the passport. */
    VISA_FREE("Visa-free"),

    /**
     * [VisaCategory.ETA] and [VisaCategory.VISA_ON_ARRIVAL] — an online form or a
     * desk at the airport, but never an embassy appointment. The same cut as
     * [VisaRequirement.isEasy], minus what is already visa-free.
     */
    EASY("No embassy"),

    /**
     * [VisaCategory.E_VISA] and [VisaCategory.VISA_REQUIRED]: apply, wait, then
     * book. An e-visa is less of an errand than a consular visa, and the list says
     * which — but on the map both mean "not this weekend".
     */
    PAPERWORK("Apply first"),

    /**
     * [VisaCategory.PERMIT_REQUIRED] and [VisaCategory.NO_ADMISSION]. Merged because
     * for a German passport this is seven permit regimes and zero refusals, so
     * splitting it would spend a fifth of the ramp on an empty band.
     */
    CLOSED("Closed"),
}

/** Which map band this category falls in. Total, so the map can never miss a place. */
val VisaCategory.band: VisaBand
    get() = when (this) {
        VisaCategory.HOME, VisaCategory.VISA_FREE -> VisaBand.VISA_FREE
        VisaCategory.ETA, VisaCategory.VISA_ON_ARRIVAL -> VisaBand.EASY
        VisaCategory.E_VISA, VisaCategory.VISA_REQUIRED -> VisaBand.PAPERWORK
        VisaCategory.PERMIT_REQUIRED, VisaCategory.NO_ADMISSION -> VisaBand.CLOSED
    }
