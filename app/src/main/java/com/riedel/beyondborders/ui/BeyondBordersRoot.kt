package com.riedel.beyondborders.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riedel.beyondborders.AppContainer
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.map.MapScreen
import com.riedel.beyondborders.ui.passport.PassportScreen
import com.riedel.beyondborders.ui.places.PlacesViewModel
import com.riedel.beyondborders.ui.settings.SettingsScreen
import com.riedel.beyondborders.ui.welcome.WelcomeScreen

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    MAP("Map", R.drawable.ic_tab_map),
    PASSPORT("Passport", R.drawable.ic_tab_passport),

    /**
     * A destination rather than the floating gear PLAN.md originally sketched: both
     * of the built screens already own their top edge — the map floats controls in
     * both top corners, the passport opens with a full-width stats card — so an
     * icon up there would have had to overlap one of them. It also makes export and
     * import discoverable, which for the one piece of data in this app that cannot
     * be re-downloaded is worth a tab.
     */
    SETTINGS("Settings", R.drawable.ic_settings),
}

/**
 * Three tabs, no navigation library.
 *
 * navigation-compose's current release is a beta, and this is a flat switch with no
 * deep links or back stack to model — a saved index is the whole requirement.
 * Revisit when a screen needs real arguments in its route.
 */
@Composable
fun BeyondBordersRoot(container: AppContainer, modifier: Modifier = Modifier) {
    val hasSeenWelcome by container.settings.hasSeenWelcome.collectAsStateWithLifecycle()
    if (!hasSeenWelcome) {
        val nationality by container.settings.nationality.collectAsStateWithLifecycle()
        WelcomeScreen(
            nationality = nationality,
            onNationalityChange = container.settings::setNationality,
            onGetStarted = container.settings::setHasSeenWelcome,
            modifier = modifier,
        )
        return
    }

    var selected by rememberSaveable { mutableIntStateOf(Tab.MAP.ordinal) }
    val tab = Tab.entries[selected]

    // Hoisted to the root so the map and the passport share one instance: marking
    // a country on one is instantly visible on the other, with no syncing.
    val placesViewModel: PlacesViewModel = viewModel(
        factory = PlacesViewModel.factory(container.places, container.visa, container.settings, container.haptics),
    )

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { selected = entry.ordinal },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                // The map keeps its own inset handling: it draws to the very edge
                // and floats its controls above the system bars.
                Tab.MAP -> MapScreen(placesViewModel)
                Tab.PASSPORT -> PassportScreen(placesViewModel)
                Tab.SETTINGS -> SettingsScreen(container)
            }
        }
    }
}
