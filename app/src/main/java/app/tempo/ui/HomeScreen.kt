package app.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tempo.data.Stats
import app.tempo.data.Workout
import app.tempo.plan.Plan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    workouts: List<Workout>,
    goal: Int,
    onGoal: (Int) -> Unit,
    plan: Plan?,
    onPlan: () -> Unit,
    onCamera: () -> Unit,
    onManual: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val today = LocalDate.now()
    val weekStart = Stats.weekStart(today)
    val week = Stats.between(workouts, weekStart, weekStart.plusDays(6))
    val minutes = week.sumOf { it.minutes }
    val daysLeft = 6 - (today.dayOfWeek.value - 1)
    val sorted = remember(workouts) {
        workouts.sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.id })
    }
    var pending by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Tempo", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "ACTIVE MINUTES THIS WEEK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$minutes", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "  / $goal min",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (minutes.toFloat() / goal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        if (minutes >= goal) "Goal reached." else
                            "${goal - minutes} min to go, ${if (daysLeft > 0) "$daysLeft days left" else "last day"} this week.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Stat("Sessions", "${week.size}")
                        Stat("Streak", "${Stats.streak(workouts, today)} d")
                        Stat("Reps", "${week.sumOf { it.reps }}")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Weekly goal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f))
                        TextButton(onClick = { onGoal((goal - 10).coerceAtLeast(10)) }) { Text("-10") }
                        TextButton(onClick = { onGoal((goal + 10).coerceAtMost(2000)) }) { Text("+10") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCamera, modifier = Modifier.weight(1f)) { Text("Count with camera") }
                OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) { Text("Log manually") }
            }
        }

        item { PlanCard(plan, today, onPlan) }

        item { WeekChart(workouts, today) }

        item { Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

        if (sorted.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet. Count a set with the camera or log a workout by hand.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(sorted, key = { it.id }) { w ->
            val sure = pending == w.id
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${w.kind.label}  ·  ${dayLabel(w.date, today)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(details(w), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        if (w.note.isNotBlank()) Text(w.note, fontSize = 13.sp)
                    }
                    TextButton(onClick = {
                        if (sure) {
                            pending = null
                            onDelete(w.id)
                        } else {
                            pending = w.id
                        }
                    }) {
                        Text(
                            if (sure) "Confirm" else "Delete",
                            color = if (sure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan?, today: LocalDate, onPlan: () -> Unit) {
    val next = plan?.nextUp(today)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                when {
                    plan == null -> {
                        Text("Workout plan", fontWeight = FontWeight.Bold)
                        Text("Get a 4 week plan built around your goal and schedule.", fontSize = 13.sp)
                    }
                    next != null -> {
                        Text("Up next: ${next.title}", fontWeight = FontWeight.Bold)
                        Text("${dayLabel(next.date, today)} · about ${next.minutes} min", fontSize = 13.sp)
                    }
                    else -> {
                        Text("Your plan", fontWeight = FontWeight.Bold)
                        Text("No sessions left. Rebuild it for the next 4 weeks.", fontSize = 13.sp)
                    }
                }
            }
            TextButton(onClick = onPlan) { Text(if (plan == null) "Create" else "Open") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeekChart(workouts: List<Workout>, today: LocalDate) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { d -> workouts.filter { it.date == d }.sumOf { it.minutes } }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(30)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, d ->
                    val v = values[i]
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (v > 0) Text("$v", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier
                                .width(24.dp)
                                .height((if (v == 0) 2f else v.toFloat() / max * 100f).dp)
                                .background(
                                    if (i == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                ),
                        )
                        Text(
                            if (i == 6) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Text("Minutes per day", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun details(w: Workout): String = buildList {
    if (w.reps > 0) add("${w.reps} reps")
    add("${w.minutes} min")
    if (w.km > 0) add("${w.km} km")
    add(w.intensityLabel)
    if (w.camera) add("camera")
}.joinToString(" · ")

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}
