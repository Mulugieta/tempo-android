package app.tempo.plan

import app.tempo.data.Kind
import app.tempo.data.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private val monday = LocalDate.parse("2026-11-16")

    private fun workout(date: String, kind: Kind = Kind.RUN, reps: Int = 0) =
        Workout(id = "$date$kind", date = LocalDate.parse(date), kind = kind, minutes = 30, reps = reps)

    private fun profile(
        goal: Goal = Goal.ACTIVE,
        level: Level = Level.BEGINNER,
        days: Int = 3,
        minutes: Int = 30,
        equipment: Equipment = Equipment.NONE,
        lowImpact: Boolean = false,
    ) = Profile(goal, level, days, minutes, equipment, lowImpact)

    @Test
    fun threeDaysForFourWeeksIsTwelveSessions() {
        val plan = Planner.generate(profile(goal = Goal.LOSE_WEIGHT), emptyList(), monday)
        assertEquals(12, plan.sessions.size)
        assertEquals(monday, plan.sessions.first().date)
    }

    @Test
    fun strengthFourDaysUsesUpperLowerSplit() {
        val plan = Planner.generate(
            profile(goal = Goal.STRENGTH, level = Level.INTERMEDIATE, days = 4, minutes = 45, equipment = Equipment.DUMBBELLS),
            emptyList(),
            monday,
        )
        assertEquals(listOf("Lower body", "Upper body", "Lower body", "Upper body"), plan.sessions.take(4).map { it.title })
        assertTrue(plan.sessions.flatMap { it.items }.any { it.name.contains("Dumbbell") })
    }

    @Test
    fun bodyweightPlansNeverUseDumbbells() {
        val plan = Planner.generate(profile(level = Level.ADVANCED, days = 5, minutes = 60), emptyList(), monday)
        val dumbbellMoves = listOf("Dumbbell", "Goblet", "Romanian")
        assertTrue(plan.sessions.flatMap { it.items }.none { item -> dumbbellMoves.any { item.name.contains(it) } })
    }

    @Test
    fun noExerciseRepeatsWithinASession() {
        val plan = Planner.generate(
            profile(goal = Goal.STRENGTH, level = Level.ADVANCED, days = 6, minutes = 75, equipment = Equipment.DUMBBELLS),
            emptyList(),
            monday,
        )
        assertTrue(plan.sessions.all { s -> s.items.map { it.name }.let { it.size == it.toSet().size } })
    }

    @Test
    fun weekFourIsLighterThanWeekThree() {
        val plan = Planner.generate(profile(level = Level.INTERMEDIATE, goal = Goal.STRENGTH, days = 3), emptyList(), monday)
        val w3 = plan.sessions.first { it.week == 3 && it.title == "Full body A" }.items[1].detail
        val w4 = plan.sessions.first { it.week == 4 && it.title == "Full body A" }.items[1].detail
        assertEquals("3 × 14", w3)
        assertEquals("2 × 10", w4)
    }

    @Test
    fun repTargetsComeFromBestCountedSet() {
        val history = listOf(workout("2026-11-10", Kind.SQUAT, reps = 30))
        val plan = Planner.generate(profile(goal = Goal.STRENGTH), history, monday)
        val squat = plan.sessions.first { it.week == 1 }.items.first { it.kind == Kind.SQUAT }
        assertEquals(18, squat.targetReps)
        assertTrue(plan.notes.any { it.contains("squats") })
    }

    @Test
    fun lowActivityHistoryEasesIn() {
        val history = listOf(workout("2026-11-05"), workout("2026-11-12"))
        val plan = Planner.generate(profile(level = Level.INTERMEDIATE, days = 5, minutes = 40), history, monday)
        assertEquals(listOf(2, 3, 5, 5), (1..4).map { w -> plan.sessions.count { it.week == w } })
    }

    @Test
    fun midWeekStartDropsPastDaysButKeepsThisWeek() {
        val wednesday = LocalDate.parse("2026-11-18")
        val plan = Planner.generate(profile(), emptyList(), wednesday)
        assertEquals(wednesday, plan.sessions.first().date)
        assertEquals(2, plan.sessions.count { it.week == 1 })
    }

    @Test
    fun lateWeekStartBeginsNextMonday() {
        val saturday = LocalDate.parse("2026-11-21")
        val plan = Planner.generate(profile(), emptyList(), saturday)
        assertEquals(LocalDate.parse("2026-11-23"), plan.sessions.first().date)
        assertEquals(12, plan.sessions.size)
    }

    @Test
    fun lowImpactPlansAvoidRunningAndJogging() {
        val banned = Regex("\\brun\\b|run-walk|jog|jump", RegexOption.IGNORE_CASE)
        for (goal in Goal.entries) for (level in Level.entries) for (days in 2..6) {
            val plan = Planner.generate(profile(goal, level, days, 45, Equipment.DUMBBELLS, lowImpact = true), emptyList(), monday)
            assertTrue(plan.sessions.flatMap { it.items }.none { banned.containsMatchIn(it.name + " " + it.detail) })
        }
    }

    @Test
    fun everyProfileGivesSensibleSessions() {
        for (goal in Goal.entries) for (level in Level.entries) for (days in 2..6) for (minutes in listOf(20, 30, 45, 60, 75)) {
            val plan = Planner.generate(profile(goal, level, days, minutes), emptyList(), monday)
            assertTrue(plan.sessions.size >= 2)
            assertTrue(plan.sessions.all { it.items.size >= 3 && it.minutes in 1..(minutes + 12) })
            assertEquals(plan.sessions.size, plan.sessions.map { it.id }.toSet().size)
        }
    }
}
