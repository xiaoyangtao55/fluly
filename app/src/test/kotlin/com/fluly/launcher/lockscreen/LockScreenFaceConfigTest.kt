package com.fluly.launcher.lockscreen

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.fluly.launcher.ui.theme.ThemeMode
import com.fluly.launcher.viewmodel.LauncherViewModel
import com.fluly.launcher.watchface.BUILT_IN_WATCHFACE_ID
import com.fluly.launcher.watchface.WatchClockPosition
import com.fluly.launcher.watchface.standard.WatchCanvasSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 锁屏表盘读的是同一份 DataStore，但走的是另一条解析路径。这条路径一旦和
 * [LauncherViewModel] 的缺省值分叉，表现是「桌面一个样、息屏一个样」——
 * 只有熄屏才看得见，所以用测试把默认值钉住。
 */
class LockScreenFaceConfigTest {

    @Test
    fun `empty preferences reproduce the launcher defaults`() {
        val config = mutablePreferencesOf().toLockScreenFaceConfig()

        assertEquals(BUILT_IN_WATCHFACE_ID, config.watchFaceId)
        assertNull(config.photoPath)
        assertNull(config.videoPath)
        assertEquals(WatchClockPosition.CENTER, config.photoOptions.clockPosition)
        assertEquals(64, config.photoOptions.clockSizeSp)
        assertEquals(0xFFFFFFFF.toInt(), config.photoOptions.textColorArgb)
        assertEquals(true, config.photoOptions.useThemeTextColor)
        assertEquals(ThemeMode.DARK, config.themeMode)
        assertEquals(100, config.globalUiScalePercent)
    }

    @Test
    fun `stored values win and out of range clock size is clamped`() {
        val prefs = mutablePreferencesOf().apply {
            this[LauncherViewModel.KEY_SELECTED_WATCHFACE_ID] = "my_face"
            this[LauncherViewModel.KEY_PHOTO_CLOCK_POSITION] = "TOP_LEFT"
            this[LauncherViewModel.KEY_PHOTO_CLOCK_SIZE] = 200
            this[LauncherViewModel.KEY_BUILTIN_PHOTO_PATH] = "/sdcard/face.jpg"
            this[LauncherViewModel.KEY_GLOBAL_UI_SCALE_PERCENT] = 130
            this[LauncherViewModel.KEY_THEME_MODE] = "LIGHT"
        }

        val config = prefs.toLockScreenFaceConfig()

        assertEquals("my_face", config.watchFaceId)
        assertEquals(WatchClockPosition.TOP_LEFT, config.photoOptions.clockPosition)
        // 越界值按 launcher 的 28..92 区间裁剪，而不是原样透传。
        assertEquals(92, config.photoOptions.clockSizeSp)
        assertEquals("/sdcard/face.jpg", config.photoPath)
        assertEquals(130, config.globalUiScalePercent)
        assertEquals(ThemeMode.LIGHT, config.themeMode)
    }

    @Test
    fun `canvas preferences follow launcher override and legacy precedence`() {
        val prefs = mutablePreferencesOf().apply {
            this[LauncherViewModel.KEY_SELECTED_WATCHFACE_ID] = "custom"
            this[LauncherViewModel.KEY_WATCHMAKER_FORCE_FILL] = false
        }
        assertEquals(WatchCanvasSettings.fromForceFill(false), prefs.toLockScreenFaceConfig().canvasSettings)
        prefs[LauncherViewModel.KEY_WATCHFACE_CANVAS_DEFAULT] = WatchCanvasSettings.GLOBAL_DEFAULT.encode()
        assertEquals(WatchCanvasSettings.GLOBAL_DEFAULT, prefs.toLockScreenFaceConfig().canvasSettings)
        val override = WatchCanvasSettings.fromForceFill(false)
        prefs[LauncherViewModel.KEY_WATCHFACE_CANVAS_OVERRIDES] = "custom=${override.encode()}"
        assertEquals(override, prefs.toLockScreenFaceConfig().canvasSettings)
    }

    @Test
    fun `blank watch face id falls back to the built in face`() {
        val prefs = mutablePreferencesOf().apply {
            this[LauncherViewModel.KEY_SELECTED_WATCHFACE_ID] = "   "
        }

        assertEquals(BUILT_IN_WATCHFACE_ID, prefs.toLockScreenFaceConfig().watchFaceId)
    }
}
