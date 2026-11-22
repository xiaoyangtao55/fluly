package com.flue.launcher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flue.launcher.applist.background.AppListBackgroundMode
import com.flue.launcher.applist.background.cropAndStoreAppListBackground
import com.flue.launcher.backup.FlueBackupOptions
import com.flue.launcher.backup.FlueBackupPreview
import com.flue.launcher.i18n.FlueComponentActivity
import com.flue.launcher.i18n.FlueLocaleManager
import com.flue.launcher.logging.FlueLog
import com.flue.launcher.motion.LocalFlueMotionProfile
import com.flue.launcher.service.FlueAccessibilityService
import com.flue.launcher.service.FlueDeviceAdminController
import com.flue.launcher.service.ScreenLockControlMode
import com.flue.launcher.service.screenLockControlMode
import com.flue.launcher.ui.anim.FlueTransitionMotion
import com.flue.launcher.ui.common.FlueColorPickerDialog
import com.flue.launcher.ui.common.FlueImageCropOverlay
import com.flue.launcher.ui.common.FlueScreenShape
import com.flue.launcher.ui.common.InternalFileBrowserHost
import com.flue.launcher.ui.common.InternalFileBrowserRequest
import com.flue.launcher.ui.common.isFlueScreenRound
import com.flue.launcher.ui.drawer.appListPalette
import com.flue.launcher.ui.navigation.LayoutMode
import com.flue.launcher.ui.settings.AboutCard
import com.flue.launcher.ui.settings.ActionCard
import com.flue.launcher.ui.settings.AppListBackgroundPage
import com.flue.launcher.ui.settings.BackgroundPreviewSources
import com.flue.launcher.ui.settings.BackupOptionRows
import com.flue.launcher.ui.settings.BlockingProgressDialog
import com.flue.launcher.ui.settings.DebugPage
import com.flue.launcher.ui.settings.DebugTapState
import com.flue.launcher.ui.settings.DisplaySizePage
import com.flue.launcher.ui.settings.DonateMethodCard
import com.flue.launcher.ui.settings.DonatePreviewDialog
import com.flue.launcher.ui.settings.EntryIcon
import com.flue.launcher.ui.settings.MessageCard
import com.flue.launcher.ui.settings.MotionProfilePage
import com.flue.launcher.ui.settings.OverlayMaskCornerRadiusDialog
import com.flue.launcher.ui.settings.ProfileRenameDialog
import com.flue.launcher.ui.settings.SectionTitle
import com.flue.launcher.ui.settings.SettingsCategoryCard
import com.flue.launcher.ui.settings.SettingsChoiceRow
import com.flue.launcher.ui.settings.SettingsEntry
import com.flue.launcher.ui.settings.SettingsEntryBinding
import com.flue.launcher.ui.settings.SettingsHierarchy
import com.flue.launcher.ui.settings.SettingsPageScaffold
import com.flue.launcher.ui.settings.SettingsSearchField
import com.flue.launcher.ui.settings.SettingsSliderRow
import com.flue.launcher.ui.settings.SettingsSwitchRow
import com.flue.launcher.ui.settings.WatchFaceSettingCard
import com.flue.launcher.ui.settings.WatchTapActionAppPickerDialog
import com.flue.launcher.ui.settings.WatchTapActionBindingCard
import com.flue.launcher.ui.settings.WatchTapActionCatalog
import com.flue.launcher.ui.settings.WatchTapActionDefinition
import com.flue.launcher.ui.settings.backupSummaryText
import com.flue.launcher.ui.settings.buildSettingsSearchIndex
import com.flue.launcher.ui.settings.exportDoneDetailText
import com.flue.launcher.ui.settings.itemFisheye
import com.flue.launcher.ui.settings.miuix.MiuixIcons
import com.flue.launcher.ui.settings.miuix.SettingsIcon
import com.flue.launcher.ui.settings.searchSettingsEntries
import com.flue.launcher.ui.settings.settingsEntryGroup
import com.flue.launcher.ui.settings.settingsEntryScreen
import com.flue.launcher.ui.settings.valueOfRow
import com.flue.launcher.ui.settings.withRow
import com.flue.launcher.ui.smartstack.MusicTextSwitchAnimations
import com.flue.launcher.ui.style.FlueAlertDialog
import com.flue.launcher.ui.style.FlueLoadingIndicator
import com.flue.launcher.ui.style.FlueSingleChoiceDialog
import com.flue.launcher.ui.style.flueSettingsGroup
import com.flue.launcher.ui.theme.LauncherTheme
import com.flue.launcher.ui.theme.ProvideGlobalUiScale
import com.flue.launcher.ui.theme.SettingsMaterialTheme
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.WatchColors
import com.flue.launcher.util.FlowCrescentDirection
import com.flue.launcher.util.FlueStudioLauncher
import com.flue.launcher.util.RecentsVisibility
import com.flue.launcher.util.flowCrescentOffsetPx
import com.flue.launcher.viewmodel.LauncherViewModel
import com.flue.launcher.watchface.BuiltInWatchFaceOptions
import com.flue.launcher.watchface.InternalWatchFaceStorage
import com.flue.launcher.watchface.WatchFaceDescriptor
import com.flue.launcher.watchface.selection.WatchFaceLongPressMode
import com.flue.launcher.watchface.standard.WatchDateLocale
import com.flue.launcher.watchface.standard.WatchLocationPermissionPolicy
import com.flue.launcher.watchface.standard.WatchTemperatureUnit
import com.flue.launcher.watchface.standard.WatchWeatherLocation
import com.flue.launcher.watchface.standard.WatchWeatherRepository
import com.flue.launcher.watchface.standard.hasHeartRatePermission
import com.flue.launcher.watchface.standard.requiredHeartRatePermission
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons as OfficialMiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add as OfficialAdd
import top.yukonga.miuix.kmp.icon.extended.Carrier as OfficialCarrier
import top.yukonga.miuix.kmp.icon.extended.Folder as OfficialFolder
import top.yukonga.miuix.kmp.icon.extended.Refresh as OfficialRefresh
import top.yukonga.miuix.kmp.icon.extended.Settings as OfficialSettings

internal val ABOUT_VERSION = BuildConfig.VERSION_NAME
internal data class OpenSourceProject(val name: String, val url: String)
internal val OPEN_SOURCE_PROJECTS = listOf(
    OpenSourceProject("compose-miuix-ui/miuix", "https://github.com/compose-miuix-ui/miuix"),
    OpenSourceProject("ailife8881/wftools", "https://gitlab.com/ailife8881/wftools"),
    OpenSourceProject("ailife8881/clox", "https://gitlab.com/ailife8881/clox"),
    OpenSourceProject(
        "ailife8881/watchmaker-community-store",
        "https://gitlab.com/ailife8881/watchmaker-community-store",
    ),
    OpenSourceProject(
        "ailife8881/watchmaker-definition",
        "https://gitlab.com/ailife8881/watchmaker-definition",
    ),
    OpenSourceProject("FFmpeg", "https://ffmpeg.org"),
)
// 两个交流群。第二个是后来开的，容量满了才会用到，所以入口做成「点一下问你进哪个」
// 而不是并排两张卡 —— 关于页在 230dp 圆屏上已经很挤，多一张卡就要多滚一屏。
internal const val COMMUNITY_GROUP_NUMBER = "1097162313"
internal const val COMMUNITY_GROUP_2_NUMBER = "318567840"
internal const val AUTHOR_QQ_NUMBER = "3513903055"
internal const val AUTHOR_BILIBILI_URL = "https://m.bilibili.com/space/1609437970"
private const val DIAGNOSTIC_LOG_DURATION_MS = 5 * 60 * 1000L
const val EXTRA_SETTINGS_DESTINATION = "settings_destination"
const val EXTRA_SETTINGS_RETURN_TO_FACE = "settings_return_to_face"
const val SETTINGS_DESTINATION_WATCH_FACES = "watch_faces"
const val SETTINGS_DESTINATION_TOOLS = "tools"
internal val LocalSettingsLeftSafeInsetPercent = compositionLocalOf { 0 }
internal val LocalSettingsRotaryHapticsEnabled = compositionLocalOf { true }

/**
 * 搜索跳转目标。[token] 每次点击自增，用来让同一个 key 也能重新触发定位。
 * [onConsumed] 由 scaffold 在定位完成后回调，避免返回上一页时又跳一次。
 */
internal data class SettingsSearchTarget(
    val itemKey: String,
    val token: Int,
    val onConsumed: () -> Unit,
)

internal val LocalSettingsSearchTarget = compositionLocalOf<SettingsSearchTarget?> { null }

enum class SettingsDestination {
    ROOT,
    WATCH_FACES,
    WATCH_FACE_MORE,
    TAP_ACTION_CATEGORIES,
    TAP_ACTION_CATEGORY,
    APP_LIST_BACKGROUND,
    HIDDEN_APPS,
    ICON_PACKS,
    APPEARANCE,
    DISPLAY_SIZE,
    FEATURE_TOGGLES,
    SIDE_SCREEN,
    NOTIFICATION_SETTINGS,
    GESTURE_SETTINGS,
    APP_LIST_BEHAVIOR,
    PERFORMANCE,
    PERFORMANCE_RENDERING,
    PERFORMANCE_MOTION,
    PERFORMANCE_SYSTEM,
    MOTION_PROFILE,
    MUSIC_TEXT_ANIMATION,
    TOOLS,
    BACKUP,
    BACKUP_EXPORT,
    BACKUP_IMPORT,
    LANGUAGE,
    ABOUT,
    DEBUG,
    DONATE
}

/**
 * 内置文件选择器给背景图用的扩展名白名单。
 *
 * 与 `InternalFileBrowser` 自己那份 `INTERNAL_IMAGE_EXTENSIONS`（private）刻意分开写：
 * 那份是「能出缩略图的类型」，这份是「`BitmapRegionDecoder` 能区域裁剪的类型」。gif 不在
 * 这里 —— 它能出缩略图，但裁出来只会是第一帧，当背景是个意外结果。
 */
private val APP_LIST_BACKGROUND_IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif", "avif",
)

/** 「更多表盘选项」里那一行的副标题：当前生效的是哪一档。 */
@Composable
private fun appListBackgroundModeLabel(mode: AppListBackgroundMode): String = stringResource(
    when (mode) {
        AppListBackgroundMode.SOLID_SYSTEM -> R.string.settings_app_list_background_solid_system
        AppListBackgroundMode.SOLID_CUSTOM -> R.string.settings_app_list_background_solid_custom
        AppListBackgroundMode.WATCH_FACE -> R.string.settings_app_list_background_watch_face
        AppListBackgroundMode.CUSTOM_IMAGE -> R.string.settings_app_list_background_custom_image
    },
)

private data class SettingsLaunchConfig(
    val initialDestination: SettingsDestination = SettingsDestination.ROOT,
    val returnToFaceOnBack: Boolean = false,
    val requestId: Long = 0L
)

internal data class BlockingProgressState(
    val title: String,
    /** 副标题。为空时只显示标题（导出完成不再回显文件路径）。 */
    val detail: String? = null,
    val completed: Boolean = false,
    val completionAction: BlockingProgressCompletionAction = BlockingProgressCompletionAction.None,
    val shareUri: Uri? = null
)

internal enum class BlockingProgressCompletionAction {
    None,
    ExportBackup,
    ImportBackup,
    WatchFaceArchiveImport
}

class SettingsActivity : FlueComponentActivity() {
    private var launchRequestId = 0L
    private var launchConfig by mutableStateOf(SettingsLaunchConfig())
    private var appliedWindowBackgroundArgb: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySettingsEdgeToEdge(
            darkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES,
        )
        RecentsVisibility.apply(this)
        launchConfig = intent.readSettingsLaunchConfig()
        setContent {
            val viewModel: LauncherViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val globalUiScalePercent by viewModel.globalUiScalePercent.collectAsStateWithLifecycle()
            val screenShape by viewModel.screenShapeOverride.collectAsStateWithLifecycle()
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemInDarkTheme
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            SettingsMaterialTheme(themeMode = themeMode, screenShape = screenShape) {
                val windowBackgroundArgb = LauncherTheme.style.screenBackground.toArgb()
                SideEffect {
                    applySettingsEdgeToEdge(
                        darkTheme = darkTheme,
                        windowBackgroundArgb = windowBackgroundArgb,
                    )
                }
                ProvideGlobalUiScale(globalUiScalePercent) {
                    SettingsRootScreen(
                        onFinish = { finish() },
                        launchConfig = launchConfig
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applySettingsEdgeToEdge(
        darkTheme: Boolean,
        windowBackgroundArgb: Int = if (darkTheme) AndroidColor.BLACK else 0xFFF7F7F7.toInt(),
    ) {
        // Settings draws its own opaque theme background. Keep the window edge-to-edge so
        // transparent status/navigation bars reveal that background instead of the theme's
        // black windowBackground. WindowCompat also works for gesture and three-button nav.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (appliedWindowBackgroundArgb != windowBackgroundArgb) {
            window.setBackgroundDrawable(ColorDrawable(windowBackgroundArgb))
            appliedWindowBackgroundArgb = windowBackgroundArgb
        }
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchConfig = intent.readSettingsLaunchConfig()
    }

    private fun Intent.readSettingsLaunchConfig(): SettingsLaunchConfig {
        launchRequestId += 1
        val initialDestination = when (getStringExtra(EXTRA_SETTINGS_DESTINATION)) {
            SETTINGS_DESTINATION_WATCH_FACES -> SettingsDestination.WATCH_FACES
            SETTINGS_DESTINATION_TOOLS -> SettingsDestination.TOOLS
            else -> SettingsDestination.ROOT
        }
        return SettingsLaunchConfig(
            initialDestination = initialDestination,
            returnToFaceOnBack = getBooleanExtra(EXTRA_SETTINGS_RETURN_TO_FACE, false),
            requestId = launchRequestId
        )
    }
}

private fun initialSettingsBackStack(
    initialDestination: SettingsDestination,
    returnToFaceOnBack: Boolean
): List<SettingsDestination> {
    return if (initialDestination != SettingsDestination.ROOT && !returnToFaceOnBack) {
        listOf(SettingsDestination.ROOT)
    } else {
        emptyList()
    }
}

@Composable
private fun SettingsRootScreen(
    onFinish: () -> Unit,
    launchConfig: SettingsLaunchConfig = SettingsLaunchConfig()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsScope = rememberCoroutineScope()
    val vm: LauncherViewModel = viewModel()
    val weatherRepository = remember(context) { WatchWeatherRepository.default(context) }
    var weatherLocationLabel by remember(weatherRepository) {
        mutableStateOf(weatherRepository.activeLocationLabel().orEmpty())
    }
    var weatherUsingSystemLocation by remember(weatherRepository) {
        mutableStateOf(weatherRepository.isUsingSystemLocation())
    }
    var weatherTemperatureUnit by remember(weatherRepository) {
        mutableStateOf(weatherRepository.temperatureUnit())
    }
    var weatherLocationSelectionGeneration by remember { mutableStateOf(0L) }
    var weatherCityDialogVisible by remember { mutableStateOf(false) }
    var watchFaceLongPressModeDialogVisible by remember { mutableStateOf(false) }
    var weatherCityDraft by remember { mutableStateOf("") }
    var weatherCityCandidates by remember { mutableStateOf<List<WatchWeatherLocation>>(emptyList()) }
    var weatherCitySearching by remember { mutableStateOf(false) }
    var weatherCitySearchError by remember { mutableStateOf<String?>(null) }
    var debugTapState by remember { mutableStateOf(DebugTapState()) }
    var internalFileBrowserRequest by remember { mutableStateOf<InternalFileBrowserRequest?>(null) }
    val rawWatchFaces by vm.availableWatchFaces.collectAsStateWithLifecycle()
    val selectedWatchFaceId by vm.selectedWatchFaceId.collectAsStateWithLifecycle()
    val rawSelectedWatchFace by vm.selectedWatchFace.collectAsStateWithLifecycle()
    // H6：缩放值存在商店那座 UI 偏好小岛里（它本来就是「商店本地 UI 偏好」的归属），
    // 这里只留一份界面镜像；网页宿主 onResume 会自己回读并对当前页 reload。
    var tapActionCategoryId by remember { mutableStateOf<String?>(null) }
    var tapActionPicker by remember { mutableStateOf<WatchTapActionDefinition?>(null) }
    var tapActionRevision by remember { mutableStateOf(0) }
    val watchFaces = rawWatchFaces
    val selectedWatchFace = rawSelectedWatchFace
    val allSelectableApps by vm.allSelectableApps.collectAsStateWithLifecycle()
    val hiddenApps by vm.hiddenApps.collectAsStateWithLifecycle()
    val availableIconPacks by vm.availableIconPacks.collectAsStateWithLifecycle()
    val selectedIconPackPackage by vm.selectedIconPackPackage.collectAsStateWithLifecycle()

    val watchFaceLastError by vm.watchFaceLastError.collectAsStateWithLifecycle()
    val layoutMode by vm.layoutMode.collectAsStateWithLifecycle()
    val sideScreenEnabled by vm.sideScreenEnabled.collectAsStateWithLifecycle()
    val blurEnabled by vm.blurEnabled.collectAsStateWithLifecycle()
    val edgeBlurEnabled by vm.edgeBlurEnabled.collectAsStateWithLifecycle()
    val lowResIcons by vm.lowResIcons.collectAsStateWithLifecycle()
    val animationOverrideEnabled by vm.animationOverrideEnabled.collectAsStateWithLifecycle()
    val splashIcon by vm.splashIcon.collectAsStateWithLifecycle()
    val splashDelay by vm.splashDelay.collectAsStateWithLifecycle()
    val directLaunchAppListEnabled by vm.directLaunchAppListEnabled.collectAsStateWithLifecycle()
    val honeycombEdgeBlurRadius by vm.honeycombEdgeBlurRadius.collectAsStateWithLifecycle()
    val honeycombTopFade by vm.honeycombTopFade.collectAsStateWithLifecycle()
    val honeycombBottomFade by vm.honeycombBottomFade.collectAsStateWithLifecycle()
    val appListLeftSafeInsetPercent by vm.appListLeftSafeInsetPercent.collectAsStateWithLifecycle()
    val activeLayoutProfile by vm.activeLayoutProfile.collectAsStateWithLifecycle()
    val globalUiScalePercent by vm.globalUiScalePercent.collectAsStateWithLifecycle()
    val overlayMaskCornerRadiusDp by vm.overlayMaskCornerRadiusDp.collectAsStateWithLifecycle()
    val appListWatchFaceColors by vm.appListWatchFaceColors.collectAsStateWithLifecycle()
    val appListRowBorderEnabled by vm.appListRowBorderEnabled.collectAsStateWithLifecycle()
    val appListFoldersEnabled by vm.appListFoldersEnabled.collectAsStateWithLifecycle()
    val musicTextSwitchAnimation by vm.musicTextSwitchAnimation.collectAsStateWithLifecycle()
    val iconShadowEnabled by vm.iconShadowEnabled.collectAsStateWithLifecycle()
    val appListReturnShrinkEnabled by vm.appListReturnShrinkEnabled.collectAsStateWithLifecycle()
    val legacyAppListTransitionEnabled by vm.legacyAppListTransitionEnabled.collectAsStateWithLifecycle()
    val appListReturnIconFloatEnabled by vm.appListReturnIconFloatEnabled.collectAsStateWithLifecycle()
    val reduceDynamicEffects by vm.reduceDynamicEffects.collectAsStateWithLifecycle()
    val screenShapeOverride by vm.screenShapeOverride.collectAsStateWithLifecycle()
    val appListStaggerEnabled by vm.appListStaggerEnabled.collectAsStateWithLifecycle()
    val faceReturnShrinkEnabled by vm.faceReturnShrinkEnabled.collectAsStateWithLifecycle()
    val activeMotionProfile by vm.activeMotionProfile.collectAsStateWithLifecycle()
    // 档案重命名弹窗（老师报：复制出来全叫副本，而且没法改名）。
    var renameLayoutProfileVisible by remember { mutableStateOf(false) }
    var renameMotionProfileVisible by remember { mutableStateOf(false) }
    val motionProfiles by vm.motionProfiles.collectAsStateWithLifecycle()
    val showStepCount by vm.showStepCount.collectAsStateWithLifecycle()
    val builtInPhotoPath by vm.builtInPhotoPath.collectAsStateWithLifecycle()
    val builtInVideoPath by vm.builtInVideoPath.collectAsStateWithLifecycle()
    val builtInPhotoClockPosition by vm.builtInPhotoClockPosition.collectAsStateWithLifecycle()
    val builtInVideoClockPosition by vm.builtInVideoClockPosition.collectAsStateWithLifecycle()
    val builtInPhotoClockSize by vm.builtInPhotoClockSize.collectAsStateWithLifecycle()
    val builtInVideoClockSize by vm.builtInVideoClockSize.collectAsStateWithLifecycle()
    val builtInPhotoClockBold by vm.builtInPhotoClockBold.collectAsStateWithLifecycle()
    val builtInVideoClockBold by vm.builtInVideoClockBold.collectAsStateWithLifecycle()
    val builtInVideoFillScreen by vm.builtInVideoFillScreen.collectAsStateWithLifecycle()
    val builtInVideoClockColorMode by vm.builtInVideoClockColorMode.collectAsStateWithLifecycle()
    val builtInWatchFaceFontPath by vm.builtInWatchFaceFontPath.collectAsStateWithLifecycle()
    val builtInPhotoClockStyle by vm.builtInPhotoClockStyle.collectAsStateWithLifecycle()
    val builtInVideoClockStyle by vm.builtInVideoClockStyle.collectAsStateWithLifecycle()
    val builtInPhotoMd3eShape by vm.builtInPhotoMd3eShape.collectAsStateWithLifecycle()
    val builtInVideoMd3eShape by vm.builtInVideoMd3eShape.collectAsStateWithLifecycle()
    val builtInPhotoUseThemeTextColor by vm.builtInPhotoUseThemeTextColor.collectAsStateWithLifecycle()
    val builtInVideoUseThemeTextColor by vm.builtInVideoUseThemeTextColor.collectAsStateWithLifecycle()
    val builtInPhotoTextColorArgb by vm.builtInPhotoTextColorArgb.collectAsStateWithLifecycle()
    val builtInVideoTextColorArgb by vm.builtInVideoTextColorArgb.collectAsStateWithLifecycle()
    val builtInPhotoMd3eAutoColors by vm.builtInPhotoMd3eAutoColors.collectAsStateWithLifecycle()
    val builtInVideoMd3eAutoColors by vm.builtInVideoMd3eAutoColors.collectAsStateWithLifecycle()
    val builtInPhotoMd3eTextColorArgb by vm.builtInPhotoMd3eTextColorArgb.collectAsStateWithLifecycle()
    val builtInVideoMd3eTextColorArgb by vm.builtInVideoMd3eTextColorArgb.collectAsStateWithLifecycle()
    val builtInPhotoMd3eFaceColorArgb by vm.builtInPhotoMd3eFaceColorArgb.collectAsStateWithLifecycle()
    val builtInVideoMd3eFaceColorArgb by vm.builtInVideoMd3eFaceColorArgb.collectAsStateWithLifecycle()
    val builtInPhotoMd3eHourColorArgb by vm.builtInPhotoMd3eHourColorArgb.collectAsStateWithLifecycle()
    val builtInVideoMd3eHourColorArgb by vm.builtInVideoMd3eHourColorArgb.collectAsStateWithLifecycle()
    val builtInPhotoMd3eMinuteColorArgb by vm.builtInPhotoMd3eMinuteColorArgb.collectAsStateWithLifecycle()
    val builtInVideoMd3eMinuteColorArgb by vm.builtInVideoMd3eMinuteColorArgb.collectAsStateWithLifecycle()
    val builtInPhotoMd3eSecondColorArgb by vm.builtInPhotoMd3eSecondColorArgb.collectAsStateWithLifecycle()
    val builtInVideoMd3eSecondColorArgb by vm.builtInVideoMd3eSecondColorArgb.collectAsStateWithLifecycle()
    val builtInPhotoShowSeconds by vm.builtInPhotoShowSeconds.collectAsStateWithLifecycle()
    val builtInVideoShowSeconds by vm.builtInVideoShowSeconds.collectAsStateWithLifecycle()
    val builtInPhotoCustomText by vm.builtInPhotoCustomText.collectAsStateWithLifecycle()
    val builtInVideoCustomText by vm.builtInVideoCustomText.collectAsStateWithLifecycle()
    val builtInManagerThumbnails by vm.builtInManagerThumbnails.collectAsStateWithLifecycle()
    val hideFromRecents by vm.hideFromRecents.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val uiStyle by vm.uiStyle.collectAsStateWithLifecycle()
    // 转场动画风格独立于界面风格（老师定）：这一页要同时读两者。
    val transitionMotion by vm.transitionMotion.collectAsStateWithLifecycle()
    val launcherStyle = LauncherTheme.style
    val flueScreenRound = isFlueScreenRound()
    val showNotification by vm.showNotification.collectAsStateWithLifecycle()
    val showOngoingNotifications by vm.showOngoingNotifications.collectAsStateWithLifecycle()
    val watchFaceTapHapticsEnabled by vm.watchFaceTapHapticsEnabled.collectAsStateWithLifecycle()
    val rotaryHapticsEnabled by vm.rotaryHapticsEnabled.collectAsStateWithLifecycle()
    val showWidgetPage by vm.showWidgetPage.collectAsStateWithLifecycle()
    val glassHighlightEnabled by vm.glassHighlightEnabled.collectAsStateWithLifecycle()
    val lockScreenWatchFaceEnabled by vm.lockScreenWatchFaceEnabled.collectAsStateWithLifecycle()
    val pullDownControlCenterEnabled by vm.pullDownControlCenterEnabled.collectAsStateWithLifecycle()
    val widgetGlassHighlightEnabled by vm.widgetGlassHighlightEnabled.collectAsStateWithLifecycle()
    val showMusicControls by vm.showMusicControls.collectAsStateWithLifecycle()
    val showMediaCustomActions by vm.showMediaCustomActions.collectAsStateWithLifecycle()
    val swapMusicNotificationComponents by vm.swapMusicNotificationComponents.collectAsStateWithLifecycle()
    val doubleTapLockScreenEnabled by vm.doubleTapLockScreenEnabled.collectAsStateWithLifecycle()
    val faceIgnoresBackKey by vm.faceIgnoresBackKey.collectAsStateWithLifecycle()
    val watchFaceChargingPowerText by vm.watchFaceChargingPowerText.collectAsStateWithLifecycle()
    val watchFaceTextEntryAnimation by vm.watchFaceTextEntryAnimation.collectAsStateWithLifecycle()
    val watchFaceTextExitAnimation by vm.watchFaceTextExitAnimation.collectAsStateWithLifecycle()
    val watchFaceStatusIndicators by vm.watchFaceStatusIndicators.collectAsStateWithLifecycle()
    val watchFaceDndIndicator by vm.watchFaceDndIndicator.collectAsStateWithLifecycle()
    val watchFaceBottomFadeEnabled by vm.watchFaceBottomFadeEnabled.collectAsStateWithLifecycle()
    val watchFaceLongPressMode by vm.watchFaceLongPressMode.collectAsStateWithLifecycle()
    val watchfaceGravityParallaxEnabled by vm.watchfaceGravityParallaxEnabled.collectAsStateWithLifecycle()
    val watchfaceGravityBackdropBlurEnabled by
        vm.watchfaceGravityBackdropBlurEnabled.collectAsStateWithLifecycle()
    val watchfaceDateLocale by vm.watchfaceDateLocale.collectAsStateWithLifecycle()
    val appLanguage by vm.appLanguage.collectAsStateWithLifecycle()
    // 表盘保存会原地覆盖 preview.jpg：令牌自增时列表缩略图重读文件。
    val watchFaceRefreshToken by vm.watchFaceRefreshToken.collectAsStateWithLifecycle()
    val accessibilityServiceConnected by FlueAccessibilityService.connected.collectAsStateWithLifecycle()
    var accessibilityServiceEnabled by remember {
        mutableStateOf(FlueAccessibilityService.isEnabled(context) || accessibilityServiceConnected)
    }
    val doubleTapLockMode = remember { screenLockControlMode(Build.VERSION.SDK_INT) }
    val doubleTapUsesDeviceAdmin = doubleTapLockMode == ScreenLockControlMode.DEVICE_ADMIN
    var deviceAdminEnabled by remember {
        mutableStateOf(FlueDeviceAdminController.isActive(context))
    }
    // The current settings header no longer renders a clock. Keeping a ticking
    // state here used to invalidate the whole active page every 30 seconds.
    val headerTime = ""
    var backupExportOptions by remember { mutableStateOf(FlueBackupOptions()) }
    var pendingBackupExportOptions by remember { mutableStateOf<FlueBackupOptions?>(null) }
    var backupImportUri by remember { mutableStateOf<Uri?>(null) }
    var backupImportPreview by remember { mutableStateOf<FlueBackupPreview?>(null) }
    var backupImportOptions by remember { mutableStateOf(FlueBackupOptions()) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupProgress by remember { mutableStateOf<BlockingProgressState?>(null) }
    var bodySensorsPermissionGranted by remember(context) {
        mutableStateOf(hasBodySensorsPermission(context))
    }
    val bodySensorsPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        bodySensorsPermissionGranted = granted || hasBodySensorsPermission(context)
        if (!bodySensorsPermissionGranted) {
            val canAskAgain = (context as? Activity)?.let { activity ->
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    requiredHeartRatePermission(),
                )
            } == true
            if (canAskAgain) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_body_sensors_permission_denied),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_body_sensors_permission_settings),
                    Toast.LENGTH_LONG,
                ).show()
                openHeartRatePermissionSettings(context)
            }
        }
    }
    var locationPermissionGranted by remember(context) {
        mutableStateOf(WatchLocationPermissionPolicy.isGranted(context))
    }
    // 表盘日程（{cex} / {cNxx}）。与心率完全同一套：只有用户主动点这一行才申请
    // READ_CALENDAR，没授权时那些标签渲染 `--`，一次查询都不会发。
    var calendarPermissionGranted by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        calendarPermissionGranted = granted ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!calendarPermissionGranted) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_calendar_permission_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    fun resolveAndUseSystemWeatherLocation() {
        val selectionGeneration = ++weatherLocationSelectionGeneration
        settingsScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                runCatching { weatherRepository.resolveSystemLocation() }.getOrNull()
            }
            if (selectionGeneration != weatherLocationSelectionGeneration) return@launch
            if (resolved != null) {
                if (!weatherRepository.useSystemLocation(resolved)) return@launch
                weatherLocationLabel = resolved.city.label.orEmpty()
                weatherUsingSystemLocation = true
                Toast.makeText(
                    context,
                    resolved.city.label?.takeIf(String::isNotBlank)?.let { label ->
                        context.getString(R.string.toast_weather_system_location_resolved_fmt, label)
                    } ?: context.getString(R.string.toast_weather_system_location),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_weather_system_location_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        locationPermissionGranted = WatchLocationPermissionPolicy.isGranted(context)
        if (locationPermissionGranted) {
            resolveAndUseSystemWeatherLocation()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.toast_weather_location_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val effectiveBackupExportOptions = backupExportOptions
    val effectiveBackupImportPreview = backupImportPreview
    val effectiveBackupImportOptions = remember(backupImportOptions, effectiveBackupImportPreview) {
        val available = effectiveBackupImportPreview?.availableOptions ?: FlueBackupOptions()
        backupImportOptions.constrainedBy(available)
    }
    val fontPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            val savedPath = InternalWatchFaceStorage.copyFont(context, uri)
            if (savedPath.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.toast_font_save_failed), Toast.LENGTH_SHORT).show()
            } else {
                vm.setBuiltInWatchFaceFontPath(savedPath)
                Toast.makeText(context, context.getString(R.string.toast_font_updated), Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun handleWatchFaceArchiveUri(uri: Uri?) {
        if (uri != null) {
            settingsScope.launch {
                backupBusy = true
                backupProgress = BlockingProgressState(
                    title = context.getString(R.string.progress_import_watchface),
                    detail = context.getString(R.string.progress_import_watchface_detail)
                )
                val result = vm.importWatchFaceArchive(uri = uri)
                result
                    .onSuccess { descriptor ->
                        backupProgress = BlockingProgressState(
                            title = context.getString(R.string.progress_import_done),
                            detail = descriptor.displayName,
                            completed = true,
                            completionAction = BlockingProgressCompletionAction.WatchFaceArchiveImport
                        )
                    }
                    .onFailure { error ->
                        backupProgress = null
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_import_failed_fmt,
                                error.message ?: error.javaClass.simpleName
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                backupBusy = false
            }
        }
    }
    val watchFaceArchivePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        handleWatchFaceArchiveUri(uri)
    }
    // ---- 应用列表背景 ------------------------------------------------------
    val appListBackground by vm.appListBackground.collectAsStateWithLifecycle()
    var appListBackgroundColorPickerVisible by remember { mutableStateOf(false) }
    // 拖动中的模糊/遮罩值与预览配置已随整页搬到 AppListBackgroundScreen.kt。
    /** 选好、还没裁剪的图。非空即裁剪覆层可见。 */
    var appListBackgroundCropUri by remember { mutableStateOf<Uri?>(null) }
    val appListBackgroundImagePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        // 选完先进裁剪，不直接落盘：这层图要铺满全屏，不裁就必然是「center-crop 切掉一半」。
        if (uri != null) appListBackgroundCropUri = uri
    }
    // 预览卡要和真机一模一样，所以颜色也走应用列表自己的调色板，而不是设置页的主题色。
    // `useWatchFaceColor = false` 与 LauncherActivity 里那个调用点一致（那边写死 false），
    // 因此 seed 这一参完全不参与运算，给什么都行。
    val appListPreviewDarkMode = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val appListPreviewPalette = appListPalette(
        seed = Color.Transparent,
        darkMode = appListPreviewDarkMode,
        uiStyle = uiStyle,
        useWatchFaceColor = false,
    )
    val backupCreatePicker = rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri ->
        val options = pendingBackupExportOptions
        pendingBackupExportOptions = null
        if (uri != null && options != null) {
            settingsScope.launch {
                backupBusy = true
                backupProgress = BlockingProgressState(
                    title = context.getString(R.string.progress_export_config),
                    detail = context.getString(R.string.progress_export_config_detail)
                )
                val result = vm.exportBackup(uri, options)
                result
                    .onSuccess { summary ->
                        backupProgress = BlockingProgressState(
                            title = context.getString(R.string.progress_export_done),
                            detail = summary.exportDoneDetailText(context),
                            completed = true,
                            completionAction = BlockingProgressCompletionAction.ExportBackup,
                            shareUri = uri
                        )
                    }
                    .onFailure { error ->
                        backupProgress = null
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_export_failed_fmt,
                                error.message ?: error.javaClass.simpleName
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                backupBusy = false
            }
        }
    }
    val backupOpenPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            settingsScope.launch {
                backupBusy = true
                backupProgress = BlockingProgressState(
                    title = context.getString(R.string.progress_read_config),
                    detail = context.getString(R.string.progress_read_config_detail)
                )
                val result = vm.readBackupPreview(uri)
                result
                    .onSuccess { preview ->
                        backupImportUri = uri
                        backupImportPreview = preview
                        backupImportOptions = preview.availableOptions
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_read_failed_fmt,
                                error.message ?: error.javaClass.simpleName
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                backupBusy = false
                backupProgress = null
            }
        }
    }
    val openAccessibilitySettings = remember(context) {
        {
            Toast.makeText(context, context.getString(R.string.toast_grant_accessibility), Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            Unit
        }
    }
    val openDeviceAdminSettings = remember(context) {
        {
            Toast.makeText(
                context,
                context.getString(R.string.toast_grant_device_admin),
                Toast.LENGTH_SHORT,
            ).show()
            if (!FlueDeviceAdminController.requestActivation(context)) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_open_device_admin_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            Unit
        }
    }

    var destination by remember { mutableStateOf(launchConfig.initialDestination) }
    LaunchedEffect(destination) {
        if (destination != SettingsDestination.ABOUT) debugTapState = DebugTapState()
    }
    var backStack by remember {
        mutableStateOf(initialSettingsBackStack(launchConfig.initialDestination, launchConfig.returnToFaceOnBack))
    }
    // 层级转场方向。静态 depth 表描述不了双入口页面（FEATURE_TOGGLES 从 ROOT
    // 进是二级、从 APPEARANCE 进是三级），所以方向只认导航意图：navigateTo
    // 置 false、handleBack 置 true，transitionSpec 直接读。
    var transitionIsBack by remember { mutableStateOf(false) }
    var hiddenAppsDraft by remember { mutableStateOf(hiddenApps) }
    var hiddenAppsDirty by remember { mutableStateOf(false) }
    var donatePreviewResId by remember { mutableStateOf<Int?>(null) }
    // 语言切换需要整进程重启：仅 recreate() 只重建设置页，桌面那边的
    // Activity 仍持有旧 locale 的 Configuration。存待生效的语言码，
    // 用户确认后杀掉进程并冷启。
    var pendingLanguageRestart by remember { mutableStateOf<String?>(null) }
    var languageSaveInProgress by remember { mutableStateOf(false) }
    var diagnosticLoggingEnabled by remember { mutableStateOf(FlueLog.isDiagnosticLoggingEnabled()) }
    var importedWatchFaceDeleteTarget by remember { mutableStateOf<WatchFaceDescriptor?>(null) }
    var showOverlayMaskCornerDialog by remember { mutableStateOf(false) }
    // Scroll positions are restoration bookkeeping, not UI state. A snapshot
    // state map here invalidated the entire settings destination on every
    // scroll pixel because SettingsPageScaffold continuously wrote offsets.
    val pageScrollPositions = remember { SettingsScrollMemory() }
    val pageScrollResetVersions = remember { mutableStateMapOf<SettingsDestination, Int>() }
    // 搜索结果点进去要落在具体那一行上，而不是目标页的页顶。
    var searchTargetItemKey by remember { mutableStateOf<String?>(null) }
    var searchTargetToken by remember { mutableStateOf(0) }

    fun resetPageScroll(target: SettingsDestination) {
        pageScrollPositions.forget(target)
        pageScrollResetVersions[target] = (pageScrollResetVersions[target] ?: 0) + 1
    }

    fun closeBlockingProgressPage() {
        val state = backupProgress
        backupProgress = null
        backupBusy = false
        if (state?.completionAction == BlockingProgressCompletionAction.WatchFaceArchiveImport) {
            // 用 resetPageScroll 而不是只 forget：进度页是叠在内容之上的兄弟组件，
            // 底下那页的 scaffold 一直活着。只清账本时它的 LazyListState 没人动，
            // 页面还停在原处，等它真被销毁时又把旧位置写回来 —— 等于什么都没做。
            // 顺带把 reset 版本号 +1 才会换掉 key，页面重新挂载并落回列表顶部。
            resetPageScroll(SettingsDestination.WATCH_FACES)
            // 导入完成后直接把用户送进表盘列表看结果，算进入而非回退。
            // 不显式写就会沿用上一次导航残留的方向。
            transitionIsBack = false
            destination = SettingsDestination.WATCH_FACES
            if (backStack.isEmpty() && !launchConfig.returnToFaceOnBack) {
                backStack = listOf(SettingsDestination.ROOT)
            }
            settingsScope.launch {
                vm.refreshWatchFaces(force = true)
            }
        }
    }

    importedWatchFaceDeleteTarget?.let { target ->
        FlueAlertDialog(
            onDismissRequest = { importedWatchFaceDeleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_watchface_title)) },
            text = {
                Text(
                    when {
                        target.isWatchmaker ->
                            stringResource(R.string.dialog_delete_watchmaker_fmt, target.displayName)
                        target.isJbWatch ->
                            stringResource(R.string.dialog_delete_jbwatch_fmt, target.displayName)
                        else ->
                            stringResource(R.string.dialog_delete_imported_fmt, target.displayName)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importedWatchFaceDeleteTarget = null
                        settingsScope.launch {
                            val result = vm.deleteImportedWatchFace(target.id)
                            result
                                .onSuccess {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_deleted_fmt, target.displayName),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.toast_delete_failed_fmt,
                                            error.message ?: error.javaClass.simpleName
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { importedWatchFaceDeleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (renameLayoutProfileVisible) {
        ProfileRenameDialog(
            currentName = activeLayoutProfile.name,
            onDismiss = { renameLayoutProfileVisible = false },
            onConfirm = { name ->
                vm.renameActiveLayoutProfile(name)
                renameLayoutProfileVisible = false
            },
        )
    }
    if (renameMotionProfileVisible) {
        ProfileRenameDialog(
            currentName = activeMotionProfile.name,
            onDismiss = { renameMotionProfileVisible = false },
            onConfirm = { name ->
                vm.renameActiveMotionProfile(name)
                renameMotionProfileVisible = false
            },
        )
    }
    if (showOverlayMaskCornerDialog && !flueScreenRound) {
        OverlayMaskCornerRadiusDialog(
            currentRadiusDp = overlayMaskCornerRadiusDp,
            onDismiss = { showOverlayMaskCornerDialog = false },
            onSave = { radiusDp ->
                vm.setOverlayMaskCornerRadiusDp(radiusDp)
                showOverlayMaskCornerDialog = false
            },
        )
    }

    backupProgress?.let { state ->
        BlockingProgressDialog(
            state = state,
            onDismiss = { closeBlockingProgressPage() },
            onShare = { state.shareUri?.let { shareBackupUri(context, it) } }
        )
    }

    InternalFileBrowserHost(
        request = internalFileBrowserRequest,
        onDismiss = { internalFileBrowserRequest = null },
    )

    if (appListBackgroundColorPickerVisible) {
        FlueColorPickerDialog(
            initial = Color(appListBackground.customColorArgb),
            title = stringResource(R.string.settings_app_list_background_color),
            onDismiss = { appListBackgroundColorPickerVisible = false },
            onConfirm = { color ->
                appListBackgroundColorPickerVisible = false
                vm.setAppListBackgroundColor(color.toArgb())
                // 顺手切到自定义色档：老师刚挑完颜色，最不该发生的事是「选了没变化」。
                vm.setAppListBackgroundMode(AppListBackgroundMode.SOLID_CUSTOM)
            },
        )
    }

    appListBackgroundCropUri?.let { pendingUri ->
        FlueImageCropOverlay(
            uri = pendingUri,
            title = stringResource(R.string.app_list_background_crop_title),
            hint = stringResource(R.string.app_list_background_crop_hint),
            // 背景图铺满全屏：裁剪框锁屏幕比例（传 null 就是这个意思）。
            aspectRatio = null,
            onCancel = { appListBackgroundCropUri = null },
            onConfirm = { sourceRect ->
                appListBackgroundCropUri = null
                settingsScope.launch {
                    val savedPath = cropAndStoreAppListBackground(
                        context = context,
                        uri = pendingUri,
                        sourceRect = sourceRect,
                        previousPath = appListBackground.imagePath,
                    )
                    if (savedPath.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_app_list_background_save_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        vm.setAppListBackgroundImagePath(savedPath)
                        vm.setAppListBackgroundMode(AppListBackgroundMode.CUSTOM_IMAGE)
                    }
                }
            },
        )
    }

    LaunchedEffect(hiddenApps) {
        if (!hiddenAppsDirty) {
            hiddenAppsDraft = hiddenApps
        }
    }
    val hideableApps = remember(allSelectableApps) {
        allSelectableApps.filter { app ->
            !app.isBuiltInSettingsEntry && !app.isFolder
        }
    }
    val hiddenHideableCount = remember(hiddenAppsDraft, hideableApps) {
        hideableApps.count { app ->
            hiddenAppsDraft.contains(app.componentKey) || hiddenAppsDraft.contains(app.packageName)
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshWatchFaces()
    }
    val motionExportPicker = rememberLauncherForActivityResult(
        CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val payload = vm.exportMotionProfileLibraryJson().getOrNull()
            if (payload == null) {
                Toast.makeText(context, context.getString(R.string.toast_no_custom_curves), Toast.LENGTH_SHORT).show()
            } else {
                settingsScope.launch(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(payload.toByteArray(Charsets.UTF_8))
                        } ?: error("无法打开导出文件")
                    }.onSuccess {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.toast_curves_exported), Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure { error ->
                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.toast_export_failed_fmt,
                                    error.message ?: error.javaClass.simpleName
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }
    val motionImportPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            settingsScope.launch(Dispatchers.IO) {
                val payloadResult = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取动画曲线文件")
                }
                val result = payloadResult.fold(
                    onSuccess = vm::importMotionProfileJson,
                    onFailure = { Result.failure(it) },
                )
                launch(Dispatchers.Main) {
                    result.onSuccess { imported ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_curves_imported_fmt, imported.size),
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_import_failed_fmt,
                                error.message ?: error.javaClass.simpleName
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
    // 布局档案的导入导出：与上面动效档案那两个 picker 同一套写法（同一个 SAF
    // 契约、同一组 Toast 文案模板），只是换成布局档案库的编解码。
    val layoutExportPicker = rememberLauncherForActivityResult(
        CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val payload = vm.exportLayoutProfileLibraryJson().getOrNull()
            if (payload == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_layout_profiles_export_empty),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                settingsScope.launch(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(payload.toByteArray(Charsets.UTF_8))
                        } ?: error("无法打开导出文件")
                    }.onSuccess {
                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_layout_profiles_exported),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }.onFailure { error ->
                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.toast_export_failed_fmt,
                                    error.message ?: error.javaClass.simpleName,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }
    val layoutImportPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            settingsScope.launch(Dispatchers.IO) {
                val payloadResult = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取布局档案文件")
                }
                val result = payloadResult.fold(
                    onSuccess = vm::importLayoutProfileJson,
                    onFailure = { Result.failure(it) },
                )
                launch(Dispatchers.Main) {
                    result.onSuccess { imported ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_layout_profiles_imported_fmt,
                                imported.size,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_import_failed_fmt,
                                error.message ?: error.javaClass.simpleName,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }
    LaunchedEffect(accessibilityServiceConnected) {
        accessibilityServiceEnabled = FlueAccessibilityService.isEnabled(context) || accessibilityServiceConnected
    }

    DisposableEffect(lifecycleOwner, context, accessibilityServiceConnected, weatherRepository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityServiceEnabled = FlueAccessibilityService.isEnabled(context) || accessibilityServiceConnected
                deviceAdminEnabled = FlueDeviceAdminController.isActive(context)
                bodySensorsPermissionGranted = hasBodySensorsPermission(context)
                locationPermissionGranted = WatchLocationPermissionPolicy.isGranted(context)
                weatherLocationLabel = weatherRepository.activeLocationLabel().orEmpty()
                weatherUsingSystemLocation = weatherRepository.isUsingSystemLocation()
                weatherTemperatureUnit = weatherRepository.temperatureUnit()
                // Studio 可能刚装完/编辑完一个 .watch，回到这一页时重扫一次，
                // 而不是等下次冷启动才看见。
                vm.refreshWatchFaces(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hideFromRecents, context) {
        (context as? Activity)?.let { RecentsVisibility.apply(it, hideFromRecents) }
    }

    val commitHiddenAppsDraft = {
        if (hiddenAppsDirty) {
            vm.setHiddenApps(hiddenAppsDraft)
            hiddenAppsDirty = false
        }
    }

    LaunchedEffect(launchConfig.requestId) {
        if (destination == SettingsDestination.HIDDEN_APPS) {
            commitHiddenAppsDraft()
        }
        destination = launchConfig.initialDestination
        backStack = initialSettingsBackStack(launchConfig.initialDestination, launchConfig.returnToFaceOnBack)
        donatePreviewResId = null
    }

    fun leaveDestination(next: SettingsDestination) {
        if (destination == SettingsDestination.HIDDEN_APPS && next != SettingsDestination.HIDDEN_APPS) {
            commitHiddenAppsDraft()
        }
    }

    val navigateTo: (SettingsDestination) -> Unit = { next ->
        if (next != destination) {
            // backStack 末尾就是 next：这是「往回走一格」，方向按返回算，
            // 否则会把用户熟悉的回退演成前进。
            val poppingBack = backStack.lastOrNull() == next
            // 位置只在「往里进」时清。poppingBack 这一格必须先算出来再判：
            // 退回去的那一页正是用户滑到某处才离开的，清掉就是把他扔回页顶。
            if (settingsScrollResetsOnEnter(destination, next, poppingBack)) {
                resetPageScroll(next)
            }
            leaveDestination(next)
            transitionIsBack = poppingBack
            backStack = if (poppingBack) {
                backStack.dropLast(1)
            } else {
                backStack + destination
            }
            destination = next
        }
    }

    val handleBack: () -> Unit = handleBack@{
        if (backupProgress?.completed == true) {
            closeBlockingProgressPage()
            return@handleBack
        }
        if (backupBusy) {
            return@handleBack
        }
        if (donatePreviewResId != null) {
            donatePreviewResId = null
            return@handleBack
        }
        val previous = backStack.lastOrNull()
        if (previous != null) {
            leaveDestination(previous)
            transitionIsBack = true
            backStack = backStack.dropLast(1)
            destination = previous
        } else {
            if (destination == SettingsDestination.HIDDEN_APPS) {
                commitHiddenAppsDraft()
            }
            onFinish()
        }
    }

    BackHandler(enabled = true) { handleBack() }

    var settingsSearchQuery by remember { mutableStateOf("") }
    // 索引文案走 strings.xml，语言切换会重建 Activity，remember(context) 足够。
    val searchIndex = remember(context) { buildSettingsSearchIndex(context) }
    val selectedIconPackLabel = availableIconPacks.firstOrNull { it.packageName == selectedIconPackPackage }?.label

    val scrollFor: (SettingsDestination) -> SavedScrollPosition = { target ->
        pageScrollPositions.positionOf(target)
    }
    // 一律照记，不看页面身份。以前这里短路过 `if (!target.resetsScrollOnEnter)`，
    // 于是 FEATURE_TOGGLES / DISPLAY_SIZE 和三级以下的页面根本没位置可恢复 ——
    // 连「进子页再退回来」都回到页顶。「进页面回到顶部」已经由 navigateTo 那边的
    // settingsScrollResetsOnEnter + resetPageScroll 负责，这里再拦一次就是重复且有害。
    val updateScroll: (SettingsDestination, Int, Int) -> Unit = { target, index, offset ->
        pageScrollPositions.record(target, index, offset)
    }

    val settingsSearchTarget = searchTargetItemKey?.let { key ->
        SettingsSearchTarget(
            itemKey = key,
            token = searchTargetToken,
            onConsumed = { searchTargetItemKey = null },
        )
    }
    // This plate is deliberately outside AnimatedContent: destination transitions may scale,
    // translate, or fade the interactive page, but must never expose the black window theme.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(launcherStyle.screenBackground),
    ) {
        val destinationTransition = updateTransition(
            targetState = destination,
            label = "settings_destination_transition",
        )
        CompositionLocalProvider(
            LocalSettingsLeftSafeInsetPercent provides appListLeftSafeInsetPercent,
            LocalSettingsRotaryHapticsEnabled provides rotaryHapticsEnabled,
            LocalFlueMotionProfile provides activeMotionProfile,
            LocalSettingsSearchTarget provides settingsSearchTarget,
        ) {
            destinationTransition.AnimatedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                transitionSpec = {
                    // 所有界面风格统一使用 MIUIX / HyperOS 式层级推拉：进子页
                    // 新页从右 1/4 滑入、旧页左移 1/6；返回时方向相反。
                    //
                    // 方向必须由「导航意图」决定，不能由静态 depth 表推导。
                    // depth 是硬编码的，而 FEATURE_TOGGLES 这类页面有两个入口
                    // （ROOT 进是二级、APPEARANCE 进是三级），单个 depth 值在
                    // 原理上就描述不了两条路径 —— 同深度跳转返回时会播成前进。
                    // navigateTo/handleBack 已经维护了真实的 backStack，直接用
                    // 它记下的意图即可。
                    val forward = !transitionIsBack
                    (
                        slideInHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                        ) { fullWidth -> if (forward) fullWidth / 4 else -fullWidth / 6 } +
                            fadeIn(tween(200))
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                        ) { fullWidth -> if (forward) -fullWidth / 6 else fullWidth / 4 } +
                            fadeOut(tween(140))
                        )
                },
            ) { currentDestination ->
                // AnimatedContent keeps both destinations alive while they slide. A
                // transparent destination lets every outgoing card remain visible
                // through the incoming page, which looks like several navigations
                // have piled up. Give each destination its own opaque plate; the
                // uncovered edge still reveals the outgoing page naturally.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(launcherStyle.screenBackground)
                        .clipToBounds(),
                ) {
        key(currentDestination, pageScrollResetVersions[currentDestination] ?: 0) {
        when (currentDestination) {
            SettingsDestination.ROOT -> SettingsPageScaffold(
                title = stringResource(R.string.settings_root_title),
                onBack = {
                    commitHiddenAppsDraft()
                    onFinish()
                },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.ROOT).index,
                initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.ROOT).offset,
                onScrollChanged = { index, offset -> updateScroll(SettingsDestination.ROOT, index, offset) }
            ) { listState, screenCenterY, screenHeightPx, _ ->
                item("settings_search") {
                    SettingsSearchField(
                        query = settingsSearchQuery,
                        onQueryChange = { settingsSearchQuery = it },
                        modifier = Modifier.padding(bottom = 4.dp),
                        scale = itemFisheye(listState, "settings_search", screenCenterY, screenHeightPx),
                    )
                }
                if (settingsSearchQuery.isNotBlank()) {
                    val searchResults = searchSettingsEntries(settingsSearchQuery, searchIndex)
                        .filterNot { layoutMode == LayoutMode.Flow && it.itemKey == "app_list_edge_spacing_compression" }
                    if (searchResults.isEmpty()) {
                        item("search_empty") {
                            Text(
                                stringResource(R.string.settings_search_empty),
                                color = LauncherTheme.style.secondaryTextColor,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    searchResults.forEachIndexed { resultIndex, entry ->
                        item("search_result_$resultIndex") {
                            SettingsCategoryCard(
                                title = entry.title,
                                subtitle = entry.description,
                                onClick = {
                                    settingsSearchQuery = ""
                                    // 先登记要定位的行，再跳页：目标页首次
                                    // 组合时就能读到，不用等第二帧。
                                    searchTargetItemKey = entry.itemKey
                                    searchTargetToken += 1
                                    runCatching { SettingsDestination.valueOf(entry.destinationName) }
                                        .getOrNull()
                                        ?.let(navigateTo)
                                },
                                scale = itemFisheye(
                                    listState,
                                    "search_result_$resultIndex",
                                    screenCenterY,
                                    screenHeightPx,
                                )
                            )
                        }
                    }
                } else {
                // 顶部预览头图已按需求移除，列表直接从「外观」分组开始。
                // 分组顺序对照 SettingsHierarchy：外观 → 表盘 → 应用列表 →
                // 动画 → 手势与输入 → 系统与工具 → 关于。
                item("section_appearance") {
                    SectionTitle(stringResource(R.string.settings_section_appearance), itemFisheye(listState, "section_appearance", screenCenterY, screenHeightPx))
                }
                item("appearance") {
                    SettingsCategoryCard(
                        title = stringResource(R.string.settings_appearance),
                        subtitle = stringResource(R.string.settings_appearance_sub),
                        onClick = { navigateTo(SettingsDestination.APPEARANCE) },
                        scale = itemFisheye(listState, "appearance", screenCenterY, screenHeightPx)
                    )
                }
                item("section_watchface") {
                    SectionTitle(stringResource(R.string.settings_section_watchface), itemFisheye(listState, "section_watchface", screenCenterY, screenHeightPx))
                }
                item("watchfaces") {
                    SettingsCategoryCard(
                        title = stringResource(R.string.settings_watchfaces),
                        // 副标题只写当前表盘名。MD3 档以前多一句「主页与表盘图形已联动」，
                        // 老师要求去掉 —— 它讲的是实现细节，用户在这一行想看的是「现在用的是哪块表」。
                        subtitle = selectedWatchFace.displayName,
                        onClick = { navigateTo(SettingsDestination.WATCH_FACES) },
                        scale = itemFisheye(listState, "watchfaces", screenCenterY, screenHeightPx)
                    )
                }
                item("section_app_list") {
                    SectionTitle(stringResource(R.string.settings_section_app_list), itemFisheye(listState, "section_app_list", screenCenterY, screenHeightPx))
                }
                flueSettingsGroup {
                    row("icon_packs") { position ->
                        SettingsCategoryCard(
                            title = stringResource(R.string.settings_icon_packs),
                            subtitle = selectedIconPackLabel ?: stringResource(R.string.settings_icon_pack_system_default),
                            onClick = { navigateTo(SettingsDestination.ICON_PACKS) },
                            scale = itemFisheye(listState, "icon_packs", screenCenterY, screenHeightPx),
                            groupPosition = position,
                        )
                    }
                    row("hidden_apps") { position ->
                        SettingsCategoryCard(
                            title = stringResource(R.string.settings_hidden_apps),
                            subtitle = stringResource(R.string.settings_hidden_apps_count_fmt, hiddenHideableCount),
                            onClick = { navigateTo(SettingsDestination.HIDDEN_APPS) },
                            scale = itemFisheye(listState, "hidden_apps", screenCenterY, screenHeightPx),
                            groupPosition = position,
                        )
                    }
                }
                item("section_animation") {
                    SectionTitle(stringResource(R.string.settings_section_animation), itemFisheye(listState, "section_animation", screenCenterY, screenHeightPx))
                }
                item("performance") {
                    SettingsCategoryCard(
                        title = stringResource(R.string.settings_performance),
                        subtitle = stringResource(R.string.settings_performance_sub),
                        onClick = { navigateTo(SettingsDestination.PERFORMANCE) },
                        scale = itemFisheye(listState, "performance", screenCenterY, screenHeightPx)
                    )
                }
                item("section_input") {
                    SectionTitle(stringResource(R.string.settings_section_input), itemFisheye(listState, "section_input", screenCenterY, screenHeightPx))
                }
                item("feature_toggles") {
                    SettingsCategoryCard(
                        title = stringResource(R.string.settings_feature_toggles),
                        subtitle = stringResource(R.string.settings_feature_toggles_sub),
                        onClick = { navigateTo(SettingsDestination.FEATURE_TOGGLES) },
                        scale = itemFisheye(listState, "feature_toggles", screenCenterY, screenHeightPx)
                    )
                }
                item("section_system") {
                    SectionTitle(stringResource(R.string.settings_section_system), itemFisheye(listState, "section_system", screenCenterY, screenHeightPx))
                }
                // 老师报「系统与工具这两处选项还没合并」：语言与工具是同一分类下的两行，
                // MIUIX 档要并成一张卡。
                flueSettingsGroup {
                    row("language") { position ->
                        val langLabel = when (appLanguage) {
                            "zh" -> stringResource(R.string.settings_language_zh)
                            "en" -> stringResource(R.string.settings_language_en)
                            "ja" -> stringResource(R.string.settings_language_ja)
                            else -> stringResource(R.string.settings_language_follow_system)
                        }
                        SettingsCategoryCard(
                            title = stringResource(R.string.settings_language),
                            subtitle = langLabel,
                            onClick = { navigateTo(SettingsDestination.LANGUAGE) },
                            scale = itemFisheye(listState, "language", screenCenterY, screenHeightPx),
                            groupPosition = position,
                        )
                    }
                    row("tools") { position ->
                        SettingsCategoryCard(
                            title = stringResource(R.string.settings_tools),
                            subtitle = stringResource(R.string.settings_tools_sub),
                            onClick = { navigateTo(SettingsDestination.TOOLS) },
                            scale = itemFisheye(listState, "tools", screenCenterY, screenHeightPx),
                            groupPosition = position,
                        )
                    }
                }
                item("section_about") {
                    SectionTitle(stringResource(R.string.settings_section_about), itemFisheye(listState, "section_about", screenCenterY, screenHeightPx))
                }
                item("about") {
                    SettingsCategoryCard(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_sub_fmt, ABOUT_VERSION),
                        onClick = { navigateTo(SettingsDestination.ABOUT) },
                        scale = itemFisheye(listState, "about", screenCenterY, screenHeightPx)
                    )
                }
                }
            }

            SettingsDestination.HIDDEN_APPS -> SettingsPageScaffold(
                title = stringResource(R.string.settings_hidden_apps),
                onBack = { handleBack() },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.HIDDEN_APPS).index,
                initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.HIDDEN_APPS).offset,
                onScrollChanged = { index, offset -> updateScroll(SettingsDestination.HIDDEN_APPS, index, offset) }
            ) { listState, screenCenterY, screenHeightPx, _ ->
                item("hidden_summary") {
                    MessageCard(
                        text = stringResource(R.string.settings_hidden_apps_count_fmt, hiddenHideableCount),
                        background = WatchColors.SurfaceGlass,
                        onClick = {}
                    )
                }
                items(
                    items = hideableApps,
                    key = { it.componentKey },
                    contentType = { "hidden_app" },
                ) { app ->
                    SettingsSwitchRow(
                        title = app.label,
                        subtitle = app.packageName,
                        checked = hiddenAppsDraft.contains(app.componentKey) || hiddenAppsDraft.contains(app.packageName),
                        onToggle = {
                            hiddenAppsDraft = hiddenAppsDraft.toMutableSet().apply {
                                if (it) {
                                    add(app.componentKey)
                                } else {
                                    remove(app.componentKey)
                                    remove(app.packageName)
                                }
                            }
                            hiddenAppsDirty = true
                        },
                        scale = itemFisheye(listState, app.componentKey, screenCenterY, screenHeightPx),
                        // LazyColumn already composes only visible/prefetched rows. Do not
                        // drive a second visibility state that unloads/reloads icons while
                        // crossing every item boundary during a fling.
                        leadingIcon = app.cachedIcon,
                        reserveLeadingIconSpace = true,
                        // 隐藏应用页是那条性能敏感的长列表：开关只画不收手势，交互仍由整行
                        // 承担。真组件自带 interactionSource 与手势链，塞进这里会推翻既有修复。
                        indicatorOnly = true,
                    )
                }
            }

            SettingsDestination.ICON_PACKS -> SettingsPageScaffold(
                title = stringResource(R.string.settings_icon_packs),
                onBack = { handleBack() },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.ICON_PACKS).index,
                initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.ICON_PACKS).offset,
                onScrollChanged = { index, offset -> updateScroll(SettingsDestination.ICON_PACKS, index, offset) }
            ) { listState, screenCenterY, screenHeightPx, _ ->
                item("icon_pack_default") {
                    SettingsChoiceRow(
                        title = stringResource(R.string.settings_icon_pack_default),
                        subtitle = stringResource(R.string.settings_icon_pack_default_sub),
                        selected = selectedIconPackPackage.isNullOrBlank(),
                        onClick = { vm.setIconPackPackage(null) },
                        scale = itemFisheye(listState, "icon_pack_default", screenCenterY, screenHeightPx)
                    )
                }
                items(
                    items = availableIconPacks,
                    key = { "iconpack_${it.packageName}" },
                    contentType = { "icon_pack" },
                ) { pack ->
                    SettingsChoiceRow(
                        title = pack.label,
                        subtitle = stringResource(R.string.settings_icon_pack_adw),
                        selected = pack.packageName == selectedIconPackPackage,
                        onClick = { vm.setIconPackPackage(pack.packageName) },
                        scale = itemFisheye(listState, "iconpack_${pack.packageName}", screenCenterY, screenHeightPx)
                    )
                }
                item("icon_pack_refresh") {
                    ActionCard(
                        title = stringResource(R.string.settings_icon_pack_refresh),
                        subtitle = stringResource(R.string.settings_icon_pack_refresh_sub),
                        icon = { SettingsIcon(material = Icons.Filled.Refresh, miuix = MiuixIcons.Refresh) },
                        onClick = { vm.refreshIconPacks() },
                        scale = itemFisheye(listState, "icon_pack_refresh", screenCenterY, screenHeightPx)
                    )
                }
        }

            SettingsDestination.WATCH_FACES -> SettingsPageScaffold(
            title = stringResource(R.string.settings_watchfaces),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.WATCH_FACES).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.WATCH_FACES).offset,
            roundFirstActionHalfHeight = 40.dp,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.WATCH_FACES, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            if (!watchFaceLastError.isNullOrBlank()) {
                item("watchface_error") {
                    MessageCard(
                        text = watchFaceLastError!!,
                        background = Color(0x33FF6B6B),
                        onClick = { vm.clearWatchFaceError() }
                    )
                }
            }
            items(
                items = watchFaces,
                key = { it.id },
                contentType = { "watch_face_setting" },
            ) { descriptor ->
                WatchFaceSettingCard(
                    descriptor = descriptor,
                    selected = descriptor.id == selectedWatchFaceId,
                    uiStyle = uiStyle,
                    builtInPhotoPath = builtInPhotoPath,
                    builtInVideoPath = builtInVideoPath,
                    photoOptions = BuiltInWatchFaceOptions(
                        clockPosition = builtInPhotoClockPosition,
                        clockSizeSp = builtInPhotoClockSize,
                        boldClock = builtInPhotoClockBold,
                        clockStyle = builtInPhotoClockStyle,
                        showSeconds = builtInPhotoShowSeconds,
                        customText = builtInPhotoCustomText,
                        fontPath = builtInWatchFaceFontPath,
                        md3eShape = builtInPhotoMd3eShape,
                        useThemeTextColor = builtInPhotoUseThemeTextColor,
                        textColorArgb = builtInPhotoTextColorArgb,
                        md3eAutoColors = builtInPhotoMd3eAutoColors,
                        md3eTextColorArgb = builtInPhotoMd3eTextColorArgb,
                        md3eFaceColorArgb = builtInPhotoMd3eFaceColorArgb,
                        md3eHourHandColorArgb = builtInPhotoMd3eHourColorArgb,
                        md3eMinuteHandColorArgb = builtInPhotoMd3eMinuteColorArgb,
                        md3eSecondHandColorArgb = builtInPhotoMd3eSecondColorArgb
                    ),
                    videoOptions = BuiltInWatchFaceOptions(
                        clockPosition = builtInVideoClockPosition,
                        clockSizeSp = builtInVideoClockSize,
                        boldClock = builtInVideoClockBold,
                        cropToFill = builtInVideoFillScreen,
                        clockColorMode = builtInVideoClockColorMode,
                        clockStyle = builtInVideoClockStyle,
                        showSeconds = builtInVideoShowSeconds,
                        customText = builtInVideoCustomText,
                        fontPath = builtInWatchFaceFontPath,
                        md3eShape = builtInVideoMd3eShape,
                        useThemeTextColor = builtInVideoUseThemeTextColor,
                        textColorArgb = builtInVideoTextColorArgb,
                        md3eAutoColors = builtInVideoMd3eAutoColors,
                        md3eTextColorArgb = builtInVideoMd3eTextColorArgb,
                        md3eFaceColorArgb = builtInVideoMd3eFaceColorArgb,
                        md3eHourHandColorArgb = builtInVideoMd3eHourColorArgb,
                        md3eMinuteHandColorArgb = builtInVideoMd3eMinuteColorArgb,
                        md3eSecondHandColorArgb = builtInVideoMd3eSecondColorArgb
                    ),
                    onSelect = { vm.selectWatchFace(descriptor.id) },
                    onDelete = if (descriptor.isJbWatch || descriptor.isWatchmaker) {
                        { importedWatchFaceDeleteTarget = descriptor }
                    } else {
                        null
                    },
                    scale = itemFisheye(listState, descriptor.id, screenCenterY, screenHeightPx),
                    // 保存表盘会原地覆盖 preview.jpg，令牌驱动缩略图重读。
                    refreshToken = watchFaceRefreshToken,
                )
            }
            // HOME 安装库只负责使用，不把商店或导入作品自动交给 Studio 编辑。
            // 创作入口打开独立 Studio 工程，不传当前安装表盘的私有路径。
            flueSettingsGroup {
                row("watchface_editor_new") { position ->
                    ActionCard(
                        title = stringResource(R.string.settings_watchface_editor_new),
                        subtitle = stringResource(R.string.settings_watchface_editor_new_sub),
                        scale = itemFisheye(listState, "watchface_editor_new", screenCenterY, screenHeightPx),
                        icon = { SettingsIcon(material = OfficialMiuixIcons.OfficialAdd, miuix = OfficialMiuixIcons.OfficialAdd) },
                        onClick = { FlueStudioLauncher.open(context) },
                        groupPosition = position,
                    )
                }
                row("watchface_import_archive") { position ->
                    ActionCard(
                        title = stringResource(R.string.settings_watchface_import),
                        subtitle = stringResource(R.string.settings_watchface_import_sub),
                        scale = itemFisheye(listState, "watchface_import_archive", screenCenterY, screenHeightPx),
                        icon = {
                            SettingsIcon(
                                material = OfficialMiuixIcons.OfficialFolder,
                                miuix = OfficialMiuixIcons.OfficialFolder,
                            )
                        },
                        onClick = {
                            watchFaceArchivePicker.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "application/*",
                                    "*/*"
                                )
                            )
                        },
                        onLongPress = {
                            internalFileBrowserRequest = InternalFileBrowserRequest(
                                title = context.getString(R.string.internal_file_browser_pick_watch),
                                extensions = setOf("watch"),
                                onSelected = { uris -> handleWatchFaceArchiveUri(uris.firstOrNull()) },
                            )
                        },
                        groupPosition = position,
                    )
                }
                row("watchface_store") { position ->
                    ActionCard(
                        title = stringResource(R.string.settings_watchface_store),
                        subtitle = stringResource(R.string.settings_watchface_store_sub),
                        scale = itemFisheye(listState, "watchface_store", screenCenterY, screenHeightPx),
                        icon = {
                            SettingsIcon(
                                material = OfficialMiuixIcons.OfficialCarrier,
                                miuix = OfficialMiuixIcons.OfficialCarrier,
                            )
                        },
                        onClick = { context.startActivity(Intent(context, com.flue.launcher.WatchStoreActivity::class.java)) },
                        groupPosition = position,
                    )
                }
                row("watchface_refresh") { position ->
                    ActionCard(
                        title = stringResource(R.string.settings_watchface_refresh),
                        subtitle = stringResource(R.string.settings_watchface_refresh_sub),
                        scale = itemFisheye(listState, "watchface_refresh", screenCenterY, screenHeightPx),
                        icon = { SettingsIcon(material = OfficialMiuixIcons.OfficialRefresh, miuix = OfficialMiuixIcons.OfficialRefresh) },
                        onClick = { vm.refreshWatchFaces(force = true) },
                        groupPosition = position,
                    )
                }
                row("watchface_more") { position ->
                    SettingsCategoryCard(
                        title = stringResource(R.string.settings_watchface_more),
                        subtitle = stringResource(R.string.settings_watchface_more_sub),
                        onClick = { navigateTo(SettingsDestination.WATCH_FACE_MORE) },
                        scale = itemFisheye(listState, "watchface_more", screenCenterY, screenHeightPx),
                        groupPosition = position,
                    )
                }
            }
        }

            SettingsDestination.WATCH_FACE_MORE -> SettingsPageScaffold(
                title = stringResource(R.string.settings_watchface_more),
                onBack = { handleBack() },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.WATCH_FACE_MORE).index,
                initialFirstVisibleItemScrollOffset =
                    scrollFor(SettingsDestination.WATCH_FACE_MORE).offset,
                roundFirstActionIndex = 2,
                onScrollChanged = { index, offset ->
                    updateScroll(SettingsDestination.WATCH_FACE_MORE, index, offset)
                }
            ) { listState, screenCenterY, screenHeightPx, _ ->
                // 这一屏既有分节标题又有三种断卡方式，所以走整屏版渲染器。
                settingsEntryScreen(
                    entries = SettingsHierarchy.renderedGroup("watch_face_more"),
                    binding = object : SettingsEntryBinding {
                        override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                            "watchface_tap_haptics" -> watchFaceTapHapticsEnabled
                            "watchface_gravity_parallax" -> watchfaceGravityParallaxEnabled
                            "watchface_gravity_backdrop_blur" -> watchfaceGravityBackdropBlurEnabled
                            "watchface_bottom_fade" -> watchFaceBottomFadeEnabled
                            "watchface_text_entry_animation" -> watchFaceTextEntryAnimation
                            "watchface_text_exit_animation" -> watchFaceTextExitAnimation
                            "watchface_status_indicators" -> watchFaceStatusIndicators
                            "watchface_dnd_indicator" -> watchFaceDndIndicator
                            "watchface_charging_power_text" -> watchFaceChargingPowerText
                            else -> false
                        }

                        override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                            when (entry.id) {
                                "watchface_tap_haptics" -> vm.setWatchFaceTapHapticsEnabled(checked)
                                "watchface_gravity_parallax" ->
                                    vm.setWatchfaceGravityParallaxEnabled(checked)
                                "watchface_gravity_backdrop_blur" ->
                                    vm.setWatchfaceGravityBackdropBlurEnabled(checked)
                                "watchface_bottom_fade" -> vm.setWatchFaceBottomFadeEnabled(checked)
                                "watchface_text_entry_animation" ->
                                    vm.setWatchFaceTextEntryAnimationEnabled(checked)
                                "watchface_text_exit_animation" ->
                                    vm.setWatchFaceTextExitAnimationEnabled(checked)
                                "watchface_status_indicators" ->
                                    vm.setWatchFaceStatusIndicatorsEnabled(checked)
                                "watchface_dnd_indicator" -> vm.setWatchFaceDndIndicatorEnabled(checked)
                                "watchface_charging_power_text" ->
                                    vm.setWatchFaceChargingPowerTextEnabled(checked)
                            }
                        }

                        override fun selectedOption(entry: SettingsEntry.Picker): String? = when (entry.id) {
                            // 枚举里还有「跟随系统」这类值，用 else 兜底成默认选项。
                            "watch_weather_unit" -> when (weatherTemperatureUnit) {
                                WatchTemperatureUnit.CELSIUS -> "celsius"
                                else -> "fahrenheit"
                            }
                            "watchface_date_locale" -> when (watchfaceDateLocale) {
                                WatchDateLocale.PREFERENCE_CHINESE -> "zh"
                                WatchDateLocale.PREFERENCE_ENGLISH -> "en"
                                WatchDateLocale.PREFERENCE_JAPANESE -> "ja"
                                else -> "system"
                            }
                            else -> null
                        }

                        override fun onPick(entry: SettingsEntry.Picker, optionId: String) {
                            when (entry.id) {
                                "watch_weather_unit" -> {
                                    val unit = when (optionId) {
                                        "celsius" -> WatchTemperatureUnit.CELSIUS
                                        else -> WatchTemperatureUnit.FAHRENHEIT
                                    }
                                    weatherRepository.setTemperatureUnit(unit)
                                    weatherTemperatureUnit = unit
                                }
                                "watchface_date_locale" -> {
                                    val locale = when (optionId) {
                                        "zh" -> WatchDateLocale.PREFERENCE_CHINESE
                                        "en" -> WatchDateLocale.PREFERENCE_ENGLISH
                                        "ja" -> WatchDateLocale.PREFERENCE_JAPANESE
                                        else -> WatchDateLocale.PREFERENCE_SYSTEM
                                    }
                                    vm.setWatchfaceDateLocale(locale)
                                }
                            }
                        }

                        override fun onOpen(entry: SettingsEntry) {
                            val action = (entry as? SettingsEntry.Action)?.action ?: return
                            when (action) {
                                "open_long_press_mode_dialog" -> watchFaceLongPressModeDialogVisible = true
                                "open_weather_city_dialog" -> {
                                    weatherCityDraft = ""
                                    weatherCityCandidates = emptyList()
                                    weatherCitySearchError = null
                                    weatherCityDialogVisible = true
                                }
                                "use_system_weather_location" -> {
                                    if (locationPermissionGranted) {
                                        resolveAndUseSystemWeatherLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            WatchLocationPermissionPolicy.PERMISSIONS
                                        )
                                    }
                                }
                                "refresh_weather" -> {
                                    weatherRepository.requestRefresh()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_weather_refresh_requested),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                "navigate_tap_actions" ->
                                    navigateTo(SettingsDestination.TAP_ACTION_CATEGORIES)
                                "open_canvas_adjust" ->
                                    CanvasAdjustActivity.start(context, selectedWatchFaceId)
                                "request_body_sensors" -> {
                                    if (bodySensorsPermissionGranted) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_body_sensors_granted),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        bodySensorsPermissionLauncher.launch(requiredHeartRatePermission())
                                    }
                                }
                                "request_calendar" -> {
                                    if (calendarPermissionGranted) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_calendar_granted),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                    }
                                }
                                "pick_watchface_font" -> fontPicker.launch(
                                    arrayOf(
                                        "font/*",
                                        "application/x-font-ttf",
                                        "application/x-font-otf",
                                        "application/vnd.ms-opentype",
                                        "application/octet-stream"
                                    )
                                )
                                "clear_watchface_font" -> vm.setBuiltInWatchFaceFontPath(null)
                            }
                        }

                        // 这一屏的副标题几乎全是状态回显：城市名、授权与否、字体文件名。
                        // 写进数据会立刻过期，只能在这里按当前状态算。
                        @Composable
                        override fun subtitle(entry: SettingsEntry): String? = when (entry.id) {
                            "watchface_long_press_mode" -> stringResource(
                                if (watchFaceLongPressMode == WatchFaceLongPressMode.CARDS) {
                                    R.string.settings_watchface_long_press_mode_cards_sub
                                } else {
                                    R.string.settings_watchface_long_press_mode_list_sub
                                }
                            )
                            "watch_weather_manual_city" ->
                                weatherLocationLabel.takeIf(String::isNotBlank)?.let { label ->
                                    stringResource(R.string.settings_watch_weather_city_selected_fmt, label)
                                } ?: if (locationPermissionGranted && weatherUsingSystemLocation) {
                                    stringResource(R.string.settings_watch_weather_city_system)
                                } else {
                                    stringResource(R.string.settings_watch_weather_city_unset)
                                }
                            "watch_weather_system_location" ->
                                if (locationPermissionGranted && weatherUsingSystemLocation) {
                                    weatherLocationLabel.takeIf(String::isNotBlank)?.let { label ->
                                        stringResource(
                                            R.string.settings_watch_weather_use_system_active_fmt,
                                            label,
                                        )
                                    } ?: stringResource(R.string.settings_watch_weather_use_system_active)
                                } else {
                                    stringResource(R.string.settings_watch_weather_use_system_sub)
                                }
                            "watchmaker_body_sensors" -> stringResource(
                                if (bodySensorsPermissionGranted) {
                                    R.string.settings_watchface_heart_rate_on
                                } else {
                                    R.string.settings_watchface_heart_rate_off
                                }
                            )
                            "watchmaker_calendar" -> stringResource(
                                if (calendarPermissionGranted) {
                                    R.string.settings_watchface_calendar_on
                                } else {
                                    R.string.settings_watchface_calendar_off
                                }
                            )
                            "watchface_font" -> builtInWatchFaceFontPath?.let { File(it).name }
                                ?: stringResource(R.string.settings_watchface_font_default)
                            else -> null
                        }

                        // 授权类条目的图标就是状态：授予后齿轮变对勾。
                        override fun icon(entry: SettingsEntry): EntryIcon? = when (entry.id) {
                            "watchmaker_body_sensors" ->
                                if (bodySensorsPermissionGranted) EntryIcon.OK else EntryIcon.SETTINGS
                            "watchmaker_calendar" ->
                                if (calendarPermissionGranted) EntryIcon.OK else EntryIcon.SETTINGS
                            else -> null
                        }

                        override fun isVisible(entry: SettingsEntry): Boolean = when (entry.id) {
                            "watchface_canvas_entry",
                            "watchface_gravity_parallax",
                            "watchface_gravity_backdrop_blur",
                            "watchmaker_body_sensors",
                            "watchmaker_calendar" -> selectedWatchFace.isWatchmaker
                            "watchface_font" -> selectedWatchFace.isBuiltin
                            "watchface_font_clear" ->
                                selectedWatchFace.isBuiltin && !builtInWatchFaceFontPath.isNullOrBlank()
                            else -> true
                        }
                    },
                    scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
                )
            }

            SettingsDestination.TAP_ACTION_CATEGORIES -> SettingsPageScaffold(
            title = stringResource(R.string.settings_tap_actions),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.TAP_ACTION_CATEGORIES).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.TAP_ACTION_CATEGORIES).offset,
            roundFirstActionIndex = 1,
            onScrollChanged = { index, offset ->
                updateScroll(SettingsDestination.TAP_ACTION_CATEGORIES, index, offset)
            },
        ) { listState, screenCenterY, screenHeightPx, _ ->
            WatchTapActionCatalog.categories.forEach { category ->
                item("tap_action_category_${category.id}") {
                    SettingsCategoryCard(
                        title = stringResource(category.titleRes),
                        subtitle = stringResource(category.descriptionRes),
                        onClick = {
                            tapActionCategoryId = category.id
                            navigateTo(SettingsDestination.TAP_ACTION_CATEGORY)
                        },
                        scale = itemFisheye(
                            listState,
                            "tap_action_category_${category.id}",
                            screenCenterY,
                            screenHeightPx,
                        ),
                    )
                }
            }
        }

            SettingsDestination.TAP_ACTION_CATEGORY -> {
            val category = WatchTapActionCatalog.category(tapActionCategoryId)
                ?: WatchTapActionCatalog.categories.first()
            SettingsPageScaffold(
                title = stringResource(category.titleRes),
                onBack = { handleBack() },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.TAP_ACTION_CATEGORY).index,
                initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.TAP_ACTION_CATEGORY).offset,
                roundFirstActionIndex = 1,
                onScrollChanged = { index, offset ->
                    updateScroll(SettingsDestination.TAP_ACTION_CATEGORY, index, offset)
                },
            ) { listState, screenCenterY, screenHeightPx, _ ->
                category.actions.forEach { action ->
                    item("tap_action_${action.id}") {
                        WatchTapActionBindingCard(
                            action = action,
                            revision = tapActionRevision,
                            scale = itemFisheye(
                                listState,
                                "tap_action_${action.id}",
                                screenCenterY,
                                screenHeightPx,
                            ),
                            onClick = { tapActionPicker = action },
                        )
                    }
                }
            }
        }

            SettingsDestination.APP_LIST_BACKGROUND -> AppListBackgroundPage(
                onBack = { handleBack() },
                headerTime = headerTime,
                initialScrollIndex = scrollFor(SettingsDestination.APP_LIST_BACKGROUND).index,
                initialScrollOffset = scrollFor(SettingsDestination.APP_LIST_BACKGROUND).offset,
                onScrollChanged = { index, offset ->
                    updateScroll(SettingsDestination.APP_LIST_BACKGROUND, index, offset)
                },
                vm = vm,
                background = appListBackground,
                palette = appListPreviewPalette,
                darkMode = appListPreviewDarkMode,
                cornerRadiusDp = overlayMaskCornerRadiusDp,
                sources = BackgroundPreviewSources(
                    watchFaceId = selectedWatchFaceId,
                    watchFaceStableKey = selectedWatchFace.stableKey,
                    watchFacePreviewPath = selectedWatchFace.previewFilePath,
                    builtInPhotoPath = builtInPhotoPath,
                    builtInVideoPath = builtInVideoPath,
                    watchFaceRefreshToken = watchFaceRefreshToken,
                ),
                // 颜色弹窗、系统选图器、内置文件浏览器都归根作用域管：
                // 它们是 Dialog / ActivityResult，不在列表里。
                onPickColor = { appListBackgroundColorPickerVisible = true },
                onPickImage = { appListBackgroundImagePicker.launch(arrayOf("image/*")) },
                onPickImageFromDevice = {
                    internalFileBrowserRequest = InternalFileBrowserRequest(
                        title = context.getString(R.string.settings_app_list_background_pick_image),
                        extensions = APP_LIST_BACKGROUND_IMAGE_EXTENSIONS,
                        onSelected = { uris -> appListBackgroundCropUri = uris.firstOrNull() },
                    )
                },
            )

            SettingsDestination.APPEARANCE -> SettingsPageScaffold(
            title = stringResource(R.string.settings_appearance),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.APPEARANCE).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.APPEARANCE).offset,
            roundFirstActionIndex = 2,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.APPEARANCE, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            // 整屏迁到数据驱动渲染：条目顺序、行 key、成组范围都与手写版逐项对齐，
            // 所以 item 索引不变，scaffold 的 roundFirstActionIndex = 2 仍然指同一行。
            settingsEntryScreen(
                entries = SettingsHierarchy.renderedGroup("appearance"),
                binding = object : SettingsEntryBinding {
                    override fun selectedOption(entry: SettingsEntry.Picker): String? = when (entry.id) {
                        "layout" -> layoutMode.name.lowercase()
                        "theme_mode" -> themeMode.name.lowercase()
                        "transition_motion" -> transitionMotion.name.lowercase()
                        "screen_shape" -> screenShapeOverride.name.lowercase()
                        else -> null
                    }

                    override fun onPick(entry: SettingsEntry.Picker, optionId: String) {
                        when (entry.id) {
                            "layout" -> vm.setLayoutMode(LayoutMode.valueOf(optionId.uppercase()))
                            "theme_mode" -> vm.setThemeMode(ThemeMode.valueOf(optionId.uppercase()))
                            "transition_motion" -> vm.setTransitionMotion(
                                FlueTransitionMotion.valueOf(optionId.uppercase()),
                            )
                            "screen_shape" -> vm.setScreenShapeOverride(
                                FlueScreenShape.valueOf(optionId.uppercase()),
                            )
                        }
                    }

                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "blur_toggle" -> blurEnabled
                        "edge_blur_toggle" -> edgeBlurEnabled
                        "splash_toggle" -> splashIcon
                        "direct_launch_app_list" -> directLaunchAppListEnabled
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            "blur_toggle" -> vm.setBlurEnabled(checked)
                            "edge_blur_toggle" -> vm.setEdgeBlurEnabled(checked)
                            "splash_toggle" -> vm.setSplashIcon(checked)
                            "direct_launch_app_list" -> vm.setDirectLaunchAppListEnabled(checked)
                        }
                    }

                    override fun sliderValue(entry: SettingsEntry.Slider): Float = when (entry.id) {
                        "edge_blur_radius" -> honeycombEdgeBlurRadius
                        "top_fade" -> honeycombTopFade.toFloat()
                        "bottom_fade" -> honeycombBottomFade.toFloat()
                        "splash_delay" -> splashDelay.toFloat()
                        else -> 0f
                    }

                    override fun onSliderChange(entry: SettingsEntry.Slider, value: Float) {
                        when (entry.id) {
                            "edge_blur_radius" -> vm.setHoneycombEdgeBlurRadius(value)
                            "top_fade" -> vm.setHoneycombTopFade(value.roundToInt())
                            "bottom_fade" -> vm.setHoneycombBottomFade(value.roundToInt())
                            "splash_delay" -> vm.setSplashDelay(value.roundToInt())
                        }
                    }

                    override fun onOpen(entry: SettingsEntry) {
                        when (entry) {
                            is SettingsEntry.Navigation -> {
                                val next = SettingsDestination.entries
                                    .firstOrNull { it.name == entry.destination } ?: return
                                navigateTo(next)
                            }
                            is SettingsEntry.Action -> if (entry.action == "show_overlay_mask_corner_dialog") {
                                showOverlayMaskCornerDialog = true
                            }
                            else -> Unit
                        }
                    }

                    // 两项副标题是运行时状态：当前背景模式名、带 dp 数值的圆角说明。
                    @Composable
                    override fun subtitle(entry: SettingsEntry): String? = when (entry.id) {
                        "app_list_background_entry" -> appListBackgroundModeLabel(appListBackground.effectiveMode)
                        "overlay_mask_corner_radius" -> stringResource(
                            R.string.settings_overlay_mask_corner_radius_sub_fmt,
                            overlayMaskCornerRadiusDp,
                        )
                        else -> null
                    }

                    // 圆屏没有遮罩圆角；启动延时只在启动图标开启时出现。
                    override fun isVisible(entry: SettingsEntry): Boolean = when (entry.id) {
                        "overlay_mask_corner_radius" -> !flueScreenRound
                        "splash_delay" -> splashIcon
                        else -> true
                    }

                    override fun isEnabled(entry: SettingsEntry): Boolean = when (entry.id) {
                        "edge_blur_radius" -> blurEnabled && edgeBlurEnabled
                        else -> true
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.DISPLAY_SIZE -> DisplaySizePage(
                onBack = { handleBack() },
                headerTime = headerTime,
                initialScrollIndex = scrollFor(SettingsDestination.DISPLAY_SIZE).index,
                initialScrollOffset = scrollFor(SettingsDestination.DISPLAY_SIZE).offset,
                onScrollChanged = { index, offset ->
                    updateScroll(SettingsDestination.DISPLAY_SIZE, index, offset)
                },
                vm = vm,
                context = context,
                onRequestRenameProfile = { renameLayoutProfileVisible = true },
                onExportProfiles = { layoutExportPicker.launch("flue-layout-profiles.json") },
                onImportProfiles = {
                    layoutImportPicker.launch(arrayOf("application/json", "text/plain"))
                },
            )

            // 「功能开关」从 16 项杂烩改为路牌页：五个按领域归类的三级页。

            SettingsDestination.FEATURE_TOGGLES -> SettingsPageScaffold(
            title = stringResource(R.string.settings_feature_toggles),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.FEATURE_TOGGLES).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.FEATURE_TOGGLES).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.FEATURE_TOGGLES, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("feature_toggles"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "glass_highlight_toggle" -> glassHighlightEnabled
                        "lock_screen_watch_face_toggle" -> lockScreenWatchFaceEnabled
                        "pull_down_control_center_toggle" -> pullDownControlCenterEnabled
                        // 这一屏的开关已经不止一个，不能再拿某一个当默认值：
                        // 漏映射时应该显示为关（明显不对，能被发现），而不是显示成别人的状态。
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            "glass_highlight_toggle" -> vm.setGlassHighlightEnabled(checked)
                            "lock_screen_watch_face_toggle" -> vm.setLockScreenWatchFaceEnabled(checked)
                            "pull_down_control_center_toggle" -> vm.setPullDownControlCenterEnabled(checked)
                            else -> Unit
                        }
                    }

                    // destination 存的就是 SettingsDestination 的枚举名，
                    // 所以这里不需要再维护一张 when 映射表。
                    override fun onOpen(entry: SettingsEntry) {
                        val name = (entry as? SettingsEntry.Navigation)?.destination ?: return
                        val next = SettingsDestination.entries.firstOrNull { it.name == name } ?: return
                        navigateTo(next)
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.SIDE_SCREEN -> SettingsPageScaffold(
            title = stringResource(R.string.settings_side_screen_title),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.SIDE_SCREEN).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.SIDE_SCREEN).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.SIDE_SCREEN, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("side_screen"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "side_screen_toggle" -> sideScreenEnabled
                        "widget_center_toggle" -> showWidgetPage
                        "widget_glass_highlight" -> widgetGlassHighlightEnabled
                        "music_notification_swap_toggle" -> swapMusicNotificationComponents
                        "music_controls_toggle" -> showMusicControls
                        "media_custom_actions_toggle" -> showMediaCustomActions
                        "show_step_count" -> showStepCount
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            "side_screen_toggle" -> vm.setSideScreenEnabled(checked)
                            "widget_center_toggle" -> vm.setShowWidgetPage(checked)
                            "widget_glass_highlight" -> vm.setWidgetGlassHighlightEnabled(checked)
                            "music_notification_swap_toggle" -> vm.setSwapMusicNotificationComponents(checked)
                            "music_controls_toggle" -> vm.setShowMusicControls(checked)
                            "media_custom_actions_toggle" -> vm.setShowMediaCustomActions(checked)
                            "show_step_count" -> vm.setShowStepCountEnabled(checked)
                        }
                    }

                    // 级联开关：上游不开，下游置灰（行仍然显示，用户看得到依赖关系）。
                    override fun isEnabled(entry: SettingsEntry): Boolean = when (entry.id) {
                        "widget_glass_highlight" -> sideScreenEnabled && showWidgetPage
                        "music_notification_swap_toggle" -> sideScreenEnabled
                        "music_controls_toggle" ->
                            sideScreenEnabled && swapMusicNotificationComponents
                        "media_custom_actions_toggle" ->
                            sideScreenEnabled && swapMusicNotificationComponents && showMusicControls
                        else -> true
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.NOTIFICATION_SETTINGS -> SettingsPageScaffold(
            title = stringResource(R.string.settings_notifications_title),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.NOTIFICATION_SETTINGS).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.NOTIFICATION_SETTINGS).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.NOTIFICATION_SETTINGS, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("notifications"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "notification_center_toggle" -> showNotification
                        "ongoing_notification_toggle" -> showOngoingNotifications
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            "notification_center_toggle" -> vm.setShowNotification(checked)
                            "ongoing_notification_toggle" -> vm.setShowOngoingNotifications(checked)
                        }
                    }

                    // 通知中心依赖副一屏；「常驻通知」再依赖通知中心本身。
                    override fun isEnabled(entry: SettingsEntry): Boolean = when (entry.id) {
                        "notification_center_toggle" -> sideScreenEnabled
                        "ongoing_notification_toggle" -> sideScreenEnabled && showNotification
                        else -> true
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.GESTURE_SETTINGS -> SettingsPageScaffold(
            title = stringResource(R.string.settings_gestures_title),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.GESTURE_SETTINGS).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.GESTURE_SETTINGS).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.GESTURE_SETTINGS, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("gestures"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "double_tap_lock_screen" -> doubleTapLockScreenEnabled
                        "face_ignores_back_key" -> faceIgnoresBackKey
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            // 打开时若授权缺失，顺手把用户带到授权页 —— 手写版的原有行为。
                            "double_tap_lock_screen" -> {
                                vm.setDoubleTapLockScreenEnabled(checked)
                                if (doubleTapUsesDeviceAdmin) {
                                    deviceAdminEnabled = FlueDeviceAdminController.isActive(context)
                                    if (checked && !deviceAdminEnabled) {
                                        openDeviceAdminSettings()
                                    }
                                } else {
                                    accessibilityServiceEnabled =
                                        FlueAccessibilityService.isEnabled(context) || accessibilityServiceConnected
                                    if (checked && !accessibilityServiceEnabled) {
                                        openAccessibilitySettings()
                                    }
                                }
                            }
                            "face_ignores_back_key" -> vm.setFaceIgnoresBackKey(checked)
                        }
                    }

                    // 副标题四分支：走设备管理员时看管理员是否已激活，否则看无障碍是否可用。
                    @Composable
                    override fun subtitle(entry: SettingsEntry): String? {
                        if (entry.id != "double_tap_lock_screen") return null
                        return when {
                            doubleTapUsesDeviceAdmin && deviceAdminEnabled ->
                                stringResource(R.string.settings_double_tap_device_admin_on)
                            doubleTapUsesDeviceAdmin ->
                                stringResource(R.string.settings_double_tap_device_admin_require)
                            accessibilityServiceEnabled -> stringResource(R.string.settings_double_tap_on)
                            else -> stringResource(R.string.settings_double_tap_require)
                        }
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.APP_LIST_BEHAVIOR -> SettingsPageScaffold(
            title = stringResource(R.string.settings_app_list_behavior_title),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.APP_LIST_BEHAVIOR).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.APP_LIST_BEHAVIOR).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.APP_LIST_BEHAVIOR, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("app_list_behavior"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "app_list_watchface_colors" -> appListWatchFaceColors
                        "app_list_row_border" -> appListRowBorderEnabled
                        "app_list_folders_enabled" -> appListFoldersEnabled
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            "app_list_watchface_colors" -> vm.setAppListWatchFaceColors(checked)
                            "app_list_row_border" -> vm.setAppListRowBorderEnabled(checked)
                            "app_list_folders_enabled" -> vm.setAppListFoldersEnabled(checked)
                        }
                    }

                    // 底色遮罩只对列表布局有意义。行仍然显示、只是置灰 ——
                    // 藏起来的话，用户切到蜂窝模式就再也找不到这个开关了。
                    override fun isEnabled(entry: SettingsEntry): Boolean = when (entry.id) {
                        "app_list_row_border" -> layoutMode == LayoutMode.List
                        else -> true
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.PERFORMANCE -> SettingsPageScaffold(
            title = stringResource(R.string.settings_performance_title),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.PERFORMANCE).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.PERFORMANCE).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.PERFORMANCE, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            // 性能页拆成三组：这一层只做目录，具体开关全在子页。
            // 原来 13 项平铺在一页，滑到底要转很久，且「图标怎么画」「动画怎么
            // 走」「系统与网络行为」三类东西混在一起没有边界。
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("performance"),
                binding = object : SettingsEntryBinding {
                    override fun onOpen(entry: SettingsEntry) {
                        val name = (entry as? SettingsEntry.Navigation)?.destination ?: return
                        val next = SettingsDestination.entries.firstOrNull { it.name == name } ?: return
                        navigateTo(next)
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.PERFORMANCE_RENDERING -> SettingsPageScaffold(
            title = stringResource(R.string.settings_perf_group_rendering),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.PERFORMANCE_RENDERING).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.PERFORMANCE_RENDERING).offset,
            onScrollChanged = { index, offset ->
                updateScroll(SettingsDestination.PERFORMANCE_RENDERING, index, offset)
            }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("performance_rendering"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "low_res" -> lowResIcons
                        "icon_shadow" -> iconShadowEnabled
                        "builtin_manager_thumbnails" -> builtInManagerThumbnails
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            "low_res" -> vm.setLowResIcons(checked)
                            "icon_shadow" -> vm.setIconShadowEnabled(checked)
                            "builtin_manager_thumbnails" -> vm.setBuiltInManagerThumbnails(checked)
                        }
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.PERFORMANCE_MOTION -> SettingsPageScaffold(
            title = stringResource(R.string.settings_perf_group_motion),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.PERFORMANCE_MOTION).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.PERFORMANCE_MOTION).offset,
            onScrollChanged = { index, offset ->
                updateScroll(SettingsDestination.PERFORMANCE_MOTION, index, offset)
            }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            // 这一屏的卡片边界是四张卡与两个裸 item 交错，靠 placement 表达；
            // 两张导航卡的副标题都是运行时状态，交给 subtitle 钩子。
            settingsEntryScreen(
                entries = SettingsHierarchy.renderedGroup("performance_motion"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean = when (entry.id) {
                        "anim_override" -> animationOverrideEnabled
                        "app_list_return_shrink" -> appListReturnShrinkEnabled
                        "app_list_return_icon_float" -> appListReturnIconFloatEnabled
                        "face_return_shrink" -> faceReturnShrinkEnabled
                        "legacy_app_list_transition" -> legacyAppListTransitionEnabled
                        "app_list_stagger" -> appListStaggerEnabled
                        "reduce_dynamic_effects" -> reduceDynamicEffects
                        else -> false
                    }

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        when (entry.id) {
                            "anim_override" -> vm.setAnimationOverrideEnabled(checked)
                            "app_list_return_shrink" -> vm.setAppListReturnShrinkEnabled(checked)
                            "app_list_return_icon_float" ->
                                vm.setAppListReturnIconFloatEnabled(checked)
                            "face_return_shrink" -> vm.setFaceReturnShrinkEnabled(checked)
                            "legacy_app_list_transition" ->
                                vm.setLegacyAppListTransitionEnabled(checked)
                            "app_list_stagger" -> vm.setAppListStaggerEnabled(checked)
                            "reduce_dynamic_effects" -> vm.setReduceDynamicEffects(checked)
                        }
                    }

                    override fun isVisible(entry: SettingsEntry): Boolean = when (entry.id) {
                        // 收缩开着时，图标浮起这条没有意义，手写版也是直接不组合。
                        "app_list_return_icon_float" -> !appListReturnShrinkEnabled
                        else -> true
                    }

                    override fun onOpen(entry: SettingsEntry) {
                        val name = (entry as? SettingsEntry.Navigation)?.destination ?: return
                        val next = SettingsDestination.entries.firstOrNull { it.name == name } ?: return
                        navigateTo(next)
                    }

                    @Composable
                    override fun subtitle(entry: SettingsEntry): String? = when (entry.id) {
                        "music_text_animation" -> stringResource(
                            MusicTextSwitchAnimations.byId(musicTextSwitchAnimation).labelRes
                        )
                        "motion_profile" -> stringResource(
                            R.string.settings_motion_profile_sub_fmt,
                            activeMotionProfile.name,
                            if (activeMotionProfile.bounceEnabled) {
                                stringResource(R.string.settings_motion_bounce_on)
                            } else {
                                stringResource(R.string.settings_motion_bounce_off)
                            }
                        )
                        else -> null
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.PERFORMANCE_SYSTEM -> SettingsPageScaffold(
            title = stringResource(R.string.settings_perf_group_system),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.PERFORMANCE_SYSTEM).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.PERFORMANCE_SYSTEM).offset,
            onScrollChanged = { index, offset ->
                updateScroll(SettingsDestination.PERFORMANCE_SYSTEM, index, offset)
            }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("performance_system"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean =
                        entry.id == "hide_from_recents" && hideFromRecents

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        if (entry.id == "hide_from_recents") vm.setHideFromRecents(checked)
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.MOTION_PROFILE -> MotionProfilePage(
                onBack = { handleBack() },
                headerTime = headerTime,
                initialScrollIndex = scrollFor(SettingsDestination.MOTION_PROFILE).index,
                initialScrollOffset = scrollFor(SettingsDestination.MOTION_PROFILE).offset,
                onScrollChanged = { index, offset ->
                    updateScroll(SettingsDestination.MOTION_PROFILE, index, offset)
                },
                vm = vm,
                activeMotionProfile = activeMotionProfile,
                motionProfiles = motionProfiles,
                onRenameRequest = { renameMotionProfileVisible = true },
                onExport = { motionExportPicker.launch("flue-motion-profiles.json") },
                onImport = {
                    motionImportPicker.launch(arrayOf("application/json", "text/plain"))
                },
            )

            SettingsDestination.MUSIC_TEXT_ANIMATION -> SettingsPageScaffold(
            title = stringResource(R.string.settings_music_text_animation),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.MUSIC_TEXT_ANIMATION).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.MUSIC_TEXT_ANIMATION).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.MUSIC_TEXT_ANIMATION, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            items(
                items = MusicTextSwitchAnimations.presets,
                key = { "music_text_anim_${it.id}" },
                contentType = { "music_text_animation" },
            ) { preset ->
                SettingsChoiceRow(
                    title = stringResource(preset.labelRes),
                    subtitle = stringResource(preset.subtitleRes),
                    selected = MusicTextSwitchAnimations.normalizeId(musicTextSwitchAnimation) == preset.id,
                    onClick = { vm.setMusicTextSwitchAnimation(preset.id) },
                    scale = itemFisheye(listState, "music_text_anim_${preset.id}", screenCenterY, screenHeightPx)
                )
            }
        }

            SettingsDestination.TOOLS -> SettingsPageScaffold(
            title = stringResource(R.string.settings_tools_title),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.TOOLS).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.TOOLS).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.TOOLS, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("tools"),
                binding = object : SettingsEntryBinding {
                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean =
                        entry.id == "diagnostic_logging" && diagnosticLoggingEnabled

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        if (entry.id != "diagnostic_logging") return
                        diagnosticLoggingEnabled = checked
                        // 5 分钟后自动关闭，所以开关只是「打开一段时间的诊断日志」。
                        FlueLog.setDiagnosticLoggingEnabled(
                            context,
                            checked,
                            DIAGNOSTIC_LOG_DURATION_MS,
                        )
                    }

                    override fun onOpen(entry: SettingsEntry) {
                        when (entry) {
                            is SettingsEntry.Navigation -> {
                                val next = SettingsDestination.entries
                                    .firstOrNull { it.name == entry.destination } ?: return
                                navigateTo(next)
                            }
                            is SettingsEntry.Action -> when (entry.action) {
                                "export_log" -> settingsScope.launch { exportLog(context) }
                                "reset_settings" -> vm.resetSettings()
                            }
                            else -> Unit
                        }
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.BACKUP -> SettingsPageScaffold(
            title = stringResource(R.string.settings_backup_menu),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.BACKUP).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.BACKUP).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.BACKUP, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryGroup(
                entries = SettingsHierarchy.renderedGroup("backup"),
                binding = object : SettingsEntryBinding {
                    override fun onOpen(entry: SettingsEntry) {
                        val name = (entry as? SettingsEntry.Navigation)?.destination ?: return
                        val next = SettingsDestination.entries.firstOrNull { it.name == name } ?: return
                        navigateTo(next)
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.BACKUP_EXPORT -> SettingsPageScaffold(
            title = stringResource(R.string.settings_backup_export_page),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.BACKUP_EXPORT).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.BACKUP_EXPORT).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.BACKUP_EXPORT, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryScreen(
                entries = SettingsHierarchy.renderedGroup("backup_export"),
                binding = object : SettingsEntryBinding {
                    // 导出页的分区全部可用：能导出的就是这台设备现在有的东西。
                    override fun isVisible(entry: SettingsEntry): Boolean =
                        entry.id !in BackupOptionRows ||
                            FlueBackupOptions().valueOfRow(entry.id)

                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean =
                        effectiveBackupExportOptions.valueOfRow(entry.id)

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        backupExportOptions = effectiveBackupExportOptions.withRow(entry.id, checked)
                    }

                    override fun onOpen(entry: SettingsEntry) {
                        val action = (entry as? SettingsEntry.Action)?.action ?: return
                        if (action != "start_backup_export") return
                        if (!effectiveBackupExportOptions.hasAny) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_select_at_least_one),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (!backupBusy) {
                            pendingBackupExportOptions = effectiveBackupExportOptions
                            backupCreatePicker.launch(vm.suggestedBackupFileName())
                        }
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.BACKUP_IMPORT -> SettingsPageScaffold(
            title = stringResource(R.string.settings_backup_import_page),
            onBack = { handleBack() },
            headerTime = headerTime,
            initialFirstVisibleItemIndex = scrollFor(SettingsDestination.BACKUP_IMPORT).index,
            initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.BACKUP_IMPORT).offset,
            onScrollChanged = { index, offset -> updateScroll(SettingsDestination.BACKUP_IMPORT, index, offset) }
        ) { listState, screenCenterY, screenHeightPx, _ ->
            settingsEntryScreen(
                entries = SettingsHierarchy.renderedGroup("backup_import"),
                binding = object : SettingsEntryBinding {
                    // 没选文件之前只有「选择备份文件」一行；选完之后才按压缩包里
                    // 实际存在的分区显示勾选项，最后才是「开始导入」。
                    override fun isVisible(entry: SettingsEntry): Boolean {
                        if (entry.id == "import_backup_pick") return true
                        val preview = effectiveBackupImportPreview ?: return false
                        return entry.id !in BackupOptionRows ||
                            preview.availableOptions.valueOfRow(entry.id)
                    }

                    override fun isChecked(entry: SettingsEntry.Toggle): Boolean =
                        effectiveBackupImportOptions.valueOfRow(entry.id)

                    override fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {
                        val preview = effectiveBackupImportPreview ?: return
                        backupImportOptions =
                            effectiveBackupImportOptions.withRow(entry.id, checked)
                                .constrainedBy(preview.availableOptions)
                    }

                    @Composable
                    override fun subtitle(entry: SettingsEntry): String? {
                        if (entry.id != "import_backup_pick") return null
                        val preview = effectiveBackupImportPreview ?: return null
                        val unknownTime = stringResource(R.string.settings_backup_unknown_time)
                        return stringResource(
                            R.string.settings_backup_preview_fmt,
                            preview.appVersion,
                            preview.createdAt.ifBlank { unknownTime },
                        )
                    }

                    override fun onOpen(entry: SettingsEntry) {
                        val action = (entry as? SettingsEntry.Action)?.action ?: return
                        when (action) {
                            "pick_backup_file" -> if (!backupBusy) {
                                backupOpenPicker.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            }
                            "start_backup_import" -> {
                                val uri = backupImportUri
                                val options = effectiveBackupImportOptions
                                if (uri == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_pick_backup_first),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else if (!options.hasAny) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_select_at_least_one),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else if (!backupBusy) {
                                    settingsScope.launch {
                                        backupBusy = true
                                        backupProgress = BlockingProgressState(
                                            title = context.getString(R.string.progress_import_config),
                                            detail = context.getString(
                                                R.string.progress_import_config_detail
                                            )
                                        )
                                        vm.importBackup(uri, options)
                                            .onSuccess {
                                                backupProgress = BlockingProgressState(
                                                    title = context.getString(R.string.progress_import_done),
                                                    detail = options.backupSummaryText(context),
                                                    completed = true,
                                                    completionAction =
                                                        BlockingProgressCompletionAction.ImportBackup
                                                )
                                                backupImportPreview = null
                                                backupImportUri = null
                                            }
                                            .onFailure { error ->
                                                backupProgress = null
                                                Toast.makeText(
                                                    context,
                                                    context.getString(
                                                        R.string.toast_import_failed_fmt,
                                                        error.message
                                                            ?: error.javaClass.simpleName
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        backupBusy = false
                                    }
                                }
                            }
                        }
                    }
                },
                scaleOf = { key -> itemFisheye(listState, key, screenCenterY, screenHeightPx) },
            )
        }

            SettingsDestination.LANGUAGE -> SettingsPageScaffold(
                title = stringResource(R.string.settings_language),
                onBack = { handleBack() },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.LANGUAGE).index,
                initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.LANGUAGE).offset,
                onScrollChanged = { index, offset -> updateScroll(SettingsDestination.LANGUAGE, index, offset) }
            ) { listState, screenCenterY, screenHeightPx, _ ->
                com.flue.launcher.i18n.FlueLocaleManager.options.forEach { option ->
                    val key = "lang_${option.code.ifEmpty { "system" }}"
                    item(key) {
                        val label = when {
                            option.code.isEmpty() -> stringResource(R.string.settings_language_follow_system)
                            option.code == "zh"    -> "中文"
                            option.code == "en"    -> "English"
                            option.code == "ja"    -> "日本語"
                            else                   -> option.labelEn
                        }
                        SettingsChoiceRow(
                            title = label,
                            subtitle = "",
                            selected = appLanguage == option.code,
                            onClick = {
                                // 已经是当前语言就不弹重启确认，避免误触白跑一次冷启。
                                if (appLanguage == option.code) return@SettingsChoiceRow
                                pendingLanguageRestart = option.code
                            },
                            scale = itemFisheye(listState, key, screenCenterY, screenHeightPx)
                        )
                    }
                }
                // 英文 / 日文 由 AI 翻译，提示准确性可能不足
                if (appLanguage == "en" || appLanguage == "ja") {
                    item("lang_ai_disclaimer") {
                        MessageCard(
                            text = if (appLanguage == "ja")
                                "英語・日本語の翻訳は AI によって生成されており、完全に正確ではない場合があります。"
                            else
                                "English and Japanese translations are AI-generated and may not be fully accurate.",
                            background = WatchColors.SurfaceGlass,
                            onClick = {}
                        )
                    }
                }
            }

            SettingsDestination.ABOUT -> SettingsPageScaffold(
                title = stringResource(R.string.settings_about_title),
                onBack = { handleBack() },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.ABOUT).index,
                initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.ABOUT).offset,
                onScrollChanged = { index, offset -> updateScroll(SettingsDestination.ABOUT, index, offset) }
            ) { listState, screenCenterY, screenHeightPx, _ ->
                item("about_card") {
                    AboutCard(
                        onDonateClick = { navigateTo(SettingsDestination.DONATE) },
                        onVersionClick = {
                            val next = debugTapState.tap(SystemClock.elapsedRealtime())
                            debugTapState = next
                            if (next.openDebug) navigateTo(SettingsDestination.DEBUG)
                        },
                        scale = itemFisheye(listState, "about_card", screenCenterY, screenHeightPx)
                    )
                }
            }
            SettingsDestination.DEBUG -> DebugPage(
                vm = vm,
                weatherRepository = weatherRepository,
                onBack = { handleBack() },
                onCrash = { error("Flue debug crash") },
            )
            SettingsDestination.DONATE -> SettingsPageScaffold(
                title = stringResource(R.string.settings_donate_title),
                onBack = { handleBack() },
                headerTime = headerTime,
                initialFirstVisibleItemIndex = scrollFor(SettingsDestination.DONATE).index,
                initialFirstVisibleItemScrollOffset = scrollFor(SettingsDestination.DONATE).offset,
                onScrollChanged = { index, offset -> updateScroll(SettingsDestination.DONATE, index, offset) }
            ) { listState, screenCenterY, screenHeightPx, _ ->
                item("donate_tip") {
                    MessageCard(
                        text = stringResource(R.string.settings_donate_tip),
                        background = Color(0xFF1A2233),
                        onClick = {}
                    )
                }
                item("donate_wechat") {
                    DonateMethodCard(
                        title = stringResource(R.string.settings_donate_wechat),
                        subtitle = stringResource(R.string.settings_donate_preview_tap),
                        resId = R.drawable.donate_wechat,
                        scale = itemFisheye(listState, "donate_wechat", screenCenterY, screenHeightPx),
                        onPreviewClick = { donatePreviewResId = R.drawable.donate_wechat }
                    )
                }
                item("donate_alipay") {
                    DonateMethodCard(
                        title = stringResource(R.string.settings_donate_alipay),
                        subtitle = stringResource(R.string.settings_donate_preview_tap),
                        resId = R.drawable.donate_alipay,
                        scale = itemFisheye(listState, "donate_alipay", screenCenterY, screenHeightPx),
                        onPreviewClick = { donatePreviewResId = R.drawable.donate_alipay }
                    )
                }
            }
            }
            }
            }
            }
            if (destinationTransition.isRunning) {
                // 新旧设置页在推拉期间会同时留在树上；用最上层命中面锁住
                // 这 260ms，避免快速二次点击落到正在滑出的旧页面。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(100f)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        },
                )
            }
        }

        donatePreviewResId?.let { resId ->
            DonatePreviewDialog(resId = resId, onDismiss = { donatePreviewResId = null })
        }

        if (watchFaceLongPressModeDialogVisible) {
            FlueSingleChoiceDialog(
                onDismissRequest = { watchFaceLongPressModeDialogVisible = false },
                title = stringResource(R.string.settings_watchface_long_press_mode),
                options = listOf(
                    WatchFaceLongPressMode.CARDS to
                        stringResource(R.string.settings_watchface_long_press_mode_cards),
                    WatchFaceLongPressMode.LIST to
                        stringResource(R.string.settings_watchface_long_press_mode_list),
                ),
                selected = watchFaceLongPressMode,
                onSelect = vm::setWatchFaceLongPressMode,
                cancelText = stringResource(android.R.string.cancel),
            )
        }

        if (weatherCityDialogVisible) {
            FlueAlertDialog(
                onDismissRequest = { weatherCityDialogVisible = false },
                title = { Text(stringResource(R.string.settings_watch_weather_city_dialog_title)) },
                // T-06：关掉外壳整块滚动，「搜索 / 取消」才钉得住卡底。
                // 候选城市能出七八条，原来叠着两层滚动（外壳 + 这里的 verticalScroll），
                // 内层吞掉纵向拖动、外层滚不动，按钮就摸不到了。
                scrollableContent = false,
                text = {
                    Column(
                        // 高度不再写死 360dp：`scrollableContent = false` 之后这里拿到的是
                        // 「卡高减掉标题与按钮」的剩余约束，内容超出时由这一层自己滚。
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.settings_watch_weather_city_dialog_message))
                        OutlinedTextField(
                            value = weatherCityDraft,
                            onValueChange = {
                                weatherCityDraft = it.take(120)
                                weatherCityCandidates = emptyList()
                                weatherCitySearchError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_watch_weather_city)) },
                            placeholder = {
                                Text(stringResource(R.string.settings_watch_weather_city_hint))
                            },
                            singleLine = true,
                        )
                        if (weatherCitySearching) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FlueLoadingIndicator(size = 18.dp)
                                Text(stringResource(R.string.settings_watch_weather_city_searching))
                            }
                        }
                        weatherCitySearchError?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                        weatherCityCandidates.forEach { candidate ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    weatherLocationSelectionGeneration++
                                    weatherRepository.setManualLocation(candidate)
                                    weatherLocationLabel = candidate.label.orEmpty()
                                    weatherUsingSystemLocation = false
                                    weatherCityDialogVisible = false
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.toast_weather_city_saved_fmt,
                                            candidate.label.orEmpty(),
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Text(
                                    text = buildString {
                                        append(candidate.label.orEmpty())
                                        append('\n')
                                        append(String.format(Locale.US, "%.4f, %.4f", candidate.latitude, candidate.longitude))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = weatherCityDraft.isNotBlank() && !weatherCitySearching,
                        onClick = {
                            val query = weatherCityDraft.trim().take(120)
                            if (query.isEmpty()) return@TextButton
                            weatherCitySearching = true
                            weatherCityCandidates = emptyList()
                            weatherCitySearchError = null
                            settingsScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { weatherRepository.searchLocations(query) }
                                }
                                weatherCitySearching = false
                                weatherCityCandidates = result.getOrDefault(emptyList())
                                weatherCitySearchError = when {
                                    result.isFailure -> context.getString(R.string.settings_watch_weather_city_search_failed)
                                    weatherCityCandidates.isEmpty() -> context.getString(R.string.settings_watch_weather_city_no_results)
                                    else -> null
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.action_search))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { weatherCityDialogVisible = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        tapActionPicker?.let { action ->
            WatchTapActionAppPickerDialog(
                action = action,
                onDismiss = { tapActionPicker = null },
                onChanged = { tapActionRevision++ },
            )
        }

        pendingLanguageRestart?.let { languageCode ->
            val dialogContext = remember(context, languageCode) {
                FlueLocaleManager.contextForLanguageSelection(context, languageCode)
            }
            FlueAlertDialog(
                onDismissRequest = {
                    if (!languageSaveInProgress) pendingLanguageRestart = null
                },
                title = { Text(dialogContext.getString(R.string.settings_language_restart_title)) },
                text = { Text(dialogContext.getString(R.string.settings_language_restart_message)) },
                confirmButton = {
                    TextButton(
                        enabled = !languageSaveInProgress,
                        onClick = {
                            if (languageSaveInProgress) return@TextButton
                            languageSaveInProgress = true
                            settingsScope.launch {
                                val saved = vm.setAppLanguage(context, languageCode)
                                languageSaveInProgress = false
                                if (saved) {
                                    pendingLanguageRestart = null
                                    restartFlueProcess(context)
                                } else {
                                    Toast.makeText(
                                        context,
                                        dialogContext.getString(R.string.toast_language_save_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    ) { Text(dialogContext.getString(R.string.settings_language_restart_confirm)) }
                },
                dismissButton = {
                    TextButton(
                        enabled = !languageSaveInProgress,
                        onClick = { pendingLanguageRestart = null },
                    ) {
                        Text(dialogContext.getString(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
internal fun AppListFlowShapePreview(
    bulgePercent: Int,
    toRight: Boolean,
    curve: com.flue.launcher.util.FlowShapeCurve,
    modifier: Modifier = Modifier,
) {
    val style = LauncherTheme.style
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(style.compactShape)
            .background(style.pressedCardColor),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val rowGap = size.height / 4.5f
            val direction = if (toRight) FlowCrescentDirection.Right else FlowCrescentDirection.Left
            repeat(5) { index ->
                val y = centerY + (index - 2) * rowGap
                val offset = flowCrescentOffsetPx(
                    rowCenterY = y,
                    viewportCenterY = centerY,
                    viewportHeight = size.height,
                    bulgePx = size.width * 0.24f * bulgePercent / 100f,
                    direction = direction,
                    curve = curve,
                )
                drawCircle(
                    color = style.accentColor,
                    radius = 8.dp.toPx(),
                    center = Offset(centerX + offset, y),
                )
                drawLine(
                    color = style.secondaryTextColor.copy(alpha = 0.28f),
                    start = Offset(centerX, y),
                    end = Offset(centerX + offset, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}

/**
 * 整进程冷启，让每个 Activity 的 attachBaseContext 重新走一遍
 * FlueLocaleManager.applyLocale。
 *
 * 只 recreate() 当前 Activity 是不够的：桌面那边的 LauncherActivity 还活着，
 * 它持有的 Configuration 仍是旧 locale，切回去就露出没翻译的旧文案。
 * 直接用 CLEAR_TASK 拉起新的 LauncherActivity，再结束旧设置页。
 * 新 Activity 的 attachBaseContext 会重新读取 SharedPreferences 语言真源，
 * 不依赖可能被系统批处理的 AlarmManager，也不需要杀进程留下黑屏窗口。
 */
internal fun restartFlueProcess(context: android.content.Context) {
    // 包内同时声明了 LauncherActivity 与 SettingsActivity 两个 LAUNCHER
    // 入口；getLaunchIntentForPackage() 在部分 ROM 上会解析回当前设置页，
    // CLEAR_TASK 后等于又打开旧入口。显式指定桌面 Activity，确保新的
    // attachBaseContext 一定从 SharedPreferences 启动真源读取语言。
    val launchIntent = android.content.Intent(context, LauncherActivity::class.java).apply {
        addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
    }

    runCatching {
        context.startActivity(launchIntent)
        // CLEAR_TASK already removes this Activity in the normal path; finish is
        // an idempotent fallback for OEM task managers that keep the old record.
        (context as? android.app.Activity)?.finish()
    }.onFailure {
        // OEM task managers can reject a task replacement while the Activity is
        // stopping. Keep the language saved and at least refresh this page.
        (context as? android.app.Activity)?.recreate()
    }
}

private fun shareBackupUri(context: android.content.Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                context.getString(R.string.backup_share_title)
            )
        )
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
    }
}

private suspend fun exportLog(context: android.content.Context) {
    try {
        val file = withContext(Dispatchers.IO) {
            val rawLog = runCatching {
                Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-d", "-t", "500"))
                    .inputStream
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrElse { error -> "logcat export failed: ${error.message.orEmpty()}" }
            val crashSummary = listOf("crash_brief.txt", "crash_info.txt", "crash_app_info.txt")
                .mapNotNull { name ->
                    val crashFile = File(context.cacheDir, name)
                    if (!crashFile.exists()) return@mapNotNull null
                    "===== $name =====\n${crashFile.readText()}"
                }
                .joinToString("\n\n")
                .ifBlank { "No private crash files found." }
            File(context.cacheDir, "wlauncher_log.txt").apply {
                writeText(
                    buildString {
                        appendLine("===== Flue Diagnostics =====")
                        appendLine("diagnosticLoggingEnabled=${FlueLog.isDiagnosticLoggingEnabled()}")
                        appendLine()
                        appendLine("===== Private Crash Files =====")
                        appendLine(crashSummary)
                        appendLine()
                        appendLine("===== Raw logcat tail =====")
                        append(rawLog)
                    }
                )
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                context.getString(R.string.settings_export_log)
            )
        )
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun hasBodySensorsPermission(context: Context): Boolean =
    hasHeartRatePermission(context)

private fun openHeartRatePermissionSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        )
    }
}
