package com.sovereignedge.pocketcoach

import com.sovereignedge.pocketcoach.data.Plates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatesTest {
    @Test
    fun kgLoading() {
        val r = Plates.load(102.5, 20.0, Plates.DEFAULT_KG)
        assertEquals(listOf(25.0, 15.0, 1.25), r.perSide)
        assertTrue(r.exact)
        assertEquals(102.5, r.achieved, 1e-9)
    }

    @Test
    fun lbLoading() {
        val r = Plates.load(225.0, 45.0, Plates.DEFAULT_LB)
        assertEquals(listOf(45.0, 45.0), r.perSide)
        assertTrue(r.exact)
        val r2 = Plates.load(315.0, 45.0, Plates.DEFAULT_LB)
        assertEquals(listOf(45.0, 45.0, 45.0), r2.perSide)
    }

    @Test
    fun inexactAndBelowBar() {
        val r = Plates.load(101.0, 20.0, Plates.DEFAULT_KG)
        assertFalse(r.exact)
        assertEquals(100.0, r.achieved, 1e-9) // 25+15 per side = 100; no 0.5 kg plates by default
        assertEquals(1.0, r.shortBy, 1e-9)
        val empty = Plates.load(15.0, 20.0, Plates.DEFAULT_KG)
        assertEquals(emptyList<Double>(), empty.perSide)
        assertEquals(20.0, empty.achieved, 1e-9)
    }

    @Test
    fun limitedPlates() {
        // Only 10s and 5s: 60 kg = bar + 10 + 10 per side.
        val r = Plates.load(60.0, 20.0, listOf(10.0, 5.0))
        assertEquals(listOf(10.0, 10.0), r.perSide)
    }

    @Test
    fun parseAndFormat() {
        assertEquals(listOf(20.0, 10.0, 2.5), Plates.parseList("10, 2.5 , 20, x"))
        assertEquals("20, 10, 2.5", Plates.formatList(listOf(20.0, 10.0, 2.5)))
    }
}
