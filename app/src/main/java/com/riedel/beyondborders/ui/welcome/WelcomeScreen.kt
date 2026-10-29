package com.riedel.beyondborders.ui.welcome

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
    nationality: String?,
    onNationalityChange: (String) -> Unit,
    passportNames: Map<String, String>,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier) { padding ->
        // Centred while it fits, scrollable once it doesn't. At a large font scale or
        // in landscape this column is taller than the screen, and without somewhere
        // to scroll "Get started" goes off the bottom edge — which strands the user
        // on the one screen whose button is the only way past it. The minimum height
        // is what keeps the centring: a plain scrolling column would sit at the top
        // on the ordinary phone-sized case this is designed around.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val viewport = maxHeight
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .fillMaxWidth()
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
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    stringResource(R.string.welcome_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    stringResource(R.string.welcome_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    stringResource(R.string.welcome_passport_hint),
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
                Button(
                    onClick = onGetStarted,
                    enabled = nationality != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                ) {
                    Text(stringResource(R.string.welcome_get_started))
                }
            }
        }
    }
}
