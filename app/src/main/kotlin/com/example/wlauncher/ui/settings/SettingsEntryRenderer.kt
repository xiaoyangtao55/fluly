package com.flue.launcher.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.flue.launcher.ui.settings.ActionCard
import com.flue.launcher.R
import com.flue.launcher.ui.settings.SettingsCategoryCard
import com.flue.launcher.ui.settings.SettingsChoiceRow
import com.flue.launcher.ui.settings.SettingsSliderRow
import com.flue.launcher.ui.settings.SettingsSwitchRow
import com.flue.launcher.ui.settings.miuix.SettingsIcon
import com.flue.launcher.ui.settings.SectionTitle
import com.flue.launcher.ui.style.flueSettingsGroup
import java.util.Locale
import top.yukonga.miuix.kmp.icon.MiuixIcons as OfficialMiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder as OfficialFolder
import top.yukonga.miuix.kmp.icon.extended.Ok as OfficialOk
import top.yukonga.miuix.kmp.icon.extended.Refresh as OfficialRefresh
import top.yukonga.miuix.kmp.icon.extended.Settings as OfficialSettings
import kotlin.math.roundToInt

/**
 * 设置项的数据驱动渲染层（重构样板）。
 *
 * ## 要解决的问题
 *
 * `SettingsActivity` 里 224 个设置项是逐个手写的 `item("key") { 控件(...) }`，
 * 一个开关 15–25 行，加一项要同时改 `strings.xml`（三语）、`SettingsHierarchy`
 * 和渲染处 —— 三处里有一处永远不会报错。
 *
 * 这里把「一个 [SettingsEntry] → 一组控件」的映射收敛到一处：调用点只声明数据，
 * 不再重复控件的样板参数。
 *
 * ## 为什么仍然走 flueSettingsGroup
 *
 * 成组卡片、组内分隔线、行距归属、鱼眼 per-key 缩放、滚动位置恢复，全都挂在
 * `flueSettingsGroup` 和行 key 上。渲染层只负责「这一行长什么样」，不碰布局语义，
 * 所以迁移前后滚动位置和动画身份不变（key 必须与手写时完全一致）。
 *
 * ## 当前覆盖
 *
 * 已接管 APPEARANCE（整屏 32 个 item）、FEATURE_TOGGLES、SIDE_SCREEN、
 * NOTIFICATION_SETTINGS 与 GESTURE_SETTINGS。
 * 渲染层现在支持全部条目类型：分节标题、Picker、Toggle、Slider、Navigation、Action，
 * 以及「动态副标题 / 条件显示 / 联动禁用」三种页面级状态钩子。
 */

/**
 * 渲染一条设置项时，向调用方索要「当前值」和「动作」的接口。
 *
 * 故意不传整个 ViewModel：设置项只关心自己那个值和一个 setter，把 vm 交进来会让
 * 渲染层重新缠上状态作用域 —— 那正是这 4585 行拆不动的原因。
 *
 * 方法都给了默认实现（空值 / 空动作）。理由：一屏通常只出现两三种条目类型，
 * 比如「功能开关」屏只有 Navigation 和 Toggle，若强制实现 Slider 的回调，
 * 每迁一屏就要写四个永远不会被调用的空方法。渲染器只会调用 `entries` 里
 * 实际存在的类型对应的方法，未实现的那几个不会被触发。
 */
internal interface SettingsEntryBinding {

    /** Picker 当前选中的 option id；无选中返回 null。 */
    fun selectedOption(entry: SettingsEntry.Picker): String? = null

    fun isChecked(entry: SettingsEntry.Toggle): Boolean = false

    fun sliderValue(entry: SettingsEntry.Slider): Float = 0f

    fun onPick(entry: SettingsEntry.Picker, optionId: String) {}

    fun onToggle(entry: SettingsEntry.Toggle, checked: Boolean) {}

    fun onSliderChange(entry: SettingsEntry.Slider, value: Float) {}

    /** Navigation / Action 的点击。 */
    fun onOpen(entry: SettingsEntry) {}

    /**
     * 动态副标题；返回非 null 时覆盖条目上声明的静态文案。
     *
     * 有些副标题是运行时状态的回显（「当前：蜂窝」「圆角 18 dp」），
     * 写进数据会变成过期的常量，只能由页面在重组时算。
     *
     * 标 `@Composable` 是因为这类文案常常要走 `stringResource` / 主题取值 ——
     * 实现方只能在组合作用域里被调用，而渲染层调用它时正好就在行内容里。
     */
    @Composable
    fun subtitle(entry: SettingsEntry): String? = null

    /**
     * 是否渲染这一条。
     *
     * 条件项（圆屏没有遮罩圆角、启动延时只在启动图标开启时出现）用它表达。
     * 放到 binding 而不是数据里，是因为判断依据是页面上下文（屏形、别的开关）。
     */
    fun isVisible(entry: SettingsEntry): Boolean = true

    /** 是否可交互。滑块依赖别的开关时置灰，但行仍然显示。 */
    fun isEnabled(entry: SettingsEntry): Boolean = true

    /**
     * 按当前状态覆盖行首图标。
     *
     * 授权类条目就是典型：授予后齿轮变对勾，用户扫一眼就知道哪一项还没配好。
     * 这种图标属于「运行时状态」，写进数据只会固化成一个过期值。
     */
    fun icon(entry: SettingsEntry): EntryIcon? = null
}

/**
 * 渲染一整屏声明式设置项：分节标题 + 按合并规则切出的成组卡片。
 *
 * 手写版里「哪些行并成一张卡」由调用点决定 —— 并卡的是 `flueSettingsGroup { row {} }`，
 * 独立的是裸 `item {}`。数据里用两条规则还原这个边界：
 *
 * 1. [SettingsEntry.Section] 一定断组，并且自己占一行标题；
 * 2. `mergeable == false`，以及 Navigation / Action / Picker 各自独占一张卡
 *    （前两者在手写版就是单张 `SettingsCategoryCard`，Picker 本身就是「一组选项」）。
 *
 * 剩下的 Toggle / Slider 连续出现就并成一张卡 —— 与手写版的成组范围逐段对应。
 */
internal fun LazyListScope.settingsEntryScreen(
    entries: List<SettingsEntry>,
    binding: SettingsEntryBinding,
    scaleOf: @Composable (String) -> Float,
) {
    val visible = entries.filter { binding.isVisible(it) }
    // 按 [CardPlacement] 累积当前卡；flush 一次就是一张卡。
    val card = mutableListOf<SettingsEntry>()

    fun flush() {
        if (card.isEmpty()) return
        settingsEntryGroup(card.toList(), binding, scaleOf)
        card.clear()
    }

    visible.forEach { entry ->
        when {
            entry is SettingsEntry.Section -> {
                flush()
                item(entry.id) { SectionTitle(entryTitle(entry), scaleOf(entry.id)) }
            }

            // 独立选项的单选：每个选项一张卡，与手写版的裸 item 一致。
            entry is SettingsEntry.Picker && entry.independentOptions -> {
                flush()
                entry.options.forEach { option ->
                    settingsEntryOptionCard(entry, option, binding, scaleOf)
                }
            }

            entry.placement == CardPlacement.ALONE -> {
                flush()
                card.add(entry)
                flush()
            }

            entry.placement == CardPlacement.START -> {
                flush()
                card.add(entry)
            }

            entry.placement == CardPlacement.INLINE -> card.add(entry)

            // AUTO：开关/滑块接在前一张卡后，动作/导航/单选各自独占。
            entry.joinsPreviousCard() -> card.add(entry)

            else -> {
                flush()
                card.add(entry)
                flush()
            }
        }
    }
    flush()
}

/** 单个选项独占一张卡（用于 [SettingsEntry.Picker.independentOptions]）。 */
private fun LazyListScope.settingsEntryOptionCard(
    entry: SettingsEntry.Picker,
    option: PickerOption,
    binding: SettingsEntryBinding,
    scaleOf: @Composable (String) -> Float,
) {
    val key = optionKey(entry, option)
    flueSettingsGroup {
        row(key) { position ->
            SettingsChoiceRow(
                title = optionLabel(option),
                subtitle = optionSubtitle(entry, option),
                selected = binding.selectedOption(entry) == option.id,
                onClick = { binding.onPick(entry, option.id) },
                scale = scaleOf(key),
                enabled = binding.isEnabled(entry),
                groupPosition = position,
            )
        }
    }
}

/** 这一条能否接在上一条后面，合并成同一张卡。 */
private fun SettingsEntry.joinsPreviousCard(): Boolean =
    this !is SettingsEntry.Section &&
        mergeable &&
        this !is SettingsEntry.Navigation &&
        this !is SettingsEntry.Action &&
        this !is SettingsEntry.Picker

/**
 * 把一组 [SettingsEntry] 渲染成成组卡片。
 *
 * - 组内每行仍然是独立 `item`，按原始行 key 参与鱼眼缩放与滚动恢复；
 * - [SettingsEntry.Picker] 展开成「每个 option 一行」，与手写版逐个
 *   `SettingsChoiceRow` 的结构完全一致；
 * - 间距、圆角、分隔线由 `flueSettingsGroup` 按位置算，这里不掺和。
 *
 * @param scaleOf 按行 key 取鱼眼缩放值。由调用方注入 `itemFisheye(...)`，
 *   因为那个函数需要 listState 与屏高，属于页面上下文而非渲染层。
 *   它本身是 `@Composable`（圆屏弦宽缩放要读 CompositionLocal），所以这里也标了
 *   `@Composable` —— 只能在 row 的 content lambda 里调用。
 */
internal fun LazyListScope.settingsEntryGroup(
    entries: List<SettingsEntry>,
    binding: SettingsEntryBinding,
    scaleOf: @Composable (String) -> Float,
) {
    flueSettingsGroup {
        entries.forEach { entry ->
            when (entry) {
                is SettingsEntry.Picker -> entry.options.forEach { option ->
                    // key 与手写版一致：布局组是 "layout_honeycomb" 这种，
                    // 不是 "layout_honeycomb_xxx"。改 key 会丢滚动位置。
                    val key = optionKey(entry, option)
                    row(key) { position ->
                        SettingsChoiceRow(
                            title = optionLabel(option),
                            subtitle = optionSubtitle(entry, option),
                            selected = binding.selectedOption(entry) == option.id,
                            onClick = { binding.onPick(entry, option.id) },
                            scale = scaleOf(key),
                            enabled = binding.isEnabled(entry),
                            groupPosition = position,
                        )
                    }
                }

                is SettingsEntry.Toggle -> row(entry.id) { position ->
                    val checked = binding.isChecked(entry)
                    SettingsSwitchRow(
                        title = entryTitle(entry),
                        subtitle = resolvedSubtitle(entry, binding),
                        checked = checked,
                        enabled = binding.isEnabled(entry),
                        onToggle = { binding.onToggle(entry, it) },
                        scale = scaleOf(entry.id),
                        groupPosition = position,
                    )
                }

                is SettingsEntry.Slider -> row(entry.id) { position ->
                    val value = binding.sliderValue(entry)
                    SettingsSliderRow(
                        title = entryTitle(entry),
                        value = value,
                        valueText = entry.valueText(value),
                        localValueText = { entry.valueText(it) },
                        range = entry.min..entry.max,
                        steps = entry.steps,
                        onValueChange = { binding.onSliderChange(entry, it) },
                        enabled = binding.isEnabled(entry),
                        scale = scaleOf(entry.id),
                        groupPosition = position,
                    )
                }

                // 两种卡片的分野在**语义**而不是图标：
                // - Navigation 是「跳一页」，用手写版一贯的 SettingsCategoryCard；
                // - Action 是「就地做一件事」，用 ActionCard，图标可空。
                // 早先按「有没有图标」二选一，结果 TOOLS 屏的两个无图标动作
                // （导出日志、恢复默认）被判成了导航样式，与手写版不一致。
                is SettingsEntry.Navigation -> row(entry.id) { position ->
                    SettingsCategoryCard(
                        title = entryTitle(entry),
                        subtitle = resolvedSubtitle(entry, binding),
                        onClick = { binding.onOpen(entry) },
                        scale = scaleOf(entry.id),
                        groupPosition = position,
                    )
                }

                is SettingsEntry.Action -> row(entry.id) { position ->
                    val icon = binding.icon(entry) ?: entry.icon
                    ActionCard(
                        title = entryTitle(entry),
                        subtitle = resolvedSubtitle(entry, binding),
                        scale = scaleOf(entry.id),
                        groupPosition = position,
                        icon = icon?.let { resolved ->
                            {
                                SettingsIcon(
                                    material = resolved.vector(),
                                    miuix = resolved.vector(),
                                )
                            }
                        },
                        onClick = { binding.onOpen(entry) },
                    )
                }

                // 分节标题由 settingsEntryScreen 处理，不会进到组里。
                is SettingsEntry.Section -> Unit
            }
        }
    }
}

/**
 * Picker 每个选项的行 key。
 *
 * 手写版的 key 是 `layout_honeycomb`，而 [SettingsEntry.Picker] 的 id 是 `layout`。
 * 这里约定 `id_optionId`，并要求条目 id 与 option id 拼出来等于历史 key ——
 * 全量迁移时逐组核对，否则鱼眼缩放会按错的 key 取值（表现为某几行不缩放）。
 */
private fun optionKey(entry: SettingsEntry.Picker, option: PickerOption): String =
    "${entry.id}_${option.id}"

@Composable
private fun entryTitle(entry: SettingsEntry): String =
    entry.titleRes?.let { stringResource(it) } ?: entry.title

@Composable
private fun entrySubtitle(entry: SettingsEntry): String? =
    entry.subtitleRes?.let { stringResource(it) } ?: entry.subtitle

@Composable
private fun optionLabel(option: PickerOption): String =
    option.labelRes?.let { stringResource(it) } ?: option.label

@Composable
private fun optionSubtitle(entry: SettingsEntry.Picker, option: PickerOption): String =
    option.subtitleRes?.let { stringResource(it) } ?: entrySubtitle(entry).orEmpty()

/**
 * 开关的副标题：优先按当前状态选 [SettingsEntry.Toggle.subtitleOnRes] /
 * [SettingsEntry.Toggle.subtitleOffRes]，都没配才回落到静态 subtitle。
 */
@Composable
private fun toggleSubtitle(entry: SettingsEntry.Toggle, checked: Boolean): String {
    val stateRes = if (checked) entry.subtitleOnRes else entry.subtitleOffRes
    return stateRes?.let { stringResource(it) } ?: entrySubtitle(entry).orEmpty()
}

/**
 * 一行的副标题，优先级：页面给的动态文案 → 开关的状态文案 → 静态资源。
 */
@Composable
private fun resolvedSubtitle(entry: SettingsEntry, binding: SettingsEntryBinding): String {
    binding.subtitle(entry)?.let { return it }
    return when (entry) {
        is SettingsEntry.Toggle -> toggleSubtitle(entry, binding.isChecked(entry))
        else -> entrySubtitle(entry).orEmpty()
    }
}

/** 数据层的语义图标 → miuix 图标。 */
private fun EntryIcon.vector(): ImageVector = when (this) {
    EntryIcon.SETTINGS -> OfficialMiuixIcons.OfficialSettings
    EntryIcon.REFRESH -> OfficialMiuixIcons.OfficialRefresh
    EntryIcon.OK -> OfficialMiuixIcons.OfficialOk
    EntryIcon.FOLDER -> OfficialMiuixIcons.OfficialFolder
}

/** 滑块右侧的数值文案，例如 `300 ms` / `2.5 dp`。 */
private fun SettingsEntry.Slider.valueText(value: Float): String {
    val fixed = decimals
    val body = when {
        // 固定小数位：手写版对「边缘模糊半径」用的是 %.1f，3 也要显示 3.0。
        fixed != null -> String.format(Locale.US, "%.${fixed}f", value)
        value % 1f == 0f -> value.roundToInt().toString()
        else -> String.format(Locale.US, "%.1f", value)
    }
    return unit?.let { "$body $it" } ?: body
}

// ---------------------------------------------------------------------------
// 迁移进度
//
// 已接入数据驱动渲染的屏记在 SettingsHierarchy.renderedGroups 里：
// - APPEARANCE           —— 9 个分节、5 张独立卡片、3 组单选、5 条滑块、2 个条件项
// - FEATURE_TOGGLES      —— 4 张分类卡片 + 1 个状态开关
// - SIDE_SCREEN          —— 7 个级联开关（下游依赖上游，置灰但可见）
// - NOTIFICATION_SETTINGS —— 2 个开关，依赖副一屏
// - GESTURE_SETTINGS     —— 2 个开关，其中一个副标题有四个状态分支
//
// 未迁移的屏仍由 SettingsActivity 手写渲染；它们的 SettingsHierarchy 条目与代码
// 存在漂移，renderedGroup() 会拒绝这类 id，避免画出不存在的设置项。
// ---------------------------------------------------------------------------

