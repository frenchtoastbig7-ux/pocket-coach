package com.sovereignedge.pocketcoach.data

import kotlin.math.roundToInt

/** Strength arithmetic and unit handling. All stored loads are kilograms. */
object Strength {
    const val KG_PER_LB = 0.45359237

    /** Epley estimate of a one-rep max from [load] lifted for [reps]; null when reps are outside the useful range. */
    fun estimate1rm(load: Double, reps: Int): Double? = when {
        reps <= 0 || load <= 0 -> null
        reps == 1 -> load
        reps > 12 -> null
        else -> load * (1 + reps / 30.0)
    }

    /** Load for [percent] of [max], rounded to [increment] in the display unit and returned in kilograms. */
    fun loadForPercent(max: Double, percent: Double, unit: String, increment: Double): Double =
        roundLoad(max * percent / 100.0, unit, increment)

    /** Rounds a kilogram load to the nearest [increment] of the display [unit] (e.g. 2.5 kg or 5 lb). */
    fun roundLoad(kg: Double, unit: String, increment: Double): Double {
        if (increment <= 0) return kg
        val display = toUnit(kg, unit)
        val rounded = (display / increment).roundToInt() * increment
        return fromUnit(rounded, unit)
    }

    fun toUnit(kg: Double, unit: String): Double = if (unit == "lb") kg / KG_PER_LB else kg
    fun fromUnit(value: Double, unit: String): Double = if (unit == "lb") value * KG_PER_LB else value

    /** Formats a kilogram load in the display unit without a unit suffix, to one decimal: 100, 102.5, 135. */
    fun fmtLoad(kg: Double?, unit: String): String {
        if (kg == null) return ""
        val v = toUnit(kg, unit)
        val r = (v * 10).roundToInt() / 10.0
        return if (kotlin.math.abs(r - r.roundToInt()) < 0.05) r.roundToInt().toString()
        else String.format(java.util.Locale.US, "%.1f", r)
    }

    fun fmtLoadUnit(kg: Double?, unit: String): String = if (kg == null) "" else "${fmtLoad(kg, unit)} $unit"

    /** Parses a load typed in the display unit, returning kilograms. */
    fun parseLoad(text: String, unit: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.let { fromUnit(it, unit) }

    fun fmtSeconds(s: Int?): String {
        if (s == null) return ""
        return if (s < 60) "${s}s" else "%d:%02d".format(s / 60, s % 60)
    }

    /** "1:30", "90", "90s", "2m" → seconds. */
    fun parseSeconds(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        if (':' in t) {
            val (m, s) = t.split(':', limit = 2)
            return (m.toIntOrNull() ?: return null) * 60 + (s.toIntOrNull() ?: return null)
        }
        if (t.endsWith("m")) return t.dropLast(1).trim().toDoubleOrNull()?.let { (it * 60).toInt() }
        return t.removeSuffix("s").trim().toDoubleOrNull()?.toInt()
    }

    fun fmtDistance(m: Double?): String {
        if (m == null) return ""
        return if (m >= 1000) "${fmtNum(m / 1000)} km" else "${fmtNum(m)} m"
    }

    fun fmtNum(v: Double): String =
        if (kotlin.math.abs(v - v.roundToInt()) < 0.005) v.roundToInt().toString()
        else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    /** Total tonnage of a set in kilograms. */
    fun volume(set: WorkoutSet): Double = (set.load ?: 0.0) * (set.reps ?: 0)

    /** First number in a reps prescription such as "8-12" or "5"; null for "AMRAP". */
    fun firstReps(rx: String): Int? = Regex("\\d+").find(rx)?.value?.toIntOrNull()
}
