package com.flue.launcher

import com.flue.launcher.ui.common.WF_TOOLS_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeedbackSurfaceContractTest {
    @Test
    fun widgetPageDoesNotExposeRefreshFailureOrManualRefreshControls() {
        val source = sourceText(
            "kotlin/com/example/wlauncher/ui/smartstack/SmartStackLayer.kt",
        )

        assertFalse(source.contains("widget_refresh_retry"))
        assertFalse(source.contains("R.string.widget_refresh"))
    }

    @Test
    fun widgetRefreshUsesTheSystemAppWidgetServiceBeforeTheCompatibilityBroadcast() {
        val source = sourceText(
            "kotlin/com/example/wlauncher/widget/WidgetRefreshCoordinator.kt",
        )

        val systemDispatch = source.indexOf("updateAppWidgetOptions")
        val compatibilityBroadcast = source.indexOf("ACTION_APPWIDGET_UPDATE")
        assertTrue("刷新必须先经系统 AppWidget 服务唤醒提供方", systemDispatch >= 0)
        assertTrue(
            "系统回调之后仍应保留传统更新广播作为兼容路径",
            compatibilityBroadcast > systemDispatch,
        )
    }

    @Test
    fun editorDefaultsToNativeSquarePreviewAndPlacesControlsBelowWatchShape() {
        val source = sourceText(
            "kotlin/com/example/wlauncher/WatchEditorActivity.kt",
        )

        assertTrue(
            "编辑器预览必须默认使用 WatchMaker 原生 512x512，而不是设备屏幕比例",
            source.contains("mutableStateOf(WatchEditorViewport(512, 512))"),
        )
        val shape = source.indexOf("item(\"watch_shape\")")
        val preview = source.indexOf("item(\"watch_preview_settings\")")
        assertTrue("预览设置必须紧跟在表盘形状之后", shape >= 0 && preview > shape)

        val toolbarStart = source.indexOf("FlueExpressiveFloatingToolbar(")
        val toolbarEnd = source.indexOf("// 画布是正方形", toolbarStart)
        val toolbar = source.substring(toolbarStart, toolbarEnd)
        assertFalse("预览设备不应继续占用顶部工具栏", toolbar.contains("watch_editor_preview_viewport"))
        assertFalse("亮屏/息屏预览不应继续占用顶部工具栏", toolbar.contains("watch_editor_preview_ambient"))
    }

    @Test
    fun diagnosticsDeclareFlueTextColorsOnTheDarkCardSurface() {
        val source = sourceText(
            "kotlin/com/flue/launcher/diagnostics/DiagnosticsSettingsSection.kt",
        )

        assertTrue(source.contains("color = style.titleColor"))
        assertTrue(source.contains("color = style.bodyColor"))
        assertTrue(source.contains("color = style.secondaryTextColor"))
    }

    @Test
    fun wfToolsEntryOpensTheReleasePage() {
        assertEquals(
            "https://gitlab.com/ailife8881/wftools/-/releases",
            WF_TOOLS_URL,
        )
    }

    private fun sourceText(relativePath: String): String {
        // 按真实模块根查找，避免商店拆分后读取旧目录或误把 build 产物当源码。
        val repositoryRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "app/src/main").isDirectory }
        val sourceRoot = listOf(
            "app", "studio", "ui-kit", "watchface-core", "watchface-runtime",
            "watchface-editor", "watchface-store-client", "watchface-store", "agent",
        ).asSequence().map { File(repositoryRoot, "$it/src/main") }
            .firstOrNull { root -> File(root, relativePath).isFile }
            ?: error("找不到主源码目录，工作目录是 ${File(".").absolutePath}")
        return File(sourceRoot, relativePath).also {
            check(it.isFile) { "找不到源码文件：${it.absolutePath}" }
        }.readText().replace("\r\n", "\n")
    }
}
