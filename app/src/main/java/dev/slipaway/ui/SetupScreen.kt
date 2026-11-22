package dev.slipaway.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.slipaway.R
import dev.slipaway.call.CallAccount
import dev.slipaway.call.CallTrigger
import dev.slipaway.data.Store

private const val STEPS = 3

/** First run: welcome, then the calling account, exact timing and a test call. */
@Composable
fun SetupScreen(store: Store) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var exactOk by remember { mutableStateOf(CallTrigger.canUseExactAlarms(context)) }
    var testResult by remember { mutableStateOf<CallTrigger.Result?>(null) }
    val label = remember(page) { CallAccount.label(context) }

    LifecycleResumeEffect(Unit) {
        exactOk = CallTrigger.canUseExactAlarms(context)
        onPauseOrDispose { }
    }
    BackHandler(enabled = page > 0) { page-- }

    fun finish() = store.updateSettings { it.copy(onboardingDone = true) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (page > 0) StepDots(current = page, total = STEPS)

                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.weight(1f),
                    label = "setup",
                ) { p ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (p) {
                            0 -> Welcome()
                            1 -> Step(
                                icon = Icons.Default.Phone,
                                title = "Switch on the calling account",
                                body = "Slip Away rings through your phone's own call screen, the same way " +
                                    "internet-calling apps do. Android only allows that for calling accounts you " +
                                    "switch on yourself, so no app can make your phone ring without your say-so.",
                                how = "Open calling accounts, tap “All calling accounts”, and switch on " +
                                    "“$label” with the plain phone icon (not your SIM card).",
                            ) {
                                Button(onClick = { openCallingAccounts(context, label) }) {
                                    Text("Open calling accounts")
                                }
                            }
                            2 -> Step(
                                icon = if (exactOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                title = "Ring on time",
                                body = "To save battery, Android delays app timers while the screen is off. " +
                                    "Allowing exact timing makes “ring me in 3 minutes” ring in 3 minutes, even " +
                                    "with your phone asleep in a pocket.",
                                how = if (exactOk) "Already allowed on this phone." else
                                    "Turn on “Allow setting alarms and reminders”. You can skip this; timed " +
                                    "calls may just come a little late.",
                            ) {
                                if (!exactOk) {
                                    Button(onClick = { requestExactAlarms(context) }) { Text("Allow exact timing") }
                                }
                            }
                            else -> Step(
                                icon = when (testResult) {
                                    CallTrigger.Result.RINGING -> Icons.Default.CheckCircle
                                    CallTrigger.Result.NOT_ENABLED, CallTrigger.Result.FAILED -> Icons.Default.Warning
                                    else -> Icons.Default.Phone
                                },
                                title = "Try a test call",
                                body = "Your phone will ring straight away. Answer it to hear the voice, or " +
                                    "decline it.",
                                how = when (testResult) {
                                    CallTrigger.Result.RINGING -> "It works. You're all set."
                                    CallTrigger.Result.NOT_ENABLED ->
                                        "The calling account is still off. Go back a step and switch it on."
                                    CallTrigger.Result.FAILED -> "Your phone wouldn't start the call. Try again."
                                    else -> null
                                },
                            ) {
                                if (testResult != CallTrigger.Result.RINGING) {
                                    Button(onClick = { testResult = CallTrigger.ringNow(context) }) {
                                        Text("Ring my phone")
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) TextButton(onClick = { page-- }) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    when (page) {
                        0 -> Button(onClick = { page = 1 }) { Text("Get started") }
                        1 -> Button(onClick = { page = 2 }) { Text("Next") }
                        2 -> if (exactOk) {
                            Button(onClick = { page = 3 }) { Text("Next") }
                        } else {
                            OutlinedButton(onClick = { page = 3 }) { Text("Skip") }
                        }
                        else -> if (testResult == CallTrigger.Result.RINGING) {
                            Button(onClick = ::finish) { Text("Done") }
                        } else {
                            TextButton(onClick = ::finish) { Text("Finish later") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    // The launcher icon's own layers (an adaptive icon can't be drawn directly).
    Box(
        Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brand.Navy),
    ) {
        // Launchers show the middle 72 of the 108-unit canvas; crop the same way so the door fills the tile.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .requiredSize(112.dp * 108 / 72)
                .align(Alignment.Center),
        )
    }
    Spacer(Modifier.height(28.dp))
    Text("Slip Away", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(12.dp))
    Text(
        "A way out that looks like a real call. Your phone rings with its own call screen, you answer, " +
            "and you've got your reason to go.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Text(
        "Setup takes about a minute.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Step(
    icon: ImageVector,
    title: String,
    body: String,
    how: String?,
    action: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
    }
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    if (how != null) {
        Spacer(Modifier.height(16.dp))
        Text(
            how,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(24.dp))
    action()
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { i ->
            val on = i < current
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .height(6.dp)
                    .width(if (i == current - 1) 28.dp else 6.dp)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}
