package com.fluly.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFaceFeedbackUiContractTest {
    @Test
    fun `editor exposes fill preview toolbar order icons and scale slider`() {
        val editor = source("com/example/wlauncher/WatchEditorActivity.kt")

        assertTrue(editor.contains("view.setCanvasSettings(WatchCanvasSettings.GLOBAL_DEFAULT)"))
        assertTrue(editor.contains("fill = true"))
        assertTrue(editor.contains("MiuixIcons.Share"))
        assertTrue(editor.contains("EditorSelectionScaleSlider("))
        assertTrue(editor.indexOf("IconButton(onClick = onBack") < editor.indexOf("IconButton(onClick = onToggleMultiSelect"))
    }

    @Test
    fun `launcher theme gives outlined inputs the larger row shape`() {
        val theme = source("com/example/wlauncher/ui/theme/Theme.kt")
        // Check the shape mapping and theme wiring, not a local variable's name.
        assertTrue(theme.contains("flulyMaterialShapes(appleWatchLauncherStyle(isDark))"))
        assertTrue(theme.contains("val rowShape = style.rowShape as CornerBasedShape"))
        assertTrue(theme.contains("extraSmall = rowShape"))
        assertTrue(Regex("""MaterialTheme\([\s\S]*?shapes\s*=\s*shapes,""").containsMatchIn(theme))
    }

    @Test
    fun `corner mask slider is not nested inside the dialog vertical scroller`() {
        val about = source("com/example/wlauncher/ui/settings/SettingsAboutAndDonate.kt")
        val dialog = about.substringAfter("internal fun OverlayMaskCornerRadiusDialog(")
            .substringBefore("internal fun", missingDelimiterValue = about)

        assertTrue(dialog.contains("scrollableContent = false"))
        assertTrue(dialog.contains("FlulySlider("))
    }

    @Test
    fun `app long press menu keeps its original centered card presentation`() {
        val popup = source("com/example/wlauncher/ui/drawer/ShortcutPopup.kt")

        assertTrue(popup.contains("val panelWidth = (maxWidth * 0.78f)"))
        assertTrue(popup.contains("label = \"overlay_scale\""))
        assertTrue(popup.contains("snapshotFlow { animAlpha }"))
        assertFalse(popup.contains("FlulyDialog("))
    }

    @Test
    fun `new watch flow always creates format one without a version chooser`() {
        val editor = source("com/example/wlauncher/WatchEditorActivity.kt")
        val creation = editor.substringAfter("val blankFaceIsRound").substringBefore("DisposableEffect(session)")

        assertFalse(editor.contains("private fun WatchFormatChoicePage("))
        assertFalse(creation.contains("pendingFormatChoice"))
        assertTrue(creation.contains("formatVersion = WatchFormatVersion.V1"))
    }

    private fun source(relative: String): String = TestSourceRoots.read(relative)
}
