package com.projectego.app.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The morph's clock, tested as the object it is: time in, fraction out.
 *
 * Two things are pinned here. First, that an uninterrupted run still produces
 * byte-for-byte the fractions the pill has always rendered - the Decelerate
 * expression, the 260/210 ms clocks, the exact endpoints. Second, that an
 * interrupted run hands the next one both the position it reached and the speed
 * it had there, which is the whole of P1: the kick on reversal was a speed
 * problem, not a position problem.
 *
 * Every test feeds its own timeline. Nothing here waits, and nothing here
 * touches Android - what these cannot prove (that a frame really arrives, that
 * the window follows) is the device checklist's job.
 */
class PillMorphEngineTest {

    private val samples = (0..1000).map { it / 1000f }

    // ---------- the curve is the curve ----------

    @Test
    fun `the curve is the platform Decelerate expression, operation for operation`() {
        // Android source: (float)(1.0f - Math.pow((1.0f - input), 2 * mFactor))
        // with mFactor = 1.8f. Transcribed literally, literals included.
        fun platform(x: Float): Float {
            val exponent = (2 * 1.8f).toDouble()
            val powered = Math.pow((1f - x).toDouble(), exponent)
            return (1.0 - powered).toFloat()
        }
        samples.forEach { x ->
            assertEquals(
                "bit-identical curve at $x",
                platform(x),
                PillMorphEngine.decelerate(x),
                0f
            )
        }
    }

    @Test
    fun `the curve equals the documented power formula on its own terms`() {
        // 1 - (1 - x)^(2 * 1.8), computed entirely in doubles. Not the same
        // operation order, so a float-only tolerance is the right comparison.
        samples.forEach { x ->
            val reference = (1.0 - Math.pow(1.0 - x.toDouble(), 2 * 1.8)).toFloat()
            assertEquals(
                "formula drift at $x",
                reference,
                PillMorphEngine.decelerate(x),
                TOLERANCE
            )
        }
    }

    // ---------- an ease run is the run it replaced ----------

    @Test
    fun `an ease run reproduces the fractions the service used to render`() {
        val expand = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        (0L..EXPAND_MILLIS).forEach { elapsed ->
            val progress = (elapsed.toFloat() / EXPAND_MILLIS.toFloat()).coerceIn(0f, 1f)
            assertEquals(
                "fraction at $elapsed ms",
                PillMorphEngine.decelerate(progress),
                expand.tick(elapsed),
                0f
            )
        }
        val collapse = PillMorphEngine.easeRun(COLLAPSE_MILLIS, 1f, 0f)
        (0L..COLLAPSE_MILLIS).forEach { elapsed ->
            val progress = (elapsed.toFloat() / COLLAPSE_MILLIS.toFloat()).coerceIn(0f, 1f)
            assertEquals(
                "a collapse runs the same curve backwards at $elapsed ms",
                PillMorphEngine.decelerate(progress),
                collapse.tick(elapsed),
                TOLERANCE
            )
        }
    }

    @Test
    fun `a run starts on its own first frame`() {
        assertEquals(0f, PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f).tick(0L), 0f)
        val continuation = PillMorphEngine.springRun(0.4f, 2f, 0f, SPRING_CAP_MILLIS)
        assertEquals(0f, continuation.tick(0L), 0f)
        assertEquals("and on the morph position it was handed", 0.4f, continuation.morph, 0f)
        assertEquals(SPRING_CAP_MILLIS, continuation.durationMillis)
    }

    @Test
    fun `a run ends exactly on its target and commits it`() {
        val expand = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        assertEquals(1f, expand.tick(EXPAND_MILLIS), 0f)
        assertEquals("the target is landed on, not approached", 1f, expand.morph, 0f)
        assertEquals("and the speed is gone with the run", 0f, expand.velocity, 0f)
        assertTrue(expand.isSettled)
        assertEquals("late frames stay on the target", 1f, expand.tick(EXPAND_MILLIS * 4), 0f)

        val collapse = PillMorphEngine.easeRun(COLLAPSE_MILLIS, 1f, 0f)
        collapse.tick(COLLAPSE_MILLIS)
        assertEquals(0f, collapse.morph, 0f)

        val settled = PillMorphEngine.springRun(0.7f, -1f, 1f, SPRING_CAP_MILLIS)
        runOut(settled)
        assertTrue("a continuation arrives on its own cue", settled.isSettled)
        assertEquals(1f, settled.morph, 0f)
        assertEquals(1f, settled.tick(SPRING_CAP_MILLIS), 0f)
    }

    @Test
    fun `expansion and collapse keep their own clocks`() {
        val expand = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        val collapse = PillMorphEngine.easeRun(COLLAPSE_MILLIS, 1f, 0f)
        assertEquals(PillMorphEngine.decelerate(0.5f), expand.tick(EXPAND_MILLIS / 2), 0f)
        assertEquals(PillMorphEngine.decelerate(0.5f), collapse.tick(COLLAPSE_MILLIS / 2), TOLERANCE)
        assertTrue("half an expansion is not the target", expand.tick(EXPAND_MILLIS / 2) < 1f)
        assertTrue("half a collapse is not the target", collapse.tick(COLLAPSE_MILLIS / 2) < 1f)
    }

    @Test
    fun `ease fractions stay legal and never go backwards`() {
        val engine = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        var previous = -1f
        samples.forEach { progress ->
            val f = engine.tick((progress * EXPAND_MILLIS).toLong())
            assertTrue("out of range at $progress: $f", f in 0f..1f)
            assertTrue("went backwards at $progress", f >= previous)
            previous = f
        }
    }

    @Test
    fun `degenerate clocks answer finite bounded fractions`() {
        assertTrue(PillMorphEngine.easeRun(0L, 0f, 1f).tick(0L) == 1f)
        val engine = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        assertEquals("a clock that goes backwards is ignored", 0f, engine.tick(-100L), 0f)
        assertEquals(0f, engine.tick(0L), 0f)
        val once = engine.tick(80L)
        assertEquals("the same frame twice changes nothing", once, engine.tick(80L), 0f)
        assertEquals("an enormous frame lands the run", 1f, engine.tick(Long.MAX_VALUE), 0f)
        val spring = PillMorphEngine.springRun(0.5f, 3f, 1f, SPRING_CAP_MILLIS)
        spring.tick(16L)
        val mid = spring.morph
        spring.tick(16L)
        assertEquals("a repeated frame does not integrate twice", mid, spring.morph, 0f)
        spring.tick(4L)
        assertEquals("nor does a shorter one", mid, spring.morph, 0f)
    }

    // ---------- interruption: position ----------

    @Test
    fun `an interruption keeps the position it reached`() {
        listOf(5L, 13L, 65L, 130L, 247L).forEach { elapsed ->
            val expand = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
            expand.tick(elapsed)
            val reached = expand.morph
            val back = PillMorphEngine.springRun(reached, expand.velocity, 0f, SPRING_CAP_MILLIS)
            assertEquals("seam at $elapsed ms starts on the live frame", 0f, back.tick(0L), 0f)
            assertEquals("seam at $elapsed ms keeps the morph position", reached, back.morph, 0f)
        }
    }

    // ---------- interruption: velocity ----------

    @Test
    fun `an interruption keeps the speed it had, in the direction it was going`() {
        val expand = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        expand.tick(EXPAND_MILLIS / 2)
        val captured = expand.velocity
        // Independent derivation: d/dx of 1-(1-x)^k is k(1-x)^(k-1), over duration.
        val expected = 3.6f * (1f - 0.5f).pow(2.6f) * 1000f / EXPAND_MILLIS.toFloat()
        assertEquals("the eased run knows its own slope", expected, captured, 1e-4f)

        val back = PillMorphEngine.springRun(expand.morph, captured, 0f, SPRING_CAP_MILLIS)
        assertTrue("the reversal still travels the way it was travelling", back.velocity > 0f)
        assertEquals(
            "and at the speed it had, since that fits inside the budget",
            captured,
            back.velocity,
            TOLERANCE
        )
    }

    @Test
    fun `momentum is bounded so a reversal cannot bounce`() {
        // Away from the new target: capped to a fraction of the remaining distance.
        val away = PillMorphEngine.springRun(0.1f, 50f, 0f, SPRING_CAP_MILLIS)
        assertTrue("the direction of travel is kept", away.velocity > 0f)
        assertTrue(
            "the amount is bounded",
            away.velocity <= RATE * 0.1f * FOLLOW_THROUGH + TOLERANCE
        )
        // Toward the new target: kept, up to the no-crossing limit.
        val toward = PillMorphEngine.springRun(0.5f, -3f, 0f, SPRING_CAP_MILLIS)
        assertEquals(-3f, toward.velocity, TOLERANCE)
        val fastToward = PillMorphEngine.springRun(0.5f, -30f, 0f, SPRING_CAP_MILLIS)
        assertEquals("the crossing limit is rate times distance", -RATE * 0.5f, fastToward.velocity, TOLERANCE)
        // And a stopped morph has nothing to carry.
        assertEquals(0f, PillMorphEngine.springRun(0.5f, 2f, 0.5f, SPRING_CAP_MILLIS).velocity, 0f)
    }

    @Test
    fun `a continuation never crosses its target`() {
        // Crossing needs speed > rate x distance, which boundContinuation forbids.
        listOf(
            Triple(0.05f, 60f, 0f), Triple(0.5f, -20f, 1f), Triple(0.9f, -60f, 0f),
            Triple(0.02f, -5f, 1f), Triple(0.98f, 5f, 0f)
        ).forEach { (start, velocity, target) ->
            val engine = PillMorphEngine.springRun(start, velocity, target, SPRING_CAP_MILLIS)
            runOut(engine).forEach { frame ->
                assertTrue(
                    "from $start toward $target crossed on a frame",
                    if (target > start) frame <= target + TOLERANCE else frame >= target - TOLERANCE
                )
                assertTrue("morph not finite", engine.morph.isFinite())
            }
            assertEquals("and it lands on the target", target, engine.morph, 0f)
        }
    }

    @Test
    fun `the follow-through after a reversal stays inside its budget`() {
        listOf(13L, 65L, 130L, 195L, 247L).forEach { elapsed ->
            val expand = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
            expand.tick(elapsed)
            val seam = expand.morph
            val remaining = abs(seam - 0f)
            val back = PillMorphEngine.springRun(seam, expand.velocity, 0f, SPRING_CAP_MILLIS)
            var furthest = seam
            var t = 0L
            while (t <= SPRING_CAP_MILLIS && !back.isSettled) {
                back.tick(t)
                furthest = maxOf(furthest, back.morph)
                t += 16L
            }
            assertTrue(
                "reversal at $elapsed ms drifted too far the old way: $furthest from $seam",
                furthest - seam <= FOLLOW_THROUGH * remaining + TOLERANCE
            )
        }
    }

    // ---------- reversals both ways, and rapid taps ----------

    @Test
    fun `reversals near the start, the middle and the end all land`() {
        listOf(
            EXPAND_MILLIS / 20 to 0f,        // 5% in, told to collapse
            EXPAND_MILLIS / 2 to 0f,         // 50% in, told to collapse
            (EXPAND_MILLIS * 19 / 20) to 0f, // 95% in, told to collapse
            COLLAPSE_MILLIS / 20 to 1f,      // the same three, the other way
            COLLAPSE_MILLIS / 2 to 1f,
            (COLLAPSE_MILLIS * 19 / 20) to 1f
        ).forEach { (elapsed, target) ->
            val expanding = target == 1f
            val first = if (expanding) {
                PillMorphEngine.easeRun(COLLAPSE_MILLIS, 1f, 0f)
            } else {
                PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
            }
            first.tick(elapsed)
            val back = PillMorphEngine.springRun(first.morph, first.velocity, target, SPRING_CAP_MILLIS)
            val frames = runOut(back)
            assertTrue("a reversal needs frames to travel", frames.size > 2)
            assertTrue("reversal to $target did not settle in ${back.durationMillis} ms", back.isSettled)
            assertEquals("reversal to $target landed exactly", target, back.morph, 0f)
            assertEquals("and its last fraction is the end of the run", 1f, frames.last(), 0f)
        }
    }

    @Test
    fun `six rapid taps keep one owner and stay bounded`() {
        var engine: PillMorphEngine = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        var elapsed = 0L
        var target = 1f
        val gaps = listOf(18L, 25L, 13L, 34L, 21L, 16L)
        gaps.forEach { gap ->
            elapsed += gap
            engine.tick(elapsed)
            // Exactly what the service reads at a tap: where the morph is and how
            // fast it is going on the frame that is currently on screen.
            val reached = engine.morph
            val velocity = engine.velocity
            assertTrue("morph finite and bounded before the tap", reached in -0.05f..1.05f)
            target = if (target == 1f) 0f else 1f
            val next = PillMorphEngine.springRun(reached, velocity, target, SPRING_CAP_MILLIS)
            assertEquals("seam position survived a tap", 0f, next.tick(0L), 0f)
            assertEquals("seam morph survived a tap", reached, next.morph, 0f)
            assertTrue("the new run stays bounded", next.morph in -0.05f..1.05f)
            engine = next
            elapsed = 0L
        }
        val frames = runOut(engine)
        assertTrue("the last tap wins", engine.isSettled)
        assertEquals(target, engine.morph, 0f)
        assertTrue(frames.isNotEmpty())
    }

    @Test
    fun `retargeting a continuation keeps position and preserves speed`() {
        val spring = PillMorphEngine.springRun(0.6f, -4f, 0f, SPRING_CAP_MILLIS)
        spring.tick(48L)
        val mid = spring.morph
        val speed = spring.velocity
        // It was heading down; a tap now sends it back up, so its speed points
        // away from the new target and must be bounded, never amplified.
        val back = PillMorphEngine.springRun(mid, speed, 1f, SPRING_CAP_MILLIS)
        assertEquals(0f, back.tick(0L), 0f)
        assertEquals(mid, back.morph, 0f)
        assertTrue("direction kept", back.velocity.sign == speed.sign)
        assertTrue("no amplification", abs(back.velocity) <= abs(speed) + TOLERANCE)
        // And once a morph is already travelling where the next tap wants it, the
        // speed is kept whole.
        val along = PillMorphEngine.springRun(0.3f, 2f, 1f, SPRING_CAP_MILLIS)
        assertEquals(2f, along.velocity, TOLERANCE)
    }

    @Test
    fun `a spring settles without ringing`() {
        val engine = PillMorphEngine.springRun(0.9f, 8f, 0f, SPRING_CAP_MILLIS)
        var previous = engine.tick(0L)
        var direction = 0f
        var turns = 0
        var elapsed = 16L
        while (elapsed <= SPRING_CAP_MILLIS && !engine.isSettled) {
            val frame = engine.tick(elapsed)
            val step = frame - previous
            if (step != 0f) {
                val sign = if (step > 0f) 1f else -1f
                if (direction != 0f && sign != direction) turns++
                direction = sign
            }
            previous = frame
            elapsed += 16L
        }
        assertTrue("a critically damped morph turns around at most once", turns <= 1)
        assertTrue("and it arrives", engine.isSettled)
    }

    @Test
    fun `a stalled frame is a settle, not an explosion`() {
        val engine = PillMorphEngine.springRun(0.35f, 12f, 1f, SPRING_CAP_MILLIS)
        engine.tick(16L)
        val afterHugeGap = engine.tick(900L)
        assertTrue("finite", engine.morph.isFinite() && afterHugeGap.isFinite())
        assertTrue("bounded", engine.morph in 0f..1.05f)
        assertTrue("a long stall settles the run", engine.isSettled)
        assertEquals(1f, engine.morph, 0f)
        assertEquals(1f, afterHugeGap, 0f)
        // A frame gap of a second is integrated as a second, not a hundred.
        val slow = PillMorphEngine.springRun(0.5f, 0f, 0f, SPRING_CAP_MILLIS)
        val first = slow.tick(1_000L)
        assertEquals("settled, so the answer is the end of the run", 1f, first, 0f)
    }

    // ---------- ownership ----------

    @Test
    fun `an abandoned run cannot move the live one`() {
        val outgoing = PillMorphEngine.easeRun(EXPAND_MILLIS, 0f, 1f)
        outgoing.tick(100L)
        val live = PillMorphEngine.springRun(outgoing.morph, outgoing.velocity, 0f, SPRING_CAP_MILLIS)
        val seam = live.morph
        // The replaced run keeps ticking (its callbacks are what P1 must not let
        // commit): it changes its own state only.
        outgoing.tick(EXPAND_MILLIS)
        assertEquals("the live morph is untouched by the old clock", seam, live.morph, 0f)
        runOut(live)
        assertEquals(0f, live.morph, 0f)
        assertTrue("the old run settled on its own terms, apart", outgoing.isSettled)
        assertEquals(1f, outgoing.morph, 0f)
    }

    @Test
    fun `a rendered fraction never leaves the run's own segment`() {
        // The endpoints of a run are a window's sizes. A fraction outside them is
        // a height below zero - an invisible pill that no further tap can reach -
        // so momentum stays in the morph and never in the answer the service draws.
        val compactHeight = 68
        val panelHeight = 520
        listOf(
            Triple(0.05f, 20f, 0f), Triple(0.5f, 8f, 0f), Triple(0.95f, 1f, 0f),
            Triple(0.95f, -20f, 1f), Triple(0.3f, -60f, 0f), Triple(0.7f, 60f, 1f)
        ).forEach { (start, velocity, target) ->
            val engine = PillMorphEngine.springRun(start, velocity, target, SPRING_CAP_MILLIS)
            val fromHeight = compactHeight + (panelHeight - compactHeight) * start
            var t = 0L
            while (t <= SPRING_CAP_MILLIS && !engine.isSettled) {
                val f = engine.tick(t)
                assertTrue("fraction $f left its segment at $t ms", f in 0f..1f && f.isFinite())
                val height = fromHeight + ((if (target == 0f) compactHeight else panelHeight) - fromHeight) * f
                assertTrue("the window would be $height px tall", height >= 0f)
                t += 8L
            }
            assertTrue("the run still arrived at its target", engine.isSettled)
        }
        // And the momentum is still real underneath the clamp: a reversal whose
        // speed points away from the new target moves the morph on the first
        // millisecond, while the picture simply holds at the seam.
        val away = PillMorphEngine.springRun(0.5f, 3f, 0f, SPRING_CAP_MILLIS)
        val firstFrame = away.tick(1L)
        assertTrue("the brake still travels the old way in the morph", away.morph > 0.5f)
        assertEquals("while the answer holds at the run's own start", 0f, firstFrame, 0f)
    }

    // ---------- the launch: an open with weight ----------

    @Test
    fun `a launch starts from rest, not from full speed`() {
        val engine = PillMorphEngine.launchRun(LAUNCH_CAP_MILLIS)
        val at20 = engine.tick(20L)
        assertTrue("20 ms in it had already moved $at20", at20 < 0.35f)
        assertTrue("but it does move", engine.tick(60L) > at20)
    }

    @Test
    fun `a heavy launch has weight without the old rebound`() {
        val engine = PillMorphEngine.launchRun(LAUNCH_CAP_MILLIS)
        var peak = 0f
        var peakAt = 0L
        var t = 0L
        while (t <= LAUNCH_CAP_MILLIS) {
            val v = engine.tick(t)
            if (v > peak) {
                peak = v
                peakAt = t
            }
            t += 2L
        }
        assertTrue("peak was $peak", peak in 1.005f..1.03f)
        val at = peakAt.toFloat() / LAUNCH_CAP_MILLIS
        assertTrue("peak at ${at * 100}% of the cap", at in 0.45f..0.72f)
    }

    @Test
    fun `a launch lands exactly on its target`() {
        val engine = PillMorphEngine.launchRun(LAUNCH_CAP_MILLIS)
        var t = 0L
        while (t <= LAUNCH_CAP_MILLIS && !engine.isSettled) {
            engine.tick(t)
            t += 4L
        }
        assertTrue("the spring must arrive inside its cap", engine.isSettled)
        assertEquals(1f, engine.morph, 0.002f)
        assertEquals(1f, engine.tick(LAUNCH_CAP_MILLIS), 0f)
    }

    @Test
    fun `a launch hands a reversal its live position and speed`() {
        val launch = PillMorphEngine.launchRun(LAUNCH_CAP_MILLIS)
        launch.tick(150L)
        val m = launch.morph
        val v = launch.velocity
        assertTrue("a launch is already moving at 150 ms", v > 0f)
        val back = PillMorphEngine.springRun(m, v, 0f, SPRING_CAP_MILLIS)
        assertEquals("the seam starts on the live frame", 0f, back.tick(0L), 0f)
        assertEquals("and on the live position", m, back.morph, 0f)
        assertTrue("the reversal keeps its trailing speed, bounded", back.velocity > 0f)
    }

    private fun runOut(engine: PillMorphEngine, step: Long = 16L): List<Float> {
        val frames = mutableListOf(engine.tick(0L))
        var elapsed = step
        while (elapsed <= engine.durationMillis && !engine.isSettled) {
            frames += engine.tick(elapsed)
            elapsed += step
        }
        return frames
    }

    private companion object {
        /** The service's two morph clocks, kept literal here so a change fails loudly. */
        const val EXPAND_MILLIS = 420L
        const val COLLAPSE_MILLIS = 210L
        const val SPRING_CAP_MILLIS = 900L
        const val LAUNCH_CAP_MILLIS = 740L

        /** The engine's own knobs, restated literally for the same reason. */
        const val RATE = 40f
        const val FOLLOW_THROUGH = 0.15f

        /** Float work leaves crumbs of a few ulps; anything more is a change. */
        const val TOLERANCE = 1e-6f
    }
}
