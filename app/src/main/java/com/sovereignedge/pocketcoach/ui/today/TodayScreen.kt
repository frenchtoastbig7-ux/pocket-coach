package com.sovereignedge.pocketcoach.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Repo
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.data.Workout
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIcon
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.BlockBadge
import com.sovereignedge.pocketcoach.ui.common.SectionHeader
import com.sovereignedge.pocketcoach.ui.common.dayName
import com.sovereignedge.pocketcoach.ui.common.fmtDate
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(app: CoachApp, modifier: Modifier, nav: Nav) {
    val today = LocalDate.now()
    var selected by remember { mutableStateOf(today) }
    val weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scheduled by remember(selected) { app.repo.scheduled(selected) }.collectAsStateWithLifecycle(emptyList())
    val workouts by remember(selected) { app.repo.workoutsOn(selected) }.collectAsStateWithLifecycle(emptyList())
    val marks by remember(weekStart) { app.repo.markedDates(weekStart, weekStart.plusDays(6)) }.collectAsStateWithLifecycle(emptyMap())
    val assignments by app.repo.assignments().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (selected == today) "Today" else fmtDate(selected)) },
                actions = {
                    if (selected != today) TextButton(onClick = { selected = today }) { Text("Today") }
                    AppIconButton(R.drawable.ic_settings, "Settings") { nav.push(Route.Settings) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { nav.push(Route.Workout(app.repo.startEmpty(selected))) } },
                icon = { AppIcon(R.drawable.ic_add, null) },
                text = { Text("Empty workout") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item {
                WeekStrip(weekStart, selected, today, marks, onSelect = { selected = it }, onShift = { selected = selected.plusWeeks(it.toLong()) })
            }
            if (workouts.isNotEmpty()) {
                item { SectionHeader("Logged") }
                items(workouts, key = { "w${it.id}" }) { w -> WorkoutCard(w) { nav.push(Route.Workout(w.id)) } }
            }
            val startedSessionIds = workouts.mapNotNull { it.sessionId }.toSet()
            val pending = scheduled.filter { it.session == null || it.session.session.id !in startedSessionIds }
            if (pending.isNotEmpty()) {
                item { SectionHeader("Planned") }
                items(pending, key = { "p${it.assignment.id}" }) { s ->
                    ScheduledCard(s, settings.unit) {
                        scope.launch {
                            val id = app.repo.startFromSession(s.session!!.session.id, selected, settings.unit, settings.increment)
                            nav.push(Route.Workout(id))
                        }
                    }
                }
            }
            if (assignments.none { it.active } && workouts.isEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("No program on the calendar", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("Follow a program from the Programs tab and its sessions will show up here on their days. Or log an empty workout and build it as you go.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(weekStart: LocalDate, selected: LocalDate, today: LocalDate, marks: Map<LocalDate, Boolean>, onSelect: (LocalDate) -> Unit, onShift: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            AppIconButton(R.drawable.ic_chevron_left, "Previous week") { onShift(-1) }
            Text(weekStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleSmall)
            AppIconButton(R.drawable.ic_chevron_right, "Next week") { onShift(1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 0 until 7) {
                val date = weekStart.plusDays(i.toLong())
                val isSel = date == selected
                Column(
                    Modifier.weight(1f).padding(2.dp).clip(MaterialTheme.shapes.medium)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { onSelect(date) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayName(i + 1).take(1), style = MaterialTheme.typography.labelSmall, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    val mark = marks[date]
                    Box(
                        Modifier.padding(top = 3.dp).size(6.dp).clip(CircleShape).background(
                            when (mark) {
                                true -> if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary
                                false -> if (isSel) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                                null -> androidx.compose.ui.graphics.Color.Transparent
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(w: Workout, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.title, style = MaterialTheme.typography.titleMedium)
                Text(if (w.finished) "Finished" else "In progress", color = if (w.finished) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            AppIcon(if (w.finished) R.drawable.ic_check else R.drawable.ic_play, null)
        }
    }
}

@Composable
private fun ScheduledCard(s: Repo.Scheduled, unit: String, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("${s.program.name} · WEEK ${s.week} · ${dayName(s.day).uppercase()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val session = s.session
            if (session == null) {
                Text("Rest day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(session.session.title.ifBlank { "Day ${session.session.day}" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                if (session.session.notes.isNotBlank()) Text(session.session.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                for (b in session.blocks) b.items.forEachIndexed { idx, i ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        BlockBadge(b.block.label + if (b.items.size > 1) (idx + 1).toString() else "")
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(i.exercise.name, style = MaterialTheme.typography.bodyLarge)
                            Text(summariseSets(i.item.sets, unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { AppIcon(R.drawable.ic_play, null); Spacer(Modifier.size(6.dp)); Text("START WORKOUT", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** "5 × 5 @ 75%", "3 × 8-12", "3 × 45s". */
fun summariseSets(sets: List<com.sovereignedge.pocketcoach.data.SetRx>, unit: String): String {
    if (sets.isEmpty()) return "no sets"
    val first = sets.first()
    val same = sets.all { it == first }
    fun one(s: com.sovereignedge.pocketcoach.data.SetRx): String {
        val what = when {
            s.seconds != null -> Strength.fmtSeconds(s.seconds)
            s.distance != null -> Strength.fmtDistance(s.distance)
            else -> s.reps.ifBlank { "–" }
        }
        val load = when (s.rxKind) {
            com.sovereignedge.pocketcoach.data.RxKind.PERCENT -> s.load?.let { " @ ${Strength.fmtNum(it)}%" } ?: ""
            com.sovereignedge.pocketcoach.data.RxKind.RPE -> s.load?.let { " @ RPE ${Strength.fmtNum(it)}" } ?: ""
            com.sovereignedge.pocketcoach.data.RxKind.ABSOLUTE -> s.load?.let { " @ ${Strength.fmtLoadUnit(it, unit)}" } ?: ""
            com.sovereignedge.pocketcoach.data.RxKind.NONE -> ""
        }
        return what + load
    }
    return if (same) "${sets.size} × ${one(first)}" else sets.joinToString(", ") { one(it) }
}
