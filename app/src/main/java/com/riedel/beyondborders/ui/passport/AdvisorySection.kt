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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.BeyondBordersApp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.advisory.ADVISORY_SOURCES
import com.riedel.beyondborders.data.advisory.AdvisoryData
import com.riedel.beyondborders.data.advisory.AdvisoryEntry
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
        )
        Text(
            if (agreement.withData == 0) stringResource(R.string.advisory_none)
            else stringResource(R.string.advisory_agreement, agreement.against, agreement.withData),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (agreement.withData in 1 until ADVISORY_SOURCES.size) {
            Text(
                stringResource(R.string.advisory_partial_data, agreement.withData, ADVISORY_SOURCES.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ADVISORY_SOURCES.forEach { key ->
            AdvisoryRow(advisory.sourceLabels[key] ?: key, entries[key], locale)
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
private fun AdvisoryRow(source: String, entry: AdvisoryEntry?, locale: Locale) {
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
        // The Auswärtiges Amt's own sentences, quoted as published. They are German in every app
        // locale, so the text is tagged German for TalkBack's pronunciation.
        entry.notes.forEach { note ->
            Text(
                buildAnnotatedString { withStyle(SpanStyle(localeList = LocaleList("de"))) { append("„$note“") } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
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

/** Colours only escalate; nothing is green, so stance 0 reads as "no flag raised", not "safe". */
@Composable
private fun advisoryColors(tone: AdvisoryTone): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    // Container/on-container pairs and error/onError are the theme's own, so light and dark
    // contrast comes with them.
    return when (tone) {
        AdvisoryTone.NEUTRAL -> c.surfaceContainerHighest to c.onSurface
        AdvisoryTone.CAUTION -> c.secondaryContainer to c.onSecondaryContainer
        AdvisoryTone.WARNING -> c.errorContainer to c.onErrorContainer
        AdvisoryTone.DANGER -> c.error to c.onError
    }
}

// --- pure functions, unit-testable without Android ---------------------------

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
