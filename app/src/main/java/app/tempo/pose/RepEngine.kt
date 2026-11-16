package app.tempo.pose

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/** Body points the counter needs. Independent of ML Kit so the logic can be unit tested. */
enum class Joint {
    NOSE,
    L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST,
    L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE,
}

/** A point in view pixels (y grows downward) with the detector's in-frame confidence 0..1. */
data class Pt(val x: Float, val y: Float, val conf: Float = 1f)

typealias Frame = Map<Joint, Pt>

/** Exercises that can be counted from the camera. */
enum class Move(val label: String, val framing: String) {
    SQUAT("Squats", "Stand side-on to the camera with your whole body in view"),
    PUSHUP("Push-ups", "Prop the phone on the floor, side-on, whole body in view"),
    CURL("Curls", "Stand side-on with your shoulder, elbow and wrist in view"),
    JACK("Jumping jacks", "Face the camera with your head to feet in view"),
}

/** Pairs drawn as the skeleton overlay. */
val BONES: List<Pair<Joint, Joint>> = listOf(
    Joint.L_SHOULDER to Joint.R_SHOULDER,
    Joint.L_SHOULDER to Joint.L_ELBOW, Joint.L_ELBOW to Joint.L_WRIST,
    Joint.R_SHOULDER to Joint.R_ELBOW, Joint.R_ELBOW to Joint.R_WRIST,
    Joint.L_SHOULDER to Joint.L_HIP, Joint.R_SHOULDER to Joint.R_HIP,
    Joint.L_HIP to Joint.R_HIP,
    Joint.L_HIP to Joint.L_KNEE, Joint.L_KNEE to Joint.L_ANKLE,
    Joint.R_HIP to Joint.R_KNEE, Joint.R_KNEE to Joint.R_ANKLE,
)

const val MIN_CONF = 0.5f

/** Angle at [b] between rays b->a and b->c, in degrees (0..180). */
fun angleDeg(a: Pt, b: Pt, c: Pt): Float {
    val v1x = a.x - b.x
    val v1y = a.y - b.y
    val v2x = c.x - b.x
    val v2y = c.y - b.y
    val norm = hypot(v1x, v1y) * hypot(v2x, v2y)
    if (norm < 1e-3f) return 180f
    val cos = ((v1x * v2x + v1y * v2y) / norm).coerceIn(-1f, 1f)
    return acos(cos) * 180f / PI.toFloat()
}

private typealias Limb = Triple<Joint, Joint, Joint>

private val LEG_L: Limb = Triple(Joint.L_HIP, Joint.L_KNEE, Joint.L_ANKLE)
private val LEG_R: Limb = Triple(Joint.R_HIP, Joint.R_KNEE, Joint.R_ANKLE)
private val ARM_L: Limb = Triple(Joint.L_SHOULDER, Joint.L_ELBOW, Joint.L_WRIST)
private val ARM_R: Limb = Triple(Joint.R_SHOULDER, Joint.R_ELBOW, Joint.R_WRIST)

private enum class Signal { START, PEAK, MID }

private fun Frame.seen(j: Joint): Pt? = this[j]?.takeIf { it.conf >= MIN_CONF }

private fun Frame.mid(a: Joint, b: Joint): Pt? {
    val pts = listOfNotNull(seen(a), seen(b))
    if (pts.isEmpty()) return null
    return Pt(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
}

/** Mean joint angle over whichever sides are confidently visible, or null if neither is. */
private fun Frame.meanAngle(l: Limb, r: Limb): Float? {
    val angles = listOf(l, r).mapNotNull { (a, b, c) ->
        val pa = seen(a)
        val pb = seen(b)
        val pc = seen(c)
        if (pa != null && pb != null && pc != null) angleDeg(pa, pb, pc) else null
    }
    return if (angles.isEmpty()) null else angles.average().toFloat()
}

/**
 * Counts repetitions from a stream of pose frames.
 *
 * Each move is reduced to a signal: START (the resting position), PEAK (the working
 * position) or MID. A rep is counted when a PEAK is followed by a return to START, so
 * half reps and jitter between the two thresholds never count. Tracking only begins after
 * the first START is seen, so starting in the bottom position does not add a phantom rep.
 */
class RepEngine(val move: Move) {
    var reps = 0
        private set

    /** True while the needed joints are confidently in view. */
    var visible = false
        private set

    /** True once the working position has been reached and the rep awaits its return. */
    var armed = false
        private set

    /** True once the resting position has been seen at least once this set. */
    var ready = false
        private set

    private var smoothed: Float? = null
    private var lastRepMs = -10_000L
    private var lastSeenMs = 0L

    fun reset() {
        reps = 0
        armed = false
        ready = false
        smoothed = null
        lastRepMs = -10_000L
    }

    /** Feed one frame. Returns true when this frame completed a rep. */
    fun update(frame: Frame, nowMs: Long): Boolean {
        val signal = signal(frame)
        if (signal == null) {
            if (nowMs - lastSeenMs > LOST_MS) {
                visible = false
                smoothed = null
            }
            return false
        }
        lastSeenMs = nowMs
        visible = true

        var counted = false
        when (signal) {
            Signal.START -> {
                if (armed && nowMs - lastRepMs >= MIN_REP_MS) {
                    reps++
                    lastRepMs = nowMs
                    counted = true
                }
                armed = false
                ready = true
            }
            Signal.PEAK -> if (ready) armed = true
            Signal.MID -> Unit
        }
        return counted
    }

    fun status(): String = when {
        !visible -> move.framing
        !ready -> "Get into the starting position"
        armed -> "Now return to the start"
        else -> "Go"
    }

    private fun signal(f: Frame): Signal? = when (move) {
        Move.SQUAT -> angleSignal(f, LEG_L, LEG_R, start = 160f, peak = 110f)
        Move.PUSHUP ->
            if (isHorizontal(f)) angleSignal(f, ARM_L, ARM_R, start = 150f, peak = 100f) else null
        Move.CURL -> angleSignal(f, ARM_L, ARM_R, start = 140f, peak = 55f)
        Move.JACK -> jackSignal(f)
    }

    private fun angleSignal(f: Frame, l: Limb, r: Limb, start: Float, peak: Float): Signal? {
        val raw = f.meanAngle(l, r) ?: return null
        val a = smoothed?.let { it + ALPHA * (raw - it) } ?: raw
        smoothed = a
        return when {
            a >= start -> Signal.START
            a <= peak -> Signal.PEAK
            else -> Signal.MID
        }
    }

    /** Push-ups only count when the body is closer to horizontal than vertical. */
    private fun isHorizontal(f: Frame): Boolean {
        val shoulders = f.mid(Joint.L_SHOULDER, Joint.R_SHOULDER) ?: return false
        val low = f.mid(Joint.L_ANKLE, Joint.R_ANKLE) ?: f.mid(Joint.L_HIP, Joint.R_HIP) ?: return false
        return abs(shoulders.x - low.x) > abs(shoulders.y - low.y)
    }

    /** Open = hands above the head and feet wide. Closed = hands below shoulders, feet together. */
    private fun jackSignal(f: Frame): Signal? {
        val nose = f.seen(Joint.NOSE) ?: return null
        val ls = f.seen(Joint.L_SHOULDER) ?: return null
        val rs = f.seen(Joint.R_SHOULDER) ?: return null
        val lw = f.seen(Joint.L_WRIST) ?: return null
        val rw = f.seen(Joint.R_WRIST) ?: return null
        val la = f.seen(Joint.L_ANKLE) ?: return null
        val ra = f.seen(Joint.R_ANKLE) ?: return null
        val shoulderWidth = abs(ls.x - rs.x)
        if (shoulderWidth < 1f) return null
        val footGap = abs(la.x - ra.x)
        val handsUp = lw.y < nose.y && rw.y < nose.y
        val handsDown = lw.y > ls.y && rw.y > rs.y
        return when {
            handsUp && footGap > 1.5f * shoulderWidth -> Signal.PEAK
            handsDown && footGap < 1.1f * shoulderWidth -> Signal.START
            else -> Signal.MID
        }
    }

    private companion object {
        const val MIN_REP_MS = 350L
        const val LOST_MS = 1000L
        const val ALPHA = 0.4f
    }
}
