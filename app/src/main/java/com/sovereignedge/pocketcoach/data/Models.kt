package com.sovereignedge.pocketcoach.data

import java.time.LocalDate

/** What a set of this exercise records besides reps. Loads are always stored in kilograms, distances in metres. */
enum class LoadKind(val label: String) {
    WEIGHT("Weight × reps"),
    BODYWEIGHT("Bodyweight reps"),
    TIME("Time"),
    DISTANCE("Distance");

    companion object {
        fun parse(s: String?): LoadKind = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: WEIGHT
    }
}

data class Exercise(
    val id: Long,
    val uuid: String,
    val name: String,
    val category: String,
    val equipment: String,
    val loadKind: LoadKind,
    val notes: String,
    val builtin: Boolean,
    /** How to perform it: setup, execution, common faults. */
    val description: String = "",
    /** A demonstration video link; blank means "search the web". */
    val videoUrl: String = "",
) {
    companion object {
        val CATEGORIES = listOf("Squat", "Hinge", "Push", "Pull", "Olympic", "Core", "Carry", "Conditioning", "Plyometric", "Accessory", "Mobility")
        val EQUIPMENT = listOf("Barbell", "Dumbbell", "Kettlebell", "Machine", "Cable", "Bodyweight", "Band", "Trap bar", "Sled", "Other")
    }
}

/** How a prescribed set states its load. */
enum class RxKind(val label: String) {
    NONE("No load"),
    PERCENT("% of 1RM"),
    RPE("RPE"),
    ABSOLUTE("Fixed load");

    companion object {
        fun parse(s: String?): RxKind = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: NONE
    }
}

/**
 * One prescribed set. [reps] is free text ("5", "8-12", "AMRAP", "30s"). For PERCENT, [load] is 0..100; for ABSOLUTE
 * it is kilograms; for RPE it is the RPE value. [seconds] / [distance] apply to timed and distance exercises.
 */
data class SetRx(
    val reps: String = "",
    val rxKind: RxKind = RxKind.NONE,
    val load: Double? = null,
    val seconds: Int? = null,
    val distance: Double? = null,
)

data class Program(
    val id: Long,
    val uuid: String,
    val name: String,
    val description: String,
    val author: String,
    val weeks: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A training day inside a program: week 1..weeks, day 1 (Monday) .. 7 (Sunday). */
data class Session(
    val id: Long,
    val programId: Long,
    val week: Int,
    val day: Int,
    val title: String,
    val notes: String,
)

/** Exercises sharing a block are performed together (A1/A2 superset). */
data class Block(
    val id: Long,
    val sessionId: Long,
    val label: String,
    val sort: Int,
    val notes: String,
)

data class Item(
    val id: Long,
    val blockId: Long,
    val exerciseId: Long,
    val sort: Int,
    val sets: List<SetRx>,
    val restSec: Int,
    val notes: String,
)

data class ItemWithExercise(val item: Item, val exercise: Exercise)
data class BlockDetail(val block: Block, val items: List<ItemWithExercise>)
data class SessionDetail(val session: Session, val blocks: List<BlockDetail>)
data class ProgramDetail(val program: Program, val sessions: List<Session>)

/** A program the athlete is following; week 1 day 1 falls on [startDate] (a Monday). */
data class Assignment(
    val id: Long,
    val programId: Long,
    val startDate: LocalDate,
    val active: Boolean,
) {
    /** The (week, day) this assignment schedules for [date], or null if outside the program. */
    fun slotOn(date: LocalDate, weeks: Int): Pair<Int, Int>? {
        val offset = date.toEpochDay() - startDate.toEpochDay()
        if (offset < 0) return null
        val week = (offset / 7).toInt() + 1
        if (week > weeks) return null
        return week to (offset % 7).toInt() + 1
    }
}

data class Workout(
    val id: Long,
    val uuid: String,
    val date: LocalDate,
    val title: String,
    val programId: Long?,
    val sessionId: Long?,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String,
    val bodyWeight: Double?,
) {
    val finished get() = finishedAt != null
}

data class WorkoutExercise(
    val id: Long,
    val workoutId: Long,
    val exerciseId: Long,
    val blockLabel: String,
    val sort: Int,
    val restSec: Int,
    val notes: String,
)

data class WorkoutSet(
    val id: Long,
    val workoutExerciseId: Long,
    val index: Int,
    val targetReps: String,
    val targetLoad: Double?,
    val targetSeconds: Int?,
    val targetDistance: Double?,
    val rxKind: RxKind,
    val rxValue: Double?,
    val reps: Int?,
    val load: Double?,
    val seconds: Int?,
    val distance: Double?,
    val rpe: Double?,
    val completed: Boolean,
)

data class WorkoutExerciseDetail(val entry: WorkoutExercise, val exercise: Exercise, val sets: List<WorkoutSet>)
data class WorkoutDetail(val workout: Workout, val exercises: List<WorkoutExerciseDetail>)

/** A one-rep max for an exercise, in kilograms. */
data class Max(
    val id: Long,
    val exerciseId: Long,
    val value: Double,
    val date: LocalDate,
    /** "entered" or "estimated". */
    val source: String,
)

/** One logged set with its context, for exercise history. */
data class SetRecord(
    val set: WorkoutSet,
    val workoutId: Long,
    val date: LocalDate,
    val workoutTitle: String,
)

data class PersonalRecord(val exercise: Exercise, val bestLoad: Double?, val bestE1rm: Double?, val max: Max?, val sets: Int)
