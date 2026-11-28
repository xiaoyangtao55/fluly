package com.fluly.launcher.ui.settings

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fluly.launcher.ui.settings.ActionCard
import com.fluly.launcher.ui.settings.MessageCard
import com.fluly.launcher.R
import com.fluly.launcher.ui.settings.SectionTitle
import com.fluly.launcher.ui.settings.SettingsChoiceRow
import com.fluly.launcher.ui.settings.SettingsPageScaffold
import com.fluly.launcher.ui.settings.SettingsSliderRow
import com.fluly.launcher.ui.settings.SettingsSwitchRow
import com.fluly.launcher.ui.settings.itemFisheye
import com.fluly.launcher.motion.FlulyMotionProfile
import com.fluly.launcher.motion.FlulyMotionProfiles
import com.fluly.launcher.motion.MotionEasing
import com.fluly.launcher.motion.MotionPhaseKey
import com.fluly.launcher.ui.style.flulySettingsGroup
import com.fluly.launcher.ui.theme.WatchColors
import com.fluly.launcher.viewmodel.LauncherViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 动画档位屏（`SettingsDestination.MOTION_PROFILE`）。
 *
 * ## 为什么整页搬出来而不是数据化
 *
 * 这一屏的条目数量本身是**运行时算出来的**：档位列表来自用户自建 profile 的数量，
 * 后面每个阶段的四条滑块取决于该阶段当前用的是贝塞尔还是弹簧（贝塞尔是 x1/y1/x2/y2，
 * 弹簧是阻尼/刚度）。再加上首行的说明卡、每个阶段的分节标题——
 * 数据层要表达这些就得引入「按集合生成条目」「条目内部再分支」两层动态结构，
 * 那已经不是声明式描述了，而是在数据里写代码。
 *
 * 所以整页手写，只是搬出 `SettingsActivity`，让它只负责路由与状态持有。
 *
 * ## 状态归属
 *
 * - 档位列表、当前档位由 `vm` 持有，页面只读；
 * - 两个文件选择器（导出/导入）与重命名弹窗属于 Activity 级状态，
 *   留在根作用域，用回调传进来；
 * - 拖动中的滑块值不需要跨页面，仍是控件内部的事。
 */
@Composable
internal fun MotionProfilePage(
    onBack: () -> Unit,
    headerTime: String,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollChanged: (Int, Int) -> Unit,
    vm: LauncherViewModel,
    activeMotionProfile: FlulyMotionProfile,
    motionProfiles: List<FlulyMotionProfile>,
    onRenameRequest: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val motionProfileEditable = !FlulyMotionProfiles.isBuiltIn(activeMotionProfile.id)

    SettingsPageScaffold(
        title = stringResource(R.string.settings_motion_profile_title),
        onBack = onBack,
        headerTime = headerTime,
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
        onScrollChanged = onScrollChanged,
    ) { listState, screenCenterY, screenHeightPx, _ ->
        item("motion_intro") {
            MessageCard(
                text = stringResource(R.string.settings_motion_profile_hint),
                background = WatchColors.SurfaceGlass,
                onClick = {},
            )
        }
        items(
            items = activeMotionProfileOptions(motionProfiles),
            key = { "motion_profile_${it.id}" },
            contentType = { "motion_profile" },
        ) { profile ->
            SettingsChoiceRow(
                title = profile.name,
                subtitle = if (FlulyMotionProfiles.isBuiltIn(profile.id)) {
                    stringResource(R.string.settings_motion_profile_preset)
                } else {
                    stringResource(R.string.settings_motion_profile_custom)
                },
                selected = profile.id == activeMotionProfile.id,
                onClick = { vm.setActiveMotionProfile(profile.id) },
                scale = itemFisheye(listState, "motion_profile_${profile.id}", screenCenterY, screenHeightPx)
            )
        }
        flulySettingsGroup {
            row("motion_copy") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_motion_profile_copy),
                    subtitle = stringResource(R.string.settings_motion_profile_copy_sub),
                    onClick = { vm.copyMotionProfile() },
                    scale = itemFisheye(listState, "motion_copy", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("motion_profile_rename") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_profile_rename),
                    subtitle = if (FlulyMotionProfiles.isBuiltIn(activeMotionProfile.id)) {
                        stringResource(R.string.settings_profile_rename_builtin_sub)
                    } else {
                        activeMotionProfile.name
                    },
                    onClick = {
                        // 内置档位不可改名，点击只是无反应（行仍然显示，副标题已说明原因）。
                        if (!FlulyMotionProfiles.isBuiltIn(activeMotionProfile.id)) {
                            onRenameRequest()
                        }
                    },
                    scale = itemFisheye(listState, "motion_profile_rename", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("motion_delete") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_motion_profile_delete),
                    subtitle = stringResource(R.string.settings_motion_profile_delete_sub),
                    onClick = { vm.deleteMotionProfile(activeMotionProfile.id) },
                    scale = itemFisheye(listState, "motion_delete", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("motion_bounce") { position ->
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_motion_bounce),
                    subtitle = stringResource(R.string.settings_motion_bounce_sub),
                    checked = activeMotionProfile.bounceEnabled,
                    enabled = motionProfileEditable,
                    onToggle = { vm.setMotionBounceEnabled(it) },
                    scale = itemFisheye(listState, "motion_bounce", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("motion_edge_strength") { position ->
                SettingsSliderRow(
                    title = stringResource(R.string.settings_motion_bounce_strength),
                    value = activeMotionProfile.edgeStrength.toFloat(),
                    valueText = "${activeMotionProfile.edgeStrength}/10",
                    localValueText = { "${it.roundToInt()}/10" },
                    range = 0f..10f,
                    steps = 9,
                    enabled = motionProfileEditable,
                    onValueChange = { vm.setMotionEdgeStrength(it.roundToInt()) },
                    scale = itemFisheye(listState, "motion_edge_strength", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
        }
        MotionPhaseSection(
            context = context,
            vm = vm,
            activeMotionProfile = activeMotionProfile,
            editable = motionProfileEditable,
            listState = listState,
            screenCenterY = screenCenterY,
            screenHeightPx = screenHeightPx,
        )
        flulySettingsGroup {
            row("motion_export") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_motion_export_curves),
                    subtitle = stringResource(R.string.settings_motion_export_curves_sub),
                    onClick = onExport,
                    scale = itemFisheye(listState, "motion_export", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("motion_import") { position ->
                ActionCard(
                    title = stringResource(R.string.settings_motion_import_curves),
                    subtitle = stringResource(R.string.settings_motion_import_curves_sub),
                    onClick = onImport,
                    scale = itemFisheye(listState, "motion_import", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
        }
    }
}

/**
 * 每个阶段（进场、启动、返回、下拉、边缘回弹）的四段内容：
 * 分节标题 + 时长/衔接两张滑块 + 两种缓动各一张单选卡 + 该缓动的参数。
 *
 * 单独抽成一个 [LazyListScope] 扩展是为了让上面的页面主体保持可读 ——
 * 这些条目都是 `item {}`/`row {}`，抽成 @Composable 函数反而要额外传一整套上下文。
 */
private fun LazyListScope.MotionPhaseSection(
    context: Context,
    vm: LauncherViewModel,
    activeMotionProfile: FlulyMotionProfile,
    editable: Boolean,
    listState: LazyListState,
    screenCenterY: Float,
    screenHeightPx: Float,
) {
    MotionPhaseKey.entries.forEach { key ->
        val phase = activeMotionProfile.phase(key)
        val keyName = key.motionPhaseLabel(context)
        item("motion_section_$key") {
            SectionTitle(
                text = "$keyName · ${phase.easing.motionEasingSummary(context)}",
                scale = itemFisheye(listState, "motion_section_$key", screenCenterY, screenHeightPx),
            )
        }
        flulySettingsGroup {
            row("motion_duration_$key") { position ->
                SettingsSliderRow(
                    title = context.getString(R.string.settings_motion_phase_duration_fmt, keyName),
                    value = phase.durationMillis.toFloat(),
                    valueText = "${phase.durationMillis} ms",
                    localValueText = { "${it.roundToInt()} ms" },
                    range = 80f..1200f,
                    steps = 55,
                    enabled = editable,
                    onValueChangeFinished = { vm.setMotionPhaseDuration(key, it.roundToInt()) },
                    onValueChange = { },
                    scale = itemFisheye(listState, "motion_duration_$key", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("motion_handoff_$key") { position ->
                SettingsSliderRow(
                    title = context.getString(R.string.settings_motion_phase_delay_fmt, keyName),
                    value = phase.handoffMillis.toFloat(),
                    valueText = "${phase.handoffMillis} ms",
                    localValueText = { "${it.roundToInt()} ms" },
                    range = 0f..500f,
                    steps = 49,
                    enabled = editable,
                    onValueChangeFinished = { vm.setMotionPhaseHandoff(key, it.roundToInt()) },
                    onValueChange = { },
                    scale = itemFisheye(listState, "motion_handoff_$key", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
        }
        item("motion_easing_cubic_$key") {
            SettingsChoiceRow(
                title = context.getString(R.string.settings_motion_bezier),
                subtitle = context.getString(R.string.settings_motion_bezier_sub),
                selected = phase.easing is MotionEasing.CubicBezier,
                enabled = editable,
                onClick = {
                    vm.setMotionPhaseEasing(
                        key,
                        MotionEasing.CubicBezier(0.22f, 1f, 0.36f, 1f),
                    )
                },
                scale = itemFisheye(listState, "motion_easing_cubic_$key", screenCenterY, screenHeightPx)
            )
        }
        item("motion_easing_spring_$key") {
            SettingsChoiceRow(
                title = context.getString(R.string.settings_motion_spring),
                subtitle = context.getString(R.string.settings_motion_spring_sub),
                selected = phase.easing is MotionEasing.Spring,
                enabled = editable,
                onClick = {
                    vm.setMotionPhaseEasing(
                        key,
                        MotionEasing.Spring(dampingRatio = 0.78f, stiffness = 420f),
                    )
                },
                scale = itemFisheye(listState, "motion_easing_spring_$key", screenCenterY, screenHeightPx)
            )
        }
        when (val easing = phase.easing) {
            is MotionEasing.CubicBezier -> {
                listOf(
                    "x1" to easing.x1,
                    "y1" to easing.y1,
                    "x2" to easing.x2,
                    "y2" to easing.y2,
                ).forEach { (parameter, parameterValue) ->
                    val isX = parameter.startsWith("x")
                    item("motion_${key}_$parameter") {
                        SettingsSliderRow(
                            title = "$keyName $parameter",
                            value = parameterValue,
                            valueText = "%.2f".format(Locale.US, parameterValue),
                            localValueText = { "%.2f".format(Locale.US, it) },
                            range = if (isX) 0f..1f else -2f..2f,
                            steps = if (isX) 19 else 39,
                            enabled = editable,
                            onValueChange = { },
                            onValueChangeFinished = { nextValue ->
                                val next = when (parameter) {
                                    "x1" -> easing.copy(x1 = nextValue)
                                    "y1" -> easing.copy(y1 = nextValue)
                                    "x2" -> easing.copy(x2 = nextValue)
                                    else -> easing.copy(y2 = nextValue)
                                }
                                vm.setMotionPhaseEasing(key, next)
                            },
                            scale = itemFisheye(
                                listState,
                                "motion_${key}_$parameter",
                                screenCenterY,
                                screenHeightPx,
                            )
                        )
                    }
                }
            }

            is MotionEasing.Spring -> {
                flulySettingsGroup {
                    row("motion_${key}_damping") { position ->
                        SettingsSliderRow(
                            title = context.getString(R.string.settings_motion_damping_fmt, keyName),
                            value = easing.dampingRatio,
                            valueText = "%.2f".format(Locale.US, easing.dampingRatio),
                            localValueText = { "%.2f".format(Locale.US, it) },
                            range = 0.05f..2f,
                            steps = 38,
                            enabled = editable,
                            onValueChange = { },
                            onValueChangeFinished = { nextValue ->
                                vm.setMotionPhaseEasing(
                                    key,
                                    easing.copy(dampingRatio = nextValue),
                                )
                            },
                            scale = itemFisheye(
                                listState,
                                "motion_${key}_damping",
                                screenCenterY,
                                screenHeightPx,
                            ),
                            groupPosition = position,
                        )
                    }
                    row("motion_${key}_stiffness") { position ->
                        SettingsSliderRow(
                            title = context.getString(R.string.settings_motion_stiffness_fmt, keyName),
                            value = easing.stiffness,
                            valueText = "${easing.stiffness.roundToInt()}",
                            localValueText = { "${it.roundToInt()}" },
                            range = 10f..3000f,
                            steps = 58,
                            enabled = editable,
                            onValueChange = { },
                            onValueChangeFinished = { nextValue ->
                                vm.setMotionPhaseEasing(
                                    key,
                                    easing.copy(stiffness = nextValue),
                                )
                            },
                            scale = itemFisheye(
                                listState,
                                "motion_${key}_stiffness",
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
}

private fun activeMotionProfileOptions(
    profiles: List<FlulyMotionProfile>,
): List<FlulyMotionProfile> = profiles.sortedWith(
    compareBy<FlulyMotionProfile> { if (FlulyMotionProfiles.isBuiltIn(it.id)) 0 else 1 }
        .thenBy { it.name },
)

private fun MotionPhaseKey.motionPhaseLabel(context: Context): String = when (this) {
    MotionPhaseKey.APP_LIST_ENTER -> context.getString(R.string.settings_motion_phase_app_list_enter)
    MotionPhaseKey.APP_LAUNCH -> context.getString(R.string.settings_motion_phase_app_launch)
    MotionPhaseKey.APP_RETURN -> context.getString(R.string.settings_motion_phase_app_return)
    MotionPhaseKey.PULL_TO_FACE -> context.getString(R.string.settings_motion_phase_pull_to_face)
    MotionPhaseKey.EDGE_BOUNCE -> context.getString(R.string.settings_motion_phase_edge_bounce)
}

private fun MotionEasing.motionEasingSummary(context: Context): String = when (this) {
    is MotionEasing.CubicBezier ->
        "Bezier(%.2f, %.2f, %.2f, %.2f)".format(Locale.US, x1, y1, x2, y2)

    is MotionEasing.Spring ->
        context.getString(R.string.settings_motion_spring_fmt)
            .format(Locale.US, dampingRatio, stiffness.roundToInt())
}
