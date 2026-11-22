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
import dev.slipaway.Prefs
import dev.slipaway.tile.RingTileService

/** Starts the fake call, now or after a delay. Every trigger (app, tile, shortcut) goes through here. */
object CallTrigger {
    private const val TAG = "SlipAway"

    enum class Result { RINGING, SCHEDULED, NOT_ENABLED, BUSY, FAILED }

    fun schedule(context: Context, delaySeconds: Int): Result {
        if (!CallAccount.isEnabled(context)) return Result.NOT_ENABLED
        cancel(context)
        if (delaySeconds <= 0) return ringNow(context)

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
        Prefs(context).scheduledAt = System.currentTimeMillis() + delayMs
        refreshTile(context)
        return Result.SCHEDULED
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        Prefs(context).scheduledAt = 0L
        refreshTile(context)
    }

    fun ringNow(context: Context): Result {
        val prefs = Prefs(context)
        prefs.scheduledAt = 0L
        refreshTile(context)

        if (FakeConnection.current != null) return Result.BUSY

        val extras = Bundle()
        val number = prefs.callerNumber.trim()
        if (number.isNotEmpty()) {
            extras.putParcelable(
                TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null),
            )
        }
        return try {
            CallAccount.telecom(context).addNewIncomingCall(CallAccount.handle(context), extras)
            prefs.accountOn = true
            Result.RINGING
        } catch (e: SecurityException) {
            // The system refuses calls on an account that's switched off.
            Log.w(TAG, "Calling account is off", e)
            prefs.accountOn = false
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
        Result.NOT_ENABLED -> "Finish setup first: switch on the calling account in Slip Away."
        Result.BUSY -> "A call is already ringing."
        Result.FAILED -> "Your phone wouldn't start the call."
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RingReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun refreshTile(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context, ComponentName(context, RingTileService::class.java),
            )
        }
    }
}
