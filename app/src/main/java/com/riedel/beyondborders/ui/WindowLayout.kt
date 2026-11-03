package com.riedel.beyondborders.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The Material "compact" boundary: below it the app is a phone and stays portrait. */
internal const val LARGE_SCREEN_DP = 600

/** Widest a list or form is allowed to get; the map and the bars are exempt. */
internal const val MAX_CONTENT_WIDTH_DP = 640

/**
 * Whether the activity should be locked to portrait. Decided on the *smallest* width, which
 * does not change when the device rotates, so a phone is always locked and a tablet never is.
 * Android 16 ignores the lock on windows of 600dp and up anyway; declining to ask for it
 * there is what stops Play flagging the manifest.
 */
internal fun shouldLockPortrait(smallestScreenWidthDp: Int): Boolean =
    smallestScreenWidthDp < LARGE_SCREEN_DP

/** Widest a dialog is allowed to get; narrower than screen content, which sits behind it. */
internal const val MAX_DIALOG_WIDTH_DP = 560

/**
 * Centres [content] in a column no wider than [MAX_CONTENT_WIDTH_DP], so rows do not stretch
 * across a tablet. On a phone the cap is never reached and this is a pass-through.
 */
@Composable
internal fun CappedWidth(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH_DP.dp).fillMaxSize(), content = content)
    }
}
