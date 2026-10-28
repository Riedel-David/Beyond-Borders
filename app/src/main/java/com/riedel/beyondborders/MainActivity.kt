package com.riedel.beyondborders

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.riedel.beyondborders.data.db.DatabaseHealth
import com.riedel.beyondborders.data.db.DatabaseRecovery
import com.riedel.beyondborders.ui.BeyondBordersRoot
import com.riedel.beyondborders.ui.Tab
import com.riedel.beyondborders.ui.recovery.DatabaseRecoveryScreen
import com.riedel.beyondborders.ui.theme.BeyondBordersTheme
import com.riedel.beyondborders.widget.TravelWidgetProvider
import com.riedel.beyondborders.widget.WidgetIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * The only Activity.
 *
 * AppCompatActivity rather than ComponentActivity because MapLibre's MapView
 * resolves AppCompat theme attributes; the Compose content itself does not care.
 */
class MainActivity : AppCompatActivity() {

    private var widgetIntent by mutableStateOf<WidgetIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BeyondBordersApp).container

        // Handle widget tap intents
        handleWidgetIntent(intent)

        setContent {
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
            val mapColors by container.settings.mapColors.collectAsStateWithLifecycle()
            val mapBase by container.settings.mapBase.collectAsStateWithLifecycle()
            val databaseHealth by container.databaseHealth.collectAsStateWithLifecycle()
            BeyondBordersTheme(themeMode, mapColors, mapBase) {
                // Nothing may query the database before the probe has cleared it, or
                // the crash this screen exists to replace happens first. Unknown is a
                // themed blank: the probe starts in Application.onCreate, one file
                // open ahead of this composition, so on a healthy database it has
                // normally already resolved by the time the first frame is drawn.
                when (val health = databaseHealth) {
                    DatabaseHealth.Unknown -> Surface(Modifier.fillMaxSize()) {}
                    DatabaseHealth.Usable -> BeyondBordersRoot(
                        container = container,
                        widgetIntent = widgetIntent,
                        onWidgetIntentConsumed = ::consumeWidgetIntent,
                    )
                    is DatabaseHealth.Unusable -> DatabaseRecoveryScreen(
                        detail = health.detail,
                        onWipe = {
                            withContext(Dispatchers.IO) {
                                val ok = DatabaseRecovery.wipe(this@MainActivity)
                                container.refreshDatabaseHealth()
                                ok
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode="singleTop" delivers a tap on an already-running app here rather
        // than stacking a second instance.
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent) {
        val tabName = intent.getStringExtra("widget_open_tab")
        val openStats = intent.getBooleanExtra("widget_open_stats", false)
        if (tabName != null) {
            // Use uptimeMillis to give each tap a distinct identity. WidgetIntent is a
            // data class with structural equality, so assigning the same (tab, openStats)
            // values is a no-op write: the state never changes, LaunchedEffect never
            // re-runs, and a second tap on the same widget does nothing.
            widgetIntent = WidgetIntent(tabName, openStats, SystemClock.uptimeMillis())
            // Clear the extras so rotation does not replay this intent.
            intent.removeExtra("widget_open_tab")
            intent.removeExtra("widget_open_stats")
        }
    }

    /** Called from BeyondBordersRoot after applying the intent, so rotation does not replay it. */
    fun consumeWidgetIntent() {
        widgetIntent = null
    }

    // The widget's own updatePeriodMillis is capped at 30 minutes; leaving the app
    // is the moment a count just changed on-screen is most likely to matter on the
    // home screen too, so it gets a nudge here rather than waiting that out.
    //
    // Backgrounding is also the moment to fold the write-ahead log back into the
    // database file (see AppContainer.checkpointDatabase, now a crash-recovery
    // measure rather than a backup one) and to refresh the JSON snapshot Auto
    // Backup actually carries. lifecycleScope survives onPause — it is cancelled
    // on destroy — and Dispatchers.IO because both touch disk.
    override fun onPause() {
        super.onPause()
        TravelWidgetProvider.requestUpdate(applicationContext)
        val container = (application as BeyondBordersApp).container
        lifecycleScope.launch(Dispatchers.IO) {
            container.checkpointDatabase()
            container.stageCloudSnapshot()
        }
    }
}
