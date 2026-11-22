package dev.slipaway.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dev.slipaway.Prefs
import dev.slipaway.VoiceMode
import java.io.File

/**
 * What the "caller" says once you answer, played quietly through the earpiece like a real call:
 * either the user's lines read out by the phone's text-to-speech, or an audio file they picked.
 *
 * Text-to-speech is turned into audio files first and played from this app, not spoken by the speech
 * engine directly: Android 15+ mutes audio from background apps, and the engine counts as one.
 */
class VoicePlayer(private val context: Context, prefs: Prefs) {

    private val mode = prefs.voiceMode
    private val lines = prefs.script.lines().map { it.trim() }.filter { it.isNotEmpty() }
    private val audioUri = prefs.audioUri?.let(Uri::parse)

    private val handler = Handler(Looper.getMainLooper())
    private val callAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val voiceDir = File(context.cacheDir, "voice")
    private val lineReady = BooleanArray(lines.size)
    private var tts: TextToSpeech? = null

    private var player: MediaPlayer? = null
    private var answered = false
    private var onHold = false
    private var released = false

    /** Next line to play; [waitingForLine] is true while it's still being turned into audio. */
    private var nextLine = 0
    private var waitingForLine = false

    init {
        // Start preparing while the phone is still ringing, so the voice is ready on answer.
        if (mode == VoiceMode.SPEAK && lines.isNotEmpty()) prepareSpeech()
    }

    /** Called when the call is answered. A short pause first, like someone saying "hello?". */
    fun start() {
        answered = true
        handler.postDelayed({ begin() }, ANSWER_PAUSE_MS)
    }

    fun pause() {
        onHold = true
        handler.removeCallbacksAndMessages(null)
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        onHold = false
        val current = player
        if (current != null) {
            runCatching { current.start() }
        } else if (mode == VoiceMode.SPEAK) {
            playNextLine()
        }
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        tts?.run { stop(); shutdown() }
        tts = null
        player?.release()
        player = null
        voiceDir.listFiles()?.forEach { it.delete() }
    }

    private fun begin() {
        if (released || onHold) return
        when (mode) {
            VoiceMode.SILENT -> Unit
            VoiceMode.SPEAK -> playNextLine()
            VoiceMode.FILE -> audioUri?.let { play(it, onFinished = {}) }
        }
    }

    // ---- spoken lines ----

    private fun prepareSpeech() {
        voiceDir.mkdirs()
        tts = TextToSpeech(context) { status ->
            if (released) return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Text-to-speech isn't available")
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) {
                    handler.post { onLineReady(utteranceId.toIntOrNull() ?: return@post) }
                }
                @Deprecated("Required override")
                override fun onError(utteranceId: String) {
                    Log.w(TAG, "Couldn't turn line $utteranceId into speech")
                }
            })
            lines.forEachIndexed { i, line ->
                engine.synthesizeToFile(line, null, lineFile(i), i.toString())
            }
        }
    }

    private fun onLineReady(index: Int) {
        if (released || index !in lineReady.indices) return
        lineReady[index] = true
        if (waitingForLine && index == nextLine) {
            waitingForLine = false
            playNextLine()
        }
    }

    private fun playNextLine() {
        if (released || onHold || !answered || nextLine >= lines.size) return
        if (!lineReady[nextLine]) {
            waitingForLine = true
            return
        }
        val index = nextLine
        play(Uri.fromFile(lineFile(index))) {
            nextLine = index + 1
            // Gap after each line, as if you were answering.
            handler.postDelayed({ playNextLine() }, GAP_BETWEEN_LINES_MS)
        }
    }

    private fun lineFile(index: Int) = File(voiceDir, "line$index.wav")

    // ---- playback ----

    private fun play(uri: Uri, onFinished: () -> Unit) {
        player?.release()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(callAudio)
                setDataSource(context, uri)
                setOnPreparedListener { if (!released && !onHold) it.start() }
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    if (!released) onFinished()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "Couldn't play audio ($what/$extra)")
                    mp.release()
                    if (player === mp) player = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open audio", e)
            null
        }
    }

    private companion object {
        const val TAG = "SlipAway"
        const val ANSWER_PAUSE_MS = 1_200L
        const val GAP_BETWEEN_LINES_MS = 3_500L
    }
}
