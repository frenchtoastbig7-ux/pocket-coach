package com.sovereignedge.pocketcoach.data

/** Plate loading arithmetic. Everything here is in the display unit, not kilograms. */
object Plates {
    val DEFAULT_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    val DEFAULT_LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)

    fun defaultPlates(unit: String): List<Double> = if (unit == "lb") DEFAULT_LB else DEFAULT_KG
    fun defaultBar(unit: String): Double = if (unit == "lb") 45.0 else 20.0

    /** Bar options offered as quick picks. */
    fun barChoices(unit: String): List<Double> = if (unit == "lb") listOf(45.0, 35.0, 15.0) else listOf(20.0, 15.0, 10.0)

    class Result(
        /** Plates on one side, heaviest first. */
        val perSide: List<Double>,
        /** Total actually on the bar with those plates. */
        val achieved: Double,
        /** Requested total. */
        val target: Double,
    ) {
        val exact: Boolean get() = kotlin.math.abs(achieved - target) < 0.001
        val shortBy: Double get() = target - achieved
    }

    /**
     * Greedy loading from the heaviest plate down, which is exact whenever every plate is a multiple of the next
     * smaller one (true of the usual kg and lb sets). Loads below the bar give an empty bar.
     */
    fun load(target: Double, bar: Double, plates: List<Double>): Result {
        val sorted = plates.filter { it > 0 }.sortedDescending()
        var remaining = ((target - bar) / 2).coerceAtLeast(0.0)
        val out = ArrayList<Double>()
        for (p in sorted) {
            while (remaining + 1e-9 >= p) {
                out += p
                remaining -= p
            }
        }
        return Result(out, bar + 2 * out.sum(), target)
    }

    /** "20, 10, 2.5" ↔ list; junk is dropped. */
    fun parseList(text: String): List<Double> =
        text.split(',', ';', ' ', '/').mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }.filter { it > 0 }.distinct().sortedDescending()

    fun formatList(plates: List<Double>): String = plates.joinToString(", ") { Strength.fmtNum(it) }

    /** Rough IPF/competition colours for kg plates; everything else by size. */
    fun colourOf(plate: Double, unit: String): Long = if (unit == "kg") when (plate) {
        25.0 -> 0xFFD32F2F
        20.0 -> 0xFF1E5AA8
        15.0 -> 0xFFF9C80E
        10.0 -> 0xFF2E7D32
        5.0 -> 0xFFF5F5F5
        2.5 -> 0xFFE53935
        1.25 -> 0xFF9E9E9E
        else -> 0xFF757575
    } else when {
        plate >= 45 -> 0xFF1E5AA8
        plate >= 35 -> 0xFFF9C80E
        plate >= 25 -> 0xFF2E7D32
        plate >= 10 -> 0xFF424242
        plate >= 5 -> 0xFF757575
        else -> 0xFF9E9E9E
    }

    /** Relative diameter for drawing, 0.35..1. */
    fun sizeOf(plate: Double, plates: List<Double>): Float {
        val max = plates.maxOrNull() ?: plate
        return (0.35 + 0.65 * (plate / max)).toFloat().coerceIn(0.35f, 1f)
    }
}
