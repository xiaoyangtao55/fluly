package com.flue.studio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.flue.launcher.ui.common.FlueScreenShape
import com.flue.launcher.ui.settings.WatchAppPickerDialog
import com.flue.launcher.ui.settings.WatchTapActionPresetDialog
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.UiStyle
import com.flue.launcher.watchface.WatchFaceDescriptor
import com.flue.launcher.watchface.editor.WatchEditorHost
import com.flue.launcher.watchface.editor.WatchEditorHostUi
import com.flue.launcher.watchface.standard.WatchmakerWatchFaceStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Studio 进程内的服务单例：预览页与编辑器读同一份表盘列表。 */
object StudioServices {
    @Volatile
    lateinit var editorHost: StudioWatchEditorHost
}

/**
 * Studio 版的 [WatchEditorHost]。
 *
 * 编辑器原来接的是 `LauncherViewModel`：主题、工程列表、导入、选中、删除。
 * 搬进 Studio 之后没有桌面进程可借，这里直接落到 `:watchface-runtime` 的表盘存储上，
 * 再给几份静态偏好 —— Studio 只有一套深色主题，也不接管「设为当前表盘」这件事。
 */
class StudioWatchEditorHost(private val context: Context) : WatchEditorHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val faces = MutableStateFlow<List<WatchFaceDescriptor>>(emptyList())

    /** Studio 只有深色；编辑器里的取色与对比度按这一份走。 */
    private val themeModeState = MutableStateFlow(ThemeMode.DARK)
    private val uiStyleState = MutableStateFlow(UiStyle.MATERIAL_3)
    private val screenShapeState = MutableStateFlow(FlueScreenShape.AUTO)
    private val uiScaleState = MutableStateFlow(100)
    private val rotaryHapticsState = MutableStateFlow(true)
    private val tapHapticsState = MutableStateFlow(true)
    private val backdropBlurState = MutableStateFlow(true)

    init {
        refreshFaces()
    }

    override val themeMode: StateFlow<ThemeMode> = themeModeState.asStateFlow()
    override val uiStyle: StateFlow<UiStyle> = uiStyleState.asStateFlow()
    override val screenShapeOverride: StateFlow<FlueScreenShape> = screenShapeState.asStateFlow()
    override val globalUiScalePercent: StateFlow<Int> = uiScaleState.asStateFlow()
    override val rotaryHapticsEnabled: StateFlow<Boolean> = rotaryHapticsState.asStateFlow()
    override val watchFaceTapHapticsEnabled: StateFlow<Boolean> = tapHapticsState.asStateFlow()
    override val watchfaceGravityBackdropBlurEnabled: StateFlow<Boolean> = backdropBlurState.asStateFlow()
    override val availableWatchFaces: StateFlow<List<WatchFaceDescriptor>> = faces.asStateFlow()

    override suspend fun importWatchmakerWatchFaceFile(file: File): Result<WatchFaceDescriptor> =
        runCatching {
            val descriptor = withContext(Dispatchers.IO) {
                WatchmakerWatchFaceStorage.importArchiveFile(context, file)
            }
            refreshFaces()
            descriptor
        }

    override suspend fun importWatchFaceArchive(uri: Uri): Result<WatchFaceDescriptor> =
        runCatching {
            val descriptor = withContext(Dispatchers.IO) {
                WatchmakerWatchFaceStorage.importArchive(context, uri)
            }
            refreshFaces()
            descriptor
        }

    /** Studio 不是桌面，「设为当前表盘」没有落点；编辑器里的这个动作变成空操作。 */
    override fun selectWatchFace(launcherId: String) = Unit

    override suspend fun deleteImportedWatchFaces(ids: Collection<String>): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            WatchmakerWatchFaceStorage.scan(context)
                .filter { it.id in ids }
                .forEach { WatchmakerWatchFaceStorage.delete(context, it) }
        }
        refreshFaces()
    }

    override fun notifyWatchFacesChanged() {
        refreshFaces()
    }

    /** Studio 自己不隐藏最近任务；编辑器里的开关只是读一个恒定值。 */
    override fun readHideFromRecents(): Boolean = false

    override fun applyHideFromRecents(activity: Activity, enabled: Boolean) = Unit

    /** 编辑器里的「去设置」深链到 Studio 的设置页（Agent 的 provider / 工作区都在那）。 */
    override fun openAgentSettings(activity: Activity) {
        activity.startActivity(
            Intent(activity, StudioActivity::class.java)
                .putExtra(StudioActivity.EXTRA_TAB, StudioActivity.TAB_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    private fun refreshFaces() {
        scope.launch {
            runCatching { WatchmakerWatchFaceStorage.scan(context) }
                .onSuccess { faces.value = it }
        }
    }
}

/**
 * 编辑器里两个选择器弹窗的实现。名单与 HOME 共用 [WatchTapActionPresetDialog]，
 * 写进图层的动作名必须在两边是同一个拼法。
 */
internal fun installStudioWatchEditorHostUi() {
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
        WatchTapActionPresetDialog(current = current, onPick = onPick, onDismiss = onDismiss)
    }
}
