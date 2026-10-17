package com.sovereignedge.pocketcoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignedge.pocketcoach.BuildConfig
import com.sovereignedge.pocketcoach.CoachApp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.Settings
import com.sovereignedge.pocketcoach.data.Plates
import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.ui.common.AppIconButton
import com.sovereignedge.pocketcoach.ui.common.ChoiceField
import com.sovereignedge.pocketcoach.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: CoachApp, onBack: () -> Unit) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val totals by app.repo.totals().collectAsStateWithLifecycle(null)
    var name by rememberSaveable { mutableStateOf(settings.athleteName) }
    var rest by rememberSaveable { mutableStateOf(Strength.fmtSeconds(settings.defaultRest)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { AppIconButton(R.drawable.ic_arrow_back, "Back", onClick = onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionHeader("Units")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Settings.UNITS.forEachIndexed { i, (v, label) ->
                    SegmentedButton(selected = settings.unit == v, onClick = { app.settings.setUnit(v) }, shape = SegmentedButtonDefaults.itemShape(i, Settings.UNITS.size)) { Text(label) }
                }
            }
            val increments = if (settings.unit == "lb") listOf(1.0, 2.5, 5.0, 10.0) else listOf(0.5, 1.0, 2.5, 5.0)
            ChoiceField("Round prescribed loads to", settings.increment, increments.map { it to "${Strength.fmtNum(it)} ${settings.unit}" }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { app.settings.setIncrement(it) }
            Text("Loads are stored in kilograms, so switching units later loses nothing.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader("Barbell")
            var barText by rememberSaveable(settings.unit) { mutableStateOf(Strength.fmtNum(settings.barWeight)) }
            var platesText by rememberSaveable(settings.unit) { mutableStateOf(Plates.formatList(settings.plates)) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = barText, onValueChange = { barText = it; it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v > 0 }?.let(app.settings::setBarWeight) },
                    label = { Text("Bar (${settings.unit})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = platesText, onValueChange = { platesText = it; Plates.parseList(it).takeIf { l -> l.isNotEmpty() }?.let(app.settings::setPlates) },
                    label = { Text("Plates you have (${settings.unit})") }, singleLine = true, modifier = Modifier.weight(2f),
                )
            }
            Text("Used by the plate calculator: tap a barbell set's target during a workout.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader("Training")
            OutlinedTextField(
                value = rest, onValueChange = { rest = it; Strength.parseSeconds(it)?.let(app.settings::setDefaultRest) },
                label = { Text("Default rest for new exercises (seconds or m:ss)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            ListItem(headlineContent = { Text("Keep screen on during a workout") }, trailingContent = { Switch(settings.keepScreenOn, app.settings::setKeepScreenOn) })
            ListItem(headlineContent = { Text("Beep when rest is over") }, trailingContent = { Switch(settings.restSound, app.settings::setRestSound) })

            SectionHeader("Appearance")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Settings.THEME_MODES.forEachIndexed { i, (v, label) ->
                    SegmentedButton(selected = settings.themeMode == v, onClick = { app.settings.setThemeMode(v) }, shape = SegmentedButtonDefaults.itemShape(i, Settings.THEME_MODES.size)) { Text(label) }
                }
            }
            Text("Colours follow your wallpaper (Material You).", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader("Coach")
            OutlinedTextField(
                value = name, onValueChange = { name = it; app.settings.setAthleteName(it) }, label = { Text("Your name (shown as author of programs you write)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                "To coach someone, write a program here, export it from the program's menu and send them the file. They open it in Pocket Coach and it lands on their calendar once they follow it.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionHeader("About")
            ListItem(headlineContent = { Text("Pocket Coach ${BuildConfig.VERSION_NAME}") }, supportingContent = { Text("Open source, Apache 2.0 · © 2026 Sovereign Edge Inc.") })
            totals?.let { t ->
                ListItem(headlineContent = { Text("On this phone") }, supportingContent = { Text("${t.programs} programs · ${t.exercises} exercises · ${t.workouts} workouts · ${t.sets} sets") })
            }
            ListItem(headlineContent = { Text("Privacy") }, supportingContent = { Text("Everything stays on the phone. No account, no server, no analytics; the app has no network permission.") })
        }
    }
}
