package com.riedel.aeroglobe.ui

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
import com.riedel.aeroglobe.AppContainer
import com.riedel.aeroglobe.R
import com.riedel.aeroglobe.settings.HomeTab
import com.riedel.aeroglobe.ui.flights.FlightsScreen
import com.riedel.aeroglobe.ui.flights.FlightsViewModel
import com.riedel.aeroglobe.ui.map.MapScreen
import com.riedel.aeroglobe.ui.passport.PassportScreen
import com.riedel.aeroglobe.ui.places.PlacesViewModel
import com.riedel.aeroglobe.ui.settings.SettingsScreen

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    MAP("Map", R.drawable.ic_tab_map),
    PASSPORT("Passport", R.drawable.ic_tab_passport),
    FLIGHTS("Flights", R.drawable.ic_tab_flights),

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
 * Four tabs, no navigation library.
 *
 * navigation-compose's current release is a beta, and this is a flat switch with no
 * deep links or back stack to model — a saved index is the whole requirement.
 * Revisit when a screen needs real arguments in its route.
 */
@Composable
fun AeroGlobeRoot(container: AppContainer, modifier: Modifier = Modifier) {
    val homeTab by container.settings.homeTab.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(startTab(homeTab).ordinal) }
    val tab = Tab.entries[selected]

    // Hoisted to the root so the map and the passport share one instance: marking
    // a country on one is instantly visible on the other, with no syncing.
    val placesViewModel: PlacesViewModel = viewModel(
        factory = PlacesViewModel.factory(container.places, container.visa),
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
                Tab.FLIGHTS -> {
                    val flightsViewModel: FlightsViewModel = viewModel(
                        factory = FlightsViewModel.factory(
                            container.flights,
                            container.flightStatuses,
                            container.aircraftPositions,
                            container.settings,
                        ),
                    )
                    FlightsScreen(flightsViewModel)
                }
                Tab.SETTINGS -> SettingsScreen(container)
            }
        }
    }
}

/**
 * Which tab a cold start lands on.
 *
 * [HomeTab.AUTO] is the setting the app ships with: the flight screen wins while a
 * flight is being tracked, the map wins when none is. Flight tracking arrives in
 * phase 3, so for now AUTO always resolves to the map.
 */
internal fun startTab(homeTab: HomeTab, hasTrackedFlight: Boolean = false): Tab = when (homeTab) {
    HomeTab.MAP -> Tab.MAP
    HomeTab.FLIGHTS -> Tab.FLIGHTS
    HomeTab.AUTO -> if (hasTrackedFlight) Tab.FLIGHTS else Tab.MAP
}
