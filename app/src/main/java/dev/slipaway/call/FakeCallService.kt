package dev.slipaway.call

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dev.slipaway.Prefs

/**
 * The system binds to this when Slip Away reports an incoming call. It hands back a [FakeConnection];
 * the phone's own dialer then shows its normal ringing screen, ringtone and vibration.
 */
class FakeCallService : ConnectionService() {

    override fun onCreateIncomingConnection(
        account: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val prefs = Prefs(this)
        val connection = FakeConnection(VoicePlayer(applicationContext, prefs))

        val address = request.address
            ?: prefs.callerNumber.trim().takeIf { it.isNotEmpty() }
                ?.let { Uri.fromParts(PhoneAccount.SCHEME_TEL, it, null) }
        if (address != null) {
            connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
        } else {
            connection.setAddress(null, TelecomManager.PRESENTATION_UNKNOWN)
        }
        prefs.callerName.trim().takeIf { it.isNotEmpty() }?.let {
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
