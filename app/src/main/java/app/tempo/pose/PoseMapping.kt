package app.tempo.pose

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

private val LANDMARKS = listOf(
    Joint.NOSE to PoseLandmark.NOSE,
    Joint.L_SHOULDER to PoseLandmark.LEFT_SHOULDER,
    Joint.R_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    Joint.L_ELBOW to PoseLandmark.LEFT_ELBOW,
    Joint.R_ELBOW to PoseLandmark.RIGHT_ELBOW,
    Joint.L_WRIST to PoseLandmark.LEFT_WRIST,
    Joint.R_WRIST to PoseLandmark.RIGHT_WRIST,
    Joint.L_HIP to PoseLandmark.LEFT_HIP,
    Joint.R_HIP to PoseLandmark.RIGHT_HIP,
    Joint.L_KNEE to PoseLandmark.LEFT_KNEE,
    Joint.R_KNEE to PoseLandmark.RIGHT_KNEE,
    Joint.L_ANKLE to PoseLandmark.LEFT_ANKLE,
    Joint.R_ANKLE to PoseLandmark.RIGHT_ANKLE,
)

/** Converts an ML Kit pose (already in preview-view pixels) into the engine's frame. */
fun Pose.toFrame(): Frame {
    val out = HashMap<Joint, Pt>()
    for ((joint, type) in LANDMARKS) {
        val lm = getPoseLandmark(type) ?: continue
        out[joint] = Pt(lm.position.x, lm.position.y, lm.inFrameLikelihood)
    }
    return out
}
