package com.fluly.launcher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterRemovalContractTest {
    private val root = sequenceOf(
        File("src/main/kotlin"),
        File("app/src/main/kotlin"),
        File("../app/src/main/kotlin"),
    ).first(File::isDirectory)

    private fun source(relative: String) = File(root, "com/example/wlauncher/$relative").readText()

    @Test
    fun `removed panel has no navigation state gesture or settings entry`() {
        assertFalse(source("ui/navigation/ScreenState.kt").contains("ControlCenter"))
        assertFalse(source("ui/navigation/GestureHost.kt").contains("OverlayGestureTarget.ControlCenter"))
        assertFalse(source("LauncherActivity.kt").contains("ControlCenterLayer"))
        assertFalse(source("SettingsActivity.kt").contains("CONTROL_CENTER_SETTINGS"))
        // 设置条目与搜索索引都已分居两处，两处都不能再出现这个面板。
        assertFalse(source("ui/settings/SettingsHierarchy.kt").contains("CONTROL_CENTER_SETTINGS"))
        assertFalse(source("ui/settings/SettingsSearchIndex.kt").contains("CONTROL_CENTER_SETTINGS"))
        assertFalse(source("viewmodel/LauncherViewModel.kt").contains("setShowControlCenter"))
    }

    @Test
    fun `music card and its settings remain on the side screen`() {
        assertTrue(source("ui/smartstack/SmartStackLayer.kt").contains("SideScreenMusicCard("))
        assertTrue(source("ui/smartstack/SideScreenMusicCard.kt").contains("if (!active) return@LaunchedEffect"))
        val searchIndex = source("ui/settings/SettingsSearchIndex.kt")
        assertTrue(searchIndex.contains("SettingsSearchEntry(\"SIDE_SCREEN\", \"music_controls_toggle\""))
        assertTrue(searchIndex.contains("SettingsSearchEntry(\"SIDE_SCREEN\", \"media_custom_actions_toggle\""))
    }
}
