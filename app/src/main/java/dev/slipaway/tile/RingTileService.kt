package dev.slipaway.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.slipaway.MainActivity
import dev.slipaway.Prefs
import dev.slipaway.call.CallTrigger

/** Quick Settings tile: one tap rings after the delay chosen in the app; tap again to cancel. */
class RingTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val prefs = Prefs(this)
        if (prefs.scheduledAt > 0L) {
            CallTrigger.cancel(this)
        } else {
            val result = CallTrigger.schedule(this, prefs.delaySeconds)
            if (result == CallTrigger.Result.NOT_ENABLED) openApp()
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val prefs = Prefs(this)
        val waiting = prefs.scheduledAt > 0L
        tile.state = if (waiting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (waiting) {
            "Tap to cancel"
        } else if (prefs.delaySeconds <= 0) {
            "Ring now"
        } else {
            "Ring in ${Prefs.formatDelay(prefs.delaySeconds)}"
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
