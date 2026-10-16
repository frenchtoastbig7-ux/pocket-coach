package com.sovereignedge.pocketcoach.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Reads a training plan from a spreadsheet (CSV, or the first sheet of an .xlsx) into a [ProgramJson.ParsedProgram].
 *
 * One row per exercise. The header row names the columns; order doesn't matter and names are matched loosely:
 *
 * | Week | Day | Session | Block | Exercise | Sets | Reps | Load | Rest | Notes | Category | Equipment | Type |
 *
 * - **Week**: 1, 2… ("Week 2" is fine). **Day**: 1–7 or Mon/Tue… (1 = Monday). Both carry down when blank.
 * - **Session**: the day's title (optional). **Block**: A, B, C… — rows sharing a block on the same day form a
 *   superset; blank means a new block per row.
 * - **Sets**: a count. **Reps**: "5", "8-12", "AMRAP", or a time ("45s", "1:30") / distance ("40 m", "2 km").
 *   A list ("5/5/5/3/3") gives each set its own value and sets the count.
 * - **Load**: "75%" (of 1RM), "RPE 8", "100 kg", "225 lb", a bare number (in the importer's unit), "BW", or blank.
 *   Lists work here too ("70%/75%/80%").
 * - **Rest**: seconds or m:ss. **Type** (optional): weight, bodyweight, time, distance; otherwise inferred.
 * - **Video** / **Description** (optional): a demo link and how-to text, stored on the exercise when it is created or
 *   when it has none yet.
 *
 * Rows the reader can't make sense of are skipped and reported in [Result.warnings], not fatal.
 */
object SpreadsheetProgram {
    class Result(val program: ProgramJson.ParsedProgram, val warnings: List<String>)

    /** CSV text (comma, semicolon or tab separated; quotes honoured). */
    fun parseCsv(text: String, name: String, unit: String): Result = parseRows(csvRows(text), name, unit)

    /** An .xlsx file's bytes; only the first worksheet is read. */
    fun parseXlsx(bytes: ByteArray, name: String, unit: String): Result = parseRows(xlsxRows(ByteArrayInputStream(bytes)), name, unit)

    private val HEADER_ALIASES = mapOf(
        "week" to listOf("week", "wk", "w"),
        "day" to listOf("day", "d", "weekday", "day of week"),
        "session" to listOf("session", "title", "workout", "session title", "day title", "name"),
        "block" to listOf("block", "group", "superset", "letter"),
        "exercise" to listOf("exercise", "movement", "lift", "exercise name"),
        "sets" to listOf("sets", "set", "no. sets", "number of sets"),
        "reps" to listOf("reps", "rep", "reps/time", "reps or time", "target", "distance", "time", "duration"),
        "load" to listOf("load", "weight", "intensity", "%", "percent", "percentage", "rpe", "kg", "lb", "lbs", "prescription"),
        "rest" to listOf("rest", "rest (s)", "rest sec", "rest seconds", "rest time"),
        "notes" to listOf("notes", "note", "cues", "comment", "comments", "coach notes"),
        "category" to listOf("category", "pattern", "muscle group"),
        "equipment" to listOf("equipment", "implement"),
        "type" to listOf("type", "kind", "load kind", "log", "logs"),
        "video" to listOf("video", "video url", "demo", "link", "url"),
        "description" to listOf("description", "how to", "how-to", "instructions", "technique", "form"),
    )

    private val DAY_NAMES = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    /** The shared parser: [rows] are cells by row, header first. */
    fun parseRows(rows: List<List<String>>, name: String, unit: String): Result {
        val warnings = ArrayList<String>()
        val nonEmpty = rows.filter { r -> r.any { it.isNotBlank() } }
        require(nonEmpty.isNotEmpty()) { "The spreadsheet is empty" }
        val headerIndex = nonEmpty.indexOfFirst { r -> r.any { cell -> columnKey(cell) == "exercise" } }
        require(headerIndex >= 0) { "No header row with an “Exercise” column was found" }
        val header = nonEmpty[headerIndex].map { columnKey(it) }
        fun col(row: List<String>, key: String): String = header.indexOf(key).let { if (it in row.indices) row[it].trim() else "" }

        class Rx(val block: String, val exercise: ProgramJson.ParsedExercise, val sets: List<SetRx>, val rest: Int, val notes: String)
        // (week, day) → title, notes, ordered rows
        val days = LinkedHashMap<Pair<Int, Int>, Triple<String, StringBuilder, ArrayList<Rx>>>()
        var week = 1
        var day = 1
        var lastSession = ""
        for ((i, row) in nonEmpty.drop(headerIndex + 1).withIndex()) {
            val lineNo = headerIndex + i + 2
            val exercise = col(row, "exercise")
            val weekText = col(row, "week")
            val dayText = col(row, "day")
            if (weekText.isNotBlank()) {
                week = parseWeek(weekText) ?: run { warnings += "Row $lineNo: can't read week “$weekText”"; week }
            }
            if (dayText.isNotBlank()) {
                day = parseDay(dayText) ?: run { warnings += "Row $lineNo: can't read day “$dayText”"; day }
            }
            val sessionTitle = col(row, "session")
            if (sessionTitle.isNotBlank()) lastSession = sessionTitle
            if (exercise.isBlank()) {
                // A row that only names a week/day/session still creates (and titles) that day.
                if (weekText.isNotBlank() || dayText.isNotBlank() || sessionTitle.isNotBlank()) {
                    days.getOrPut(week to day) { Triple(lastSession, StringBuilder(), ArrayList()) }
                    if (sessionTitle.isNotBlank()) days[week to day] = days[week to day]!!.let { Triple(sessionTitle, it.second, it.third) }
                    if (col(row, "notes").isNotBlank()) days[week to day]!!.second.append(col(row, "notes")).append('\n')
                }
                continue
            }
            val entry = days.getOrPut(week to day) { Triple(sessionTitle.ifBlank { lastSession }, StringBuilder(), ArrayList()) }
            if (sessionTitle.isNotBlank() && entry.first.isBlank()) days[week to day] = Triple(sessionTitle, entry.second, entry.third)

            val typeText = col(row, "type")
            val repsText = col(row, "reps")
            val loadText = col(row, "load")
            val setsText = col(row, "sets")
            val kind = parseKind(typeText) ?: inferKind(repsText, loadText)
            val sets = try {
                buildSets(setsText, repsText, loadText, kind, unit)
            } catch (e: IllegalArgumentException) {
                warnings += "Row $lineNo (${exercise}): ${e.message}"
                continue
            }
            val rest = col(row, "rest").let { if (it.isBlank()) 0 else Strength.parseSeconds(it) ?: run { warnings += "Row $lineNo: can't read rest “$it”"; 0 } }
            val category = col(row, "category").ifBlank { "Accessory" }
            val equipment = col(row, "equipment").ifBlank { "Other" }
            days[week to day]!!.third += Rx(col(row, "block").uppercase(), ProgramJson.ParsedExercise(exercise, category, equipment, kind, col(row, "description"), col(row, "video")), sets, rest, col(row, "notes"))
        }
        require(days.isNotEmpty()) { "No training days were found" }

        val sessions = days.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).map { (slot, v) ->
            val (title, notes, rxs) = v
            // Consecutive rows with the same non-blank block letter share a block; blank letters get their own.
            val blocks = ArrayList<ProgramJson.ParsedBlock>()
            var current: MutableList<ProgramJson.ParsedItem>? = null
            var currentLabel = ""
            var auto = 0
            for (rx in rxs) {
                val item = ProgramJson.ParsedItem(rx.exercise, rx.rest, rx.notes, rx.sets)
                if (rx.block.isNotBlank() && rx.block == currentLabel && current != null) {
                    current += item
                } else {
                    current = mutableListOf(item)
                    currentLabel = rx.block.ifBlank { nextLabel(auto++, blocks.map { it.label }) }
                    blocks += ProgramJson.ParsedBlock(currentLabel, "", current)
                    if (rx.block.isBlank()) currentLabel = "" // blank rows never merge with the next row
                }
            }
            ProgramJson.ParsedSession(slot.first, slot.second, title, notes.toString().trim(), blocks)
        }
        val weeks = sessions.maxOf { it.week }
        return Result(ProgramJson.ParsedProgram(name, "", "", weeks, sessions), warnings)
    }

    private fun nextLabel(i: Int, used: List<String>): String {
        var n = i
        while (true) {
            val l = ('A' + (n % 26)).toString() + if (n >= 26) (n / 26).toString() else ""
            if (l !in used) return l
            n++
        }
    }

    private fun columnKey(cell: String): String? {
        val c = cell.trim().lowercase().replace(Regex("\\s+"), " ")
        if (c.isEmpty()) return null
        return HEADER_ALIASES.entries.firstOrNull { (_, names) -> c in names }?.key
    }

    fun parseWeek(s: String): Int? = Regex("\\d+").find(s)?.value?.toIntOrNull()?.takeIf { it >= 1 }

    fun parseDay(s: String): Int? {
        val t = s.trim().lowercase()
        DAY_NAMES.forEachIndexed { i, n -> if (t.startsWith(n)) return i + 1 }
        return Regex("\\d+").find(t)?.value?.toIntOrNull()?.takeIf { it in 1..7 }
    }

    private fun parseKind(s: String): LoadKind? = when (s.trim().lowercase()) {
        "weight", "weighted", "load", "w" -> LoadKind.WEIGHT
        "bodyweight", "bw", "body weight", "b" -> LoadKind.BODYWEIGHT
        "time", "timed", "duration", "t" -> LoadKind.TIME
        "distance", "dist", "d" -> LoadKind.DISTANCE
        else -> null
    }

    private val TIME_RX = Regex("^\\d+(\\.\\d+)?\\s*(s|sec|secs|seconds|min|mins|m)$|^\\d+:\\d{2}$")
    private val DIST_RX = Regex("^\\d+(\\.\\d+)?\\s*(m|meters|metres|km|k|mi|miles|yd|yards)$")

    private fun inferKind(reps: String, load: String): LoadKind {
        val first = splitList(reps).firstOrNull()?.trim()?.lowercase() ?: ""
        return when {
            first.matches(TIME_RX) && !first.endsWith("m") || first.matches(Regex("^\\d+:\\d{2}$")) -> LoadKind.TIME
            first.matches(DIST_RX) -> LoadKind.DISTANCE
            first.matches(TIME_RX) -> LoadKind.TIME
            load.trim().lowercase() in setOf("bw", "bodyweight", "body weight") -> LoadKind.BODYWEIGHT
            else -> LoadKind.WEIGHT
        }
    }

    /** "5/5/3", "5, 5, 3", "5;5;3", "5 | 5 | 3". */
    fun splitList(s: String): List<String> = s.split('/', ';', '|', ',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Builds the per-set prescriptions; throws IllegalArgumentException with a short reason. */
    fun buildSets(setsText: String, repsText: String, loadText: String, kind: LoadKind, unit: String): List<SetRx> {
        var reps = splitList(repsText)
        var count = setsText.trim().let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
        // "5x5" or "3 x 8-12" in the reps cell states both; it wins over the sets column.
        if (reps.size == 1) {
            Regex("^(\\d+)\\s*[x×]\\s*(.+)$", RegexOption.IGNORE_CASE).find(reps[0])?.let { m ->
                count = m.groupValues[1].toInt(); reps = listOf(m.groupValues[2].trim())
            }
        }
        val loads = splitList(loadText)
        val n = maxOf(count ?: 0, reps.size, loads.size).let { if (it == 0) throw IllegalArgumentException("no sets, reps or load given") else it }
        return (0 until n).map { i ->
            val r = reps.getOrNull(i) ?: reps.lastOrNull() ?: ""
            val l = loads.getOrNull(i) ?: loads.lastOrNull() ?: ""
            val (rxKind, rxValue) = parseLoad(l, unit)
            when (kind) {
                LoadKind.TIME -> SetRx("", rxKind, rxValue, seconds = parseTime(r) ?: throw IllegalArgumentException("can't read time “$r”"))
                LoadKind.DISTANCE -> SetRx("", rxKind, rxValue, distance = parseDistance(r) ?: throw IllegalArgumentException("can't read distance “$r”"))
                else -> SetRx(r.ifBlank { throw IllegalArgumentException("missing reps") }, rxKind, rxValue)
            }
        }
    }

    private fun parseTime(s: String): Int? {
        val t = s.trim().lowercase()
        Regex("^(\\d+(?:\\.\\d+)?)\\s*(min|mins|m)$").find(t)?.let { return (it.groupValues[1].toDouble() * 60).toInt() }
        return Strength.parseSeconds(t.removeSuffix("sec").removeSuffix("secs").removeSuffix("seconds").trim())
    }

    private fun parseDistance(s: String): Double? {
        val m = Regex("^(\\d+(?:\\.\\d+)?)\\s*([a-z]*)$").find(s.trim().lowercase()) ?: return null
        val v = m.groupValues[1].toDouble()
        return when (m.groupValues[2]) {
            "", "m", "meters", "metres" -> v
            "km", "k" -> v * 1000
            "mi", "miles" -> v * 1609.344
            "yd", "yards" -> v * 0.9144
            else -> null
        }
    }

    /** "75%", "rpe 8", "@8", "100 kg", "225 lb", "100", "bw", "" → prescription kind and value (kg for absolute). */
    fun parseLoad(s: String, unit: String): Pair<RxKind, Double?> {
        val t = s.trim().lowercase().replace(",", ".")
        if (t.isEmpty() || t in setOf("bw", "bodyweight", "body weight", "-", "—", "n/a", "none")) return RxKind.NONE to null
        Regex("^(\\d+(?:\\.\\d+)?)\\s*%$").find(t)?.let { return RxKind.PERCENT to it.groupValues[1].toDouble() }
        Regex("^(?:rpe\\s*|@\\s*)(\\d+(?:\\.\\d+)?)$").find(t)?.let { return RxKind.RPE to it.groupValues[1].toDouble() }
        Regex("^(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|kilos|lb|lbs|pounds)?$").find(t)?.let {
            val v = it.groupValues[1].toDouble()
            val u = when (it.groupValues[2]) { "lb", "lbs", "pounds" -> "lb"; "kg", "kgs", "kilos" -> "kg"; else -> unit }
            return RxKind.ABSOLUTE to Strength.fromUnit(v, u)
        }
        throw IllegalArgumentException("can't read load “$s”")
    }

    // ---------- CSV ----------

    fun csvRows(text: String): List<List<String>> {
        val body = text.removePrefix("﻿")
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                quoted -> if (c == '"') { if (body.getOrNull(i + 1) == '"') { cell.append('"'); i++ } else quoted = false } else cell.append(c)
                c == '"' -> quoted = true
                c == delimiter -> { row += cell.toString(); cell.setLength(0) }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && body.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.setLength(0); rows += row; row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    // ---------- XLSX ----------

    fun xlsxRows(input: InputStream): List<List<String>> {
        var shared: List<String> = emptyList()
        val sheets = sortedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == "xl/sharedStrings.xml" -> shared = parseSharedStrings(zip.readBytes())
                    e.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) -> sheets[e.name] = zip.readBytes()
                }
            }
        }
        val first = sheets.entries.minByOrNull { Regex("\\d+").find(it.key)!!.value.toInt() }?.value
            ?: throw IllegalArgumentException("The workbook has no worksheet")
        return parseSheet(first, shared)
    }

    private fun dom(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = dom(bytes)
        val items = doc.getElementsByTagNameNS("*", "si")
        return (0 until items.length).map { i ->
            val si = items.item(i) as Element
            val ts = si.getElementsByTagNameNS("*", "t")
            (0 until ts.length).joinToString("") { ts.item(it).textContent }
        }
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val doc = dom(bytes)
        val rowNodes = doc.getElementsByTagNameNS("*", "row")
        val rows = ArrayList<List<String>>()
        for (r in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(r) as Element
            val cells = rowEl.getElementsByTagNameNS("*", "c")
            val out = ArrayList<String>()
            for (c in 0 until cells.length) {
                val cell = cells.item(c) as Element
                val col = columnIndex(cell.getAttribute("r"))
                while (out.size < col) out += ""
                val type = cell.getAttribute("t")
                val v = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent ?: ""
                val text = when (type) {
                    "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> cell.getElementsByTagNameNS("*", "t").let { ts -> (0 until ts.length).joinToString("") { ts.item(it).textContent } }
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    else -> v.toDoubleOrNull()?.let { d -> if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString() } ?: v
                }
                if (out.size == col) out += text else out[col] = text
            }
            rows += out
        }
        return rows
    }

    /** "B7" → 1, "AA3" → 26. Cells without a reference land after the previous one. */
    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return Int.MIN_VALUE.coerceAtLeast(0)
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return n - 1
    }

    /** A filled-in example to start from. */
    fun templateCsv(): String = """
        Week,Day,Session,Block,Exercise,Sets,Reps,Load,Rest,Notes
        1,Mon,Lower,A,Back Squat,5,5,70%,3:00,Belt on the last two sets
        1,Mon,,B,Romanian Deadlift,3,8,RPE 7,2:00,
        1,Mon,,C,Walking Lunge,3,10,,1:00,Superset with plank
        1,Mon,,C,Plank,3,45s,,1:00,
        1,Wed,Upper,A,Bench Press,5,5,70%,3:00,
        1,Wed,,B,Barbell Row,4,8,RPE 7,2:00,
        1,Wed,,C,Overhead Press,3,8,RPE 7,1:30,
        1,Wed,,C,Pull-Up,3,AMRAP,BW,1:30,
        1,Fri,Full body,A,Deadlift,3,5/5/3,75%/80%/85%,3:00,Top set heavy
        1,Fri,,B,Front Squat,3,6,RPE 7,2:00,
        1,Fri,,C,Farmer's Carry,3,40 m,32 kg,1:30,
        2,Mon,Lower,A,Back Squat,5,5,75%,3:00,
    """.trimIndent() + "\n"
}
