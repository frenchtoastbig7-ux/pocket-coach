package com.sovereignedge.pocketcoach

import com.sovereignedge.pocketcoach.data.LoadKind
import com.sovereignedge.pocketcoach.data.RxKind
import com.sovereignedge.pocketcoach.data.SetRx
import com.sovereignedge.pocketcoach.data.SpreadsheetProgram
import com.sovereignedge.pocketcoach.data.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SpreadsheetProgramTest {
    @Test
    fun templateImportsCleanly() {
        val r = SpreadsheetProgram.parseCsv(SpreadsheetProgram.templateCsv(), "Template", "kg")
        assertEquals(emptyList<String>(), r.warnings)
        val p = r.program
        assertEquals("Template", p.name)
        assertEquals(2, p.weeks)
        assertEquals(4, p.sessions.size)
        val mon = p.sessions.first { it.week == 1 && it.day == 1 }
        assertEquals("Lower", mon.title)
        assertEquals(listOf("A", "B", "C"), mon.blocks.map { it.label })
        assertEquals(2, mon.blocks[2].items.size) // lunge + plank superset
        val squat = mon.blocks[0].items[0]
        assertEquals("Back Squat", squat.exercise.name)
        assertEquals(180, squat.restSec)
        assertEquals(List(5) { SetRx("5", RxKind.PERCENT, 70.0) }, squat.sets)
        val plank = mon.blocks[2].items[1]
        assertEquals(LoadKind.TIME, plank.exercise.loadKind)
        assertEquals(45, plank.sets[0].seconds)
        val fri = p.sessions.first { it.week == 1 && it.day == 5 }
        val dl = fri.blocks[0].items[0]
        assertEquals(listOf(SetRx("5", RxKind.PERCENT, 75.0), SetRx("5", RxKind.PERCENT, 80.0), SetRx("3", RxKind.PERCENT, 85.0)), dl.sets)
        val carry = fri.blocks[2].items[0]
        assertEquals(LoadKind.DISTANCE, carry.exercise.loadKind)
        assertEquals(40.0, carry.sets[0].distance!!, 1e-9)
        assertEquals(RxKind.ABSOLUTE, carry.sets[0].rxKind)
        assertEquals(32.0, carry.sets[0].load!!, 1e-9)
        val pullUp = p.sessions.first { it.day == 3 }.blocks[2].items[1]
        assertEquals(LoadKind.BODYWEIGHT, pullUp.exercise.loadKind)
        assertEquals("AMRAP", pullUp.sets[0].reps)
    }

    @Test
    fun looseHeadersCarryDownAndSkipBadRows() {
        val csv = """
            Wk;Day;Movement;Sets;Reps;Weight;Rest
            1;Monday;Squat;3;5;225 lb;90
            ;;Bench;3;8-12;;60
            ;Wed;Deadlift;1;5x3;100;
            ;;Mystery;;;;
            2;Mon;Squat;3;5;;
        """.trimIndent()
        val r = SpreadsheetProgram.parseCsv(csv, "Plan", "kg")
        assertEquals(1, r.warnings.size)
        assertTrue(r.warnings[0].contains("Mystery"))
        val p = r.program
        assertEquals(2, p.weeks)
        assertEquals(3, p.sessions.size)
        val mon = p.sessions.first { it.week == 1 && it.day == 1 }
        assertEquals(2, mon.blocks.size) // blank block letters never merge
        assertEquals(RxKind.ABSOLUTE, mon.blocks[0].items[0].sets[0].rxKind)
        assertEquals(225.0, Strength.toUnit(mon.blocks[0].items[0].sets[0].load!!, "lb"), 1e-6)
        assertEquals(90, mon.blocks[0].items[0].restSec)
        val wed = p.sessions.first { it.week == 1 && it.day == 3 }
        val dl = wed.blocks[0].items[0]
        assertEquals(5, dl.sets.size) // "5x3" in the reps cell
        assertEquals("3", dl.sets[0].reps)
        assertEquals(100.0, dl.sets[0].load!!, 1e-9) // bare number in the importer's unit (kg)
    }

    @Test
    fun videoAndDescriptionColumns() {
        val csv = "Exercise,Sets,Reps,Video,How to\nZercher Squat,3,5,https://example.com/z,Bar in the crook of the elbows\n"
        val e = SpreadsheetProgram.parseCsv(csv, "x", "kg").program.sessions[0].blocks[0].items[0].exercise
        assertEquals("https://example.com/z", e.videoUrl)
        assertEquals("Bar in the crook of the elbows", e.description)
    }

    @Test
    fun loadParsing() {
        assertEquals(RxKind.PERCENT to 72.5, SpreadsheetProgram.parseLoad("72.5%", "kg"))
        assertEquals(RxKind.RPE to 8.0, SpreadsheetProgram.parseLoad("RPE 8", "kg"))
        assertEquals(RxKind.RPE to 7.5, SpreadsheetProgram.parseLoad("@7.5", "kg"))
        assertEquals(RxKind.NONE to null, SpreadsheetProgram.parseLoad("BW", "kg"))
        assertEquals(RxKind.NONE to null, SpreadsheetProgram.parseLoad("", "kg"))
        val lb = SpreadsheetProgram.parseLoad("135", "lb")
        assertEquals(RxKind.ABSOLUTE, lb.first)
        assertEquals(135.0, Strength.toUnit(lb.second!!, "lb"), 1e-6)
        assertThrows(IllegalArgumentException::class.java) { SpreadsheetProgram.parseLoad("heavy", "kg") }
    }

    @Test
    fun rejectsSheetsWithoutExerciseColumn() {
        assertThrows(IllegalArgumentException::class.java) { SpreadsheetProgram.parseCsv("a,b,c\n1,2,3\n", "x", "kg") }
        assertThrows(IllegalArgumentException::class.java) { SpreadsheetProgram.parseCsv("", "x", "kg") }
    }

    @Test
    fun quotedCsvCells() {
        val csv = "Exercise,Sets,Reps,Notes\n\"Farmer's Carry\",3,\"40 m\",\"Heavy, \"\"but\"\" clean\"\n"
        val rows = SpreadsheetProgram.csvRows(csv)
        assertEquals(listOf("Farmer's Carry", "3", "40 m", "Heavy, \"but\" clean"), rows[1])
        val p = SpreadsheetProgram.parseCsv(csv, "x", "kg").program
        assertEquals("Heavy, \"but\" clean", p.sessions[0].blocks[0].items[0].notes)
    }

    @Test
    fun readsXlsx() {
        // A minimal workbook: shared strings for text, inline numbers, one cell missing (C3 skipped).
        val shared = listOf("Week", "Day", "Exercise", "Sets", "Reps", "Load", "Mon", "Back Squat", "75%", "Bench Press", "RPE 8")
        val sst = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${shared.size}" uniqueCount="${shared.size}">""")
            shared.forEach { append("<si><t>$it</t></si>") }
            append("</sst>")
        }
        fun s(ref: String, i: Int) = """<c r="$ref" t="s"><v>$i</v></c>"""
        fun n(ref: String, v: String) = """<c r="$ref"><v>$v</v></c>"""
        val sheet = """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1">${s("A1", 0)}${s("B1", 1)}${s("C1", 2)}${s("D1", 3)}${s("E1", 4)}${s("F1", 5)}</row>
            <row r="2">${n("A2", "1")}${s("B2", 6)}${s("C2", 7)}${n("D2", "5")}${n("E2", "5")}${s("F2", 8)}</row>
            <row r="3">${s("C3", 9)}${n("D3", "3")}${n("E3", "8")}${s("F3", 10)}</row>
            </sheetData></worksheet>"""
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("xl/workbook.xml")); zip.write("<workbook/>".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml")); zip.write(sst.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet.toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        val r = SpreadsheetProgram.parseXlsx(bytes, "Book", "kg")
        assertEquals(emptyList<String>(), r.warnings)
        val mon = r.program.sessions.single()
        assertEquals(1, mon.week); assertEquals(1, mon.day)
        assertEquals(listOf("Back Squat", "Bench Press"), mon.blocks.map { it.items[0].exercise.name })
        assertEquals(List(5) { SetRx("5", RxKind.PERCENT, 75.0) }, mon.blocks[0].items[0].sets)
        assertEquals(List(3) { SetRx("8", RxKind.RPE, 8.0) }, mon.blocks[1].items[0].sets)
    }
}
