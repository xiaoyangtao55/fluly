package com.fluly.launcher.ui.settings

import androidx.annotation.StringRes
import com.fluly.launcher.R

/**
 * 设置页信息架构的声明式模型。
 *
 * ## 建模单位是「屏」，不是「主题」
 *
 * 最初按主题分组（外观 / 表盘 / 应用列表 / 动画 / 系统），那是给搜索与信息架构用的
 * 视角。但真正被渲染的是一个个 `SettingsDestination` 页面 —— 一个主题组横跨三屏时
 * 拿它渲染任何一屏都会画错。所以现在**一个组对一屏**，组 id 与屏名对应：
 *
 * | 组 id | 屏 |
 * | --- | --- |
 * | `appearance` | `APPEARANCE` |
 * | `watch_face_more` | `WATCH_FACE_MORE` |
 * | `app_list_behavior` | `APP_LIST_BEHAVIOR` |
 * | `performance` / `performance_rendering` / `performance_motion` / `performance_system` | 同名四屏 |
 * | `side_screen` / `notifications` / `gestures` | 三个独立子页 |
 * | `feature_toggles` | `FEATURE_TOGGLES` |
 * | `tools` / `backup` / `backup_export` / `backup_import` | 同名四屏 |
 *
 * 约定：
 * - 条目 `id` 必须等于手写版的行 key（鱼眼缩放、滚动位置、搜索索引三处都用它做身份）；
 * - `prefKey` 是 DataStore key 名，与 ViewModel 里的常量一致；不落盘的填 null；
 * - 同一批条目可以出现在两个组里（`backupOptions` 服务导出/导入两屏），
 *   但**同一个 prefKey 不得对应两个不同 id** —— 那会画出两个互不同步的开关。
 *
 * 尚未建模、仍整屏手写的有：`WATCH_FACES`、`LANGUAGE`、`ABOUT`、
 * `ICON_PACKS`、`HIDDEN_APPS`、`DONATE`、`DEBUG`、`TAP_ACTION_*`，以及三个
 * 独立文件里的整页（`DISPLAY_SIZE` / `APP_LIST_BACKGROUND` / `MOTION_PROFILE`）。
 */
data class SettingsGroup(
    val id: String,
    val title: String,
    val items: List<SettingsEntry>
)

/** 条目现状：EXISTING 已有实现与 key；PLANNED 规划中（key 或页面尚未落地）。 */
enum class EntryStatus { EXISTING, PLANNED }

/**
 * 这一条与相邻行的成卡关系。
 *
 * 手写版里「哪些行并成一张卡」是逐段写死的：`flulySettingsGroup { row {} }` 并卡，
 * 裸 `item {}` 独立。这个枚举就是那层布局语义，缺了它就没法还原卡片边界 ——
 * 而边界一错，圆角、分隔线、行距全变，用户一眼就看出「设置页变味了」。
 */
enum class CardPlacement {
    /** 默认：开关/滑块接在前一张卡后，动作/导航/单选各自独立。 */
    AUTO,

    /** 独占一张卡，前后都断开。 */
    ALONE,

    /** 开始一张新卡，后续可并入的行（开关、滑块、INLINE）跟进来。 */
    START,

    /** 并入当前卡，即使自己本来会独占。 */
    INLINE,
}

/**
 * 行首图标。
 *
 * 数据层只声明语义（齿轮 / 刷新 / 勾 / 文件夹），具体 ImageVector 由渲染层映射。
 * 把 `ImageVector` 直接写进数据会绑死 miuix 依赖，也没法做「按状态换图标」。
 */
enum class EntryIcon { SETTINGS, REFRESH, OK, FOLDER }

/** 单选项（用于 Picker）。 */
data class PickerOption(
    val id: String,
    /** 回退文案。新条目只给 [labelRes]，这里留空即可。 */
    val label: String = "",
    /** 文案资源；非空时优先于 [label]。 */
    @StringRes val labelRes: Int? = null,
    /** 该选项自己的说明；为空时回落到所属 [SettingsEntry] 的 subtitle。 */
    @StringRes val subtitleRes: Int? = null
)

sealed interface SettingsEntry {
    val id: String

    /**
     * 回退标题。新条目只给 [titleRes] 即可，这里留空 ——
     * 中文写两遍（这里一份、strings.xml 一份）迟早会不一致，而渲染层只看 [titleRes]，
     * 不一致也不会报错，只会让这份数据看起来"像是对的"。
     */
    val title: String
    val subtitle: String?
    /** DataStore preferences key 名；导航/动作类条目为 null。 */
    val prefKey: String?
    val status: EntryStatus

    /**
     * 是否与相邻同类条目合并成一张卡。
     *
     * 默认合并（HyperOS 的同类行并卡）。置 false 表示这一条独占一张卡 ——
     * 手写版里确实有些项是裸 `item {}` 而非 `flulySettingsGroup { row {} }`，
     * 比如「启动延时」：它跟着开关出现，但和上面两个开关不是同一张卡。
     */
    val mergeable: Boolean get() = true

    /** 与相邻行的成卡关系；见 [CardPlacement]。 */
    val placement: CardPlacement get() = CardPlacement.AUTO

    /**
     * 标题文案资源；非空时优先于 [title]。
     *
     * 新条目只给资源 ID —— `AGENTS.md` 要求用户可见文案进 `strings.xml`（含三语），
     * 而早期条目把中文直接写在 [title] 里，两种写法在渲染层必须都能吃。
     */
    @get:StringRes val titleRes: Int? get() = null

    /** 说明文案资源；非空时优先于 [subtitle]。 */
    @get:StringRes val subtitleRes: Int? get() = null

    /** 布尔开关。 */
    data class Toggle(
        override val id: String,
        override val title: String = "",
        override val prefKey: String?,
        override val subtitle: String? = null,
        override val status: EntryStatus = EntryStatus.EXISTING,
        /** 依赖的前置开关（例如通知开关依赖副一屏开启）。 */
        val dependsOn: String? = null,
        @get:StringRes override val titleRes: Int? = null,
        @get:StringRes override val subtitleRes: Int? = null,
        /**
         * 开关打开 / 关闭时各自的说明文案，优先于 [subtitleRes]。
         *
         * 有些开关的副标题本身就是状态回显（例如「玻璃高光」打开时显示
         * 「已开启」、关闭时显示「已关闭」），这类文案写不进单一的 subtitle，
         * 又不该在渲染处写 `if (checked)` —— 状态相关的文案属于数据。
         */
        @get:StringRes val subtitleOnRes: Int? = null,
        @get:StringRes val subtitleOffRes: Int? = null,
        override val placement: CardPlacement = CardPlacement.AUTO
    ) : SettingsEntry

    /** 数值滑块。 */
    data class Slider(
        override val id: String,
        override val title: String = "",
        override val prefKey: String?,
        val min: Float,
        val max: Float,
        val steps: Int = 0,
        val unit: String? = null,
        override val subtitle: String? = null,
        override val status: EntryStatus = EntryStatus.EXISTING,
        val dependsOn: String? = null,
        @get:StringRes override val titleRes: Int? = null,
        @get:StringRes override val subtitleRes: Int? = null,
        /**
         * 固定小数位；null 表示「整数就不带小数、否则一位」。
         *
         * 手写版对小数位的处理并不统一：边缘模糊半径固定 `%.1f dp`（3 也要显示 3.0），
         * 顶部/底部渐隐固定整数，漏掉这个字段会让数值文案悄悄变样。
         */
        val decimals: Int? = null,
        override val mergeable: Boolean = true,
        override val placement: CardPlacement = CardPlacement.AUTO
    ) : SettingsEntry

    /** 多选一（枚举）选择器。 */
    data class Picker(
        override val id: String,
        override val title: String = "",
        override val prefKey: String?,
        val options: List<PickerOption>,
        override val subtitle: String? = null,
        override val status: EntryStatus = EntryStatus.EXISTING,
        @get:StringRes override val titleRes: Int? = null,
        @get:StringRes override val subtitleRes: Int? = null,
        /**
         * 每个选项独占一张卡（手写版是裸 `item`，而不是组内 `row`）。
         *
         * 「温度单位」「日期语言」两组都是各占一张卡：选项少、每组都是独立决策，
         * 并进一张大卡反而看不出是单选。
         */
        val independentOptions: Boolean = false,
        override val placement: CardPlacement = CardPlacement.AUTO
    ) : SettingsEntry

    /** 跳转到子页面 / 其他 Activity。 */
    data class Navigation(
        override val id: String,
        override val title: String = "",
        /** 目标：SettingsDestination 名或 Activity 类名。 */
        val destination: String,
        override val subtitle: String? = null,
        override val status: EntryStatus = EntryStatus.EXISTING,
        @get:StringRes override val titleRes: Int? = null,
        @get:StringRes override val subtitleRes: Int? = null,
        override val placement: CardPlacement = CardPlacement.AUTO
    ) : SettingsEntry {
        override val prefKey: String? get() = null
    }

    /**
     * 分节标题。
     *
     * 不占卡片、不参与成组，只占一行文字 —— 它同时也是「分组边界」：
     * 相邻两条被它隔开，就必然落在两张卡里。
     */
    data class Section(
        override val id: String,
        override val title: String = "",
        @get:StringRes override val titleRes: Int? = null
    ) : SettingsEntry {
        override val subtitle: String? get() = null
        override val prefKey: String? get() = null
        override val status: EntryStatus get() = EntryStatus.EXISTING
    }

    /** 一次性动作（导出、重置、授权跳转等）。 */
    data class Action(
        override val id: String,
        override val title: String = "",
        /** 动作标识，重构时映射到具体回调。 */
        val action: String,
        override val subtitle: String? = null,
        override val status: EntryStatus = EntryStatus.EXISTING,
        @get:StringRes override val titleRes: Int? = null,
        @get:StringRes override val subtitleRes: Int? = null,
        /** 行首图标；null 表示与 `SettingsCategoryCard` 一样不带图标。 */
        val icon: EntryIcon? = null,
        override val placement: CardPlacement = CardPlacement.AUTO
    ) : SettingsEntry {
        override val prefKey: String? get() = null
    }
}

object SettingsHierarchy {

    // ---------------------------------------------------------------- 外观
    // 这一组对应 APPEARANCE 整屏：9 个分节、5 张独立卡片、3 组单选、5 条滑块
    // （其中 1 条联动禁用）、2 个条件项。条目顺序与手写版逐项对齐，行 key 一字不改。
    //
    // 副标题留空的几项是「动态文案」——当前背景模式名、带 dp 数值的圆角说明，
    // 由渲染层的 binding 实时给出，数据里写死反而会显示成旧值。
    private val appearance = SettingsGroup(
        id = "appearance",
        title = "外观",
        items = listOf(
            SettingsEntry.Section(
                id = "layout_header",
                title = "布局",
                titleRes = R.string.settings_section_layout
            ),
            SettingsEntry.Picker(
                id = "layout",
                title = "布局",
                titleRes = R.string.settings_section_layout,
                prefKey = null,
                options = listOf(
                    PickerOption(
                        id = "honeycomb",
                        labelRes = R.string.settings_layout_honeycomb,
                        subtitleRes = R.string.settings_layout_honeycomb_sub
                    ),
                    PickerOption(
                        id = "list",
                        labelRes = R.string.settings_layout_list,
                        subtitleRes = R.string.settings_layout_list_sub
                    ),
                    PickerOption(
                        id = "grid",
                        labelRes = R.string.settings_layout_grid,
                        subtitleRes = R.string.settings_layout_grid_sub
                    ),
                    PickerOption(
                        id = "flow",
                        labelRes = R.string.settings_layout_flow,
                        subtitleRes = R.string.settings_layout_flow_sub
                    ),
                    PickerOption(
                        id = "ring",
                        labelRes = R.string.settings_layout_ring,
                        subtitleRes = R.string.settings_layout_ring_sub
                    )
                )
            ),
            SettingsEntry.Section(
                id = "display_header",
                title = "显示",
                titleRes = R.string.settings_display_header
            ),
            SettingsEntry.Navigation(
                id = "app_list_background_entry",
                title = "应用列表背景",
                titleRes = R.string.settings_app_list_background,
                destination = "APP_LIST_BACKGROUND"
            ),
            SettingsEntry.Navigation(
                id = "display_size_menu",
                title = "显示大小",
                titleRes = R.string.settings_display_fit,
                subtitleRes = R.string.settings_display_fit_sub,
                destination = "DISPLAY_SIZE"
            ),
            // 圆屏没有「遮罩圆角」这回事，这一条只在方屏出现；副标题带当前 dp 值。
            SettingsEntry.Action(
                id = "overlay_mask_corner_radius",
                title = "遮罩圆角",
                titleRes = R.string.settings_overlay_mask_corner_radius,
                action = "show_overlay_mask_corner_dialog"
            ),
            SettingsEntry.Navigation(
                id = "feature_toggles_menu",
                title = "功能开关",
                titleRes = R.string.settings_feature_toggles,
                subtitleRes = R.string.settings_feature_toggles_sub,
                destination = "FEATURE_TOGGLES"
            ),
            SettingsEntry.Picker(
                id = "theme_mode",
                title = "深色模式",
                prefKey = "theme_mode",
                options = listOf(
                    PickerOption(
                        id = "system",
                        labelRes = R.string.settings_theme_system,
                        subtitleRes = R.string.settings_theme_system_sub
                    ),
                    PickerOption(
                        id = "dark",
                        labelRes = R.string.settings_theme_dark,
                        subtitleRes = R.string.settings_theme_dark_sub
                    ),
                    PickerOption(
                        id = "light",
                        labelRes = R.string.settings_theme_light,
                        subtitleRes = R.string.settings_theme_light_sub
                    )
                )
            ),
            SettingsEntry.Section(
                id = "transition_motion_header",
                title = "界面动画",
                titleRes = R.string.settings_transition_motion_header
            ),
            SettingsEntry.Picker(
                id = "transition_motion",
                title = "界面动画",
                prefKey = null,
                options = listOf(
                    PickerOption(
                        id = "scale",
                        labelRes = R.string.settings_transition_motion_scale,
                        subtitleRes = R.string.settings_transition_motion_scale_sub
                    ),
                    PickerOption(
                        id = "slide",
                        labelRes = R.string.settings_transition_motion_slide,
                        subtitleRes = R.string.settings_transition_motion_slide_sub
                    )
                )
            ),
            SettingsEntry.Section(
                id = "screen_shape_header",
                title = "屏幕形状",
                titleRes = R.string.settings_screen_shape_header
            ),
            SettingsEntry.Picker(
                id = "screen_shape",
                title = "屏幕形状",
                prefKey = null,
                options = listOf(
                    PickerOption(
                        id = "auto",
                        labelRes = R.string.settings_screen_shape_auto,
                        subtitleRes = R.string.settings_screen_shape_auto_sub
                    ),
                    PickerOption(
                        id = "round",
                        labelRes = R.string.settings_screen_shape_round,
                        subtitleRes = R.string.settings_screen_shape_override_sub
                    ),
                    PickerOption(
                        id = "square",
                        labelRes = R.string.settings_screen_shape_square,
                        subtitleRes = R.string.settings_screen_shape_override_sub
                    )
                )
            ),
            SettingsEntry.Section(
                id = "visual_effects_header",
                title = "视觉效果",
                titleRes = R.string.settings_visual_effects_header
            ),
            SettingsEntry.Toggle(
                id = "blur_toggle",
                title = "模糊",
                prefKey = "blur_enabled",
                titleRes = R.string.settings_blur,
                subtitleRes = R.string.settings_blur_sub
            ),
            SettingsEntry.Toggle(
                id = "edge_blur_toggle",
                title = "边缘模糊",
                prefKey = "edge_blur_enabled",
                titleRes = R.string.settings_edge_blur,
                subtitleRes = R.string.settings_edge_blur_sub
            ),
            // 半径滑块只有在「模糊 + 边缘模糊」都开着时才可拖动。
            SettingsEntry.Slider(
                id = "edge_blur_radius",
                title = "边缘模糊半径",
                prefKey = "honeycomb_edge_blur_tenths",
                min = 0.5f,
                max = 5f,
                steps = 8,
                unit = "dp",
                titleRes = R.string.settings_edge_blur_radius,
                decimals = 1
            ),
            SettingsEntry.Slider(
                id = "top_fade",
                title = "顶部渐隐",
                prefKey = "honeycomb_top_fade",
                min = 0f,
                max = 160f,
                steps = 15,
                unit = "dp",
                titleRes = R.string.settings_top_fade,
                decimals = 0
            ),
            SettingsEntry.Slider(
                id = "bottom_fade",
                title = "底部渐隐",
                prefKey = "honeycomb_bottom_fade",
                min = 0f,
                max = 160f,
                steps = 15,
                unit = "dp",
                titleRes = R.string.settings_bottom_fade,
                decimals = 0
            ),
            SettingsEntry.Section(
                id = "launch_header",
                title = "启动",
                titleRes = R.string.settings_section_launch
            ),
            SettingsEntry.Toggle(
                id = "splash_toggle",
                title = "启动图标",
                prefKey = "splash_icon",
                titleRes = R.string.settings_splash,
                subtitleRes = R.string.settings_splash_sub
            ),
            SettingsEntry.Toggle(
                id = "direct_launch_app_list",
                title = "直接进入应用列表",
                prefKey = "direct_launch_app_list",
                titleRes = R.string.settings_direct_launch_app_list,
                subtitleOnRes = R.string.settings_direct_launch_app_list_on,
                subtitleOffRes = R.string.settings_direct_launch_app_list_off
            ),
            // 手写版这里是裸 item，不和上面两个开关并卡；且只在启动图标开启时出现。
            SettingsEntry.Slider(
                id = "splash_delay",
                title = "启动延时",
                prefKey = "splash_delay",
                min = 300f,
                max = 1500f,
                steps = 11,
                unit = "ms",
                titleRes = R.string.settings_splash_delay,
                decimals = 0,
                mergeable = false
            )
        )
    )

    // ---------------------------------------------------------------- 表盘
    // ---------------------------------------------------- 表盘 · 更多设置
    // WATCH_FACE_MORE 屏。原来和 WATCH_FACES 混在一个 "watchFace" 主题组里，
    // 那个组只有 11 条、缺天气/点击动作/日期语言三整块。这里按屏重建。
    //
    // 这一屏把三种布局语义都用上了，正好是 [CardPlacement] 的完整示例：
    // - 天气城市 + 系统定位并成一张卡（START + INLINE）；
    // - 温度单位、日期语言各自「每个选项一张卡」（independentOptions）；
    // - 授权类条目带行首图标，且图标随授权状态变（binding.icon 覆盖）。
    private val watchFaceMore = SettingsGroup(
        id = "watch_face_more",
        title = "表盘 · 更多设置",
        items = listOf(
            SettingsEntry.Action(
                id = "watchface_long_press_mode",
                titleRes = R.string.settings_watchface_long_press_mode,
                action = "open_long_press_mode_dialog",
                icon = EntryIcon.SETTINGS,
                placement = CardPlacement.ALONE
            ),
            SettingsEntry.Section(
                id = "watch_weather_header",
                titleRes = R.string.settings_watch_weather
            ),
            SettingsEntry.Action(
                id = "watch_weather_manual_city",
                titleRes = R.string.settings_watch_weather_city,
                action = "open_weather_city_dialog",
                icon = EntryIcon.SETTINGS,
                placement = CardPlacement.START
            ),
            SettingsEntry.Action(
                id = "watch_weather_system_location",
                titleRes = R.string.settings_watch_weather_use_system,
                action = "use_system_weather_location",
                icon = EntryIcon.REFRESH,
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Section(
                id = "watch_weather_unit_header",
                titleRes = R.string.settings_watch_weather_unit
            ),
            SettingsEntry.Picker(
                id = "watch_weather_unit",
                prefKey = null,
                // 温度单位存在 WeatherRepository，不走 DataStore。
                independentOptions = true,
                options = listOf(
                    PickerOption(
                        "celsius",
                        labelRes = R.string.settings_watch_weather_celsius,
                        subtitleRes = R.string.settings_watch_weather_celsius_sub
                    ),
                    PickerOption(
                        "fahrenheit",
                        labelRes = R.string.settings_watch_weather_fahrenheit,
                        subtitleRes = R.string.settings_watch_weather_fahrenheit_sub
                    )
                )
            ),
            SettingsEntry.Action(
                id = "watch_weather_refresh",
                titleRes = R.string.settings_watch_weather_refresh,
                subtitleRes = R.string.settings_watch_weather_refresh_sub,
                action = "refresh_weather",
                icon = EntryIcon.REFRESH,
                placement = CardPlacement.ALONE
            ),
            SettingsEntry.Action(
                id = "tap_actions",
                titleRes = R.string.settings_tap_actions,
                subtitleRes = R.string.settings_tap_actions_sub,
                action = "navigate_tap_actions",
                icon = EntryIcon.SETTINGS,
                placement = CardPlacement.START
            ),
            SettingsEntry.Toggle(
                id = "watchface_tap_haptics",
                prefKey = "watchface_tap_haptics_enabled",
                titleRes = R.string.settings_watchface_tap_haptics,
                subtitleRes = R.string.settings_watchface_tap_haptics_sub
            ),
            // ---- 以下五项只在 Watchmaker (.watch) 表盘下出现 ----
            SettingsEntry.Action(
                id = "watchface_canvas_entry",
                titleRes = R.string.settings_watchface_canvas_section,
                subtitleRes = R.string.settings_watchface_canvas_entry_sub,
                action = "open_canvas_adjust",
                icon = EntryIcon.SETTINGS,
                placement = CardPlacement.START
            ),
            SettingsEntry.Toggle(
                id = "watchface_gravity_parallax",
                prefKey = "watchface_gravity_parallax_enabled",
                titleRes = R.string.settings_watchface_gravity_parallax,
                subtitleOnRes = R.string.settings_watchface_gravity_parallax_on,
                subtitleOffRes = R.string.settings_watchface_gravity_parallax_off
            ),
            SettingsEntry.Toggle(
                id = "watchface_gravity_backdrop_blur",
                prefKey = "watchface_gravity_backdrop_blur_enabled",
                titleRes = R.string.settings_watchface_gravity_backdrop_blur,
                subtitleOnRes = R.string.settings_watchface_gravity_backdrop_blur_on,
                subtitleOffRes = R.string.settings_watchface_gravity_backdrop_blur_off,
                placement = CardPlacement.START
            ),
            // 图标由 binding.icon 按授权状态给（授予后变对勾），所以这里不声明。
            SettingsEntry.Action(
                id = "watchmaker_body_sensors",
                titleRes = R.string.settings_watchface_heart_rate,
                action = "request_body_sensors",
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Action(
                id = "watchmaker_calendar",
                titleRes = R.string.settings_watchface_calendar,
                action = "request_calendar",
                placement = CardPlacement.ALONE
            ),
            // ---- 以下两项只在内置（图片/视频）表盘下出现 ----
            SettingsEntry.Action(
                id = "watchface_font",
                titleRes = R.string.settings_watchface_font,
                action = "pick_watchface_font",
                icon = EntryIcon.SETTINGS,
                placement = CardPlacement.ALONE
            ),
            SettingsEntry.Action(
                id = "watchface_font_clear",
                titleRes = R.string.settings_watchface_font_clear,
                subtitleRes = R.string.settings_watchface_font_clear_sub,
                action = "clear_watchface_font",
                icon = EntryIcon.REFRESH,
                placement = CardPlacement.ALONE
            ),
            SettingsEntry.Toggle(
                id = "watchface_bottom_fade",
                prefKey = "watchface_bottom_fade_enabled",
                titleRes = R.string.settings_watchface_bottom_fade,
                subtitleOnRes = R.string.settings_watchface_bottom_fade_on,
                subtitleOffRes = R.string.settings_watchface_bottom_fade_off,
                placement = CardPlacement.START
            ),
            SettingsEntry.Toggle(
                id = "watchface_text_entry_animation",
                prefKey = "watchface_text_entry_animation",
                titleRes = R.string.settings_watchface_text_entry_animation,
                subtitleRes = R.string.settings_watchface_text_entry_animation_sub
            ),
            SettingsEntry.Toggle(
                id = "watchface_text_exit_animation",
                prefKey = "watchface_text_exit_animation",
                titleRes = R.string.settings_watchface_text_exit_animation,
                subtitleRes = R.string.settings_watchface_text_exit_animation_sub
            ),
            SettingsEntry.Section(
                id = "watchface_date_locale_header",
                titleRes = R.string.settings_watchface_date_language
            ),
            SettingsEntry.Picker(
                id = "watchface_date_locale",
                prefKey = "watchface_date_locale",
                independentOptions = true,
                options = listOf(
                    PickerOption(
                        "system",
                        labelRes = R.string.settings_theme_system,
                        subtitleRes = R.string.settings_watchface_date_system_sub
                    ),
                    // 「中文 / English / 日本語」三项在代码里就是硬编码，
                    // 与系统语言无关（语言选择器本身就该用各自的语言写）。
                    PickerOption("zh", "中文", subtitleRes = R.string.settings_watchface_date_zh_sub),
                    PickerOption("en", "English", subtitleRes = R.string.settings_watchface_date_en_sub),
                    PickerOption("ja", "日本語", subtitleRes = R.string.settings_watchface_date_ja_sub)
                )
            ),
            SettingsEntry.Toggle(
                id = "watchface_status_indicators",
                prefKey = "watchface_status_indicators",
                titleRes = R.string.settings_watchface_status,
                subtitleRes = R.string.settings_watchface_status_sub,
                placement = CardPlacement.START
            ),
            SettingsEntry.Toggle(
                id = "watchface_dnd_indicator",
                prefKey = "watchface_dnd_indicator",
                titleRes = R.string.settings_watchface_dnd_indicator,
                subtitleRes = R.string.settings_watchface_dnd_indicator_sub
            ),
            SettingsEntry.Toggle(
                id = "watchface_charging_power_text",
                prefKey = "watchface_charging_power_text",
                titleRes = R.string.settings_watchface_charging_power,
                subtitleRes = R.string.settings_watchface_charging_power_sub
            )
        )
    )

    // -------------------------------------------------- 应用列表 · 行内行为
    // 这里曾有一个名为 "app_list" 的「主题视图」组，横跨 DISPLAY_SIZE /
    // APP_LIST_BACKGROUND / APP_LIST_BEHAVIOR 三屏；渲染单位却是屏，所以它
    // 从未能服务任何一页。三屏各自有着落（两屏整页搬出、一屏按屏建模）之后，
    // 该组已整组删除。以下按屏成组 —— 主题分组留给信息架构与搜索。
    //
    // 行 key 与手写版一字不差：改名会同时丢掉滚动位置、鱼眼缩放与搜索索引，
    // 而且三者都不会报错。
    private val appListBehavior = SettingsGroup(
        id = "app_list_behavior",
        title = "应用列表 · 行内行为",
        items = listOf(
            SettingsEntry.Toggle(
                id = "app_list_watchface_colors",
                prefKey = "app_list_watchface_colors",
                titleRes = R.string.settings_watchface_launch_mask,
                subtitleOnRes = R.string.settings_watchface_launch_mask_on,
                subtitleOffRes = R.string.settings_watchface_launch_mask_off
            ),
            SettingsEntry.Toggle(
                id = "app_list_row_border",
                prefKey = "app_list_row_border_enabled",
                titleRes = R.string.settings_list_row_bg,
                subtitleOnRes = R.string.settings_list_row_bg_on,
                subtitleOffRes = R.string.settings_list_row_bg_off
            ),
            SettingsEntry.Toggle(
                id = "app_list_folders_enabled",
                prefKey = "app_list_folders_enabled",
                titleRes = R.string.settings_allow_folders,
                subtitleOnRes = R.string.settings_allow_folders_on,
                subtitleOffRes = R.string.settings_allow_folders_off
            )
        )
    )

    // -------------------------------------------------------- 性能 · 目录
    // PERFORMANCE 屏本身只是一个三行目录，原来这屏根本没有建模（代码里 13 项
    // 平铺在一页的遗留结构，后来才拆成三张卡片 + 三个子页）。
    private val performance = SettingsGroup(
        id = "performance",
        title = "性能",
        items = listOf(
            SettingsEntry.Navigation(
                id = "perf_group_rendering",
                titleRes = R.string.settings_perf_group_rendering,
                subtitleRes = R.string.settings_perf_group_rendering_sub,
                destination = "PERFORMANCE_RENDERING",
                placement = CardPlacement.START
            ),
            SettingsEntry.Navigation(
                id = "perf_group_motion",
                titleRes = R.string.settings_perf_group_motion,
                subtitleRes = R.string.settings_perf_group_motion_sub,
                destination = "PERFORMANCE_MOTION",
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Navigation(
                id = "perf_group_system",
                titleRes = R.string.settings_perf_group_system,
                subtitleRes = R.string.settings_perf_group_system_sub,
                destination = "PERFORMANCE_SYSTEM",
                placement = CardPlacement.INLINE
            )
        )
    )

    // ---------------------------------------------------- 性能 · 图标渲染
    private val performanceRendering = SettingsGroup(
        id = "performance_rendering",
        title = "图标与渲染",
        items = listOf(
            SettingsEntry.Toggle(
                id = "low_res",
                prefKey = "low_res_icons",
                titleRes = R.string.settings_low_res_icons,
                subtitleRes = R.string.settings_low_res_icons_sub,
                placement = CardPlacement.START
            ),
            SettingsEntry.Toggle(
                id = "icon_shadow",
                prefKey = "app_icon_shadow_enabled",
                titleRes = R.string.settings_icon_shadow,
                subtitleRes = R.string.settings_icon_shadow_sub,
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Toggle(
                id = "builtin_manager_thumbnails",
                prefKey = "builtin_manager_thumbnails",
                titleRes = R.string.settings_builtin_thumbnails,
                subtitleRes = R.string.settings_builtin_thumbnails_sub,
                placement = CardPlacement.INLINE
            )
        )
    )

    // ---------------------------------------------------- 性能 · 动画
    // 这一屏是「旧 animation 组」的真实内容。原组 6 条里 3 条是幽灵
    // （animation_curve_tier 从未实现、animation_override 的 key 写成了
    // animation_override_enabled 而代码里行 key 是 anim_override、
    // classic_return_animation 入口早已删除），已随本屏重建清掉。
    //
    // 卡片边界比前几屏碎：4 张卡 + 2 个裸 item 交错，正好用 placement 表达。
    private val performanceMotion = SettingsGroup(
        id = "performance_motion",
        title = "动画与转场",
        items = listOf(
            SettingsEntry.Toggle(
                id = "anim_override",
                prefKey = "animation_override_enabled",
                titleRes = R.string.settings_launch_animation,
                subtitleRes = R.string.settings_launch_animation_sub,
                placement = CardPlacement.START
            ),
            SettingsEntry.Toggle(
                id = "app_list_return_shrink",
                prefKey = "app_list_return_shrink_enabled",
                titleRes = R.string.settings_app_list_shrink,
                subtitleRes = R.string.settings_app_list_shrink_sub,
                placement = CardPlacement.INLINE
            ),
            // 只在「应用列表返回收缩」关闭时出现：收缩开着时图标浮起没有意义。
            SettingsEntry.Toggle(
                id = "app_list_return_icon_float",
                prefKey = "app_list_return_icon_float_enabled",
                titleRes = R.string.settings_icon_float,
                subtitleRes = R.string.settings_icon_float_sub,
                placement = CardPlacement.ALONE
            ),
            SettingsEntry.Toggle(
                id = "face_return_shrink",
                prefKey = "face_return_shrink_enabled",
                titleRes = R.string.settings_face_shrink,
                subtitleRes = R.string.settings_face_shrink_sub,
                placement = CardPlacement.ALONE
            ),
            SettingsEntry.Toggle(
                id = "legacy_app_list_transition",
                prefKey = "legacy_app_list_transition_enabled",
                titleRes = R.string.settings_legacy_transition,
                subtitleRes = R.string.settings_legacy_transition_sub,
                placement = CardPlacement.START
            ),
            SettingsEntry.Toggle(
                id = "app_list_stagger",
                prefKey = "app_list_stagger_enabled",
                titleRes = R.string.settings_stagger_entry,
                subtitleRes = R.string.settings_stagger_entry_sub,
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Toggle(
                id = "reduce_dynamic_effects",
                prefKey = "reduce_dynamic_effects",
                titleRes = R.string.settings_reduce_motion,
                subtitleRes = R.string.settings_reduce_motion_sub,
                placement = CardPlacement.INLINE
            ),
            // 副标题是当前选中的预设名，只能由页面算。
            SettingsEntry.Navigation(
                id = "music_text_animation",
                titleRes = R.string.settings_music_text_animation,
                destination = "MUSIC_TEXT_ANIMATION",
                placement = CardPlacement.ALONE
            ),
            // 副标题是「档位名 + 是否弹跳」，同样只能运行时算。
            SettingsEntry.Navigation(
                id = "motion_profile",
                titleRes = R.string.settings_motion_profile,
                destination = "MOTION_PROFILE",
                placement = CardPlacement.ALONE
            )
        )
    )

    // ---------------------------------------------------- 性能 · 系统
    private val performanceSystem = SettingsGroup(
        id = "performance_system",
        title = "系统行为",
        items = listOf(
            SettingsEntry.Toggle(
                id = "hide_from_recents",
                prefKey = "hide_from_recents",
                titleRes = R.string.settings_hide_recents,
                subtitleRes = R.string.settings_hide_recents_sub
            )
        )
    )

    // ------------------------------------------------------------ 副一屏
    // 原来这三屏被塞在一个 "input" 聚合组里，与实际页面结构不符：
    // 它们各自是独立的 SettingsDestination，手写版就是「整屏一张卡」。
    // 现在按屏建模，条目 id 直接等于行 key（改名会丢滚动位置与鱼眼缩放）。
    private val sideScreen = SettingsGroup(
        id = "side_screen",
        title = "副一屏",
        items = listOf(
            SettingsEntry.Toggle(
                id = "side_screen_toggle",
                prefKey = "side_screen_enabled",
                titleRes = R.string.settings_side_screen,
                subtitleRes = R.string.settings_side_screen_sub
            ),
            SettingsEntry.Toggle(
                id = "widget_center_toggle",
                prefKey = "show_widget_page",
                titleRes = R.string.settings_widget_page,
                subtitleOnRes = R.string.settings_widget_page_on,
                subtitleOffRes = R.string.settings_widget_page_off
            ),
            SettingsEntry.Toggle(
                id = "widget_glass_highlight",
                prefKey = "widget_glass_highlight_enabled",
                titleRes = R.string.settings_widget_glass_highlight,
                subtitleOnRes = R.string.settings_widget_glass_highlight_on,
                subtitleOffRes = R.string.settings_widget_glass_highlight_off
            ),
            SettingsEntry.Toggle(
                id = "music_notification_swap_toggle",
                prefKey = "swap_music_notification_components",
                titleRes = R.string.settings_swap_music_notifications,
                subtitleOnRes = R.string.settings_swap_music_notifications_on,
                subtitleOffRes = R.string.settings_swap_music_notifications_off
            ),
            SettingsEntry.Toggle(
                id = "music_controls_toggle",
                prefKey = "show_music_controls",
                titleRes = R.string.settings_show_music,
                subtitleOnRes = R.string.settings_show_music_on,
                subtitleOffRes = R.string.settings_show_music_off
            ),
            SettingsEntry.Toggle(
                id = "media_custom_actions_toggle",
                prefKey = "show_media_custom_actions",
                titleRes = R.string.settings_media_actions,
                subtitleOnRes = R.string.settings_media_actions_on,
                subtitleOffRes = R.string.settings_media_actions_off
            ),
            SettingsEntry.Toggle(
                id = "show_step_count",
                prefKey = "show_step_count",
                titleRes = R.string.settings_show_steps,
                subtitleRes = R.string.settings_show_steps_sub
            )
        )
    )

    // ------------------------------------------------------------ 通知
    private val notifications = SettingsGroup(
        id = "notifications",
        title = "通知",
        items = listOf(
            SettingsEntry.Toggle(
                id = "notification_center_toggle",
                prefKey = "show_notification",
                titleRes = R.string.settings_show_notifications,
                subtitleOnRes = R.string.settings_show_notifications_on,
                subtitleOffRes = R.string.settings_show_notifications_off
            ),
            SettingsEntry.Toggle(
                id = "ongoing_notification_toggle",
                prefKey = "show_ongoing_notifications",
                titleRes = R.string.settings_show_ongoing,
                subtitleOnRes = R.string.settings_show_ongoing_on,
                subtitleOffRes = R.string.settings_show_ongoing_off
            )
        )
    )

    // ------------------------------------------------------------ 手势
    // 注意："双击表盘息屏" 的副标题按「走设备管理员 / 无障授权是否已给」四分支变化，
    // 那是运行时状态，只能由页面算（binding.subtitle）；这里给的是兜底文案。
    private val gestures = SettingsGroup(
        id = "gestures",
        title = "手势",
        items = listOf(
            SettingsEntry.Toggle(
                id = "double_tap_lock_screen",
                prefKey = "double_tap_lock_screen_enabled",
                titleRes = R.string.settings_double_tap_lock,
                subtitleRes = R.string.settings_double_tap_require
            ),
            SettingsEntry.Toggle(
                id = "face_ignores_back_key",
                prefKey = "face_ignores_back_key",
                titleRes = R.string.settings_face_ignores_back,
                subtitleRes = R.string.settings_face_ignores_back_sub
            )
        )
    )


    // ------------------------------------------------------------ 功能开关屏
    // FEATURE_TOGGLES 这一屏不是"六大分组"里的某一组，而是一个入口聚合页：
    // 四张分类卡片分别跳进副一屏 / 通知 / 手势 / 应用列表行为，末尾挂一个玻璃高光开关。
    // 它是第一个真正被界面消费的组（其余组仍在等迁移），所以 id 直接沿用屏名。
    private val featureToggles = SettingsGroup(
        id = "feature_toggles",
        title = "功能开关",
        items = listOf(
            SettingsEntry.Navigation(
                id = "cat_side_screen",
                title = "副一屏",
                titleRes = R.string.settings_side_screen_title,
                subtitleRes = R.string.settings_side_screen_card_sub,
                destination = "SIDE_SCREEN"
            ),
            SettingsEntry.Navigation(
                id = "cat_notifications",
                title = "通知",
                titleRes = R.string.settings_notifications_title,
                subtitleRes = R.string.settings_notifications_card_sub,
                destination = "NOTIFICATION_SETTINGS"
            ),
            SettingsEntry.Navigation(
                id = "cat_gestures",
                title = "手势",
                titleRes = R.string.settings_gestures_title,
                subtitleRes = R.string.settings_gestures_card_sub,
                destination = "GESTURE_SETTINGS"
            ),
            SettingsEntry.Navigation(
                id = "cat_app_list_behavior",
                title = "应用列表行为",
                titleRes = R.string.settings_app_list_behavior_title,
                subtitleRes = R.string.settings_app_list_behavior_card_sub,
                destination = "APP_LIST_BEHAVIOR"
            ),
            SettingsEntry.Toggle(
                id = "glass_highlight_toggle",
                title = "玻璃高光",
                prefKey = "glass_highlight_enabled",
                titleRes = R.string.settings_glass_highlight,
                subtitleOnRes = R.string.settings_glass_highlight_on,
                subtitleOffRes = R.string.settings_glass_highlight_off
            ),
            SettingsEntry.Toggle(
                id = "lock_screen_watch_face_toggle",
                title = "锁屏表盘",
                prefKey = "lock_screen_watch_face_enabled",
                titleRes = R.string.settings_lock_screen_watch_face,
                subtitleOnRes = R.string.settings_lock_screen_watch_face_on,
                subtitleOffRes = R.string.settings_lock_screen_watch_face_off
            ),
            SettingsEntry.Toggle(
                id = "pull_down_control_center_toggle",
                title = "下拉控制中心",
                prefKey = "pull_down_control_center_enabled",
                titleRes = R.string.settings_pull_down_control_center,
                subtitleOnRes = R.string.settings_pull_down_control_center_on,
                subtitleOffRes = R.string.settings_pull_down_control_center_off
            )
        )
    )

    // ---------------------------------------------------------------- 工具
    // TOOLS 屏。原 "system" 组把四屏混在一起，且 8 条里有 3 条是幽灵
    // （accessibility / notification_access 从未落地，backup 的实际行 key 是 backup_menu，
    // global_ui_scale 其实渲染在 DISPLAY_SIZE 屏）—— 按屏重建时全部修正。
    private val tools = SettingsGroup(
        id = "tools",
        title = "工具",
        items = listOf(
            SettingsEntry.Navigation(
                id = "backup_menu",
                titleRes = R.string.settings_backup_menu,
                subtitleRes = R.string.settings_backup_menu_sub,
                destination = "BACKUP",
                placement = CardPlacement.START
            ),
            SettingsEntry.Action(
                id = "export_log",
                titleRes = R.string.settings_export_log,
                subtitleRes = R.string.settings_export_log_sub,
                action = "export_log",
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Toggle(
                id = "diagnostic_logging",
                prefKey = null,
                titleRes = R.string.settings_diagnostic_logging,
                // 非 DataStore：FlulyLog 的内部状态，5 分钟后自动关闭，所以走双文案。
                subtitleOnRes = R.string.settings_diagnostic_logging_on,
                subtitleOffRes = R.string.settings_diagnostic_logging_off,
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Action(
                id = "default_home_app",
                titleRes = R.string.settings_default_home_app,
                // 副标题是动态的（是否已是默认，见 TOOLS 页 binding），这是渲染/搜索的兜底文案。
                subtitleRes = R.string.settings_default_home_app_sub,
                action = "default_home_app",
                placement = CardPlacement.INLINE
            ),
            SettingsEntry.Action(
                id = "reset_defaults",
                titleRes = R.string.settings_reset_defaults,
                subtitleRes = R.string.settings_reset_defaults_sub,
                action = "reset_settings",
                placement = CardPlacement.INLINE
            )
        )
    )

    // ---------------------------------------------------------------- 备份
    private val backup = SettingsGroup(
        id = "backup",
        title = "备份",
        items = listOf(
            SettingsEntry.Navigation(
                id = "backup_export_page",
                titleRes = R.string.settings_backup_export_page,
                subtitleRes = R.string.settings_backup_export_page_sub,
                destination = "BACKUP_EXPORT",
                placement = CardPlacement.START
            ),
            SettingsEntry.Navigation(
                id = "backup_import_page",
                titleRes = R.string.settings_backup_import_page,
                subtitleRes = R.string.settings_backup_import_page_sub,
                destination = "BACKUP_IMPORT",
                placement = CardPlacement.INLINE
            )
        )
    )

    /**
     * 备份内容勾选项，导出/导入两屏共用。
     *
     * 这是模型里**唯一**一处同一批条目出现在两个组：两屏勾的是同一组内容，
     * 只是导入屏会按压缩包里实际有的分区隐藏一部分。复用同一份声明而不是
     * 各写一遍，否则七条文案要在两个组里同步维护。
     *
     * 它们不落 DataStore（`prefKey = null`）—— 选择是「这次操作要做什么」，
     * 不是持久偏好，进程结束就该丢。
     */
    private val backupOptions: List<SettingsEntry> = listOf(
        SettingsEntry.Toggle(
            id = "backup_settings",
            prefKey = null,
            titleRes = R.string.backup_item_settings,
            subtitleRes = R.string.backup_item_settings_sub,
            placement = CardPlacement.START
        ),
        SettingsEntry.Toggle(
            id = "backup_app_order",
            prefKey = null,
            titleRes = R.string.backup_item_app_order,
            subtitleRes = R.string.backup_item_app_order_sub,
            placement = CardPlacement.INLINE
        ),
        SettingsEntry.Toggle(
            id = "backup_widget_order",
            prefKey = null,
            titleRes = R.string.backup_item_widget_order,
            subtitleRes = R.string.backup_item_widget_order_sub,
            placement = CardPlacement.INLINE
        ),
        SettingsEntry.Toggle(
            id = "backup_side_screen_order",
            prefKey = null,
            titleRes = R.string.backup_item_side_screen_order,
            subtitleRes = R.string.backup_item_side_screen_order_sub,
            placement = CardPlacement.INLINE
        ),
        SettingsEntry.Toggle(
            id = "backup_photo_watchface",
            prefKey = null,
            titleRes = R.string.backup_item_photo_watchface,
            subtitleRes = R.string.backup_item_photo_watchface_sub,
            placement = CardPlacement.INLINE
        ),
        SettingsEntry.Toggle(
            id = "backup_video_watchface",
            prefKey = null,
            titleRes = R.string.backup_item_video_watchface,
            subtitleRes = R.string.backup_item_video_watchface_sub,
            placement = CardPlacement.INLINE
        ),
        SettingsEntry.Toggle(
            id = "backup_watch_files",
            prefKey = null,
            titleRes = R.string.backup_item_watch_files,
            subtitleRes = R.string.backup_item_watch_files_sub,
            placement = CardPlacement.INLINE
        )
    )

    private val backupExport = SettingsGroup(
        id = "backup_export",
        title = "导出配置",
        items = backupOptions + listOf(
            SettingsEntry.Action(
                id = "export_backup_start",
                titleRes = R.string.settings_backup_export_start,
                subtitleRes = R.string.settings_backup_export_start_sub,
                action = "start_backup_export",
                placement = CardPlacement.ALONE
            )
        )
    )

    private val backupImport = SettingsGroup(
        id = "backup_import",
        title = "导入配置",
        items = listOf(
            // 副标题随「是否已选文件」变化（选完会显示版本号与时间），由页面算。
            SettingsEntry.Action(
                id = "import_backup_pick",
                titleRes = R.string.settings_backup_import_pick,
                subtitleRes = R.string.settings_backup_import_pick_sub,
                action = "pick_backup_file",
                placement = CardPlacement.ALONE
            )
        ) + backupOptions + listOf(
            // 只有预览解析成功后才出现（手写版是 `preview?.let { .. }`）。
            SettingsEntry.Action(
                id = "import_backup_start",
                titleRes = R.string.settings_backup_import_start,
                subtitleRes = R.string.settings_backup_import_start_sub,
                action = "start_backup_import",
                placement = CardPlacement.ALONE
            )
        )
    )

    /** 新信息架构的完整分组列表（顺序即页面展示顺序）。 */
    val groups: List<SettingsGroup> = listOf(
        appearance,
        watchFaceMore,
        appListBehavior,
        performance,
        performanceRendering,
        performanceMotion,
        performanceSystem,
        sideScreen,
        notifications,
        gestures,
        featureToggles,
        tools,
        backup,
        backupExport,
        backupImport
    )

    /**
     * 已经由数据驱动渲染接管的分组。
     *
     * 迁移是逐屏进行的，未列入此处的组仍由 `SettingsActivity` 手写渲染，
     * 它们的内容也可能与代码存在漂移 —— 直接拿去渲染会画出不存在的设置项。
     * 新迁一屏就把它加进来，这个集合就是"进度表"。
     */
    val renderedGroups: List<SettingsGroup> = listOf(
        appearance,
        watchFaceMore,
        appListBehavior,
        performance,
        performanceRendering,
        performanceMotion,
        performanceSystem,
        sideScreen,
        notifications,
        gestures,
        featureToggles,
        tools,
        backup,
        backupExport,
        backupImport
    )

    /** 按 id 查找条目，供未来的渲染层使用。 */
    fun findEntry(id: String): SettingsEntry? =
        groups.asSequence().flatMap { it.items }.firstOrNull { it.id == id }

    /**
     * 取一个已迁移分组的条目，交给 [settingsEntryGroup] 渲染。
     *
     * 只允许取 [renderedGroups] 里的组：未迁移的组与代码存在漂移，
     * 直接渲染会画出不存在的设置项。传入未迁移的 id 会立即失败，
     * 而不是安静地画出一个错误的页面。
     */
    fun renderedGroup(id: String): List<SettingsEntry> =
        renderedGroups.firstOrNull { it.id == id }?.items
            ?: error("设置分组 $id 尚未迁移到数据驱动渲染（见 SettingsHierarchy.renderedGroups）")
}
