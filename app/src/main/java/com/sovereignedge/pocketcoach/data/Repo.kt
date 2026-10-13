package com.sovereignedge.pocketcoach.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/**
 * All reads and writes. Reads are Flows that re-query whenever a table they depend on changes; writes run in a
 * transaction on the IO dispatcher and then tick the change bus for the tables they touched.
 */
class Repo(context: Context, dbName: String = "coach.db") {
    private val helper = CoachDb(context, dbName)
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 256)

    private fun <T> watch(vararg tables: String, query: (SQLiteDatabase) -> T): Flow<T> =
        changes.onStart { emit(tables.first()) }
            .filter { it in tables }
            .map { query(db) }
            .conflate()
            .flowOn(Dispatchers.IO)

    private suspend fun <T> write(vararg tables: String, block: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) {
            val d = db
            d.beginTransaction()
            try {
                block(d).also { d.setTransactionSuccessful() }
            } finally {
                d.endTransaction()
            }
        }.also { tables.forEach { changes.tryEmit(it) } }

    private suspend fun <T> read(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) { block(db) }

    fun close() = helper.close()

    // ---------- Exercises ----------

    fun exercises(): Flow<List<Exercise>> = watch(T_EX) { d -> queryExercises(d, null, null) }

    fun exercise(id: Long): Flow<Exercise?> = watch(T_EX) { d -> queryExercises(d, "id=?", arrayOf(id.toString())).firstOrNull() }

    private fun queryExercises(d: SQLiteDatabase, where: String?, args: Array<String>?): List<Exercise> =
        d.query("exercises", null, where, args, null, null, "name COLLATE NOCASE").mapAll(::exerciseOf)

    private fun exerciseOf(c: Cursor) = Exercise(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        uuid = c.getString(c.getColumnIndexOrThrow("uuid")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        category = c.getString(c.getColumnIndexOrThrow("category")),
        equipment = c.getString(c.getColumnIndexOrThrow("equipment")),
        loadKind = LoadKind.parse(c.getString(c.getColumnIndexOrThrow("load_kind"))),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        builtin = c.getInt(c.getColumnIndexOrThrow("builtin")) == 1,
    )

    suspend fun findExercise(name: String): Exercise? = read { d -> queryExercises(d, "name=? COLLATE NOCASE", arrayOf(name.trim())).firstOrNull() }

    /** Adds an exercise, or returns the existing one with the same name. */
    suspend fun addExercise(name: String, category: String, equipment: String, loadKind: LoadKind, notes: String = ""): Long =
        write(T_EX) { d -> insertExercise(d, name, category, equipment, loadKind, notes) }

    private fun insertExercise(d: SQLiteDatabase, name: String, category: String, equipment: String, loadKind: LoadKind, notes: String): Long {
        d.rawQuery("SELECT id FROM exercises WHERE name=? COLLATE NOCASE", arrayOf(name.trim())).use { if (it.moveToFirst()) return it.getLong(0) }
        return d.insertOrThrow("exercises", null, ContentValues().apply {
            put("uuid", UUID.randomUUID().toString())
            put("name", name.trim())
            put("category", category)
            put("equipment", equipment)
            put("load_kind", loadKind.name)
            put("notes", notes)
            put("builtin", 0)
            put("created_at", System.currentTimeMillis())
        })
    }

    suspend fun updateExercise(e: Exercise) = write(T_EX, T_ITEMS, T_WE) { d ->
        d.update("exercises", ContentValues().apply {
            put("name", e.name.trim()); put("category", e.category); put("equipment", e.equipment)
            put("load_kind", e.loadKind.name); put("notes", e.notes)
        }, "id=?", arrayOf(e.id.toString()))
    }

    /** Returns false when the exercise is referenced by a program or a logged workout. */
    suspend fun deleteExercise(id: Long): Boolean = write(T_EX) { d ->
        val used = d.rawQuery(
            "SELECT (SELECT COUNT(*) FROM items WHERE exercise_id=?) + (SELECT COUNT(*) FROM workout_exercises WHERE exercise_id=?)",
            arrayOf(id.toString(), id.toString())
        ).single { it.getLong(0) }
        if (used > 0) false else d.delete("exercises", "id=?", arrayOf(id.toString())) > 0
    }

    // ---------- Programs ----------

    fun programs(): Flow<List<Program>> = watch(T_PROG) { d -> d.query("programs", null, null, null, null, null, "updated_at DESC").mapAll(::programOf) }

    fun program(id: Long): Flow<ProgramDetail?> = watch(T_PROG, T_SESS) { d -> loadProgram(d, id) }

    private fun loadProgram(d: SQLiteDatabase, id: Long): ProgramDetail? {
        val p = d.query("programs", null, "id=?", arrayOf(id.toString()), null, null, null).mapAll(::programOf).firstOrNull() ?: return null
        val sessions = d.query("sessions", null, "program_id=?", arrayOf(id.toString()), null, null, "week, day").mapAll(::sessionOf)
        return ProgramDetail(p, sessions)
    }

    private fun programOf(c: Cursor) = Program(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        uuid = c.getString(c.getColumnIndexOrThrow("uuid")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        description = c.getString(c.getColumnIndexOrThrow("description")),
        author = c.getString(c.getColumnIndexOrThrow("author")),
        weeks = c.getInt(c.getColumnIndexOrThrow("weeks")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
    )

    private fun sessionOf(c: Cursor) = Session(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        programId = c.getLong(c.getColumnIndexOrThrow("program_id")),
        week = c.getInt(c.getColumnIndexOrThrow("week")),
        day = c.getInt(c.getColumnIndexOrThrow("day")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
    )

    suspend fun createProgram(name: String, description: String = "", author: String = "", weeks: Int = 4): Long = write(T_PROG) { d ->
        val now = System.currentTimeMillis()
        d.insertOrThrow("programs", null, ContentValues().apply {
            put("uuid", UUID.randomUUID().toString()); put("name", name.trim()); put("description", description)
            put("author", author); put("weeks", weeks.coerceAtLeast(1)); put("created_at", now); put("updated_at", now)
        })
    }

    /** Shrinking the week count deletes the sessions beyond it. */
    suspend fun updateProgram(p: Program) = write(T_PROG, T_SESS) { d ->
        d.update("programs", ContentValues().apply {
            put("name", p.name.trim()); put("description", p.description); put("author", p.author)
            put("weeks", p.weeks.coerceAtLeast(1)); put("updated_at", System.currentTimeMillis())
        }, "id=?", arrayOf(p.id.toString()))
        d.delete("sessions", "program_id=? AND week>?", arrayOf(p.id.toString(), p.weeks.toString()))
    }

    suspend fun deleteProgram(id: Long) = write(T_PROG, T_SESS, T_ASSIGN, T_WO) { d -> d.delete("programs", "id=?", arrayOf(id.toString())) }

    suspend fun duplicateProgram(id: Long, newName: String? = null): Long = write(T_PROG, T_SESS) { d ->
        val src = loadProgram(d, id) ?: error("no program")
        val now = System.currentTimeMillis()
        val newId = d.insertOrThrow("programs", null, ContentValues().apply {
            put("uuid", UUID.randomUUID().toString()); put("name", newName ?: "${src.program.name} (copy)")
            put("description", src.program.description); put("author", src.program.author)
            put("weeks", src.program.weeks); put("created_at", now); put("updated_at", now)
        })
        for (s in src.sessions) copySession(d, s.id, newId, s.week, s.day)
        newId
    }

    private fun copySession(d: SQLiteDatabase, fromSessionId: Long, toProgramId: Long, week: Int, day: Int): Long {
        val detail = loadSession(d, fromSessionId) ?: error("no session")
        d.delete("sessions", "program_id=? AND week=? AND day=?", arrayOf(toProgramId.toString(), week.toString(), day.toString()))
        val newSession = d.insertOrThrow("sessions", null, ContentValues().apply {
            put("program_id", toProgramId); put("week", week); put("day", day)
            put("title", detail.session.title); put("notes", detail.session.notes)
        })
        for (b in detail.blocks) {
            val newBlock = d.insertOrThrow("blocks", null, ContentValues().apply {
                put("session_id", newSession); put("label", b.block.label); put("sort", b.block.sort); put("notes", b.block.notes)
            })
            for (i in b.items) {
                d.insertOrThrow("items", null, ContentValues().apply {
                    put("block_id", newBlock); put("exercise_id", i.exercise.id); put("sort", i.item.sort)
                    put("sets_json", SetRxCodec.toJson(i.item.sets)); put("rest_sec", i.item.restSec); put("notes", i.item.notes)
                })
            }
        }
        return newSession
    }

    /** Copies every session of [fromWeek] onto [toWeek], replacing what was there. */
    suspend fun copyWeek(programId: Long, fromWeek: Int, toWeek: Int) = write(T_SESS) { d ->
        val sessions = d.query("sessions", null, "program_id=? AND week=?", arrayOf(programId.toString(), fromWeek.toString()), null, null, "day").mapAll(::sessionOf)
        d.delete("sessions", "program_id=? AND week=?", arrayOf(programId.toString(), toWeek.toString()))
        for (s in sessions) copySession(d, s.id, programId, toWeek, s.day)
        touchProgram(d, programId)
    }

    /** Copies one session onto another (week, day) slot of the same program. */
    suspend fun copySessionTo(sessionId: Long, week: Int, day: Int) = write(T_SESS) { d ->
        val s = d.query("sessions", null, "id=?", arrayOf(sessionId.toString()), null, null, null).mapAll(::sessionOf).first()
        copySession(d, sessionId, s.programId, week, day)
        touchProgram(d, s.programId)
    }

    private fun touchProgram(d: SQLiteDatabase, programId: Long) {
        d.update("programs", ContentValues().apply { put("updated_at", System.currentTimeMillis()) }, "id=?", arrayOf(programId.toString()))
    }

    // ---------- Sessions, blocks, items ----------

    fun session(id: Long): Flow<SessionDetail?> = watch(T_SESS, T_ITEMS, T_EX) { d -> loadSession(d, id) }

    private fun loadSession(d: SQLiteDatabase, id: Long): SessionDetail? {
        val s = d.query("sessions", null, "id=?", arrayOf(id.toString()), null, null, null).mapAll(::sessionOf).firstOrNull() ?: return null
        val blocks = d.query("blocks", null, "session_id=?", arrayOf(id.toString()), null, null, "sort, id").mapAll { c ->
            Block(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getString(4))
        }
        val details = blocks.map { b ->
            val items = d.rawQuery(
                """SELECT i.id, i.block_id, i.exercise_id, i.sort, i.sets_json, i.rest_sec, i.notes,
                          e.id, e.uuid, e.name, e.category, e.equipment, e.load_kind, e.notes, e.builtin
                   FROM items i JOIN exercises e ON e.id=i.exercise_id WHERE i.block_id=? ORDER BY i.sort, i.id""",
                arrayOf(b.id.toString())
            ).mapAll { c ->
                ItemWithExercise(
                    Item(c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3), SetRxCodec.fromJson(c.getString(4)), c.getInt(5), c.getString(6)),
                    Exercise(c.getLong(7), c.getString(8), c.getString(9), c.getString(10), c.getString(11), LoadKind.parse(c.getString(12)), c.getString(13), c.getInt(14) == 1),
                )
            }
            BlockDetail(b, items)
        }
        return SessionDetail(s, details)
    }

    suspend fun getOrCreateSession(programId: Long, week: Int, day: Int): Long = write(T_SESS) { d ->
        d.rawQuery("SELECT id FROM sessions WHERE program_id=? AND week=? AND day=?", arrayOf(programId.toString(), week.toString(), day.toString()))
            .use { if (it.moveToFirst()) return@write it.getLong(0) }
        touchProgram(d, programId)
        d.insertOrThrow("sessions", null, ContentValues().apply { put("program_id", programId); put("week", week); put("day", day) })
    }

    suspend fun updateSession(s: Session) = write(T_SESS) { d ->
        d.update("sessions", ContentValues().apply { put("title", s.title); put("notes", s.notes) }, "id=?", arrayOf(s.id.toString()))
        touchProgram(d, s.programId)
    }

    suspend fun deleteSession(id: Long) = write(T_SESS) { d -> d.delete("sessions", "id=?", arrayOf(id.toString())) }

    suspend fun addBlock(sessionId: Long): Long = write(T_SESS) { d ->
        val count = d.rawQuery("SELECT COUNT(*) FROM blocks WHERE session_id=?", arrayOf(sessionId.toString())).single { it.getInt(0) }
        d.insertOrThrow("blocks", null, ContentValues().apply {
            put("session_id", sessionId); put("label", ('A' + (count % 26)).toString()); put("sort", count)
        })
    }

    suspend fun updateBlock(b: Block) = write(T_SESS) { d ->
        d.update("blocks", ContentValues().apply { put("label", b.label); put("notes", b.notes) }, "id=?", arrayOf(b.id.toString()))
    }

    suspend fun deleteBlock(id: Long) = write(T_SESS) { d -> d.delete("blocks", "id=?", arrayOf(id.toString())) }

    suspend fun moveBlock(id: Long, up: Boolean) = write(T_SESS) { d ->
        swapSort(d, "blocks", "session_id", id, up)
    }

    /** Adds [exerciseId] to a block with three empty sets by default. */
    suspend fun addItem(blockId: Long, exerciseId: Long, sets: List<SetRx> = List(3) { SetRx("") }, restSec: Int = 90): Long = write(T_SESS, T_ITEMS) { d ->
        val count = d.rawQuery("SELECT COUNT(*) FROM items WHERE block_id=?", arrayOf(blockId.toString())).single { it.getInt(0) }
        d.insertOrThrow("items", null, ContentValues().apply {
            put("block_id", blockId); put("exercise_id", exerciseId); put("sort", count)
            put("sets_json", SetRxCodec.toJson(sets)); put("rest_sec", restSec)
        })
    }

    suspend fun updateItem(i: Item) = write(T_SESS, T_ITEMS) { d ->
        d.update("items", ContentValues().apply {
            put("exercise_id", i.exerciseId); put("sets_json", SetRxCodec.toJson(i.sets)); put("rest_sec", i.restSec); put("notes", i.notes)
        }, "id=?", arrayOf(i.id.toString()))
    }

    suspend fun deleteItem(id: Long) = write(T_SESS, T_ITEMS) { d -> d.delete("items", "id=?", arrayOf(id.toString())) }

    suspend fun moveItem(id: Long, up: Boolean) = write(T_SESS, T_ITEMS) { d -> swapSort(d, "items", "block_id", id, up) }

    /** Swaps the `sort` of a row with its neighbour inside its parent, renumbering first so gaps don't matter. */
    private fun swapSort(d: SQLiteDatabase, table: String, parentCol: String, id: Long, up: Boolean) {
        val parent = d.rawQuery("SELECT $parentCol FROM $table WHERE id=?", arrayOf(id.toString())).single { it.getLong(0) }
        val ids = d.rawQuery("SELECT id FROM $table WHERE $parentCol=? ORDER BY sort, id", arrayOf(parent.toString())).mapAll { it.getLong(0) }.toMutableList()
        val idx = ids.indexOf(id)
        val other = if (up) idx - 1 else idx + 1
        if (idx < 0 || other !in ids.indices) return
        ids[idx] = ids[other].also { ids[other] = ids[idx] }
        ids.forEachIndexed { i, rowId -> d.update(table, ContentValues().apply { put("sort", i) }, "id=?", arrayOf(rowId.toString())) }
    }

    // ---------- Assignments and schedule ----------

    fun assignments(): Flow<List<Assignment>> = watch(T_ASSIGN) { d -> queryAssignments(d) }

    private fun queryAssignments(d: SQLiteDatabase) =
        d.query("assignments", null, null, null, null, null, "start_date DESC").mapAll { c ->
            Assignment(c.getLong(0), c.getLong(1), LocalDate.ofEpochDay(c.getLong(2)), c.getInt(3) == 1)
        }

    suspend fun assign(programId: Long, startDate: LocalDate): Long = write(T_ASSIGN) { d ->
        d.insertOrThrow("assignments", null, ContentValues().apply {
            put("program_id", programId); put("start_date", startDate.toEpochDay()); put("active", 1)
        })
    }

    suspend fun updateAssignment(a: Assignment) = write(T_ASSIGN) { d ->
        d.update("assignments", ContentValues().apply { put("start_date", a.startDate.toEpochDay()); put("active", if (a.active) 1 else 0) }, "id=?", arrayOf(a.id.toString()))
    }

    suspend fun deleteAssignment(id: Long) = write(T_ASSIGN) { d -> d.delete("assignments", "id=?", arrayOf(id.toString())) }

    class Scheduled(val assignment: Assignment, val program: Program, val week: Int, val day: Int, val session: SessionDetail?)

    /** What active programs put on [date]: one entry per program, with a null session on rest days. */
    fun scheduled(date: LocalDate): Flow<List<Scheduled>> = watch(T_ASSIGN, T_PROG, T_SESS, T_ITEMS, T_EX) { d -> loadScheduled(d, date) }

    private fun loadScheduled(d: SQLiteDatabase, date: LocalDate): List<Scheduled> {
        val out = ArrayList<Scheduled>()
        for (a in queryAssignments(d).filter { it.active }) {
            val p = d.query("programs", null, "id=?", arrayOf(a.programId.toString()), null, null, null).mapAll(::programOf).firstOrNull() ?: continue
            val (week, day) = a.slotOn(date, p.weeks) ?: continue
            val sessionId = d.rawQuery("SELECT id FROM sessions WHERE program_id=? AND week=? AND day=?", arrayOf(p.id.toString(), week.toString(), day.toString()))
                .use { if (it.moveToFirst()) it.getLong(0) else null }
            out += Scheduled(a, p, week, day, sessionId?.let { loadSession(d, it) })
        }
        return out
    }

    /** Dates in [from, to] that have a scheduled session or a logged workout, for calendar dots. */
    fun markedDates(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Boolean>> = watch(T_ASSIGN, T_PROG, T_SESS, T_WO) { d ->
        val result = HashMap<LocalDate, Boolean>() // true = workout logged, false = only planned
        d.rawQuery("SELECT DISTINCT date FROM workouts WHERE date BETWEEN ? AND ?", arrayOf(from.toEpochDay().toString(), to.toEpochDay().toString()))
            .mapAll { LocalDate.ofEpochDay(it.getLong(0)) }.forEach { result[it] = true }
        val assignments = queryAssignments(d).filter { it.active }
        val programs = d.query("programs", null, null, null, null, null, null).mapAll(::programOf).associateBy { it.id }
        var day = from
        while (!day.isAfter(to)) {
            if (day !in result) {
                for (a in assignments) {
                    val p = programs[a.programId] ?: continue
                    val (w, dd) = a.slotOn(day, p.weeks) ?: continue
                    val has = d.rawQuery("SELECT 1 FROM sessions WHERE program_id=? AND week=? AND day=?", arrayOf(p.id.toString(), w.toString(), dd.toString())).use { it.moveToFirst() }
                    if (has) { result[day] = false; break }
                }
            }
            day = day.plusDays(1)
        }
        result
    }

    // ---------- Workouts ----------

    fun workoutsOn(date: LocalDate): Flow<List<Workout>> = watch(T_WO) { d ->
        d.query("workouts", null, "date=?", arrayOf(date.toEpochDay().toString()), null, null, "started_at").mapAll(::workoutOf)
    }

    fun workouts(limit: Int = 500): Flow<List<Workout>> = watch(T_WO) { d ->
        d.query("workouts", null, null, null, null, null, "date DESC, started_at DESC", limit.toString()).mapAll(::workoutOf)
    }

    fun workout(id: Long): Flow<WorkoutDetail?> = watch(T_WO, T_WE, T_SETS, T_EX) { d -> loadWorkout(d, id) }

    private fun workoutOf(c: Cursor) = Workout(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        uuid = c.getString(c.getColumnIndexOrThrow("uuid")),
        date = LocalDate.ofEpochDay(c.getLong(c.getColumnIndexOrThrow("date"))),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        programId = c.getLongOrNull("program_id"),
        sessionId = c.getLongOrNull("session_id"),
        startedAt = c.getLong(c.getColumnIndexOrThrow("started_at")),
        finishedAt = c.getLongOrNull("finished_at"),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        bodyWeight = c.getDoubleOrNull("body_weight"),
    )

    private fun loadWorkout(d: SQLiteDatabase, id: Long): WorkoutDetail? {
        val w = d.query("workouts", null, "id=?", arrayOf(id.toString()), null, null, null).mapAll(::workoutOf).firstOrNull() ?: return null
        val entries = d.rawQuery(
            """SELECT we.id, we.workout_id, we.exercise_id, we.block_label, we.sort, we.rest_sec, we.notes,
                      e.id, e.uuid, e.name, e.category, e.equipment, e.load_kind, e.notes, e.builtin
               FROM workout_exercises we JOIN exercises e ON e.id=we.exercise_id WHERE we.workout_id=? ORDER BY we.sort, we.id""",
            arrayOf(id.toString())
        ).mapAll { c ->
            WorkoutExercise(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getInt(4), c.getInt(5), c.getString(6)) to
                Exercise(c.getLong(7), c.getString(8), c.getString(9), c.getString(10), c.getString(11), LoadKind.parse(c.getString(12)), c.getString(13), c.getInt(14) == 1)
        }
        return WorkoutDetail(w, entries.map { (we, e) -> WorkoutExerciseDetail(we, e, querySets(d, we.id)) })
    }

    private fun querySets(d: SQLiteDatabase, weId: Long): List<WorkoutSet> =
        d.query("sets", null, "we_id=?", arrayOf(weId.toString()), null, null, "idx, id").mapAll(::setOf)

    private fun setOf(c: Cursor) = WorkoutSet(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        workoutExerciseId = c.getLong(c.getColumnIndexOrThrow("we_id")),
        index = c.getInt(c.getColumnIndexOrThrow("idx")),
        targetReps = c.getString(c.getColumnIndexOrThrow("target_reps")),
        targetLoad = c.getDoubleOrNull("target_load"),
        targetSeconds = c.getIntOrNull("target_seconds"),
        targetDistance = c.getDoubleOrNull("target_distance"),
        rxKind = RxKind.parse(c.getString(c.getColumnIndexOrThrow("rx_kind"))),
        rxValue = c.getDoubleOrNull("rx_value"),
        reps = c.getIntOrNull("reps"),
        load = c.getDoubleOrNull("load"),
        seconds = c.getIntOrNull("seconds"),
        distance = c.getDoubleOrNull("distance"),
        rpe = c.getDoubleOrNull("rpe"),
        completed = c.getInt(c.getColumnIndexOrThrow("completed")) == 1,
    )

    /** Creates a workout from a programmed session, turning %1RM prescriptions into target loads. */
    suspend fun startFromSession(sessionId: Long, date: LocalDate, unit: String, increment: Double): Long = write(T_WO, T_WE, T_SETS) { d ->
        val s = loadSession(d, sessionId) ?: error("no session")
        val p = d.query("programs", null, "id=?", arrayOf(s.session.programId.toString()), null, null, null).mapAll(::programOf).first()
        val title = s.session.title.ifBlank { "Week ${s.session.week} day ${s.session.day}" }
        val workoutId = insertWorkout(d, date, "${p.name} · $title", p.id, s.session.id)
        var sort = 0
        for (b in s.blocks) for (i in b.items) {
            val weId = d.insertOrThrow("workout_exercises", null, ContentValues().apply {
                put("workout_id", workoutId); put("exercise_id", i.exercise.id); put("block_label", b.block.label)
                put("sort", sort++); put("rest_sec", i.item.restSec); put("notes", i.item.notes)
            })
            val max = currentMaxOf(d, i.exercise.id)
            i.item.sets.forEachIndexed { idx, rx ->
                val targetLoad = when (rx.rxKind) {
                    RxKind.PERCENT -> if (max != null && rx.load != null) Strength.loadForPercent(max, rx.load, unit, increment) else null
                    RxKind.ABSOLUTE -> rx.load
                    else -> null
                }
                d.insertOrThrow("sets", null, ContentValues().apply {
                    put("we_id", weId); put("idx", idx); put("target_reps", rx.reps)
                    targetLoad?.let { put("target_load", it) }
                    rx.seconds?.let { put("target_seconds", it) }
                    rx.distance?.let { put("target_distance", it) }
                    put("rx_kind", rx.rxKind.name); rx.load?.let { put("rx_value", it) }
                })
            }
        }
        workoutId
    }

    suspend fun startEmpty(date: LocalDate, title: String = "Workout"): Long = write(T_WO) { d -> insertWorkout(d, date, title, null, null) }

    private fun insertWorkout(d: SQLiteDatabase, date: LocalDate, title: String, programId: Long?, sessionId: Long?): Long =
        d.insertOrThrow("workouts", null, ContentValues().apply {
            put("uuid", UUID.randomUUID().toString()); put("date", date.toEpochDay()); put("title", title)
            programId?.let { put("program_id", it) }; sessionId?.let { put("session_id", it) }
            put("started_at", System.currentTimeMillis())
        })

    suspend fun updateWorkout(w: Workout) = write(T_WO) { d ->
        d.update("workouts", ContentValues().apply {
            put("date", w.date.toEpochDay()); put("title", w.title); put("notes", w.notes)
            if (w.bodyWeight != null) put("body_weight", w.bodyWeight) else putNull("body_weight")
        }, "id=?", arrayOf(w.id.toString()))
    }

    suspend fun deleteWorkout(id: Long) = write(T_WO, T_WE, T_SETS) { d -> d.delete("workouts", "id=?", arrayOf(id.toString())) }

    class NewRecord(val exercise: Exercise, val e1rm: Double, val previous: Double?)

    /** Marks the workout finished and records any estimated one-rep maxes that beat the current ones. */
    suspend fun finishWorkout(id: Long): List<NewRecord> = write(T_WO, T_MAX) { d ->
        val detail = loadWorkout(d, id) ?: return@write emptyList()
        d.update("workouts", ContentValues().apply { put("finished_at", System.currentTimeMillis()) }, "id=?", arrayOf(id.toString()))
        val records = ArrayList<NewRecord>()
        for (ex in detail.exercises) {
            if (ex.exercise.loadKind != LoadKind.WEIGHT) continue
            val best = ex.sets.filter { it.completed }.mapNotNull { s -> Strength.estimate1rm(s.load ?: return@mapNotNull null, s.reps ?: return@mapNotNull null) }.maxOrNull() ?: continue
            val current = currentMaxOf(d, ex.exercise.id)
            if (current == null || best > current + 0.01) {
                d.insertOrThrow("maxes", null, ContentValues().apply {
                    put("exercise_id", ex.exercise.id); put("value", best); put("date", detail.workout.date.toEpochDay()); put("source", "estimated")
                })
                records += NewRecord(ex.exercise, best, current)
            }
        }
        records
    }

    suspend fun reopenWorkout(id: Long) = write(T_WO) { d ->
        d.update("workouts", ContentValues().apply { putNull("finished_at") }, "id=?", arrayOf(id.toString()))
    }

    suspend fun addWorkoutExercise(workoutId: Long, exerciseId: Long, sets: Int = 3): Long = write(T_WE, T_SETS) { d ->
        val count = d.rawQuery("SELECT COUNT(*) FROM workout_exercises WHERE workout_id=?", arrayOf(workoutId.toString())).single { it.getInt(0) }
        val weId = d.insertOrThrow("workout_exercises", null, ContentValues().apply {
            put("workout_id", workoutId); put("exercise_id", exerciseId); put("sort", count); put("rest_sec", 90)
        })
        repeat(sets) { idx -> d.insertOrThrow("sets", null, ContentValues().apply { put("we_id", weId); put("idx", idx) }) }
        weId
    }

    suspend fun updateWorkoutExercise(we: WorkoutExercise) = write(T_WE) { d ->
        d.update("workout_exercises", ContentValues().apply { put("notes", we.notes); put("rest_sec", we.restSec); put("block_label", we.blockLabel) }, "id=?", arrayOf(we.id.toString()))
    }

    suspend fun deleteWorkoutExercise(id: Long) = write(T_WE, T_SETS) { d -> d.delete("workout_exercises", "id=?", arrayOf(id.toString())) }

    suspend fun moveWorkoutExercise(id: Long, up: Boolean) = write(T_WE) { d -> swapSort(d, "workout_exercises", "workout_id", id, up) }

    /** Appends a set that repeats the previous set's targets and values. */
    suspend fun addSet(weId: Long): Long = write(T_SETS) { d ->
        val last = querySets(d, weId).lastOrNull()
        d.insertOrThrow("sets", null, ContentValues().apply {
            put("we_id", weId); put("idx", (last?.index ?: -1) + 1)
            if (last != null) {
                put("target_reps", last.targetReps); last.targetLoad?.let { put("target_load", it) }
                last.targetSeconds?.let { put("target_seconds", it) }; last.targetDistance?.let { put("target_distance", it) }
                put("rx_kind", last.rxKind.name); last.rxValue?.let { put("rx_value", it) }
                last.reps?.let { put("reps", it) }; last.load?.let { put("load", it) }
                last.seconds?.let { put("seconds", it) }; last.distance?.let { put("distance", it) }
            }
        })
    }

    suspend fun updateSet(s: WorkoutSet) = write(T_SETS) { d ->
        d.update("sets", ContentValues().apply {
            put("target_reps", s.targetReps)
            if (s.targetLoad != null) put("target_load", s.targetLoad) else putNull("target_load")
            if (s.reps != null) put("reps", s.reps) else putNull("reps")
            if (s.load != null) put("load", s.load) else putNull("load")
            if (s.seconds != null) put("seconds", s.seconds) else putNull("seconds")
            if (s.distance != null) put("distance", s.distance) else putNull("distance")
            if (s.rpe != null) put("rpe", s.rpe) else putNull("rpe")
            put("completed", if (s.completed) 1 else 0)
        }, "id=?", arrayOf(s.id.toString()))
    }

    suspend fun deleteSet(id: Long) = write(T_SETS) { d -> d.delete("sets", "id=?", arrayOf(id.toString())) }

    // ---------- Maxes, history, records ----------

    fun maxes(exerciseId: Long): Flow<List<Max>> = watch(T_MAX) { d ->
        d.query("maxes", null, "exercise_id=?", arrayOf(exerciseId.toString()), null, null, "date DESC, id DESC").mapAll(::maxOf)
    }

    fun currentMax(exerciseId: Long): Flow<Double?> = watch(T_MAX) { d -> currentMaxOf(d, exerciseId) }

    private fun maxOf(c: Cursor) = Max(c.getLong(0), c.getLong(1), c.getDouble(2), LocalDate.ofEpochDay(c.getLong(3)), c.getString(4))

    /** The most recent max on record, whatever its source. */
    private fun currentMaxOf(d: SQLiteDatabase, exerciseId: Long): Double? =
        d.rawQuery("SELECT value FROM maxes WHERE exercise_id=? ORDER BY date DESC, id DESC LIMIT 1", arrayOf(exerciseId.toString()))
            .use { if (it.moveToFirst()) it.getDouble(0) else null }

    suspend fun setMax(exerciseId: Long, kg: Double, date: LocalDate = LocalDate.now()) = write(T_MAX) { d ->
        d.insertOrThrow("maxes", null, ContentValues().apply {
            put("exercise_id", exerciseId); put("value", kg); put("date", date.toEpochDay()); put("source", "entered")
        })
    }

    suspend fun deleteMax(id: Long) = write(T_MAX) { d -> d.delete("maxes", "id=?", arrayOf(id.toString())) }

    /** Completed sets of an exercise, newest first. */
    fun history(exerciseId: Long, limit: Int = 300): Flow<List<SetRecord>> = watch(T_SETS, T_WO, T_WE) { d ->
        d.rawQuery(
            """SELECT s.*, w.id AS w_id, w.date AS w_date, w.title AS w_title FROM sets s
               JOIN workout_exercises we ON we.id=s.we_id JOIN workouts w ON w.id=we.workout_id
               WHERE we.exercise_id=? AND s.completed=1 ORDER BY w.date DESC, w.started_at DESC, s.idx LIMIT ?""",
            arrayOf(exerciseId.toString(), limit.toString())
        ).mapAll { c ->
            SetRecord(setOf(c), c.getLong(c.getColumnIndexOrThrow("w_id")), LocalDate.ofEpochDay(c.getLong(c.getColumnIndexOrThrow("w_date"))), c.getString(c.getColumnIndexOrThrow("w_title")))
        }
    }

    /** Every exercise that has a max or a completed set, with its bests. */
    fun personalRecords(): Flow<List<PersonalRecord>> = watch(T_SETS, T_MAX, T_EX) { d ->
        val exercises = queryExercises(d, null, null).associateBy { it.id }
        val bests = HashMap<Long, Triple<Double?, Double?, Int>>()
        d.rawQuery(
            """SELECT we.exercise_id, s.load, s.reps FROM sets s JOIN workout_exercises we ON we.id=s.we_id WHERE s.completed=1""", null
        ).mapAll { c ->
            val exId = c.getLong(0)
            val load = if (c.isNull(1)) null else c.getDouble(1)
            val reps = if (c.isNull(2)) null else c.getInt(2)
            val prev = bests[exId] ?: Triple(null, null, 0)
            val e1 = if (load != null && reps != null) Strength.estimate1rm(load, reps) else null
            bests[exId] = Triple(maxOfNullable(prev.first, load), maxOfNullable(prev.second, e1), prev.third + 1)
        }
        val maxes = HashMap<Long, Max>()
        d.query("maxes", null, null, null, null, null, "date, id").mapAll(::maxOf).forEach { maxes[it.exerciseId] = it }
        (bests.keys + maxes.keys).mapNotNull { id ->
            val e = exercises[id] ?: return@mapNotNull null
            val b = bests[id]
            PersonalRecord(e, b?.first, b?.second, maxes[id], b?.third ?: 0)
        }.sortedWith(compareByDescending<PersonalRecord> { it.max?.value ?: it.bestE1rm ?: 0.0 }.thenBy { it.exercise.name })
    }

    private fun maxOfNullable(a: Double?, b: Double?): Double? = if (a == null) b else if (b == null) a else maxOf(a, b)

    class WeekStat(val weekStart: LocalDate, val workouts: Int, val tonnage: Double, val sets: Int)

    /** Per-week training totals for the last [weeks] weeks (Monday-start), oldest first. */
    fun weeklyStats(weeks: Int = 12): Flow<List<WeekStat>> = watch(T_WO, T_SETS) { d ->
        val thisMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val from = thisMonday.minusWeeks((weeks - 1).toLong())
        val rows = d.rawQuery(
            """SELECT w.id, w.date, COALESCE(s.load,0)*COALESCE(s.reps,0), s.completed FROM workouts w
               LEFT JOIN workout_exercises we ON we.workout_id=w.id LEFT JOIN sets s ON s.we_id=we.id
               WHERE w.date>=? AND w.finished_at IS NOT NULL""",
            arrayOf(from.toEpochDay().toString())
        ).mapAll { c -> Triple(c.getLong(0), LocalDate.ofEpochDay(c.getLong(1)), if (c.isNull(3) || c.getInt(3) == 0) 0.0 else c.getDouble(2)) to (!c.isNull(3) && c.getInt(3) == 1) }
        (0 until weeks).map { i ->
            val start = from.plusWeeks(i.toLong())
            val end = start.plusDays(6)
            val inWeek = rows.filter { !it.first.second.isBefore(start) && !it.first.second.isAfter(end) }
            WeekStat(start, inWeek.map { it.first.first }.distinct().size, inWeek.sumOf { it.first.third }, inWeek.count { it.second })
        }
    }

    class Totals(val workouts: Int, val sets: Int, val tonnage: Double, val exercises: Int, val programs: Int)

    fun totals(): Flow<Totals> = watch(T_WO, T_SETS, T_EX, T_PROG) { d ->
        Totals(
            workouts = d.rawQuery("SELECT COUNT(*) FROM workouts WHERE finished_at IS NOT NULL", null).single { it.getInt(0) },
            sets = d.rawQuery("SELECT COUNT(*) FROM sets WHERE completed=1", null).single { it.getInt(0) },
            tonnage = d.rawQuery("SELECT COALESCE(SUM(COALESCE(load,0)*COALESCE(reps,0)),0) FROM sets WHERE completed=1", null).single { it.getDouble(0) },
            exercises = d.rawQuery("SELECT COUNT(*) FROM exercises", null).single { it.getInt(0) },
            programs = d.rawQuery("SELECT COUNT(*) FROM programs", null).single { it.getInt(0) },
        )
    }

    // ---------- Program files ----------

    suspend fun exportProgram(programId: Long): String? = read { d ->
        val p = loadProgram(d, programId) ?: return@read null
        ProgramJson.export(ProgramJson.ExportedProgram(p.program, p.sessions.mapNotNull { loadSession(d, it.id) }))
    }

    /** Creates a program from a parsed file, matching exercises by name and creating unknown ones. Returns its id. */
    suspend fun importProgram(parsed: ProgramJson.ParsedProgram): Long = write(T_PROG, T_SESS, T_ITEMS, T_EX) { d ->
        val now = System.currentTimeMillis()
        val programId = d.insertOrThrow("programs", null, ContentValues().apply {
            put("uuid", UUID.randomUUID().toString()); put("name", parsed.name); put("description", parsed.description)
            put("author", parsed.author); put("weeks", parsed.weeks); put("created_at", now); put("updated_at", now)
        })
        for (s in parsed.sessions) {
            d.delete("sessions", "program_id=? AND week=? AND day=?", arrayOf(programId.toString(), s.week.toString(), s.day.toString()))
            val sessionId = d.insertOrThrow("sessions", null, ContentValues().apply {
                put("program_id", programId); put("week", s.week); put("day", s.day); put("title", s.title); put("notes", s.notes)
            })
            s.blocks.forEachIndexed { bi, b ->
                val blockId = d.insertOrThrow("blocks", null, ContentValues().apply {
                    put("session_id", sessionId); put("label", b.label); put("sort", bi); put("notes", b.notes)
                })
                b.items.forEachIndexed { ii, i ->
                    val exId = insertExercise(d, i.exercise.name, i.exercise.category, i.exercise.equipment, i.exercise.loadKind, "")
                    d.insertOrThrow("items", null, ContentValues().apply {
                        put("block_id", blockId); put("exercise_id", exId); put("sort", ii)
                        put("sets_json", SetRxCodec.toJson(i.sets)); put("rest_sec", i.restSec); put("notes", i.notes)
                    })
                }
            }
        }
        programId
    }

    companion object {
        const val T_EX = "exercises"
        const val T_PROG = "programs"
        const val T_SESS = "sessions"
        const val T_ITEMS = "items"
        const val T_ASSIGN = "assignments"
        const val T_WO = "workouts"
        const val T_WE = "workout_exercises"
        const val T_SETS = "sets"
        const val T_MAX = "maxes"
    }
}

// ---------- Cursor helpers ----------

inline fun <T> Cursor.mapAll(block: (Cursor) -> T): List<T> = use {
    val out = ArrayList<T>(count)
    while (moveToNext()) out += block(this)
    out
}

inline fun <T> Cursor.single(block: (Cursor) -> T): T = use { moveToFirst(); block(this) }

fun Cursor.getLongOrNull(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }
fun Cursor.getIntOrNull(col: String): Int? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
fun Cursor.getDoubleOrNull(col: String): Double? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
