package dev.slipaway

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class VoiceMode { SILENT, SPEAK, FILE }

/** Everything the user sets up, kept in one small SharedPreferences file. */
class Prefs(context: Context) {
    val raw: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Number shown on the call. If it's a saved contact, the dialer shows their name and photo. */
    var callerNumber: String
        get() = raw.getString(KEY_NUMBER, "") ?: ""
        set(v) = raw.edit { putString(KEY_NUMBER, v) }

    /** Name to show when the number isn't a saved contact (some dialers ignore it). */
    var callerName: String
        get() = raw.getString(KEY_NAME, "") ?: ""
        set(v) = raw.edit { putString(KEY_NAME, v) }

    var delaySeconds: Int
        get() = raw.getInt(KEY_DELAY, 30)
        set(v) = raw.edit { putInt(KEY_DELAY, v) }

    var voiceMode: VoiceMode
        get() = runCatching { VoiceMode.valueOf(raw.getString(KEY_VOICE, null)!!) }
            .getOrDefault(VoiceMode.SPEAK)
        set(v) = raw.edit { putString(KEY_VOICE, v.name) }

    var script: String
        get() = raw.getString(KEY_SCRIPT, null) ?: DEFAULT_SCRIPT
        set(v) = raw.edit { putString(KEY_SCRIPT, v) }

    var audioUri: String?
        get() = raw.getString(KEY_AUDIO_URI, null)
        set(v) = raw.edit { putString(KEY_AUDIO_URI, v) }

    var audioName: String?
        get() = raw.getString(KEY_AUDIO_NAME, null)
        set(v) = raw.edit { putString(KEY_AUDIO_NAME, v) }

    /**
     * Whether the calling account is switched on, as last seen. Android 16 won't let an app read its
     * own account without the phone-numbers permission, so the answer is learned from placing a call.
     */
    var accountOn: Boolean
        get() = raw.getBoolean(KEY_ACCOUNT_ON, false)
        set(v) = raw.edit { putBoolean(KEY_ACCOUNT_ON, v) }

    /** Name of the calling account, which some dialers show as "via …". Blank means automatic. */
    var accountLabel: String
        get() = raw.getString(KEY_ACCOUNT_LABEL, "") ?: ""
        set(v) = raw.edit { putString(KEY_ACCOUNT_LABEL, v) }

    /** Wall-clock time a delayed call is due, or 0 when none is waiting. */
    var scheduledAt: Long
        get() = raw.getLong(KEY_SCHEDULED_AT, 0L)
        set(v) = raw.edit { putLong(KEY_SCHEDULED_AT, v) }

    companion object {
        private const val FILE_NAME = "slipaway"
        private const val KEY_NUMBER = "caller_number"
        private const val KEY_NAME = "caller_name"
        private const val KEY_DELAY = "delay_seconds"
        private const val KEY_VOICE = "voice_mode"
        private const val KEY_SCRIPT = "script"
        private const val KEY_AUDIO_URI = "audio_uri"
        private const val KEY_AUDIO_NAME = "audio_name"
        const val KEY_SCHEDULED_AT = "scheduled_at"
        const val KEY_ACCOUNT_ON = "account_on"
        private const val KEY_ACCOUNT_LABEL = "account_label"

        val DELAY_CHOICES = listOf(0, 10, 30, 60, 180, 300, 600)

        const val DEFAULT_SCRIPT = """Hey, it's me. Sorry to call out of nowhere.
Are you busy right now? I really need your help with something.
It's kind of urgent. Can you come over?
Okay. Thank you so much. See you soon."""

        fun formatDelay(seconds: Int): String = when {
            seconds <= 0 -> "Now"
            seconds < 60 -> "$seconds s"
            else -> "${seconds / 60} min"
        }
    }
}
