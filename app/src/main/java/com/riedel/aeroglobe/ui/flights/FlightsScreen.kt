package com.riedel.aeroglobe.ui.flights

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.riedel.aeroglobe.R
import com.riedel.aeroglobe.ui.ComingSoon

@Composable
fun FlightsScreen(modifier: Modifier = Modifier) {
    ComingSoon(
        icon = R.drawable.ic_tab_flights,
        title = "Flights",
        detail = "Gate, aircraft position, delay risk and a log of what changed " +
            "since you last looked. Phase 3.",
        modifier = modifier,
    )
}
