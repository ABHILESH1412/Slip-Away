package dev.slipaway.ui

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.slipaway.Prefs
import dev.slipaway.R
import dev.slipaway.VoiceMode
import dev.slipaway.call.CallAccount
import dev.slipaway.call.CallTrigger
import dev.slipaway.tile.RingTileService
import kotlinx.coroutines.delay

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var accountOn by remember { mutableStateOf(CallAccount.isEnabled(context)) }
    var exactOk by remember { mutableStateOf(CallTrigger.canUseExactAlarms(context)) }
    var scheduledAt by remember { mutableLongStateOf(prefs.scheduledAt) }

    // Coming back from Settings: re-check what the user switched on.
    LifecycleResumeEffect(Unit) {
        accountOn = CallAccount.isEnabled(context)
        exactOk = CallTrigger.canUseExactAlarms(context)
        scheduledAt = prefs.scheduledAt
        onPauseOrDispose { }
    }

    // The tile, a shortcut or the alarm can change the countdown while this screen is open.
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Prefs.KEY_SCHEDULED_AT -> scheduledAt = prefs.scheduledAt
                Prefs.KEY_ACCOUNT_ON -> accountOn = prefs.accountOn
            }
        }
        prefs.raw.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.raw.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    "Slip Away",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "A call that looks exactly like your phone's own, whenever you need a way out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!accountOn) SetupCard(context)
            if (accountOn && !exactOk) ExactAlarmCard(context)

            RingCard(
                prefs = prefs,
                enabled = accountOn,
                scheduledAt = scheduledAt,
            )
            CallerCard(prefs)
            VoiceCard(prefs)
            TipsCard(context)
        }
    }
}

@Composable
private fun Section(
    title: String,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SetupCard(context: Context) {
    val label = remember { CallAccount.label(context) }
    Section("One-time setup", highlight = true) {
        Text(
            "Slip Away rings through your phone's own call screen. For that, Android needs you to " +
                "switch on its calling account once.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "1.  Open calling accounts, then “All calling accounts”, and switch on “$label” " +
                "(the one with a plain phone icon, not your SIM card).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { openCallingAccounts(context) }) { Text("Open calling accounts") }
        Text(
            "2.  Come back and try a test call. Your phone will ring right away.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            when (CallTrigger.ringNow(context)) {
                CallTrigger.Result.NOT_ENABLED ->
                    context.toast("“$label” is still switched off in calling accounts.")
                CallTrigger.Result.FAILED -> context.toast("Your phone wouldn't start the call.")
                else -> Unit
            }
        }) { Text("Try a test call") }
    }
}

@Composable
private fun ExactAlarmCard(context: Context) {
    Section("Ring on time") {
        Text(
            "Allow exact timing so a delayed call arrives on the second, even with the phone asleep " +
                "in your pocket. Without it, it can come a little late.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivitySafely(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }
        }) { Text("Allow exact timing") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RingCard(prefs: Prefs, enabled: Boolean, scheduledAt: Long) {
    val context = LocalContext.current
    var delaySeconds by remember { mutableIntStateOf(prefs.delaySeconds) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(scheduledAt) {
        while (scheduledAt > 0L) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    Section("Ring me") {
        if (scheduledAt > 0L) {
            val left = ((scheduledAt - now + 999) / 1000).coerceAtLeast(0)
            Text(
                if (left > 0) "%d:%02d".format(left / 60, left % 60) else "Any moment…",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "You can lock your phone. It will ring like a normal call.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { CallTrigger.cancel(context) }) { Text("Cancel") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Prefs.DELAY_CHOICES.forEach { seconds ->
                    FilterChip(
                        selected = seconds == delaySeconds,
                        onClick = {
                            delaySeconds = seconds
                            prefs.delaySeconds = seconds
                        },
                        label = { Text(Prefs.formatDelay(seconds)) },
                    )
                }
            }
            Button(
                onClick = {
                    val result = CallTrigger.schedule(context, delaySeconds)
                    CallTrigger.describe(result)?.let { context.toast(it) }
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_call), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (delaySeconds <= 0) "Ring now" else "Ring in ${Prefs.formatDelay(delaySeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!enabled) {
                Text(
                    "Finish the one-time setup above first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CallerCard(prefs: Prefs) {
    val context = LocalContext.current
    var number by remember { mutableStateOf(prefs.callerNumber) }
    var name by remember { mutableStateOf(prefs.callerName) }
    var accountLabel by remember { mutableStateOf(prefs.accountLabel) }

    val pickContact = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        readPickedPhone(context, uri)?.let { (pickedNumber, pickedName) ->
            number = pickedNumber
            prefs.callerNumber = pickedNumber
            // A saved contact shows their own name; a stale custom name would only confuse.
            name = ""
            prefs.callerName = ""
            context.toast("Calls will come from $pickedName")
        }
    }

    Section("Who's calling") {
        Text(
            "Use a number saved in your contacts and your phone shows their name and photo itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = number,
            onValueChange = {
                number = it
                prefs.callerNumber = it
            },
            label = { Text("Phone number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { context.launchSafely { pickContact.launch(Unit) } }) {
            Text("Choose from contacts")
        }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                prefs.callerName = it
            },
            label = { Text("Name, if the number isn't a contact") },
            supportingText = { Text("Some phones ignore this and show the number only.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = accountLabel,
            onValueChange = {
                accountLabel = it
                prefs.accountLabel = it
                CallAccount.register(context)
            },
            label = { Text("Calling account name") },
            placeholder = { Text(CallAccount.defaultLabel(context)) },
            supportingText = {
                Text(
                    "Your phone may show “Incoming call via …” with this name. Keep it the same as " +
                        "your SIM's name so it matches real calls. Empty uses the SIM's name " +
                        "(“${CallAccount.defaultLabel(context)}”).",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VoiceCard(prefs: Prefs) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(prefs.voiceMode) }
    var script by remember { mutableStateOf(prefs.script) }
    var audioName by remember { mutableStateOf(prefs.audioName) }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val displayName = displayNameOf(context, uri) ?: "Audio file"
        prefs.audioUri = uri.toString()
        prefs.audioName = displayName
        audioName = displayName
        mode = VoiceMode.FILE
        prefs.voiceMode = VoiceMode.FILE
    }

    fun choose(m: VoiceMode) {
        mode = m
        prefs.voiceMode = m
    }

    Section("After you answer") {
        Text(
            "Something plays quietly through the earpiece, so it sounds like a real conversation to " +
                "anyone nearby.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceRow("A voice reads my lines", mode == VoiceMode.SPEAK) { choose(VoiceMode.SPEAK) }
        if (mode == VoiceMode.SPEAK) {
            OutlinedTextField(
                value = script,
                onValueChange = {
                    script = it
                    prefs.script = it
                },
                label = { Text("What the caller says") },
                supportingText = { Text("One line at a time, with a pause after each for your reply.") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                script = Prefs.DEFAULT_SCRIPT
                prefs.script = Prefs.DEFAULT_SCRIPT
            }) { Text("Reset to the example") }
        }
        ChoiceRow("Play a recording", mode == VoiceMode.FILE) {
            if (prefs.audioUri == null) {
                context.launchSafely { pickAudio.launch(arrayOf("audio/*")) }
            } else {
                choose(VoiceMode.FILE)
            }
        }
        if (mode == VoiceMode.FILE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    audioName ?: "No file chosen",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { context.launchSafely { pickAudio.launch(arrayOf("audio/*")) } }) {
                    Text("Choose file")
                }
            }
            Text(
                "Tip: record a friend saying they need you, with gaps for your replies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChoiceRow("Silence", mode == VoiceMode.SILENT) { choose(VoiceMode.SILENT) }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TipsCard(context: Context) {
    Section("Start it without anyone noticing") {
        Tip("Before you walk in, set a delay and lock your phone. It rings on its own.")
        Tip(
            "Long-press the Slip Away icon for “Call in 10 s / 1 min / 3 min”. You can drag those " +
                "onto your home screen as their own icons.",
        )
        Tip("Add the Slip Away Quick Settings tile: swipe down, tap it, and it rings after your chosen delay.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = { requestTile(context) }) { Text("Add the Quick Settings tile") }
        }
        Tip("Silent mode and Do Not Disturb mute it, just like a real call. The call also shows in your call history.")
    }
}

@Composable
private fun Tip(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium)
}

// ---- helpers ----

private fun openCallingAccounts(context: Context) {
    val opened = context.startActivitySafely(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
    if (!opened) {
        context.startActivitySafely(Intent(Settings.ACTION_SETTINGS))
        context.toast("Search Settings for “Calling accounts” and switch on “${CallAccount.label(context)}”.")
    }
}

private fun requestTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val statusBar = context.getSystemService(StatusBarManager::class.java)
    statusBar.requestAddTileService(
        ComponentName(context, RingTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_call),
        context.mainExecutor,
    ) { result ->
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
            context.toast("The tile is already in Quick Settings.")
        }
    }
}

private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

private fun Context.launchSafely(block: () -> Unit) {
    try {
        block()
    } catch (e: ActivityNotFoundException) {
        toast("No app on this phone can do that.")
    }
}

private fun Context.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()

/** Opens the contacts app on phone numbers. The pick grants read access to that one row only. */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

private fun readPickedPhone(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) (c.getString(0) ?: return@use null) to (c.getString(1) ?: "that contact") else null
    }
}.getOrNull()

private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()
