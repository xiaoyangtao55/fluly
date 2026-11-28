package com.fluly.launcher.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListEntryTransitionTest {

    @Test
    fun `Apple uses beta12 inner motion while Material keeps Fluly`() {
        assertEquals(
            AppListEntryMotion.BETA_12_CLASSIC,
            appListEntryMotionForStyle(appleWatchStyle = true),
        )
        assertEquals(
            AppListEntryMotion.FLULY_NEXT,
            appListEntryMotionForStyle(appleWatchStyle = false),
        )
    }

    @Test
    fun `beta12 classic leaves inner content fully visible and geometrically stable`() {
        val visuals = appListEntryVisuals(
            progress = 0.42f,
            motion = AppListEntryMotion.BETA_12_CLASSIC,
        )

        assertVisualProgress(expected = 1f, actual = visuals)
        assertEquals(0f, visuals.verticalOffsetFraction)
    }

    @Test
    fun `fluly next keeps one clamped content progress and an explicit vertical offset`() {
        val entering = appListEntryVisuals(
            progress = 0.25f,
            motion = AppListEntryMotion.FLULY_NEXT,
        )
        assertVisualProgress(expected = 0.25f, actual = entering)
        assertEquals(0.06f, entering.verticalOffsetFraction, 0.0001f)

        assertVisualProgress(
            expected = 0f,
            actual = appListEntryVisuals(-0.5f, AppListEntryMotion.FLULY_NEXT),
        )
        assertVisualProgress(
            expected = 1f,
            actual = appListEntryVisuals(1.5f, AppListEntryMotion.FLULY_NEXT),
        )
    }

    private fun assertVisualProgress(expected: Float, actual: AppListEntryVisuals) {
        assertEquals(expected, actual.backgroundProgress)
        assertEquals(expected, actual.backgroundFillProgress)
        assertEquals(expected, actual.iconProgress)
        assertEquals(expected, actual.surfaceProgress)
        assertEquals(expected, actual.edgeProgress)
    }
}
