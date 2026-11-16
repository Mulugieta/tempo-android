package app.tempo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tempo.data.Kind
import app.tempo.data.Store
import app.tempo.data.Workout
import app.tempo.plan.Plan
import app.tempo.plan.Planner
import app.tempo.ui.CameraScreen
import app.tempo.ui.HomeScreen
import app.tempo.ui.ManualScreen
import app.tempo.ui.PlanScreen
import app.tempo.ui.TempoTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent {
            TempoTheme { TempoApp(store) }
        }
    }
}

private enum class Screen { HOME, CAMERA, MANUAL, PLAN }

@Composable
private fun TempoApp(store: Store) {
    var workouts by remember { mutableStateOf(store.load()) }
    var goal by remember { mutableIntStateOf(store.goal) }
    var plan by remember { mutableStateOf<Plan?>(store.loadPlan()) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    // The camera can be opened from Home (free choice) or from the plan (exercise and target preset).
    var camKind by remember { mutableStateOf<Kind?>(null) }
    var camTarget by remember { mutableIntStateOf(0) }
    var camReturn by remember { mutableStateOf(Screen.HOME) }

    fun add(w: Workout) {
        workouts = workouts + w
        store.save(workouts)
    }

    fun updatePlan(next: Plan?) {
        plan = next
        store.savePlan(next)
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.CAMERA) camReturn else Screen.HOME
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    workouts = workouts,
                    goal = goal,
                    plan = plan,
                    onGoal = {
                        goal = it
                        store.goal = it
                    },
                    onPlan = { screen = Screen.PLAN },
                    onCamera = {
                        camKind = null
                        camTarget = 0
                        camReturn = Screen.HOME
                        screen = Screen.CAMERA
                    },
                    onManual = { screen = Screen.MANUAL },
                    onDelete = { id ->
                        workouts = workouts.filter { it.id != id }
                        store.save(workouts)
                    },
                )
                Screen.CAMERA -> CameraScreen(
                    initialKind = camKind,
                    targetReps = camTarget,
                    onSave = {
                        add(it)
                        screen = camReturn
                    },
                    onClose = { screen = camReturn },
                )
                Screen.MANUAL -> ManualScreen(
                    onSave = {
                        add(it)
                        screen = Screen.HOME
                    },
                    onClose = { screen = Screen.HOME },
                )
                Screen.PLAN -> PlanScreen(
                    plan = plan,
                    onCreate = { profile -> updatePlan(Planner.generate(profile, workouts, LocalDate.now())) },
                    onToggle = { id ->
                        updatePlan(
                            plan?.let { p ->
                                p.copy(sessions = p.sessions.map { s -> if (s.id == id) s.copy(done = !s.done) else s })
                            },
                        )
                    },
                    onCount = { kind, target ->
                        camKind = kind
                        camTarget = target
                        camReturn = Screen.PLAN
                        screen = Screen.CAMERA
                    },
                    onDelete = { updatePlan(null) },
                    onClose = { screen = Screen.HOME },
                )
            }
        }
    }
}
