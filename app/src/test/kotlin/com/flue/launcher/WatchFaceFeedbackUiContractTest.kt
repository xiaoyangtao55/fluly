package com.flue.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFaceFeedbackUiContractTest {
    @Test
    fun `aod settings are a child page instead of an inline appearance card`() {
        val settings = source("com/example/wlauncher/SettingsActivity.kt")
        val hierarchy = source("com/example/wlauncher/ui/settings/SettingsHierarchy.kt")

        // 目的页本身仍是独立子页：枚举里有它，分支渲染 SettingsPageScaffold。
        assertTrue(settings.contains("SettingsDestination.AOD -> {"))
        assertTrue(settings.substringAfter("SettingsDestination.AOD -> {").contains("SettingsPageScaffold("))

        // 入口迁到声明式条目后，跳转不再写字面量 navigateTo(SettingsDestination.X)，
        // 而是由 destination 名字驱动 —— 所以这条断言盯数据，不盯调用点。
        assertTrue(hierarchy.contains("destination = \"AOD\""))
        assertFalse(settings.contains("item(\"aod_settings\") {\n                AodSettingsSection()"))
    }

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
        assertTrue(theme.contains("flueMaterialShapes(appleWatchLauncherStyle(isDark))"))
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
        assertTrue(dialog.contains("FlueSlider("))
    }

    @Test
    fun `app long press menu keeps its original centered card presentation`() {
        val popup = source("com/example/wlauncher/ui/drawer/ShortcutPopup.kt")

        assertTrue(popup.contains("val panelWidth = (maxWidth * 0.78f)"))
        assertTrue(popup.contains("label = \"overlay_scale\""))
        assertTrue(popup.contains("snapshotFlow { animAlpha }"))
        assertFalse(popup.contains("FlueDialog("))
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
