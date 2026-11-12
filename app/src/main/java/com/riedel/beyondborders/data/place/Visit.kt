package com.riedel.beyondborders.data.place

/** One visit as the UI sees it; [year] null means undated, [month] null means the whole year. */
data class Visit(val id: String, val code: String, val year: Int?, val month: Int? = null)

/** Earliest year a visit may be dated to. */
const val MIN_VISIT_YEAR = 1900

/** Latest year a backup file may carry; fixed so import never depends on the device clock. */
const val MAX_IMPORT_VISIT_YEAR = 2100

/** True for an undated visit or a year in [MIN_VISIT_YEAR]..[currentYear]. */
internal fun isValidVisitYear(year: Int?, currentYear: Int): Boolean =
    year == null || year in MIN_VISIT_YEAR..currentYear

/** The calendar year of [millis] in the device's time zone. */
internal fun yearOf(millis: Long): Int =
    java.util.Calendar.getInstance().apply { timeInMillis = millis }.get(java.util.Calendar.YEAR)

/** A month needs a year to belong to, and must be 1..12; null (whole year) is always fine. */
internal fun isValidVisitMonth(year: Int?, month: Int?): Boolean =
    month == null || (year != null && month in 1..12)

/** The month to keep for a visit: [month] if it is valid for [year], otherwise null. */
internal fun cleanVisitMonth(year: Int?, month: Int?): Int? =
    month?.takeIf { isValidVisitMonth(year, it) }

/** The calendar month (1..12) of [millis] in the device's time zone. */
internal fun monthOf(millis: Long): Int =
    java.util.Calendar.getInstance().apply { timeInMillis = millis }.get(java.util.Calendar.MONTH) + 1

/** True for a month later than [currentMonth] within [currentYear]: a visit that has not happened yet. */
internal fun isFutureVisitMonth(year: Int?, month: Int?, currentYear: Int, currentMonth: Int): Boolean =
    year == currentYear && month != null && month > currentMonth
