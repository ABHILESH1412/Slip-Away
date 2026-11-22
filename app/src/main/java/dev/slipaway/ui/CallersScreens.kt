package dev.slipaway.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slipaway.call.VoicePlayer
import dev.slipaway.data.Caller
import dev.slipaway.data.Store
import dev.slipaway.data.VoiceMode

@Composable
fun CallersScreen(store: Store, nav: Navigator) {
    val callers by store.callers.collectAsStateWithLifecycle()
    val selectedId by store.selectedId.collectAsStateWithLifecycle()

    AppScreen(
        title = "Callers",
        onBack = nav::back,
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = {
                    val fresh = Caller()
                    store.saveCaller(fresh)
                    nav.go(Screen.EditCaller(fresh.id))
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add caller") },
            )
        },
    ) {
        Text(
            "Tap a caller to use them. A number saved in your contacts shows that contact's real name and photo " +
                "on the call.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            callers.forEachIndexed { i, caller ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val selected = caller.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) {
                            store.select(caller.id)
                            nav.back()
                        }
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(caller, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(caller.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            caller.number.ifBlank { "No number" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "In use",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { nav.go(Screen.EditCaller(caller.id)) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${caller.title}")
                    }
                }
            }
        }
        Spacer(Modifier.height(88.dp)) // room above the add button
    }
}

/** Edits save as you type. A new caller left completely empty is removed again on the way out. */
@Composable
fun EditCallerScreen(store: Store, nav: Navigator, id: String) {
    val context = LocalContext.current
    val original = remember(id) { store.caller(id) }
    if (original == null) {
        LaunchedEffect(id) { nav.back() }
        return
    }
    val settings by store.settings.collectAsStateWithLifecycle()
    val callerCount = store.callers.collectAsStateWithLifecycle().value.size
    var caller by remember(id) { mutableStateOf(original) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val preview = rememberVoicePreview()

    fun update(change: (Caller) -> Caller) {
        caller = change(caller)
        store.saveCaller(caller)
    }

    fun leave() {
        preview.stop()
        if (caller.name.isBlank() && caller.number.isBlank() && callerCount > 1) store.deleteCaller(caller.id)
        nav.back()
    }
    BackHandler(onBack = ::leave)

    val pickContact = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        if (uri != null) {
            readPickedPhone(context, uri)?.let { (number, name) ->
                update { it.copy(number = number, name = name.ifBlank { it.name }) }
            }
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = displayNameOf(context, uri) ?: "Recording"
            update { it.copy(voiceMode = VoiceMode.FILE, audioUri = uri.toString(), audioName = name) }
        }
    }
    fun chooseAudio() = try {
        pickAudio.launch(arrayOf("audio/*"))
    } catch (e: android.content.ActivityNotFoundException) {
        context.toast("No app on this phone can pick audio files.")
    }

    AppScreen(
        title = if (original.name.isBlank() && original.number.isBlank()) "New caller" else "Edit caller",
        onBack = ::leave,
        actions = {
            if (callerCount > 1) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete caller")
                }
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = caller.name,
            onValueChange = { v -> update { it.copy(name = v) } },
            label = { Text("Name") },
            placeholder = { Text("Mom") },
            supportingText = { Text("Shown on the call when the number isn't a saved contact.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = caller.number,
            onValueChange = { v -> update { it.copy(number = v) } },
            label = { Text("Phone number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            try {
                pickContact.launch(Unit)
            } catch (e: android.content.ActivityNotFoundException) {
                context.toast("No contacts app found.")
            }
        }) {
            Icon(Icons.Default.Person, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose from contacts")
        }

        SectionHeader("After you answer")
        Text(
            "Plays quietly through the earpiece, so it sounds like a real conversation to anyone nearby.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            VoiceOption("A voice reads lines", caller.voiceMode == VoiceMode.SPEAK) {
                update { it.copy(voiceMode = VoiceMode.SPEAK) }
            }
            VoiceOption("Play a recording", caller.voiceMode == VoiceMode.FILE) {
                if (caller.audioUri == null) chooseAudio() else update { it.copy(voiceMode = VoiceMode.FILE) }
            }
            VoiceOption("Silence", caller.voiceMode == VoiceMode.SILENT) {
                update { it.copy(voiceMode = VoiceMode.SILENT) }
            }
        }
        Spacer(Modifier.height(16.dp))

        when (caller.voiceMode) {
            VoiceMode.SPEAK -> {
                OutlinedTextField(
                    value = caller.script,
                    onValueChange = { v -> update { it.copy(script = v) } },
                    label = { Text("What ${caller.name.ifBlank { "the caller" }} says") },
                    supportingText = { Text("One line at a time, with a pause after each for your reply.") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        preview.speak(
                            VoicePlayer.scriptLines(caller.script),
                            settings.speechRate,
                            settings.speechPitch,
                            (settings.pauseSeconds * 1000).toLong(),
                        )
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Preview")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { update { it.copy(script = Caller.DEFAULT_SCRIPT) } }) {
                        Text("Use the example")
                    }
                }
                Text(
                    "Speed, pitch and the pause between lines are in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            VoiceMode.FILE -> {
                Panel {
                    Text(caller.audioName ?: "No file chosen", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = ::chooseAudio) { Text("Choose file") }
                        Spacer(Modifier.width(8.dp))
                        caller.audioUri?.let { uri ->
                            TextButton(onClick = { preview.play(uri.toUri()) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Preview")
                            }
                        }
                    }
                }
                Text(
                    "Tip: ask a friend to record “Hey, can you come now? It's urgent”, with gaps for your replies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            VoiceMode.SILENT -> Text(
                "Nothing plays. You do the talking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${caller.title}?") },
            text = { Text("Their number and lines are removed from Slip Away. Your contacts aren't touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    preview.stop()
                    store.deleteCaller(caller.id)
                    nav.back()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
