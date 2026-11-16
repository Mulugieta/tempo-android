package app.tempo.data

import app.tempo.pose.Move
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class Kind(val label: String, val distance: Boolean, val move: Move? = null) {
    RUN("Run", true),
    WALK("Walk", true),
    CYCLE("Cycle", true),
    SWIM("Swim", true),
    STRENGTH("Strength", false),
    HIIT("HIIT", false),
    YOGA("Yoga", false),
    SQUAT("Squats", false, Move.SQUAT),
    PUSHUP("Push-ups", false, Move.PUSHUP),
    CURL("Curls", false, Move.CURL),
    JACK("Jumping jacks", false, Move.JACK),
}

val INTENSITY = listOf("Easy", "Moderate", "Hard")

data class Workout(
    val id: String,
    val date: LocalDate,
    val kind: Kind,
    val minutes: Int,
    val reps: Int = 0,
    val km: Double = 0.0,
    val intensity: Int = 2,
    val note: String = "",
    val camera: Boolean = false,
) {
    val intensityLabel: String get() = INTENSITY[(intensity - 1).coerceIn(0, 2)]
}

object Stats {
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun between(list: List<Workout>, from: LocalDate, to: LocalDate): List<Workout> =
        list.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }

    /** Consecutive days with a workout, ending today (or yesterday if nothing is logged yet today). */
    fun streak(list: List<Workout>, today: LocalDate): Int {
        val days = list.map { it.date }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }
}
