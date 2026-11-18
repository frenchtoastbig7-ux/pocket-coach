package com.sovereignedge.pocketcoach.ui.programs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Program
import com.sovereignedge.pocketcoach.data.Session
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIcon
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.ConfirmDialog
import com.sovereignedge.pocketcoach.ui.common.dayName
import com.sovereignedge.pocketcoach.ui.common.fmtDate
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(app: CoachApp, programId: Long, nav: Nav) {
    val detail by remember(programId) { app.repo.program(programId) }.collectAsStateWithLifecycle(null)
    val assignments by app.repo.assignments().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText
        if (uri != null && text != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                    snackbar.showSnackbar("Program file saved")
                } catch (e: Exception) {
                    snackbar.showSnackbar("Could not save: ${e.message}")
                }
            }
        }
        exportText = null
    }

    val p = detail?.program
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(p?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { AppIconButton(R.drawable.ic_arrow_back, "Back") { nav.pop() } },
                actions = {
                    AppIconButton(R.drawable.ic_more, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit details") }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                            menu = false
                            scope.launch { nav.replaceAll(Route.Program(app.repo.duplicateProgram(programId))) }
                        })
                        DropdownMenuItem(text = { Text("Export program file") }, onClick = {
                            menu = false
                            scope.launch {
                                exportText = app.repo.exportProgram(programId)
                                exporter.launch("${(p?.name ?: "program").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "program" }}.json")
                            }
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        val d = detail
        if (d == null) return@Scaffold
        val mine = assignments.filter { it.programId == programId && it.active }
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (d.program.description.isNotBlank()) Text(d.program.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        listOfNotNull("${d.program.weeks} weeks", "${d.sessions.size} sessions", d.program.author.takeIf { it.isNotBlank() }?.let { "by $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (mine.isEmpty()) {
                        Button(onClick = { picking = true }) { AppIcon(R.drawable.ic_flag, null); Spacer(Modifier.padding(4.dp)); Text("Follow this program") }
                    } else {
                        for (a in mine) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("On your calendar from ${fmtDate(a.startDate)}", modifier = Modifier.weight(1f))
                                TextButton(onClick = { scope.launch { app.repo.updateAssignment(a.copy(active = false)) } }) { Text("Stop") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Tap a day to plan it. Day 1 is Monday.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items((1..d.program.weeks).toList(), key = { it }) { week ->
                WeekRow(week, d.program, d.sessions.filter { it.week == week },
                    onOpen = { day -> scope.launch { nav.push(Route.Session(app.repo.getOrCreateSession(programId, week, day))) } },
                    onCopyNext = { scope.launch { app.repo.copyWeek(programId, week, week + 1); snackbar.showSnackbar("Week $week copied to week ${week + 1}") } },
                    onCopyAll = { scope.launch { for (w in 1..d.program.weeks) if (w != week) app.repo.copyWeek(programId, week, w); snackbar.showSnackbar("Week $week copied to every week") } },
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { app.repo.updateProgram(d.program.copy(weeks = d.program.weeks + 1)) } }) { Text("Add week") }
                    if (d.program.weeks > 1) OutlinedButton(onClick = { scope.launch { app.repo.updateProgram(d.program.copy(weeks = d.program.weeks - 1)) } }) { Text("Remove last week") }
                }
            }
        }
    }

    if (editing && p != null) {
        ProgramDetailsDialog(p, onDismiss = { editing = false }) { scope.launch { app.repo.updateProgram(it) } }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete program?", "Its sessions are removed from the calendar. Logged workouts are kept.", onConfirm = { scope.launch { app.repo.deleteProgram(programId); nav.pop() } }, onDismiss = { confirmDelete = false })
    }
    if (picking) {
        StartDatePicker(onDismiss = { picking = false }) { date ->
            scope.launch { app.repo.assign(programId, date); snackbar.showSnackbar("Week 1 starts ${fmtDate(date)}") }
        }
    }
}

@Composable
private fun WeekRow(week: Int, program: Program, sessions: List<Session>, onOpen: (Int) -> Unit, onCopyNext: () -> Unit, onCopyAll: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Week $week", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).padding(start = 4.dp))
            AppIconButton(R.drawable.ic_more, "Week actions") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (week < program.weeks) DropdownMenuItem(text = { Text("Copy to week ${week + 1}") }, onClick = { menu = false; onCopyNext() })
                if (program.weeks > 1) DropdownMenuItem(text = { Text("Copy to every week") }, onClick = { menu = false; onCopyAll() })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (day in 1..7) {
                val s = sessions.firstOrNull { it.day == day }
                Column(
                    Modifier.weight(1f).height(72.dp).clip(MaterialTheme.shapes.small)
                        .background(if (s != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onOpen(day) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayName(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    if (s != null) {
                        Text(s.title.ifBlank { "Session" }, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    } else {
                        AppIcon(R.drawable.ic_add, "Plan", Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramDetailsDialog(p: Program, onDismiss: () -> Unit, onSave: (Program) -> Unit) {
    var name by rememberSaveable { mutableStateOf(p.name) }
    var author by rememberSaveable { mutableStateOf(p.author) }
    var weeks by rememberSaveable { mutableStateOf(p.weeks.toString()) }
    var description by rememberSaveable { mutableStateOf(p.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Program details") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(
                    value = weeks, onValueChange = { weeks = it.filter(Char::isDigit).take(2) }, label = { Text("Weeks") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    supportingText = { if ((weeks.toIntOrNull() ?: p.weeks) < p.weeks) Text("Sessions in removed weeks are deleted") },
                )
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (weeks.toIntOrNull() ?: 0) > 0, onClick = {
                onSave(p.copy(name = name.trim(), author = author.trim(), weeks = weeks.toInt(), description = description.trim())); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks the Monday week 1 starts on; any other day chosen snaps back to its Monday. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartDatePicker(onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val nextMonday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    val state = rememberDatePickerState(initialSelectedDateMillis = nextMonday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis ?: return@TextButton
                val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                onPick(date); onDismiss()
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, title = { Text("Week 1 starts on the Monday of…", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
