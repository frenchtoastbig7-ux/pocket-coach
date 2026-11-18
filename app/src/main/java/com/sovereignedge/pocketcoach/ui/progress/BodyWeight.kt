package com.sovereignedge.pocketcoach.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.BodyWeight
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.LineChart
import com.sovereignedge.pocketcoach.ui.common.fmtDate
import com.sovereignedge.pocketcoach.ui.common.fmtDateShort
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Latest reading, 90-day trend and a way in to log or review entries. */
@Composable
fun BodyWeightCard(app: CoachApp, entries: List<BodyWeight>, unit: String, modifier: Modifier = Modifier) {
    var logging by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    val latest = entries.firstOrNull()
    val since = LocalDate.now().minusDays(90)
    val trend = entries.filter { !it.date.isBefore(since) }.groupBy { it.date }.map { (d, l) -> d to l.first().kg }.sortedBy { it.first }
    val delta = if (trend.size >= 2) trend.last().second - trend.first().second else null

    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BODY WEIGHT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(latest?.let { Strength.fmtLoadUnit(it.kg, unit) } ?: "Not logged", style = MaterialTheme.typography.headlineMedium)
                    if (latest != null) Text(
                        fmtDate(latest.date) + (delta?.let { d -> "  ·  ${if (d >= 0) "+" else ""}${Strength.fmtLoad(d, unit)} $unit over 90 days" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { logging = true }) { Text("LOG", style = MaterialTheme.typography.labelLarge) }
            }
            if (trend.size >= 2) {
                Spacer(Modifier.height(8.dp))
                LineChart(trend.map { Strength.toUnit(it.second, unit) }, fmtDateShort(trend.first().first), fmtDateShort(trend.last().first), { "${Strength.fmtNum(it)} $unit" }, height = 110.dp)
            }
            if (entries.isNotEmpty()) TextButton(onClick = { history = true }) { Text("All entries (${entries.size})") }
        }
    }
    if (logging) LogWeightDialog(unit, latest?.kg, onDismiss = { logging = false }) { kg, date ->
        app.scope.launch { val id = app.repo.logBodyWeight(kg, date); app.exportWeightIfEnabled(id, kg, date) }
    }
    if (history) BodyWeightHistorySheet(app, entries, unit) { history = false }
}

@Composable
fun LogWeightDialog(unit: String, lastKg: Double?, onDismiss: () -> Unit, onSave: (kg: Double, date: LocalDate) -> Unit) {
    var text by rememberSaveable { mutableStateOf(lastKg?.let { Strength.fmtLoad(it, unit) } ?: "") }
    var daysAgo by rememberSaveable { mutableStateOf(0) }
    val kg = Strength.parseLoad(text, unit)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log body weight") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("Weight ($unit)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((d, label) in listOf(0 to "Today", 1 to "Yesterday", 2 to "2 days ago")) {
                        androidx.compose.material3.FilterChip(selected = daysAgo == d, onClick = { daysAgo = d }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = kg != null, onClick = { onSave(kg!!, LocalDate.now().minusDays(daysAgo.toLong())); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightHistorySheet(app: CoachApp, entries: List<BodyWeight>, unit: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Text("Body weight", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn {
                items(entries, key = { it.id }) { e ->
                    ListItem(
                        headlineContent = { Text(Strength.fmtLoadUnit(e.kg, unit)) },
                        supportingContent = { Text(fmtDate(e.date) + (if (e.source == "health") " · Health Connect" else "") + (if (e.note.isNotBlank()) " · ${e.note}" else "")) },
                        trailingContent = { AppIconButton(R.drawable.ic_delete, "Delete") { scope.launch { app.repo.deleteBodyWeight(e.id) } } },
                    )
                }
            }
        }
    }
}
