package com.fluly.launcher.ui.style

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class TransientDialogSourcePolicyTest {
    /**
     * 允许用原生 Dialog 的「整屏任务页」。
     *
     * 扫描覆盖全部九模块；使用仓库相对路径，避免同名文件共享豁免。
     * 设置页拆分后的全屏任务按实际所在文件登记，不豁免瞬时菜单。
     */
    private val allowedFullscreenDialogFiles = setOf(
        "app/src/main/kotlin/com/example/wlauncher/SettingsActivity.kt",
        "watchface-store/src/main/kotlin/com/example/wlauncher/WatchStoreActivity.kt",
        // 从 SettingsActivity 拆出的捐赠图片全屏预览与备份阻塞进度页。
        "app/src/main/kotlin/com/example/wlauncher/ui/settings/SettingsAboutAndDonate.kt",
        "app/src/main/kotlin/com/example/wlauncher/ui/settings/SettingsBackupDialogs.kt",
        "ui-kit/src/main/kotlin/com/fluly/launcher/ui/common/FlulyImageCropOverlay.kt",
        "ui-kit/src/main/kotlin/com/fluly/launcher/ui/common/InternalFileBrowser.kt",
        "watchface-store/src/main/kotlin/com/fluly/launcher/ui/store/WatchStorePageStack.kt",
    )

    @Test
    fun `transient surfaces do not use material alert dialog`() {
        val violations = kotlinSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if (
                    "androidx.compose.material3.AlertDialog" in code ||
                    Regex("""(?<!Fluly)\bAlertDialog\s*\(""").containsMatchIn(code)
                ) {
                    "${relative(file)}:${index + 1}"
                } else {
                    null
                }
            }
        }.toList()
        assertEquals("瞬时提示必须进入 FlulyOverlayHost：\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `platform dialog remains limited to enumerated full screen tasks`() {
        val violations = kotlinSources().flatMap { file ->
            val relative = relative(file)
            if (relative in allowedFullscreenDialogFiles) return@flatMap emptyList()
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if (
                    !code.trimStart().startsWith("*") && (
                        "androidx.compose.ui.window.Dialog" in code ||
                            Regex("""(?<!Fluly)\bDialog\s*\(""").containsMatchIn(code)
                        )
                ) {
                    "$relative:${index + 1}"
                } else {
                    null
                }
            }
        }.toList()
        assertEquals("原生 Dialog 仅允许用于列举的全屏任务页：\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `transient menus and bottom sheets use the overlay host`() {
        val forbidden = listOf(
            Regex("""\bDropdownMenu\s*\("""),
            Regex("""\bWindowListPopup\s*\("""),
            Regex("""\bModalBottomSheet\s*\("""),
            Regex("""\bBottomSheetScaffold\s*\("""),
        )
        val violations = kotlinSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if (forbidden.any { it.containsMatchIn(code) }) {
                    "${relative(file)}:${index + 1}"
                } else {
                    null
                }
            }
        }.toList()
        assertEquals("瞬时菜单与底部弹层必须进入 FlulyOverlayHost：\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `overlay input blocker is a sibling and prompt exit is animated`() {
        val host = sourceRoots()
            .asSequence()
            .map { File(it, "com/fluly/launcher/ui/style/FlulyOverlayHost.kt") }
            .first(File::isFile)
            .readText()

        assertEquals(false, host.contains(".fillMaxSize()\n                        .pointerInput(chainToken)"))
        assertEquals(true, host.contains("FlulyOverlayInputBlocker("))
        assertEquals(true, host.contains("targetValue = if (visible) 1f else 0f"))
        assertEquals(true, host.contains("policy.exitDurationMillis"))
    }

    @Test
    fun `backdrop and full screen content share the requested reveal motion`() {
        val host = sourceRoots()
            .asSequence()
            .map { File(it, "com/fluly/launcher/ui/style/FlulyOverlayHost.kt") }
            .first(File::isFile)
            .readText()
        val foreground = host.substringAfter("private fun FlulyOverlayForeground(")
            .substringBefore("internal fun Modifier.flulyPromptTextReveal")
        val dialog = sourceRoots()
            .asSequence()
            .map { File(it, "com/fluly/launcher/ui/style/FlulyDialog.kt") }
            .first(File::isFile)
            .readText()

        assertEquals(true, host.contains("maxBlurRadiusPx * backdropProgress.value"))
        assertEquals(true, host.contains("alpha = backdropProgress.value"))
        assertEquals(true, foreground.contains("revealWholeContent"))
        assertEquals(true, foreground.contains("renderEffect ="))
        assertEquals(true, host.contains("internal fun Modifier.flulyPromptTextReveal"))
        assertEquals(true, dialog.contains("modifier = Modifier.flulyPromptTextReveal()"))
    }

    private fun kotlinSources(): Sequence<File> = RepositorySourceScan.kotlinSources()

    private fun sourceRoots(): List<File> = RepositorySourceScan.sourceRoots()

    private fun relative(file: File): String = RepositorySourceScan.relative(file)
}
