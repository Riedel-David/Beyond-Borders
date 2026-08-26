package com.riedel.beyondborders.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R

/**
 * Shown once, before the first launch reaches the tabs. [com.riedel.beyondborders.settings.SettingsStore.hasSeenWelcome]
 * is what stops it coming back — see [com.riedel.beyondborders.ui.BeyondBordersRoot].
 *
 * States the two things a new user cannot infer from the tabs themselves: that
 * nothing here leaves the phone, and that the visa answers assume one specific
 * passport. Everything else — how marking works, what the colours mean — is
 * discoverable from using the app, so it stays out of this screen.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text(
                "Beyond Borders",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Mark the places you've been, lived in, or want to go — on a map " +
                    "of all 252, not just the 195 in the UN.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Everything you mark stays on this phone. No account, no server, " +
                    "no network — export a backup yourself whenever you want one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Visa requirements shown here assume a German passport — there is no " +
                    "switcher yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            FilledTonalButton(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) {
                Text("Get started")
            }
        }
    }
}
