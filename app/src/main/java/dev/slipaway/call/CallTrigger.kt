package dev.slipaway.call

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.util.Log
import dev.slipaway.data.Scheduled
import dev.slipaway.data.Store
import dev.slipaway.tile.RingTileService
import dev.slipaway.widget.RingWidget

/** Starts the fake call, now or after a delay. Every trigger (app, tile, shortcut) goes through here. */
object CallTrigger {
    private const val TAG = "SlipAway"
    const val EXTRA_CALLER_ID = "dev.slipaway.CALLER_ID"

    enum class Result { RINGING, SCHEDULED, NOT_ENABLED, BUSY, FAILED }

    /** Rings the selected caller after [delaySeconds] (0 = now). */
    fun schedule(context: Context, delaySeconds: Int): Result {
        val store = Store.get(context)
        if (!CallAccount.isEnabled(context)) return Result.NOT_ENABLED
        cancel(context)
        val callerId = store.selectedId.value
        if (delaySeconds <= 0) return ringNow(context, callerId)

        val delayMs = delaySeconds * 1000L
        val at = SystemClock.elapsedRealtime() + delayMs
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        try {
            if (canUseExactAlarms(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            // Exact-alarm access was taken away between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
        store.setScheduled(Scheduled(System.currentTimeMillis() + delayMs, delayMs, callerId))
        refreshTile(context)
        return Result.SCHEDULED
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        Store.get(context).setScheduled(null)
        refreshTile(context)
    }

    /** Rings now. Also learns whether the calling account is on, since the system refuses it if not. */
    fun ringNow(context: Context, callerId: String? = null): Result {
        val store = Store.get(context)
        store.setScheduled(null)
        refreshTile(context)

        if (FakeConnection.current != null) return Result.BUSY

        val caller = callerId?.let(store::caller) ?: store.selectedCaller
        val extras = Bundle().apply {
            putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, Bundle().apply {
                putString(EXTRA_CALLER_ID, caller.id)
            })
        }
        val number = caller.number.trim()
        if (number.isNotEmpty()) {
            extras.putParcelable(
                TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null),
            )
        }
        return try {
            CallAccount.telecom(context).addNewIncomingCall(CallAccount.handle(context), extras)
            markAccountOn(store, true)
            Result.RINGING
        } catch (e: SecurityException) {
            // The system refuses calls on an account that's switched off.
            Log.w(TAG, "Calling account is off", e)
            markAccountOn(store, false)
            Result.NOT_ENABLED
        } catch (e: Exception) {
            Log.w(TAG, "The system refused the incoming call", e)
            Result.FAILED
        }
    }

    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun describe(result: Result): String? = when (result) {
        Result.RINGING, Result.SCHEDULED -> null
        Result.NOT_ENABLED -> "Finish setup first: switch on Slip Away's calling account."
        Result.BUSY -> "A call is already ringing."
        Result.FAILED -> "Your phone wouldn't start the call."
    }

    private fun markAccountOn(store: Store, on: Boolean) {
        if (store.settings.value.accountOn != on) store.updateSettings { it.copy(accountOn = on) }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RingReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Brings the Quick Settings tile and home-screen widgets up to date. */
    private fun refreshTile(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context, ComponentName(context, RingTileService::class.java),
            )
        }
        runCatching { RingWidget.updateAll(context) }
    }
}
