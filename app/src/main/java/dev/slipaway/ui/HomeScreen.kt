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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/** Side margin of the page content (set by [AppScreen]). */
private val PAGE_MARGIN = 16.dp

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
    val label = remember(settings.accountLabel) { CallAccount.label(context) }

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
        scroll = false,
        actions = {
            IconButton(onClick = { nav.go(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    ) {
        AnimatedContent(
            targetState = scheduled,
            contentKey = { it != null },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
            label = "home",
        ) { waiting ->
            val status: @Composable () -> Unit = {
                StatusChip(
                    ready = settings.accountOn,
                    label = label,
                    onFix = { store.updateSettings { it.copy(onboardingDone = false) } },
                )
            }
            if (waiting != null) {
                Countdown(
                    status = status,
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
                    status = status,
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

/**
 * Fills the whole screen: [top] at the top, [middle] centered in the space left, [bottom] at the bottom.
 * On a screen too short for all of it, it scrolls instead of cutting anything off.
 */
@Composable
private fun FullHeight(
    top: @Composable () -> Unit,
    middle: @Composable (available: Dp) -> Unit,
    bottom: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = screen),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth()) { top() }
            Box(Modifier.padding(vertical = 24.dp), contentAlignment = Alignment.Center) { middle(screen) }
            Column(Modifier.fillMaxWidth()) { bottom() }
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

@Composable
private fun Ready(
    status: @Composable () -> Unit,
    caller: Caller,
    delaySeconds: Int,
    onDelay: (Int) -> Unit,
    onRing: () -> Unit,
    onCaller: () -> Unit,
    onVoice: () -> Unit,
) {
    FullHeight(
        top = {
            status()
            Spacer(Modifier.height(16.dp))
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
            )
            Spacer(Modifier.height(8.dp))
            DelayRow(delaySeconds, onDelay)
        },
        middle = { available ->
            RingButton(
                label = if (delaySeconds <= 0) "Ring now" else "Ring in ${Store.formatDelay(delaySeconds)}",
                size = (available * 0.3f).coerceIn(168.dp, 260.dp),
                onClick = onRing,
            )
        },
        bottom = {
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
        },
    )
}

/** Preset delays and "Custom", in one line that scrolls sideways. */
@Composable
private fun DelayRow(delaySeconds: Int, onDelay: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val isCustom = delaySeconds !in Store.DELAY_CHOICES
    val selectedIndex = if (isCustom) Store.DELAY_CHOICES.size else Store.DELAY_CHOICES.indexOf(delaySeconds)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))

    // Scrolls edge to edge: the row reaches past the page margin, and the first and last chips line up with it.
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = PAGE_MARGIN),
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val extra = PAGE_MARGIN.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.maxWidth + extra * 2,
                        maxWidth = constraints.maxWidth + extra * 2,
                    ),
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
            },
    ) {
        items(Store.DELAY_CHOICES, key = { it }) { seconds ->
            FilterChip(
                selected = seconds == delaySeconds,
                onClick = { onDelay(seconds) },
                label = { Text(Store.formatDelay(seconds)) },
            )
        }
        item(key = "custom") {
            FilterChip(
                selected = isCustom,
                onClick = { editing = true },
                label = { Text(if (isCustom) Store.formatDelay(delaySeconds) else "Custom") },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
                },
            )
        }
    }

    if (editing) {
        CustomDelayDialog(
            initialSeconds = if (isCustom) delaySeconds else 0,
            onPick = onDelay,
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun CustomDelayDialog(initialSeconds: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(if (initialSeconds > 0) (initialSeconds / 60).toString() else "") }
    var seconds by rememberSaveable { mutableStateOf(if (initialSeconds > 0) (initialSeconds % 60).toString() else "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        val m = minutes.trim().ifEmpty { "0" }.toIntOrNull()
        val s = seconds.trim().ifEmpty { "0" }.toIntOrNull()
        val total = if (m == null || s == null) null else m * 60 + s
        error = when {
            total == null || m!! < 0 || s!! < 0 -> "Enter whole numbers."
            s >= 60 -> "Seconds go up to 59."
            total < 1 -> "Enter at least 1 second."
            total > Store.MAX_CUSTOM_DELAY -> "Up to ${Store.MAX_CUSTOM_DELAY / 60} minutes."
            else -> null
        }
        if (error == null && total != null) {
            onPick(total)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ring me in") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(2); error = null },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(2); error = null },
                        label = { Text("Seconds") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "Your last choice is also used by the widget and the Quick Settings tile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The one big action: a round button in the brand color. */
@Composable
private fun RingButton(label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(size * 0.05f),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = label },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(Icons.Default.Call, contentDescription = null, Modifier.size(size * 0.2f))
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Countdown(
    status: @Composable () -> Unit,
    scheduled: Scheduled,
    caller: Caller,
    onCancel: () -> Unit,
    onRingNow: () -> Unit,
) {
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

    FullHeight(
        top = { status() },
        middle = { available ->
            val ring = (available * 0.36f).coerceIn(200.dp, 300.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(ring)) {
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
            }
        },
        bottom = {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Next time, start it from the home-screen widget, the Quick Settings tile, or by " +
                            "long-pressing the app icon.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
    )
}
