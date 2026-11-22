package dev.slipaway.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slipaway.R
import dev.slipaway.call.CallAccount
import dev.slipaway.call.CallTrigger
import dev.slipaway.call.VoicePlayer
import dev.slipaway.data.Store
import dev.slipaway.data.ThemeMode
import dev.slipaway.widget.RingWidget
import kotlin.math.roundToInt

private enum class Dialog { NONE, THEME, TIMEOUT, ACCOUNT_NAME }

@Composable
fun SettingsScreen(store: Store, nav: Navigator) {
    val context = LocalContext.current
    val settings by store.settings.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }
    var exactOk by remember { mutableStateOf(CallTrigger.canUseExactAlarms(context)) }
    val preview = rememberVoicePreview()
    val label = remember(settings.accountLabel) { CallAccount.label(context) }
    val repo = stringResource(R.string.repo_url)

    LifecycleResumeEffect(Unit) {
        exactOk = CallTrigger.canUseExactAlarms(context)
        CallAccount.isEnabled(context)
        onPauseOrDispose { preview.stop() }
    }

    AppScreen(title = "Settings", onBack = { preview.stop(); nav.back() }) {
        SectionHeader("Appearance")
        Panel(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                title = "Theme",
                summary = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> "Same as your phone"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                onClick = { dialog = Dialog.THEME },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Divider()
                SwitchRow(
                    title = "Wallpaper colors",
                    summary = "Use colors from your wallpaper instead of Slip Away's",
                    checked = settings.wallpaperColors,
                    onChange = { on -> store.updateSettings { it.copy(wallpaperColors = on) } },
                )
            }
        }

        SectionHeader("Call")
        Panel(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                title = "Ring for",
                summary = "${settings.ringTimeoutSeconds} seconds, then it becomes a missed call",
                onClick = { dialog = Dialog.TIMEOUT },
            )
            Divider()
            SettingRow(
                title = "Calling account name",
                summary = if (settings.accountLabel.isBlank()) "$label (your SIM's name)" else label,
                onClick = { dialog = Dialog.ACCOUNT_NAME },
            )
        }

        SectionHeader("Voice")
        Panel {
            SliderSetting(
                title = "Speech speed",
                value = settings.speechRate,
                range = 0.5f..2f,
                display = { "%.1f×".format(it) },
                onChange = { v -> store.updateSettings { it.copy(speechRate = v) } },
            )
            SliderSetting(
                title = "Pitch",
                value = settings.speechPitch,
                range = 0.5f..2f,
                display = { "%.1f×".format(it) },
                onChange = { v -> store.updateSettings { it.copy(speechPitch = v) } },
            )
            SliderSetting(
                title = "Pause between lines",
                value = settings.pauseSeconds,
                range = 1f..8f,
                display = { "%.1f s".format(it) },
                onChange = { v -> store.updateSettings { it.copy(pauseSeconds = v) } },
            )
            OutlinedButton(onClick = {
                val lines = VoicePlayer.scriptLines(store.selectedCaller.script).take(2)
                preview.speak(
                    lines.ifEmpty { listOf("Hey, it's me. Can you come over?") },
                    settings.speechRate,
                    settings.speechPitch,
                    (settings.pauseSeconds * 1000).toLong(),
                )
            }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Preview voice")
            }
        }

        SectionHeader("Shortcuts")
        Panel(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                title = "Home-screen widget",
                summary = "One tap rings after your last chosen delay. Tap again to cancel",
                onClick = {
                    if (!RingWidget.requestPin(context)) {
                        context.toast("Long-press your home screen, tap Widgets, and find Slip Away.")
                    }
                },
                trailing = { Text("Add", color = MaterialTheme.colorScheme.primary) },
            )
            Divider()
            SettingRow(
                title = "Quick Settings tile",
                summary = if (canRequestTile) {
                    "Swipe down and tap once to ring after your chosen delay"
                } else {
                    "Swipe down twice, tap edit, and drag “Slip Away” into your tiles"
                },
                onClick = if (canRequestTile) ({ requestTile(context) }) else null,
                trailing = if (canRequestTile) ({ Text("Add", color = MaterialTheme.colorScheme.primary) }) else null,
            )
            Divider()
            SettingRow(
                title = "App icon shortcuts",
                summary = "Long-press the Slip Away icon for “Call in 10 s”, “1 min” or “3 min”. Drag one to " +
                    "your home screen for a one-tap button.",
            )
        }

        SectionHeader("Setup")
        Panel(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                title = "Calling account",
                summary = if (settings.accountOn) "On, as “$label”" else
                    "Off. Slip Away can't ring until you switch it on",
                onClick = { openCallingAccounts(context, label) },
                trailing = { StatusIcon(settings.accountOn) },
            )
            Divider()
            SettingRow(
                title = "Exact timing",
                summary = if (exactOk) "Allowed. Timed calls ring on the second" else
                    "Not allowed. Timed calls may come late",
                onClick = if (exactOk) null else ({ requestExactAlarms(context) }),
                trailing = { StatusIcon(exactOk) },
            )
            Divider()
            SettingRow(
                title = "Test call",
                summary = "Ring your phone now with ${store.selectedCaller.title}",
                onClick = {
                    CallTrigger.describe(CallTrigger.ringNow(context))?.let { context.toast(it) }
                },
            )
            Divider()
            SettingRow(
                title = "Run setup again",
                onClick = { store.updateSettings { it.copy(onboardingDone = false) } },
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )
        }

        SectionHeader("About")
        Panel(contentPadding = PaddingValues(0.dp)) {
            SettingRow(title = "Version", summary = appVersion(context))
            Divider()
            SettingRow(title = "Source code", summary = "Free and open source on GitHub", onClick = {
                context.openUrl(repo)
            })
            Divider()
            SettingRow(title = "Report a problem", summary = "Open an issue on GitHub", onClick = {
                context.openUrl("$repo/issues")
            })
            Divider()
            SettingRow(title = "License", summary = "GNU General Public License v3.0", onClick = {
                context.openUrl("https://www.gnu.org/licenses/gpl-3.0.html")
            })
            Divider()
            SettingRow(
                title = "Open-source licenses",
                summary = "Libraries Slip Away is built with",
                onClick = { nav.go(Screen.Licenses) },
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "No internet access, no ads, no tracking. Everything stays on your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    when (dialog) {
        Dialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = listOf(ThemeMode.SYSTEM to "Same as your phone", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
            selected = settings.themeMode,
            onPick = { m -> store.updateSettings { it.copy(themeMode = m) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.TIMEOUT -> ChoiceDialog(
            title = "Ring for",
            options = Store.TIMEOUT_CHOICES.map { it to "$it seconds" },
            selected = settings.ringTimeoutSeconds,
            onPick = { s -> store.updateSettings { it.copy(ringTimeoutSeconds = s) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ACCOUNT_NAME -> AccountNameDialog(
            current = settings.accountLabel,
            simName = CallAccount.defaultLabel(context),
            onSave = { name ->
                store.updateSettings { it.copy(accountLabel = name.trim()) }
                CallAccount.register(context)
            },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.NONE -> Unit
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun StatusIcon(ok: Boolean) {
    if (ok) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = okColor())
    } else {
        Icon(Icons.Default.Warning, contentDescription = "Needs attention", tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    // Slide freely, save when let go.
    var current by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(display(current), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current,
            onValueChange = { current = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, text) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton) {
                                onPick(value)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountNameDialog(current: String, simName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calling account name") },
        text = {
            Column {
                Text(
                    "Some phones show “Incoming call via …” with this name. Keep it the same as your SIM's " +
                        "name so fake calls match real ones.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(simName) },
                    supportingText = { Text("Leave empty to use your SIM's name ($simName).") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class Library(val name: String, val by: String, val license: String, val url: String)

private val LIBRARIES = listOf(
    Library("Kotlin", "JetBrains", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
    Library("Kotlin coroutines", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Library("AndroidX Core", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
    Library("AndroidX Activity", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/activity"),
    Library("AndroidX Lifecycle", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    Library("Jetpack Compose", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/compose"),
    Library("Material 3 for Compose", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/compose-material3"),
    Library("Material icons", "Google", "Apache License 2.0", "https://fonts.google.com/icons"),
)

@Composable
fun LicensesScreen(nav: Navigator) {
    val context = LocalContext.current
    AppScreen(title = "Open-source licenses", onBack = nav::back) {
        Text(
            "Slip Away is free software under the GNU GPL v3.0. It's built with these libraries:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )
        Panel(contentPadding = PaddingValues(0.dp)) {
            LIBRARIES.forEachIndexed { i, lib ->
                if (i > 0) Divider()
                SettingRow(
                    title = lib.name,
                    summary = "${lib.by} · ${lib.license}",
                    onClick = { context.openUrl(lib.url) },
                )
            }
        }
        TextButton(
            onClick = { context.openUrl("https://www.apache.org/licenses/LICENSE-2.0") },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Read the Apache License 2.0") }
    }
}
