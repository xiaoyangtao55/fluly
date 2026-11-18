package com.flue.launcher.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStyleDefaultsTest {
    @Test
    fun `all saved launcher styles migrate to watchOS`() {
        assertEquals(UiStyle.APPLE_WATCH, DEFAULT_UI_STYLE)
        listOf(null, "not-a-style", "APPLE_WATCH", "MATERIAL_3", "MIUIX").forEach { saved ->
            assertEquals(UiStyle.APPLE_WATCH, uiStyleFromPreference(saved))
        }
    }

    @Test
    fun `watchOS capsule geometry remains unchanged across light and dark`() {
        val dark = appleWatchLauncherStyle(true).capsuleMaxRadius!!
        assertTrue(dark > 28.dp)
        assertEquals(dark, appleWatchLauncherStyle(false).capsuleMaxRadius)
    }
}
