package com.flue.launcher.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flue.launcher.ui.settings.ActionCard
import com.flue.launcher.AppListFlowShapePreview
import com.flue.launcher.R
import com.flue.launcher.ui.settings.SectionTitle
import com.flue.launcher.ui.settings.SettingsChoiceRow
import com.flue.launcher.ui.settings.SettingsPageScaffold
import com.flue.launcher.ui.settings.SettingsSliderRow
import com.flue.launcher.ui.settings.SettingsSwitchRow
import com.flue.launcher.ui.settings.itemFisheye
import com.flue.launcher.layout.FLUE_LAYOUT_CRESCENT_BULGE_MAX
import com.flue.launcher.layout.FlueLayoutProfiles
import com.flue.launcher.restartFlueProcess
import com.flue.launcher.ui.navigation.LayoutMode
import com.flue.launcher.ui.settings.miuix.MiuixIcons
import com.flue.launcher.ui.style.FlueButton
import com.flue.launcher.ui.style.flueSettingsGroup
import com.flue.launcher.viewmodel.LauncherViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 显示大小屏（`SettingsDestination.DISPLAY_SIZE`）。
 *
 * ## 为什么这一屏没有走数据驱动渲染
 *
 * 前面几屏（外观、功能开关、副一屏、通知、手势）迁到了 `SettingsHierarchy` +
 * `settingsEntryGroup`，条目都是静态的。这一屏不行，它有三块真·动态内容：
 *
 * 1. **布局档案列表** —— `items(layoutProfiles.filter { ... })`，档案是用户数据，
 *    数量与内容运行时才知道，选中态跟着 `activeLayoutProfile` 走；
 * 2. **心流曲线的四个参数滑杆** —— 由 `activeLayoutProfile.flowShapeCurve` 现场生成，
 *    写进静态数据表只会在换档案后显示旧值；
 * 3. **心流形状预览** —— 纯绘制的 `item`，不是设置项，没有"值"可声明。
 *
 * 硬塞进数据模型的代价是给模型加「动态条目」「自定义绘制槽」这类逃生舱，
 * 那会把「用数据描述界面」退化成「用另一种写法写代码」。所以这一屏保持手写，
 * 只是整页搬出 `SettingsActivity` —— 后者的职责收敛成路由。
 *
 * ## 为什么是「整页」而不是「列表内容」
 *
 * `SettingsPageScaffold` 的 content 不是 `@Composable`：同一段构建代码还要在
 * `LaunchedEffect` 里被喂给 `SettingsKeyIndexRecorder` 建搜索索引，那里没有组合上下文。
 * 所以状态收集必须发生在 scaffold **外面**，也就只能做成一个整页 composable。
 *
 * @param onRequestRenameProfile 档案重命名弹窗归外层管（Dialog 不在列表里）。
 * @param onExportProfiles 导出走 SAF，launcher 由外层创建。
 * @param onImportProfiles 导入同上。
 */
@Composable
internal fun DisplaySizePage(
    onBack: () -> Unit,
    headerTime: String,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollChanged: (Int, Int) -> Unit,
    vm: LauncherViewModel,
    context: Context,
    onRequestRenameProfile: () -> Unit,
    onExportProfiles: () -> Unit,
    onImportProfiles: () -> Unit,
) {
    val globalUiScalePercent by vm.globalUiScalePercent.collectAsStateWithLifecycle()
    val honeycombCols by vm.honeycombCols.collectAsStateWithLifecycle()
    val appListScalePercent by vm.appListScalePercent.collectAsStateWithLifecycle()
    val appListScrollIndicatorEnabled by vm.appListScrollIndicatorEnabled.collectAsStateWithLifecycle()
    val appListEdgeAlignEnabled by vm.appListEdgeAlignEnabled.collectAsStateWithLifecycle()
    val layoutMode by vm.layoutMode.collectAsStateWithLifecycle()
    val appListFisheyeEnabled by vm.appListFisheyeEnabled.collectAsStateWithLifecycle()
    val appListEdgeSpacingCompressionEnabled by
        vm.appListEdgeSpacingCompressionEnabled.collectAsStateWithLifecycle()
    val appListFisheyeRangeRows by vm.appListFisheyeRangeRows.collectAsStateWithLifecycle()
    val appListFisheyeStrengthPercent by vm.appListFisheyeStrengthPercent.collectAsStateWithLifecycle()
    val appListLeftSafeInsetPercent by vm.appListLeftSafeInsetPercent.collectAsStateWithLifecycle()
    val materialHoneycombTopFisheyeEnabled by
        vm.materialHoneycombTopFisheyeEnabled.collectAsStateWithLifecycle()
    val appListGridIconPlateEnabled by vm.appListGridIconPlateEnabled.collectAsStateWithLifecycle()
    val appListFlowCrescentToRight by vm.appListFlowCrescentToRight.collectAsStateWithLifecycle()
    val appListFlowCrescentBulgePercent by vm.appListFlowCrescentBulgePercent.collectAsStateWithLifecycle()
    val layoutProfiles by vm.layoutProfiles.collectAsStateWithLifecycle()
    val activeLayoutProfile by vm.activeLayoutProfile.collectAsStateWithLifecycle()
    val rotaryHapticsEnabled by vm.rotaryHapticsEnabled.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(R.string.settings_display_fit),
        onBack = onBack,
        headerTime = headerTime,
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
        onScrollChanged = onScrollChanged,
    ) { listState, screenCenterY, screenHeightPx, _ ->
        flueSettingsGroup {
            row("global_ui_scale") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_global_ui_size),
                    value = globalUiScalePercent.toFloat(),
                    valueText = "${globalUiScalePercent}%",
                    localValueText = { "${((it / 5f).roundToInt() * 5).coerceIn(50, 150)}%" },
                    range = 50f..150f,
                    steps = 19,
                    onValueChange = {},
                    onValueChangeFinished = { vm.setGlobalUiScalePercent(((it / 5f).roundToInt() * 5).coerceIn(50, 150)) },
                    scale = itemFisheye(listState, "global_ui_scale", screenCenterY, screenHeightPx),
                    supportingText = stringResource(R.string.settings_global_ui_restart_hint),
                    groupPosition = position,
                    // 重启按钮挨着滑杆放在同一张卡里：这一项只有重启后才全量生效，
                    // 动作离它太远会读成「另一个功能」。
                    footer = {
                        FlueButton(
                            onClick = { restartFlueProcess(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.settings_global_ui_apply_restart),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                )
            }
            row("honeycomb_cols") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_honeycomb_cols),
                    value = honeycombCols.toFloat(),
                    valueText = stringResource(R.string.settings_honeycomb_cols_fmt, honeycombCols),
                    localValueText = { context.resources.getString(R.string.settings_honeycomb_cols_fmt, it.roundToInt()) },
                    range = 1f..5f,
                    steps = 3,
                    onValueChange = { vm.setHoneycombCols(it.roundToInt()) },
                    scale = itemFisheye(listState, "honeycomb_cols", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("app_list_scale") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_app_list_size),
                    value = appListScalePercent.toFloat(),
                    valueText = "${appListScalePercent}%",
                    range = 50f..200f,
                    steps = 14,
                    onValueChange = { vm.setAppListScalePercent(it.toInt()) },
                    scale = itemFisheye(listState, "app_list_scale", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            // 指示器和这三项同卡：它们都是「整块屏幕怎么摆」这一层，与布局样式无关。
            // 不放进下面的鱼眼组是因为鱼眼那一卡里每一项都被 `enabled = appListFisheyeEnabled`
            // 串着，指示器跟鱼眼毫无关系，混进去会被读成「关掉鱼眼这条也就没了」。
            row("app_list_scroll_indicator") { position ->
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_scroll_indicator),
                    subtitle = if (appListScrollIndicatorEnabled) {
                        stringResource(R.string.settings_scroll_indicator_on)
                    } else {
                        stringResource(R.string.settings_scroll_indicator_off)
                    },
                    checked = appListScrollIndicatorEnabled,
                    onToggle = vm::setAppListScrollIndicatorEnabled,
                    scale = itemFisheye(listState, "app_list_scroll_indicator", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("app_list_edge_align") { position ->
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_app_list_edge_align),
                    subtitle = if (appListEdgeAlignEnabled) {
                        stringResource(R.string.settings_app_list_edge_align_on)
                    } else {
                        stringResource(R.string.settings_app_list_edge_align_off)
                    },
                    checked = appListEdgeAlignEnabled,
                    onToggle = vm::setAppListEdgeAlignEnabled,
                    scale = itemFisheye(listState, "app_list_edge_align", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
        }

        // 鱼眼三段：总开关 → 子开关组 → 数值组。
        // 以前是开关与滑块交替（开关 → 滑块 → 开关 → 滑块 → 开关 → 滑块），
        // 读起来找不到主次。网格 / 心流各自专属那几项只在对应布局显示。
        flueSettingsGroup {
            row("app_list_fisheye") { position ->
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_fisheye),
                    subtitle = stringResource(R.string.settings_fisheye_sub),
                    checked = appListFisheyeEnabled,
                    onToggle = vm::setAppListFisheyeEnabled,
                    scale = itemFisheye(listState, "app_list_fisheye", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            if (layoutMode != LayoutMode.Flow) {
            row("app_list_edge_spacing_compression") { position ->
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_edge_spacing),
                    subtitle = stringResource(R.string.settings_edge_spacing_sub),
                    checked = appListEdgeSpacingCompressionEnabled,
                    enabled = appListFisheyeEnabled,
                    onToggle = vm::setAppListEdgeSpacingCompressionEnabled,
                    scale = itemFisheye(listState, "app_list_edge_spacing_compression", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            }
            row("app_list_fisheye_range_rows") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_fisheye_range),
                    supportingText = stringResource(R.string.settings_fisheye_range_sub),
                    value = appListFisheyeRangeRows.toFloat(),                    valueText = stringResource(R.string.settings_fisheye_range_fmt, appListFisheyeRangeRows),
                    localValueText = {
                        val rows = it.roundToInt()
                        context.resources.getString(R.string.settings_fisheye_range_fmt, rows)
                    },
                    range = 1f..8f,
                    steps = 6,
                    onValueChange = { vm.setAppListFisheyeRangeRows(it.roundToInt()) },
                    enabled = appListFisheyeEnabled,
                    scale = itemFisheye(listState, "app_list_fisheye_range_rows", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("app_list_fisheye_strength") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_fisheye_strength),
                    supportingText = stringResource(R.string.settings_fisheye_strength_sub),
                    value = appListFisheyeStrengthPercent.toFloat(),
                    valueText = "${appListFisheyeStrengthPercent}%",
                    localValueText = { "${it.roundToInt()}%" },
                    range = 0f..200f,
                    steps = 19,
                    onValueChange = { vm.setAppListFisheyeStrengthPercent(it.roundToInt()) },
                    enabled = appListFisheyeEnabled,
                    scale = itemFisheye(listState, "app_list_fisheye_strength", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("app_list_left_safe_inset") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_left_safe_area),
                    supportingText = stringResource(R.string.settings_left_safe_area_sub),
                    value = appListLeftSafeInsetPercent.toFloat(),
                    valueText = "${appListLeftSafeInsetPercent}%",
                    localValueText = { "${((it / 5f).roundToInt() * 5).coerceIn(0, 50)}%" },
                    range = 0f..50f,
                    steps = 9,
                    onValueChange = { vm.setAppListLeftSafeInsetPercent((it / 5f).roundToInt() * 5) },
                    scale = itemFisheye(listState, "app_list_left_safe_inset", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
        }
        // 网格布局专属那一组。
        //
        // 这两项的实现都只看密集网格那条路：「首行额外缩放」只被
        // `nativeDenseGridChildVisual` 读（其余布局连读都不读），底板则是网格格子
        // 独有的那块 rowBackground。摆在鱼眼组里时，用列表 / 蜂窝 / 心流的人也会看到
        // 一个拨了完全没反应的开关 —— 所以按布局收起来，与下面心流那一组同一个规矩。
        if (layoutMode == LayoutMode.Grid) {
            flueSettingsGroup {
                row("material_honeycomb_top_fisheye") { position ->
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_fisheye_top_row),
                        subtitle = stringResource(R.string.settings_fisheye_top_row_sub),
                        checked = materialHoneycombTopFisheyeEnabled,
                        // 顶部鱼眼是鱼眼的子项：`nativeDenseGridChildVisual` 里
                        // 顶部带要 `fisheyeEnabled && honeycombTopFisheyeEnabled` 同时成立。
                        enabled = appListFisheyeEnabled,
                        onToggle = vm::setMaterialHoneycombTopFisheyeEnabled,
                        scale = itemFisheye(listState, "material_honeycomb_top_fisheye", screenCenterY, screenHeightPx),
                        groupPosition = position,
                    )
                }
                row("app_list_grid_icon_plate") { position ->
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_grid_icon_plate),
                        subtitle = if (appListGridIconPlateEnabled) {
                            stringResource(R.string.settings_grid_icon_plate_on)
                        } else {
                            stringResource(R.string.settings_grid_icon_plate_off)
                        },
                        checked = appListGridIconPlateEnabled,
                        onToggle = vm::setAppListGridIconPlateEnabled,
                        scale = itemFisheye(listState, "app_list_grid_icon_plate", screenCenterY, screenHeightPx),
                        groupPosition = position,
                    )
                }
            }
        }
        if (layoutMode == LayoutMode.Flow) {
            item("app_list_flow_shape_preview") {
                val previewScale = itemFisheye(
                    listState,
                    "app_list_flow_shape_preview",
                    screenCenterY,
                    screenHeightPx,
                )
                AppListFlowShapePreview(
                    bulgePercent = appListFlowCrescentBulgePercent,
                    toRight = appListFlowCrescentToRight,
                    curve = activeLayoutProfile.flowShapeCurve,
                    modifier = Modifier.graphicsLayer {
                        scaleX = previewScale
                        scaleY = previewScale
                        alpha = previewScale.coerceIn(0.55f, 1f)
                    },
                )
            }
            flueSettingsGroup {
                row("app_list_flow_crescent_direction") { position ->
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_flow_crescent_direction),
                        subtitle = if (appListFlowCrescentToRight) {
                            stringResource(R.string.settings_flow_crescent_direction_right)
                        } else {
                            stringResource(R.string.settings_flow_crescent_direction_left)
                        },
                        checked = appListFlowCrescentToRight,
                        onToggle = vm::setAppListFlowCrescentToRight,
                        scale = itemFisheye(listState, "app_list_flow_crescent_direction", screenCenterY, screenHeightPx),
                        groupPosition = position,
                    )
                }
                row("app_list_flow_crescent_bulge") { position ->
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_flow_crescent_bulge),
                        supportingText = stringResource(R.string.settings_flow_crescent_bulge_sub),
                        value = appListFlowCrescentBulgePercent.toFloat(),
                        valueText = "${appListFlowCrescentBulgePercent}%",
                        localValueText = { "${it.roundToInt()}%" },
                        // 100% = 峰值正好贴边；100~150% 是老师要的「更深的弧」，
                        // 代价是上下两端那几行会被推出可视区左侧（滚上来就回来了）。
                        range = 0f..FLUE_LAYOUT_CRESCENT_BULGE_MAX.toFloat(),
                        // 5% 一格：0..150 共 30 段，中间刻度 29 个。
                        steps = 29,
                        onValueChange = { vm.setAppListFlowCrescentBulgePercent(it.roundToInt()) },
                        scale = itemFisheye(listState, "app_list_flow_crescent_bulge", screenCenterY, screenHeightPx),
                        groupPosition = position,
                    )
                }
                listOf(
                    "X1" to activeLayoutProfile.flowShapeCurve.x1,
                    "Y1" to activeLayoutProfile.flowShapeCurve.y1,
                    "X2" to activeLayoutProfile.flowShapeCurve.x2,
                    "Y2" to activeLayoutProfile.flowShapeCurve.y2,
                ).forEach { (parameter, value) ->
                    row("app_list_flow_curve_$parameter") { position ->
                        SettingsSliderRow(
                            title = stringResource(R.string.settings_flow_curve_parameter_fmt, parameter),
                            value = value,
                            valueText = "%.2f".format(Locale.US, value),
                            localValueText = { "%.2f".format(Locale.US, it) },
                            range = 0f..1f,
                            steps = 19,
                            onValueChange = { next ->
                                val current = activeLayoutProfile.flowShapeCurve
                                vm.setAppListFlowShapeCurve(
                                    when (parameter) {
                                        "X1" -> current.copy(x1 = next)
                                        "Y1" -> current.copy(y1 = next)
                                        "X2" -> current.copy(x2 = next)
                                        else -> current.copy(y2 = next)
                                    },
                                )
                            },
                            scale = itemFisheye(
                                listState,
                                "app_list_flow_curve_$parameter",
                                screenCenterY,
                                screenHeightPx,
                            ),
                            groupPosition = position,
                        )
                    }
                }
            }
        }
        // 布局档案：鱼眼那几项是「当前档案」的内容，所以档案管理紧跟在它们后面，
        // 而不是另开一页 —— 换档案与调参数分两页的话，改完这边看不见那边变化。
        item("layout_profile_header") {
            SectionTitle(
                stringResource(R.string.settings_layout_profile_header),
                itemFisheye(listState, "layout_profile_header", screenCenterY, screenHeightPx),
            )
        }
        items(
            items = layoutProfiles.filter { it.layoutMode == layoutMode },
            key = { "layout_profile_${it.id}" },
            contentType = { "layout_profile" },
        ) { profile ->
            SettingsChoiceRow(
                title = profile.name,
                subtitle = if (FlueLayoutProfiles.isBuiltIn(profile.id)) {
                    stringResource(R.string.settings_layout_profile_preset)
                } else {
                    stringResource(R.string.settings_layout_profile_custom)
                },
                selected = profile.id == activeLayoutProfile.id,
                onClick = { vm.setActiveLayoutProfile(profile.id) },
                scale = itemFisheye(
                    listState,
                    "layout_profile_${profile.id}",
                    screenCenterY,
                    screenHeightPx,
                ),
            )
        }
        flueSettingsGroup {
            row("layout_profile_copy") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_layout_profile_copy),
                    subtitle = stringResource(R.string.settings_layout_profile_copy_sub),
                    onClick = { vm.copyLayoutProfile() },
                    scale = itemFisheye(listState, "layout_profile_copy", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("layout_profile_rename") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_profile_rename),
                    // 内置档不给改名：它们的名字是「哪种布局的默认档」这一层含义，
                    // 改掉之后档案列表就读不出来了。副本随便改。
                    subtitle = if (FlueLayoutProfiles.isBuiltIn(activeLayoutProfile.id)) {
                        stringResource(R.string.settings_profile_rename_builtin_sub)
                    } else {
                        activeLayoutProfile.name
                    },
                    onClick = {
                        if (!FlueLayoutProfiles.isBuiltIn(activeLayoutProfile.id)) {
                            onRequestRenameProfile()
                        }
                    },
                    scale = itemFisheye(listState, "layout_profile_rename", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("layout_profile_reset") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_layout_profile_reset),
                    subtitle = stringResource(R.string.settings_layout_profile_reset_sub),
                    onClick = { vm.resetActiveLayoutProfile() },
                    scale = itemFisheye(listState, "layout_profile_reset", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            // 删除也是「布局方案管理」这一类，要和复制/重命名/重置同卡。
            //
            // 以前它是组外一个独立 item，因为带条件（内置方案不能删）。但 row 只是个
            // 普通收集器、不是 composable，条件直接写在这里就行 —— 组的首尾位置是按
            // 实际收进来的行数算的，少一行不会错位。摆在组外的后果是把一张卡劈成两半，
            // 中间夹一个孤零零的胶囊（老师报的「布局那里 miuix 还是分开的」）。
            if (!FlueLayoutProfiles.isBuiltIn(activeLayoutProfile.id)) {
                row("layout_profile_delete") { position ->
                    ActionCard(
                        title = stringResource(R.string.settings_layout_profile_delete),
                        subtitle = stringResource(R.string.settings_layout_profile_delete_sub),
                        onClick = { vm.deleteLayoutProfile(activeLayoutProfile.id) },
                        scale = itemFisheye(listState, "layout_profile_delete", screenCenterY, screenHeightPx),
                        groupPosition = position,
                    )
                }
            }
        }
        flueSettingsGroup {
            row("layout_profile_export") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_layout_profile_export),
                    subtitle = stringResource(R.string.settings_layout_profile_export_sub),
                    onClick = { onExportProfiles() },
                    scale = itemFisheye(listState, "layout_profile_export", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("layout_profile_import") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_layout_profile_import),
                    subtitle = stringResource(R.string.settings_layout_profile_import_sub),
                    onClick = {
                        onImportProfiles()
                    },
                    scale = itemFisheye(listState, "layout_profile_import", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("rotary_haptics") { position ->
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_rotary_haptics),
                    subtitle = if (rotaryHapticsEnabled) {
                        stringResource(R.string.settings_rotary_haptics_on)
                    } else {
                        stringResource(R.string.settings_rotary_haptics_off)
                    },
                    checked = rotaryHapticsEnabled,
                    onToggle = vm::setRotaryHapticsEnabled,
                    scale = itemFisheye(listState, "rotary_haptics", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
        }
    }
}
