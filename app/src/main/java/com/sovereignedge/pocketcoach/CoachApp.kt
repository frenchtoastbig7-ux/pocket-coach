package com.sovereignedge.pocketcoach

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import com.sovereignedge.pocketcoach.data.ProgramJson
import com.sovereignedge.pocketcoach.data.Repo
import com.sovereignedge.pocketcoach.data.SpreadsheetProgram
import com.sovereignedge.pocketcoach.health.HealthSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoachApp : Application() {
    lateinit var repo: Repo
    lateinit var settings: Settings
    lateinit var health: HealthSync
    /** Set when Health Connect asks the app to explain its permissions. */
    val showHealthRationale = MutableStateFlow(false)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Results of opening a plan file (JSON program, CSV or Excel): Ok(programId) or Err(message). */
    sealed class ImportEvent {
        class Ok(val programId: Long, val name: String, val warnings: List<String>) : ImportEvent()
        class Err(val message: String) : ImportEvent()
    }

    private val _imports = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 4)
    val imports: SharedFlow<ImportEvent> = _imports

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        settings = Settings(this)
        health = HealthSync(this, repo)
    }

    /** Mirrors a finished workout to Health Connect when the user has turned that on. Failures are silent. */
    fun exportWorkoutIfEnabled(workoutId: Long) {
        if (!settings.current.healthSync) return
        scope.launch {
            try {
                repo.workout(workoutId).collect { d -> if (d != null) { health.exportWorkout(d) }; return@collect }
            } catch (_: Exception) { }
        }
    }

    fun exportWeightIfEnabled(rowId: Long, kg: Double, date: java.time.LocalDate) {
        if (!settings.current.healthSync) return
        scope.launch { try { repo.setBodyWeightHealthId(rowId, health.exportWeight(kg, date)) } catch (_: Exception) { } }
    }

    /** Reads a shared plan file and imports it. The format is sniffed from the content, not the extension. */
    fun importProgramFile(uri: Uri) {
        scope.launch {
            val event = try {
                val (bytes, displayName) = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the file")
                    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment ?: "Imported plan"
                    bytes to name
                }
                val baseName = displayName.substringBeforeLast('.').ifBlank { "Imported plan" }
                val unit = settings.current.unit
                val (parsed, warnings) = withContext(Dispatchers.Default) {
                    when {
                        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() ->
                            SpreadsheetProgram.parseXlsx(bytes, baseName, unit).let { it.program to it.warnings }
                        else -> {
                            val text = String(bytes, Charsets.UTF_8)
                            if (text.trimStart().startsWith("{")) ProgramJson.parse(text) to emptyList()
                            else SpreadsheetProgram.parseCsv(text, baseName, unit).let { it.program to it.warnings }
                        }
                    }
                }
                val id = repo.importProgram(parsed)
                ImportEvent.Ok(id, parsed.name, warnings)
            } catch (e: Exception) {
                ImportEvent.Err(e.message ?: "Import failed")
            }
            _imports.emit(event)
        }
    }
}
