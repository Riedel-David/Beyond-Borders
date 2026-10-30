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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LICENCE_LINES = listOf(
    "Beyond Borders" to "GPL-3.0",
    "Map" to "MapLibre Native (BSD-2-Clause)",
    "Borders" to "Natural Earth (public domain)",
    "Labels" to "Noto Sans (SIL OFL 1.1)",
    "Visa data" to "Visa-requirements dataset, derived from Wikipedia contributors (CC BY-SA 4.0), see THIRD_PARTY_NOTICES.md in the repository",
    "Official advice links" to "Auswärtiges Amt (German Federal Foreign Office)",
    "Libraries" to "AndroidX, Jetpack Compose, Room, Kotlin coroutines (Apache-2.0)",
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
    ) { Text("Report a problem") }
}

@Composable
internal fun LicencesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-source licences") },
        text = {
            SelectionContainer {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LICENCE_LINES.forEachIndexed { i, (label, body) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 12.dp),
                        )
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                        if (i == 0) RepoLink()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
