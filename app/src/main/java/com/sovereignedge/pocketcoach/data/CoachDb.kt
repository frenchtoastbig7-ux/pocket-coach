package com.sovereignedge.pocketcoach.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Plain SQLite: the schema is small and this avoids an annotation-processing step in the build.
 * Loads are kilograms, distances metres, dates are epoch days, timestamps epoch milliseconds.
 */
class CoachDb(context: Context, name: String = "coach.db") : SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE exercises(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'Accessory',
                equipment TEXT NOT NULL DEFAULT 'Other',
                load_kind TEXT NOT NULL DEFAULT 'WEIGHT',
                notes TEXT NOT NULL DEFAULT '',
                builtin INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE UNIQUE INDEX exercises_name ON exercises(name COLLATE NOCASE)")
        db.execSQL(
            """CREATE TABLE programs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                author TEXT NOT NULL DEFAULT '',
                weeks INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                program_id INTEGER NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
                week INTEGER NOT NULL,
                day INTEGER NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                UNIQUE(program_id, week, day))"""
        )
        db.execSQL(
            """CREATE TABLE blocks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                label TEXT NOT NULL,
                sort INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX blocks_session ON blocks(session_id)")
        db.execSQL(
            """CREATE TABLE items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                block_id INTEGER NOT NULL REFERENCES blocks(id) ON DELETE CASCADE,
                exercise_id INTEGER NOT NULL REFERENCES exercises(id) ON DELETE RESTRICT,
                sort INTEGER NOT NULL DEFAULT 0,
                sets_json TEXT NOT NULL DEFAULT '[]',
                rest_sec INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX items_block ON items(block_id)")
        db.execSQL(
            """CREATE TABLE assignments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                program_id INTEGER NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
                start_date INTEGER NOT NULL,
                active INTEGER NOT NULL DEFAULT 1)"""
        )
        db.execSQL(
            """CREATE TABLE workouts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL UNIQUE,
                date INTEGER NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                program_id INTEGER REFERENCES programs(id) ON DELETE SET NULL,
                session_id INTEGER REFERENCES sessions(id) ON DELETE SET NULL,
                started_at INTEGER NOT NULL,
                finished_at INTEGER,
                notes TEXT NOT NULL DEFAULT '',
                body_weight REAL)"""
        )
        db.execSQL("CREATE INDEX workouts_date ON workouts(date)")
        db.execSQL(
            """CREATE TABLE workout_exercises(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                workout_id INTEGER NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
                exercise_id INTEGER NOT NULL REFERENCES exercises(id) ON DELETE RESTRICT,
                block_label TEXT NOT NULL DEFAULT '',
                sort INTEGER NOT NULL DEFAULT 0,
                rest_sec INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX workout_exercises_workout ON workout_exercises(workout_id)")
        db.execSQL("CREATE INDEX workout_exercises_exercise ON workout_exercises(exercise_id)")
        db.execSQL(
            """CREATE TABLE sets(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                we_id INTEGER NOT NULL REFERENCES workout_exercises(id) ON DELETE CASCADE,
                idx INTEGER NOT NULL,
                target_reps TEXT NOT NULL DEFAULT '',
                target_load REAL,
                target_seconds INTEGER,
                target_distance REAL,
                rx_kind TEXT NOT NULL DEFAULT 'NONE',
                rx_value REAL,
                reps INTEGER,
                load REAL,
                seconds INTEGER,
                distance REAL,
                rpe REAL,
                completed INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX sets_we ON sets(we_id)")
        db.execSQL(
            """CREATE TABLE maxes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                exercise_id INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
                value REAL NOT NULL,
                date INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT 'entered')"""
        )
        db.execSQL("CREATE INDEX maxes_exercise ON maxes(exercise_id, date)")
        seedExercises(db)
        seedSampleProgram(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First schema version; migrations go here.
    }

    private fun seedExercises(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        for ((name, category, equipment, kind) in BUILTIN_EXERCISES) {
            db.insert("exercises", null, ContentValues().apply {
                put("uuid", UUID.randomUUID().toString())
                put("name", name)
                put("category", category)
                put("equipment", equipment)
                put("load_kind", kind.name)
                put("builtin", 1)
                put("created_at", now)
            })
        }
    }

    /** A small percentage-based template so the first launch isn't empty. Scheduled from next Monday. */
    private fun seedSampleProgram(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val programId = db.insert("programs", null, ContentValues().apply {
            put("uuid", UUID.randomUUID().toString())
            put("name", "Foundations")
            put("description", "Four weeks of linear strength work, three days a week. Squat, bench and deadlift run off your one-rep maxes; set them in the Library before you start.")
            put("author", "Pocket Coach")
            put("weeks", 4)
            put("created_at", now)
            put("updated_at", now)
        })
        fun exId(name: String): Long = db.rawQuery("SELECT id FROM exercises WHERE name=? COLLATE NOCASE", arrayOf(name)).use {
            it.moveToFirst(); it.getLong(0)
        }
        fun pct(p: Double, reps: String = "5", n: Int = 5) = List(n) { SetRx(reps, RxKind.PERCENT, p) }
        fun rpe(r: Double, reps: String, n: Int) = List(n) { SetRx(reps, RxKind.RPE, r) }
        fun plain(reps: String, n: Int) = List(n) { SetRx(reps, RxKind.NONE) }
        fun timed(sec: Int, n: Int) = List(n) { SetRx("", RxKind.NONE, seconds = sec) }
        fun dist(m: Double, n: Int) = List(n) { SetRx("", RxKind.NONE, distance = m) }

        data class Rx(val exercise: String, val sets: List<SetRx>, val rest: Int)
        data class Blk(val label: String, val items: List<Rx>)
        for (week in 1..4) {
            val base = 65.0 + 5 * week
            val days = listOf(
                Triple(1, "Lower", listOf(
                    Blk("A", listOf(Rx("Back Squat", pct(base), 180))),
                    Blk("B", listOf(Rx("Romanian Deadlift", rpe(7.0, "8", 3), 120))),
                    Blk("C", listOf(Rx("Walking Lunge", plain("10", 3), 60), Rx("Plank", timed(45, 3), 60))),
                )),
                Triple(3, "Upper", listOf(
                    Blk("A", listOf(Rx("Bench Press", pct(base), 180))),
                    Blk("B", listOf(Rx("Barbell Row", rpe(7.0, "8", 4), 120))),
                    Blk("C", listOf(Rx("Overhead Press", rpe(7.0, "8", 3), 90), Rx("Pull-Up", plain("AMRAP", 3), 90))),
                )),
                Triple(5, "Full body", listOf(
                    Blk("A", listOf(Rx("Deadlift", pct(base + 5, "5", 3), 180))),
                    Blk("B", listOf(Rx("Front Squat", rpe(7.0, "6", 3), 120))),
                    Blk("C", listOf(Rx("Dumbbell Bench Press", plain("10", 3), 60), Rx("Farmer's Carry", dist(40.0, 3), 90))),
                )),
            )
            for ((day, title, blocks) in days) {
                val sessionId = db.insert("sessions", null, ContentValues().apply {
                    put("program_id", programId); put("week", week); put("day", day); put("title", title)
                })
                blocks.forEachIndexed { bi, blk ->
                    val blockId = db.insert("blocks", null, ContentValues().apply {
                        put("session_id", sessionId); put("label", blk.label); put("sort", bi)
                    })
                    blk.items.forEachIndexed { ii, rx ->
                        db.insert("items", null, ContentValues().apply {
                            put("block_id", blockId); put("exercise_id", exId(rx.exercise)); put("sort", ii)
                            put("sets_json", SetRxCodec.toJson(rx.sets)); put("rest_sec", rx.rest)
                        })
                    }
                }
            }
        }
        val nextMonday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        db.insert("assignments", null, ContentValues().apply {
            put("program_id", programId); put("start_date", nextMonday.toEpochDay()); put("active", 1)
        })
    }

    companion object {
        const val VERSION = 1

        private val W = LoadKind.WEIGHT
        private val B = LoadKind.BODYWEIGHT
        private val T = LoadKind.TIME
        private val D = LoadKind.DISTANCE

        /** name, category, equipment, kind. */
        val BUILTIN_EXERCISES: List<Quad> = listOf(
            Quad("Back Squat", "Squat", "Barbell", W),
            Quad("Front Squat", "Squat", "Barbell", W),
            Quad("Box Squat", "Squat", "Barbell", W),
            Quad("Pause Squat", "Squat", "Barbell", W),
            Quad("Overhead Squat", "Squat", "Barbell", W),
            Quad("Goblet Squat", "Squat", "Kettlebell", W),
            Quad("Bulgarian Split Squat", "Squat", "Dumbbell", W),
            Quad("Walking Lunge", "Squat", "Dumbbell", W),
            Quad("Reverse Lunge", "Squat", "Dumbbell", W),
            Quad("Step-Up", "Squat", "Dumbbell", W),
            Quad("Leg Press", "Squat", "Machine", W),
            Quad("Leg Extension", "Accessory", "Machine", W),
            Quad("Deadlift", "Hinge", "Barbell", W),
            Quad("Sumo Deadlift", "Hinge", "Barbell", W),
            Quad("Trap Bar Deadlift", "Hinge", "Trap bar", W),
            Quad("Romanian Deadlift", "Hinge", "Barbell", W),
            Quad("Single-Leg Romanian Deadlift", "Hinge", "Dumbbell", W),
            Quad("Good Morning", "Hinge", "Barbell", W),
            Quad("Hip Thrust", "Hinge", "Barbell", W),
            Quad("Glute Bridge", "Hinge", "Bodyweight", B),
            Quad("Kettlebell Swing", "Hinge", "Kettlebell", W),
            Quad("Nordic Hamstring Curl", "Hinge", "Bodyweight", B),
            Quad("Leg Curl", "Accessory", "Machine", W),
            Quad("Back Extension", "Hinge", "Bodyweight", B),
            Quad("Bench Press", "Push", "Barbell", W),
            Quad("Close-Grip Bench Press", "Push", "Barbell", W),
            Quad("Incline Bench Press", "Push", "Barbell", W),
            Quad("Dumbbell Bench Press", "Push", "Dumbbell", W),
            Quad("Incline Dumbbell Press", "Push", "Dumbbell", W),
            Quad("Overhead Press", "Push", "Barbell", W),
            Quad("Push Press", "Push", "Barbell", W),
            Quad("Dumbbell Shoulder Press", "Push", "Dumbbell", W),
            Quad("Push-Up", "Push", "Bodyweight", B),
            Quad("Dip", "Push", "Bodyweight", B),
            Quad("Floor Press", "Push", "Barbell", W),
            Quad("Lateral Raise", "Accessory", "Dumbbell", W),
            Quad("Triceps Pushdown", "Accessory", "Cable", W),
            Quad("Skull Crusher", "Accessory", "Barbell", W),
            Quad("Pull-Up", "Pull", "Bodyweight", B),
            Quad("Chin-Up", "Pull", "Bodyweight", B),
            Quad("Lat Pulldown", "Pull", "Cable", W),
            Quad("Barbell Row", "Pull", "Barbell", W),
            Quad("Pendlay Row", "Pull", "Barbell", W),
            Quad("Dumbbell Row", "Pull", "Dumbbell", W),
            Quad("Seated Cable Row", "Pull", "Cable", W),
            Quad("Chest-Supported Row", "Pull", "Machine", W),
            Quad("Inverted Row", "Pull", "Bodyweight", B),
            Quad("Face Pull", "Pull", "Cable", W),
            Quad("Biceps Curl", "Accessory", "Dumbbell", W),
            Quad("Hammer Curl", "Accessory", "Dumbbell", W),
            Quad("Barbell Curl", "Accessory", "Barbell", W),
            Quad("Clean", "Olympic", "Barbell", W),
            Quad("Power Clean", "Olympic", "Barbell", W),
            Quad("Hang Clean", "Olympic", "Barbell", W),
            Quad("Clean and Jerk", "Olympic", "Barbell", W),
            Quad("Snatch", "Olympic", "Barbell", W),
            Quad("Power Snatch", "Olympic", "Barbell", W),
            Quad("Hang Snatch", "Olympic", "Barbell", W),
            Quad("Split Jerk", "Olympic", "Barbell", W),
            Quad("Clean Pull", "Olympic", "Barbell", W),
            Quad("Snatch Pull", "Olympic", "Barbell", W),
            Quad("Plank", "Core", "Bodyweight", T),
            Quad("Side Plank", "Core", "Bodyweight", T),
            Quad("Dead Bug", "Core", "Bodyweight", B),
            Quad("Hanging Leg Raise", "Core", "Bodyweight", B),
            Quad("Ab Wheel Rollout", "Core", "Other", B),
            Quad("Pallof Press", "Core", "Cable", W),
            Quad("Russian Twist", "Core", "Dumbbell", W),
            Quad("Cable Crunch", "Core", "Cable", W),
            Quad("Farmer's Carry", "Carry", "Dumbbell", D),
            Quad("Suitcase Carry", "Carry", "Kettlebell", D),
            Quad("Sled Push", "Carry", "Sled", D),
            Quad("Sled Drag", "Carry", "Sled", D),
            Quad("Box Jump", "Plyometric", "Bodyweight", B),
            Quad("Broad Jump", "Plyometric", "Bodyweight", B),
            Quad("Depth Jump", "Plyometric", "Bodyweight", B),
            Quad("Medicine Ball Slam", "Plyometric", "Other", W),
            Quad("Rowing", "Conditioning", "Machine", D),
            Quad("Assault Bike", "Conditioning", "Machine", T),
            Quad("Run", "Conditioning", "Bodyweight", D),
            Quad("Sprint", "Conditioning", "Bodyweight", D),
            Quad("Jump Rope", "Conditioning", "Other", T),
            Quad("Burpee", "Conditioning", "Bodyweight", B),
            Quad("Wall Ball", "Conditioning", "Other", W),
            Quad("Battle Rope", "Conditioning", "Other", T),
            Quad("Calf Raise", "Accessory", "Machine", W),
            Quad("Hip Abduction", "Accessory", "Machine", W),
            Quad("Shrug", "Accessory", "Barbell", W),
            Quad("Band Pull-Apart", "Accessory", "Band", B),
            Quad("Couch Stretch", "Mobility", "Bodyweight", T),
            Quad("Hip 90/90", "Mobility", "Bodyweight", T),
            Quad("Thoracic Rotation", "Mobility", "Bodyweight", B),
        )
    }

    data class Quad(val name: String, val category: String, val equipment: String, val kind: LoadKind)
}
