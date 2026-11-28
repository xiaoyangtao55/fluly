package com.fluly.launcher.ui.style

import com.fluly.launcher.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class FlulyExpressiveControlsTest {
    @Test
    fun `material3 uses expressive action containers while other styles keep legacy layout`() {
        assertEquals(
            FlulyExpressiveActionContainer.BUTTON_GROUP,
            flulyExpressiveActionContainer(UiStyle.MATERIAL_3, FlulyExpressiveActionKind.LOGIN_ACTIONS),
        )
        assertEquals(
            FlulyExpressiveActionContainer.SPLIT_BUTTON,
            flulyExpressiveActionContainer(UiStyle.MATERIAL_3, FlulyExpressiveActionKind.WEB_ACTIONS),
        )
        assertEquals(
            FlulyExpressiveActionContainer.FLOATING_TOOLBAR,
            flulyExpressiveActionContainer(UiStyle.MATERIAL_3, FlulyExpressiveActionKind.EDITOR_TOOLBAR),
        )
        UiStyle.entries.filterNot { it == UiStyle.MATERIAL_3 }.forEach { style ->
            FlulyExpressiveActionKind.entries.forEach { kind ->
                assertEquals(
                    "${style.name} must retain its existing action layout",
                    FlulyExpressiveActionContainer.LEGACY,
                    flulyExpressiveActionContainer(style, kind),
                )
            }
        }
    }
}
