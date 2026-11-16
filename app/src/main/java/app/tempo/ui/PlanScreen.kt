package app.tempo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tempo.data.Kind
import app.tempo.plan.Equipment
import app.tempo.plan.Goal
import app.tempo.plan.Level
import app.tempo.plan.Plan
import app.tempo.plan.Profile
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun PlanScreen(
    plan: Plan?,
    onCreate: (Profile) -> Unit,
    onToggle: (String) -> Unit,
    onCount: (Kind, Int) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var editing by remember { mutableStateOf(plan == null) }
    if (plan == null || editing) {
        PlanForm(
            initial = plan?.profile,
            onSubmit = {
                editing = false
                onCreate(it)
            },
            onCancel = if (plan != null) ({ editing = false }) else onClose,
        )
    } else {
        PlanView(plan, onToggle, onCount, onEdit = { editing = true }, onDelete = onDelete, onClose = onClose)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanForm(initial: Profile?, onSubmit: (Profile) -> Unit, onCancel: () -> Unit) {
    var goal by remember { mutableStateOf(initial?.goal ?: Goal.ACTIVE) }
    var level by remember { mutableStateOf(initial?.level ?: Level.BEGINNER) }
    var equipment by remember { mutableStateOf(initial?.equipment ?: Equipment.NONE) }
    var days by remember { mutableIntStateOf(initial?.days ?: 3) }
    var minutes by remember { mutableIntStateOf(initial?.minutes ?: 30) }
    var lowImpact by remember { mutableStateOf(initial?.lowImpact ?: false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (initial == null) "Create your plan" else "Rebuild your plan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Tempo builds a 4 week plan from your answers and your logged workouts. It runs on your phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Label("Main goal")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach { g -> FilterChip(selected = goal == g, onClick = { goal = g }, label = { Text(g.label) }) }
        }
        Label("Experience")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Level.entries.forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l.label) }) }
        }
        Label("Workout days per week")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..6).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d") }) }
        }
        Label("Time per session")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 30, 45, 60, 75).forEach { m ->
                FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
            }
        }
        Label("Equipment")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.entries.forEach { e ->
                FilterChip(selected = equipment == e, onClick = { equipment = e }, label = { Text(e.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Low impact", fontWeight = FontWeight.Medium)
                Text(
                    "No running or jumping. Good for sore joints or small spaces.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = lowImpact, onCheckedChange = { lowImpact = it })
        }
        if (initial != null) {
            Text("This replaces your current plan and its progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onSubmit(Profile(goal, level, days, minutes, equipment, lowImpact)) },
            ) { Text(if (initial == null) "Create my plan" else "Rebuild plan") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
}

@Composable
private fun PlanView(
    plan: Plan,
    onToggle: (String) -> Unit,
    onCount: (Kind, Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val today = LocalDate.now()
    val weeks = plan.sessions.map { it.week }.distinct().sorted()
    var week by remember { mutableIntStateOf(plan.nextUp(today)?.week ?: weeks.firstOrNull() ?: 1) }
    var confirmDelete by remember { mutableStateOf(false) }
    val done = plan.sessions.count { it.done }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "${plan.profile.goal.label} · ${plan.profile.level.label} · ${plan.profile.days} days · ${plan.profile.minutes} min",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$done of ${plan.sessions.size} sessions done", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { if (plan.sessions.isEmpty()) 0f else done.toFloat() / plan.sessions.size },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                plan.notes.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weeks.forEach { w ->
                FilterChip(selected = week == w, onClick = { week = w }, label = { Text("Week $w") })
            }
        }

        plan.sessions.filter { it.week == week }.forEach { s ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (s.done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "${dayLabel(s.date, today)} · about ${s.minutes} min",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.done) Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    s.items.forEach { item ->
                        val kind = item.kind
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                Text(
                                    listOf(item.detail, item.note).filter { it.isNotBlank() }.joinToString(" · "),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (kind != null) {
                                TextButton(onClick = { onCount(kind, item.targetReps) }) { Text("Count") }
                            }
                        }
                    }
                    if (s.done) {
                        OutlinedButton(onClick = { onToggle(s.id) }) { Text("Undo done") }
                    } else {
                        Button(onClick = { onToggle(s.id) }) { Text("Mark done") }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Rebuild plan") }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Back") }
        }
        TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
            Text(
                if (confirmDelete) "Tap again to delete this plan" else "Delete plan",
                color = if (confirmDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}
