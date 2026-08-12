package com.riedel.aeroglobe

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.aeroglobe.ui.AeroGlobeRoot
import com.riedel.aeroglobe.ui.theme.AeroGlobeTheme

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
            AeroGlobeTheme(themeMode) {
                AeroGlobeRoot(container)
            }
        }
    }
}
