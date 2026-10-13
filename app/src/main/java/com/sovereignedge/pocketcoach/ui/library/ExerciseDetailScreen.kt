package com.sovereignedge.pocketcoach.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.LoadKind
import com.sovereignedge.pocketcoach.data.Max
import com.sovereignedge.pocketcoach.data.SetRecord
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.ConfirmDialog
import com.sovereignedge.pocketcoach.ui.common.ExerciseEditorDialog
import com.sovereignedge.pocketcoach.ui.common.LineChart
import com.sovereignedge.pocketcoach.ui.common.SectionHeader
import com.sovereignedge.pocketcoach.ui.common.TextPromptDialog
import com.sovereignedge.pocketcoach.ui.common.fmtDate
import com.sovereignedge.pocketcoach.ui.common.fmtDateShort
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(app: CoachApp, exerciseId: Long, nav: Nav) {
    val exercise by remember(exerciseId) { app.repo.exercise(exerciseId) }.collectAsStateWithLifecycle(null)
    val maxes by remember(exerciseId) { app.repo.maxes(exerciseId) }.collectAsStateWithLifecycle(emptyList())
    val history by remember(exerciseId) { app.repo.history(exerciseId) }.collectAsStateWithLifecycle(emptyList())
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var settingMax by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteMax by remember { mutableStateOf<Max?>(null) }
    val unit = settings.unit

    val e = exercise
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(e?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { AppIconButton(R.drawable.ic_arrow_back, "Back") { nav.pop() } },
                actions = {
                    AppIconButton(R.drawable.ic_more, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        if (e == null) return@Scaffold
        val current = maxes.firstOrNull()
        // Best estimated 1RM per training day, oldest first, for the chart.
        val e1rmByDay = history.filter { it.set.load != null && it.set.reps != null }
            .groupBy { it.date }.mapNotNull { (date, sets) -> sets.mapNotNull { Strength.estimate1rm(it.set.load!!, it.set.reps!!) }.maxOrNull()?.let { date to it } }
            .sortedBy { it.first }
        val byWorkout = history.groupBy { it.workoutId }.values.sortedByDescending { it.first().date }

        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text("${e.category} · ${e.equipment} · ${e.loadKind.label}", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.notes.isNotBlank()) Text(e.notes, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (e.loadKind == LoadKind.WEIGHT) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("One-rep max", style = MaterialTheme.typography.labelMedium)
                                Text(current?.let { Strength.fmtLoadUnit(it.value, unit) } ?: "Not set", style = MaterialTheme.typography.headlineMedium)
                                if (current != null) Text("${if (current.source == "estimated") "Estimated" else "Entered"} ${fmtDate(current.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { settingMax = true }) { Text(if (current == null) "Set" else "Update") }
                        }
                    }
                    Text("Percentage prescriptions in your programs are calculated from this number. Finishing a workout with a heavier set raises it automatically.",
                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e1rmByDay.size >= 2) {
                    item {
                        SectionHeader("Estimated 1RM over time")
                        LineChart(
                            values = e1rmByDay.map { it.second },
                            labelStart = fmtDateShort(e1rmByDay.first().first), labelEnd = fmtDateShort(e1rmByDay.last().first),
                            formatValue = { Strength.fmtLoadUnit(it, unit) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            if (byWorkout.isNotEmpty()) {
                item { SectionHeader("History · ${history.size} sets") }
                items(byWorkout, key = { "w${it.first().workoutId}" }) { sets ->
                    val first = sets.first()
                    ListItem(
                        headlineContent = { Text(fmtDate(first.date)) },
                        supportingContent = {
                            Column {
                                Text(first.workoutTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(sets.joinToString("   ") { describeSet(it, e.loadKind, unit) })
                            }
                        },
                        modifier = Modifier.clickable { nav.push(Route.Workout(first.workoutId)) },
                    )
                }
            } else {
                item { Text("No sets logged yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (maxes.size > 1) {
                item { SectionHeader("Max history") }
                items(maxes, key = { "m${it.id}" }) { m ->
                    ListItem(
                        headlineContent = { Text(Strength.fmtLoadUnit(m.value, unit)) },
                        supportingContent = { Text("${if (m.source == "estimated") "Estimated" else "Entered"} · ${fmtDate(m.date)}") },
                        trailingContent = { AppIconButton(R.drawable.ic_delete, "Delete") { deleteMax = m } },
                    )
                }
            }
        }
    }

    if (editing && e != null) {
        ExerciseEditorDialog(initial = e, onDismiss = { editing = false }) { name, cat, eq, kind, notes ->
            scope.launch { app.repo.updateExercise(e.copy(name = name, category = cat, equipment = eq, loadKind = kind, notes = notes)) }
        }
    }
    if (settingMax) {
        TextPromptDialog("One-rep max", "Load in $unit", maxes.firstOrNull()?.let { Strength.fmtLoad(it.value, unit) } ?: "", keyboardType = KeyboardType.Decimal, onDismiss = { settingMax = false }) { text ->
            Strength.parseLoad(text, unit)?.takeIf { it > 0 }?.let { kg -> scope.launch { app.repo.setMax(exerciseId, kg) } }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete exercise?", "Only possible when no program or workout uses it.", onConfirm = {
            scope.launch { if (app.repo.deleteExercise(exerciseId)) nav.pop() else snackbar.showSnackbar("In use by a program or workout; can't delete") }
        }, onDismiss = { confirmDelete = false })
    }
    deleteMax?.let { m -> ConfirmDialog("Delete this max?", "${Strength.fmtLoadUnit(m.value, unit)} from ${fmtDate(m.date)}.", onConfirm = { scope.launch { app.repo.deleteMax(m.id) } }, onDismiss = { deleteMax = null }) }
}

/** "5 × 100", "12", "45s", "40 m (+20)". */
fun describeSet(r: SetRecord, kind: LoadKind, unit: String): String {
    val s = r.set
    return when (kind) {
        LoadKind.TIME -> Strength.fmtSeconds(s.seconds) + (s.load?.let { " @ ${Strength.fmtLoad(it, unit)}" } ?: "")
        LoadKind.DISTANCE -> Strength.fmtDistance(s.distance) + (s.load?.let { " @ ${Strength.fmtLoad(it, unit)}" } ?: "")
        LoadKind.BODYWEIGHT -> "${s.reps ?: "–"}" + (s.load?.let { " (+${Strength.fmtLoad(it, unit)})" } ?: "")
        LoadKind.WEIGHT -> "${s.reps ?: "–"} × ${Strength.fmtLoad(s.load, unit).ifBlank { "–" }}"
    }
}
