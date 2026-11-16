package app.tempo.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RepEngineTest {

    private var clock = 0L

    /** Leg with the given knee angle: hip above knee, ankle swung out by the angle. */
    private fun knee(deg: Double): Frame {
        val r = Math.toRadians(deg)
        return mapOf(
            Joint.L_HIP to Pt(0f, 0f),
            Joint.L_KNEE to Pt(0f, 1f),
            Joint.L_ANKLE to Pt(sin(r).toFloat(), (1 - cos(r)).toFloat()),
        )
    }

    private fun RepEngine.feed(frame: Frame, frames: Int = 10) {
        repeat(frames) {
            clock += 33
            update(frame, clock)
        }
    }

    @Test
    fun angleOfRightAngleIs90() {
        assertEquals(90f, angleDeg(Pt(0f, 0f), Pt(0f, 1f), Pt(1f, 1f)), 0.01f)
        assertEquals(180f, angleDeg(Pt(0f, 0f), Pt(0f, 1f), Pt(0f, 2f)), 0.01f)
    }

    @Test
    fun threeFullSquatsCountThree() {
        val e = RepEngine(Move.SQUAT)
        repeat(3) {
            e.feed(knee(170.0))
            e.feed(knee(90.0))
        }
        e.feed(knee(170.0))
        assertEquals(3, e.reps)
    }

    @Test
    fun shallowMovementDoesNotCount() {
        val e = RepEngine(Move.SQUAT)
        e.feed(knee(170.0))
        e.feed(knee(130.0))
        e.feed(knee(170.0))
        assertEquals(0, e.reps)
    }

    @Test
    fun startingInTheBottomPositionDoesNotAddAPhantomRep() {
        val e = RepEngine(Move.SQUAT)
        e.feed(knee(90.0))
        e.feed(knee(170.0))
        assertEquals(0, e.reps)
        e.feed(knee(90.0))
        e.feed(knee(170.0))
        assertEquals(1, e.reps)
    }

    @Test
    fun lowConfidenceFramesAreIgnoredAndBodyIsMarkedLost() {
        val e = RepEngine(Move.SQUAT)
        e.feed(knee(170.0))
        assertTrue(e.visible)
        val faint = knee(90.0).mapValues { Pt(it.value.x, it.value.y, 0.1f) }
        e.feed(faint, frames = 60)
        assertFalse(e.visible)
        assertEquals(0, e.reps)
    }

    @Test
    fun resetClearsTheSet() {
        val e = RepEngine(Move.SQUAT)
        e.feed(knee(170.0)); e.feed(knee(90.0)); e.feed(knee(170.0))
        assertEquals(1, e.reps)
        e.reset()
        assertEquals(0, e.reps)
        assertFalse(e.ready)
    }

    private fun jack(open: Boolean): Frame {
        val wristY = if (open) -2f else 5f
        val wristX = if (open) 2f else 1.2f
        val footX = if (open) 2.5f else 0.5f
        return mapOf(
            Joint.NOSE to Pt(0f, 0f),
            Joint.L_SHOULDER to Pt(-1f, 2f), Joint.R_SHOULDER to Pt(1f, 2f),
            Joint.L_WRIST to Pt(-wristX, wristY), Joint.R_WRIST to Pt(wristX, wristY),
            Joint.L_ANKLE to Pt(-footX, 10f), Joint.R_ANKLE to Pt(footX, 10f),
        )
    }

    @Test
    fun jumpingJacksCountOnReturnToClosed() {
        val e = RepEngine(Move.JACK)
        e.feed(jack(false), 12)
        repeat(2) {
            e.feed(jack(true), 12)
            e.feed(jack(false), 12)
        }
        assertEquals(2, e.reps)
    }

    @Test
    fun pushUpsIgnoredWhenStandingUpright() {
        val e = RepEngine(Move.PUSHUP)
        // Shoulder above ankle: vertical body, so the arm signal must not be used.
        val upright = mapOf(
            Joint.L_SHOULDER to Pt(0f, 0f), Joint.L_ELBOW to Pt(0f, 1f), Joint.L_WRIST to Pt(1f, 1f),
            Joint.L_ANKLE to Pt(0f, 6f),
        )
        e.feed(upright, 30)
        assertFalse(e.visible)
        assertEquals(0, e.reps)
    }
}
