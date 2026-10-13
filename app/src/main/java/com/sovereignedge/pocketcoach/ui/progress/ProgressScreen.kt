package com.sovereignedge.pocketcoach.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Repo
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.ui.Nav
import com.sovereignedge.pocketcoach.ui.Route
import com.sovereignedge.pocketcoach.ui.common.AppIcon
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.BarChart
import com.sovereignedge.pocketcoach.ui.common.EmptyState
import com.sovereignedge.pocketcoach.ui.common.SectionHeader
import com.sovereignedge.pocketcoach.ui.common.fmtDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(app: CoachApp, modifier: Modifier, nav: Nav) {
    val totals by app.repo.totals().collectAsStateWithLifecycle(null)
    val weeks by app.repo.weeklyStats(12).collectAsStateWithLifecycle(emptyList())
    val records by app.repo.personalRecords().collectAsStateWithLifecycle(emptyList())
    val workouts by app.repo.workouts().collectAsStateWithLifecycle(emptyList())
    val settings by app.settings.state.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf("tonnage") }
    val unit = settings.unit

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Progress") }, actions = { AppIconButton(R.drawable.ic_settings, "Settings") { nav.push(Route.Settings) } }) },
    ) { padding ->
        val t = totals
        if (t != null && t.workouts == 0 && workouts.isEmpty()) {
            EmptyState(R.drawable.ic_trending, "Nothing logged yet", "Finish a workout and your totals, weekly volume and records appear here.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (t != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("${t.workouts}", "workouts", Modifier.weight(1f))
                        StatCard("${t.sets}", "sets", Modifier.weight(1f))
                        StatCard(Strength.fmtLoad(t.tonnage, unit), "$unit lifted", Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Last 12 weeks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    FilterChip(selected = metric == "tonnage", onClick = { metric = "tonnage" }, label = { Text("Tonnage") })
                    FilterChip(selected = metric == "sets", onClick = { metric = "sets" }, label = { Text("Sets") })
                    FilterChip(selected = metric == "workouts", onClick = { metric = "workouts" }, label = { Text("Days") })
                }
                BarChart(
                    values = weeks.map { w -> when (metric) { "sets" -> w.sets.toDouble(); "workouts" -> w.workouts.toDouble(); else -> Strength.toUnit(w.tonnage, unit) } },
                    labels = weeks.filterIndexed { i, _ -> i % 3 == 0 }.map { it.weekStart.format(DateTimeFormatter.ofPattern("d MMM")) } + listOf("now"),
                    formatValue = { Strength.fmtNum(it) + if (metric == "tonnage") " $unit" else "" },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (records.isNotEmpty()) {
                item { SectionHeader("Records") }
                items(records.take(30), key = { "r${it.exercise.id}" }) { r ->
                    ListItem(
                        headlineContent = { Text(r.exercise.name) },
                        supportingContent = {
                            Text(listOfNotNull(
                                r.max?.let { "1RM ${Strength.fmtLoadUnit(it.value, unit)}" },
                                r.bestLoad?.let { "heaviest set ${Strength.fmtLoadUnit(it, unit)}" },
                                "${r.sets} sets",
                            ).joinToString(" · "))
                        },
                        trailingContent = { AppIcon(R.drawable.ic_trophy, null) },
                        modifier = Modifier.clickable { nav.push(Route.Exercise(r.exercise.id)) },
                    )
                }
            }
            if (workouts.isNotEmpty()) {
                item { SectionHeader("History") }
                items(workouts, key = { "w${it.id}" }) { w ->
                    ListItem(
                        headlineContent = { Text(w.title) },
                        supportingContent = { Text(fmtDate(w.date) + if (!w.finished) " · in progress" else "") },
                        trailingContent = { AppIcon(R.drawable.ic_chevron_right, null) },
                        modifier = Modifier.clickable { nav.push(Route.Workout(w.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
