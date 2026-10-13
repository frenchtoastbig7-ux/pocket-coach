package com.sovereignedge.pocketcoach.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Exercise
import com.sovereignedge.pocketcoach.data.LoadKind
import kotlinx.coroutines.launch

/** Search-and-pick sheet over the exercise library, with a shortcut to create a missing exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(app: CoachApp, onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    val exercises by app.repo.exercises().collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filtered = exercises.filter { e ->
        (category == null || e.category == category) && (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true) || e.equipment.contains(query.trim(), ignoreCase = true))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search exercises") }, singleLine = true,
                leadingIcon = { AppIcon(R.drawable.ic_search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(Modifier.padding(vertical = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                items(listOf<String?>(null) + Exercise.CATEGORIES) { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c ?: "All") }, modifier = Modifier.padding(end = 8.dp))
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (query.isNotBlank() && filtered.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                    item {
                        ListItem(
                            headlineContent = { Text("Create “${query.trim()}”") },
                            leadingContent = { AppIcon(R.drawable.ic_add, null) },
                            modifier = Modifier.clickable { creating = true },
                        )
                    }
                }
                items(filtered, key = { it.id }) { e ->
                    ListItem(
                        headlineContent = { Text(e.name) },
                        supportingContent = { Text("${e.category} · ${e.equipment}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { onPick(e); onDismiss() },
                    )
                }
            }
        }
    }
    if (creating) {
        ExerciseEditorDialog(initial = null, initialName = query.trim(), onDismiss = { creating = false }) { name, cat, eq, kind, notes ->
            scope.launch {
                val id = app.repo.addExercise(name, cat, eq, kind, notes)
                app.repo.exercise(id).collect { e -> if (e != null) { onPick(e); onDismiss() }; return@collect }
            }
        }
    }
}

/** Create or edit an exercise. */
@Composable
fun ExerciseEditorDialog(
    initial: Exercise?,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, equipment: String, loadKind: LoadKind, notes: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: initialName) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "Accessory") }
    var equipment by rememberSaveable { mutableStateOf(initial?.equipment ?: "Barbell") }
    var kind by rememberSaveable { mutableStateOf(initial?.loadKind ?: LoadKind.WEIGHT) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        title = { Text(if (initial == null) "New exercise" else "Edit exercise") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 8.dp)) {
                    ChoiceField("Category", category, Exercise.CATEGORIES.map { it to it }, Modifier.weight(1f)) { category = it }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    ChoiceField("Equipment", equipment, Exercise.EQUIPMENT.map { it to it }, Modifier.weight(1f)) { equipment = it }
                }
                ChoiceField("Logs", kind, LoadKind.entries.map { it to it.label }, Modifier.padding(top = 8.dp)) { kind = it }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Cues / notes") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), category, equipment, kind, notes.trim()); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
