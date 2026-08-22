package com.riedel.aeroglobe

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.ui.AeroGlobeRoot
import com.riedel.aeroglobe.ui.theme.AeroGlobeTheme
import com.riedel.aeroglobe.widget.TravelWidgetProvider

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

        val container = (application as AeroGlobeApp).container

        setContent {
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
            val mapColors by container.settings.mapColors.collectAsStateWithLifecycle()
            val mapBase by container.settings.mapBase.collectAsStateWithLifecycle()
            AeroGlobeTheme(themeMode, mapColors, mapBase) {
                AeroGlobeRoot(container)
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
