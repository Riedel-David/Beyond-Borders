package com.riedel.beyondborders.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.visa.PassportPickerButton

/**
 * The launcher icon's own background gradient (see ic_launcher_background.xml),
 * reproduced here rather than referenced: [R.drawable.ic_launcher_foreground] is
 * only the adaptive icon's foreground layer, a pale globe that the system always
 * composites on top of this gradient. Drawn alone on a light theme's near-white
 * background, it disappears.
 */
private val LauncherIconBackground = Brush.linearGradient(
    listOf(Color(0xFF1E6FE3), Color(0xFF0A2E67)),
)

/**
 * Shown once, before the first launch reaches the tabs. [com.riedel.beyondborders.settings.SettingsStore.hasSeenWelcome]
 * is what stops it coming back — see [com.riedel.beyondborders.ui.BeyondBordersRoot].
 *
 * States the two things a new user cannot infer from the tabs themselves: that
 * nothing here leaves the phone, and that the visa answers are for one specific
 * passport — which this screen is also where that passport gets picked, since a
 * choice a user never makes defaults quietly to DE and never invites a second
 * look. Everything else — how marking works, what the colours mean — is
 * discoverable from using the app, so it stays out of this screen.
 */
@Composable
fun WelcomeScreen(
    nationality: String,
    onNationalityChange: (String) -> Unit,
    passportNames: Map<String, String>,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(LauncherIconBackground),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            }
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
                "Visa requirements are for one passport at a time. Pick yours — you " +
                    "can change it later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            PassportPickerButton(
                selected = nationality,
                names = passportNames,
                onSelect = onNationalityChange,
                modifier = Modifier.padding(top = 12.dp),
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
