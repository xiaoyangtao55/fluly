package com.flue.launcher.ui.style

import android.os.Build
import kotlinx.coroutines.flow.Flow

/**
 * 对话框 / 提示覆层要用的全局视觉偏好。
 *
 * 这两个开关在 HOME 里是设置项（存 DataStore），但 :ui-kit 不认识启动器的设置层，
 * 所以由宿主在 `Application.onCreate` 里塞一条 Flow 进来；没塞（Studio、预览、
 * 单测）就走 [FlueOverlayPreferenceSource.default]，与设置项缺省值一致。
 */
data class FlueOverlayPreferences(
    /** 高级材质：API 31+ 的 RenderEffect 背景模糊，关掉就只压暗。 */
    val advancedMaterial: Boolean,
    /** 降低动效：提示不再做位移 / 模糊升起，只淡入。 */
    val reduceMotion: Boolean,
)

/** 宿主注入点。对应 `WLauncher` 的 `KEY_BLUR` / `KEY_REDUCE_DYNAMIC_EFFECTS`。 */
object FlueOverlayPreferenceSource {

    @Volatile
    var flow: Flow<FlueOverlayPreferences>? = null

    /** 设置项的缺省值：API 31+ 默认开高级材质，动效不降。 */
    fun default(): FlueOverlayPreferences = FlueOverlayPreferences(
        advancedMaterial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        reduceMotion = false,
    )
}
