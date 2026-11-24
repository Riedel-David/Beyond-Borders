package com.riedel.beyondborders.ui.passport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.BeyondBordersApp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.advisory.ADVISORY_SOURCES
import com.riedel.beyondborders.data.advisory.AdvisoryData
import com.riedel.beyondborders.data.advisory.AdvisoryEntry
import com.riedel.beyondborders.data.advisory.Agreement
import com.riedel.beyondborders.data.advisory.agreementOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * What the three governments say about one place, side by side. There is no blended number
 * and no "safe" colour: stance 0 is a neutral grey, the colour only escalates from there.
 * Looked up by the place's own ISO code and nothing else, so a territory shows what the data
 * has for that exact code (usually nothing), never its sovereign's rating.
 *
 * Renders nothing when there is no advisory data at all (asset missing or unparsable), as
 * opposed to a place the data simply does not cover, which says "no data".
 */
@Composable
internal fun AdvisorySection(countryCode: String, modifier: Modifier = Modifier) {
    val repo = (LocalContext.current.applicationContext as? BeyondBordersApp)?.container?.advisory ?: return
    val version by repo.dataVersion.collectAsState()
    val data by produceState<AdvisoryData?>(null, repo, version) { value = repo.current() }
    val advisory = data ?: return
    val entries = advisory.forPlace(countryCode)
    val agreement = agreementOf(entries)
    val locale = LocalConfiguration.current.locales[0]

    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.advisory_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() },
        )
        advisoryHeadline(agreement)?.let { (resId, args) ->
            Text(stringResource(resId, *args), style = MaterialTheme.typography.bodyMedium)
        }
        if (agreement.withData in 1 until ADVISORY_SOURCES.size) {
            Text(
                stringResource(R.string.advisory_partial_data, agreement.withData, ADVISORY_SOURCES.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ADVISORY_SOURCES.forEach { key ->
            AdvisoryRow(key, advisory.sourceLabels[key] ?: key, entries[key], locale)
        }
        Text(
            stringResource(R.string.advisory_as_of, formatAdvisoryDate(asOfDate(advisory), locale)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.advisory_check_official),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdvisoryRow(key: String, source: String, entry: AdvisoryEntry?, locale: Locale) {
    Column(Modifier.fillMaxWidth()) {
        Text(source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry == null) {
            // Absent is not "no warning": the source simply has nothing for this place.
            Text(stringResource(R.string.advisory_no_data), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val (container, content) = advisoryColors(advisoryTone(entry.stance))
        Text(
            entry.label,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier
                .background(container, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        // A source's own sentences, quoted as published. The Auswärtiges Amt's are German in every
        // app locale, so they are tagged German for TalkBack's pronunciation; the US and UK ones are
        // English. Never truncated: the AA's terms forbid showing its text incomplete, so a sentence
        // wraps in full at any font scale.
        entry.notes.forEach { note ->
            val german = key == GERMAN_SOURCE
            Text(
                buildAnnotatedString {
                    if (german) withStyle(SpanStyle(localeList = LocaleList("de"))) { append(quoteAdvisoryNote(key, note)) }
                    else append(quoteAdvisoryNote(key, note))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.tags.isNotEmpty()) {
            Text(
                stringResource(R.string.advisory_tags, entry.tags.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The date takes the leftover width and wraps; the link keeps its own, so
            // neither a large font scale nor a long translation can push one off the row.
            Text(
                stringResource(R.string.advisory_updated, formatAdvisoryDate(entry.updated, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            val uriHandler = LocalUriHandler.current
            val description = stringResource(R.string.advisory_open_source_for, source)
            // Plain text rather than a TextButton, whose 40dp minimum height inflated the
            // row. Guarded like the other links: a device with no browser must not crash on tap.
            Text(
                stringResource(R.string.advisory_open_source),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .clickable(role = Role.Button) { runCatching { uriHandler.openUri(entry.url) } }
                    .semantics { contentDescription = description }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

internal enum class AdvisoryTone { NEUTRAL, CAUTION, WARNING, DANGER }

/**
 * Colours only escalate; nothing is green, so stance 0 reads as "no flag raised", not "safe".
 * Fixed pairs rather than theme roles: the theme's own roles put stance 0 and 1 on two nearly
 * identical blue-greys in light and made the strongest tone the palest in dark.
 */
@Composable
private fun advisoryColors(tone: AdvisoryTone): Pair<Color, Color> {
    val (container, content) = advisoryPalette(tone, dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    return Color(container) to Color(content)
}

// --- pure functions, unit-testable without Android ---------------------------

/** The source key whose notes are German. */
internal const val GERMAN_SOURCE = "DE"

/** German sentences in German quotes, English ones in English quotes. */
internal fun quoteAdvisoryNote(sourceKey: String, note: String): String =
    if (sourceKey == GERMAN_SOURCE) "„$note“" else "“$note”"

/**
 * The agreement line, or null when it should not be shown: with no data at all the "none" line,
 * with at least one source advising against travel the count, and otherwise nothing (the rows speak).
 */
internal fun advisoryHeadline(a: Agreement): Pair<Int, Array<Any>>? = when {
    a.withData == 0 -> R.string.advisory_none to emptyArray()
    a.against >= 1 -> R.string.advisory_agreement to arrayOf(a.against, a.withData)
    else -> null
}

/**
 * (badge fill, text) as packed ARGB. Text is at least 4.5:1 on its fill in both themes; in dark the
 * fills get lighter step by step (neutral, amber, dark red, bright red) so the strongest is never the palest,
 * and in light the amber of stance 1 stays clear of the blue-grey of stance 0.
 */
internal fun advisoryPalette(tone: AdvisoryTone, dark: Boolean): Pair<Int, Int> = if (dark) when (tone) {
    AdvisoryTone.NEUTRAL -> 0xFF2E363F.toInt() to 0xFFE2E6EC.toInt()
    AdvisoryTone.CAUTION -> 0xFF574100.toInt() to 0xFFFFEFC2.toInt()
    AdvisoryTone.WARNING -> 0xFF8C1D18.toInt() to 0xFFF9DEDC.toInt()
    AdvisoryTone.DANGER -> 0xFFC62828.toInt() to 0xFFFFFFFF.toInt()
} else when (tone) {
    AdvisoryTone.NEUTRAL -> 0xFFDEE4ED.toInt() to 0xFF131A22.toInt()
    AdvisoryTone.CAUTION -> 0xFFFFE2A0.toInt() to 0xFF3A2A00.toInt()
    AdvisoryTone.WARNING -> 0xFFF9DEDC.toInt() to 0xFF410E0B.toInt()
    AdvisoryTone.DANGER -> 0xFFB3261E.toInt() to 0xFFFFFFFF.toInt()
}

internal fun advisoryTone(stance: Int): AdvisoryTone = when (stance) {
    1 -> AdvisoryTone.CAUTION
    2 -> AdvisoryTone.WARNING
    3 -> AdvisoryTone.DANGER
    else -> AdvisoryTone.NEUTRAL
}

/** The source's `updated` day ("2026-10-02") in the user's locale; an unreadable one is shown as given. */
internal fun formatAdvisoryDate(iso: String, locale: Locale): String = try {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(LocalDate.parse(iso))
} catch (e: Exception) {
    iso
}

internal fun formatAdvisoryDate(day: LocalDate, locale: Locale): String = formatAdvisoryDate(day.toString(), locale)

private fun asOfDate(data: AdvisoryData): LocalDate =
    data.generatedAt.atZone(ZoneId.systemDefault()).toLocalDate()
