package dev.slipaway.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class VoiceMode { SPEAK, FILE, SILENT }

/** Someone who can "call" you. Each caller has their own number and what they say. */
data class Caller(
    val id: String = UUID.randomUUID().toString(),
    /** Shown in the app, and on the call if the number isn't a saved contact. */
    val name: String = "",
    val number: String = "",
    val voiceMode: VoiceMode = VoiceMode.SPEAK,
    val script: String = DEFAULT_SCRIPT,
    val audioUri: String? = null,
    val audioName: String? = null,
) {
    val title: String get() = name.ifBlank { number.ifBlank { "Unknown caller" } }
    val initial: String get() = title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

    companion object {
        const val DEFAULT_SCRIPT = """Hey, it's me. Sorry to call out of nowhere.
Are you busy right now? I really need your help with something.
It's kind of urgent. Can you come over?
Okay. Thank you so much. See you soon."""
    }
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You: take colors from the wallpaper instead of the Slip Away palette. */
    val wallpaperColors: Boolean = false,
    val delaySeconds: Int = 30,
    val ringTimeoutSeconds: Int = 45,
    /** Name of the calling account, which some dialers show as "via …". Blank means the SIM's name. */
    val accountLabel: String = "",
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val pauseSeconds: Float = 3.5f,
    val onboardingDone: Boolean = false,
    /** Check GitHub for new releases and install them. The only thing that uses the internet. */
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    /**
     * Whether the calling account is switched on, as last seen. Android 16 won't let an app read its own
     * account without the phone-numbers permission, so this is learned from placing a call.
     */
    val accountOn: Boolean = false,
)

/** A call waiting to ring: when it's due and how long the wait was (for the countdown ring). */
data class Scheduled(val atMillis: Long, val totalMillis: Long, val callerId: String)

/**
 * Everything the user sets up, kept in SharedPreferences and exposed as state the screens can watch.
 * One instance per process, shared by the screens, the call service, the tile and the alarm.
 */
class Store private constructor(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _callers = MutableStateFlow(readCallers())
    val callers: StateFlow<List<Caller>> = _callers.asStateFlow()

    private val _selectedId = MutableStateFlow(
        sp.getString(K_SELECTED, null)?.takeIf { id -> _callers.value.any { it.id == id } }
            ?: _callers.value.first().id,
    )
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _scheduled = MutableStateFlow(readScheduled())
    val scheduled: StateFlow<Scheduled?> = _scheduled.asStateFlow()

    val selectedCaller: Caller
        get() = _callers.value.firstOrNull { it.id == _selectedId.value } ?: _callers.value.first()

    fun caller(id: String): Caller? = _callers.value.firstOrNull { it.id == id }

    // ---- settings ----

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        writeSettings(_settings.value)
    }

    // ---- callers ----

    fun select(id: String) {
        _selectedId.value = id
        sp.edit { putString(K_SELECTED, id) }
    }

    /** Adds the caller, or replaces the one with the same id. */
    fun saveCaller(caller: Caller) {
        _callers.update { list ->
            if (list.any { it.id == caller.id }) list.map { if (it.id == caller.id) caller else it }
            else list + caller
        }
        writeCallers(_callers.value)
    }

    fun deleteCaller(id: String) {
        if (_callers.value.size <= 1) return
        _callers.update { list -> list.filterNot { it.id == id } }
        writeCallers(_callers.value)
        if (_selectedId.value == id) select(_callers.value.first().id)
    }

    // ---- scheduled call ----

    fun setScheduled(value: Scheduled?) {
        _scheduled.value = value
        sp.edit {
            if (value == null) {
                remove(K_SCHED_AT); remove(K_SCHED_TOTAL); remove(K_SCHED_CALLER)
            } else {
                putLong(K_SCHED_AT, value.atMillis)
                putLong(K_SCHED_TOTAL, value.totalMillis)
                putString(K_SCHED_CALLER, value.callerId)
            }
        }
    }

    // ---- persistence ----

    private fun readSettings(): Settings {
        val accountOn = sp.getBoolean(K_ACCOUNT_ON, false)
        return Settings(
            themeMode = enumOr(sp.getString(K_THEME, null), ThemeMode.SYSTEM),
            wallpaperColors = sp.getBoolean(K_WALLPAPER, false),
            delaySeconds = sp.getInt(K_DELAY, 30),
            ringTimeoutSeconds = sp.getInt(K_TIMEOUT, 45),
            accountLabel = sp.getString(K_ACCOUNT_LABEL, "") ?: "",
            speechRate = sp.getFloat(K_RATE, 1.0f),
            speechPitch = sp.getFloat(K_PITCH, 1.0f),
            pauseSeconds = sp.getFloat(K_PAUSE, 3.5f),
            // Someone who already got a call through has finished setup.
            onboardingDone = sp.getBoolean(K_ONBOARDING, accountOn),
            autoUpdate = sp.getBoolean(K_AUTO_UPDATE, true),
            lastUpdateCheck = sp.getLong(K_LAST_UPDATE_CHECK, 0L),
            accountOn = accountOn,
        )
    }

    private fun writeSettings(s: Settings) = sp.edit {
        putString(K_THEME, s.themeMode.name)
        putBoolean(K_WALLPAPER, s.wallpaperColors)
        putInt(K_DELAY, s.delaySeconds)
        putInt(K_TIMEOUT, s.ringTimeoutSeconds)
        putString(K_ACCOUNT_LABEL, s.accountLabel)
        putFloat(K_RATE, s.speechRate)
        putFloat(K_PITCH, s.speechPitch)
        putFloat(K_PAUSE, s.pauseSeconds)
        putBoolean(K_ONBOARDING, s.onboardingDone)
        putBoolean(K_AUTO_UPDATE, s.autoUpdate)
        putLong(K_LAST_UPDATE_CHECK, s.lastUpdateCheck)
        putBoolean(K_ACCOUNT_ON, s.accountOn)
    }

    private fun readCallers(): List<Caller> {
        val json = sp.getString(K_CALLERS, null)
        val parsed = json?.let {
            runCatching {
                val array = JSONArray(it)
                (0 until array.length()).map { i -> array.getJSONObject(i).toCaller() }
            }.getOrNull()
        }
        if (!parsed.isNullOrEmpty()) return parsed

        // First run, or an install from before saved callers existed: carry the old single caller over.
        return listOf(
            Caller(
                name = sp.getString(OLD_NAME, "") ?: "",
                number = sp.getString(OLD_NUMBER, "") ?: "",
                voiceMode = enumOr(sp.getString(OLD_VOICE, null), VoiceMode.SPEAK),
                script = sp.getString(OLD_SCRIPT, null) ?: Caller.DEFAULT_SCRIPT,
                audioUri = sp.getString(OLD_AUDIO_URI, null),
                audioName = sp.getString(OLD_AUDIO_NAME, null),
            ),
        ).also { writeCallers(it) }
    }

    private fun writeCallers(list: List<Caller>) = sp.edit {
        putString(K_CALLERS, JSONArray(list.map { it.toJson() }).toString())
    }

    private fun readScheduled(): Scheduled? {
        val at = sp.getLong(K_SCHED_AT, 0L)
        if (at <= 0L) return null
        return Scheduled(at, sp.getLong(K_SCHED_TOTAL, 0L), sp.getString(K_SCHED_CALLER, "") ?: "")
    }

    private fun Caller.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("number", number)
        put("voice", voiceMode.name); put("script", script)
        put("audioUri", audioUri ?: JSONObject.NULL); put("audioName", audioName ?: JSONObject.NULL)
    }

    private fun JSONObject.toCaller() = Caller(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        name = optString("name"),
        number = optString("number"),
        voiceMode = enumOr(optString("voice"), VoiceMode.SPEAK),
        script = optString("script", Caller.DEFAULT_SCRIPT),
        audioUri = optString("audioUri").takeIf { !isNull("audioUri") && it.isNotEmpty() },
        audioName = optString("audioName").takeIf { !isNull("audioName") && it.isNotEmpty() },
    )

    companion object {
        private const val FILE_NAME = "slipaway"
        private const val K_THEME = "theme_mode"
        private const val K_WALLPAPER = "wallpaper_colors"
        private const val K_DELAY = "delay_seconds"
        private const val K_TIMEOUT = "ring_timeout_seconds"
        private const val K_ACCOUNT_LABEL = "account_label"
        private const val K_RATE = "speech_rate"
        private const val K_PITCH = "speech_pitch"
        private const val K_PAUSE = "pause_seconds"
        private const val K_ONBOARDING = "onboarding_done"
        private const val K_ACCOUNT_ON = "account_on"
        private const val K_AUTO_UPDATE = "auto_update"
        private const val K_LAST_UPDATE_CHECK = "last_update_check"
        private const val K_CALLERS = "callers"
        private const val K_SELECTED = "selected_caller"
        private const val K_SCHED_AT = "scheduled_at"
        private const val K_SCHED_TOTAL = "scheduled_total"
        private const val K_SCHED_CALLER = "scheduled_caller"

        // Keys from version 0.1, read once to migrate.
        private const val OLD_NUMBER = "caller_number"
        private const val OLD_NAME = "caller_name"
        private const val OLD_VOICE = "voice_mode"
        private const val OLD_SCRIPT = "script"
        private const val OLD_AUDIO_URI = "audio_uri"
        private const val OLD_AUDIO_NAME = "audio_name"

        val DELAY_CHOICES = listOf(0, 10, 30, 60, 180, 300, 600)
        val TIMEOUT_CHOICES = listOf(20, 30, 45, 60, 90)

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context).also { instance = it } }

        /** Longest delay the Custom option allows: one hour. */
        const val MAX_CUSTOM_DELAY = 60 * 60

        /** Shorter form for tight spaces like the widget: "10 s", "3 min", "2:30". */
        fun formatDelayShort(seconds: Int): String = when {
            seconds <= 0 -> "Now"
            seconds < 60 -> "$seconds s"
            seconds % 60 == 0 -> "${seconds / 60} min"
            else -> "%d:%02d".format(seconds / 60, seconds % 60)
        }

        fun formatDelay(seconds: Int): String = when {
            seconds <= 0 -> "Now"
            seconds < 60 -> "$seconds s"
            seconds % 60 == 0 -> "${seconds / 60} min"
            else -> "${seconds / 60} min ${seconds % 60} s"
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)
    }
}
