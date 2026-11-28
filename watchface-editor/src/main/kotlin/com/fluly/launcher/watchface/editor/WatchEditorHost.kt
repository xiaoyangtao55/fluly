package com.fluly.launcher.watchface.editor

import android.app.Activity
import android.net.Uri
import com.fluly.launcher.ui.common.FlulyScreenShape
import com.fluly.launcher.ui.theme.ThemeMode
import com.fluly.launcher.ui.theme.UiStyle
import com.fluly.launcher.watchface.WatchFaceDescriptor
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * 创作编辑器要用、但只有宿主才知道的那些事；不提供商店下载或安装能力。
 *
 * 这个模块里所有页面原本都直接拿 `LauncherViewModel`：主题偏好、表盘列表、
 * 安装归档、删除导入、最近任务隐藏，还有「改完表盘通知桌面刷新」。搬进模块之后
 * 这些都得由宿主提供，模块本身不认识 DataStore，也不认识 `LauncherViewModel`。
 *
 * 实现方是 app（见 `com.fluly.launcher.FlulyApplication`），在 `onCreate` 里
 * 通过 [WatchEditorHostProvider.host] 注入。注入时机必须早于任何 Activity 创建。
 */
interface WatchEditorHost {

    /** 界面主题：跟桌面设置页同一份偏好。 */
    val themeMode: StateFlow<ThemeMode>

    /** 控件风格（MIUIX / M3 / watchOS）。 */
    val uiStyle: StateFlow<UiStyle>

    /** 用户手动指定的屏幕形状；空表示跟随系统上报。 */
    val screenShapeOverride: StateFlow<FlulyScreenShape>

    /** 全局 UI 缩放百分比。 */
    val globalUiScalePercent: StateFlow<Int>

    /** 表冠逐格反馈开关。 */
    val rotaryHapticsEnabled: StateFlow<Boolean>

    /** 表盘点击的触感反馈开关。 */
    val watchFaceTapHapticsEnabled: StateFlow<Boolean>

    /** 表盘预览的景深模糊开关。 */
    val watchfaceGravityBackdropBlurEnabled: StateFlow<Boolean>

    /** 已安装的表盘（含内置与导入）。 */
    val availableWatchFaces: StateFlow<List<WatchFaceDescriptor>>

    /** 把用户选中的 `.watch` / WatchMaker 文件导入为可用表盘。 */
    suspend fun importWatchmakerWatchFaceFile(file: File): Result<WatchFaceDescriptor>

    /** 从 `content://` 或文件 Uri 导入一个表盘归档（站外下载走这条路）。 */
    suspend fun importWatchFaceArchive(uri: Uri): Result<WatchFaceDescriptor>

    /** 把某个已安装表盘设为当前表盘。 */
    fun selectWatchFace(launcherId: String)

    /** 删除导入的表盘。 */
    suspend fun deleteImportedWatchFaces(ids: Collection<String>): Result<Unit>

    /**
     * 表盘列表变了（导入、安装、编辑保存），通知桌面重新扫一遍。
     *
     * 原来是直接对 `LauncherViewModel.sharedWatchFaceRefreshToken` 自增。
     */
    fun notifyWatchFacesChanged()

    /** 桌面设置里的「从最近任务隐藏」当前值。 */
    fun readHideFromRecents(): Boolean

    /** 临时覆盖本 Activity 的最近任务可见性。 */
    fun applyHideFromRecents(activity: Activity, enabled: Boolean)

    /** 打开「设置 → Agent」，编辑器里的 Agent 面板用它跳到完整设置页。 */
    fun openAgentSettings(activity: Activity)
}

/**
 * 宿主注入点。没注入时 [require] 会立刻炸 —— 编辑器离开宿主没有意义，
 * 与其静默降级成「主题不对、装不了表盘」，不如在第一次打开就暴露出来。
 */
object WatchEditorHostProvider {

    @Volatile
    var host: WatchEditorHost? = null

    fun require(): WatchEditorHost =
        host ?: error("WatchEditorHost 未注入：FlulyApplication.onCreate 里没设 WatchEditorHostProvider.host")
}
