package app.tempo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tempo.data.Kind
import app.tempo.data.Workout
import app.tempo.pose.BONES
import app.tempo.pose.Frame
import app.tempo.pose.MIN_CONF
import app.tempo.pose.RepEngine
import app.tempo.pose.toFrame
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

private enum class Phase { SETUP, COUNTDOWN, COUNTING, DONE }

private val Scrim = Color.Black.copy(alpha = 0.55f)

@Composable
fun CameraScreen(initialKind: Kind?, targetReps: Int, onSave: (Workout) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera access needed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Tempo uses the camera to see your body position and count reps. Video is analysed on this phone and is never saved or sent anywhere.",
            )
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            OutlinedButton(onClick = onClose) { Text("Back") }
        }
        return
    }

    val kinds = remember { Kind.entries.filter { it.move != null } }
    var kind by remember { mutableStateOf(initialKind ?: Kind.SQUAT) }
    val engine = remember(kind) { RepEngine(kind.move!!) }
    var phase by remember { mutableStateOf(Phase.SETUP) }
    var reps by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(3) }
    var front by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<Frame>(emptyMap()) }
    var inView by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(phase) {
        if (phase == Phase.COUNTDOWN) {
            for (i in 3 downTo 1) {
                countdown = i
                delay(1000)
            }
            engine.reset()
            reps = 0
            elapsed = 0
            phase = Phase.COUNTING
        }
    }
    LaunchedEffect(phase) {
        if (phase == Phase.COUNTING) {
            val t0 = SystemClock.elapsedRealtime()
            while (true) {
                elapsed = ((SystemClock.elapsedRealtime() - t0) / 1000).toInt()
                delay(250)
            }
        }
    }

    val onFrame: (Frame?) -> Unit = { f ->
        val current = f ?: emptyMap()
        frame = current
        val counted = engine.update(current, SystemClock.uptimeMillis())
        inView = engine.visible
        status = engine.status()
        if (phase == Phase.COUNTING && counted) {
            reps = engine.reps
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(Modifier.fillMaxSize()) {
        PosePreview(front = front, onFrame = onFrame, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val bone = Color(0xFF8AA0FF)
            val joint = Color.White
            BONES.forEach { (a, b) ->
                val pa = frame[a]
                val pb = frame[b]
                if (pa != null && pb != null && pa.conf >= MIN_CONF && pb.conf >= MIN_CONF) {
                    drawLine(bone, Offset(pa.x, pa.y), Offset(pb.x, pb.y), strokeWidth = 7f, cap = StrokeCap.Round)
                }
            }
            frame.values.forEach { p ->
                if (p.conf >= MIN_CONF) drawCircle(joint, radius = 9f, center = Offset(p.x, p.y))
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Scrim).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        enabled = phase == Phase.SETUP,
                        onClick = { kind = k },
                        label = { Text(k.label) },
                    )
                }
            }
            if (phase == Phase.COUNTING || phase == Phase.DONE) {
                Text(if (targetReps > 0) "$reps / $targetReps" else "$reps", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}",
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            Text(
                when (phase) {
                    Phase.SETUP -> if (inView) "You're in view. Tap Start." else kind.move!!.framing
                    Phase.COUNTDOWN -> "Get into position: $countdown"
                    Phase.COUNTING -> if (targetReps > 0 && reps >= targetReps) "Target reached" else status
                    Phase.DONE -> "Set complete"
                },
                color = Color.White,
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Scrim).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            when (phase) {
                Phase.SETUP -> {
                    Button(onClick = { phase = Phase.COUNTDOWN }) { Text("Start") }
                    OutlinedButton(onClick = { front = !front }) { Text("Flip camera", color = Color.White) }
                    OutlinedButton(onClick = onClose) { Text("Close", color = Color.White) }
                }
                Phase.COUNTDOWN -> OutlinedButton(onClick = { phase = Phase.SETUP }) { Text("Cancel", color = Color.White) }
                Phase.COUNTING -> Button(onClick = { phase = Phase.DONE }) { Text("Stop") }
                Phase.DONE -> {
                    Button(
                        enabled = reps > 0,
                        onClick = {
                            onSave(
                                Workout(
                                    id = UUID.randomUUID().toString(),
                                    date = LocalDate.now(),
                                    kind = kind,
                                    minutes = ((elapsed + 30) / 60).coerceAtLeast(1),
                                    reps = reps,
                                    intensity = 2,
                                    camera = true,
                                ),
                            )
                        },
                    ) { Text("Save $reps reps") }
                    OutlinedButton(onClick = { phase = Phase.SETUP }) { Text("Discard", color = Color.White) }
                }
            }
        }
    }
}

/** Camera preview that streams ML Kit pose results in preview-view pixel coordinates. */
@Composable
private fun PosePreview(front: Boolean, onFrame: (Frame?) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onFrame)
    val controller = remember { LifecycleCameraController(ctx) }

    LaunchedEffect(front) {
        controller.cameraSelector =
            if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }

    DisposableEffect(controller, owner) {
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
        )
        val executor = ContextCompat.getMainExecutor(ctx)
        controller.setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(
                listOf(detector),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                executor,
            ) { result ->
                latest(result.getValue(detector)?.toFrame())
            },
        )
        controller.bindToLifecycle(owner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            detector.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            PreviewView(c).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
    )
}
