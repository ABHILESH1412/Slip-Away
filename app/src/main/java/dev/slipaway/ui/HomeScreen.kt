package dev.slipaway.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slipaway.call.CallAccount
import dev.slipaway.call.CallTrigger
import dev.slipaway.data.Caller
import dev.slipaway.data.Scheduled
import dev.slipaway.data.Store
import dev.slipaway.data.VoiceMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(store: Store, nav: Navigator) {
    val context = LocalContext.current
    val settings by store.settings.collectAsStateWithLifecycle()
    val callers by store.callers.collectAsStateWithLifecycle()
    val selectedId by store.selectedId.collectAsStateWithLifecycle()
    val scheduled by store.scheduled.collectAsStateWithLifecycle()
    val caller = callers.firstOrNull { it.id == selectedId } ?: callers.first()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        CallAccount.isEnabled(context) // refreshes the stored state where Android allows reading it
        onPauseOrDispose { }
    }

    fun ring() {
        val result = CallTrigger.schedule(context, settings.delaySeconds)
        val message = CallTrigger.describe(result) ?: return
        scope.launch {
            val action = if (result == CallTrigger.Result.NOT_ENABLED) "Set up" else null
            val answer = snackbar.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
            if (answer == SnackbarResult.ActionPerformed) {
                store.updateSettings { it.copy(onboardingDone = false) }
            }
        }
    }

    AppScreen(
        title = "Slip Away",
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { nav.go(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    ) {
        StatusChip(
            ready = settings.accountOn,
            label = remember(settings.accountLabel) { CallAccount.label(context) },
            onFix = { store.updateSettings { it.copy(onboardingDone = false) } },
        )
        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = scheduled,
            contentKey = { it != null },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "home",
        ) { waiting ->
            if (waiting != null) {
                Countdown(
                    scheduled = waiting,
                    caller = store.caller(waiting.callerId) ?: caller,
                    onCancel = { CallTrigger.cancel(context) },
                    onRingNow = {
                        CallTrigger.cancel(context)
                        CallTrigger.ringNow(context, waiting.callerId)
                    },
                )
            } else {
                Ready(
                    caller = caller,
                    delaySeconds = settings.delaySeconds,
                    onDelay = { s -> store.updateSettings { it.copy(delaySeconds = s) } },
                    onRing = ::ring,
                    onCaller = { nav.go(Screen.Callers) },
                    onVoice = { nav.go(Screen.EditCaller(caller.id)) },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(ready: Boolean, label: String, onFix: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = if (ready) Modifier else Modifier.clickable(role = Role.Button, onClick = onFix),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (ready) okColor() else MaterialTheme.colorScheme.error, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (ready) "Ready · via $label" else "Finish setup",
                style = MaterialTheme.typography.labelLarge,
            )
            if (!ready) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ready(
    caller: Caller,
    delaySeconds: Int,
    onDelay: (Int) -> Unit,
    onRing: () -> Unit,
    onCaller: () -> Unit,
    onVoice: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Panel(onClick = onCaller, contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(caller)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(caller.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        caller.number.ifBlank { "No number. Tap to add one" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Ring me in",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Store.DELAY_CHOICES.forEach { seconds ->
                FilterChip(
                    selected = seconds == delaySeconds,
                    onClick = { onDelay(seconds) },
                    label = { Text(Store.formatDelay(seconds)) },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        RingButton(
            label = if (delaySeconds <= 0) "Ring now" else "Ring in ${Store.formatDelay(delaySeconds)}",
            onClick = onRing,
        )
        Spacer(Modifier.height(28.dp))

        Panel(onClick = onVoice, contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("After you answer", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (caller.voiceMode) {
                            VoiceMode.SPEAK -> "A voice reads ${caller.title}'s lines"
                            VoiceMode.FILE -> "Plays ${caller.audioName ?: "a recording"}"
                            VoiceMode.SILENT -> "Silence. You do the talking"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

/** The one big action: a round button in the brand color. */
@Composable
private fun RingButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(196.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(176.dp)
                .semantics { contentDescription = label },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Call, contentDescription = null, Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Countdown(scheduled: Scheduled, caller: Caller, onCancel: () -> Unit, onRingNow: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(scheduled) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val leftMs = (scheduled.atMillis - now).coerceAtLeast(0)
    val fraction = if (scheduled.totalMillis > 0) leftMs.toFloat() / scheduled.totalMillis else 0f
    val leftS = (leftMs + 999) / 1000
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val arc = if (LocalThemeInfo.current.brandColors) Brand.Amber else MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(240.dp)) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    arc, -90f, 360f * fraction, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (leftS > 0) "%d:%02d".format(leftS / 60, leftS % 60) else "Any moment",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "until ${caller.title} calls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "You can lock your phone. It rings like a normal call.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.width(200.dp)) { Text("Cancel") }
        TextButton(onClick = onRingNow) { Text("Ring now instead") }
        Spacer(Modifier.height(24.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Next time, start it from the Quick Settings tile or by long-pressing the app icon.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
