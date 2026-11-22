package dev.slipaway.call

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import dev.slipaway.R
import dev.slipaway.data.Store

/**
 * The "calling account" Slip Away registers with the system, like an internet-calling app does.
 * Because calls come in through it, the phone's own dialer draws the ringing screen.
 */
object CallAccount {
    private const val TAG = "SlipAway"
    private const val ACCOUNT_ID = "slipaway_line"

    /**
     * Only this made-up scheme is listed as supported, not "tel". That keeps the account out of the
     * running for *outgoing* phone calls, so the dialer never starts asking "call with SIM or Phone?".
     * Incoming calls can still carry a normal tel: number.
     */
    private const val PRIVATE_SCHEME = "slipaway"

    fun handle(context: Context) = PhoneAccountHandle(
        ComponentName(context, FakeCallService::class.java), ACCOUNT_ID,
    )

    /**
     * The name shown in Calling accounts and, on some dialers, as "Incoming call via …".
     *
     * By default it copies the SIM's name (e.g. "airtel"). Once a phone has two calling accounts,
     * Google's dialer marks real SIM calls "via airtel" as well, so a fake call then reads the same.
     */
    fun label(context: Context): String =
        Store.get(context).settings.value.accountLabel.trim().ifEmpty { defaultLabel(context) }

    fun defaultLabel(context: Context): String =
        context.getSystemService(TelephonyManager::class.java)?.simOperatorName?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.call_account_label)

    /** Registers the account, or updates its name. The user's on/off switch is kept either way. */
    fun register(context: Context) {
        val label = label(context)
        val account = PhoneAccount.builder(handle(context), label)
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .setShortDescription(label)
            .setSupportedUriSchemes(listOf(PRIVATE_SCHEME))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_call))
            .build()
        try {
            telecom(context).registerPhoneAccount(account)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't register the calling account", e)
        }
    }

    /**
     * True once the user has switched the account on in Settings → Calling accounts.
     *
     * Older Android versions answer directly. Newer ones (seen on Android 16) refuse unless the app holds
     * READ_PHONE_NUMBERS, which isn't worth asking users for, so the app then uses what it learned the
     * last time it placed a call (see [CallTrigger.ringNow]).
     */
    fun isEnabled(context: Context): Boolean {
        val store = Store.get(context)
        return try {
            val on = telecom(context).getPhoneAccount(handle(context))?.isEnabled == true
            if (on != store.settings.value.accountOn) store.updateSettings { it.copy(accountOn = on) }
            on
        } catch (e: SecurityException) {
            store.settings.value.accountOn
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the calling account", e)
            store.settings.value.accountOn
        }
    }

    internal fun telecom(context: Context): TelecomManager =
        context.getSystemService(TelecomManager::class.java)
}
