package com.sovereignedge.pocketcoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.ui.library.ExerciseDetailScreen
import com.sovereignedge.pocketcoach.ui.library.LibraryScreen
import com.sovereignedge.pocketcoach.ui.programs.ProgramEditorScreen
import com.sovereignedge.pocketcoach.ui.programs.ProgramsScreen
import com.sovereignedge.pocketcoach.ui.programs.SessionEditorScreen
import com.sovereignedge.pocketcoach.ui.progress.ProgressScreen
import com.sovereignedge.pocketcoach.ui.settings.SettingsScreen
import com.sovereignedge.pocketcoach.ui.today.TodayScreen
import com.sovereignedge.pocketcoach.ui.workout.WorkoutScreen

/** Full-screen destinations pushed over the tabs. */
sealed class Route {
    data object Settings : Route()
    data class Workout(val id: Long) : Route()
    data class Program(val id: Long) : Route()
    data class Session(val id: Long) : Route()
    data class Exercise(val id: Long) : Route()
}

class Nav(private val stack: MutableList<Route>) {
    fun push(route: Route) { stack += route }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** Pops back to the tabs, then pushes [route]. */
    fun replaceAll(route: Route) { stack.clear(); stack += route }
}

enum class Tab(val label: String, val icon: Int) {
    Today("Today", R.drawable.ic_today),
    Programs("Programs", R.drawable.ic_calendar),
    Library("Library", R.drawable.ic_dumbbell),
    Progress("Progress", R.drawable.ic_trending),
}

@Composable
fun AppRoot(app: CoachApp) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    PocketCoachTheme(themeMode = settings.themeMode) {
        var tabIndex by rememberSaveable { mutableIntStateOf(0) }
        val stack = remember { mutableStateListOf<Route>() }
        val nav = remember { Nav(stack) }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            app.imports.collect { event ->
                when (event) {
                    is CoachApp.ImportEvent.Ok -> { tabIndex = Tab.Programs.ordinal; nav.replaceAll(Route.Program(event.programId)); 
                        val note = if (event.warnings.isEmpty()) "" else " · ${event.warnings.size} row${if (event.warnings.size == 1) "" else "s"} skipped"
                        snackbar.showSnackbar("Imported “${event.name}”$note")
                    }
                    is CoachApp.ImportEvent.Err -> snackbar.showSnackbar("Could not import: ${event.message}")
                }
            }
        }

        // Edge-to-edge windows don't resize for the keyboard, so make room for it here (the nav bar inset is added by each Scaffold).
        Surface(Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
            val top = stack.lastOrNull()
            if (top != null) {
                BackHandler { nav.pop() }
                when (top) {
                    Route.Settings -> SettingsScreen(app, onBack = nav::pop)
                    is Route.Workout -> WorkoutScreen(app, top.id, nav)
                    is Route.Program -> ProgramEditorScreen(app, top.id, nav)
                    is Route.Session -> SessionEditorScreen(app, top.id, nav)
                    is Route.Exercise -> ExerciseDetailScreen(app, top.id, nav)
                }
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = tabIndex == index,
                                    onClick = { tabIndex = index },
                                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                    AnimatedContent(targetState = Tab.entries[tabIndex], label = "tab") { tab ->
                        when (tab) {
                            Tab.Today -> TodayScreen(app, modifier, nav)
                            Tab.Programs -> ProgramsScreen(app, modifier, nav)
                            Tab.Library -> LibraryScreen(app, modifier, nav)
                            Tab.Progress -> ProgressScreen(app, modifier, nav)
                        }
                    }
                }
            }
        }
    }
}
