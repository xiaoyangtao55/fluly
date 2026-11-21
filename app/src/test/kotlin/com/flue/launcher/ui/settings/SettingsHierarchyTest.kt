package com.flue.launcher.ui.settings

import com.flue.launcher.SettingsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声明式设置模型与代码的一致性约束。
 *
 * 迁移是逐屏做的，最容易出的事故不是编译错误，而是「行为悄悄变了」：
 * 行 key 改一个字符、destination 拼错、文案没进 strings.xml —— 这三类都不会报错，
 * 只会表现为鱼眼不缩放、点击没反应、或者中文硬编码进了代码。
 * 这里把三条约束钉死。
 */
class SettingsHierarchyTest {

    /**
     * 行 key 必须与手写版逐字一致。
     *
     * `flueSettingsGroup` 用 key 取鱼眼缩放值、恢复滚动位置，改名不报错，
     * 只会让那几行在圆屏上不缩放、返回时跳回页顶。
     */
    @Test
    fun featureTogglesKeepsHistoricalRowKeys() {
        val keys = SettingsHierarchy.renderedGroup("feature_toggles").map { it.id }

        assertEquals(
            listOf(
                "cat_side_screen",
                "cat_notifications",
                "cat_gestures",
                "cat_app_list_behavior",
                "glass_highlight_toggle",
            ),
            keys,
        )
    }

    /**
     * 导航条目的 destination 会被渲染层按枚举名解析：
     * 名字写错时 `firstOrNull` 返回 null，点击就是「没反应」，不会崩。
     */
    @Test
    fun navigationDestinationsAllResolveToRealPages() {
        val pageNames = SettingsDestination.entries.map { it.name }.toSet()

        SettingsHierarchy.renderedGroup("feature_toggles")
            .filterIsInstance<SettingsEntry.Navigation>()
            .forEach { entry ->
                assertTrue(
                    "${entry.id} 的 destination=${entry.destination} 不是有效页面",
                    entry.destination in pageNames,
                )
            }
    }

    /** AGENTS.md 要求用户可见文案进 strings.xml（含三语），迁移时不能退化成硬编码。 */
    @Test
    fun migratedEntriesCarryStringResources() {
        SettingsHierarchy.renderedGroup("feature_toggles").forEach { entry ->
            assertNotNull("${entry.id} 缺少 titleRes", entry.titleRes)
        }
    }

    /** 状态回显类开关（如玻璃高光）必须两个状态的文案都声明，否则会退化成空副标题。 */
    @Test
    fun statefulToggleDeclaresBothSubtitleStates() {
        val toggle = SettingsHierarchy.renderedGroup("feature_toggles")
            .filterIsInstance<SettingsEntry.Toggle>()
            .single()

        assertNotNull("${toggle.id} 缺少 subtitleOnRes", toggle.subtitleOnRes)
        assertNotNull("${toggle.id} 缺少 subtitleOffRes", toggle.subtitleOffRes)
    }

    /** 未注册的组 id 取用必须立即失败，而不是画出错误的页面。 */
    @Test(expected = IllegalStateException::class)
    fun unmigratedGroupFailsFast() {
        SettingsHierarchy.renderedGroup("no_such_screen_group")
    }

    /**
     * 副一屏 / 通知 / 手势三屏的条目顺序与行 key。
     *
     * 这三屏原来共用 SettingsHierarchy 里一个 "input" 聚合组，与实际页面结构不符
     * （旧组把「表冠滚动震动」也塞了进来，而它其实渲染在显示大小屏）。
     * 拆成按屏建模后，这里钉住两件事：key 与手写版逐字一致、条目不串屏。
     */
    @Test
    fun splitScreensKeepHistoricalRowKeys() {
        assertEquals(
            listOf(
                "side_screen_toggle",
                "widget_center_toggle",
                "widget_glass_highlight",
                "music_notification_swap_toggle",
                "music_controls_toggle",
                "media_custom_actions_toggle",
                "show_step_count",
            ),
            SettingsHierarchy.renderedGroup("side_screen").map { it.id },
        )
        assertEquals(
            listOf(
                "notification_center_toggle",
                "ongoing_notification_toggle",
            ),
            SettingsHierarchy.renderedGroup("notifications").map { it.id },
        )
        assertEquals(
            listOf(
                "double_tap_lock_screen",
                "face_ignores_back_key",
            ),
            SettingsHierarchy.renderedGroup("gestures").map { it.id },
        )
    }

    /**
     * 这三屏的手写版有一个共同点：整屏只有一张卡（一个 `flueSettingsGroup` 包住全部行）。
     * 所以每条都必须 mergeable，否则渲染层会切出多张卡，界面直接变形。
     */
    @Test
    fun splitScreenTogglesAllMergeIntoOneCard() {
        listOf("side_screen", "notifications", "gestures").forEach { groupId ->
            SettingsHierarchy.renderedGroup(groupId).forEach { entry ->
                assertTrue("$groupId/${entry.id} 必须可与相邻行合并", entry.mergeable)
                assertTrue("$groupId/${entry.id} 应为 Toggle", entry is SettingsEntry.Toggle)
            }
        }
    }

    /**
     * 这三屏的文案同样必须走字符串资源。
     *
     * 同时钉住新约定：迁移过的条目 `title` 留空。中文写两遍（模型一份、strings.xml 一份）
     * 迟早会不一致，而渲染层只看 `titleRes`，不一致也不会报错。
     */
    @Test
    fun splitScreenEntriesCarryStringResources() {
        listOf("side_screen", "notifications", "gestures").forEach { groupId ->
            SettingsHierarchy.renderedGroup(groupId).forEach { entry ->
                assertNotNull("$groupId/${entry.id} 缺少 titleRes", entry.titleRes)
                assertEquals("$groupId/${entry.id} 不应再写中文回退标题", "", entry.title)
            }
        }
    }

    /**
     * APPEARANCE 整屏展开后的 item 序列必须与手写版逐项一致。
     *
     * 这条测的是三件同时可能出错、又都不会报错的事：
     * 1. 分节标题的位置（少一个标题、标题跑到组中间）；
     * 2. Picker 展开成几行、每行 key 叫什么（`theme_mode_system` 这种）；
     * 3. 条目整体顺序 —— scaffold 的 `roundFirstActionIndex` 是按 item 索引写的，
     *    顺序一变，圆屏上「第一个可操作项」就会指到错的行。
     */
    @Test
    fun appearanceExpandsToHistoricalItemOrder() {
        assertEquals(
            listOf(
                // 布局
                "layout_header",
                "layout_honeycomb", "layout_list", "layout_grid", "layout_flow", "layout_ring",
                // 显示
                "display_header",
                "app_list_background_entry", "display_size_menu",
                "overlay_mask_corner_radius", "feature_toggles_menu",
                // 深浅色（没有分节标题，紧跟上一张卡）
                "theme_mode_system", "theme_mode_dark", "theme_mode_light",
                // 界面动画
                "transition_motion_header",
                "transition_motion_scale", "transition_motion_slide",
                // 屏幕形状
                "screen_shape_header",
                "screen_shape_auto", "screen_shape_round", "screen_shape_square",
                // 视觉效果
                "visual_effects_header",
                "blur_toggle", "edge_blur_toggle", "edge_blur_radius", "top_fade", "bottom_fade",
                // 启动
                "launch_header",
                "splash_toggle", "direct_launch_app_list", "splash_delay",
            ),
            expandItemKeys(SettingsHierarchy.renderedGroup("appearance")),
        )
    }

    /**
     * 把条目按渲染层的规则展开成 item key 序列。
     *
     * 这里刻意**不复用**生产代码的分组扫描：两处独立实现，同时写错才会漏过。
     */
    private fun expandItemKeys(entries: List<SettingsEntry>): List<String> = buildList {
        entries.forEach { entry ->
            when (entry) {
                is SettingsEntry.Picker -> entry.options.forEach { add("${entry.id}_${it.id}") }
                else -> add(entry.id)
            }
        }
    }

    /**
     * APP_LIST_BEHAVIOR 是第一个「按屏」建的组：`app_list` 主题组横跨
     * DISPLAY_SIZE / APP_LIST_BACKGROUND / APP_LIST_BEHAVIOR 三屏，拿它渲染会串屏。
     *
     * 钉住三件事：
     * 1. 行 key 与手写版逐字一致（`app_list_folders_enabled` 不是 `app_list_folders`）；
     * 2. 三条都是状态回显开关 —— 副标题必须两态都有，否则关闭时会露出空行；
     * 3. 不写中文回退标题。
     */
    @Test
    fun appListBehaviorIsAScreenScopedGroup() {
        val entries = SettingsHierarchy.renderedGroup("app_list_behavior")

        assertEquals(
            listOf("app_list_watchface_colors", "app_list_row_border", "app_list_folders_enabled"),
            entries.map { it.id },
        )

        entries.forEach { entry ->
            val toggle = entry as SettingsEntry.Toggle
            assertNotNull("${toggle.id} 缺少 titleRes", toggle.titleRes)
            assertNotNull("${toggle.id} 缺少 subtitleOnRes", toggle.subtitleOnRes)
            assertNotNull("${toggle.id} 缺少 subtitleOffRes", toggle.subtitleOffRes)
            assertEquals("${toggle.id} 不应再写中文回退标题", "", toggle.title)
        }
    }

    /**
     * 同一个设置不允许在两个组各建一份。
     *
     * 数据层是按主题重排的，很容易把「模糊」既放进 appearance（代码里叫 `blur_toggle`）
     * 又放进 animation（叫 `blur`）。两边 key 不一致时迁移会画出两个开关，
     * 而且关掉其中一个另一个还亮着 —— 用户会以为设置没生效。
     */
    @Test
    fun noSettingIsModeledTwiceAcrossGroups() {
        val seen = mutableMapOf<String, String>()
        SettingsHierarchy.groups.forEach { group ->
            group.items.forEach { entry ->
                entry.prefKey?.let { key ->
                    val previous = seen.put(key, "${group.id}/${entry.id}")
                    assertTrue(
                        "prefKey=$key 同时出现在 $previous 与 ${group.id}/${entry.id}",
                        previous == null,
                    )
                }
            }
        }
    }

    /**
     * `WATCH_FACE_MORE` 整屏展开后的 item 序列。
     *
     * 这一屏是首个同时用到「分节标题 + 断卡 + 独立选项单选」的页面，
     * 顺序一错，圆屏的 `roundFirstActionIndex = 2` 会指到别的行。
     */
    @Test
    fun watchFaceMoreExpandsToHistoricalItemOrder() {
        assertEquals(
            listOf(
                "watchface_long_press_mode",
                "watch_weather_header",
                "watch_weather_manual_city", "watch_weather_system_location",
                "watch_weather_unit_header",
                "watch_weather_unit_celsius", "watch_weather_unit_fahrenheit",
                "watch_weather_refresh",
                "tap_actions", "watchface_tap_haptics",
                "watchface_canvas_entry", "watchface_gravity_parallax",
                "watchface_gravity_backdrop_blur", "watchmaker_body_sensors",
                "watchmaker_calendar",
                "watchface_font", "watchface_font_clear",
                "watchface_bottom_fade", "watchface_text_entry_animation",
                "watchface_text_exit_animation",
                "watchface_date_locale_header",
                "watchface_date_locale_system", "watchface_date_locale_zh",
                "watchface_date_locale_en", "watchface_date_locale_ja",
                "watchface_status_indicators", "watchface_dnd_indicator",
                "watchface_charging_power_text",
            ),
            expandItemKeys(SettingsHierarchy.renderedGroup("watch_face_more")),
        )
    }

    /**
     * 卡片边界必须与手写版逐段一致。
     *
     * 手写版用「`flueSettingsGroup { row {} }` 并卡、裸 `item {}` 独立」表达边界，
     * 数据层换成 [CardPlacement]。边界错了不会崩，只会让圆角、分隔线、行距变样 ——
     * 属于「一眼看出设置页变味」但测试发现不了的那类回归，所以在这里钉住。
     *
     * 这里刻意重写一遍分组算法，不复用生产代码：两处独立实现，同时写错才会漏过。
     */
    @Test
    fun watchFaceMoreKeepsHistoricalCardBoundaries() {
        val cards = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()

        fun flush() {
            if (current.isNotEmpty()) {
                cards.add(current)
                current = mutableListOf()
            }
        }

        SettingsHierarchy.renderedGroup("watch_face_more").forEach { entry ->
            when {
                entry is SettingsEntry.Section -> flush()
                entry is SettingsEntry.Picker && entry.independentOptions -> {
                    flush()
                    entry.options.forEach { cards.add(mutableListOf("${entry.id}_${it.id}")) }
                }
                entry.placement == CardPlacement.ALONE -> {
                    flush()
                    cards.add(mutableListOf(entry.id))
                }
                entry.placement == CardPlacement.START -> {
                    flush()
                    current.add(entry.id)
                }
                entry.placement == CardPlacement.INLINE -> current.add(entry.id)
                entry is SettingsEntry.Toggle || entry is SettingsEntry.Slider -> current.add(entry.id)
                else -> {
                    flush()
                    cards.add(mutableListOf(entry.id))
                }
            }
        }
        flush()

        assertEquals(
            listOf(
                listOf("watchface_long_press_mode"),
                listOf("watch_weather_manual_city", "watch_weather_system_location"),
                listOf("watch_weather_unit_celsius"),
                listOf("watch_weather_unit_fahrenheit"),
                listOf("watch_weather_refresh"),
                listOf("tap_actions", "watchface_tap_haptics"),
                listOf("watchface_canvas_entry", "watchface_gravity_parallax"),
                listOf("watchface_gravity_backdrop_blur", "watchmaker_body_sensors"),
                listOf("watchmaker_calendar"),
                listOf("watchface_font"),
                listOf("watchface_font_clear"),
                listOf(
                    "watchface_bottom_fade", "watchface_text_entry_animation",
                    "watchface_text_exit_animation",
                ),
                listOf("watchface_date_locale_system"),
                listOf("watchface_date_locale_zh"),
                listOf("watchface_date_locale_en"),
                listOf("watchface_date_locale_ja"),
                listOf(
                    "watchface_status_indicators", "watchface_dnd_indicator",
                    "watchface_charging_power_text",
                ),
            ),
            cards,
        )
    }
    /**
     * 性能四屏的行 key 序列。
     *
     * `app_list_return_icon_float` 在手写版里是「收缩关着才出现」的条件项，
     * 数据层把它留在原位、由 `isVisible` 过滤 —— 位置错了不会崩，
     * 只是关掉收缩后那一行会跑到卡片里的另一处。
     */
    @Test
    fun performanceScreensKeepHistoricalRowKeys() {
        assertEquals(
            listOf("perf_group_rendering", "perf_group_motion", "perf_group_system"),
            SettingsHierarchy.renderedGroup("performance").map { it.id },
        )
        assertEquals(
            listOf("low_res", "icon_shadow", "builtin_manager_thumbnails"),
            SettingsHierarchy.renderedGroup("performance_rendering").map { it.id },
        )
        assertEquals(
            listOf(
                "anim_override",
                "app_list_return_shrink",
                "app_list_return_icon_float",
                "face_return_shrink",
                "legacy_app_list_transition",
                "app_list_stagger",
                "reduce_dynamic_effects",
                "music_text_animation",
                "motion_profile",
            ),
            SettingsHierarchy.renderedGroup("performance_motion").map { it.id },
        )
        assertEquals(
            listOf("hide_from_recents"),
            SettingsHierarchy.renderedGroup("performance_system").map { it.id },
        )
    }

    /**
     * 动画屏的卡片边界：四张卡与两个裸 item 交错。
     *
     * 手写版是 `flueSettingsGroup{..} → item → item → flueSettingsGroup{..} → item → item`，
     * 顺序或合并方式错一处，圆角与行距就会与原来不同 —— 不报错，只能靠这条测试发现。
     */
    @Test
    fun performanceMotionKeepsHistoricalCardBoundaries() {
        assertEquals(
            listOf(
                listOf("perf_group_rendering", "perf_group_motion", "perf_group_system"),
            ),
            cardBoundaries(SettingsHierarchy.renderedGroup("performance")),
        )
        assertEquals(
            listOf(listOf("low_res", "icon_shadow", "builtin_manager_thumbnails")),
            cardBoundaries(SettingsHierarchy.renderedGroup("performance_rendering")),
        )
        assertEquals(
            listOf(
                listOf("anim_override", "app_list_return_shrink"),
                listOf("app_list_return_icon_float"),
                listOf("face_return_shrink"),
                listOf("legacy_app_list_transition", "app_list_stagger", "reduce_dynamic_effects"),
                listOf("music_text_animation"),
                listOf("motion_profile"),
            ),
            cardBoundaries(SettingsHierarchy.renderedGroup("performance_motion")),
        )
        assertEquals(
            listOf(listOf("hide_from_recents")),
            cardBoundaries(SettingsHierarchy.renderedGroup("performance_system")),
        )
    }

    /**
     * 所有已迁移组的用户可见文案都必须来自 strings.xml。
     *
     * 迁移时最容易图省事写中文常量 —— 编译通过、界面正确，只有换语言才暴露。
     */
    @Test
    fun everyMigratedEntryCarriesTitleRes() {
        SettingsHierarchy.renderedGroups.forEach { group ->
            group.items.forEach { entry ->
                if (entry is SettingsEntry.Picker) {
                    // 单选条的标题是「每个选项一行」，条目自身的 title 不参与渲染，
                    // 所以这里检查的是 option 的 labelRes。
                    entry.options.forEach { option ->
                        // 语言选择器是唯一例外：选项名必须用各自语言写（中文 / English /
                        // 日本語），不能走本地化资源，所以允许硬编码。
                        assertTrue(
                            "${group.id}/${entry.id}/${option.id} 既无 labelRes 也无 label",
                            option.labelRes != null || option.label.isNotBlank(),
                        )
                    }
                } else {
                    assertNotNull("${group.id}/${entry.id} 缺少 titleRes", entry.titleRes)
                }
            }
        }
    }

    /**
     * 把条目按渲染层的规则切卡。与 [SettingsHierarchyTest.watchFaceMoreKeepsHistoricalCardBoundaries]
     * 里的实现是同一套规则，但那条测试特意内联写死，避免测试与被测逻辑同时改动。
     */
    private fun cardBoundaries(entries: List<SettingsEntry>): List<List<String>> {
        val cards = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        fun flush() {
            if (current.isNotEmpty()) {
                cards.add(current)
                current = mutableListOf()
            }
        }
        entries.forEach { entry ->
            when {
                entry is SettingsEntry.Section -> flush()
                entry is SettingsEntry.Picker && entry.independentOptions -> {
                    flush()
                    entry.options.forEach { cards.add(mutableListOf("${entry.id}_${it.id}")) }
                }
                entry.placement == CardPlacement.ALONE -> {
                    flush()
                    cards.add(mutableListOf(entry.id))
                }
                entry.placement == CardPlacement.START -> {
                    flush()
                    current.add(entry.id)
                }
                entry.placement == CardPlacement.INLINE -> current.add(entry.id)
                entry is SettingsEntry.Toggle || entry is SettingsEntry.Slider -> current.add(entry.id)
                else -> {
                    flush()
                    cards.add(mutableListOf(entry.id))
                }
            }
        }
        flush()
        return cards
    }
    /**
     * 工具 / 备份四屏的行 key。
     *
     * 原 `system` 组把四屏混在一起，8 条里 3 条是幽灵（`accessibility`、
     * `notification_access` 从未落地，`backup` 的实际 key 是 `backup_menu`）。
     */
    @Test
    fun toolsAndBackupScreensKeepHistoricalRowKeys() {
        assertEquals(
            listOf("backup_menu", "export_log", "diagnostic_logging", "reset_defaults"),
            SettingsHierarchy.renderedGroup("tools").map { it.id },
        )
        assertEquals(
            listOf("backup_export_page", "backup_import_page"),
            SettingsHierarchy.renderedGroup("backup").map { it.id },
        )
        assertEquals(
            listOf(
                "backup_settings",
                "backup_app_order",
                "backup_widget_order",
                "backup_side_screen_order",
                "backup_photo_watchface",
                "backup_video_watchface",
                "backup_watch_files",
                "export_backup_start",
            ),
            SettingsHierarchy.renderedGroup("backup_export").map { it.id },
        )
        assertEquals(
            listOf(
                "import_backup_pick",
                "backup_settings",
                "backup_app_order",
                "backup_widget_order",
                "backup_side_screen_order",
                "backup_photo_watchface",
                "backup_video_watchface",
                "backup_watch_files",
                "import_backup_start",
            ),
            SettingsHierarchy.renderedGroup("backup_import").map { it.id },
        )
    }

    @Test
    fun toolsAndBackupKeepHistoricalCardBoundaries() {
        assertEquals(
            listOf(listOf("backup_menu", "export_log", "diagnostic_logging", "reset_defaults")),
            cardBoundaries(SettingsHierarchy.renderedGroup("tools")),
        )
        assertEquals(
            listOf(listOf("backup_export_page", "backup_import_page")),
            cardBoundaries(SettingsHierarchy.renderedGroup("backup")),
        )
        val optionIds = listOf(
            "backup_settings",
            "backup_app_order",
            "backup_widget_order",
            "backup_side_screen_order",
            "backup_photo_watchface",
            "backup_video_watchface",
            "backup_watch_files",
        )
        assertEquals(
            listOf(optionIds, listOf("export_backup_start")),
            cardBoundaries(SettingsHierarchy.renderedGroup("backup_export")),
        )
        assertEquals(
            listOf(
                listOf("import_backup_pick"),
                optionIds,
                listOf("import_backup_start"),
            ),
            cardBoundaries(SettingsHierarchy.renderedGroup("backup_import")),
        )
    }

    /**
     * 导出与导入共用**同一批**勾选条目。
     *
     * 这不是风格问题：两条链各写一份 `SettingsEntry` 时，标题、副标题、可用性
     * 判断都会慢慢分叉，最后表现为「导入页的某一项文案跟导出页不一样」。
     * 用 `assertSame` 钉住对象身份，复制一份就会失败。
     */
    @Test
    fun backupScreensShareTheSameOptionEntries() {
        val exportOptions = SettingsHierarchy.renderedGroup("backup_export")
            .filter { it.id.startsWith("backup_") && it.id != "backup_export_page" }
        val importOptions = SettingsHierarchy.renderedGroup("backup_import")
            .filter { it.id.startsWith("backup_") && it.id != "backup_import_page" }

        assertEquals(exportOptions.size, importOptions.size)
        exportOptions.zip(importOptions).forEach { (fromExport, fromImport) ->
            assertSame(
                "「${fromExport.id}」在导出与导入两屏不是同一个条目对象",
                fromExport,
                fromImport,
            )
        }
    }
}
