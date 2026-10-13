package com.sovereignedge.pocketcoach.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Block
import com.sovereignedge.pocketcoach.data.Exercise
import com.sovereignedge.pocketcoach.data.Item
import com.sovereignedge.pocketcoach.data.ItemWithExercise
import com.sovereignedge.pocketcoach.data.LoadKind
import com.sovereignedge.pocketcoach.data.RxKind
import com.sovereignedge.pocketcoach.data.SetRx
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIcon
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.ChoiceField
import com.sovereignedge.pocketcoach.ui.common.ConfirmDialog
import com.sovereignedge.pocketcoach.ui.common.ExercisePickerSheet
import com.sovereignedge.pocketcoach.ui.common.TextPromptDialog
import com.sovereignedge.pocketcoach.ui.common.dayName
import com.sovereignedge.pocketcoach.ui.today.summariseSets
import kotlinx.coroutines.launch
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorScreen(app: CoachApp, sessionId: Long, nav: Nav) {
    val detail by remember(sessionId) { app.repo.session(sessionId) }.collectAsStateWithLifecycle(null)
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<Long?>(null) }
    var editItem by remember { mutableStateOf<ItemWithExercise?>(null) }
    var renameBlock by remember { mutableStateOf<Block?>(null) }
    var copyTo by remember { mutableStateOf(false) }

    val s = detail?.session
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(s?.title?.ifBlank { "Untitled session" } ?: "", style = MaterialTheme.typography.titleMedium)
                        if (s != null) Text("Week ${s.week} · ${dayName(s.day, TextStyle.FULL)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { AppIconButton(R.drawable.ic_arrow_back, "Back") { nav.pop() } },
                actions = {
                    AppIconButton(R.drawable.ic_edit, "Rename") { editTitle = true }
                    AppIconButton(R.drawable.ic_more, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Session notes") }, onClick = { menu = false; editNotes = true })
                        DropdownMenuItem(text = { Text("Copy to another day") }, onClick = { menu = false; copyTo = true })
                        DropdownMenuItem(text = { Text("Delete session") }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            if (d.session.notes.isNotBlank()) {
                item { Text(d.session.notes, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable { editNotes = true }, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(d.blocks, key = { it.block.id }) { b ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Block ${b.block.label}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).clickable { renameBlock = b.block })
                            if (b.items.size > 1) Text("superset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AppIconButton(R.drawable.ic_up, "Move up") { scope.launch { app.repo.moveBlock(b.block.id, true) } }
                            AppIconButton(R.drawable.ic_down, "Move down") { scope.launch { app.repo.moveBlock(b.block.id, false) } }
                            AppIconButton(R.drawable.ic_delete, "Delete block") { scope.launch { app.repo.deleteBlock(b.block.id) } }
                        }
                        b.items.forEachIndexed { idx, i ->
                            Row(Modifier.fillMaxWidth().clickable { editItem = i }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${b.block.label}${if (b.items.size > 1) (idx + 1).toString() else ""}  ${i.exercise.name}", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(summariseSets(i.item.sets, settings.unit), i.item.restSec.takeIf { it > 0 }?.let { "rest ${Strength.fmtSeconds(it)}" }, i.item.notes.takeIf { it.isNotBlank() }).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                AppIconButton(R.drawable.ic_up, "Move up") { scope.launch { app.repo.moveItem(i.item.id, true) } }
                                AppIconButton(R.drawable.ic_down, "Move down") { scope.launch { app.repo.moveItem(i.item.id, false) } }
                            }
                        }
                        TextButton(onClick = { pickFor = b.block.id }, modifier = Modifier.padding(start = 8.dp)) { AppIcon(R.drawable.ic_add, null); Text(if (b.items.isEmpty()) "Add exercise" else "Add to superset") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { scope.launch { app.repo.addBlock(sessionId) } }, modifier = Modifier.padding(16.dp)) { AppIcon(R.drawable.ic_add, null); Spacer(Modifier.width(4.dp)); Text("Add block") }
            }
        }
    }

    if (editTitle && s != null) TextPromptDialog("Session title", "Title", s.title, allowBlank = true, onDismiss = { editTitle = false }) { scope.launch { app.repo.updateSession(s.copy(title = it)) } }
    if (editNotes && s != null) TextPromptDialog("Session notes", "Notes for the athlete", s.notes, allowBlank = true, singleLine = false, onDismiss = { editNotes = false }) { scope.launch { app.repo.updateSession(s.copy(notes = it)) } }
    if (confirmDelete) ConfirmDialog("Delete this session?", "The day becomes a rest day.", onConfirm = { scope.launch { app.repo.deleteSession(sessionId); nav.pop() } }, onDismiss = { confirmDelete = false })
    renameBlock?.let { b -> TextPromptDialog("Block label", "Label", b.label, onDismiss = { renameBlock = null }) { scope.launch { app.repo.updateBlock(b.copy(label = it.take(3).uppercase())) } } }
    pickFor?.let { blockId ->
        ExercisePickerSheet(app, onDismiss = { pickFor = null }) { e ->
            scope.launch { app.repo.addItem(blockId, e.id, defaultSets(e), settings.defaultRest) }
        }
    }
    editItem?.let { i ->
        ItemEditorDialog(app, i, settings.unit, onDismiss = { editItem = null },
            onSave = { scope.launch { app.repo.updateItem(it) } },
            onDelete = { scope.launch { app.repo.deleteItem(i.item.id) } })
    }
    if (copyTo && s != null) {
        val programDetail by remember { app.repo.program(s.programId) }.collectAsStateWithLifecycle(null)
        val weeks = programDetail?.program?.weeks ?: s.week
        var week by remember { mutableStateOf(s.week) }
        var day by remember { mutableStateOf(if (s.day < 7) s.day + 1 else 1) }
        AlertDialog(
            onDismissRequest = { copyTo = false },
            title = { Text("Copy session to") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceField("Week", week, (1..weeks).map { it to "Week $it" }, Modifier.weight(1f)) { week = it }
                    ChoiceField("Day", day, (1..7).map { it to dayName(it, TextStyle.FULL) }, Modifier.weight(1f)) { day = it }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { app.repo.copySessionTo(sessionId, week, day) }; copyTo = false }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = { copyTo = false }) { Text("Cancel") } },
        )
    }
}

/** Sensible starting prescription for a freshly added exercise. */
fun defaultSets(e: Exercise): List<SetRx> = when (e.loadKind) {
    LoadKind.WEIGHT -> List(3) { SetRx("8", RxKind.NONE) }
    LoadKind.BODYWEIGHT -> List(3) { SetRx("10", RxKind.NONE) }
    LoadKind.TIME -> List(3) { SetRx("", RxKind.NONE, seconds = 30) }
    LoadKind.DISTANCE -> List(3) { SetRx("", RxKind.NONE, distance = 20.0) }
}

/** Edits one programmed exercise: its sets (reps / time / distance and load prescription), rest and notes. */
@Composable
fun ItemEditorDialog(app: CoachApp, iwe: ItemWithExercise, unit: String, onDismiss: () -> Unit, onSave: (Item) -> Unit, onDelete: () -> Unit) {
    var exercise by remember { mutableStateOf(iwe.exercise) }
    val sets = remember { mutableStateListOf<SetRx>().apply { addAll(iwe.item.sets) } }
    var rest by rememberSaveable { mutableStateOf(if (iwe.item.restSec > 0) Strength.fmtSeconds(iwe.item.restSec) else "") }
    var notes by rememberSaveable { mutableStateOf(iwe.item.notes) }
    var swapping by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(12.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { swapping = true }) { Text("Change") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val kind = exercise.loadKind
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("Set", Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
                    Text(when (kind) { LoadKind.TIME -> "Time"; LoadKind.DISTANCE -> "Metres"; else -> "Reps" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("Load", Modifier.weight(1.6f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(40.dp))
                }
                sets.forEachIndexed { idx, s ->
                    SetRxRow(idx, s, kind, unit, onChange = { sets[idx] = it }, onRemove = { sets.removeAt(idx) })
                }
                Row(Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { sets += (sets.lastOrNull() ?: SetRx("8")) }) { AppIcon(R.drawable.ic_add, null); Text("Add set") }
                    if (sets.size > 1) TextButton(onClick = { val f = sets.first(); for (i in sets.indices) sets[i] = f }) { Text("Same for all") }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(value = rest, onValueChange = { rest = it }, label = { Text("Rest between sets (e.g. 90, 1:30)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Coach notes") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) { AppIcon(R.drawable.ic_delete, null); Text("Remove from session") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(iwe.item.copy(exerciseId = exercise.id, sets = sets.toList(), restSec = Strength.parseSeconds(rest) ?: 0, notes = notes.trim()))
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (swapping) ExercisePickerSheet(app, onDismiss = { swapping = false }) { exercise = it }
    if (confirmDelete) ConfirmDialog("Remove exercise?", "It is removed from this session only.", confirmLabel = "Remove", onConfirm = { onDelete(); onDismiss() }, onDismiss = { confirmDelete = false })
}

@Composable
private fun SetRxRow(idx: Int, s: SetRx, kind: LoadKind, unit: String, onChange: (SetRx) -> Unit, onRemove: () -> Unit) {
    var primary by remember(idx, kind) {
        mutableStateOf(when (kind) { LoadKind.TIME -> s.seconds?.let(Strength::fmtSeconds) ?: ""; LoadKind.DISTANCE -> s.distance?.let(Strength::fmtNum) ?: ""; else -> s.reps })
    }
    var loadText by remember(idx, s.rxKind) {
        mutableStateOf(s.load?.let { if (s.rxKind == RxKind.ABSOLUTE) Strength.fmtLoad(it, unit) else Strength.fmtNum(it) } ?: "")
    }
    var kindMenu by remember { mutableStateOf(false) }
    // Keep the text in sync when "Same for all" rewrites this row.
    LaunchedEffect(s) {
        val p = when (kind) { LoadKind.TIME -> s.seconds?.let(Strength::fmtSeconds) ?: ""; LoadKind.DISTANCE -> s.distance?.let(Strength::fmtNum) ?: ""; else -> s.reps }
        if (parsePrimary(primary, kind) != parsePrimary(p, kind)) primary = p
        val l = s.load?.let { if (s.rxKind == RxKind.ABSOLUTE) Strength.fmtLoad(it, unit) else Strength.fmtNum(it) } ?: ""
        if (parseLoadText(loadText, s.rxKind, unit) != s.load) loadText = l
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${idx + 1}", Modifier.width(36.dp))
        OutlinedTextField(
            value = primary, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = if (kind == LoadKind.WEIGHT || kind == LoadKind.BODYWEIGHT) KeyboardType.Text else KeyboardType.Number),
            onValueChange = { t ->
                primary = t
                onChange(when (kind) {
                    LoadKind.TIME -> s.copy(seconds = Strength.parseSeconds(t))
                    LoadKind.DISTANCE -> s.copy(distance = t.replace(',', '.').toDoubleOrNull())
                    else -> s.copy(reps = t)
                })
            },
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = loadText, singleLine = true, modifier = Modifier.weight(1.6f), enabled = s.rxKind != RxKind.NONE,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            onValueChange = { t -> loadText = t; onChange(s.copy(load = parseLoadText(t, s.rxKind, unit))) },
            trailingIcon = {
                TextButton(onClick = { kindMenu = true }) {
                    Text(when (s.rxKind) { RxKind.NONE -> "—"; RxKind.PERCENT -> "%"; RxKind.RPE -> "RPE"; RxKind.ABSOLUTE -> unit })
                }
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                    RxKind.entries.forEach { k ->
                        DropdownMenuItem(text = { Text(k.label) }, onClick = { kindMenu = false; loadText = ""; onChange(s.copy(rxKind = k, load = null)) })
                    }
                }
            },
        )
        AppIconButton(R.drawable.ic_close, "Remove set") { onRemove() }
    }
}

private fun parsePrimary(t: String, kind: LoadKind): Any? = when (kind) {
    LoadKind.TIME -> Strength.parseSeconds(t)
    LoadKind.DISTANCE -> t.replace(',', '.').toDoubleOrNull()
    else -> t
}

private fun parseLoadText(t: String, kind: RxKind, unit: String): Double? = when (kind) {
    RxKind.NONE -> null
    RxKind.ABSOLUTE -> Strength.parseLoad(t, unit)
    else -> t.trim().replace(',', '.').toDoubleOrNull()
}
