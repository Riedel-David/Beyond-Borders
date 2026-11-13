package com.riedel.beyondborders.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.riedel.beyondborders.BuildConfig
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.places.ReviewChoice

/**
 * Our own dialog rather than Google's in-app review library: the app carries no Google
 * dependency and no INTERNET permission, and this keeps both true. Dismissing it by
 * back or tapping outside only hides it for this start; just Later records an ask.
 */
@Composable
internal fun ReviewPromptDialog(onChoice: (ReviewChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_title)) },
        text = { Text(stringResource(R.string.review_body)) },
        // One full-width column in the button slot rather than three buttons in its end-aligned
        // row, which would squeeze the labels (longer ones in German or Russian) together.
        // One filled primary action, the two opt-outs as quiet text buttons beneath it.
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button(onClick = { onChoice(ReviewChoice.RATE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_rate))
                }
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onChoice(ReviewChoice.LATER) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_later), textAlign = TextAlign.Center)
                    }
                    TextButton(onClick = { onChoice(ReviewChoice.NEVER) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_never), textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}

/** Opens the Play Store app on this app's page, or the web page when no store app answers.
 * Returns whether anything opened. */
internal fun openPlayStorePage(context: Context): Boolean {
    val id = BuildConfig.APPLICATION_ID
    fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(view("market://details?id=$id"))
        true
    } catch (_: Exception) { // not only ActivityNotFound: a restricted profile throws SecurityException
        runCatching { context.startActivity(view("https://play.google.com/store/apps/details?id=$id")) }.isSuccess
    }
}
