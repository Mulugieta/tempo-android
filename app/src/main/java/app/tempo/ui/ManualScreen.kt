package app.tempo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tempo.data.INTENSITY
import app.tempo.data.Kind
import app.tempo.data.Workout
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualScreen(onSave: (Workout) -> Unit, onClose: () -> Unit) {
    var kind by remember { mutableStateOf(Kind.RUN) }
    var minutes by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var intensity by remember { mutableIntStateOf(2) }
    var daysAgo by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Log a workout", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kind.entries.forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
            }
        }

        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
            label = { Text("Duration (min)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (kind.distance) {
            OutlinedTextField(
                value = km,
                onValueChange = { km = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                label = { Text("Distance (km)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (kind.move != null) {
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it.filter(Char::isDigit).take(4) },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Intensity", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTENSITY.forEachIndexed { i, label ->
                FilterChip(selected = intensity == i + 1, onClick = { intensity = i + 1 }, label = { Text(label) })
            }
        }

        Text("Day", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = daysAgo == 0, onClick = { daysAgo = 0 }, label = { Text("Today") })
            FilterChip(selected = daysAgo == 1, onClick = { daysAgo = 1 }, label = { Text("Yesterday") })
            FilterChip(selected = daysAgo == 2, onClick = { daysAgo = 2 }, label = { Text("2 days ago") })
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(120) },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val m = minutes.toIntOrNull()
                    if (m == null || m < 1 || m > 600) {
                        error = "Enter a duration between 1 and 600 minutes."
                    } else {
                        onSave(
                            Workout(
                                id = UUID.randomUUID().toString(),
                                date = LocalDate.now().minusDays(daysAgo.toLong()),
                                kind = kind,
                                minutes = m,
                                reps = reps.toIntOrNull() ?: 0,
                                km = if (kind.distance) km.toDoubleOrNull() ?: 0.0 else 0.0,
                                intensity = intensity,
                                note = note.trim(),
                            ),
                        )
                    }
                },
            ) { Text("Add to log") }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}
