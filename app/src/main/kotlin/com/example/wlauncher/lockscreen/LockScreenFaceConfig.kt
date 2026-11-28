package com.flue.launcher.lockscreen

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.flue.launcher.viewmodel.LauncherViewModel
import com.flue.launcher.viewmodel.dataStore
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.UiStyle
import com.flue.launcher.ui.theme.uiStyleFromPreference
import com.flue.launcher.watchface.BUILT_IN_WATCHFACE_ID
import com.flue.launcher.watchface.BuiltInWatchFaceOptions
import com.flue.launcher.watchface.WatchClockColorMode
import com.flue.launcher.watchface.WatchClockPosition
import kotlinx.coroutines.flow.first
import com.flue.launcher.watchface.WatchFaceDescriptor
import com.flue.launcher.watchface.standard.WatchCanvasSettings

/**
 * 锁屏表盘只需要当前表盘的渲染配置，不需要 [LauncherViewModel] 的
 * 应用列表和通知监听。所以这里单独从 DataStore 读一份最小快照，
 * 而不是在锁屏 Activity 里再养一个 ViewModel。
 */
data class LockScreenFaceConfig(
    val watchFaceId: String,
    val photoPath: String?,
    val videoPath: String?,
    val photoOptions: BuiltInWatchFaceOptions,
    val videoOptions: BuiltInWatchFaceOptions,
    val uiStyle: UiStyle,
    val themeMode: ThemeMode,
    val globalUiScalePercent: Int,
    val canvasSettings: WatchCanvasSettings = WatchCanvasSettings.GLOBAL_DEFAULT,
    val gravityParallaxEnabled: Boolean = true,
    val gravityBackdropBlurEnabled: Boolean = false,
)

internal fun resolveLockScreenFace(
    selectedId: String,
    available: List<WatchFaceDescriptor>,
): WatchFaceDescriptor? = available.firstOrNull { it.id == selectedId }
    ?: available.firstOrNull { it.id == BUILT_IN_WATCHFACE_ID }

suspend fun Context.readLockScreenFaceConfig(): LockScreenFaceConfig =
    dataStore.data.first().toLockScreenFaceConfig()

/**
 * 默认值与 [LauncherViewModel] 加载偏好时的缺省值逐项对齐 —— 两处一旦分叉，
 * 锁屏和桌面的同一个表盘会长得不一样，而这种差异只有熄屏才看得见。
 */
internal fun Preferences.toLockScreenFaceConfig(): LockScreenFaceConfig {
    val fontPath = this[LauncherViewModel.KEY_WATCHFACE_FONT_PATH]

    val photoOptions = BuiltInWatchFaceOptions(
        clockPosition = parseClockPosition(this[LauncherViewModel.KEY_PHOTO_CLOCK_POSITION]),
        clockSizeSp = (this[LauncherViewModel.KEY_PHOTO_CLOCK_SIZE] ?: 64).coerceIn(28, 92),
        boldClock = this[LauncherViewModel.KEY_PHOTO_CLOCK_BOLD] ?: false,
        showSeconds = this[LauncherViewModel.KEY_PHOTO_SHOW_SECONDS] ?: false,
        customText = this[LauncherViewModel.KEY_PHOTO_CUSTOM_TEXT].orEmpty(),
        fontPath = fontPath,
        useThemeTextColor = this[LauncherViewModel.KEY_PHOTO_USE_THEME_TEXT_COLOR] ?: true,
        textColorArgb = this[LauncherViewModel.KEY_PHOTO_TEXT_COLOR] ?: 0xFFFFFFFF.toInt(),
    )

    val videoOptions = BuiltInWatchFaceOptions(
        clockPosition = parseClockPosition(this[LauncherViewModel.KEY_VIDEO_CLOCK_POSITION]),
        clockSizeSp = (this[LauncherViewModel.KEY_VIDEO_CLOCK_SIZE] ?: 64).coerceIn(28, 92),
        boldClock = this[LauncherViewModel.KEY_VIDEO_CLOCK_BOLD] ?: false,
        cropToFill = this[LauncherViewModel.KEY_VIDEO_FILL_SCREEN] ?: true,
        clockColorMode = parseClockColorMode(this[LauncherViewModel.KEY_VIDEO_CLOCK_COLOR_MODE]),
        showSeconds = this[LauncherViewModel.KEY_VIDEO_SHOW_SECONDS] ?: false,
        customText = this[LauncherViewModel.KEY_VIDEO_CUSTOM_TEXT].orEmpty(),
        fontPath = fontPath,
        useThemeTextColor = this[LauncherViewModel.KEY_VIDEO_USE_THEME_TEXT_COLOR] ?: true,
        textColorArgb = this[LauncherViewModel.KEY_VIDEO_TEXT_COLOR] ?: 0xFFFFFFFF.toInt(),
    )

    val selectedId = this[LauncherViewModel.KEY_SELECTED_WATCHFACE_ID]
        ?.takeIf { it.isNotBlank() } ?: BUILT_IN_WATCHFACE_ID
    val canvasDefault = this[LauncherViewModel.KEY_WATCHFACE_CANVAS_DEFAULT]
        ?.let(WatchCanvasSettings::decode)
        ?: this[LauncherViewModel.KEY_WATCHMAKER_FORCE_FILL]?.let(WatchCanvasSettings::fromForceFill)
        ?: WatchCanvasSettings.GLOBAL_DEFAULT
    val canvasOverrides = this[LauncherViewModel.KEY_WATCHFACE_CANVAS_OVERRIDES]
        .orEmpty().lineSequence().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null
            else line.substring(0, separator) to WatchCanvasSettings.decode(line.substring(separator + 1))
        }.toMap()
    return LockScreenFaceConfig(
        watchFaceId = selectedId,
        canvasSettings = canvasOverrides[selectedId] ?: canvasDefault,
        gravityParallaxEnabled = this[LauncherViewModel.KEY_WATCHFACE_GRAVITY_PARALLAX] ?: true,
        gravityBackdropBlurEnabled = this[LauncherViewModel.KEY_WATCHFACE_GRAVITY_BACKDROP_BLUR] ?: false,
        photoPath = this[LauncherViewModel.KEY_BUILTIN_PHOTO_PATH],
        videoPath = this[LauncherViewModel.KEY_BUILTIN_VIDEO_PATH],
        photoOptions = photoOptions,
        videoOptions = videoOptions,
        uiStyle = uiStyleFromPreference(this[LauncherViewModel.KEY_UI_STYLE]),
        themeMode = runCatching { ThemeMode.valueOf(this[LauncherViewModel.KEY_THEME_MODE].orEmpty()) }
            .getOrDefault(ThemeMode.DARK),
        globalUiScalePercent = (this[LauncherViewModel.KEY_GLOBAL_UI_SCALE_PERCENT] ?: 100)
            .coerceIn(50, 150),
    )
}

private fun parseClockPosition(raw: String?): WatchClockPosition =
    runCatching { WatchClockPosition.valueOf(raw.orEmpty()) }.getOrDefault(WatchClockPosition.CENTER)

private fun parseClockColorMode(raw: String?): WatchClockColorMode =
    runCatching { WatchClockColorMode.valueOf(raw.orEmpty()) }.getOrDefault(WatchClockColorMode.AUTO)
