package dev.slipaway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.slipaway.call.CallAccount
import dev.slipaway.ui.MainScreen
import dev.slipaway.ui.SlipAwayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Safe to repeat: re-registering keeps the account's on/off switch as the user left it.
        CallAccount.register(this)
        setContent {
            SlipAwayTheme { MainScreen() }
        }
    }
}
