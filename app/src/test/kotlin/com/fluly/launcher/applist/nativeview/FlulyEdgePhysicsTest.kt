package com.fluly.launcher.applist.nativeview

import com.fluly.launcher.motion.FlulyMotionProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FlulyEdgePhysicsTest {
    @Test
    fun `single move remainder crossing top edge stretches immediately`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }

        val consumed = physics.pull(FlulyEdgePhysics.Edge.TOP, outwardDeltaPx = 24f)

        assertEquals(24f, consumed, 0f)
        assertTrue(physics.offset > 0f)
        assertEquals(FlulyEdgePhysics.Phase.PULL, physics.currentPhase)
    }

    @Test
    fun `top and bottom pulls are symmetric`() {
        val top = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        val bottom = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }

        top.pull(FlulyEdgePhysics.Edge.TOP, 80f)
        bottom.pull(FlulyEdgePhysics.Edge.BOTTOM, 80f)

        assertEquals(top.offset, -bottom.offset, 0.001f)
        assertEquals(
            top.distance(FlulyEdgePhysics.Edge.TOP),
            bottom.distance(FlulyEdgePhysics.Edge.BOTTOM),
            0.001f,
        )
        assertEquals(0f, top.distance(FlulyEdgePhysics.Edge.BOTTOM), 0f)
        assertEquals(0f, bottom.distance(FlulyEdgePhysics.Edge.TOP), 0f)
    }

    @Test
    fun `high velocity absorb extends before rebounding without exceeding cap`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        val maximum = physics.snapshot().maxOffsetPx

        physics.absorb(FlulyEdgePhysics.Edge.BOTTOM, velocityPxPerSecond = 20_000f)
        assertEquals(FlulyEdgePhysics.Phase.EXTENDING, physics.currentPhase)
        assertTrue("absorb must move on the impact frame", physics.offset < 0f)
        physics.advanceBy(80L)

        assertTrue(abs(physics.offset) > 0f)
        assertTrue(abs(physics.offset) <= maximum)

        repeat(5) { physics.advanceBy(100L) }
        assertEquals(0f, physics.offset, 0.001f)
        assertFalse(physics.isAnimating)
    }

    @Test
    fun `recovering the complete stretch finishes the edge and leaves remainder for content`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        physics.pull(FlulyEdgePhysics.Edge.TOP, 80f)
        val stretched = physics.offset

        val consumed = physics.pull(
            FlulyEdgePhysics.Edge.TOP,
            outwardDeltaPx = -(stretched + 24f),
        )

        assertEquals(-stretched, consumed, 0.001f)
        assertEquals(0f, physics.offset, 0f)
        assertEquals(FlulyEdgePhysics.Phase.IDLE, physics.currentPhase)
        assertFalse(physics.isActive(FlulyEdgePhysics.Edge.TOP))
    }

    @Test
    fun `legacy top edge handoff forwards only the pointer remainder past rest`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        val pointer = NativeLegacyEdgePointerState().apply { begin(100f) }
        physics.pull(FlulyEdgePhysics.Edge.TOP, 80f)
        val stretched = physics.offset
        val physicalDelta = pointer.physicalDeltaTo(60f)

        val consumed = physics.consumeInwardPointerDelta(physicalDelta)
        val forwardedOffset = pointer.advanceTo(60f, consumed)

        assertEquals(-stretched, consumed, 0.001f)
        assertEquals(-(40f - stretched), pointer.forwardedDeltaFrom(100f), 0.001f)
        assertEquals(stretched, forwardedOffset, 0.001f)
        assertEquals(0f, physics.offset, 0f)
        assertFalse(physics.hasActiveEdge)
    }

    @Test
    fun `legacy bottom edge handoff preserves the opposite pointer sign`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        val pointer = NativeLegacyEdgePointerState().apply { begin(100f) }
        physics.pull(FlulyEdgePhysics.Edge.BOTTOM, 80f)
        val stretched = abs(physics.offset)
        val physicalDelta = pointer.physicalDeltaTo(140f)

        val consumed = physics.consumeInwardPointerDelta(physicalDelta)
        val forwardedOffset = pointer.advanceTo(140f, consumed)

        assertEquals(stretched, consumed, 0.001f)
        assertEquals(40f - stretched, pointer.forwardedDeltaFrom(100f), 0.001f)
        assertEquals(-stretched, forwardedOffset, 0.001f)
        assertEquals(0f, physics.offset, 0f)
        assertFalse(physics.hasActiveEdge)
    }

    @Test
    fun `legacy terminal velocity expires after a stationary pause`() {
        val velocity = NativeLegacyPointerVelocityState(maximumSampleAgeMs = 100L)
        velocity.begin(pointerY = 100f, eventTime = 0L)
        velocity.record(
            pointerY = 60f,
            eventTime = 20L,
            preserveWhenStationary = false,
        )
        assertTrue(abs(velocity.pointerVelocityY) > 0f)

        velocity.record(
            pointerY = 60f,
            eventTime = 220L,
            preserveWhenStationary = true,
        )

        assertEquals(0f, velocity.pointerVelocityY, 0f)
    }

    @Test
    fun `touch takes over an in flight rebound at the presented offset`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        physics.pull(FlulyEdgePhysics.Edge.TOP, 160f)
        physics.release(FlulyEdgePhysics.Edge.TOP)
        physics.advanceBy(90L)
        val presented = physics.offset

        physics.takeOver(FlulyEdgePhysics.Edge.TOP)

        assertEquals(presented, physics.offset, 0f)
        assertEquals(FlulyEdgePhysics.Phase.PULL, physics.currentPhase)
        assertFalse(physics.isAnimating)

        val consumed = physics.pull(FlulyEdgePhysics.Edge.TOP, -4f)
        assertEquals(-min(4f, max(0f, presented)), consumed, 0.001f)
        assertEquals(max(0f, presented - 4f), physics.offset, 0.001f)
    }

    @Test
    fun `animator completion clears unfinished physics and frame requests`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        physics.pull(FlulyEdgePhysics.Edge.TOP, 160f)
        physics.release(FlulyEdgePhysics.Edge.TOP)
        assertTrue(physics.needsAnimationFrame)

        physics.finishAnimation()

        assertEquals(0f, physics.offset, 0f)
        assertFalse(physics.needsAnimationFrame)
        assertFalse(physics.hasActiveEdge)
    }

    @Test
    fun `shrinking motion strength during absorb keeps later frames inside the new cap`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }
        physics.absorb(FlulyEdgePhysics.Edge.TOP, velocityPxPerSecond = 20_000f)
        physics.updateMotionProfile(
            FlulyMotionProfiles.Gentle.copy(edgeStrength = 1),
        )
        val reducedMaximum = physics.snapshot().maxOffsetPx

        physics.advanceBy(16L)

        assertTrue(abs(physics.offset) <= reducedMaximum)
    }

    @Test
    fun `pulled edge stays frame idle until release starts rebound`() {
        val physics = FlulyEdgePhysics(density = 1f).apply { updateViewport(400) }

        physics.pull(FlulyEdgePhysics.Edge.TOP, 80f)

        assertFalse(physics.needsAnimationFrame)
        physics.release(FlulyEdgePhysics.Edge.TOP)
        assertTrue(physics.needsAnimationFrame)
    }

    @Test
    fun `disabled motion profile consumes no edge distance`() {
        val physics = FlulyEdgePhysics(density = 1f).apply {
            updateViewport(400)
            updateMotionProfile(
                FlulyMotionProfiles.Gentle.copy(
                    bounceEnabled = false,
                    edgeStrength = 0,
                ),
            )
        }

        assertEquals(0f, physics.pull(FlulyEdgePhysics.Edge.TOP, 80f), 0f)
        assertEquals(0f, physics.offset, 0f)
        physics.absorb(FlulyEdgePhysics.Edge.TOP, 20_000f)
        assertFalse(physics.isAnimating)
    }

    @Test
    fun `edge strength scales pull response while five preserves CloX baseline`() {
        val gentle = FlulyEdgePhysics(density = 1f).apply {
            updateViewport(400)
            updateMotionProfile(FlulyMotionProfiles.Gentle.copy(edgeStrength = 5))
        }
        val strong = FlulyEdgePhysics(density = 1f).apply {
            updateViewport(400)
            updateMotionProfile(FlulyMotionProfiles.Gentle.copy(edgeStrength = 10))
        }

        gentle.pull(FlulyEdgePhysics.Edge.TOP, 40f)
        strong.pull(FlulyEdgePhysics.Edge.TOP, 40f)

        assertEquals(7.2f, gentle.offset, 0.001f)
        assertTrue(strong.offset > gentle.offset)
        assertTrue(strong.snapshot().maxOffsetPx > gentle.snapshot().maxOffsetPx)
    }
}
