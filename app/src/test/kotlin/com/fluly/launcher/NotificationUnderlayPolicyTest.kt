package com.fluly.launcher

import com.fluly.launcher.ui.navigation.ScreenState
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationUnderlayPolicyTest {
    @Test
    fun notificationBlursOnlyTheSceneItWasOpenedFrom() {
        assertEquals(
            0.6f,
            notificationUnderlayBlurProgress(ScreenState.Stack, ScreenState.Stack, 0.6f),
            0f,
        )
        assertEquals(
            0f,
            notificationUnderlayBlurProgress(ScreenState.Widgets, ScreenState.Stack, 0.6f),
            0f,
        )
        assertEquals(
            0f,
            notificationUnderlayBlurProgress(ScreenState.Stack, ScreenState.Widgets, 0.6f),
            0f,
        )
    }

    @Test
    fun notificationUnderlayProgressIsSanitized() {
        assertEquals(
            1f,
            notificationUnderlayBlurProgress(ScreenState.Stack, ScreenState.Stack, 2f),
            0f,
        )
        assertEquals(
            0f,
            notificationUnderlayBlurProgress(
                ScreenState.Stack,
                ScreenState.Stack,
                Float.NaN,
            ),
            0f,
        )
        assertEquals(
            0f,
            notificationUnderlayBlurProgress(ScreenState.Face, ScreenState.Face, 0.8f),
            0f,
        )
    }
}
