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
import com.flue.launcher.watchface.WatchFaceClockStyle
import com.flue.launcher.watchface.WatchFaceMd3eShape
import kotlinx.coroutines.flow.first

/**
 * 锁屏表盘只需要「当前表盘长什么样」这七项，不需要 [LauncherViewModel] 那 4451 行里的
 * 应用列表、通知监听、传感器状态。所以这里单独从 DataStore 读一份最小快照，
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
)

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
        clockStyle = parseClockStyle(this[LauncherViewModel.KEY_PHOTO_CLOCK_STYLE]),
        showSeconds = this[LauncherViewModel.KEY_PHOTO_SHOW_SECONDS] ?: false,
        customText = this[LauncherViewModel.KEY_PHOTO_CUSTOM_TEXT].orEmpty(),
        fontPath = fontPath,
        md3eShape = parseMd3eShape(this[LauncherViewModel.KEY_PHOTO_MD3E_SHAPE]),
        useThemeTextColor = this[LauncherViewModel.KEY_PHOTO_USE_THEME_TEXT_COLOR] ?: true,
        textColorArgb = this[LauncherViewModel.KEY_PHOTO_TEXT_COLOR] ?: 0xFFFFFFFF.toInt(),
        md3eAutoColors = this[LauncherViewModel.KEY_PHOTO_MD3E_AUTO_COLORS] ?: true,
        md3eTextColorArgb = this[LauncherViewModel.KEY_PHOTO_MD3E_TEXT_COLOR] ?: 0xFF202938.toInt(),
        md3eFaceColorArgb = this[LauncherViewModel.KEY_PHOTO_MD3E_FACE_COLOR] ?: 0xFFEAF1FF.toInt(),
        md3eHourHandColorArgb = this[LauncherViewModel.KEY_PHOTO_MD3E_HOUR_COLOR] ?: 0xFF334155.toInt(),
        md3eMinuteHandColorArgb = this[LauncherViewModel.KEY_PHOTO_MD3E_MINUTE_COLOR] ?: 0xFF5F84B6.toInt(),
        md3eSecondHandColorArgb = this[LauncherViewModel.KEY_PHOTO_MD3E_SECOND_COLOR] ?: 0xFF806EA4.toInt(),
    )

    val videoOptions = BuiltInWatchFaceOptions(
        clockPosition = parseClockPosition(this[LauncherViewModel.KEY_VIDEO_CLOCK_POSITION]),
        clockSizeSp = (this[LauncherViewModel.KEY_VIDEO_CLOCK_SIZE] ?: 64).coerceIn(28, 92),
        boldClock = this[LauncherViewModel.KEY_VIDEO_CLOCK_BOLD] ?: false,
        cropToFill = this[LauncherViewModel.KEY_VIDEO_FILL_SCREEN] ?: true,
        clockColorMode = parseClockColorMode(this[LauncherViewModel.KEY_VIDEO_CLOCK_COLOR_MODE]),
        clockStyle = parseClockStyle(this[LauncherViewModel.KEY_VIDEO_CLOCK_STYLE]),
        showSeconds = this[LauncherViewModel.KEY_VIDEO_SHOW_SECONDS] ?: false,
        customText = this[LauncherViewModel.KEY_VIDEO_CUSTOM_TEXT].orEmpty(),
        fontPath = fontPath,
        md3eShape = parseMd3eShape(this[LauncherViewModel.KEY_VIDEO_MD3E_SHAPE]),
        useThemeTextColor = this[LauncherViewModel.KEY_VIDEO_USE_THEME_TEXT_COLOR] ?: true,
        textColorArgb = this[LauncherViewModel.KEY_VIDEO_TEXT_COLOR] ?: 0xFFFFFFFF.toInt(),
        md3eAutoColors = this[LauncherViewModel.KEY_VIDEO_MD3E_AUTO_COLORS] ?: true,
        md3eTextColorArgb = this[LauncherViewModel.KEY_VIDEO_MD3E_TEXT_COLOR] ?: 0xFF202938.toInt(),
        md3eFaceColorArgb = this[LauncherViewModel.KEY_VIDEO_MD3E_FACE_COLOR] ?: 0xFFEAF1FF.toInt(),
        md3eHourHandColorArgb = this[LauncherViewModel.KEY_VIDEO_MD3E_HOUR_COLOR] ?: 0xFF334155.toInt(),
        md3eMinuteHandColorArgb = this[LauncherViewModel.KEY_VIDEO_MD3E_MINUTE_COLOR] ?: 0xFF5F84B6.toInt(),
        md3eSecondHandColorArgb = this[LauncherViewModel.KEY_VIDEO_MD3E_SECOND_COLOR] ?: 0xFF806EA4.toInt(),
    )

    return LockScreenFaceConfig(
        watchFaceId = this[LauncherViewModel.KEY_SELECTED_WATCHFACE_ID]
            ?.takeIf { it.isNotBlank() }
            ?: BUILT_IN_WATCHFACE_ID,
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

private fun parseClockStyle(raw: String?): WatchFaceClockStyle =
    runCatching { WatchFaceClockStyle.valueOf(raw.orEmpty()) }.getOrDefault(WatchFaceClockStyle.DIGITAL)

private fun parseMd3eShape(raw: String?): WatchFaceMd3eShape =
    runCatching { WatchFaceMd3eShape.valueOf(raw.orEmpty()) }.getOrDefault(WatchFaceMd3eShape.COOKIE)
