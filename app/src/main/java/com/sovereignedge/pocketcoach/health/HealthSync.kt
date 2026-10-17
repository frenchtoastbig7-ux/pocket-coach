package com.sovereignedge.pocketcoach.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import com.sovereignedge.pocketcoach.data.Repo
import com.sovereignedge.pocketcoach.data.WorkoutDetail
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Health Connect bridge. Health Connect is the on-device store that Google Fit, Fitbit, Samsung Health and others
 * sync through, so this is how body weight gets in from a smart scale and how workouts reach the rest of the phone.
 * Nothing here touches the network; the app still has no INTERNET permission.
 */
class HealthSync(private val context: Context, private val repo: Repo) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    /** AVAILABLE, UPDATE_REQUIRED or UNAVAILABLE. */
    val status: Int get() = HealthConnectClient.getSdkStatus(context)
    val available: Boolean get() = status == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient get() = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean = try {
        available && client.permissionController.getGrantedPermissions().containsAll(permissions)
    } catch (e: Exception) {
        false
    }

    /** Pulls the last year of weight readings into the log. Returns how many were new. */
    suspend fun importWeights(): Int {
        val now = Instant.now()
        val response = client.readRecords(
            ReadRecordsRequest(WeightRecord::class, timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(365L * 86400), now))
        )
        val readings = response.records.map { r ->
            val zone = r.zoneOffset ?: ZoneId.systemDefault().rules.getOffset(r.time)
            Triple(r.metadata.id, r.time.atZone(zone).toLocalDate(), r.weight.inKilograms)
        }
        return repo.importBodyWeights(readings)
    }

    /** Writes a weight and returns the Health Connect record id. */
    suspend fun exportWeight(kg: Double, date: LocalDate): String {
        val time = ZonedDateTime.of(date, if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON, ZoneId.systemDefault())
        val response = client.insertRecords(listOf(WeightRecord(time = time.toInstant(), zoneOffset = time.offset, weight = Mass.kilograms(kg), metadata = Metadata.manualEntry())))
        return response.recordIdsList.first()
    }

    /** Writes a finished workout as an exercise session (strength training unless it was all conditioning). */
    suspend fun exportWorkout(detail: WorkoutDetail) {
        val w = detail.workout
        val end = w.finishedAt ?: return
        val start = minOf(w.startedAt, end - 60_000)
        val zone = ZoneId.systemDefault()
        val categories = detail.exercises.map { it.exercise.category }.toSet()
        val names = detail.exercises.map { it.exercise.name.lowercase() }
        val type = when {
            categories.isNotEmpty() && categories.all { it == "Conditioning" } && names.all { it.contains("run") || it.contains("sprint") } -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            categories.isNotEmpty() && categories.all { it == "Conditioning" } && names.all { it.contains("cycl") || it.contains("bike") } -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            categories.isNotEmpty() && categories.all { it == "Conditioning" } && names.all { it.contains("row") } -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
            else -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        }
        val startZdt = Instant.ofEpochMilli(start).atZone(zone)
        val endZdt = Instant.ofEpochMilli(end).atZone(zone)
        client.insertRecords(listOf(
            ExerciseSessionRecord(
                startTime = startZdt.toInstant(), startZoneOffset = startZdt.offset,
                endTime = endZdt.toInstant(), endZoneOffset = endZdt.offset,
                exerciseType = type, title = w.title, notes = w.notes.ifBlank { null },
                metadata = Metadata.manualEntry(),
            )
        ))
    }
}
