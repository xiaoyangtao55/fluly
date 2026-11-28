package com.fluly.launcher.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlulyAccessibilityServiceTest {
    private val expected = "com.fluly.launcher/com.fluly.launcher.service.FlulyAccessibilityService"

    @Test
    fun `manager reported service counts as enabled when secure setting is empty`() {
        assertTrue(
            containsFlulyAccessibilityService(
                enabledServiceSetting = null,
                managerEnabledComponents = listOf(expected),
                expectedComponent = expected,
            )
        )
    }

    @Test
    fun `unrelated manager service does not satisfy the accessibility check`() {
        assertFalse(
            containsFlulyAccessibilityService(
                enabledServiceSetting = null,
                managerEnabledComponents = listOf(
                    "com.example.other/com.example.other.OtherService"
                ),
                expectedComponent = expected,
            )
        )
    }

    @Test
    fun `short class notation and whitespace are normalized`() {
        assertTrue(
            containsFlulyAccessibilityService(
                enabledServiceSetting = " other/pkg.Other: com.fluly.launcher/.service.FlulyAccessibilityService ",
                managerEnabledComponents = emptyList(),
                expectedComponent = expected,
            )
        )
    }
}
