package com.sovereignedge.pocketcoach.data

import org.json.JSONArray
import org.json.JSONObject

/** Serialises set prescriptions for the `sets_json` column and for program files. */
object SetRxCodec {
    fun toJson(sets: List<SetRx>): String = JSONArray().apply { sets.forEach { put(toObj(it)) } }.toString()

    fun toObj(s: SetRx): JSONObject = JSONObject().apply {
        put("reps", s.reps)
        put("rx", s.rxKind.name.lowercase())
        s.load?.let { put("load", it) }
        s.seconds?.let { put("seconds", it) }
        s.distance?.let { put("distance", it) }
    }

    fun fromJson(text: String?): List<SetRx> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            fromArray(JSONArray(text))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fromArray(arr: JSONArray): List<SetRx> = (0 until arr.length()).map { fromObj(arr.getJSONObject(it)) }

    fun fromObj(o: JSONObject): SetRx = SetRx(
        reps = o.optString("reps", ""),
        rxKind = RxKind.parse(o.optString("rx", "none")),
        load = if (o.has("load") && !o.isNull("load")) o.getDouble("load") else null,
        seconds = if (o.has("seconds") && !o.isNull("seconds")) o.getInt("seconds") else null,
        distance = if (o.has("distance") && !o.isNull("distance")) o.getDouble("distance") else null,
    )
}

/**
 * The portable program file: a coach writes a program on one phone, shares the `.json`, and the athlete imports it.
 * Exercises travel by name (plus category/equipment/kind so unknown ones can be created), never by id.
 *
 * ```
 * {"format":"pocketcoach.program","version":1,"program":{"name":..,"weeks":4,"sessions":[{"week":1,"day":1,"title":..,
 *   "blocks":[{"label":"A","items":[{"exercise":{"name":"Back Squat",..},"restSec":180,"sets":[{"reps":"5","rx":"percent","load":75}]}]}]}]}}
 * ```
 */
object ProgramJson {
    const val FORMAT = "pocketcoach.program"
    const val VERSION = 1

    class ExportedProgram(
        val program: Program,
        val sessions: List<SessionDetail>,
    )

    fun export(p: ExportedProgram): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        val prog = JSONObject()
        prog.put("name", p.program.name)
        prog.put("description", p.program.description)
        prog.put("author", p.program.author)
        prog.put("weeks", p.program.weeks)
        val sessions = JSONArray()
        for (s in p.sessions) {
            val so = JSONObject()
            so.put("week", s.session.week)
            so.put("day", s.session.day)
            so.put("title", s.session.title)
            so.put("notes", s.session.notes)
            val blocks = JSONArray()
            for (b in s.blocks) {
                val bo = JSONObject()
                bo.put("label", b.block.label)
                bo.put("notes", b.block.notes)
                val items = JSONArray()
                for (i in b.items) {
                    val io = JSONObject()
                    io.put("exercise", JSONObject().apply {
                        put("name", i.exercise.name)
                        put("category", i.exercise.category)
                        put("equipment", i.exercise.equipment)
                        put("loadKind", i.exercise.loadKind.name.lowercase())
                        if (i.exercise.description.isNotBlank()) put("description", i.exercise.description)
                        if (i.exercise.videoUrl.isNotBlank()) put("video", i.exercise.videoUrl)
                    })
                    io.put("restSec", i.item.restSec)
                    io.put("notes", i.item.notes)
                    io.put("sets", JSONArray().apply { i.item.sets.forEach { put(SetRxCodec.toObj(it)) } })
                    items.put(io)
                }
                bo.put("items", items)
                blocks.put(bo)
            }
            so.put("blocks", blocks)
            sessions.put(so)
        }
        prog.put("sessions", sessions)
        root.put("program", prog)
        return root.toString(2)
    }

    class ParsedExercise(val name: String, val category: String, val equipment: String, val loadKind: LoadKind, val description: String = "", val videoUrl: String = "")
    class ParsedItem(val exercise: ParsedExercise, val restSec: Int, val notes: String, val sets: List<SetRx>)
    class ParsedBlock(val label: String, val notes: String, val items: List<ParsedItem>)
    class ParsedSession(val week: Int, val day: Int, val title: String, val notes: String, val blocks: List<ParsedBlock>)
    class ParsedProgram(val name: String, val description: String, val author: String, val weeks: Int, val sessions: List<ParsedSession>)

    /** Throws IllegalArgumentException with a readable message on anything that isn't a program file. */
    fun parse(text: String): ParsedProgram {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a JSON file")
        }
        require(root.optString("format") == FORMAT) { "Not a Pocket Coach program file" }
        require(root.optInt("version", 1) <= VERSION) { "This file needs a newer version of the app" }
        val prog = root.optJSONObject("program") ?: throw IllegalArgumentException("File has no program")
        val name = prog.optString("name").ifBlank { "Imported program" }
        val sessionsArr = prog.optJSONArray("sessions") ?: JSONArray()
        val sessions = ArrayList<ParsedSession>()
        for (si in 0 until sessionsArr.length()) {
            val so = sessionsArr.getJSONObject(si)
            val blocksArr = so.optJSONArray("blocks") ?: JSONArray()
            val blocks = ArrayList<ParsedBlock>()
            for (bi in 0 until blocksArr.length()) {
                val bo = blocksArr.getJSONObject(bi)
                val itemsArr = bo.optJSONArray("items") ?: JSONArray()
                val items = ArrayList<ParsedItem>()
                for (ii in 0 until itemsArr.length()) {
                    val io = itemsArr.getJSONObject(ii)
                    val eo = io.optJSONObject("exercise") ?: throw IllegalArgumentException("An item has no exercise")
                    val ex = ParsedExercise(
                        name = eo.optString("name").ifBlank { throw IllegalArgumentException("An exercise has no name") },
                        category = eo.optString("category", "Accessory"),
                        equipment = eo.optString("equipment", "Other"),
                        loadKind = LoadKind.parse(eo.optString("loadKind", "weight")),
                        description = eo.optString("description", ""),
                        videoUrl = eo.optString("video", ""),
                    )
                    items += ParsedItem(ex, io.optInt("restSec", 0), io.optString("notes", ""), SetRxCodec.fromArray(io.optJSONArray("sets") ?: JSONArray()))
                }
                blocks += ParsedBlock(bo.optString("label", ('A' + bi).toString()), bo.optString("notes", ""), items)
            }
            val week = so.optInt("week", 1).coerceAtLeast(1)
            val day = so.optInt("day", 1).coerceIn(1, 7)
            sessions += ParsedSession(week, day, so.optString("title", ""), so.optString("notes", ""), blocks)
        }
        val weeks = maxOf(prog.optInt("weeks", 1), sessions.maxOfOrNull { it.week } ?: 1).coerceAtLeast(1)
        return ParsedProgram(name, prog.optString("description", ""), prog.optString("author", ""), weeks, sessions)
    }
}
