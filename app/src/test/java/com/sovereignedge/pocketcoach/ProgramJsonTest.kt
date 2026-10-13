package com.sovereignedge.pocketcoach

import com.sovereignedge.pocketcoach.data.Block
import com.sovereignedge.pocketcoach.data.BlockDetail
import com.sovereignedge.pocketcoach.data.Exercise
import com.sovereignedge.pocketcoach.data.Item
import com.sovereignedge.pocketcoach.data.ItemWithExercise
import com.sovereignedge.pocketcoach.data.LoadKind
import com.sovereignedge.pocketcoach.data.Program
import com.sovereignedge.pocketcoach.data.ProgramJson
import com.sovereignedge.pocketcoach.data.RxKind
import com.sovereignedge.pocketcoach.data.Session
import com.sovereignedge.pocketcoach.data.SessionDetail
import com.sovereignedge.pocketcoach.data.SetRx
import com.sovereignedge.pocketcoach.data.SetRxCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgramJsonTest {
    private val squat = Exercise(1, "u1", "Back Squat", "Squat", "Barbell", LoadKind.WEIGHT, "", true)
    private val plank = Exercise(2, "u2", "Plank", "Core", "Bodyweight", LoadKind.TIME, "", true)

    @Test
    fun setRxRoundTrips() {
        val sets = listOf(SetRx("5", RxKind.PERCENT, 75.0), SetRx("AMRAP", RxKind.NONE), SetRx("", RxKind.ABSOLUTE, 20.0, seconds = 45), SetRx("", RxKind.NONE, distance = 40.0))
        assertEquals(sets, SetRxCodec.fromJson(SetRxCodec.toJson(sets)))
        assertEquals(emptyList<SetRx>(), SetRxCodec.fromJson("not json"))
    }

    @Test
    fun exportThenParse() {
        val program = Program(1, "p", "Test", "desc", "Coach", 2, 0, 0)
        val session = Session(1, 1, 2, 3, "Lower", "go hard")
        val detail = SessionDetail(session, listOf(
            BlockDetail(Block(1, 1, "A", 0, ""), listOf(ItemWithExercise(Item(1, 1, 1, 0, listOf(SetRx("5", RxKind.PERCENT, 80.0)), 180, "belt"), squat))),
            BlockDetail(Block(2, 1, "B", 1, ""), listOf(ItemWithExercise(Item(2, 2, 2, 0, listOf(SetRx("", RxKind.NONE, seconds = 60)), 30, ""), plank))),
        ))
        val text = ProgramJson.export(ProgramJson.ExportedProgram(program, listOf(detail)))
        val parsed = ProgramJson.parse(text)
        assertEquals("Test", parsed.name)
        assertEquals("Coach", parsed.author)
        assertEquals(2, parsed.weeks)
        assertEquals(1, parsed.sessions.size)
        val s = parsed.sessions[0]
        assertEquals(2, s.week)
        assertEquals(3, s.day)
        assertEquals("Lower", s.title)
        assertEquals(2, s.blocks.size)
        val a = s.blocks[0].items[0]
        assertEquals("Back Squat", a.exercise.name)
        assertEquals(LoadKind.WEIGHT, a.exercise.loadKind)
        assertEquals(180, a.restSec)
        assertEquals(listOf(SetRx("5", RxKind.PERCENT, 80.0)), a.sets)
        val b = s.blocks[1].items[0]
        assertEquals(LoadKind.TIME, b.exercise.loadKind)
        assertEquals(60, b.sets[0].seconds)
    }

    @Test
    fun weeksGrowToFitSessions() {
        val text = """{"format":"pocketcoach.program","version":1,"program":{"name":"X","weeks":1,"sessions":[{"week":3,"day":1,"blocks":[]}]}}"""
        assertEquals(3, ProgramJson.parse(text).weeks)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { ProgramJson.parse("{\"hello\":1}") }
        assertThrows(IllegalArgumentException::class.java) { ProgramJson.parse("garbage") }
        assertThrows(IllegalArgumentException::class.java) { ProgramJson.parse("""{"format":"pocketcoach.program","version":99,"program":{}}""") }
    }
}
