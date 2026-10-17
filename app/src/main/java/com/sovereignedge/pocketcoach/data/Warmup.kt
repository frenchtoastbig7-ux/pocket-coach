package com.sovereignedge.pocketcoach.data

/** Warm-up ramps toward a working load. */
object Warmup {
    class Step(val load: Double, val reps: Int)

    /**
     * A ramp for [workingKg]: the empty bar for 8 (barbell lifts), then 50 % × 5, 70 % × 3 and 85 % × 1, each rounded
     * to [increment] in [unit]. Steps that would land on the bar, repeat the previous step, or crowd the working set
     * are dropped, so light working weights get a short ramp and the empty bar alone when that's all that fits.
     */
    fun ramp(workingKg: Double, unit: String, increment: Double, barKg: Double?): List<Step> {
        if (workingKg <= 0) return emptyList()
        val out = ArrayList<Step>()
        val minGap = Strength.fromUnit(increment, unit) * 0.99
        var last = 0.0
        if (barKg != null && barKg > 0 && workingKg > barKg + minGap) {
            out += Step(barKg, 8)
            last = barKg
        }
        for ((pct, reps) in listOf(0.5 to 5, 0.7 to 3, 0.85 to 1)) {
            val raw = workingKg * pct
            val load = Strength.roundLoad(raw, unit, increment)
            if (barKg != null && load <= barKg + minGap) continue // can't load less than the bar
            if (load <= last + minGap) continue
            if (load >= workingKg - minGap) continue
            out += Step(load, reps)
            last = load
        }
        return out
    }
}
