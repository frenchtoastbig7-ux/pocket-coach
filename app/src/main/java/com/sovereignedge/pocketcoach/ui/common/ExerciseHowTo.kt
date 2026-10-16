package com.sovereignedge.pocketcoach.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sovereignedge.pocketcoach.R
import com.sovereignedge.pocketcoach.data.Exercise
import java.net.URLEncoder

/** Opens the exercise's own demo link, or a web video search for it, in the phone's browser. The app itself stays offline. */
fun openDemoVideo(context: Context, e: Exercise) {
    val url = e.videoUrl.trim().ifBlank {
        "https://www.youtube.com/results?search_query=" + URLEncoder.encode("how to ${e.name} exercise form", "UTF-8")
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open $url", Toast.LENGTH_LONG).show()
    }
}

/** The how-to card body: description, demo button, and where the video comes from. */
@Composable
fun HowToContent(e: Exercise, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        if (e.description.isNotBlank()) {
            Text(e.description, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("No description yet. Add one, or put a Description column in your plan spreadsheet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (e.notes.isNotBlank()) Text("Cues: ${e.notes}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(onClick = { openDemoVideo(context, e) }) { AppIcon(R.drawable.ic_play, null); Spacer(Modifier.width(6.dp)); Text("Watch demo") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onEdit) { Text("Edit") }
        }
        Text(
            if (e.videoUrl.isBlank()) "Opens a video search in your browser. Add your own link in Edit or a Video column in the plan." else "Opens ${e.videoUrl.take(60)}${if (e.videoUrl.length > 60) "…" else ""}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Mid-workout sheet: what the exercise is, how to do it, and a way to the demo video and history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHowToSheet(e: Exercise, onDismiss: () -> Unit, onHistory: () -> Unit, onEdit: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(e.name, style = MaterialTheme.typography.headlineSmall)
            Text("${e.category} · ${e.equipment} · ${e.loadKind.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            HowToContent(e, onEdit = { onDismiss(); onEdit() })
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { onDismiss(); onHistory() }) { AppIcon(R.drawable.ic_history, null); Spacer(Modifier.width(6.dp)); Text("History & 1RM") }
            }
        }
    }
}
