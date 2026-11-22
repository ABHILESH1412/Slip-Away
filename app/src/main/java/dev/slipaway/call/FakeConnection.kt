package dev.slipaway.call

import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.DisconnectCause

/** One fake call, from ringing to hang-up. The system drives it through these callbacks. */
class FakeConnection(
    private val voice: VoicePlayer,
    private val ringTimeoutMs: Long,
) : Connection() {

    private val handler = Handler(Looper.getMainLooper())

    /** A real call stops ringing after a while and becomes a missed call. */
    private val giveUp = Runnable {
        if (state == STATE_RINGING) end(DisconnectCause.MISSED)
    }

    init {
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_HOLD or CAPABILITY_SUPPORT_HOLD
        // Internet-call audio mode: lets the app's own sound reach the earpiece on every phone.
        audioModeIsVoip = true
    }

    fun ring() {
        setRinging()
        handler.postDelayed(giveUp, ringTimeoutMs)
    }

    override fun onAnswer() {
        handler.removeCallbacks(giveUp)
        setActive()
        voice.start()
    }

    override fun onAnswer(videoState: Int) = onAnswer()

    override fun onReject() = end(DisconnectCause.REJECTED)

    override fun onReject(replyMessage: String?) = end(DisconnectCause.REJECTED)

    override fun onDisconnect() = end(DisconnectCause.LOCAL)

    override fun onAbort() = end(DisconnectCause.CANCELED)

    override fun onHold() {
        setOnHold()
        voice.pause()
    }

    override fun onUnhold() {
        setActive()
        voice.resume()
    }

    private fun end(cause: Int) {
        handler.removeCallbacks(giveUp)
        voice.release()
        setDisconnected(DisconnectCause(cause))
        destroy()
        if (current === this) current = null
    }

    companion object {
        /** The call in progress, if any. Stops a second fake call ringing over the first. */
        @Volatile
        var current: FakeConnection? = null
    }
}
