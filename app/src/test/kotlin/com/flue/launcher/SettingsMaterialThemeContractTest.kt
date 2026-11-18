package com.flue.launcher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMaterialThemeContractTest {
    private fun source(relative: String): String {
        val root = sequenceOf(File("src/main/kotlin"), File("app/src/main/kotlin"), File("../app/src/main/kotlin"))
            .first(File::isDirectory)
        return File(root, "com/example/wlauncher/$relative").readText()
    }

    @Test
    fun `settings theme is scoped and independent of launcher preference`() {
        val settings = source("SettingsActivity.kt")
        val activity = settings.substringBefore("private fun applySettingsEdgeToEdge")
        assertTrue(activity.contains("SettingsMaterialTheme(themeMode = themeMode, screenShape = screenShape)"))
        assertFalse(activity.contains("viewModel.uiStyle.collectAsStateWithLifecycle()"))
        assertFalse(settings.contains("vm.setUiStyle("))
        assertFalse(settings.contains("row(\"ui_style_miuix\")"))
        assertTrue(source("LauncherActivity.kt").contains("WatchLauncherTheme("))
    }

    @Test
    fun `material you uses dynamic colors with an older android fallback`() {
        val theme = source("ui/theme/SettingsMaterialTheme.kt")
        assertTrue(theme.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.S"))
        assertTrue(theme.contains("dynamicDarkColorScheme(context)"))
        assertTrue(theme.contains("dynamicLightColorScheme(context)"))
        assertTrue(theme.contains("if (dark) darkColorScheme() else lightColorScheme()"))
        assertTrue(theme.contains("cardColor = colors.surfaceContainerLow"))
        assertTrue(theme.contains("secondaryTextColor = colors.onSurfaceVariant"))
    }
}
