package com.riedel.beyondborders.ui.visa

import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.VisaRequirement
import com.riedel.beyondborders.ui.UiText

/**
 * The words the map and the passport both use for a visa answer.
 *
 * Shared so the two surfaces cannot drift into describing the same requirement
 * differently — which, for a screen whose only job is to be trusted, would be worse
 * than either wording alone.
 */

/**
 * "Visa-free · 90 days", or just the category where no day count is known.
 *
 * When [passportCode] is non-null, appends attribution — e.g.
 * "Visa-free · 90 days (British passport)". The caller decides whether to
 * attribute; this function appends whenever the argument is provided.
 */
fun requirementSummaryText(
    requirement: VisaRequirement,
    passportCode: String? = null,
): UiText {
    var text: UiText = UiText.Res(requirement.category.labelRes)
    requirement.days?.let {
        text = UiText.Res(R.string.visa_summary_with_days, text, UiText.Plural(R.plurals.visa_days, it, it))
    }
    passportCode?.let {
        text = UiText.Res(R.string.visa_summary_with_passport, text, passportAdjective(it))
    }
    return text
}

/** "German" for DE, "British" for GB — the adjective [asOfNotice] and the Settings picker use. */
fun passportAdjective(isoCode: String): UiText = when (isoCode) {
    "DE" -> UiText.Res(R.string.visa_adjective_de)
    "GB" -> UiText.Res(R.string.visa_adjective_gb)
    else -> UiText.Plain(isoCode)
}

/**
 * The disclaimer required on every visa surface.
 *
 * One function rather than a string in each screen: the promise is that the app never
 * shows a visa answer without saying how old it is, and a promise kept in two places
 * is a promise that gets half-kept in the third. Names the passport too, since it can
 * now differ from the one the reader expects — an unstated assumption is exactly what
 * this notice exists to rule out.
 */
fun asOfNoticeText(visa: VisaRegistry): UiText =
    UiText.Res(R.string.visa_as_of_notice, visa.asOf, passportAdjective(visa.passport))

/**
 * The disclaimer for two passports, naming both and showing the older of the two
 * `asOf` dates.
 *
 * With two datasets the honest claim is the staler one — the answers shown are
 * current as of the older date, not the newer. String comparison with `<=` works
 * here because `asOf` is ISO YYYY-MM-DD format; a format change would silently
 * pick the wrong date.
 */
fun asOfNoticeText(primary: VisaRegistry, second: VisaRegistry): UiText {
    val olderDate = if (primary.asOf <= second.asOf) primary.asOf else second.asOf
    return UiText.Res(
        R.string.visa_as_of_notice_two,
        olderDate,
        passportAdjective(primary.passport),
        passportAdjective(second.passport),
    )
}

/**
 * How the Auswärtiges Amt link should be labelled.
 *
 * Named for the country whose page it actually is. Greenland's advice lives on the
 * Denmark page, and a link that said "Foreign Office: Greenland" while opening a page
 * headed Dänemark would look like a bug in exactly the place this app claims to be
 * careful about.
 */
fun officialLinkText(requirement: VisaRequirement, nameOf: (String) -> String?): UiText {
    val parent = requirement.officialFor ?: return UiText.Res(R.string.visa_official_link)
    val name = nameOf(parent) ?: parent
    return UiText.Res(R.string.visa_official_link_for, name)
}
