package com.riedel.beyondborders.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riedel.beyondborders.AppContainer
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.map.MapScreen
import com.riedel.beyondborders.ui.passport.PassportScreen
import com.riedel.beyondborders.ui.places.PlacesViewModel
import com.riedel.beyondborders.ui.settings.SettingsScreen
import com.riedel.beyondborders.ui.welcome.WelcomeScreen
import com.riedel.beyondborders.widget.WidgetIntent

enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    MAP(R.string.root_tab_map, R.drawable.ic_tab_map),
    PASSPORT(R.string.root_tab_passport, R.drawable.ic_tab_passport),

    /**
     * A destination rather than the floating gear originally sketched: both
     * of the built screens already own their top edge — the map floats controls in
     * both top corners, the passport opens with a full-width stats card — so an
     * icon up there would have had to overlap one of them. It also makes export and
     * import discoverable, which for the one piece of data in this app that cannot
     * be re-downloaded is worth a tab.
     */
    SETTINGS(R.string.root_tab_settings, R.drawable.ic_settings),
}

/**
 * Three tabs, no navigation library.
 *
 * navigation-compose's current release is a beta, and this is a flat switch with no
 * deep links or back stack to model — a saved index is the whole requirement.
 * Revisit when a screen needs real arguments in its route.
 */
@Composable
fun BeyondBordersRoot(
    container: AppContainer,
    widgetIntent: WidgetIntent? = null,
    onWidgetIntentConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hasSeenWelcome by container.settings.hasSeenWelcome.collectAsStateWithLifecycle()
    // Hoisted above the welcome gate so the welcome screen, the map, the passport and
    // Settings all read the one registry copy the ViewModel holds. It starts empty and
    // fills asynchronously, so the counts below are 0 until it has loaded and every
    // consumer treats 0 as "not yet" rather than printing it.
    val placesViewModel: PlacesViewModel = viewModel(
        factory = PlacesViewModel.factory(container.places, container.visa, container.settings, container.haptics),
    )
    // Every change of the configuration's first locale, including one made from the
    // system's per-app-language screen, re-derives the place names.
    val locale = LocalConfiguration.current.locales[0]
    // Not a LaunchedEffect: that runs after the first composition, so the retained
    // ViewModel (still on the old locale) drew one frame in the old language. This is an
    // idempotent StateFlow write, kept in `remember` so it happens once per locale and,
    // placed directly after the ViewModel is obtained, before any of its state is
    // collected below. Not a SideEffect, which would run only after those reads.
    remember(locale) { placesViewModel.setLocale(locale) }
    val allPlaces by placesViewModel.places.collectAsStateWithLifecycle()
    val passportNames by placesViewModel.passportNames.collectAsStateWithLifecycle()
    val englishNames by placesViewModel.englishPassportNames.collectAsStateWithLifecycle()
    val placeCount = allPlaces.size
    val unMemberCount = remember(allPlaces) { allPlaces.count { it.unMember } }
    if (!hasSeenWelcome) {
        val nationality by container.settings.nationality.collectAsStateWithLifecycle()
        WelcomeScreen(
            nationality = nationality,
            onNationalityChange = container.settings::setNationality,
            passportNames = passportNames,
            englishNames = englishNames,
            placeCount = placeCount,
            unMemberCount = unMemberCount,
            onGetStarted = container.settings::setHasSeenWelcome,
            modifier = modifier,
        )
        return
    }

    val placeNames = remember(allPlaces) { allPlaces.associate { it.code to it.name } }
    var selected by rememberSaveable { mutableIntStateOf(Tab.MAP.ordinal) }
    var openStats by rememberSaveable { mutableStateOf<Boolean?>(null) }

    // Handle widget intent: set the tab and stats flag, then consume it. Uses Tab enum
    // name matching so a typo is a visible no-op rather than a silent one.
    LaunchedEffect(widgetIntent) {
        widgetIntent?.let { intent ->
            Tab.entries.firstOrNull { it.name == intent.tabName }?.let { tab ->
                selected = tab.ordinal
                openStats = intent.openStats
            }
            onWidgetIntentConsumed()
        }
    }
    val tab = Tab.entries[selected]

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { selected = entry.ordinal },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        // rememberSaveableStateHolder keeps each tab's own composition alive under
        // the one that's showing, rather than disposing it — so leaving Settings
        // mid-import or mid-scroll and coming back finds it exactly as it was.
        val tabState = rememberSaveableStateHolder()
        AnimatedContent(
            targetState = tab,
            label = "tab",
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            tabState.SaveableStateProvider(current.name) {
                when (current) {
                    // The map draws to the very edge and floats its own controls
                    // above the system bars, so it takes the Scaffold's padding as
                    // contentPadding instead of having it applied here.
                    Tab.MAP -> Box(Modifier.fillMaxSize()) {
                        MapScreen(placesViewModel, contentPadding = padding)
                    }
                    Tab.PASSPORT -> Box(Modifier.fillMaxSize().padding(padding)) {
                        PassportScreen(
                            viewModel = placesViewModel,
                            openStats = openStats,
                            onStatsConsumed = { openStats = null },
                        )
                    }
                    Tab.SETTINGS -> Box(Modifier.fillMaxSize().padding(padding)) {
                        SettingsScreen(container, placeCount = placeCount, unMemberCount = unMemberCount, placeNames = placeNames, passportNames = passportNames, englishNames = englishNames)
                    }
                }
            }
        }
    }
}
