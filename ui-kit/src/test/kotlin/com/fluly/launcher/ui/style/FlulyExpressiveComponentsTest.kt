package com.fluly.launcher.ui.style

import com.fluly.launcher.ui.theme.UiStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlulyExpressiveComponentsTest {
    @Test
    fun `material 3 is the only style that opts into expressive components`() {
        assertTrue(useMaterial3ExpressiveComponents(UiStyle.MATERIAL_3))
        assertFalse(useMaterial3ExpressiveComponents(UiStyle.MIUIX))
        assertFalse(useMaterial3ExpressiveComponents(UiStyle.APPLE_WATCH))
    }
}
