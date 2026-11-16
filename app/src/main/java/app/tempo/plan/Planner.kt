package app.tempo.plan

import app.tempo.data.Kind
import app.tempo.data.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

enum class Goal(val label: String) {
    STRENGTH("Build strength"),
    LOSE_WEIGHT("Lose weight"),
    ENDURANCE("Build endurance"),
    ACTIVE("Stay active"),
}

enum class Level(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

enum class Equipment(val label: String) {
    NONE("Bodyweight only"),
    DUMBBELLS("Dumbbells or bands"),
}

data class Profile(
    val goal: Goal,
    val level: Level,
    val days: Int,
    val minutes: Int,
    val equipment: Equipment,
    val lowImpact: Boolean,
)

/** One line in a session. [kind] is set only for moves the camera can count. */
data class PlanItem(
    val name: String,
    val detail: String,
    val minutes: Int,
    val kind: Kind? = null,
    val targetReps: Int = 0,
    val note: String = "",
)

data class PlanSession(
    val id: String,
    val week: Int,
    val date: LocalDate,
    val title: String,
    val minutes: Int,
    val items: List<PlanItem>,
    val done: Boolean = false,
)

data class Plan(
    val profile: Profile,
    val created: LocalDate,
    val sessions: List<PlanSession>,
    val notes: List<String>,
) {
    /** The soonest unfinished session from today onward. */
    fun nextUp(today: LocalDate): PlanSession? =
        sessions.filter { !it.done && !it.date.isBefore(today) }.minByOrNull { it.date }
}

/**
 * Builds a four-week plan on the phone, no network or account needed.
 *
 * The plan adapts to the person in four ways: the goal picks the weekly mix of strength and
 * cardio, level and week set sets, reps and intensity (week 4 is a lighter week), the time
 * budget decides how many exercises fit, and logged history sets a gentler start and personal
 * rep targets (from the best camera-counted set of each exercise).
 */
object Planner {
    const val WEEKS = 4

    fun generate(profile: Profile, history: List<Workout>, today: LocalDate): Plan {
        val days = profile.days.coerceIn(2, 6)
        val notes = mutableListOf<String>()

        // History: how active the person has really been in the last four weeks.
        val recent = history.filter { !it.date.isBefore(today.minusDays(27)) && !it.date.isAfter(today) }
        val perWeek = recent.size / 4.0
        val startDays =
            if (recent.isNotEmpty() && perWeek + 1.0 < days) (ceil(perWeek).toInt() + 1).coerceIn(2, days) else days
        val weekDays = listOf(startDays, min(days, startDays + 1), days, days)
        if (startDays < days) {
            notes += "You have averaged ${"%.1f".format(perWeek)} workouts a week lately, so week 1 has " +
                "$startDays sessions and builds to $days by week 3."
        }

        val best: Map<Kind, Int> = history
            .filter { it.reps > 0 && it.kind.move != null }
            .groupBy { it.kind }
            .mapValues { entry -> entry.value.maxOf { it.reps } }

        // Calendar: weeks run Monday to Sunday. Start this week if at least two sessions are left in it.
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstWeekLeft = DAY_SLOTS.getValue(weekDays[0]).count { !thisMonday.plusDays(it.toLong()).isBefore(today) }
        val start = if (firstWeekLeft >= 2) thisMonday else thisMonday.plusWeeks(1)

        val builder = Builder(profile, best)
        val occurrences = mutableMapOf<Type, Int>()
        val sessions = mutableListOf<PlanSession>()

        for (week in 1..WEEKS) {
            val n = weekDays[week - 1]
            val types = pattern(profile.goal, n)
            val slots = DAY_SLOTS.getValue(n)
            var cardioIdx = 0
            for (i in 0 until n) {
                val type = types[i]
                val date = start.plusWeeks(week - 1L).plusDays(slots[i].toLong())
                val thisCardio = cardioIdx
                if (type == Type.CARDIO) cardioIdx++
                if (date.isBefore(today)) continue
                val variant = occurrences.getOrDefault(type, 0)
                occurrences[type] = variant + 1
                val items = builder.session(type, week, variant, thisCardio)
                sessions += PlanSession(
                    id = "w$week-$date-$i",
                    week = week,
                    date = date,
                    title = type.title,
                    minutes = items.sumOf { it.minutes },
                    items = items,
                )
            }
        }

        if (builder.personalized.isNotEmpty()) {
            notes += "Rep targets for ${builder.personalized.joinToString(", ")} are set from your best counted sets."
        }
        notes += "Weeks 1 to 3 build up gradually. Week 4 is lighter so your body can recover."
        if (profile.lowImpact) notes += "Low impact: no running or jumping. Cardio is walking based."
        if (start != thisMonday) notes += "Your plan starts next Monday, ${start}."

        return Plan(profile, today, sessions, notes)
    }

    // ---- structure -------------------------------------------------------------------------

    private enum class Type(val title: String) {
        FULL_A("Full body A"),
        FULL_B("Full body B"),
        LOWER("Lower body"),
        UPPER("Upper body"),
        CARDIO("Cardio"),
        CORE("Core and mobility"),
    }

    private enum class Pat { SQUAT, LUNGE, HINGE, PUSH, PULL, CORE }

    private enum class Measure { REPS, EACH_SIDE, SECONDS }

    private class Ex(
        val name: String,
        val pat: Pat,
        val measure: Measure,
        val base: Int,
        val dumbbells: Boolean = false,
        val minLevel: Level = Level.BEGINNER,
        val kind: Kind? = null,
    )

    private val LIB = listOf(
        Ex("Bodyweight squat", Pat.SQUAT, Measure.REPS, 12, kind = Kind.SQUAT),
        Ex("Goblet squat", Pat.SQUAT, Measure.REPS, 10, dumbbells = true),
        Ex("Reverse lunge", Pat.LUNGE, Measure.EACH_SIDE, 8),
        Ex("Split squat", Pat.LUNGE, Measure.EACH_SIDE, 8),
        Ex("Glute bridge", Pat.HINGE, Measure.REPS, 12),
        Ex("Romanian deadlift", Pat.HINGE, Measure.REPS, 10, dumbbells = true),
        Ex("Single-leg glute bridge", Pat.HINGE, Measure.EACH_SIDE, 8, minLevel = Level.INTERMEDIATE),
        Ex("Push-up", Pat.PUSH, Measure.REPS, 8, kind = Kind.PUSHUP),
        Ex("Dumbbell shoulder press", Pat.PUSH, Measure.REPS, 10, dumbbells = true),
        Ex("Pike push-up", Pat.PUSH, Measure.REPS, 6, minLevel = Level.INTERMEDIATE),
        Ex("Dumbbell row", Pat.PULL, Measure.REPS, 10, dumbbells = true),
        Ex("Dumbbell curl", Pat.PULL, Measure.REPS, 10, dumbbells = true, kind = Kind.CURL),
        Ex("Prone Y-T raise", Pat.PULL, Measure.REPS, 10),
        Ex("Superman hold", Pat.PULL, Measure.SECONDS, 20),
        Ex("Plank", Pat.CORE, Measure.SECONDS, 25),
        Ex("Dead bug", Pat.CORE, Measure.EACH_SIDE, 8),
        Ex("Bicycle crunch", Pat.CORE, Measure.REPS, 16),
    )

    /** Weekday slots by sessions per week (0 = Monday). */
    private val DAY_SLOTS = mapOf(
        2 to listOf(0, 3),
        3 to listOf(0, 2, 4),
        4 to listOf(0, 1, 3, 4),
        5 to listOf(0, 1, 2, 4, 5),
        6 to listOf(0, 1, 2, 3, 4, 5),
    )

    private fun pattern(goal: Goal, n: Int): List<Type> {
        val a = Type.FULL_A
        val b = Type.FULL_B
        val c = Type.CARDIO
        val core = Type.CORE
        return when (goal) {
            Goal.STRENGTH -> when (n) {
                2 -> listOf(a, b)
                3 -> listOf(a, b, a)
                4 -> listOf(Type.LOWER, Type.UPPER, Type.LOWER, Type.UPPER)
                5 -> listOf(Type.LOWER, Type.UPPER, c, Type.LOWER, Type.UPPER)
                else -> listOf(Type.LOWER, Type.UPPER, c, Type.LOWER, Type.UPPER, core)
            }
            Goal.LOSE_WEIGHT -> when (n) {
                2 -> listOf(a, c)
                3 -> listOf(a, c, b)
                4 -> listOf(a, c, b, c)
                5 -> listOf(a, c, b, c, core)
                else -> listOf(a, c, b, c, Type.UPPER, c)
            }
            Goal.ENDURANCE -> when (n) {
                2 -> listOf(c, a)
                3 -> listOf(c, a, c)
                4 -> listOf(c, a, c, c)
                5 -> listOf(c, a, c, b, c)
                else -> listOf(c, a, c, core, c, b)
            }
            Goal.ACTIVE -> when (n) {
                2 -> listOf(a, c)
                3 -> listOf(a, c, core)
                4 -> listOf(a, c, b, core)
                5 -> listOf(a, c, b, c, core)
                else -> listOf(a, c, b, c, core, c)
            }
        }
    }

    private fun slots(t: Type): List<Pat> = when (t) {
        Type.FULL_A -> listOf(Pat.SQUAT, Pat.PUSH, Pat.PULL, Pat.HINGE, Pat.CORE)
        Type.FULL_B -> listOf(Pat.LUNGE, Pat.PULL, Pat.PUSH, Pat.HINGE, Pat.CORE)
        Type.LOWER -> listOf(Pat.SQUAT, Pat.HINGE, Pat.LUNGE, Pat.HINGE, Pat.CORE)
        Type.UPPER -> listOf(Pat.PUSH, Pat.PULL, Pat.PUSH, Pat.PULL, Pat.CORE)
        Type.CORE -> listOf(Pat.CORE, Pat.CORE, Pat.CORE, Pat.PULL)
        Type.CARDIO -> emptyList()
    }

    // ---- progression -----------------------------------------------------------------------

    private val WEEK_FACTOR = listOf(1.0, 1.1, 1.2, 0.8)
    private val CARDIO_FACTOR = listOf(0.85, 0.95, 1.0, 0.75)
    private val RUN_MINUTES = listOf(1, 2, 3, 2)

    private fun setsFor(level: Level, week: Int): Int = when (level) {
        Level.BEGINNER -> listOf(2, 2, 3, 2)
        Level.INTERMEDIATE -> listOf(3, 3, 3, 2)
        Level.ADVANCED -> listOf(3, 4, 4, 3)
    }[week - 1]

    private fun levelFactor(level: Level): Double = when (level) {
        Level.BEGINNER -> 0.8
        Level.INTERMEDIATE -> 1.0
        Level.ADVANCED -> 1.25
    }

    // ---- session building ------------------------------------------------------------------

    private class Builder(val p: Profile, val best: Map<Kind, Int>) {
        val personalized = linkedSetOf<String>()

        private val warmUp = PlanItem("Mobility warm-up", "5 min: arm circles, hip circles, leg swings, cat-cow", 5)

        fun session(type: Type, week: Int, variant: Int, cardioIdx: Int): List<PlanItem> =
            if (type == Type.CARDIO) cardio(week, cardioIdx) else strength(type, week, variant)

        private fun strength(type: Type, week: Int, variant: Int): List<PlanItem> {
            val items = mutableListOf(warmUp)
            var total = warmUp.minutes
            val used = mutableSetOf<String>()
            for (pat in slots(type)) {
                val ex = choose(pat, variant, used) ?: continue
                val item = strengthItem(ex, week)
                // Always keep warm-up plus three exercises, then stop when the time budget is full.
                if (total + item.minutes > p.minutes && items.size >= 4) break
                items += item
                total += item.minutes
                used += ex.name
            }
            val left = p.minutes - total
            if (type == Type.CORE) {
                val m = left.coerceIn(5, 10)
                items += PlanItem("Mobility finish", "$m min: hip flexor, hamstring, chest and spine rotation stretches", m)
            } else if (left >= 6) {
                val m = min(left, 12)
                val easy = p.lowImpact || p.level == Level.BEGINNER
                items += PlanItem(
                    "Cardio finisher",
                    if (easy) "$m min brisk walk or easy march in place" else "$m min: 30 s fast, 60 s easy, repeat",
                    m,
                )
            }
            return items
        }

        private fun choose(pat: Pat, variant: Int, used: Set<String>): Ex? {
            var list = LIB.filter {
                it.pat == pat &&
                    (p.equipment == Equipment.DUMBBELLS || !it.dumbbells) &&
                    it.minLevel.ordinal <= p.level.ordinal
            }
            if (p.equipment == Equipment.DUMBBELLS) list = list.sortedByDescending { it.dumbbells }
            if (list.isEmpty()) return null
            val shift = variant % list.size
            val rotated = list.drop(shift) + list.take(shift)
            return rotated.firstOrNull { it.name !in used }
        }

        private fun strengthItem(ex: Ex, week: Int): PlanItem {
            val sets = setsFor(p.level, week)
            val wf = WEEK_FACTOR[week - 1]
            var amount = when (ex.measure) {
                Measure.SECONDS -> (((ex.base * levelFactor(p.level) * wf) / 5).roundToInt() * 5).coerceAtLeast(15)
                Measure.EACH_SIDE -> (ex.base * levelFactor(p.level) * wf).roundToInt().coerceAtLeast(4)
                Measure.REPS -> (ex.base * levelFactor(p.level) * wf).roundToInt().coerceAtLeast(5)
            }
            var note = ""
            val k = ex.kind
            val bestReps = if (k != null) best[k] else null
            if (k != null && bestReps != null && ex.measure == Measure.REPS) {
                amount = (bestReps * 0.6 * wf).roundToInt().coerceIn(5, 40)
                note = "Based on your best set of $bestReps"
                personalized += k.label.lowercase()
            }
            val name = if (p.level == Level.BEGINNER && ex.name == "Push-up") "Knee push-up" else ex.name
            val detail = when (ex.measure) {
                Measure.REPS -> "$sets × $amount"
                Measure.EACH_SIDE -> "$sets × $amount each side"
                Measure.SECONDS -> "$sets × $amount s"
            }
            val workSec = when (ex.measure) {
                Measure.REPS -> amount * 3
                Measure.EACH_SIDE -> amount * 6
                Measure.SECONDS -> amount
            }
            val minutes = ceil(sets * (workSec + 45) / 60.0).toInt()
            return PlanItem(
                name = name,
                detail = detail,
                minutes = minutes,
                kind = if (ex.measure == Measure.REPS) k else null,
                targetReps = if (k != null && ex.measure == Measure.REPS) amount else 0,
                note = note,
            )
        }

        private fun cardio(week: Int, cardioIdx: Int): List<PlanItem> {
            val base = (p.minutes - 10).coerceAtLeast(10)
            val main = (base * CARDIO_FACTOR[week - 1]).roundToInt().coerceAtLeast(10)
            val mainItem: PlanItem = when {
                p.lowImpact && cardioIdx == 0 && p.level != Level.BEGINNER ->
                    PlanItem("Brisk walk with pickups", "$main min brisk walk, 1 min faster every 5 min", main)
                p.lowImpact ->
                    PlanItem("Brisk walk", "$main min at a pace where you can speak in short sentences", main)
                p.level == Level.BEGINNER -> {
                    val run = RUN_MINUTES[week - 1]
                    val n = (main / (run + 2)).coerceAtLeast(1)
                    PlanItem("Run-walk intervals", "$n × (run $run min, walk 2 min)", n * (run + 2))
                }
                cardioIdx == 0 -> {
                    val hard = if (p.level == Level.ADVANCED) 2 else 1
                    val n = (main / (hard + 2)).coerceAtLeast(2)
                    PlanItem("Interval run", "$n × ($hard min hard, 2 min easy)", n * (hard + 2))
                }
                else -> PlanItem("Steady easy run", "$main min at a conversational pace", main)
            }
            return listOf(
                PlanItem(
                    "Easy warm-up",
                    if (p.lowImpact) "5 min easy walk, building to a brisk walk" else "5 min easy walk, building to a gentle jog",
                    5,
                ),
                mainItem,
                PlanItem("Cool-down", "5 min easy walk, then calf, quad and hamstring stretches", 5),
            )
        }
    }
}
