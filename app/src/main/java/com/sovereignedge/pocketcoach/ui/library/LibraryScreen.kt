package com.sovereignedge.pocketcoach.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Exercise
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIcon
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.ExerciseEditorDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(app: CoachApp, modifier: Modifier, nav: Nav) {
    val exercises by app.repo.exercises().collectAsStateWithLifecycle(emptyList())
    val records by app.repo.personalRecords().collectAsStateWithLifecycle(emptyList())
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val maxById = records.associate { it.exercise.id to (it.max?.value ?: it.bestE1rm) }

    val filtered = exercises.filter { e ->
        (category == null || e.category == category) && (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true) || e.equipment.contains(query.trim(), ignoreCase = true))
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Library") }, actions = { AppIconButton(R.drawable.ic_settings, "Settings") { nav.push(Route.Settings) } }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { AppIcon(R.drawable.ic_add, "New exercise") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("Search ${exercises.size} exercises") }, singleLine = true,
                    leadingIcon = { AppIcon(R.drawable.ic_search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                LazyRow(Modifier.padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(listOf<String?>(null) + Exercise.CATEGORIES) { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c ?: "All") }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            items(filtered, key = { it.id }) { e ->
                val max = maxById[e.id]
                ListItem(
                    headlineContent = { Text(e.name) },
                    supportingContent = { Text("${e.category} · ${e.equipment}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { if (max != null) Text(Strength.fmtLoadUnit(max, settings.unit), style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.clickable { nav.push(Route.Exercise(e.id)) },
                )
            }
        }
    }
    if (creating) {
        ExerciseEditorDialog(initial = null, onDismiss = { creating = false }) { name, cat, eq, kind, notes ->
            scope.launch { nav.push(Route.Exercise(app.repo.addExercise(name, cat, eq, kind, notes))) }
        }
    }
}
