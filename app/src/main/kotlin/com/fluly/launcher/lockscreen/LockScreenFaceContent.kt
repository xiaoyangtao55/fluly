package com.fluly.launcher.lockscreen

import android.content.Context
import com.fluly.launcher.watchface.BUILT_IN_WATCHFACE_ID
import com.fluly.launcher.watchface.WatchFaceDescriptor
import com.fluly.launcher.watchface.WatchFaceDescriptors
import com.fluly.launcher.watchface.jbwatch.JbWatchFaceHost
import com.fluly.launcher.watchface.jbwatch.JbWatchFaceStorage
import com.fluly.launcher.watchface.standard.WatchmakerWatchFaceHost
import com.fluly.launcher.watchface.standard.WatchmakerWatchFaceStorage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fluly.launcher.ui.common.isFlulyScreenRound
import com.fluly.launcher.ui.home.WatchFaceLayer
import com.fluly.launcher.ui.theme.ProvideGlobalUiScale
import com.fluly.launcher.ui.theme.WatchLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 DataStore 快照解析锁屏要渲染的表盘 descriptor。
 *
 * 内置 + JB + WatchMaker 三路扫描与主桌面同一套存储，解析失败（null）由调用方
 * 各自回落 —— 锁屏侧回落默认内置表盘，且不改写用户选择。
 */
internal suspend fun resolveLockScreenFaceDescriptor(
    context: Context,
    snapshot: LockScreenFaceConfig,
): WatchFaceDescriptor? = withContext(Dispatchers.IO) {
    resolveLockScreenFace(
        snapshot.watchFaceId,
        WatchFaceDescriptors.builtInDescriptors(context) +
            runCatching { JbWatchFaceStorage.scan(context) }.getOrDefault(emptyList()) +
            runCatching { WatchmakerWatchFaceStorage.scan(context) }.getOrDefault(emptyList()),
    )
}

/**
 * 锁屏表盘的渲染主体，锁屏 Activity 与悬浮窗（[LockScreenFaceOverlay]）共用。
 *
 * 只负责「给定快照 + descriptor 画什么」，不负责配置加载与可见性门控 ——
 * 那两件事由宿主按各自的生命周期决定（Activity 随 resume/pause，悬浮窗随
 * SCREEN_ON/OFF + Keyguard 状态）。熄屏时 `faceVisible=false` 会暂停渲染。
 *
 * 认证之前的三条铁律（宿主侧同样约束，这里保底）：
 * - 通知角标/状态指示不出现（`hasNotifications=false`）
 * - 长按/双击不接（那两个动作属于桌面）
 * - 原生 WatchMaker 表盘不得弹权限请求（`allowPermissionRequests=false`）
 */
@Composable
internal fun LockScreenFaceContent(
    snapshot: LockScreenFaceConfig,
    descriptor: WatchFaceDescriptor?,
    faceVisible: Boolean,
    onFaceLoadFailure: () -> Unit,
) {
    WatchLauncherTheme(themeMode = snapshot.themeMode, uiStyle = snapshot.uiStyle) {
        ProvideGlobalUiScale(snapshot.globalUiScalePercent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                val selected = descriptor
                when {
                    selected?.isWatchmaker == true -> WatchmakerWatchFaceHost(
                        descriptor = selected,
                        isFaceVisible = faceVisible,
                        refreshToken = 0,
                        onLongPress = null,
                        onDoubleTap = null,
                        onTapAction = {},
                        tapHapticsEnabled = false,
                        allowPermissionRequests = false,
                        canvasSettings = snapshot.canvasSettings,
                        roundScreen = isFlulyScreenRound(),
                        gravityParallaxEnabled = snapshot.gravityParallaxEnabled,
                        gravityBackdropBlurEnabled = snapshot.gravityBackdropBlurEnabled,
                        onLoadFailure = { _, _ -> onFaceLoadFailure() },
                    )
                    selected?.isJbWatch == true -> JbWatchFaceHost(
                        descriptor = selected,
                        isFaceVisible = faceVisible,
                        refreshToken = 0,
                        onLoadFailure = { _, _ -> onFaceLoadFailure() },
                    )
                    else -> WatchFaceLayer(
                        watchFaceId = selected?.id ?: BUILT_IN_WATCHFACE_ID,
                        photoPath = snapshot.photoPath,
                        videoPath = snapshot.videoPath,
                        isFaceVisible = faceVisible,
                        uiStyle = snapshot.uiStyle,
                        photoOptions = snapshot.photoOptions,
                        videoOptions = snapshot.videoOptions,
                        // 锁屏在认证之前：通知角标、状态指示都不该出现。
                        showStatusIndicators = false,
                        showBottomFade = false,
                        hasNotifications = false,
                        // 锁屏上不接长按/双击：那两个动作属于桌面。
                        onLongPress = null,
                        onDoubleTap = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
