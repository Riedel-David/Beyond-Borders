package com.riedel.beyondborders.ui.settings

import androidx.compose.foundation.layout.Column
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
    UiText.Res(R.string.settings_licence_flags) to UiText.Plain("country-flags, Wikimedia Commons (public domain)"),
    UiText.Res(R.string.settings_licence_visa) to UiText.Res(R.string.settings_licence_visa_body),
    UiText.Res(R.string.settings_licence_advice) to UiText.Res(R.string.settings_licence_advice_body),
    UiText.Res(R.string.settings_licence_libraries) to
        UiText.Plain("AndroidX, Jetpack Compose, Room, Kotlin coroutines (Apache-2.0)"),
)

private const val REPO_URL = "https://github.com/Riedel-David/Beyond-Borders"

// Hands the URL to the browser. The app itself declares no INTERNET permission and
// needs none for this: the browser does the network. Guarded like the visa screen's
// links, because a device with no browser would otherwise crash on tap.
@Composable
internal fun RepoLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { runCatching { uriHandler.openUri(REPO_URL) } },
        modifier = modifier,
    ) { Text("github.com/Riedel-David/Beyond-Borders") }
}

// There is no crash reporting or analytics, so the issue tracker is the only way a
// problem reaches the developer; keep it one tap from About.
@Composable
internal fun ReportProblemLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { runCatching { uriHandler.openUri("$REPO_URL/issues") } },
        modifier = modifier,
    ) { Text(stringResource(R.string.settings_report_problem)) }
}

@Composable
internal fun LicencesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licences_title)) },
        text = {
            SelectionContainer {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LICENCE_LINES.forEachIndexed { i, (label, body) ->
                        Text(
                            label.asString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 12.dp),
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
