package com.sovereignedge.pocketcoach.ui.workout

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.LoadKind
import com.sovereignedge.pocketcoach.data.Repo
import com.sovereignedge.pocketcoach.data.RxKind
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.data.WorkoutExerciseDetail
import com.sovereignedge.pocketcoach.data.WorkoutSet
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIcon
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.ConfirmDialog
import com.sovereignedge.pocketcoach.ui.common.ExercisePickerSheet
import com.sovereignedge.pocketcoach.ui.common.TextPromptDialog
import com.sovereignedge.pocketcoach.ui.common.fmtDate
import com.sovereignedge.pocketcoach.ui.common.fmtDateShort
import com.sovereignedge.pocketcoach.ui.common.fmtDuration
import com.sovereignedge.pocketcoach.ui.library.describeSet
import com.sovereignedge.pocketcoach.ui.library.effectiveKind
import com.sovereignedge.pocketcoach.ui.common.ExerciseEditorDialog
import com.sovereignedge.pocketcoach.ui.common.ExerciseHowToSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(app: CoachApp, workoutId: Long, nav: Nav) {
    val detail by remember(workoutId) { app.repo.workout(workoutId) }.collectAsStateWithLifecycle(null)
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf<List<Repo.NewRecord>?>(null) }

    // Rest timer: end time in wall-clock millis, ticked every quarter second.
    var restEnd by remember { mutableStateOf<Long?>(null) }
    var restTotal by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(restEnd) {
        val end = restEnd ?: return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= end) {
                if (settings.restSound) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90).apply { startTone(ToneGenerator.TONE_PROP_BEEP2, 400) } }
                restEnd = null
                break
            }
            delay(250)
        }
    }
    fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restTotal = seconds * 1000L
        restEnd = System.currentTimeMillis() + restTotal
    }

    val w = detail?.workout
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn, w?.finished) {
        view.keepScreenOn = settings.keepScreenOn && w?.finished == false
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { editTitle = true }) {
                        Text(w?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (w != null) Text(fmtDate(w.date) + if (w.finished) " · finished" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { AppIconButton(R.drawable.ic_arrow_back, "Back") { nav.pop() } },
                actions = {
                    if (w != null && !w.finished) {
                        Button(onClick = { scope.launch { records = app.repo.finishWorkout(workoutId) } }, modifier = Modifier.padding(end = 4.dp)) { Text("Finish") }
                    }
                    AppIconButton(R.drawable.ic_more, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; editTitle = true })
                        DropdownMenuItem(text = { Text("Notes") }, onClick = { menu = false; editNotes = true })
                        if (w?.finished == true) DropdownMenuItem(text = { Text("Reopen") }, onClick = { menu = false; scope.launch { app.repo.reopenWorkout(workoutId) } })
                        DropdownMenuItem(text = { Text("Delete workout") }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
        bottomBar = {
            val end = restEnd
            if (end != null) {
                RestBar(remaining = end - now, total = restTotal, onAdd = { restEnd = end + 30_000 }, onSkip = { restEnd = null })
            }
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            if (d.workout.finished) {
                item {
                    val done = d.exercises.flatMap { it.sets }.filter { it.completed }
                    val tonnage = done.sumOf(Strength::volume)
                    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Stat(fmtDuration((d.workout.finishedAt ?: 0) - d.workout.startedAt), "duration")
                            Stat("${done.size}", "sets")
                            Stat(Strength.fmtLoadUnit(tonnage, settings.unit), "tonnage")
                        }
                    }
                }
            }
            if (d.workout.notes.isNotBlank()) item { Text(d.workout.notes, Modifier.padding(horizontal = 16.dp).clickable { editNotes = true }, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(d.exercises, key = { it.entry.id }) { ex ->
                ExerciseCard(app, ex, settings.unit, readOnly = d.workout.finished, onOpenExercise = { nav.push(Route.Exercise(ex.exercise.id)) }, onRest = { startRest(it) })
            }
            if (!d.workout.finished) {
                item {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.padding(16.dp)) { AppIcon(R.drawable.ic_add, null); Spacer(Modifier.width(4.dp)); Text("Add exercise") }
                }
            }
        }
    }

    if (editTitle && w != null) TextPromptDialog("Workout title", "Title", w.title, onDismiss = { editTitle = false }) { scope.launch { app.repo.updateWorkout(w.copy(title = it)) } }
    if (editNotes && w != null) TextPromptDialog("Workout notes", "How did it go?", w.notes, allowBlank = true, singleLine = false, onDismiss = { editNotes = false }) { scope.launch { app.repo.updateWorkout(w.copy(notes = it)) } }
    if (confirmDelete) ConfirmDialog("Delete workout?", "Every set logged in it is lost.", onConfirm = { scope.launch { app.repo.deleteWorkout(workoutId); nav.pop() } }, onDismiss = { confirmDelete = false })
    if (picking) ExercisePickerSheet(app, onDismiss = { picking = false }) { e -> scope.launch { app.repo.addWorkoutExercise(workoutId, e.id) } }
    records?.let { list ->
        AlertDialog(
            onDismissRequest = { records = null; nav.pop() },
            title = { Text(if (list.isEmpty()) "Workout saved" else "New records!") },
            text = {
                Column {
                    if (list.isEmpty()) Text("Nice work. It's on your calendar and in your history.")
                    for (r in list) {
                        Text("${r.exercise.name}: estimated 1RM ${Strength.fmtLoadUnit(r.e1rm, settings.unit)}" + (r.previous?.let { " (was ${Strength.fmtLoadUnit(it, settings.unit)})" } ?: ""))
                    }
                    if (list.isNotEmpty()) Text("Percentage work in your programs now uses the new numbers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { records = null; nav.pop() }) { Text("Done") } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RestBar(remaining: Long, total: Long, onAdd: () -> Unit, onSkip: () -> Unit) {
    val secs = ((remaining + 999) / 1000).coerceAtLeast(0)
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            val frac = if (total > 0) (remaining.toFloat() / total).coerceIn(0f, 1f) else 0f
            Row(Modifier.fillMaxWidth().height(4.dp)) {
                Spacer(Modifier.weight(frac.coerceAtLeast(0.001f)).fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.primary))
                if (frac < 1f) Spacer(Modifier.weight((1f - frac).coerceAtLeast(0.001f)))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_timer, null)
                Spacer(Modifier.width(12.dp))
                Text("Rest  %d:%02d".format(secs / 60, secs % 60), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text("+30s") }
                TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun ExerciseCard(app: CoachApp, ex: WorkoutExerciseDetail, unit: String, readOnly: Boolean, onOpenExercise: () -> Unit, onRest: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    var editRest by remember { mutableStateOf(false) }
    var howTo by remember { mutableStateOf(false) }
    var editExercise by remember { mutableStateOf(false) }
    // The exercise's default shape, unless its sets say otherwise (a timed row of a distance exercise).
    val kind = ex.sets.firstOrNull()?.let { effectiveKind(it, ex.exercise.loadKind) } ?: ex.exercise.loadKind
    val last by remember(ex.exercise.id, ex.entry.workoutId) { app.repo.lastPerformance(ex.exercise.id, ex.entry.workoutId) }.collectAsStateWithLifecycle(null)
    val lastSets = last?.sets ?: emptyList()
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { howTo = true }) {
                    Text((if (ex.entry.blockLabel.isNotBlank()) "${ex.entry.blockLabel}  " else "") + ex.exercise.name, style = MaterialTheme.typography.titleMedium)
                    val sub = listOfNotNull(ex.entry.restSec.takeIf { it > 0 }?.let { "rest ${Strength.fmtSeconds(it)}" }, ex.entry.notes.takeIf { it.isNotBlank() })
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    last?.let { l ->
                        Text(
                            "Last time · ${fmtDateShort(l.date)}: " + l.sets.joinToString(", ") { describeSet(it, kind, unit) },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (!readOnly && ex.entry.restSec > 0) AppIconButton(R.drawable.ic_timer, "Start rest timer") { onRest(ex.entry.restSec) }
                AppIconButton(R.drawable.ic_more, "Exercise actions") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("How to & demo") }, onClick = { menu = false; howTo = true })
                    DropdownMenuItem(text = { Text("Notes") }, onClick = { menu = false; editNotes = true })
                    DropdownMenuItem(text = { Text("Rest time") }, onClick = { menu = false; editRest = true })
                    DropdownMenuItem(text = { Text("Exercise history") }, onClick = { menu = false; onOpenExercise() })
                    if (!readOnly) {
                        DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; scope.launch { app.repo.moveWorkoutExercise(ex.entry.id, true) } })
                        DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; scope.launch { app.repo.moveWorkoutExercise(ex.entry.id, false) } })
                        DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; scope.launch { app.repo.deleteWorkoutExercise(ex.entry.id) } })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Set", Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium)
                Text("Target", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
                if (kind != LoadKind.TIME && kind != LoadKind.DISTANCE || true) Text(if (kind == LoadKind.BODYWEIGHT) "+$unit" else unit, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(when (kind) { LoadKind.TIME -> "Time"; LoadKind.DISTANCE -> "m"; else -> "Reps" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("RPE", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(44.dp))
            }
            ex.sets.forEach { s ->
                val rowKind = effectiveKind(s, ex.exercise.loadKind)
                // Same set number last time (if it carried a load), else the last loaded set, else the last set: shown as a
                // placeholder and logged when an empty set is ticked.
                val suggestion = lastSets.getOrNull(s.index)?.takeIf { it.load != null || rowKind != LoadKind.WEIGHT }
                    ?: lastSets.lastOrNull { it.load != null } ?: lastSets.lastOrNull()
                SetRow(s, rowKind, unit, readOnly, suggestion,
                    onUpdate = { scope.launch { app.repo.updateSet(it) } },
                    onCompleted = { if (ex.entry.restSec > 0) onRest(ex.entry.restSec) },
                    onDelete = { scope.launch { app.repo.deleteSet(s.id) } })
            }
            if (!readOnly) TextButton(onClick = { scope.launch { app.repo.addSet(ex.entry.id) } }, modifier = Modifier.padding(start = 8.dp)) { AppIcon(R.drawable.ic_add, null); Text("Add set") }
        }
    }
    if (howTo) ExerciseHowToSheet(ex.exercise, onDismiss = { howTo = false }, onHistory = onOpenExercise, onEdit = { editExercise = true })
    if (editExercise) {
        ExerciseEditorDialog(initial = ex.exercise, onDismiss = { editExercise = false }) { name, cat, eq, k, notes, description, video ->
            scope.launch { app.repo.updateExercise(ex.exercise.copy(name = name, category = cat, equipment = eq, loadKind = k, notes = notes, description = description, videoUrl = video)) }
        }
    }
    if (editNotes) TextPromptDialog("Notes", "Notes for ${ex.exercise.name}", ex.entry.notes, allowBlank = true, singleLine = false, onDismiss = { editNotes = false }) { scope.launch { app.repo.updateWorkoutExercise(ex.entry.copy(notes = it)) } }
    if (editRest) TextPromptDialog("Rest between sets", "Seconds or m:ss", if (ex.entry.restSec > 0) Strength.fmtSeconds(ex.entry.restSec) else "", allowBlank = true, onDismiss = { editRest = false }) {
        scope.launch { app.repo.updateWorkoutExercise(ex.entry.copy(restSec = Strength.parseSeconds(it) ?: 0)) }
    }
}

/** One logged set: target on the left, editable load / reps (or time / distance) / RPE, and a done checkbox. */
@Composable
private fun SetRow(s: WorkoutSet, kind: LoadKind, unit: String, readOnly: Boolean, suggestion: WorkoutSet?, onUpdate: (WorkoutSet) -> Unit, onCompleted: () -> Unit, onDelete: () -> Unit) {
    var loadText by remember(s.id) { mutableStateOf(Strength.fmtLoad(s.load, unit)) }
    var primaryText by remember(s.id) { mutableStateOf(primaryOf(s, kind)) }
    var rpeText by remember(s.id) { mutableStateOf(s.rpe?.let(Strength::fmtNum) ?: "") }
    // Writes from elsewhere (completing a set fills in the targets) must show up without clobbering what's being typed.
    LaunchedEffect(s.load, s.reps, s.seconds, s.distance, s.rpe) {
        if (Strength.parseLoad(loadText, unit) != s.load) loadText = Strength.fmtLoad(s.load, unit)
        if (parsePrimary(primaryText, kind) != primaryValue(s, kind)) primaryText = primaryOf(s, kind)
        if (rpeText.replace(',', '.').toDoubleOrNull() != s.rpe) rpeText = s.rpe?.let(Strength::fmtNum) ?: ""
    }
    val target = buildString {
        when {
            s.targetSeconds != null -> append(Strength.fmtSeconds(s.targetSeconds))
            s.targetDistance != null -> append(Strength.fmtDistance(s.targetDistance))
            s.targetReps.isNotBlank() -> append(s.targetReps)
        }
        when {
            s.targetLoad != null -> append(if (isEmpty()) "" else " × ").append(Strength.fmtLoad(s.targetLoad, unit))
            s.rxKind == RxKind.PERCENT && s.rxValue != null -> append(" @ ${Strength.fmtNum(s.rxValue)}%")
            s.rxKind == RxKind.RPE && s.rxValue != null -> append(" @ RPE ${Strength.fmtNum(s.rxValue)}")
        }
    }
    val bg = if (s.completed) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${s.index + 1}", Modifier.width(32.dp))
        Text(target.ifBlank { "—" }, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // What a tick would log: typed value, else the prescription, else last time.
        val fillLoad = s.targetLoad ?: suggestion?.load
        val fillPrimary: Any? = when (kind) {
            LoadKind.TIME -> s.targetSeconds ?: suggestion?.seconds
            LoadKind.DISTANCE -> s.targetDistance ?: suggestion?.distance
            else -> Strength.firstReps(s.targetReps) ?: suggestion?.reps
        }
        CompactField(loadText, Modifier.weight(1f), readOnly, KeyboardType.Decimal, hint = if (s.completed) "" else Strength.fmtLoad(fillLoad, unit)) { loadText = it; onUpdate(s.copy(load = Strength.parseLoad(it, unit))) }
        Spacer(Modifier.width(4.dp))
        val primaryHint = when (val v = fillPrimary) { null -> ""; is Int -> if (kind == LoadKind.TIME) Strength.fmtSeconds(v) else v.toString(); is Double -> Strength.fmtNum(v); else -> "" }
        CompactField(primaryText, Modifier.weight(1f), readOnly, if (kind == LoadKind.TIME) KeyboardType.Text else KeyboardType.Number, hint = if (s.completed) "" else primaryHint) { t ->
            primaryText = t
            onUpdate(when (kind) {
                LoadKind.TIME -> s.copy(seconds = Strength.parseSeconds(t))
                LoadKind.DISTANCE -> s.copy(distance = t.replace(',', '.').toDoubleOrNull())
                else -> s.copy(reps = t.filter(Char::isDigit).toIntOrNull())
            })
        }
        Spacer(Modifier.width(4.dp))
        CompactField(rpeText, Modifier.weight(0.8f), readOnly, KeyboardType.Decimal) { rpeText = it; onUpdate(s.copy(rpe = it.replace(',', '.').toDoubleOrNull())) }
        Checkbox(
            checked = s.completed, enabled = !readOnly,
            onCheckedChange = { done ->
                if (done) {
                    // Ticking an untouched set logs the prescription as performed.
                    val filled = s.copy(
                        completed = true,
                        load = s.load ?: fillLoad,
                        reps = s.reps ?: if (kind == LoadKind.TIME || kind == LoadKind.DISTANCE) null else fillPrimary as? Int,
                        seconds = s.seconds ?: if (kind == LoadKind.TIME) fillPrimary as? Int else null,
                        distance = s.distance ?: if (kind == LoadKind.DISTANCE) fillPrimary as? Double else null,
                    )
                    onUpdate(filled)
                    onCompleted()
                } else onUpdate(s.copy(completed = false))
            },
        )
    }
}

@Composable
private fun CompactField(value: String, modifier: Modifier, readOnly: Boolean, keyboard: KeyboardType, hint: String = "", onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, readOnly = readOnly, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        placeholder = if (hint.isEmpty()) null else ({
            Text(hint, style = if (hint.length > 4) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), maxLines = 1, softWrap = false,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }),
    )
}

private fun primaryOf(s: WorkoutSet, kind: LoadKind): String = when (kind) {
    LoadKind.TIME -> s.seconds?.let(Strength::fmtSeconds) ?: ""
    LoadKind.DISTANCE -> s.distance?.let(Strength::fmtNum) ?: ""
    else -> s.reps?.toString() ?: ""
}

private fun primaryValue(s: WorkoutSet, kind: LoadKind): Any? = when (kind) {
    LoadKind.TIME -> s.seconds
    LoadKind.DISTANCE -> s.distance
    else -> s.reps
}

private fun parsePrimary(t: String, kind: LoadKind): Any? = when (kind) {
    LoadKind.TIME -> Strength.parseSeconds(t)
    LoadKind.DISTANCE -> t.replace(',', '.').toDoubleOrNull()
    else -> t.filter(Char::isDigit).toIntOrNull()
}
