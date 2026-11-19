package com.flue.launcher

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import com.flue.launcher.ui.settings.WatchAppPickerDialog
import com.flue.launcher.ui.settings.WatchTapActionPresetDialog
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.UiStyle
import com.flue.launcher.ui.common.FlueScreenShape
import com.flue.launcher.util.RecentsVisibility
import com.flue.launcher.viewmodel.LauncherViewModel
import com.flue.launcher.watchface.WatchFaceDescriptor
import com.flue.launcher.watchface.editor.WatchEditorHost
import com.flue.launcher.watchface.editor.WatchEditorHostUi
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * [WatchEditorHost] 的启动器实现：把编辑器 / 商店要的能力接到 `LauncherViewModel` 上。
 *
 * 这里自己建一个 **进程级** 的 ViewModel 实例，而不是复用某个 Activity 的：
 * 编辑器、商店、站内浏览器是三个独立 Activity，各自的 ViewModel 是隔离的，
 * 谁先打开就得让其它两个看到同一份表盘列表。偏好类的东西本来就来自 DataStore，
 * 多一个实例不会读到旧值。
 */
class WatchEditorHostImpl(private val application: Application) : WatchEditorHost {

    private val viewModel: LauncherViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(LauncherViewModel::class.java)
    }

    override val themeMode: StateFlow<ThemeMode> get() = viewModel.themeMode
    override val uiStyle: StateFlow<UiStyle> get() = viewModel.uiStyle
    override val screenShapeOverride: StateFlow<FlueScreenShape> get() = viewModel.screenShapeOverride
    override val globalUiScalePercent: StateFlow<Int> get() = viewModel.globalUiScalePercent
    override val rotaryHapticsEnabled: StateFlow<Boolean> get() = viewModel.rotaryHapticsEnabled
    override val watchFaceTapHapticsEnabled: StateFlow<Boolean>
        get() = viewModel.watchFaceTapHapticsEnabled
    override val watchfaceGravityBackdropBlurEnabled: StateFlow<Boolean>
        get() = viewModel.watchfaceGravityBackdropBlurEnabled
    override val availableWatchFaces: StateFlow<List<WatchFaceDescriptor>>
        get() = viewModel.availableWatchFaces

    override suspend fun importWatchmakerWatchFaceFile(file: File): Result<WatchFaceDescriptor> =
        viewModel.importWatchmakerWatchFaceFile(file)

    override suspend fun importWatchFaceArchive(uri: Uri): Result<WatchFaceDescriptor> =
        viewModel.importWatchFaceArchive(uri)

    override suspend fun installWatchStoreArchive(
        archive: File,
        installAsRound: Boolean,
    ): Result<WatchFaceDescriptor> = viewModel.installWatchStoreArchive(archive, installAsRound)

    override fun selectWatchFace(launcherId: String) = viewModel.selectWatchFace(launcherId)

    override suspend fun deleteImportedWatchFaces(ids: Collection<String>): Result<Unit> =
        viewModel.deleteImportedWatchFaces(ids)

    override fun notifyWatchFacesChanged() {
        LauncherViewModel.sharedWatchFaceRefreshToken.value =
            LauncherViewModel.sharedWatchFaceRefreshToken.value + 1
    }

    override fun readHideFromRecents(): Boolean = RecentsVisibility.readPreference(application)

    override fun applyHideFromRecents(activity: Activity, enabled: Boolean) =
        RecentsVisibility.apply(activity, enabled)

    override fun openAgentSettings(activity: Activity) {
        activity.startActivity(
            Intent(activity, SettingsActivity::class.java).apply {
                putExtra(EXTRA_SETTINGS_DESTINATION, SETTINGS_DESTINATION_AGENT)
                putExtra(EXTRA_SETTINGS_RETURN_TO_FACE, true)
            },
        )
    }
}

/**
 * 编辑器里两个选择器弹窗的实现。
 *
 * 应用选择器直接复用设置页那份（带图标缓存），点击动作选择器复用
 * 「设置 → 表盘点击动作」的名单 —— 编辑器写进图层的动作名必须正好是用户能在
 * 设置页绑定应用的那个键。
 */
internal fun installWatchEditorHostUi() {
    WatchEditorHostUi.appPicker = { title, subtitle, onPick, onDismiss ->
        WatchAppPickerDialog(
            title = title,
            subtitle = subtitle,
            selected = null,
            onPick = { app -> onPick(app.label, app.packageName, app.activityClass) },
            onDismiss = onDismiss,
        )
    }
    WatchEditorHostUi.tapActionPicker = { current, onPick, onDismiss ->
        WatchTapActionPresetDialog(
            current = current,
            onPick = onPick,
            onDismiss = onDismiss,
        )
    }
}
