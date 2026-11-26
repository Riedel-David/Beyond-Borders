package com.riedel.beyondborders.ui.settings

import androidx.compose.foundation.layout.Column
import com.riedel.beyondborders.ui.NavRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.UiText
import com.riedel.beyondborders.ui.asString

// Licence names are proper nouns and stay as plain text; only the headings and the two
// bodies that are sentences are resources.
private val LICENCE_LINES = listOf(
    UiText.Res(R.string.settings_licence_app) to UiText.Plain("GPL-3.0"),
    UiText.Res(R.string.settings_licence_map) to UiText.Plain("MapLibre Native (BSD-2-Clause)"),
    UiText.Res(R.string.settings_licence_borders) to UiText.Plain("Natural Earth (public domain)"),
    UiText.Res(R.string.settings_licence_labels) to UiText.Plain("Noto Sans (SIL OFL 1.1)"),
    UiText.Res(R.string.settings_licence_font) to UiText.Plain("Inter (SIL OFL 1.1)"),
    UiText.Res(R.string.settings_licence_flags) to UiText.Plain("country-flags, Wikimedia Commons (public domain)"),
    UiText.Res(R.string.settings_licence_libraries) to
        UiText.Plain("AndroidX, Jetpack Compose, Room, Kotlin coroutines (Apache-2.0)"),
)

// One data source: a heading, what it is, and the pages a reader can open to check it. Link
// texts are host names and licence names, so they stay plain text like the licence names above.
private class SourceEntry(val label: UiText?, val body: UiText, val links: List<Pair<String, String>>)

// The Auswärtiges Amt line is its required attribution, so its first sentence is the AA's own
// wording in every language, and the app says plainly that it shows only the warning status.
private val SOURCE_ENTRIES = listOf(
    SourceEntry(
        UiText.Res(R.string.settings_licence_visa),
        UiText.Res(R.string.settings_licence_visa_body),
        listOf("github.com/Riedel-David/Visa-requirements" to "https://github.com/Riedel-David/Visa-requirements"),
    ),
    SourceEntry(
        UiText.Res(R.string.settings_source_advisory),
        UiText.Res(R.string.settings_source_advisory_body),
        emptyList(),
    ),
    SourceEntry(
        null,
        UiText.Res(R.string.settings_source_us),
        listOf("travel.state.gov" to "https://travel.state.gov/content/travel/en/traveladvisories/traveladvisories.html"),
    ),
    SourceEntry(
        null,
        UiText.Res(R.string.settings_source_uk),
        listOf(
            "gov.uk/foreign-travel-advice" to "https://www.gov.uk/foreign-travel-advice",
            "Open Government Licence v3.0" to "https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/",
        ),
    ),
    SourceEntry(
        null,
        UiText.Res(R.string.settings_source_de),
        listOf("auswaertiges-amt.de" to "https://www.auswaertiges-amt.de/de/service/laender"),
    ),
)

private const val REPO_URL = "https://github.com/Riedel-David/Beyond-Borders"

// Hands the URL to the browser. The app needs no network access of its own for
// this: the browser does the network. Guarded like the visa screen's
// links, because a device with no browser would otherwise crash on tap.
@Composable
internal fun RepoLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { runCatching { uriHandler.openUri(REPO_URL) } },
        modifier = modifier,
    ) { Text("github.com/Riedel-David/Beyond-Borders") }
}

// Same guarded hand-off to the browser as RepoLink, pulled left by the button's own padding.
@Composable
private fun SourceLink(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { runCatching { uriHandler.openUri(url) } },
        modifier = Modifier.offset(x = (-12).dp),
    ) { Text(text) }
}

// There is no crash reporting or analytics, so the issue tracker is the only way a
// problem reaches the developer; keep it one tap from About.
@Composable
internal fun ReportProblemLink() {
    val uriHandler = LocalUriHandler.current
    NavRow(
        title = stringResource(R.string.settings_report_problem),
        onClick = { runCatching { uriHandler.openUri("$REPO_URL/issues") } },
    )
}

@Composable
internal fun LicencesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licences_title)) },
        text = {
            SelectionContainer {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.settings_sources_heading),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SOURCE_ENTRIES.forEach { entry ->
                        // The three governments sit under one "Travel advisories" heading.
                        if (entry.label != null) Text(
                            entry.label.asString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            entry.body.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = if (entry.label == null) 8.dp else 0.dp),
                        )
                        entry.links.forEach { (text, url) -> SourceLink(text, url) }
                    }
                    Text(
                        stringResource(R.string.settings_licences_heading),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    LICENCE_LINES.forEachIndexed { i, (label, body) ->
                        Text(
                            label.asString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(body.asString(), style = MaterialTheme.typography.bodyMedium)
                        if (i == 0) RepoLink()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_licences_close)) } },
    )
}
