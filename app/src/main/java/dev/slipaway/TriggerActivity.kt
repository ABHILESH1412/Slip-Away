package dev.slipaway

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import dev.slipaway.call.CallTrigger

/** No screen of its own: the launcher shortcuts open this, it schedules the call and closes. */
class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        // Shortcut XML may hand the value over as an Int or as a String.
        val delay = extras?.getString(EXTRA_DELAY)?.toIntOrNull()
            ?: extras?.getInt(EXTRA_DELAY, -1)?.takeIf { it >= 0 }
            ?: Prefs(this).delaySeconds

        val result = CallTrigger.schedule(this, delay)
        val message = CallTrigger.describe(result)
            ?: if (result == CallTrigger.Result.SCHEDULED) "Ringing in ${Prefs.formatDelay(delay)}" else null
        message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        finish()
    }

    companion object {
        const val EXTRA_DELAY = "delay_seconds"
    }
}
