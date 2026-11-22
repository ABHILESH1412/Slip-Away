package dev.slipaway.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.slipaway.MainActivity
import dev.slipaway.call.CallTrigger
import dev.slipaway.data.Store

/** Quick Settings tile: one tap rings after the delay chosen in the app; tap again to cancel. */
class RingTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val store = Store.get(this)
        if (store.scheduled.value != null) {
            CallTrigger.cancel(this)
        } else {
            val result = CallTrigger.schedule(this, store.settings.value.delaySeconds)
            if (result == CallTrigger.Result.NOT_ENABLED) openApp()
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val store = Store.get(this)
        val waiting = store.scheduled.value != null
        val delay = store.settings.value.delaySeconds
        tile.state = if (waiting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            waiting -> "Tap to cancel"
            delay <= 0 -> "${store.selectedCaller.title} · now"
            else -> "${store.selectedCaller.title} · ${Store.formatDelay(delay)}"
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            // Only reached below Android 14, where the PendingIntent version doesn't exist.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
