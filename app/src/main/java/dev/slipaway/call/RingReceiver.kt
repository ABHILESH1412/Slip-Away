package dev.slipaway.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.slipaway.data.Store

/** Fired by the alarm when a delayed call is due. */
class RingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callerId = Store.get(context).scheduled.value?.callerId
        CallTrigger.ringNow(context, callerId)
    }
}
