package com.sovereignedge.pocketcoach.ui.programs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Assignment
import com.sovereignedge.pocketcoach.data.SpreadsheetProgram
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIcon
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.ConfirmDialog
import com.sovereignedge.pocketcoach.ui.common.EmptyState
import com.sovereignedge.pocketcoach.ui.common.SectionHeader
import com.sovereignedge.pocketcoach.ui.common.fmtDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(app: CoachApp, modifier: Modifier, nav: Nav) {
    val programs by app.repo.programs().collectAsStateWithLifecycle(emptyList())
    val assignments by app.repo.assignments().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var deleteAssignment by remember { mutableStateOf<Assignment?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(app::importProgramFile) }
    val templateSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(SpreadsheetProgram.templateCsv()) }
                snackbar.showSnackbar("Template saved. Fill it in and import it here.")
            } catch (e: Exception) {
                snackbar.showSnackbar("Could not save: ${e.message}")
            }
        }
    }
    fun pickFile() = importer.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/octet-stream"))

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Programs") },
                actions = {
                    AppIconButton(R.drawable.ic_download, "Import a plan file") { pickFile() }
                    AppIconButton(R.drawable.ic_more, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Import plan (CSV, Excel, JSON)") }, onClick = { menu = false; pickFile() })
                        DropdownMenuItem(text = { Text("Save spreadsheet template") }, onClick = { menu = false; templateSaver.launch("training-plan.csv") })
                        DropdownMenuItem(text = { Text("Spreadsheet format") }, onClick = { menu = false; showFormat = true })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; nav.push(Route.Settings) })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { AppIcon(R.drawable.ic_add, null) }, text = { Text("New program") })
        },
    ) { padding ->
        if (programs.isEmpty()) {
            EmptyState(R.drawable.ic_calendar, "No programs yet", "Write your own, or import a plan: a CSV or Excel sheet, or a program file from another Pocket Coach.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                val active = assignments.filter { it.active }
                if (active.isNotEmpty()) {
                    item { SectionHeader("Following") }
                    items(active, key = { "a${it.id}" }) { a ->
                        val p = programs.firstOrNull { it.id == a.programId }
                        ListItem(
                            headlineContent = { Text(p?.name ?: "Program") },
                            supportingContent = { Text("Week 1 starts ${fmtDate(a.startDate)} · ${p?.weeks ?: 0} weeks") },
                            trailingContent = {
                                Switch(checked = a.active, onCheckedChange = { on -> scope.launch { app.repo.updateAssignment(a.copy(active = on)) } })
                            },
                            modifier = Modifier.clickable { p?.let { nav.push(Route.Program(it.id)) } },
                        )
                    }
                }
                val paused = assignments.filter { !it.active }
                if (paused.isNotEmpty()) {
                    item { SectionHeader("Paused") }
                    items(paused, key = { "a${it.id}" }) { a ->
                        val p = programs.firstOrNull { it.id == a.programId }
                        ListItem(
                            headlineContent = { Text(p?.name ?: "Program") },
                            supportingContent = { Text("Was starting ${fmtDate(a.startDate)}") },
                            trailingContent = {
                                Row {
                                    Switch(checked = false, onCheckedChange = { scope.launch { app.repo.updateAssignment(a.copy(active = true)) } })
                                    AppIconButton(R.drawable.ic_delete, "Remove") { deleteAssignment = a }
                                }
                            },
                        )
                    }
                }
                item { SectionHeader("All programs") }
                items(programs, key = { "p${it.id}" }) { p ->
                    val days = remember(p.id) { mutableStateOf<Int?>(null) }
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text(listOfNotNull("${p.weeks} week${if (p.weeks == 1) "" else "s"}", p.author.takeIf { it.isNotBlank() }?.let { "by $it" }).joinToString(" · "))
                        },
                        trailingContent = { AppIcon(R.drawable.ic_chevron_right, null) },
                        modifier = Modifier.clickable { nav.push(Route.Program(p.id)) },
                    )
                }
            }
        }
    }

    if (creating) {
        NewProgramDialog(defaultAuthor = app.settings.current.athleteName, onDismiss = { creating = false }) { name, weeks, description ->
            scope.launch { nav.push(Route.Program(app.repo.createProgram(name, description, app.settings.current.athleteName, weeks))) }
        }
    }
    if (showFormat) SpreadsheetFormatDialog(onDismiss = { showFormat = false }, onTemplate = { templateSaver.launch("training-plan.csv") })
    deleteAssignment?.let { a ->
        ConfirmDialog("Remove from calendar?", "Logged workouts are kept.", confirmLabel = "Remove", onConfirm = { scope.launch { app.repo.deleteAssignment(a.id) } }, onDismiss = { deleteAssignment = null })
    }
}

@Composable
fun NewProgramDialog(defaultAuthor: String, onDismiss: () -> Unit, onCreate: (name: String, weeks: Int, description: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var weeks by rememberSaveable { mutableStateOf("4") }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New program") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = weeks, onValueChange = { weeks = it.filter(Char::isDigit).take(2) }, label = { Text("Weeks") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (defaultAuthor.isNotBlank()) Text("Author: $defaultAuthor", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (weeks.toIntOrNull() ?: 0) > 0, onClick = { onCreate(name.trim(), weeks.toInt(), description.trim()); onDismiss() }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Explains the spreadsheet columns the importer understands. */
@Composable
fun SpreadsheetFormatDialog(onDismiss: () -> Unit, onTemplate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spreadsheet format") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("One row per exercise. The first row names the columns; order doesn't matter.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                for ((k, v) in listOf(
                    "Week" to "1, 2, 3… Carries down when blank.",
                    "Day" to "1–7 or Mon, Tue… (1 = Monday). Carries down when blank.",
                    "Session" to "Title for that day (optional).",
                    "Block" to "A, B, C… Rows with the same letter on the same day form a superset.",
                    "Exercise" to "Name. Unknown ones are added to your library.",
                    "Sets" to "How many.",
                    "Reps" to "5, 8-12, AMRAP, or a time (45s, 1:30) or distance (40 m, 2 km). A list like 5/5/3 gives each set its own.",
                    "Load" to "75% (of 1RM), RPE 8, 100 kg, 225 lb, BW, or blank. Lists work: 70%/75%/80%.",
                    "Rest" to "Seconds or m:ss.",
                    "Notes" to "Coach cues (optional). Also Category, Equipment, Type (weight / bodyweight / time / distance).",
                )) {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(k, Modifier.width(76.dp), style = MaterialTheme.typography.labelLarge)
                        Text(v, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Excel (.xlsx) and CSV from Google Sheets, Numbers or Excel all work; only the first sheet is read.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onTemplate(); onDismiss() }) { Text("Save template") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
