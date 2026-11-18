package com.sovereignedge.pocketcoach

import com.sovereignedge.pocketcoach.data.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrengthTest {
    @Test
    fun epleyEstimate() {
        assertEquals(100.0, Strength.estimate1rm(100.0, 1)!!, 1e-9)
        assertEquals(116.666, Strength.estimate1rm(100.0, 5)!!, 1e-3)
        assertNull(Strength.estimate1rm(100.0, 0))
        assertNull(Strength.estimate1rm(100.0, 13))
        assertNull(Strength.estimate1rm(0.0, 5))
    }

    @Test
    fun percentRoundsInDisplayUnit() {
        // 75% of 100 kg = 75 kg, already on a 2.5 kg step.
        assertEquals(75.0, Strength.loadForPercent(100.0, 75.0, "kg", 2.5), 1e-9)
        // 72% of 100 kg = 72 kg → 72.5 kg.
        assertEquals(72.5, Strength.loadForPercent(100.0, 72.0, "kg", 2.5), 1e-9)
        // 80% of 315 lb = 252 lb → 250 lb on a 5 lb step, returned in kg.
        val max315lb = Strength.fromUnit(315.0, "lb")
        val kg = Strength.loadForPercent(max315lb, 80.0, "lb", 5.0)
        assertEquals(250.0, Strength.toUnit(kg, "lb"), 1e-6)
    }

    @Test
    fun loadFormatting() {
        assertEquals("100", Strength.fmtLoad(100.0, "kg"))
        assertEquals("102.5", Strength.fmtLoad(102.5, "kg"))
        assertEquals("135", Strength.fmtLoad(Strength.fromUnit(135.0, "lb"), "lb"))
        assertEquals("", Strength.fmtLoad(null, "kg"))
        assertEquals(61.235, Strength.parseLoad("135", "lb")!!, 1e-3)
        assertEquals(60.0, Strength.parseLoad("60,0", "kg")!!, 1e-9)
        assertNull(Strength.parseLoad("", "kg"))
    }

    @Test
    fun seconds() {
        assertEquals(90, Strength.parseSeconds("1:30"))
        assertEquals(90, Strength.parseSeconds("90"))
        assertEquals(90, Strength.parseSeconds("90s"))
        assertEquals(120, Strength.parseSeconds("2m"))
        assertNull(Strength.parseSeconds(""))
        assertEquals("45s", Strength.fmtSeconds(45))
        assertEquals("1:30", Strength.fmtSeconds(90))
    }

    @Test
    fun firstRepsOfPrescription() {
        assertEquals(8, Strength.firstReps("8-12"))
        assertEquals(5, Strength.firstReps("5"))
        assertNull(Strength.firstReps("AMRAP"))
    }
}
