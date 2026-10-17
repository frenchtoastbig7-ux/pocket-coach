package com.sovereignedge.pocketcoach

import android.content.Context
import com.sovereignedge.pocketcoach.data.Plates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsSnapshot(
    /** "kg" or "lb". */
    val unit: String,
    /** Rounding increment for prescribed loads, in the display unit. */
    val increment: Double,
    /** "system", "light" or "dark". */
    val themeMode: String,
    /** Default rest between sets, seconds. */
    val defaultRest: Int,
    /** Shown as the author of programs written on this phone. */
    val athleteName: String,
    /** Keep the screen on during a workout. */
    val keepScreenOn: Boolean,
    /** Sound at the end of the rest timer. */
    val restSound: Boolean,
    /** Barbell weight for the plate calculator, in the display unit. */
    val barWeight: Double,
    /** Plates available (one value per pair), in the display unit, heaviest first. */
    val plates: List<Double>,
)

/** Preferences, with a StateFlow so Compose screens recompose on change. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<SettingsSnapshot> = _state
    val current: SettingsSnapshot get() = _state.value

    /** Switching unit also resets the rounding increment to that unit's usual plate step. */
    fun setUnit(value: String) = edit {
        putString(K_UNIT, value)
        putFloat(K_INCREMENT, if (value == "lb") 5f else 2.5f)
        remove(K_BAR); remove(K_PLATES) // back to that unit's usual bar and plates
    }
    fun setBarWeight(value: Double) = edit { putFloat(K_BAR, value.toFloat()) }
    fun setPlates(value: List<Double>) = edit { putString(K_PLATES, Plates.formatList(value)) }
    fun setIncrement(value: Double) = edit { putFloat(K_INCREMENT, value.toFloat()) }
    fun setThemeMode(value: String) = edit { putString(K_THEME, value) }
    fun setDefaultRest(value: Int) = edit { putInt(K_REST, value.coerceIn(0, 3600)) }
    fun setAthleteName(value: String) = edit { putString(K_NAME, value.trim()) }
    fun setKeepScreenOn(value: Boolean) = edit { putBoolean(K_SCREEN_ON, value) }
    fun setRestSound(value: Boolean) = edit { putBoolean(K_REST_SOUND, value) }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = snapshot()
    }

    private fun snapshot() = SettingsSnapshot(
        unit = prefs.getString(K_UNIT, "kg") ?: "kg",
        increment = prefs.getFloat(K_INCREMENT, 2.5f).toDouble(),
        themeMode = prefs.getString(K_THEME, "system") ?: "system",
        defaultRest = prefs.getInt(K_REST, 90),
        athleteName = prefs.getString(K_NAME, "") ?: "",
        keepScreenOn = prefs.getBoolean(K_SCREEN_ON, true),
        restSound = prefs.getBoolean(K_REST_SOUND, true),
        barWeight = run {
            val unit = prefs.getString(K_UNIT, "kg") ?: "kg"
            if (prefs.contains(K_BAR)) prefs.getFloat(K_BAR, 20f).toDouble() else Plates.defaultBar(unit)
        },
        plates = run {
            val unit = prefs.getString(K_UNIT, "kg") ?: "kg"
            prefs.getString(K_PLATES, null)?.let(Plates::parseList)?.takeIf { it.isNotEmpty() } ?: Plates.defaultPlates(unit)
        },
    )

    companion object {
        val UNITS = listOf("kg" to "Kilograms", "lb" to "Pounds")
        val THEME_MODES = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
        private const val K_UNIT = "unit"
        private const val K_INCREMENT = "increment"
        private const val K_THEME = "theme_mode"
        private const val K_REST = "default_rest"
        private const val K_NAME = "athlete_name"
        private const val K_SCREEN_ON = "keep_screen_on"
        private const val K_REST_SOUND = "rest_sound"
        private const val K_BAR = "bar_weight"
        private const val K_PLATES = "plates"
    }
}
