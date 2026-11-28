package com.fluly.launcher.ui.style

import androidx.compose.ui.unit.dp
import com.fluly.launcher.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlulyLoadingIndicatorPolicyTest {

    @Test
    fun `style visual sizes remain distinct around one requested size`() {
        val requested = 28.dp
        val miuix = flulyLoadingIndicatorVisualSize(requested, UiStyle.MIUIX)
        val watchFaceLoading = flulyLoadingIndicatorVisualSize(
            requested,
            UiStyle.MIUIX,
            FlulyLoadingIndicatorUsage.WATCH_FACE_LOADING,
        )
        val watchStore = flulyLoadingIndicatorVisualSize(
            requested,
            UiStyle.MIUIX,
            FlulyLoadingIndicatorUsage.WATCH_STORE,
        )
        val material = flulyLoadingIndicatorVisualSize(requested, UiStyle.MATERIAL_3)
        val apple = flulyLoadingIndicatorVisualSize(requested, UiStyle.APPLE_WATCH)

        // The default is deliberately kept compact. Only the watch-face boot page
        // gets the full nominal size; store surfaces use the compact market token.
        assertEquals(requested.value * FLULY_MIUIX_LOADING_VISUAL_SCALE, miuix.value, 0.001f)
        assertEquals(requested, watchFaceLoading)
        assertEquals(requested.value * FLULY_MIUIX_STORE_LOADING_VISUAL_SCALE, watchStore.value, 0.001f)
        assertEquals(requested, apple)
        assertTrue(material > requested)
    }

    @Test
    fun `market compact indicators keep the same style scaling policy`() {
        val requested = 11.dp

        assertEquals(
            requested.value * FLULY_MIUIX_LOADING_VISUAL_SCALE,
            flulyLoadingIndicatorVisualSize(requested, UiStyle.MIUIX).value,
            0.001f,
        )
        assertEquals(
            requested.value * FLULY_MD3_LOADING_VISUAL_SCALE,
            flulyLoadingIndicatorVisualSize(requested, UiStyle.MATERIAL_3).value,
            0.001f,
        )
    }
}
