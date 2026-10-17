package com.sovereignedge.pocketcoach

import com.sovereignedge.pocketcoach.data.Strength
import com.sovereignedge.pocketcoach.data.Warmup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupTest {
    @Test
    fun kgRampForHeavySquat() {
        val steps = Warmup.ramp(140.0, "kg", 2.5, 20.0)
        assertEquals(listOf(20.0, 70.0, 97.5, 120.0), steps.map { it.load })
        assertEquals(listOf(8, 5, 3, 1), steps.map { it.reps })
    }

    @Test
    fun lbRampForBench() {
        val kg = Strength.fromUnit(165.0, "lb")
        val steps = Warmup.ramp(kg, "lb", 5.0, Strength.fromUnit(45.0, "lb"))
        assertEquals(listOf(45.0, 85.0, 115.0, 140.0), steps.map { Math.round(Strength.toUnit(it.load, "lb")).toDouble() })
    }

    @Test
    fun lightWeightsGetShortRamp() {
        // 40 kg: 50 % = 20 (the bar, dropped), 70 % = 27.5, 85 % = 35 → bar, 27.5, 35 (35 < 40 - 2.5 so it stays).
        val steps = Warmup.ramp(40.0, "kg", 2.5, 20.0)
        assertEquals(listOf(20.0, 27.5, 35.0), steps.map { it.load })
        // 22.5 kg: only the empty bar fits.
        assertEquals(listOf(20.0), Warmup.ramp(22.5, "kg", 2.5, 20.0).map { it.load })
        // 21 kg is within one plate step of the bar: nothing to ramp.
        assertTrue(Warmup.ramp(21.0, "kg", 2.5, 20.0).isEmpty())
        assertTrue(Warmup.ramp(0.0, "kg", 2.5, 20.0).isEmpty())
    }

    @Test
    fun noBarForDumbbells() {
        val steps = Warmup.ramp(30.0, "kg", 2.5, null)
        assertEquals(listOf(15.0, 20.0, 25.0), steps.map { it.load })
    }
}
