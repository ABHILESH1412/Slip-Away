package dev.slipaway

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slipaway.call.CallAccount
import dev.slipaway.data.Store
import dev.slipaway.ui.SlipAwayApp
import dev.slipaway.ui.SlipAwayTheme
import dev.slipaway.ui.isDarkTheme
import dev.slipaway.update.UpdateWorker
import dev.slipaway.update.Updates

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store.get(this)
        // Safe to repeat: re-registering keeps the account's on/off switch as the user left it.
        CallAccount.register(this)
        setContent {
            val settings by store.settings.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.themeMode)
            // Status and navigation bar icons follow the app's theme, not just the phone's.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            SlipAwayTheme(dark = dark, wallpaperColors = settings.wallpaperColors) {
                SlipAwayApp(store)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Updates.appVisible = true
        UpdateWorker.checkSoon(this)
    }

    override fun onPause() {
        Updates.appVisible = false
        super.onPause()
    }
}
