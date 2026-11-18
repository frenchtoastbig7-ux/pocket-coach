package com.sovereignedge.pocketcoach.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sovereignedge.pocketcoach.Settings
import com.sovereignedge.pocketcoach.data.Plates
import com.sovereignedge.pocketcoach.data.Strength

/**
 * Plates per side for a barbell load. [initialKg] seeds the load field; the bar and plate set come from Settings,
 * and a bar picked here is remembered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorSheet(settings: Settings, initialKg: Double?, onDismiss: () -> Unit, onUseLoad: ((Double) -> Unit)? = null) {
    val snap = settings.current
    val unit = snap.unit
    var loadText by remember { mutableStateOf(initialKg?.let { Strength.fmtLoad(it, unit) } ?: "") }
    var bar by remember { mutableStateOf(snap.barWeight) }
    val plates = snap.plates
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val target = loadText.trim().replace(',', '.').toDoubleOrNull()
    val result = target?.let { Plates.load(it, bar, plates) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text("Plate calculator", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = loadText, onValueChange = { loadText = it }, label = { Text("Total load ($unit)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bar", style = MaterialTheme.typography.labelLarge)
                val choices = (Plates.barChoices(unit) + bar).distinct().sortedDescending()
                for (b in choices) {
                    FilterChip(selected = bar == b, onClick = { bar = b; settings.setBarWeight(b) }, label = { Text("${Strength.fmtNum(b)} $unit") })
                }
            }
            Spacer(Modifier.height(16.dp))
            if (result == null) {
                Text("Enter the total weight on the bar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                PlateDiagram(result.perSide, plates, unit)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (result.perSide.isEmpty()) "Empty bar" else "Per side: " + result.perSide.joinToString(" + ") { Strength.fmtNum(it) },
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (result.exact) "${Strength.fmtNum(result.achieved)} $unit total"
                    else "Closest with your plates: ${Strength.fmtNum(result.achieved)} $unit (${Strength.fmtNum(result.shortBy)} $unit under)",
                    color = if (result.exact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                if (onUseLoad != null && !result.exact) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { onUseLoad(Strength.fromUnit(result.achieved, unit)); onDismiss() }) { Text("Log ${Strength.fmtNum(result.achieved)} $unit") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Plates: ${Plates.formatList(plates)} $unit · change in Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One side of the bar, plates drawn from the collar outwards. */
@Composable
fun PlateDiagram(perSide: List<Double>, allPlates: List<Double>, unit: String) {
    val sleeve = MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
        // Bar shaft and collar
        Box(Modifier.width(40.dp).height(14.dp).background(sleeve, RoundedCornerShape(3.dp)))
        Box(Modifier.width(8.dp).height(34.dp).background(sleeve, RoundedCornerShape(2.dp)))
        for (p in perSide) {
            val h = (110 * Plates.sizeOf(p, allPlates)).dp
            val w = if (p >= 10) 22.dp else if (p >= 5) 16.dp else 12.dp
            Spacer(Modifier.width(3.dp))
            Box(Modifier.width(w).height(h).clip(RoundedCornerShape(4.dp)).background(Color(Plates.colourOf(p, unit))), contentAlignment = Alignment.Center) {
                if (p >= 10 || unit == "lb") Text(Strength.fmtNum(p), style = MaterialTheme.typography.labelSmall, color = if (p == 5.0 && unit == "kg") Color.Black else Color.White)
            }
        }
        Spacer(Modifier.width(3.dp))
        Box(Modifier.weight(1f).height(14.dp).background(sleeve, RoundedCornerShape(3.dp)))
    }
}
