package com.fluly.launcher.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fluly.launcher.ui.settings.ActionCard
import com.fluly.launcher.R
import com.fluly.launcher.ui.settings.SectionTitle
import com.fluly.launcher.ui.settings.SettingsChoiceRow
import com.fluly.launcher.ui.settings.SettingsPageScaffold
import com.fluly.launcher.ui.settings.SettingsSliderRow
import com.fluly.launcher.applist.background.AppListBackgroundConfig
import com.fluly.launcher.applist.background.AppListBackgroundMode
import com.fluly.launcher.applist.background.AppListBackgroundPreviewCard
import com.fluly.launcher.applist.background.rememberAppListBackgroundPainting
import com.fluly.launcher.ui.settings.itemFisheye
import com.fluly.launcher.ui.common.flulyColorToHex
import com.fluly.launcher.ui.drawer.AppListPalette
import com.fluly.launcher.ui.settings.miuix.MiuixIcons
import com.fluly.launcher.ui.settings.miuix.SettingsIcon
import com.fluly.launcher.ui.style.FlulyButton
import com.fluly.launcher.ui.style.flulySettingsGroup
import com.fluly.launcher.viewmodel.LauncherViewModel
import top.yukonga.miuix.kmp.icon.MiuixIcons as OfficialMiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder as OfficialFolder
import top.yukonga.miuix.kmp.icon.extended.Refresh as OfficialRefresh
import top.yukonga.miuix.kmp.icon.extended.Settings as OfficialSettings
import kotlin.math.roundToInt

/**
 * 应用列表背景屏（`SettingsDestination.APP_LIST_BACKGROUND`）。
 *
 * ## 为什么这一屏也不进数据层
 *
 * 12 个 item 里有 10 个不是「值 + 开关」：最上面是实时预览卡（模式、颜色、模糊、
 * 遮罩改哪一项都立刻重绘），中间四项是互斥的单选但选中态来自同一个枚举，
 * 后面两块条件项依赖「当前模式是否有底图」，模糊/遮罩两条滑块还带一个跨两行的
 * 重置按钮。要数据化就得给模型加「动态条目」「条件组」「组尾自定义内容」三类逃生舱，
 * 那等于换一种写法写代码。所以整页手写，只是搬出 `SettingsActivity`。
 *
 * ## 拖动值为什么在这里而不是外层
 *
 * 两条滑块抬手才落盘（每帧提交等于拖动时连烘十几张模糊图），但预览卡要跟手，
 * 所以拖动值单独存一份只喂预览。它是纯粹的瞬态 —— 离开页面就该丢，
 * 放在页面里比放在设置页根作用域更准确。
 */

/** 预览卡需要的素材来源：表盘状态与内置表盘资源，都在设置页根作用域，页面自己拿不到。 */
internal data class BackgroundPreviewSources(
    val watchFaceId: String,
    val watchFaceStableKey: String,
    val watchFacePreviewPath: String?,
    val builtInPhotoPath: String?,
    val builtInVideoPath: String?,
    val watchFaceRefreshToken: Int,
)

@Composable
internal fun AppListBackgroundPage(
    onBack: () -> Unit,
    headerTime: String,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollChanged: (Int, Int) -> Unit,
    vm: LauncherViewModel,
    background: AppListBackgroundConfig,
    palette: AppListPalette,
    darkMode: Boolean,
    cornerRadiusDp: Int,
    sources: BackgroundPreviewSources,
    onPickColor: () -> Unit,
    onPickImage: () -> Unit,
    onPickImageFromDevice: () -> Unit,
) {
    var blurDrag by remember { mutableStateOf<Int?>(null) }
    var scrimDrag by remember { mutableStateOf<Int?>(null) }
    // 落盘值追上拖动值之后就把覆盖清掉。不能在 onValueChangeFinished 里直接清：
    // StateFlow 的那一跳要等一次协程调度，中间会有一帧读到旧值，预览就闪一下。
    LaunchedEffect(background.blurPercent, blurDrag) {
        if (blurDrag == background.blurPercent) blurDrag = null
    }
    LaunchedEffect(background.scrimPercent, scrimDrag) {
        if (scrimDrag == background.scrimPercent) scrimDrag = null
    }
    /** 预览用的配置：落盘值叠上拖动中的临时值。 */
    val previewConfig = remember(background, blurDrag, scrimDrag) {
        background.copy(
            blurPercent = blurDrag ?: background.blurPercent,
            scrimPercent = scrimDrag ?: background.scrimPercent,
        )
    }
    // 按**屏幕**尺寸解码而不是按卡片尺寸：缓存键里带着目标尺寸，用屏幕尺寸就能和启动器那份
    // 现成的缓存对上（老师是从列表上滑进设置的，那张图基本一定是热的），一次多余解码都不用。
    val viewport = with(LocalDensity.current) {
        LocalConfiguration.current.let { config ->
            config.screenWidthDp.dp.roundToPx() to config.screenHeightDp.dp.roundToPx()
        }
    }
    val previewPainting = rememberAppListBackgroundPainting(
        config = previewConfig,
        fallbackColor = palette.background,
        darkMode = darkMode,
        viewportWidthPx = viewport.first,
        viewportHeightPx = viewport.second,
        watchFaceId = sources.watchFaceId,
        watchFaceStableKey = sources.watchFaceStableKey,
        watchFacePreviewPath = sources.watchFacePreviewPath,
        builtInPhotoPath = sources.builtInPhotoPath,
        builtInVideoPath = sources.builtInVideoPath,
        watchFaceRefreshToken = sources.watchFaceRefreshToken,
        // Android 12 以下拖动时不做模糊：一次拖动要连烘十几张，低端机直接卡死。
        blurPercentOverride = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            blurDrag != null
        ) 0 else null,
    )

    SettingsPageScaffold(
    title = stringResource(R.string.settings_app_list_background),
    onBack = onBack,
    headerTime = headerTime,
    initialFirstVisibleItemIndex = initialScrollIndex,
    initialFirstVisibleItemScrollOffset = initialScrollOffset,
    // 预览卡插在最前面之后整体后移一位。圆屏仍旧把「背景模式」这行摆在屏幕正中 ——
    // 卡片就在它上面，居中位置上方的半屏空间装得下（卡高不超过屏高 38%），
    // 所以进页面时预览与选项都看得见。
    roundFirstActionIndex = 2,
    onScrollChanged = onScrollChanged,
) { listState, screenCenterY, screenHeightPx, _ ->
    val backgroundMode = background.mode
    // 实时预览：模式、颜色、图片、模糊、遮罩改哪一项都立刻反映在这张卡上，
    // 拖滑块时也跟手（拖动值单独存了一份，见 previewConfig）。
    item("app_list_background_preview") {
        val previewScale = itemFisheye(
            listState,
            "app_list_background_preview",
            screenCenterY,
            screenHeightPx,
        )
        AppListBackgroundPreviewCard(
            painting = previewPainting,
            iconColor = palette.item,
            labelColor = palette.text,
            squareCornerRadiusDp = cornerRadiusDp,
            modifier = Modifier.graphicsLayer {
                scaleX = previewScale
                scaleY = previewScale
                alpha = previewScale.coerceIn(0.55f, 1f)
            },
        )
    }
    item("app_list_background_mode_header") {
        SectionTitle(
            stringResource(R.string.settings_app_list_background_mode),
            itemFisheye(
                listState,
                "app_list_background_mode_header",
                screenCenterY,
                screenHeightPx,
            ),
        )
    }
    item("app_list_background_solid_system") {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_app_list_background_solid_system),
            subtitle = stringResource(
                R.string.settings_app_list_background_solid_system_sub,
            ),
            selected = backgroundMode == AppListBackgroundMode.SOLID_SYSTEM,
            onClick = {
                vm.setAppListBackgroundMode(AppListBackgroundMode.SOLID_SYSTEM)
            },
            scale = itemFisheye(
                listState,
                "app_list_background_solid_system",
                screenCenterY,
                screenHeightPx,
            ),
        )
    }
    item("app_list_background_solid_custom") {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_app_list_background_solid_custom),
            subtitle = stringResource(
                R.string.settings_app_list_background_solid_custom_sub,
            ),
            selected = backgroundMode == AppListBackgroundMode.SOLID_CUSTOM,
            onClick = {
                vm.setAppListBackgroundMode(AppListBackgroundMode.SOLID_CUSTOM)
            },
            scale = itemFisheye(
                listState,
                "app_list_background_solid_custom",
                screenCenterY,
                screenHeightPx,
            ),
        )
    }
    item("app_list_background_watch_face") {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_app_list_background_watch_face),
            subtitle = stringResource(R.string.settings_app_list_background_watch_face_sub),
            selected = backgroundMode == AppListBackgroundMode.WATCH_FACE,
            onClick = { vm.setAppListBackgroundMode(AppListBackgroundMode.WATCH_FACE) },
            scale = itemFisheye(
                listState,
                "app_list_background_watch_face",
                screenCenterY,
                screenHeightPx,
            ),
        )
    }
    item("app_list_background_custom_image") {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_app_list_background_custom_image),
            subtitle = stringResource(
                R.string.settings_app_list_background_custom_image_sub,
            ),
            selected = backgroundMode == AppListBackgroundMode.CUSTOM_IMAGE,
            onClick = { vm.setAppListBackgroundMode(AppListBackgroundMode.CUSTOM_IMAGE) },
            scale = itemFisheye(
                listState,
                "app_list_background_custom_image",
                screenCenterY,
                screenHeightPx,
            ),
        )
    }
    // 自定义颜色：只在那一档露出来。选完颜色会自动切到这一档（见弹窗回调）。
    if (backgroundMode == AppListBackgroundMode.SOLID_CUSTOM) {
        item("app_list_background_pick_color") {
            ActionCard(
                title = stringResource(R.string.settings_app_list_background_color),
                subtitle = flulyColorToHex(Color(background.customColorArgb)),
                scale = itemFisheye(
                    listState,
                    "app_list_background_pick_color",
                    screenCenterY,
                    screenHeightPx,
                ),
                icon = {
                    SettingsIcon(
                        material = OfficialMiuixIcons.OfficialSettings,
                        miuix = OfficialMiuixIcons.OfficialSettings,
                        tint = Color(background.customColorArgb),
                    )
                },
                onClick = { onPickColor() },
            )
        }
    }
    // 自定义图片：点击走系统选择器，长按拉起内置文件选择器
    // （与「导入表盘」同一套约定，老师已经熟悉这个手势）。
    if (backgroundMode == AppListBackgroundMode.CUSTOM_IMAGE) {
        item("app_list_background_pick_image") {
            ActionCard(
                title = if (background.imagePath.isNullOrBlank()) {
                    stringResource(R.string.settings_app_list_background_pick_image)
                } else {
                    stringResource(R.string.settings_app_list_background_change_image)
                },
                subtitle = stringResource(
                    R.string.settings_app_list_background_pick_image_sub,
                ),
                scale = itemFisheye(
                    listState,
                    "app_list_background_pick_image",
                    screenCenterY,
                    screenHeightPx,
                ),
                icon = {
                    SettingsIcon(
                        material = OfficialMiuixIcons.OfficialFolder,
                        miuix = OfficialMiuixIcons.OfficialFolder,
                    )
                },
                onClick = { onPickImage() },
                onLongPress = onPickImageFromDevice,
            )
        }
        if (!background.imagePath.isNullOrBlank()) {
            item("app_list_background_clear_image") {
                ActionCard(
                    title = stringResource(
                        R.string.settings_app_list_background_clear_image,
                    ),
                    subtitle = stringResource(
                        R.string.settings_app_list_background_clear_image_sub,
                    ),
                    scale = itemFisheye(
                        listState,
                        "app_list_background_clear_image",
                        screenCenterY,
                        screenHeightPx,
                    ),
                    icon = {
                        SettingsIcon(
                            material = OfficialMiuixIcons.OfficialRefresh,
                            miuix = OfficialMiuixIcons.OfficialRefresh,
                        )
                    },
                    onClick = { vm.setAppListBackgroundImagePath(null) },
                )
            }
        }
    }
    // 模糊与遮罩只对「有底图」的两档有意义 —— 纯色档下它们无处施加，
    // 露出来只会让人以为是坏的。
    if (backgroundMode.usesImage) {
        item("app_list_background_effect_header") {
            SectionTitle(
                stringResource(R.string.settings_app_list_background_effects),
                itemFisheye(
                    listState,
                    "app_list_background_effect_header",
                    screenCenterY,
                    screenHeightPx,
                ),
            )
        }
        flulySettingsGroup {
            row("app_list_background_blur") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_app_list_background_blur),
                    value = background.blurPercent.toFloat(),
                    valueText = "${background.blurPercent}%",
                    localValueText = { "${it.roundToInt()}%" },
                    range = 0f..100f,
                    steps = 0,
                    // 拖动值只喂预览卡，不落盘。
                    onValueChange = {
                        blurDrag = it.roundToInt()
                    },
                    // 只在抬手时落盘：模糊要重新烘焙（30 及以下）或换一档 RenderEffect，
                    // 每帧都提交等于拖动时连烘十几张。
                    onValueChangeFinished = {
                        vm.setAppListBackgroundBlurPercent(it.roundToInt())
                        blurDrag = null
                    },
                    supportingText = stringResource(
                        R.string.settings_app_list_background_blur_sub,
                    ),
                    scale = itemFisheye(
                        listState,
                        "app_list_background_blur",
                        screenCenterY,
                        screenHeightPx,
                    ),
                    groupPosition = position,
                    footer = {
                        FlulyButton(
                            onClick = {
                                blurDrag = null
                                scrimDrag = null
                                vm.resetAppListBackgroundEffects()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_app_list_background_reset))
                        }
                    },
                )
            }
            row("app_list_background_scrim") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_app_list_background_scrim),
                    value = background.scrimPercent.toFloat(),
                    valueText = "${background.scrimPercent}%",
                    localValueText = { "${it.roundToInt()}%" },
                    range = 0f..100f,
                    steps = 0,
                    onValueChange = {
                        scrimDrag = it.roundToInt()
                    },
                    onValueChangeFinished = {
                        vm.setAppListBackgroundScrimPercent(it.roundToInt())
                        scrimDrag = null
                    },
                    supportingText = stringResource(
                        R.string.settings_app_list_background_scrim_sub,
                    ),
                    scale = itemFisheye(
                        listState,
                        "app_list_background_scrim",
                        screenCenterY,
                        screenHeightPx,
                    ),
                    groupPosition = position,
                )
            }
        }
    }
}
}
