package com.riedel.beyondborders.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.riedel.beyondborders.AppContainer
import com.riedel.beyondborders.BeyondBordersApp
import com.riedel.beyondborders.ui.places.TravelStats
import com.riedel.beyondborders.ui.places.computeStats
import com.riedel.beyondborders.ui.theme.BeyondBordersTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown once, when the text widget is dropped on the home screen — what it counts
 * and whether it carries its own card background. [WidgetPrefs] is the only place
 * this choice is stored; there is no in-app screen for it because it only ever
 * applies to a widget instance, not to the app.
 */
class TravelWidgetConfigActivity : AppCompatActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cancelled unless setResult(RESULT_OK) below is reached: backing out of
        // this screen must not leave a half-configured widget on the home screen.
        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val container = (application as BeyondBordersApp).container

        setContent {
            BeyondBordersTheme {
                ConfigScreen(
                    container = container,
                    onDone = { metric, filledBackground -> save(metric, filledBackground) },
                )
            }
        }
    }

    private fun save(metric: WidgetMetric, filledBackground: Boolean) {
        lifecycleScope.launch {
            // Both the preferences write and the widget update read or touch disk and
            // the AppWidget binder, so they belong off the main thread — this runs at
            // the moment the user taps Done, which is the worst moment to stall.
            withContext(Dispatchers.IO) {
                WidgetPrefs.save(this@TravelWidgetConfigActivity, widgetId, metric, filledBackground)
                val manager = AppWidgetManager.getInstance(this@TravelWidgetConfigActivity)
                TravelWidgetProvider.updateOne(this@TravelWidgetConfigActivity, manager, widgetId)
            }
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    container: AppContainer,
    onDone: (WidgetMetric, Boolean) -> Unit,
) {
    var metric by remember { mutableStateOf(WidgetMetric.DEFAULT) }
    var filledBackground by remember { mutableStateOf(true) }

    // Fetch the real stats for the preview
    var stats by remember { mutableStateOf(TravelStats.EMPTY) }
    LaunchedEffect(Unit) {
        val statuses = container.places.statuses.first()
        val denominator = container.settings.denominator.first()
        // Assign the result, not the state itself, so the snapshot state is written
        // on the main thread rather than from the IO dispatcher.
        stats = withContext(Dispatchers.IO) {
            computeStats(
                container.places.places(),
                statuses,
                denominator,
            )
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("What should the widget show?", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WidgetMetric.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = metric == option,
                        onClick = { metric = option },
                        shape = SegmentedButtonDefaults.itemShape(index, WidgetMetric.entries.size),
                    ) {
                        Text(
                            when (option) {
                                WidgetMetric.COUNTRIES -> "Countries"
                                WidgetMetric.PERCENT -> "Percent"
                                WidgetMetric.CONTINENTS -> "Continents"
                            }
                        )
                    }
                }
            }

            Column {
                Text("Card background", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (filledBackground) "Filled" else "Transparent")
                    Switch(checked = filledBackground, onCheckedChange = { filledBackground = it })
                }
            }

            // Live preview: inflate the real RemoteViews at MEDIUM tier
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f),
            ) {
                AndroidView(
                    factory = { context ->
                        android.widget.FrameLayout(context)
                    },
                    update = { frameLayout ->
                        frameLayout.removeAllViews()
                        val views = travelWidgetViews(
                            context = frameLayout.context,
                            tier = WidgetTier.MEDIUM,
                            metric = metric,
                            filled = filledBackground,
                            stats = stats,
                        )
                        frameLayout.addView(views.apply(frameLayout.context, frameLayout))
                    },
                )
            }

            FilledTonalButton(
                onClick = { onDone(metric, filledBackground) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add widget")
            }
        }
    }
}
