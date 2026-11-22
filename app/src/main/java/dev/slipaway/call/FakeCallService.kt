package dev.slipaway.call

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dev.slipaway.data.Store

/**
 * The system binds to this when Slip Away reports an incoming call. It hands back a [FakeConnection];
 * the phone's own dialer then shows its normal ringing screen, ringtone and vibration.
 */
class FakeCallService : ConnectionService() {

    override fun onCreateIncomingConnection(
        account: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val store = Store.get(this)
        val callerId = request.extras?.getString(CallTrigger.EXTRA_CALLER_ID)
        val caller = callerId?.let(store::caller) ?: store.selectedCaller
        val settings = store.settings.value

        val connection = FakeConnection(
            voice = VoicePlayer(applicationContext, caller, settings),
            ringTimeoutMs = settings.ringTimeoutSeconds * 1000L,
        )

        val address = request.address
            ?: caller.number.trim().takeIf { it.isNotEmpty() }
                ?.let { Uri.fromParts(PhoneAccount.SCHEME_TEL, it, null) }
        if (address != null) {
            connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
        } else {
            connection.setAddress(null, TelecomManager.PRESENTATION_UNKNOWN)
        }
        // A saved contact's own name wins on the call screen; this only shows for other numbers.
        caller.name.trim().takeIf { it.isNotEmpty() }?.let {
            connection.setCallerDisplayName(it, TelecomManager.PRESENTATION_ALLOWED)
        }

        FakeConnection.current = connection
        connection.ring()
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        account: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        FakeConnection.current = null
    }

    /** Slip Away never places real calls. */
    override fun onCreateOutgoingConnection(
        account: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection = Connection.createFailedConnection(
        DisconnectCause(DisconnectCause.ERROR, "Slip Away can't place calls"),
    )
}
