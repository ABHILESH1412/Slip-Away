package dev.slipaway.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slipaway.data.Store

/** The app's screens. Kept as plain strings so the back stack survives rotation. */
sealed interface Screen {
    data object Home : Screen
    data object Callers : Screen
    data class EditCaller(val id: String) : Screen
    data object Settings : Screen
    data object Licenses : Screen

    fun key(): String = when (this) {
        Home -> "home"
        Callers -> "callers"
        is EditCaller -> "edit:$id"
        Settings -> "settings"
        Licenses -> "licenses"
    }

    companion object {
        fun of(key: String): Screen = when {
            key == "callers" -> Callers
            key.startsWith("edit:") -> EditCaller(key.removePrefix("edit:"))
            key == "settings" -> Settings
            key == "licenses" -> Licenses
            else -> Home
        }
    }
}

class Navigator(private val stack: MutableList<Screen>) {
    var forward by mutableStateOf(true)
        private set
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(screen: Screen) {
        forward = true
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) {
            forward = false
            stack.removeAt(stack.lastIndex)
        }
    }
}

@Composable
fun SlipAwayApp(store: Store) {
    val settings by store.settings.collectAsStateWithLifecycle()

    if (!settings.onboardingDone) {
        SetupScreen(store)
        return
    }

    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<Screen>, String>(
            save = { list -> list.map { it.key() } },
            restore = { keys -> keys.map(Screen::of).toMutableStateList() },
        ),
    ) { listOf<Screen>(Screen.Home).toMutableStateList() }
    val nav = remember(stack) { Navigator(stack) }

    BackHandler(enabled = nav.canGoBack) { nav.back() }

    AnimatedContent(
        targetState = nav.current,
        contentKey = { it.key() },
        transitionSpec = {
            val dir = if (nav.forward) 1 else -1
            (slideInHorizontally(tween(260)) { w -> dir * w / 4 } + fadeIn(tween(260)))
                .togetherWith(slideOutHorizontally(tween(260)) { w -> -dir * w / 4 } + fadeOut(tween(180)))
        },
        label = "screens",
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(store, nav)
            Screen.Callers -> CallersScreen(store, nav)
            is Screen.EditCaller -> EditCallerScreen(store, nav, screen.id)
            Screen.Settings -> SettingsScreen(store, nav)
            Screen.Licenses -> LicensesScreen(nav)
        }
    }
}
