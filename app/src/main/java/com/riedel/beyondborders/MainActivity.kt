package com.riedel.beyondborders

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.riedel.beyondborders.data.db.DatabaseHealth
import com.riedel.beyondborders.data.db.DatabaseRecovery
import com.riedel.beyondborders.ui.BeyondBordersRoot
import com.riedel.beyondborders.ui.recovery.DatabaseRecoveryScreen
import com.riedel.beyondborders.ui.theme.BeyondBordersTheme
import com.riedel.beyondborders.widget.TravelWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The only Activity.
 *
 * AppCompatActivity rather than ComponentActivity because MapLibre's MapView
 * resolves AppCompat theme attributes; the Compose content itself does not care.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BeyondBordersApp).container

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
                    DatabaseHealth.Usable -> BeyondBordersRoot(container)
                    is DatabaseHealth.Unusable -> DatabaseRecoveryScreen(
                        detail = health.detail,
                        onWipe = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                DatabaseRecovery.wipe(this@MainActivity)
                                container.refreshDatabaseHealth()
                            }
                        },
                    )
                }
            }
        }
    }

    // The widget's own updatePeriodMillis is capped at 30 minutes; leaving the app
    // is the moment a count just changed on-screen is most likely to matter on the
    // home screen too, so it gets a nudge here rather than waiting that out.
    override fun onPause() {
        super.onPause()
        TravelWidgetProvider.requestUpdate(applicationContext)
    }
}
