package com.fluly.launcher.ui.style

import com.fluly.launcher.ui.prompt.PromptTextEffect
import org.junit.Assert.assertTrue
import org.junit.Test

class FlulyOverlayRevealPolicyTest {
    @Test
    fun `advanced material full screen overlays reveal the whole content on enter and exit`() {
        assertTrue(shouldRevealOverlayContent(fullScreen = true, textReveal = true, effect = PromptTextEffect.BLUR_RISE))
    }

    @Test
    fun `advanced material dialogs reveal title body and actions as one foreground`() {
        assertTrue(shouldRevealOverlayContent(fullScreen = false, textReveal = true, effect = PromptTextEffect.BLUR_RISE))
    }
}
