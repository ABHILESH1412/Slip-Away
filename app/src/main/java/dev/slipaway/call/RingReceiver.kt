package dev.slipaway.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the alarm when a delayed call is due. */
class RingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CallTrigger.ringNow(context)
    }
}
