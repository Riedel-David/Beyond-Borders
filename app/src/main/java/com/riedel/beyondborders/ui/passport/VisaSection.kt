package com.riedel.beyondborders.ui.passport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaConfidence
import com.riedel.beyondborders.data.visa.VisaOrigin
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.band
import com.riedel.beyondborders.ui.places.VisaFilter
import com.riedel.beyondborders.ui.places.VisaRow
import com.riedel.beyondborders.ui.theme.LocalMapPalette
import com.riedel.beyondborders.ui.theme.VisaRamp
import com.riedel.beyondborders.ui.visa.asOfNotice
import com.riedel.beyondborders.ui.visa.officialLinkLabel
import com.riedel.beyondborders.ui.visa.requirementSummary

/**
 * The passport's other mode: not where you have been, but where this passport lets
 * you go.
 *
 * Same list, same search field, same 252 places as the travel mode — which is the
 * argument for it being a mode rather than a fifth tab. The rows are ordered easiest
 * first, so the answer to "where can I go" is the top of the screen.
 */
internal fun LazyListScope.visaSection(
    visa: VisaRegistry?,
    rows: List<VisaRow>,
    filter: VisaFilter,
    filters: List<VisaFilter>,
    query: String,
    selectedCode: String?,
    onQuery: (String) -> Unit,
    onFilter: (VisaFilter) -> Unit,
    onSelect: (String?) -> Unit,
    nameOf: (String) -> String?,
) {
    if (visa == null) {
        item("visa-missing") { VisaUnavailable() }
        return
    }

    item("visa-summary") {
        VisaSummaryCard(visa, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }

    item("visa-search") {
        SearchField(
            query = query,
            onQuery = onQuery,
            label = "Search destinations",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    item("visa-filters") {
        // Ten chips on one scrolling row, for the same reason the travel filters
        // scroll: they are one flat set, and "All" staying put on the left is what
        // makes the overflow discoverable.
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { onFilter(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }

    if (rows.isEmpty()) {
        item("visa-empty") {
            Text(
                "Nothing matches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    // Headers by category, inline, as in the travel list. Worth having even under a
    // single-category filter: it is the label the map's legend does not have room for.
    var lastHeader: String? = null
    rows.forEach { row ->
        val header = row.requirement.category.label
        if (header != lastHeader) {
            lastHeader = header
            item("visa-header-$header") {
                VisaSectionHeader(header, row.requirement.category.band)
            }
        }
        item("visa-${row.place.code}") {
            VisaListRow(
                row = row,
                expanded = row.place.code == selectedCode,
                onClick = {
                    onSelect(if (row.place.code == selectedCode) null else row.place.code)
                },
                nameOf = nameOf,
                visa = visa,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * The headline: what this passport is worth, as one number.
 *
 * The band bar underneath is the same four colours in the same order as the map's
 * lens, so switching to the map is recognising a picture rather than learning a new
 * one.
 */
@Composable
private fun VisaSummaryCard(visa: VisaRegistry, modifier: Modifier = Modifier) {
    val ramp = LocalMapPalette.current.visa
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${visa.visaFreeCount}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "  of ${visa.requirements.size} places visa-free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                "${visa.easyCount} reachable without an embassy appointment",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BandBar(visa, ramp, Modifier.padding(top = 12.dp))
            Text(
                asOfNotice(visa),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            // Attribution as a link rather than a line of small print: the licence
            // asks for the credit, and someone checking an answer deserves to be able
            // to reach the thing the answer came from.
            LinkText("${visa.datasetName} · ${visa.datasetLicence}", visa.datasetUrl)
        }
    }
}

/**
 * The four bands, sized by how many places are in each.
 *
 * A proportional bar rather than four numbers: "most of the world is one tap of
 * paperwork away" is a shape, not a figure.
 */
@Composable
private fun BandBar(visa: VisaRegistry, ramp: VisaRamp, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.extraSmall),
        ) {
            VisaBand.entries.forEach { band ->
                val count = visa.countByBand[band] ?: 0
                // A band with no places contributes no weight at all: Compose treats
                // a zero weight as an error, and a hairline segment would read as a
                // rounding artefact anyway.
                if (count > 0) {
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(ramp.colorFor(band))
                    )
                }
            }
        }
        // Two per row, as the continent bars are: four labels side by side do not fit
        // across a 360 dp phone, and a colour with a number but no word is a riddle.
        VisaBand.entries.filter { (visa.countByBand[it] ?: 0) > 0 }.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { band ->
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(ramp.colorFor(band))
                        )
                        Text(
                            "${band.label} ${visa.countByBand[band]}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A category header, carrying the band colour so the list and the map agree. */
@Composable
private fun VisaSectionHeader(text: String, band: VisaBand) {
    val ramp = LocalMapPalette.current.visa
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(ramp.colorFor(band))
        )
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One destination. Tapping opens the detail underneath rather than navigating: the
 * next row down is usually the comparison you wanted.
 */
@Composable
private fun VisaListRow(
    row: VisaRow,
    expanded: Boolean,
    onClick: () -> Unit,
    nameOf: (String) -> String?,
    visa: VisaRegistry,
) {
    val ramp = LocalMapPalette.current.visa
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The band as a stripe down the row: the one piece of the map's language
            // that survives being shrunk to list size.
            Box(
                Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(ramp.colorFor(row.requirement.category.band))
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(row.place.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(requirementSummary(row.requirement))
                        // "Visa-free and I have never been" is the row worth acting
                        // on, so the ones already crossed off say so.
                        if (row.status.isBeen) append(" · been")
                        if (row.status == PlaceStatus.WISHLIST) append(" · on the wishlist")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A curated answer that is not solid says so on the row, not only in the
            // detail: someone scanning the list should not have to open eight of them
            // to find out which one is shaky.
            if (row.requirement.confidence == VisaConfidence.LOW) {
                Text(
                    "check",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (expanded) {
            VisaDetail(
                row = row,
                visa = visa,
                nameOf = nameOf,
                modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/**
 * Everything the app knows about one answer, and where to go to check it.
 *
 * The date and the two official links are here as well as on the summary card
 * because this is the surface a decision actually gets made on, and a disclaimer
 * that has scrolled off the top of the screen is not a disclaimer.
 */
@Composable
private fun VisaDetail(
    row: VisaRow,
    visa: VisaRegistry,
    nameOf: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val requirement = row.requirement
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        requirement.note?.let { note ->
            Text(note, style = MaterialTheme.typography.bodyMedium)
        }
        requirement.confidence?.let { confidence ->
            Text(
                confidence.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (confidence == VisaConfidence.LOW) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        // A disagreement with upstream is shown rather than quietly won. If this
        // app's answer is the wrong one, the reader can see there is a second one.
        if (requirement.origin == VisaOrigin.CORRECTION && requirement.datasetSaid != null) {
            Text(
                "${visa.datasetName} says \"${requirement.datasetSaid}\" — corrected here",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            asOfNotice(visa),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        requirement.officialUrl?.let { url ->
            LinkText(officialLinkLabel(requirement, nameOf), url)
        }
        // Named for what it is. The IATA Travel Centre is one form for the whole
        // world, so calling it "IATA: Suriname" would promise a page that does not
        // exist.
        LinkText("IATA Travel Centre (search there)", visa.iataUrl)
        requirement.sourceUrl?.let { url ->
            LinkText("What this answer rests on", url)
        }
    }
}

/**
 * A link, as a text button in the primary colour.
 *
 * The tap is guarded because it leaves the app: a device with no browser — a locked
 * kiosk, a stripped ROM — would otherwise crash the visa screen, which is a poor
 * trade for a link that was only ever advisory.
 */
@Composable
private fun LinkText(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { runCatching { uriHandler.openUri(url) } },
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun VisaUnavailable() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("No visa data", style = MaterialTheme.typography.titleMedium)
        Text(
            "The bundled visa list could not be read, so this mode has nothing to " +
                "show. Your travel map is unaffected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
